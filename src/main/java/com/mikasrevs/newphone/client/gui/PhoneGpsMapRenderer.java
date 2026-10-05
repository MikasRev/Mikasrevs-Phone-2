package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** High quality, cached top-down terrain renderer for the MPhone GPS. */
public final class PhoneGpsMapRenderer {
    public static final int DEFAULT_ZOOM_LEVEL = 1;

    private static final int[] ZOOM_STEPS = {1, 2, 4, 8};
    private static final int CACHE_MARGIN = 24;
    private static final int FULL_MAX_CACHE_PIXELS = 45_000;
    private static final int MINIMAP_MAX_CACHE_PIXELS = 12_000;
    private static final int MINIMAP_MIN_SCREEN_STRIDE = 2;
    private static final int MAX_BUILD_SAMPLES_PER_FRAME = 1_024;
    private static final long BUILD_BUDGET_NANOS = 1_750_000L;
    private static final long UNLOADED_REFRESH_MS = 5_000L;
    private static final int UNLOADED_HEIGHT = Integer.MIN_VALUE;
    private static final ResourceLocation MAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(NewPhone.MOD_ID, "dynamic/gps_map");
    private static final ResourceLocation MINIMAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(NewPhone.MOD_ID, "dynamic/gps_minimap");
    private static final TerrainCache FULL_CACHE = new TerrainCache();
    private static final TerrainCache MINIMAP_CACHE = new TerrainCache();

    private PhoneGpsMapRenderer() { }

    /** Drops pending work and world references after disconnect while retaining small reusable textures. */
    public static void clearWorldCache() {
        clearWorldCache(FULL_CACHE);
        clearWorldCache(MINIMAP_CACHE);
    }

    private static void clearWorldCache(TerrainCache cache) {
        cache.pending = null;
        cache.level = null;
        cache.dimension = "";
        cache.refreshedAt = 0L;
        cache.retryBuildAt = 0L;
        cache.hadUnloadedSamples = false;
    }

    /** Backwards-compatible square renderer used by the world minimap. */
    public static void drawMap(
            GuiGraphics graphics, Minecraft minecraft, int left, int top, int size, boolean showRoute
    ) {
        PhoneGpsState.Location center = PhoneGpsState.currentLocation(minecraft);
        drawMapInternal(graphics, minecraft, left, top, size, size, center, DEFAULT_ZOOM_LEVEL, null,
                showRoute, MapStyle.TERRAIN, false, MINIMAP_CACHE, MINIMAP_TEXTURE,
                MINIMAP_MIN_SCREEN_STRIDE, MINIMAP_MAX_CACHE_PIXELS);
    }

    /** Service HUD variant with a rectangular viewport and the lightweight minimap cache. */
    public static Projection drawMinimap(
            GuiGraphics graphics,
            Minecraft minecraft,
            int left,
            int top,
            int width,
            int height,
            int zoomLevel,
            PhoneGpsState.Location selected,
            boolean showRoute,
            MapStyle style
    ) {
        PhoneGpsState.Location center = PhoneGpsState.currentLocation(minecraft);
        return drawMapInternal(graphics, minecraft, left, top, width, height, center, zoomLevel, selected,
                showRoute, style, false, MINIMAP_CACHE, MINIMAP_TEXTURE,
                MINIMAP_MIN_SCREEN_STRIDE, MINIMAP_MAX_CACHE_PIXELS);
    }

    /**
     * Draws a rectangular north-up map and returns the exact screen/world projection used by its overlays.
     * The center can be independent of the player, which lets the GPS screen pan without changing the route.
     */
    public static Projection drawMap(
            GuiGraphics graphics,
            Minecraft minecraft,
            int left,
            int top,
            int width,
            int height,
            PhoneGpsState.Location center,
            int zoomLevel,
            PhoneGpsState.Location selected,
            boolean showRoute,
            MapStyle style
    ) {
        return drawMap(graphics, minecraft, left, top, width, height, center, zoomLevel, selected,
                showRoute, style, false);
    }

    /** Keeps the current terrain cache stable while the user is actively dragging the map. */
    public static Projection drawMap(
            GuiGraphics graphics,
            Minecraft minecraft,
            int left,
            int top,
            int width,
            int height,
            PhoneGpsState.Location center,
            int zoomLevel,
            PhoneGpsState.Location selected,
            boolean showRoute,
            MapStyle style,
            boolean interactivePan
    ) {
        return drawMapInternal(graphics, minecraft, left, top, width, height, center, zoomLevel, selected,
                showRoute, style, interactivePan, FULL_CACHE, MAP_TEXTURE, 1, FULL_MAX_CACHE_PIXELS);
    }

    private static Projection drawMapInternal(
            GuiGraphics graphics,
            Minecraft minecraft,
            int left,
            int top,
            int width,
            int height,
            PhoneGpsState.Location center,
            int zoomLevel,
            PhoneGpsState.Location selected,
            boolean showRoute,
            MapStyle style,
            boolean interactivePan,
            TerrainCache cache,
            ResourceLocation textureLocation,
            int minimumScreenStride,
            int maxCachePixels
    ) {
        PhoneGpsState.Location safeCenter = center == null
                ? PhoneGpsState.currentLocation(minecraft)
                : center;
        int safeZoom = clampZoom(zoomLevel);
        Projection projection = new Projection(
                left, top, Math.max(0, width), Math.max(0, height), safeCenter,
                safeZoom, ZOOM_STEPS[safeZoom]
        );
        if (graphics == null || projection.width() <= 0 || projection.height() <= 0) return projection;

        MapStyle safeStyle = style == null ? MapStyle.TERRAIN : style;
        graphics.fill(left, top, left + width, top + height, fogColor(safeCenter.dimension(), safeStyle, 0, 0));
        CacheView view = ensureCache(minecraft, projection, safeStyle, interactivePan,
                cache, textureLocation, minimumScreenStride, maxCachePixels);
        if (view != null) {
            drawCacheView(graphics, textureLocation, cache, projection, view);
        }

        graphics.enableScissor(left, top, left + width, top + height);
        drawOverlays(graphics, minecraft, projection, selected, showRoute);
        graphics.disableScissor();
        drawMapEdge(graphics, left, top, width, height);
        return projection;
    }

    private static CacheView ensureCache(
            Minecraft minecraft,
            Projection projection,
            MapStyle style,
            boolean interactivePan,
            TerrainCache cache,
            ResourceLocation textureLocation,
            int minimumScreenStride,
            int maxCachePixels
    ) {
        if (minecraft == null) return null;

        int screenStride = samplingStride(
                projection.width(), projection.height(), minimumScreenStride, maxCachePixels
        );
        int sourceWidth = ceilDiv(projection.width(), screenStride);
        int sourceHeight = ceilDiv(projection.height(), screenStride);
        int sampleStep = projection.blocksPerPixel() * screenStride;
        int wantedCenterX = quantize(projection.center().x(), sampleStep);
        int wantedCenterZ = quantize(projection.center().z(), sampleStep);
        Level level = matchingLevel(minecraft, projection.center().dimension());
        long now = System.currentTimeMillis();

        boolean compatible = cache.texture != null
                && cache.level == level
                && Objects.equals(cache.dimension, projection.center().dimension())
                && cache.style == style
                && cache.sampleStep == sampleStep
                && cache.sourceWidth == sourceWidth
                && cache.sourceHeight == sourceHeight;
        float sourceX = compatible
                ? (float) (CACHE_MARGIN
                        + ((double) projection.center().x() - cache.centerX) / sampleStep)
                : CACHE_MARGIN;
        float sourceY = compatible
                ? (float) (CACHE_MARGIN
                        + ((double) projection.center().z() - cache.centerZ) / sampleStep)
                : CACHE_MARGIN;
        boolean cropFits = compatible
                && sourceX >= 0 && sourceY >= 0
                && sourceX + sourceWidth <= cache.textureWidth
                && sourceY + sourceHeight <= cache.textureHeight;

        boolean refreshUnloaded = compatible && cropFits && level != null && cache.hadUnloadedSamples
                && now - cache.refreshedAt >= UNLOADED_REFRESH_MS;
        boolean needsBuild = !compatible || !cropFits || refreshUnloaded;
        if (!interactivePan && needsBuild && now >= cache.retryBuildAt) {
            requestBuild(cache, level, projection.center().dimension(), style,
                    wantedCenterX, wantedCenterZ, sampleStep, sourceWidth, sourceHeight);
        }

        if (cache.pending != null) {
            if (!cache.pending.matches(level, projection.center().dimension(), style,
                    sampleStep, sourceWidth, sourceHeight)
                    || !cache.pending.covers(wantedCenterX, wantedCenterZ)) {
                cache.pending = null;
            } else {
                advanceBuild(minecraft, cache, textureLocation);
            }
        }

        compatible = cache.texture != null
                && cache.level == level
                && Objects.equals(cache.dimension, projection.center().dimension())
                && cache.style == style
                && cache.sampleStep == sampleStep
                && cache.sourceWidth == sourceWidth
                && cache.sourceHeight == sourceHeight;
        if (!compatible) return null;

        sourceX = (float) (CACHE_MARGIN
                + ((double) projection.center().x() - cache.centerX) / sampleStep);
        sourceY = (float) (CACHE_MARGIN
                + ((double) projection.center().z() - cache.centerZ) / sampleStep);
        return new CacheView(sourceX, sourceY, sourceWidth, sourceHeight);
    }

    /**
     * Keeps cached terrain on the same projection as markers when a pan reaches beyond the cache.
     * The unavailable part stays as the fog fill drawn by the caller until the pending build lands.
     */
    private static void drawCacheView(
            GuiGraphics graphics,
            ResourceLocation textureLocation,
            TerrainCache cache,
            Projection projection,
            CacheView view
    ) {
        float sourceRight = view.sourceX + view.sourceWidth;
        float sourceBottom = view.sourceY + view.sourceHeight;
        float visibleSourceLeft = Math.max(0.0F, view.sourceX);
        float visibleSourceTop = Math.max(0.0F, view.sourceY);
        float visibleSourceRight = Math.min(cache.textureWidth, sourceRight);
        float visibleSourceBottom = Math.min(cache.textureHeight, sourceBottom);
        if (visibleSourceRight <= visibleSourceLeft || visibleSourceBottom <= visibleSourceTop) return;

        boolean fullView = visibleSourceLeft == view.sourceX && visibleSourceTop == view.sourceY
                && visibleSourceRight == sourceRight && visibleSourceBottom == sourceBottom;
        if (!fullView) {
            double scaleX = projection.width() / (double) view.sourceWidth;
            double scaleY = projection.height() / (double) view.sourceHeight;
            int clipLeft = projection.left() + (int) Math.ceil((visibleSourceLeft - view.sourceX) * scaleX);
            int clipTop = projection.top() + (int) Math.ceil((visibleSourceTop - view.sourceY) * scaleY);
            int clipRight = projection.left() + (int) Math.floor((visibleSourceRight - view.sourceX) * scaleX);
            int clipBottom = projection.top() + (int) Math.floor((visibleSourceBottom - view.sourceY) * scaleY);
            clipLeft = clamp(clipLeft, projection.left(), projection.left() + projection.width());
            clipTop = clamp(clipTop, projection.top(), projection.top() + projection.height());
            clipRight = clamp(clipRight, projection.left(), projection.left() + projection.width());
            clipBottom = clamp(clipBottom, projection.top(), projection.top() + projection.height());
            if (clipRight <= clipLeft || clipBottom <= clipTop) return;
            graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        }

        graphics.blit(
                textureLocation,
                projection.left(),
                projection.top(),
                projection.width(),
                projection.height(),
                view.sourceX,
                view.sourceY,
                view.sourceWidth,
                view.sourceHeight,
                cache.textureWidth,
                cache.textureHeight
        );
        if (!fullView) graphics.disableScissor();
    }

    private static void requestBuild(
            TerrainCache cache,
            Level level,
            String dimension,
            MapStyle style,
            int centerX,
            int centerZ,
            int sampleStep,
            int sourceWidth,
            int sourceHeight
    ) {
        PendingBuild pending = cache.pending;
        if (pending != null
                && pending.matches(level, dimension, style, sampleStep, sourceWidth, sourceHeight)
                && pending.covers(centerX, centerZ)) {
            return;
        }
        cache.pending = new PendingBuild(
                level, dimension, style, centerX, centerZ, sampleStep, sourceWidth, sourceHeight
        );
    }

    private static void advanceBuild(
            Minecraft minecraft, TerrainCache cache, ResourceLocation textureLocation
    ) {
        PendingBuild build = cache.pending;
        if (build == null) return;

        int total = build.colors.length;
        if (build.nextSample < total) {
            long deadline = System.nanoTime() + BUILD_BUDGET_NANOS;
            int processed = 0;
            while (build.nextSample < total && processed < MAX_BUILD_SAMPLES_PER_FRAME) {
                int index = build.nextSample++;
                int row = index / build.textureWidth;
                int col = index - row * build.textureWidth;
                int worldX = build.originX + col * build.sampleStep;
                int worldZ = build.originZ + row * build.sampleStep;
                TerrainSample sample = sample(
                        minecraft, build.level, build.dimension, worldX, worldZ, build.samplePos, build.style
                );
                build.colors[index] = sample.color();
                build.heights[index] = sample.height();
                build.waterDepths[index] = sample.waterDepth();
                if (sample.height() == UNLOADED_HEIGHT) build.unloadedSamples++;
                processed++;

                if ((processed & 31) == 0 && System.nanoTime() >= deadline) break;
            }
            // Keep the post-processing/upload out of the frame that completed sampling.
            return;
        }

        shadeTerrain(build.colors, build.heights, build.waterDepths,
                build.textureWidth, build.textureHeight, build.style);
        if (!uploadTexture(minecraft, cache, textureLocation,
                build.colors, build.textureWidth, build.textureHeight)) {
            cache.pending = null;
            cache.retryBuildAt = System.currentTimeMillis() + 1_000L;
            return;
        }

        cache.level = build.level;
        cache.dimension = build.dimension;
        cache.style = build.style;
        cache.centerX = build.centerX;
        cache.centerZ = build.centerZ;
        cache.sampleStep = build.sampleStep;
        cache.sourceWidth = build.sourceWidth;
        cache.sourceHeight = build.sourceHeight;
        cache.textureWidth = build.textureWidth;
        cache.textureHeight = build.textureHeight;
        cache.refreshedAt = System.currentTimeMillis();
        cache.hadUnloadedSamples = build.unloadedSamples > 0;
        cache.pending = null;
        cache.retryBuildAt = 0L;
    }

    private static boolean uploadTexture(
            Minecraft minecraft,
            TerrainCache cache,
            ResourceLocation textureLocation,
            int[] colors,
            int width,
            int height
    ) {
        NativeImage pixels = cache.texture == null ? null : cache.texture.getPixels();
        boolean canReuse = pixels != null && pixels.getWidth() == width && pixels.getHeight() == height;
        NativeImage target = canReuse ? pixels : new NativeImage(width, height, false);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    target.setPixelRGBA(x, y, argbToAbgr(colors[y * width + x]));
                }
            }
            if (canReuse) {
                cache.texture.upload();
            } else {
                if (cache.texture != null) minecraft.getTextureManager().release(textureLocation);
                DynamicTexture replacement = new DynamicTexture(target);
                replacement.setFilter(false, false);
                minecraft.getTextureManager().register(textureLocation, replacement);
                cache.texture = replacement;
            }
            return true;
        } catch (RuntimeException exception) {
            if (!canReuse) target.close();
            return false;
        }
    }

    private static TerrainSample sample(
            Minecraft minecraft,
            Level level,
            String dimension,
            int x,
            int z,
            BlockPos.MutableBlockPos pos,
            MapStyle style
    ) {
        if (level == null) return new TerrainSample(fogColor(dimension, style, x, z), UNLOADED_HEIGHT, 0);
        pos.set(x, level.getMinBuildHeight(), z);
        if (!level.hasChunkAt(pos)) {
            return new TerrainSample(fogColor(dimension, style, x, z), UNLOADED_HEIGHT, 0);
        }

        try {
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            surfaceY = clamp(surfaceY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
            pos.set(x, surfaceY, z);
            BlockState state = level.getBlockState(pos);
            int colorY = surfaceY;

            for (int depth = 0; depth < 8 && (state.isAir() || state.getMapColor(level, pos) == MapColor.NONE); depth++) {
                if (colorY <= level.getMinBuildHeight()) break;
                pos.setY(--colorY);
                state = level.getBlockState(pos);
            }

            boolean water = state.getFluidState().is(FluidTags.WATER);
            int waterDepth = water ? waterDepth(level, x, surfaceY, z) : 0;
            pos.set(x, colorY, z);
            MapColor mapColor = state.getMapColor(level, pos);
            int base = mapColor == null || mapColor == MapColor.NONE || mapColor.col == 0
                    ? fogColor(dimension, style, x, z)
                    : 0xFF000000 | mapColor.col;
            int tint = minecraft.getBlockColors().getColor(state, level, pos, 0);
            if (tint != -1) base = blend(base, 0xFF000000 | tint, water ? 0.78F : 0.62F);
            return new TerrainSample(applyStyle(base, style), surfaceY, waterDepth);
        } catch (RuntimeException ignored) {
            return new TerrainSample(fogColor(dimension, style, x, z), UNLOADED_HEIGHT, 0);
        }
    }

    private static int waterDepth(Level level, int x, int surfaceY, int z) {
        int floorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        return clamp(surfaceY - floorY, 0, 32);
    }

    private static void shadeTerrain(
            int[] colors, int[] heights, int[] waterDepths, int width, int height, MapStyle style
    ) {
        int slopeStrength = style == MapStyle.CONTRAST ? 4 : 3;
        int maxShade = style == MapStyle.CONTRAST ? 38 : 28;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int index = row * width + col;
                int own = heights[index];
                if (own == UNLOADED_HEIGHT) continue;
                int west = col > 0 && heights[index - 1] != UNLOADED_HEIGHT ? heights[index - 1] : own;
                int north = row > 0 && heights[index - width] != UNLOADED_HEIGHT ? heights[index - width] : own;
                int slope = clamp((own - west) * slopeStrength + (own - north) * 2, -maxShade, maxShade);
                int depthShade = Math.min(style == MapStyle.CONTRAST ? 42 : 34, waterDepths[index] * 2);
                colors[index] = brightness(colors[index], slope - depthShade);
            }
        }
    }

    private static void drawOverlays(
            GuiGraphics graphics,
            Minecraft minecraft,
            Projection projection,
            PhoneGpsState.Location selected,
            boolean showRoute
    ) {
        PhoneGpsState.Location player = PhoneGpsState.currentLocation(minecraft);
        PhoneGpsState.Location target = PhoneGpsState.targetLocation();
        boolean playerDimension = sameDimension(player, projection.center());
        boolean targetDimension = sameDimension(target, projection.center());
        double playerX = projection.worldToScreenX(player.x());
        double playerY = projection.worldToScreenZ(player.z());
        boolean playerVisible = playerDimension && projection.contains(playerX, playerY);

        if (playerVisible && minecraft != null && minecraft.player != null) {
            drawHeadingBeam(graphics, projection, playerX, playerY,
                    minecraft.player.getViewYRot(minecraft.getFrameTime()));
        }

        if (showRoute && playerDimension && targetDimension) {
            drawRoute(graphics, projection,
                    playerX, playerY,
                    projection.worldToScreenX(target.x()), projection.worldToScreenZ(target.z()));
        }

        if (sameDimension(selected, projection.center()) && !sameLocation(selected, target)) {
            drawSelectionMarker(graphics, projection, selected);
        }
        if (targetDimension) drawWaypoint(graphics, projection, target);
        if (playerVisible) {
            drawPlayerDot(graphics, (int) Math.round(playerX), (int) Math.round(playerY));
        }
    }

    /** A soft, widening headlight beam makes the player's north-up heading readable at a glance. */
    private static void drawHeadingBeam(
            GuiGraphics graphics, Projection projection, double screenX, double screenY, float yawDegrees
    ) {
        int length = clamp(Math.round(Math.min(projection.width(), projection.height()) * 0.17F), 28, 52);
        int farHalfWidth = clamp(Math.round(length * 0.42F), 12, 22);
        int start = 5;
        int bandHeight = Math.max(2, (length - start) / 14);

        graphics.pose().pushPose();
        graphics.pose().translate(screenX, screenY, 0.0D);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(yawDegrees));
        for (int y = start; y < length; y += bandHeight) {
            int bottom = Math.min(length + 1, y + bandHeight);
            float progress = (y - start) / (float) Math.max(1, length - start);
            int outerHalf = Math.max(2, Math.round(2 + (farHalfWidth - 2) * progress));
            int middleHalf = Math.max(1, Math.round(outerHalf * 0.62F));
            int innerHalf = Math.max(1, Math.round(outerHalf * 0.30F));
            int outerAlpha = Math.round(24 + (3 - 24) * progress);
            int middleAlpha = Math.round(34 + (4 - 34) * progress);
            int innerAlpha = Math.round(46 + (5 - 46) * progress);

            graphics.fill(-outerHalf, y, outerHalf + 1, bottom, outerAlpha << 24 | 0xBBDFFF);
            graphics.fill(-middleHalf, y, middleHalf + 1, bottom, middleAlpha << 24 | 0xD9EEFF);
            graphics.fill(-innerHalf, y, innerHalf + 1, bottom, innerAlpha << 24 | 0xF2FAFF);
        }
        graphics.pose().popPose();
    }

    private static void drawRoute(
            GuiGraphics graphics, Projection projection, double x1, double y1, double x2, double y2
    ) {
        double[] clipped = clipLine(
                x1, y1, x2, y2,
                projection.left() + 1.0D,
                projection.top() + 1.0D,
                projection.left() + projection.width() - 2.0D,
                projection.top() + projection.height() - 2.0D
        );
        if (clipped == null) return;
        int ax = (int) Math.round(clipped[0]);
        int ay = (int) Math.round(clipped[1]);
        int bx = (int) Math.round(clipped[2]);
        int by = (int) Math.round(clipped[3]);
        drawThickLine(graphics, ax, ay, bx, by, 2, 0xB51A2633);
        drawThickLine(graphics, ax, ay, bx, by, 1, 0xFF2D8CFF);
    }

    private static void drawWaypoint(GuiGraphics graphics, Projection projection, PhoneGpsState.Location location) {
        MarkerPoint point = markerPoint(projection, location, 9);
        int x = point.x();
        int y = point.y();
        fillCircle(graphics, x + 1, y - 2, 8, 0x66000000);
        graphics.fill(x - 2, y, x + 3, y + 7, 0xFFD93B3B);
        fillCircle(graphics, x, y - 3, 7, 0xFFFFFFFF);
        fillCircle(graphics, x, y - 3, 5, point.outside() ? 0xFFFF645C : 0xFFE53935);
        fillCircle(graphics, x, y - 3, 2, 0xFFFFFFFF);
    }

    private static void drawSelectionMarker(
            GuiGraphics graphics, Projection projection, PhoneGpsState.Location location
    ) {
        MarkerPoint point = markerPoint(projection, location, 10);
        int x = point.x();
        int y = point.y();
        drawCircleOutline(graphics, x, y, 8, 0xFFFFFFFF);
        drawCircleOutline(graphics, x, y, 6, point.outside() ? 0xFFFF7A70 : 0xFFFF3B30);
        graphics.fill(x - 1, y - 10, x + 2, y - 4, 0xFFFF3B30);
        graphics.fill(x - 1, y + 5, x + 2, y + 11, 0xFFFF3B30);
        graphics.fill(x - 10, y - 1, x - 4, y + 2, 0xFFFF3B30);
        graphics.fill(x + 5, y - 1, x + 11, y + 2, 0xFFFF3B30);
        fillCircle(graphics, x, y, 2, 0xFFFF3B30);
    }

    private static MarkerPoint markerPoint(
            Projection projection, PhoneGpsState.Location location, int inset
    ) {
        double rawX = projection.worldToScreenX(location.x());
        double rawY = projection.worldToScreenZ(location.z());
        boolean outside = !projection.contains(rawX, rawY);
        int minX = projection.left() + Math.min(inset, Math.max(0, projection.width() / 2));
        int maxX = projection.left() + projection.width() - 1 - Math.min(inset, Math.max(0, projection.width() / 2));
        int minY = projection.top() + Math.min(inset, Math.max(0, projection.height() / 2));
        int maxY = projection.top() + projection.height() - 1 - Math.min(inset, Math.max(0, projection.height() / 2));
        int x = clamp((int) Math.round(rawX), Math.min(minX, maxX), Math.max(minX, maxX));
        int y = clamp((int) Math.round(rawY), Math.min(minY, maxY), Math.max(minY, maxY));
        return new MarkerPoint(x, y, outside);
    }

    private static void drawPlayerDot(GuiGraphics graphics, int x, int y) {
        fillCircle(graphics, x + 1, y + 1, 7, 0x66000000);
        fillCircle(graphics, x, y, 6, 0xFFFFFFFF);
        fillCircle(graphics, x, y, 4, 0xFF1677FF);
        fillCircle(graphics, x - 1, y - 1, 2, 0xFF73B7FF);
    }

    private static void drawMapEdge(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + 1, 0x4DFFFFFF);
        graphics.fill(left, top + height - 1, left + width, top + height, 0x73000000);
        graphics.fill(left, top, left + 1, top + height, 0x4DFFFFFF);
        graphics.fill(left + width - 1, top, left + width, top + height, 0x73000000);
    }

    private static void drawThickLine(
            GuiGraphics graphics, int x1, int y1, int x2, int y2, int radius, int color
    ) {
        int dx = Math.abs(x2 - x1);
        int sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1);
        int sy = y1 < y2 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x1 - radius, y1 - radius, x1 + radius + 1, y1 + radius + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int twice = error * 2;
            if (twice >= dy) {
                error += dy;
                x1 += sx;
            }
            if (twice <= dx) {
                error += dx;
                y1 += sy;
            }
        }
    }

    private static void fillCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = (int) Math.floor(Math.sqrt(radius * radius - y * y));
            graphics.fill(cx - halfWidth, cy + y, cx + halfWidth + 1, cy + y + 1, color);
        }
    }

    private static void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        int x = radius;
        int y = 0;
        int error = 1 - radius;
        while (x >= y) {
            plotCirclePoints(graphics, cx, cy, x, y, color);
            y++;
            if (error < 0) {
                error += y * 2 + 1;
            } else {
                x--;
                error += (y - x) * 2 + 1;
            }
        }
    }

    private static void plotCirclePoints(
            GuiGraphics graphics, int cx, int cy, int x, int y, int color
    ) {
        plot(graphics, cx + x, cy + y, color);
        plot(graphics, cx + y, cy + x, color);
        plot(graphics, cx - y, cy + x, color);
        plot(graphics, cx - x, cy + y, color);
        plot(graphics, cx - x, cy - y, color);
        plot(graphics, cx - y, cy - x, color);
        plot(graphics, cx + y, cy - x, color);
        plot(graphics, cx + x, cy - y, color);
    }

    private static void plot(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 2, y + 2, color);
    }

    private static double[] clipLine(
            double x1, double y1, double x2, double y2, double minX, double minY, double maxX, double maxY
    ) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1 - minX, maxX - x1, y1 - minY, maxY - y1};
        double start = 0.0D;
        double end = 1.0D;
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0D) {
                if (q[i] < 0.0D) return null;
                continue;
            }
            double ratio = q[i] / p[i];
            if (p[i] < 0.0D) start = Math.max(start, ratio);
            else end = Math.min(end, ratio);
            if (start > end) return null;
        }
        return new double[] {x1 + start * dx, y1 + start * dy, x1 + end * dx, y1 + end * dy};
    }

    private static Level matchingLevel(Minecraft minecraft, String dimension) {
        Level level = minecraft.level;
        if (level == null) return null;
        String current = level.dimension().location().toString();
        return Objects.equals(current, dimension) ? level : null;
    }

    private static int samplingStride(int width, int height, int minimumStride, int maxCachePixels) {
        int stride = Math.max(1, minimumStride);
        while ((long) (ceilDiv(width, stride) + CACHE_MARGIN * 2)
                * (ceilDiv(height, stride) + CACHE_MARGIN * 2) > Math.max(1, maxCachePixels)) {
            stride++;
        }
        return stride;
    }

    private static int fogColor(String dimension, MapStyle style, int x, int z) {
        int base;
        if (dimension != null && dimension.contains("the_nether")) base = 0xFF30282A;
        else if (dimension != null && dimension.contains("the_end")) base = 0xFF34342E;
        else base = 0xFF27323B;
        int hash = x * 73428767 ^ z * 912931;
        int noise = ((hash >>> 12) & 3) - 1;
        if (style == MapStyle.CONTRAST) noise -= 3;
        return brightness(base, noise);
    }

    private static int applyStyle(int color, MapStyle style) {
        float contrast = style == MapStyle.CONTRAST ? 1.22F : 1.06F;
        float saturation = style == MapStyle.CONTRAST ? 1.18F : 1.08F;
        int r = color >>> 16 & 255;
        int g = color >>> 8 & 255;
        int b = color & 255;
        float luminance = r * 0.299F + g * 0.587F + b * 0.114F;
        r = clamp(Math.round(luminance + (r - luminance) * saturation), 0, 255);
        g = clamp(Math.round(luminance + (g - luminance) * saturation), 0, 255);
        b = clamp(Math.round(luminance + (b - luminance) * saturation), 0, 255);
        r = clamp(Math.round(128.0F + (r - 128.0F) * contrast), 0, 255);
        g = clamp(Math.round(128.0F + (g - 128.0F) * contrast), 0, 255);
        b = clamp(Math.round(128.0F + (b - 128.0F) * contrast), 0, 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int blend(int first, int second, float amount) {
        float t = Mth.clamp(amount, 0.0F, 1.0F);
        int r = Math.round((first >>> 16 & 255) * (1.0F - t) + (second >>> 16 & 255) * t);
        int g = Math.round((first >>> 8 & 255) * (1.0F - t) + (second >>> 8 & 255) * t);
        int b = Math.round((first & 255) * (1.0F - t) + (second & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int brightness(int color, int amount) {
        int a = color >>> 24 & 255;
        int r = clamp((color >>> 16 & 255) + amount, 0, 255);
        int g = clamp((color >>> 8 & 255) + amount, 0, 255);
        int b = clamp((color & 255) + amount, 0, 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** NativeImage RGBA pixels are packed as ABGR integers on little-endian clients. */
    private static int argbToAbgr(int argb) {
        return argb & 0xFF00FF00 | (argb & 0x00FF0000) >>> 16 | (argb & 0x000000FF) << 16;
    }

    private static boolean sameDimension(PhoneGpsState.Location first, PhoneGpsState.Location second) {
        return first != null && second != null && Objects.equals(first.dimension(), second.dimension());
    }

    private static boolean sameLocation(PhoneGpsState.Location first, PhoneGpsState.Location second) {
        return first != null && second != null
                && Objects.equals(first.dimension(), second.dimension())
                && first.x() == second.x() && first.z() == second.z();
    }

    private static int quantize(int value, int step) {
        long rounded = Math.round(value / (double) Math.max(1, step)) * (long) Math.max(1, step);
        return (int) Mth.clamp(rounded, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.max(1, (Math.max(0, value) + divisor - 1) / divisor);
    }

    private static int clampZoom(int zoomLevel) {
        return clamp(zoomLevel, 0, ZOOM_STEPS.length - 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum MapStyle {
        TERRAIN,
        CONTRAST
    }

    /** Immutable conversion between the visible map rectangle and block coordinates. */
    public static record Projection(
            int left,
            int top,
            int width,
            int height,
            PhoneGpsState.Location center,
            int zoomLevel,
            int blocksPerPixel
    ) {
        public Projection {
            width = Math.max(0, width);
            height = Math.max(0, height);
            center = center == null
                    ? new PhoneGpsState.Location("minecraft:overworld", 0, 0, 0)
                    : center;
            zoomLevel = clampZoom(zoomLevel);
            blocksPerPixel = ZOOM_STEPS[zoomLevel];
        }

        public PhoneGpsState.Location screenToLocation(double screenX, double screenY, int y) {
            long worldX = Math.round(center.x() + (screenX - (left + width / 2.0D)) * blocksPerPixel);
            long worldZ = Math.round(center.z() + (screenY - (top + height / 2.0D)) * blocksPerPixel);
            return new PhoneGpsState.Location(center.dimension(), safeCoordinate(worldX), y, safeCoordinate(worldZ));
        }

        public double worldToScreenX(double worldX) {
            return left + width / 2.0D + (worldX - center.x()) / blocksPerPixel;
        }

        public double worldToScreenZ(double worldZ) {
            return top + height / 2.0D + (worldZ - center.z()) / blocksPerPixel;
        }

        public boolean contains(double screenX, double screenY) {
            return screenX >= left && screenX < left + width && screenY >= top && screenY < top + height;
        }

        private static int safeCoordinate(long coordinate) {
            if (coordinate < Integer.MIN_VALUE) return Integer.MIN_VALUE;
            if (coordinate > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            return (int) coordinate;
        }
    }

    private static final class TerrainCache {
        private DynamicTexture texture;
        private PendingBuild pending;
        private Level level;
        private String dimension = "";
        private MapStyle style = MapStyle.TERRAIN;
        private int centerX;
        private int centerZ;
        private int sampleStep;
        private int sourceWidth;
        private int sourceHeight;
        private int textureWidth;
        private int textureHeight;
        private long refreshedAt;
        private long retryBuildAt;
        private boolean hadUnloadedSamples;
    }

    private static final class PendingBuild {
        private final Level level;
        private final String dimension;
        private final MapStyle style;
        private final int centerX;
        private final int centerZ;
        private final int sampleStep;
        private final int sourceWidth;
        private final int sourceHeight;
        private final int textureWidth;
        private final int textureHeight;
        private final int originX;
        private final int originZ;
        private final int[] colors;
        private final int[] heights;
        private final int[] waterDepths;
        private final BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        private int nextSample;
        private int unloadedSamples;

        private PendingBuild(
                Level level,
                String dimension,
                MapStyle style,
                int centerX,
                int centerZ,
                int sampleStep,
                int sourceWidth,
                int sourceHeight
        ) {
            this.level = level;
            this.dimension = dimension;
            this.style = style;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.sampleStep = sampleStep;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.textureWidth = sourceWidth + CACHE_MARGIN * 2;
            this.textureHeight = sourceHeight + CACHE_MARGIN * 2;
            this.originX = centerX - (textureWidth - 1) * sampleStep / 2;
            this.originZ = centerZ - (textureHeight - 1) * sampleStep / 2;
            this.colors = new int[textureWidth * textureHeight];
            this.heights = new int[colors.length];
            this.waterDepths = new int[colors.length];
        }

        private boolean matches(
                Level wantedLevel,
                String wantedDimension,
                MapStyle wantedStyle,
                int wantedSampleStep,
                int wantedSourceWidth,
                int wantedSourceHeight
        ) {
            return level == wantedLevel
                    && Objects.equals(dimension, wantedDimension)
                    && style == wantedStyle
                    && sampleStep == wantedSampleStep
                    && sourceWidth == wantedSourceWidth
                    && sourceHeight == wantedSourceHeight;
        }

        private boolean covers(int wantedCenterX, int wantedCenterZ) {
            double sourceX = CACHE_MARGIN + ((double) wantedCenterX - centerX) / sampleStep;
            double sourceY = CACHE_MARGIN + ((double) wantedCenterZ - centerZ) / sampleStep;
            return sourceX >= 0.0D && sourceY >= 0.0D
                    && sourceX + sourceWidth <= textureWidth
                    && sourceY + sourceHeight <= textureHeight;
        }
    }

    private record CacheView(float sourceX, float sourceY, int sourceWidth, int sourceHeight) { }
    private record TerrainSample(int color, int height, int waterDepth) { }
    private record MarkerPoint(int x, int y, boolean outside) { }
}

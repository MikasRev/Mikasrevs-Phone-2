package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Local screenshot-backed MPhone gallery for Forge 1.20.1. */
public final class PhoneGalleryStore {
    private static final int MAX_CACHED_TEXTURES = 24;
    private static final List<Path> PHOTOS = new ArrayList<>();
    private static final Map<Path, GalleryTexture> TEXTURES = new LinkedHashMap<>(32, 0.75F, true);
    private static final Map<Path, Long> TEXTURE_FILE_TIMES = new HashMap<>();
    private static final Map<Path, Long> TEXTURE_CHECK_TIMES = new HashMap<>();
    private static final Map<ResourceLocation, DynamicTexture> LIVE_TEXTURES = new HashMap<>();
    private static final AtomicLong TEXTURE_SEQUENCE = new AtomicLong();
    private static long lastRefreshAt;

    private PhoneGalleryStore() { }

    public static synchronized void onPhotoCaptured(Minecraft minecraft) { refresh(minecraft, true); }

    public static synchronized List<Path> photos(Minecraft minecraft) {
        refresh(minecraft, false);
        return List.copyOf(PHOTOS);
    }

    public static synchronized Path latestPhoto(Minecraft minecraft) {
        List<Path> photos = photos(minecraft);
        return photos.isEmpty() ? null : photos.get(0);
    }

    public static synchronized GalleryTexture textureFor(Minecraft minecraft, Path path) {
        if (minecraft == null || path == null) return null;
        Path normalized;
        try {
            normalized = path.toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
        long now = System.currentTimeMillis();
        GalleryTexture cached = TEXTURES.get(normalized);
        Long lastCheck = TEXTURE_CHECK_TIMES.get(normalized);
        if (cached != null && lastCheck != null && now - lastCheck < 1000L) return cached;
        TEXTURE_CHECK_TIMES.put(normalized, now);
        if (!Files.isRegularFile(normalized)) {
            if (cached != null) release(cached.location());
            TEXTURES.remove(normalized);
            TEXTURE_FILE_TIMES.remove(normalized);
            return null;
        }
        long modified = lastModified(normalized);
        Long oldModified = TEXTURE_FILE_TIMES.get(normalized);
        if (cached != null && oldModified != null && oldModified == modified) return cached;
        if (cached != null) {
            release(cached.location());
            TEXTURES.remove(normalized);
            TEXTURE_FILE_TIMES.remove(normalized);
        }
        try (InputStream input = Files.newInputStream(normalized)) {
            NativeImage image = NativeImage.read(input);
            int width = Math.max(1, image.getWidth());
            int height = Math.max(1, image.getHeight());
            String name = "gallery/runtime_" + Integer.toUnsignedString(normalized.toString().hashCode(), 16)
                    + "_" + Long.toUnsignedString(TEXTURE_SEQUENCE.incrementAndGet(), 36);
            ResourceLocation location = new ResourceLocation(NewPhone.MOD_ID, name);
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, false);
            minecraft.getTextureManager().register(location, texture);
            LIVE_TEXTURES.put(location, texture);
            GalleryTexture result = new GalleryTexture(location, width, height);
            TEXTURES.put(normalized, result);
            TEXTURE_FILE_TIMES.put(normalized, modified);
            while (TEXTURES.size() > MAX_CACHED_TEXTURES) {
                Path oldest = TEXTURES.keySet().iterator().next();
                release(TEXTURES.remove(oldest).location());
                TEXTURE_FILE_TIMES.remove(oldest);
                TEXTURE_CHECK_TIMES.remove(oldest);
            }
            return result;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static void refresh(Minecraft minecraft, boolean force) {
        if (minecraft == null) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastRefreshAt < 900L) return;
        lastRefreshAt = now;
        PHOTOS.clear();
        Path screenshotDir = minecraft.gameDirectory.toPath().resolve("screenshots");
        if (!Files.isDirectory(screenshotDir)) return;
        try (var stream = Files.list(screenshotDir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".png"))
                    .sorted(Comparator.comparingLong(PhoneGalleryStore::lastModified).reversed())
                    .forEach(PHOTOS::add);
        } catch (IOException ignored) { }
    }

    private static long lastModified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0L; }
    }

    public static synchronized void resetSession() {
        for (ResourceLocation location : new ArrayList<>(LIVE_TEXTURES.keySet())) release(location);
        PHOTOS.clear();
        TEXTURES.clear();
        TEXTURE_FILE_TIMES.clear();
        TEXTURE_CHECK_TIMES.clear();
        lastRefreshAt = 0L;
    }

    private static void release(ResourceLocation location) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && location != null) {
            try { minecraft.getTextureManager().release(location); } catch (RuntimeException ignored) { }
        }
        LIVE_TEXTURES.remove(location);
    }

    public record GalleryTexture(ResourceLocation location, int width, int height) { }
}

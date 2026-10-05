package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneGpsMapRenderer;
import com.mikasrevs.newphone.client.gui.PhoneGpsState;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

/** Full-surface, coordinate-driven GPS for the MPhone. */
public final class ZPhoneGpsScreen extends AbstractZPhoneScreen {
    private static final int MAP_LEFT = 1;
    private static final int MAP_TOP = 1;
    private static final int MAP_RIGHT = 299;
    private static final int MAP_BOTTOM = 609;

    private final StringBuilder coordinateInput = new StringBuilder();
    private PhoneGpsState.Location viewCenter;
    private PhoneGpsState.Location selectedLocation;
    private PhoneGpsMapRenderer.Projection projection;
    private PhoneGpsMapRenderer.MapStyle mapStyle = PhoneGpsMapRenderer.MapStyle.TERRAIN;
    private int zoomLevel;
    private boolean followPlayer = true;
    private boolean searchFocused;
    private boolean draggingMap;
    private boolean dragMoved;
    private double dragStartX;
    private double dragStartY;
    private PhoneGpsState.Location dragStartCenter;
    private String toast = "";
    private long toastUntil;
    private long inputErrorUntil;

    public ZPhoneGpsScreen() {
        super(Component.literal(PhoneSettingsStore.text("Haritalar", "Maps")));
    }

    @Override
    protected void init() {
        super.init();
        if (viewCenter != null) return;
        PhoneGpsState.Location target = PhoneGpsState.targetLocation();
        if (target != null) {
            viewCenter = target;
            selectedLocation = target;
            followPlayer = false;
        } else {
            viewCenter = PhoneGpsState.currentLocation(minecraft);
            followPlayer = true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        PhoneGpsState.Location current = PhoneGpsState.currentLocation(minecraft);
        if (viewCenter == null) viewCenter = current;
        if (followPlayer && !draggingMap) viewCenter = current;

        int mapLeft = x(MAP_LEFT);
        int mapTop = y(MAP_TOP);
        int mapWidth = x(MAP_RIGHT) - mapLeft;
        int mapHeight = y(MAP_BOTTOM) - mapTop;
        projection = PhoneGpsMapRenderer.drawMap(
                graphics, minecraft, mapLeft, mapTop, mapWidth, mapHeight,
                viewCenter, zoomLevel, selectedLocation, true, mapStyle, draggingMap
        );

        // Keep phone chrome readable without obscuring the full-bleed terrain.
        graphics.fill(x(1), y(1), x(299), y(45), 0x48040B12);
        graphics.fill(x(1), y(580), x(299), y(609), 0x30040B12);
        drawStatusBar(graphics);
        drawSearchBar(graphics, mouseX, mouseY);
        drawMapControls(graphics, mouseX, mouseY);
        drawLocationCard(graphics, current, mouseX, mouseY);
        drawToast(graphics);
        drawHomeIndicator(graphics);
        drawFrame(graphics);
        drawHoveredTooltip(graphics, mouseX, mouseY);
    }

    private void drawSearchBar(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean error = System.currentTimeMillis() < inputErrorUntil;
        int border = error ? 0xFFFF4D45 : 0xFF2F80ED;
        drawRoundedRect(graphics, x(14), y(55), x(286), y(99), size(18), 0x42000000);
        if (searchFocused || error) {
            drawRoundedRect(graphics, x(12), y(51), x(288), y(96), size(19), border);
            drawRoundedRect(graphics, x(14), y(53), x(286), y(94), size(17), 0xFFF8FAFC);
        } else {
            drawRoundedRect(graphics, x(13), y(52), x(287), y(95), size(18), 0xFFF8FAFC);
        }

        drawMagnifier(graphics, x(31), y(73), 0xFF263443);
        String shown = coordinateInput.length() == 0
                ? text("X, Z veya X, Y, Z gir", "Enter X, Z or X, Y, Z")
                : fitTail(coordinateInput.toString(), x(236) - x(49), size(9));
        int textColor = coordinateInput.length() == 0 ? 0xFF7A8794 : 0xFF172331;
        PhoneModernText.drawColoredNoShadow(graphics, shown, x(49), y(65), size(9), textColor);
        if (searchFocused && isCaretVisible()) {
            int width = coordinateInput.length() == 0 ? 0 : PhoneModernText.width(shown, size(9));
            int caretX = Math.min(x(238), x(49) + width + size(1));
            graphics.fill(caretX, y(64), caretX + Math.max(1, size(1)), y(82), border);
        }

        int actionColor = inside(mouseX, mouseY, 246, 56, 281, 91) ? 0xFF176FE0 : 0xFF2F80ED;
        fillCircle(graphics, x(264), y(73), size(15), actionColor);
        drawCrosshair(graphics, x(264), y(73), 0xFFFFFFFF, actionColor);
    }

    private void drawMapControls(GuiGraphics graphics, int mouseX, int mouseY) {
        drawFloatingCircle(graphics, 268, 130, inside(mouseX, mouseY, 247, 109, 289, 151), 0xFFF8FAFC);
        drawCompass(graphics, x(268), y(130));

        drawFloatingCircle(graphics, 268, 181, inside(mouseX, mouseY, 247, 160, 289, 202), 0xFFF8FAFC);
        drawLayers(graphics, x(268), y(181), mapStyle == PhoneGpsMapRenderer.MapStyle.CONTRAST);

        drawRoundedRect(graphics, x(247), y(212), x(289), y(294), size(20), 0x42000000);
        drawRoundedRect(graphics, x(247), y(209), x(289), y(291), size(20), 0xFFF8FAFC);
        boolean canZoomIn = zoomLevel > 0;
        boolean canZoomOut = zoomLevel < 3;
        if (canZoomIn && inside(mouseX, mouseY, 247, 209, 289, 250)) {
            drawRoundedRect(graphics, x(249), y(211), x(287), y(249), size(18), 0xFFE9EEF4);
        }
        if (canZoomOut && inside(mouseX, mouseY, 247, 250, 289, 291)) {
            drawRoundedRect(graphics, x(249), y(251), x(287), y(289), size(18), 0xFFE9EEF4);
        }
        graphics.fill(x(255), y(250), x(281), y(251), 0xFFD7DEE6);
        PhoneModernText.drawCenteredColoredNoShadow(
                graphics, "+", x(268), y(219), size(17), canZoomIn ? 0xFF263443 : 0xFFAAB4BE
        );
        PhoneModernText.drawCenteredColoredNoShadow(
                graphics, "-", x(268), y(257), size(18), canZoomOut ? 0xFF263443 : 0xFFAAB4BE
        );

        boolean recenterHover = inside(mouseX, mouseY, 245, 305, 291, 351);
        drawFloatingCircle(graphics, 268, 328, recenterHover, recenterHover ? 0xFF176FE0 : 0xFF2F80ED);
        drawCrosshair(graphics, x(268), y(328), 0xFFFFFFFF, recenterHover ? 0xFF176FE0 : 0xFF2F80ED);

        if (PhoneGpsState.hasTargetLocation()) {
            boolean navigateHover = inside(mouseX, mouseY, 245, 362, 291, 408);
            drawFloatingCircle(graphics, 268, 385, navigateHover, navigateHover ? 0xFF176FE0 : 0xFF2F80ED);
            drawNavigationArrow(graphics, x(268), y(385), 0xFFFFFFFF);
        }
    }

    private void drawLocationCard(
            GuiGraphics graphics, PhoneGpsState.Location current, int mouseX, int mouseY
    ) {
        PhoneGpsState.Location display = displayLocation(current);
        PhoneGpsState.Location target = PhoneGpsState.targetLocation();
        boolean isTarget = sameLocation(display, target);
        boolean selected = selectedLocation != null;

        drawRoundedRect(graphics, x(14), y(501), x(286), y(580), size(15), 0x44000000);
        drawRoundedRect(graphics, x(13), y(497), x(287), y(576), size(14), 0xF7F8FAFC);
        String eyebrow = isTarget
                ? text("WAYPOINT", "WAYPOINT")
                : selected ? text("SE\u00c7\u0130L\u0130 KONUM", "SELECTED LOCATION")
                : text("MEVCUT KONUM", "CURRENT POSITION");
        int accent = isTarget ? 0xFFE33D37 : 0xFF2478E5;
        PhoneModernText.drawColoredNoShadow(graphics, eyebrow, x(27), y(506), size(7), accent);
        PhoneModernText.drawColoredNoShadow(
                graphics, fitText(display.compactCoordinates(), x(241) - x(27), size(9)),
                x(27), y(522), size(9), 0xFF152230
        );

        String detail = locationDetail(current, display);
        PhoneModernText.drawColoredNoShadow(
                graphics, fitText(detail, x(180) - x(27), size(7)),
                x(27), y(547), size(7), 0xFF657381
        );

        boolean copyHover = inside(mouseX, mouseY, 246, 504, 279, 537);
        fillCircle(graphics, x(263), y(520), size(12), copyHover ? 0xFFE3E9EF : 0xFFEDF1F5);
        drawCopyIcon(graphics, x(263), y(520), 0xFF384654);

        boolean actionHover = inside(mouseX, mouseY, 187, 541, 276, 569);
        int actionColor = isTarget ? (actionHover ? 0xFFC92F2A : 0xFFE33D37)
                : (actionHover ? 0xFF176FE0 : 0xFF2F80ED);
        drawRoundedRect(graphics, x(187), y(541), x(276), y(569), size(12), actionColor);
        drawWaypointIcon(graphics, x(201), y(555), 0xFFFFFFFF, actionColor);
        PhoneModernText.drawCenteredColoredNoShadow(
                graphics,
                isTarget ? text("Kald\u0131r", "Remove") : text("Pin Ekle", "Set pin"),
                x(237), y(549), size(7), 0xFFFFFFFF
        );
    }

    private void drawToast(GuiGraphics graphics) {
        if (toast.isBlank() || System.currentTimeMillis() >= toastUntil) return;
        int textWidth = PhoneModernText.width(toast, size(7));
        int half = Math.min(size(116), textWidth / 2 + size(13));
        drawRoundedRect(graphics, x(150) - half, y(460), x(150) + half, y(489), size(12), 0xE914202C);
        PhoneModernText.drawCenteredColored(graphics, toast, x(150), y(469), size(7), 0xFFFFFFFF);
    }

    private void drawHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        String tooltip = null;
        if (inside(mouseX, mouseY, 247, 109, 289, 151)) {
            tooltip = text("Kuzey yukar\u0131", "North up");
        } else if (inside(mouseX, mouseY, 247, 160, 289, 202)) {
            tooltip = mapStyle == PhoneGpsMapRenderer.MapStyle.TERRAIN
                    ? text("Y\u00fcksek kontrast", "High contrast")
                    : text("Arazi g\u00f6r\u00fcn\u00fcm\u00fc", "Terrain view");
        } else if (inside(mouseX, mouseY, 247, 209, 289, 250)) {
            tooltip = text("Yak\u0131nla\u015ft\u0131r", "Zoom in");
        } else if (inside(mouseX, mouseY, 247, 250, 289, 291)) {
            tooltip = text("Uzakla\u015ft\u0131r", "Zoom out");
        } else if (inside(mouseX, mouseY, 245, 305, 291, 351)) {
            tooltip = text("Konumuma d\u00f6n", "My location");
        } else if (PhoneGpsState.hasTargetLocation() && inside(mouseX, mouseY, 245, 362, 291, 408)) {
            tooltip = text("Navigasyonu ba\u015flat", "Start navigation");
        } else if (inside(mouseX, mouseY, 246, 504, 279, 537)) {
            tooltip = text("Koordinat\u0131 kopyala", "Copy coordinates");
        }
        if (tooltip != null) graphics.renderTooltip(font, Component.literal(tooltip), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (inside(mouseX, mouseY, 14, 53, 244, 94)) {
            searchFocused = true;
            return true;
        }
        if (inside(mouseX, mouseY, 244, 51, 287, 96)) {
            submitCoordinates();
            return true;
        }
        searchFocused = false;

        if (inside(mouseX, mouseY, 247, 109, 289, 151)) {
            showToast(text("Harita kuzeye hizal\u0131", "Map aligned north"));
            return true;
        }
        if (inside(mouseX, mouseY, 247, 160, 289, 202)) {
            mapStyle = mapStyle == PhoneGpsMapRenderer.MapStyle.TERRAIN
                    ? PhoneGpsMapRenderer.MapStyle.CONTRAST
                    : PhoneGpsMapRenderer.MapStyle.TERRAIN;
            showToast(mapStyle == PhoneGpsMapRenderer.MapStyle.TERRAIN
                    ? text("Arazi g\u00f6r\u00fcn\u00fcm\u00fc", "Terrain view")
                    : text("Y\u00fcksek kontrast", "High contrast"));
            return true;
        }
        if (inside(mouseX, mouseY, 247, 209, 289, 250)) {
            changeZoom(-1);
            return true;
        }
        if (inside(mouseX, mouseY, 247, 250, 289, 291)) {
            changeZoom(1);
            return true;
        }
        if (inside(mouseX, mouseY, 245, 305, 291, 351)) {
            recenterOnPlayer();
            return true;
        }
        if (PhoneGpsState.hasTargetLocation() && inside(mouseX, mouseY, 245, 362, 291, 408)) {
            PhoneGpsState.enableMinimap(minecraft);
            return true;
        }
        if (inside(mouseX, mouseY, 246, 504, 279, 537)) {
            copyDisplayedLocation();
            return true;
        }
        if (inside(mouseX, mouseY, 187, 541, 276, 569)) {
            toggleWaypoint();
            return true;
        }

        if (projection != null && inside(mouseX, mouseY, 4, 100, 296, 493)) {
            draggingMap = true;
            dragMoved = false;
            dragStartX = mouseX;
            dragStartY = mouseY;
            dragStartCenter = viewCenter;
            followPlayer = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingMap && projection != null && dragStartCenter != null) {
            double totalX = mouseX - dragStartX;
            double totalY = mouseY - dragStartY;
            dragMoved |= Math.abs(totalX) > size(3) || Math.abs(totalY) > size(3);
            long worldX = Math.round(dragStartCenter.x() - totalX * projection.blocksPerPixel());
            long worldZ = Math.round(dragStartCenter.z() - totalY * projection.blocksPerPixel());
            viewCenter = new PhoneGpsState.Location(
                    dragStartCenter.dimension(), safeCoordinate(worldX), dragStartCenter.y(), safeCoordinate(worldZ)
            );
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingMap) {
            draggingMap = false;
            if (!dragMoved && projection != null && projection.contains(mouseX, mouseY)) {
                PhoneGpsState.Location raw = projection.screenToLocation(
                        mouseX, mouseY, PhoneGpsState.currentLocation(minecraft).y()
                );
                selectedLocation = withSurfaceY(raw);
                followPlayer = false;
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0.0D && inside(mouseX, mouseY, MAP_LEFT, MAP_TOP, MAP_RIGHT, MAP_BOTTOM)) {
            changeZoom(delta > 0.0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (searchFocused && character >= 32 && character != 127 && coordinateInput.length() < 48) {
            coordinateInput.append(character);
            inputErrorUntil = 0L;
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocused) {
                searchFocused = false;
                return true;
            }
            PhoneNavigationState.backOrHome(minecraft);
            return true;
        }
        if (searchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (coordinateInput.length() > 0) coordinateInput.deleteCharAt(coordinateInput.length() - 1);
            inputErrorUntil = 0L;
            return true;
        }
        if (searchFocused && keyCode == GLFW.GLFW_KEY_A && hasControlDown()) {
            coordinateInput.setLength(0);
            return true;
        }
        if (searchFocused && keyCode == GLFW.GLFW_KEY_V && hasControlDown() && minecraft != null) {
            appendClipboard(minecraft.keyboardHandler.getClipboard());
            return true;
        }
        if (searchFocused && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            submitCoordinates();
            return true;
        }
        if (!searchFocused && keyCode == GLFW.GLFW_KEY_X) {
            PhoneGpsState.enableMinimap(minecraft);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submitCoordinates() {
        Optional<PhoneGpsState.Location> parsed = PhoneGpsState.parseCoordinates(
                coordinateInput.toString(), PhoneGpsState.currentLocation(minecraft)
        );
        if (parsed.isEmpty()) {
            inputErrorUntil = System.currentTimeMillis() + 1_600L;
            showToast(text("Ge\u00e7ersiz koordinat", "Invalid coordinates"));
            searchFocused = true;
            return;
        }
        selectedLocation = withSurfaceY(parsed.get());
        viewCenter = selectedLocation;
        followPlayer = false;
        searchFocused = false;
        coordinateInput.setLength(0);
        coordinateInput.append(selectedLocation.x()).append(", ")
                .append(selectedLocation.y()).append(", ").append(selectedLocation.z());
        showToast(text("Koordinat se\u00e7ildi", "Coordinate selected"));
    }

    private void toggleWaypoint() {
        PhoneGpsState.Location current = PhoneGpsState.currentLocation(minecraft);
        PhoneGpsState.Location display = displayLocation(current);
        if (sameLocation(display, PhoneGpsState.targetLocation())) {
            PhoneGpsState.clearTargetLocation();
            showToast(text("Waypoint kald\u0131r\u0131ld\u0131", "Waypoint removed"));
        } else {
            PhoneGpsState.setTargetLocation(display);
            selectedLocation = display;
            showToast(text("Waypoint eklendi", "Waypoint added"));
        }
    }

    private void recenterOnPlayer() {
        viewCenter = PhoneGpsState.currentLocation(minecraft);
        selectedLocation = null;
        followPlayer = true;
        showToast(text("Mevcut konum", "Current position"));
    }

    private void copyDisplayedLocation() {
        if (minecraft == null) return;
        minecraft.keyboardHandler.setClipboard(
                PhoneGpsState.clipboardText(displayLocation(PhoneGpsState.currentLocation(minecraft)))
        );
        showToast(text("Konum kopyaland\u0131", "Location copied"));
    }

    private void changeZoom(int amount) {
        int next = Mth.clamp(zoomLevel + amount, 0, 3);
        if (next == zoomLevel) {
            showToast(amount < 0 ? text("En yak\u0131n g\u00f6r\u00fcn\u00fcm", "Closest view")
                    : text("En geni\u015f g\u00f6r\u00fcn\u00fcm", "Widest view"));
            return;
        }
        zoomLevel = next;
        showToast(text("Yak\u0131nla\u015ft\u0131rma", "Zoom") + " " + (zoomLevel + 1) + "/4");
    }

    private PhoneGpsState.Location displayLocation(PhoneGpsState.Location current) {
        return selectedLocation == null ? current : selectedLocation;
    }

    private PhoneGpsState.Location withSurfaceY(PhoneGpsState.Location location) {
        if (minecraft == null || minecraft.level == null || location == null) return location;
        Level level = minecraft.level;
        if (!level.dimension().location().toString().equals(location.dimension())) return location;
        BlockPos pos = new BlockPos(location.x(), level.getMinBuildHeight(), location.z());
        if (!level.hasChunkAt(pos)) return location;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, location.x(), location.z());
        return new PhoneGpsState.Location(location.dimension(), location.x(), surface, location.z());
    }

    private String locationDetail(PhoneGpsState.Location current, PhoneGpsState.Location display) {
        String biome = biomeName(display);
        double distance = PhoneGpsState.horizontalDistance(current, display);
        if (!Double.isFinite(distance) || distance < 1.0D) return biome;
        return biome + "  |  " + Math.round(distance) + " " + text("blok", "blocks");
    }

    private String biomeName(PhoneGpsState.Location location) {
        if (minecraft == null || minecraft.level == null || location == null) return "";
        Level level = minecraft.level;
        if (!level.dimension().location().toString().equals(location.dimension())) {
            return location.friendlyDimension();
        }
        BlockPos pos = new BlockPos(location.x(), level.getMinBuildHeight(), location.z());
        if (!level.hasChunkAt(pos)) return text("Harita verisi y\u00fcklenmedi", "Map data unavailable");
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, location.x(), location.z()) - 1;
        pos = new BlockPos(location.x(), Mth.clamp(surfaceY,
                level.getMinBuildHeight(), level.getMaxBuildHeight() - 1), location.z());
        String path = level.getBiome(pos).unwrapKey()
                .map(key -> key.location().getPath())
                .orElse("unknown");
        return titleCase(path.replace('_', ' '));
    }

    private void appendClipboard(String clipboard) {
        if (clipboard == null) return;
        for (int i = 0; i < clipboard.length() && coordinateInput.length() < 48; i++) {
            char character = clipboard.charAt(i);
            if (character >= 32 && character != 127) coordinateInput.append(character);
        }
        inputErrorUntil = 0L;
    }

    private void showToast(String message) {
        toast = message == null ? "" : message;
        toastUntil = System.currentTimeMillis() + 1_650L;
    }

    private String fitTail(String value, int maxWidth, int textSize) {
        String result = value == null ? "" : value;
        while (result.length() > 1 && PhoneModernText.width(result, textSize) > maxWidth) {
            result = result.substring(1);
        }
        return result;
    }

    private String fitText(String value, int maxWidth, int textSize) {
        String result = value == null ? "" : value;
        if (PhoneModernText.width(result, textSize) <= maxWidth) return result;
        while (result.length() > 3 && PhoneModernText.width(result + "...", textSize) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private static String titleCase(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalize = true;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return result.toString();
    }

    private static boolean sameLocation(PhoneGpsState.Location first, PhoneGpsState.Location second) {
        return first != null && second != null
                && first.x() == second.x() && first.z() == second.z()
                && first.dimension().equals(second.dimension());
    }

    private static int safeCoordinate(long value) {
        return (int) Math.max(-30_000_000L, Math.min(30_000_000L, value));
    }

    private String text(String turkish, String english) {
        return PhoneSettingsStore.text(turkish, english);
    }

    private void drawFloatingCircle(
            GuiGraphics graphics, int designX, int designY, boolean hovered, int background
    ) {
        fillCircle(graphics, x(designX + 1), y(designY + 3), size(20), 0x3D000000);
        fillCircle(graphics, x(designX), y(designY), size(20), background);
        if (hovered && background == 0xFFF8FAFC) {
            fillCircle(graphics, x(designX), y(designY), size(18), 0xFFE9EEF4);
        }
    }

    private void drawMagnifier(GuiGraphics graphics, int centerX, int centerY, int color) {
        fillCircle(graphics, centerX - size(1), centerY - size(1), size(6), color);
        fillCircle(graphics, centerX - size(1), centerY - size(1), size(4), 0xFFF8FAFC);
        drawLine(graphics, centerX + size(3), centerY + size(3),
                centerX + size(8), centerY + size(8), Math.max(1, size(2)), color);
    }

    private void drawCrosshair(GuiGraphics graphics, int centerX, int centerY, int color, int background) {
        fillCircle(graphics, centerX, centerY, size(7), color);
        fillCircle(graphics, centerX, centerY, size(4), background);
        fillCircle(graphics, centerX, centerY, size(2), color);
        graphics.fill(centerX - size(1), centerY - size(10), centerX + size(1), centerY - size(5), color);
        graphics.fill(centerX - size(1), centerY + size(5), centerX + size(1), centerY + size(10), color);
        graphics.fill(centerX - size(10), centerY - size(1), centerX - size(5), centerY + size(1), color);
        graphics.fill(centerX + size(5), centerY - size(1), centerX + size(10), centerY + size(1), color);
    }

    private void drawCompass(GuiGraphics graphics, int centerX, int centerY) {
        PhoneModernText.drawCenteredColoredNoShadow(
                graphics, "N", centerX, centerY - size(16), size(6), 0xFFE33D37
        );
        drawLine(graphics, centerX, centerY - size(11), centerX, centerY,
                Math.max(2, size(3)), 0xFFE33D37);
        drawLine(graphics, centerX, centerY, centerX, centerY + size(11),
                Math.max(2, size(3)), 0xFF263443);
        fillCircle(graphics, centerX, centerY, size(2), 0xFFFFFFFF);
    }

    private void drawLayers(GuiGraphics graphics, int centerX, int centerY, boolean active) {
        int color = active ? 0xFF2F80ED : 0xFF263443;
        for (int layer = 0; layer < 3; layer++) {
            int yOffset = size(layer * 5 - 6);
            drawLine(graphics, centerX - size(9), centerY + yOffset,
                    centerX, centerY + yOffset + size(5), size(2), color);
            drawLine(graphics, centerX, centerY + yOffset + size(5),
                    centerX + size(9), centerY + yOffset, size(2), color);
        }
    }

    private void drawNavigationArrow(GuiGraphics graphics, int centerX, int centerY, int color) {
        for (int row = 0; row < size(17); row++) {
            int half = Math.max(1, row / 3);
            graphics.fill(centerX - half, centerY - size(10) + row,
                    centerX + half + 1, centerY - size(9) + row, color);
        }
        graphics.fill(centerX - size(2), centerY + size(3), centerX + size(2), centerY + size(11), color);
    }

    private void drawCopyIcon(GuiGraphics graphics, int centerX, int centerY, int color) {
        drawRectOutline(graphics, centerX - size(6), centerY - size(7),
                centerX + size(4), centerY + size(5), color);
        drawRectOutline(graphics, centerX - size(2), centerY - size(3),
                centerX + size(8), centerY + size(9), color);
    }

    private void drawWaypointIcon(
            GuiGraphics graphics, int centerX, int centerY, int color, int background
    ) {
        fillCircle(graphics, centerX, centerY - size(3), size(5), color);
        fillCircle(graphics, centerX, centerY - size(3), size(2), background);
        graphics.fill(centerX - size(1), centerY + size(1), centerX + size(2), centerY + size(8), color);
    }

    private void drawRectOutline(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        int thickness = Math.max(1, size(1));
        graphics.fill(left, top, right, top + thickness, color);
        graphics.fill(left, bottom - thickness, right, bottom, color);
        graphics.fill(left, top, left + thickness, bottom, color);
        graphics.fill(right - thickness, top, right, bottom, color);
    }

    private static void drawLine(
            GuiGraphics graphics, int x1, int y1, int x2, int y2, int thickness, int color
    ) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            graphics.fill(x1, y1, x1 + thickness, y1 + thickness, color);
            return;
        }
        int radius = Math.max(0, thickness / 2);
        for (int step = 0; step <= steps; step++) {
            int x = x1 + (x2 - x1) * step / steps;
            int y = y1 + (y2 - y1) * step / steps;
            graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
        }
    }
}

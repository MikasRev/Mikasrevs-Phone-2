package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhoneWallpaperCatalog;
import com.mikasrevs.newphone.client.gui.PhoneWallpaperRenderer;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore.WallpaperTarget;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Wallpaper picker for bundled artwork and locally captured gallery photos. */
public final class ZPhoneWallpaperScreen extends AbstractZPhoneScreen {
    private static final int GRID_LEFT = 22;
    private static final int GRID_TOP = 187;
    private static final int CELL_WIDTH = 78;
    private static final int COLUMN_GAP = 11;
    private static final int PHOTO_ROW_STEP = 87;
    private static final int PHOTO_VISIBLE_ROWS = 4;

    private enum Tab {
        BUILT_IN,
        PHOTOS
    }

    private final Path initialPhoto;
    private WallpaperTarget target = WallpaperTarget.BOTH;
    private Tab tab;
    private int photoScrollRow;
    private boolean initialPhotoPositioned;
    private String notice = "";
    private long noticeUntil;

    public ZPhoneWallpaperScreen() {
        this(null);
    }

    public ZPhoneWallpaperScreen(Path initialPhoto) {
        super(Component.literal(PhoneSettingsStore.text("Duvar Ka\u011f\u0131d\u0131", "Wallpaper")));
        this.initialPhoto = normalize(initialPhoto);
        this.tab = this.initialPhoto == null ? Tab.BUILT_IN : Tab.PHOTOS;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawWallpaper(graphics);
        drawRoundedRect(graphics, x(3), y(40), x(297), y(606), size(22), 0xF4070A0F);
        drawStatusBar(graphics);
        drawBackButton(graphics);
        PhoneModernText.drawCenteredColoredFitted(
                graphics,
                PhoneSettingsStore.text("Duvar Ka\u011f\u0131d\u0131", "Wallpaper"),
                x(150), y(58), size(11), size(142), 0xFFFFFFFF
        );

        drawTargets(graphics, mouseX, mouseY);
        drawTabs(graphics, mouseX, mouseY);
        if (tab == Tab.BUILT_IN) drawBuiltInGrid(graphics, mouseX, mouseY);
        else drawPhotoGrid(graphics, mouseX, mouseY);
        drawNotice(graphics);
        drawHomeIndicator(graphics);
        drawFrame(graphics);
    }

    private void drawTargets(GuiGraphics graphics, int mouseX, int mouseY) {
        WallpaperTarget[] targets = {WallpaperTarget.HOME, WallpaperTarget.LOCK, WallpaperTarget.BOTH};
        String[] labels = {
                PhoneSettingsStore.text("Ana Ekran", "Home"),
                PhoneSettingsStore.text("Kilit", "Lock"),
                PhoneSettingsStore.text("Her \u0130kisi", "Both")
        };
        for (int i = 0; i < targets.length; i++) {
            int left = 22 + i * 87;
            boolean selected = target == targets[i];
            boolean hover = inside(mouseX, mouseY, left, 91, left + 82, 123);
            int color = selected
                    ? PhoneSettingsStore.accentColor()
                    : hover ? 0xFF273443 : 0xFF18212C;
            drawRoundedRect(graphics, x(left), y(91), x(left + 82), y(123), size(9), color);
            PhoneModernText.drawCenteredColoredFitted(
                    graphics, labels[i], x(left + 41), y(101), size(7), size(70), 0xFFFFFFFF
            );
        }
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        drawTab(
                graphics, mouseX, mouseY, Tab.BUILT_IN, 22, 148,
                PhoneSettingsStore.text("Haz\u0131rlar", "Built-in")
        );
        drawTab(
                graphics, mouseX, mouseY, Tab.PHOTOS, 152, 278,
                PhoneSettingsStore.text("Foto\u011fraflar\u0131m", "My Photos")
        );
    }

    private void drawTab(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            Tab candidate,
            int left,
            int right,
            String label
    ) {
        boolean selected = tab == candidate;
        boolean hover = inside(mouseX, mouseY, left, 137, right, 171);
        drawRoundedRect(
                graphics,
                x(left), y(137), x(right), y(171), size(9),
                selected ? 0xFF253345 : hover ? 0xFF1D2835 : 0xFF121922
        );
        PhoneModernText.drawCenteredColoredFitted(
                graphics,
                label,
                x((left + right) / 2), y(147), size(7), size(right - left - 18),
                selected ? 0xFFFFFFFF : 0xFF8D99A7
        );
        if (selected) {
            graphics.fill(x(left + 18), y(168), x(right - 18), y(170), PhoneSettingsStore.accentColor());
        }
    }

    private void drawBuiltInGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int count = Math.min(PhoneWallpaperCatalog.count(), PhoneSettingsStore.wallpaperCount());
        for (int index = 0; index < count; index++) {
            int column = index % 3;
            int row = index / 3;
            int left = GRID_LEFT + column * (CELL_WIDTH + COLUMN_GAP);
            int top = GRID_TOP + row * 116;
            boolean selected = PhoneSettingsStore.isBuiltInWallpaperSelected(index, target);
            boolean hover = inside(mouseX, mouseY, left, top, left + CELL_WIDTH, top + 110);
            drawRoundedRect(
                    graphics,
                    x(left), y(top), x(left + CELL_WIDTH), y(top + 110), size(7),
                    selected ? PhoneSettingsStore.accentColor() : hover ? 0xFFFFD45A : 0xFF28323E
            );
            drawRoundedRect(
                    graphics,
                    x(left + 2), y(top + 2), x(left + CELL_WIDTH - 2), y(top + 108), size(5), 0xFF111821
            );
            PhoneWallpaperRenderer.blitCover(
                    graphics,
                    PhoneWallpaperCatalog.texture(index),
                    x(left + 3), y(top + 3), x(left + CELL_WIDTH - 3), y(top + 86),
                    PhoneWallpaperCatalog.TEXTURE_WIDTH,
                    PhoneWallpaperCatalog.TEXTURE_HEIGHT
            );
            PhoneModernText.drawCenteredColoredFitted(
                    graphics,
                    PhoneSettingsStore.wallpaperName(index),
                    x(left + CELL_WIDTH / 2), y(top + 92), size(5), size(CELL_WIDTH - 10), 0xFFF1F4F8
            );
            if (selected) drawSelectionCheck(graphics, left + CELL_WIDTH - 12, top + 13);
        }
    }

    private void drawPhotoGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Path> photos = PhoneGalleryStore.photos(minecraft);
        positionInitialPhoto(photos);
        photoScrollRow = Math.max(0, Math.min(maxPhotoScroll(photos), photoScrollRow));
        if (photos.isEmpty()) {
            drawEmptyPhotos(graphics, mouseX, mouseY);
            return;
        }

        int first = photoScrollRow * 3;
        int visible = PHOTO_VISIBLE_ROWS * 3;
        int last = Math.min(photos.size(), first + visible);
        for (int index = first; index < last; index++) {
            int localIndex = index - first;
            int left = GRID_LEFT + (localIndex % 3) * (CELL_WIDTH + COLUMN_GAP);
            int top = GRID_TOP + (localIndex / 3) * PHOTO_ROW_STEP;
            Path photo = photos.get(index);
            boolean selected = PhoneSettingsStore.isPhotoWallpaperSelected(photo, target);
            boolean hover = inside(mouseX, mouseY, left, top, left + CELL_WIDTH, top + CELL_WIDTH);
            drawRoundedRect(
                    graphics,
                    x(left), y(top), x(left + CELL_WIDTH), y(top + CELL_WIDTH), size(7),
                    selected ? PhoneSettingsStore.accentColor() : hover ? 0xFFFFD45A : 0xFF28323E
            );
            PhoneGalleryStore.GalleryTexture texture = PhoneGalleryStore.textureFor(minecraft, photo);
            if (texture != null) {
                PhoneWallpaperRenderer.blitCover(
                        graphics,
                        texture.location(),
                        x(left + 2), y(top + 2), x(left + CELL_WIDTH - 2), y(top + CELL_WIDTH - 2),
                        texture.width(), texture.height()
                );
            } else {
                drawRoundedRect(
                        graphics,
                        x(left + 2), y(top + 2), x(left + CELL_WIDTH - 2), y(top + CELL_WIDTH - 2),
                        size(5), 0xFF202B37
                );
            }
            if (selected) drawSelectionCheck(graphics, left + CELL_WIDTH - 12, top + 13);
        }
        drawPhotoScrollBar(graphics, photos);
    }

    private void drawEmptyPhotos(GuiGraphics graphics, int mouseX, int mouseY) {
        int iconSize = size(48);
        boolean hover = inside(mouseX, mouseY, 82, 351, 218, 390);
        MPhoneAppIconRenderer.draw(
                graphics,
                PhoneApp.CAMERA,
                x(150) - iconSize / 2,
                y(244),
                iconSize,
                false
        );
        PhoneModernText.drawCenteredColoredFitted(
                graphics,
                PhoneSettingsStore.text("Hen\u00fcz foto\u011fraf yok", "No photos yet"),
                x(150), y(310), size(10), size(220), 0xFFFFFFFF
        );
        drawRoundedRect(
                graphics,
                x(82), y(351), x(218), y(390), size(12),
                hover ? 0xFF2997FF : 0xFF0A84FF
        );
        PhoneModernText.drawCenteredColoredFitted(
                graphics,
                PhoneSettingsStore.text("Kameray\u0131 A\u00e7", "Open Camera"),
                x(150), y(363), size(8), size(112), 0xFFFFFFFF
        );
    }

    private void drawPhotoScrollBar(GuiGraphics graphics, List<Path> photos) {
        int maxScroll = maxPhotoScroll(photos);
        if (maxScroll <= 0) return;
        int trackTop = GRID_TOP;
        int trackBottom = 523;
        int trackHeight = trackBottom - trackTop;
        int rows = (photos.size() + 2) / 3;
        int thumbHeight = Math.max(28, Math.round(trackHeight * (PHOTO_VISIBLE_ROWS / (float) rows)));
        int thumbTop = trackTop + Math.round((trackHeight - thumbHeight) * (photoScrollRow / (float) maxScroll));
        drawRoundedRect(graphics, x(284), y(trackTop), x(287), y(trackBottom), size(2), 0x332F3B49);
        drawRoundedRect(
                graphics,
                x(284), y(thumbTop), x(287), y(thumbTop + thumbHeight), size(2), 0xFF77879A
        );
    }

    private void drawSelectionCheck(GuiGraphics graphics, int centerX, int centerY) {
        fillCircle(graphics, x(centerX), y(centerY), size(9), PhoneSettingsStore.accentColor());
        int stroke = Math.max(1, size(2));
        for (int step = 0; step < 4; step++) {
            int px = x(centerX - 5 + step);
            int py = y(centerY - 1 + step);
            graphics.fill(px, py, px + stroke, py + stroke, 0xFFFFFFFF);
        }
        for (int step = 0; step < 7; step++) {
            int px = x(centerX - 1 + step);
            int py = y(centerY + 2 - step);
            graphics.fill(px, py, px + stroke, py + stroke, 0xFFFFFFFF);
        }
    }

    private void drawNotice(GuiGraphics graphics) {
        if (notice.isBlank() || System.currentTimeMillis() > noticeUntil) return;
        drawRoundedRect(graphics, x(82), y(544), x(218), y(574), size(12), 0xE51B2633);
        PhoneModernText.drawCenteredColoredFitted(
                graphics, notice, x(150), y(554), size(7), size(118), 0xFFFFFFFF
        );
    }

    private void positionInitialPhoto(List<Path> photos) {
        if (initialPhotoPositioned) return;
        initialPhotoPositioned = true;
        if (initialPhoto == null || photos.isEmpty()) return;
        for (int index = 0; index < photos.size(); index++) {
            if (initialPhoto.equals(normalize(photos.get(index)))) {
                photoScrollRow = Math.min(maxPhotoScroll(photos), index / 3);
                return;
            }
        }
    }

    private int maxPhotoScroll(List<Path> photos) {
        int rows = (photos.size() + 2) / 3;
        return Math.max(0, rows - PHOTO_VISIBLE_ROWS);
    }

    private void showAppliedNotice(boolean applied) {
        notice = applied
                ? PhoneSettingsStore.text("Uyguland\u0131", "Applied")
                : PhoneSettingsStore.text("Foto\u011fraf kullan\u0131lam\u0131yor", "Photo unavailable");
        noticeUntil = System.currentTimeMillis() + 1600L;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (handleBackButton(mouseX, mouseY)) return true;

        WallpaperTarget[] targets = {WallpaperTarget.HOME, WallpaperTarget.LOCK, WallpaperTarget.BOTH};
        for (int i = 0; i < targets.length; i++) {
            int left = 22 + i * 87;
            if (inside(mouseX, mouseY, left, 91, left + 82, 123)) {
                target = targets[i];
                return true;
            }
        }
        if (inside(mouseX, mouseY, 22, 137, 148, 171)) {
            tab = Tab.BUILT_IN;
            return true;
        }
        if (inside(mouseX, mouseY, 152, 137, 278, 171)) {
            tab = Tab.PHOTOS;
            return true;
        }

        if (tab == Tab.BUILT_IN) {
            int count = Math.min(PhoneWallpaperCatalog.count(), PhoneSettingsStore.wallpaperCount());
            for (int index = 0; index < count; index++) {
                int left = GRID_LEFT + (index % 3) * (CELL_WIDTH + COLUMN_GAP);
                int top = GRID_TOP + (index / 3) * 116;
                if (inside(mouseX, mouseY, left, top, left + CELL_WIDTH, top + 110)) {
                    PhoneSettingsStore.setBuiltInWallpaper(index, target);
                    showAppliedNotice(true);
                    return true;
                }
            }
        } else {
            List<Path> photos = PhoneGalleryStore.photos(minecraft);
            if (photos.isEmpty() && inside(mouseX, mouseY, 82, 351, 218, 390)) {
                PhoneNavigationState.open(minecraft, this, new ZPhoneCameraScreen(), PhoneApp.CAMERA);
                return true;
            }
            int first = photoScrollRow * 3;
            int last = Math.min(photos.size(), first + PHOTO_VISIBLE_ROWS * 3);
            for (int index = first; index < last; index++) {
                int localIndex = index - first;
                int left = GRID_LEFT + (localIndex % 3) * (CELL_WIDTH + COLUMN_GAP);
                int top = GRID_TOP + (localIndex / 3) * PHOTO_ROW_STEP;
                if (inside(mouseX, mouseY, left, top, left + CELL_WIDTH, top + CELL_WIDTH)) {
                    showAppliedNotice(PhoneSettingsStore.setPhotoWallpaper(photos.get(index), target));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.PHOTOS && delta != 0.0D && inside(mouseX, mouseY, 12, GRID_TOP, 288, 526)) {
            List<Path> photos = PhoneGalleryStore.photos(minecraft);
            photoScrollRow = delta < 0.0D
                    ? Math.min(maxPhotoScroll(photos), photoScrollRow + 1)
                    : Math.max(0, photoScrollRow - 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            PhoneNavigationState.backOrHome(minecraft);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static Path normalize(Path path) {
        if (path == null) return null;
        try {
            return path.toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}

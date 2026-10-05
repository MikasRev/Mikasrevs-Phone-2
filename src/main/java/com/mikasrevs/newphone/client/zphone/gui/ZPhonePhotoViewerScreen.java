package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class ZPhonePhotoViewerScreen extends AbstractZPhoneScreen {
    private final List<Path> photos;
    private final String sharedPhotoId;
    private final Screen returnScreen;
    private int index;

    public ZPhonePhotoViewerScreen(List<Path> photos, int index) {
        super(Component.literal(PhoneSettingsStore.text("Foto\u011fraf", "Photo")));
        this.photos = photos == null ? List.of() : List.copyOf(photos);
        this.sharedPhotoId = "";
        this.returnScreen = null;
        this.index = Math.max(0, Math.min(Math.max(0, this.photos.size() - 1), index));
    }

    public ZPhonePhotoViewerScreen(String sharedPhotoId, Screen returnScreen) {
        super(Component.literal(PhoneSettingsStore.text("Foto\u011fraf", "Photo")));
        this.photos = List.of();
        this.sharedPhotoId = sharedPhotoId == null ? "" : sharedPhotoId;
        this.returnScreen = returnScreen;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawRoundedRect(graphics, x(3), y(8), x(297), y(606), size(22), 0xFF030405);
        drawStatusBar(graphics);
        PhoneModernText.drawColored(graphics, "\u2039", x(22), y(52), size(22), 0xFF62A9FF);
        String heading = shared() || photos.isEmpty()
                ? PhoneSettingsStore.text("Foto\u011fraf", "Photo")
                : (index + 1) + PhoneSettingsStore.text(" / ", " of ") + photos.size();
        PhoneModernText.drawCenteredColored(graphics, heading, x(150), y(57), size(9), 0xFFFFFFFF);
        drawPhoto(graphics);
        drawBottom(graphics);
        drawHomeIndicator(graphics);
        drawFrame(graphics);
    }

    private void drawPhoto(GuiGraphics graphics) {
        int left = x(13);
        int top = y(99);
        int right = x(287);
        int bottom = y(466);
        graphics.fill(left, top, right, bottom, 0xFF090A0C);

        ResourceLocation location = null;
        int textureWidth = 1;
        int textureHeight = 1;
        if (shared()) {
            PhonePhotoStore.PhotoTexture texture = PhonePhotoStore.textureFor(minecraft, sharedPhotoId);
            if (texture != null) {
                location = texture.location();
                textureWidth = texture.width();
                textureHeight = texture.height();
            }
        } else if (!photos.isEmpty()) {
            PhoneGalleryStore.GalleryTexture texture = PhoneGalleryStore.textureFor(minecraft, currentPhoto());
            if (texture != null) {
                location = texture.location();
                textureWidth = texture.width();
                textureHeight = texture.height();
            }
        }
        if (location == null) {
            PhoneModernText.drawCenteredColored(
                    graphics,
                    PhoneSettingsStore.text("Foto\u011fraf kullan\u0131lam\u0131yor", "Photo unavailable"),
                    x(150), y(270), size(9), 0xFF8B949F
            );
            return;
        }

        int boxWidth = right - left;
        int boxHeight = bottom - top;
        float scale = Math.min(boxWidth / (float) textureWidth, boxHeight / (float) textureHeight);
        int width = Math.max(1, Math.round(textureWidth * scale));
        int height = Math.max(1, Math.round(textureHeight * scale));
        int photoLeft = left + (boxWidth - width) / 2;
        int photoTop = top + (boxHeight - height) / 2;
        graphics.blit(
                location,
                photoLeft,
                photoTop,
                width,
                height,
                0,
                0,
                textureWidth,
                textureHeight,
                textureWidth,
                textureHeight
        );
    }

    private void drawBottom(GuiGraphics graphics) {
        if (shared()) {
            PhoneModernText.drawCenteredColoredFitted(
                    graphics,
                    PhoneSettingsStore.text(
                            "Kapatmak i\u00e7in foto\u011frafa veya geri d\u00fc\u011fmesine dokun",
                            "Tap photo or back to close"
                    ),
                    x(150), y(500), size(7), size(258), 0xFF8B949F
            );
            return;
        }
        if (photos.isEmpty()) return;

        String date = "";
        try {
            date = dateFormatter().format(
                    Instant.ofEpochMilli(Files.getLastModifiedTime(currentPhoto()).toMillis())
                            .atZone(ZoneId.systemDefault())
            );
        } catch (Exception ignored) { }
        PhoneModernText.drawCenteredColored(graphics, date, x(150), y(487), size(7), 0xFF8B949F);

        drawRoundedRect(graphics, x(27), y(519), x(82), y(555), size(15), 0xFF171D25);
        PhoneModernText.drawCenteredColored(
                graphics, "\u2039", x(54), y(523), size(20), index > 0 ? 0xFFFFFFFF : 0xFF4A515A
        );
        drawRoundedRect(
                graphics, x(91), y(519), x(209), y(555), size(12), PhoneSettingsStore.accentColor()
        );
        PhoneModernText.drawCenteredColoredFitted(
                graphics,
                PhoneSettingsStore.text("Duvar Ka\u011f\u0131d\u0131", "Wallpaper"),
                x(150), y(530), size(7), size(102), 0xFFFFFFFF
        );
        drawRoundedRect(graphics, x(218), y(519), x(273), y(555), size(15), 0xFF171D25);
        PhoneModernText.drawCenteredColored(
                graphics,
                "\u203a",
                x(245), y(523), size(20),
                index + 1 < photos.size() ? 0xFFFFFFFF : 0xFF4A515A
        );
    }

    private boolean shared() {
        return !sharedPhotoId.isBlank();
    }

    private Path currentPhoto() {
        return photos.isEmpty() ? null : photos.get(index);
    }

    private DateTimeFormatter dateFormatter() {
        return DateTimeFormatter.ofPattern("d MMM yyyy \u2022 HH:mm", PhoneSettingsStore.locale());
    }

    private void back() {
        if (shared() && returnScreen != null && minecraft != null) minecraft.setScreen(returnScreen);
        else PhoneNavigationState.backOrHome(minecraft);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (inside(mouseX, mouseY, 15, 45, 58, 82)) {
            back();
            return true;
        }
        if (shared() && inside(mouseX, mouseY, 13, 99, 287, 466)) {
            back();
            return true;
        }
        if (!shared() && inside(mouseX, mouseY, 27, 519, 82, 555) && index > 0) {
            index--;
            return true;
        }
        if (!shared() && inside(mouseX, mouseY, 91, 519, 209, 555) && currentPhoto() != null) {
            PhoneNavigationState.open(minecraft, this, new ZPhoneWallpaperScreen(currentPhoto()));
            return true;
        }
        if (!shared() && inside(mouseX, mouseY, 218, 519, 273, 555) && index + 1 < photos.size()) {
            index++;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!shared() && keyCode == GLFW.GLFW_KEY_LEFT && index > 0) {
            index--;
            return true;
        }
        if (!shared() && keyCode == GLFW.GLFW_KEY_RIGHT && index + 1 < photos.size()) {
            index++;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            back();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

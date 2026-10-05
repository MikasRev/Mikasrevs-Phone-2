package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneLegacyAppScreen;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;

import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore;
import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneCameraScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class PhoneBlockPostProfileEditScreen extends AbstractZPhoneLegacyAppScreen {
    private SelectionTarget target = SelectionTarget.NONE;
    private String statusText = "";
    private long statusUntil;
    private final StringBuilder bioDraft = new StringBuilder();
    private boolean bioFocused;

    public PhoneBlockPostProfileEditScreen() {
        super(Component.literal(PhoneSettingsStore.text("BlockPost Profili", "BlockPost Profile")));
        PhoneSocialClientData.requestSync();
        bioDraft.append(PhoneBlockPostStore.profileBio());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawLegacyAppShell(graphics);
        graphics.fill(screenX(104), screenY(136), screenX(796), screenY(1490), 0xFF000000);
        drawHeader(graphics);
        drawProfilePreview(graphics);
        drawActionRows(graphics, mouseX, mouseY);
        drawBioEditor(graphics, mouseX, mouseY);
        drawGalleryPicker(graphics, mouseX, mouseY);
        drawStatus(graphics);
        finishLegacyAppShell(graphics);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.fill(screenX(104), screenY(136), screenX(796), screenY(238), 0xFF000000);
        PhoneModernText.drawColored(graphics, "‹", screenX(144), screenY(170), screenSize(38), 0xFFF1F5F8);
        PhoneModernText.draw(graphics, PhoneSettingsStore.text("Profili düzenle", "Edit profile"), screenX(204), screenY(176), screenSize(29));
        PhoneModernText.drawColored(graphics, "X", screenX(744), screenY(178), screenSize(22), 0xFFF2F2F2);
        graphics.fill(screenX(104), screenY(236), screenX(796), screenY(238), 0xFF2F3336);
    }

    private void drawProfilePreview(GuiGraphics graphics) {
        drawCover(graphics, 122, 260, 656, 224);
        graphics.fill(screenX(122), screenY(480), screenX(778), screenY(484), 0xFF2F3336);
        graphics.fill(screenX(142), screenY(402), screenX(294), screenY(554), 0xFF000000);
        if (!drawStoredImage(graphics, PhoneBlockPostStore.profileAvatarPath(), 150, 410, 136, 136)) {
            PhoneModernText.drawCenteredColoredFitted(graphics, PhoneSettingsStore.text("AVATAR", "AVATAR"),
                    screenX(218), screenY(466), screenSize(20), screenSize(128), 0xFF8EA4BA);
        }
        PhoneModernText.draw(graphics, PhoneSettingsStore.text("BlockPost profili", "BlockPost profile"), screenX(318), screenY(500), screenSize(28));
        PhoneModernText.drawColoredFitted(graphics,
                PhoneSettingsStore.text("Avatar ve kapak fotoğrafını değiştir", "Change avatar and cover photo"),
                screenX(318), screenY(540), screenSize(16), screenSize(430), 0xFF71767B);
    }

    private void drawActionRows(GuiGraphics graphics, int mouseX, int mouseY) {
        drawActionButton(graphics, mouseX, mouseY, 132, 590, 440, 666,
                PhoneSettingsStore.text("Galeriden avatar seç", "Choose avatar from gallery"), 0xFF202327);
        drawActionButton(graphics, mouseX, mouseY, 460, 590, 768, 666,
                PhoneSettingsStore.text("Avatar fotoğrafı çek", "Take avatar photo"), 0xFF1D9BF0);
        drawActionButton(graphics, mouseX, mouseY, 132, 684, 440, 760,
                PhoneSettingsStore.text("Galeriden kapak seç", "Choose cover from gallery"), 0xFF202327);
        drawActionButton(graphics, mouseX, mouseY, 460, 684, 768, 760,
                PhoneSettingsStore.text("Kapak fotoğrafı çek", "Take cover photo"), 0xFF1D9BF0);
    }

    private void drawActionButton(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int left,
            int top,
            int right,
            int bottom,
            String text,
            int color
    ) {
        boolean hover = inside(mouseX, mouseY, left, top, right, bottom);
        drawRoundedRect(graphics, screenX(left), screenY(top), screenX(right), screenY(bottom),
                screenSize(16), hover ? brighten(color) : color);
        PhoneModernText.drawCenteredFitted(graphics, text, screenX((left + right) / 2),
                screenY(top + 24), screenSize(17), screenSize(right - left - 24));
    }

    private void drawBioEditor(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(screenX(122), screenY(782), screenX(778), screenY(900), 0xFF000000);
        PhoneModernText.draw(graphics, PhoneSettingsStore.text("Biyografi", "Bio"), screenX(144), screenY(800), screenSize(20));
        boolean hover = inside(mouseX, mouseY, 142, 830, 644, 878);
        drawRoundedRect(graphics, screenX(142), screenY(830), screenX(644), screenY(878),
                screenSize(16), bioFocused || hover ? 0xFF202327 : 0xFF16181C);
        String shown = bioDraft.length() > 72 ? bioDraft.substring(bioDraft.length() - 72) : bioDraft.toString();
        PhoneModernText.drawColoredFitted(graphics,
                shown.isBlank() ? PhoneSettingsStore.text("Kendinden bahset...", "Tell people about yourself...") : shown,
                screenX(158), screenY(844), screenSize(16), screenSize(468),
                shown.isBlank() ? 0xFF71767B : 0xFFFFFFFF);
        drawRoundedRect(graphics, screenX(660), screenY(830), screenX(758), screenY(878),
                screenSize(16), 0xFF1D9BF0);
        PhoneModernText.drawCenteredFitted(graphics, PhoneSettingsStore.text("Kaydet", "Save"),
                screenX(709), screenY(844), screenSize(16), screenSize(82));
        PhoneModernText.drawColored(graphics, bioDraft.length() + "/160", screenX(688), screenY(886), screenSize(12), 0xFF71767B);
    }

    private void drawGalleryPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        if (target == SelectionTarget.NONE) {
            return;
        }
        graphics.fill(screenX(122), screenY(920), screenX(778), screenY(1374), 0xFF000000);
        String title = switch (target) {
            case AVATAR -> PhoneSettingsStore.text("Galeriden avatar seç", "Choose avatar from gallery");
            case COVER -> PhoneSettingsStore.text("Galeriden kapak seç", "Choose cover from gallery");
            case NONE -> PhoneSettingsStore.text("Galeri seçimi", "Gallery selection");
        };
        PhoneModernText.drawColoredFitted(graphics, title, screenX(144), screenY(942),
                screenSize(24), screenSize(610), 0xFFF4F4F4);
        PhoneModernText.drawColoredFitted(
                graphics,
                PhoneSettingsStore.text("Fotoğraflar", "Photos"),
                screenX(144),
                screenY(980),
                screenSize(16),
                screenSize(610),
                0xFF71767B
        );

        List<Path> photos = PhoneGalleryStore.photos(this.minecraft);
        if (photos.isEmpty()) {
            PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Galeride fotoğraf bulunamadı", "No photos found in gallery"), screenX(450), screenY(1080), screenSize(22), 0xFF8296AA);
            return;
        }

        int cellWidth = 146;
        int cellHeight = 194;
        int startX = 142;
        int startY = 1018;
        int shown = Math.min(8, photos.size());
        for (int index = 0; index < shown; index++) {
            int column = index % 4;
            int row = index / 4;
            int left = startX + column * 154;
            int top = startY + row * 210;
            boolean hover = inside(mouseX, mouseY, left, top, left + cellWidth, top + cellHeight);
            drawRoundedRect(graphics, screenX(left), screenY(top), screenX(left + cellWidth),
                    screenY(top + cellHeight), screenSize(10), hover ? 0xFF202327 : 0xFF101214);
            drawPhoto(graphics, photos.get(index), left + 6, top + 6, cellWidth - 12, 144);
            PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Seç", "Select"), screenX(left + cellWidth / 2), screenY(top + 160), screenSize(15), hover ? 0xFFFFFFFF : 0xFF9AB0C5);
        }
    }

    private void drawCover(GuiGraphics graphics, int left, int top, int width, int height) {
        if (!drawStoredImage(graphics, PhoneBlockPostStore.profileCoverPath(), left, top, width, height)) {
            graphics.fill(screenX(left), screenY(top), screenX(left + width), screenY(top + height), 0xFF242424);
            graphics.fill(screenX(left), screenY(top), screenX(left + width), screenY(top + 54), 0xFF333333);
        }
    }

    private void drawPhoto(GuiGraphics graphics, Path path, int left, int top, int width, int height) {
        try {
            PhoneGalleryStore.GalleryTexture texture = PhoneGalleryStore.textureFor(this.minecraft, path);
            if (texture == null) {
                return;
            }
            graphics.blit(
                    texture.location(),
                    screenX(left),
                    screenY(top),
                    screenSize(width),
                    screenSize(height),
                    0,
                    0,
                    texture.width(),
                    texture.height(),
                    texture.width(),
                    texture.height()
            );
        } catch (RuntimeException ignored) {
        }
    }

    private boolean drawStoredImage(GuiGraphics graphics, String pathValue, int left, int top, int width, int height) {
        try {
            if (pathValue == null || pathValue.isBlank()) {
                return false;
            }
            if (pathValue.startsWith("social:")) {
                PhonePhotoStore.PhotoTexture texture = PhonePhotoStore.textureFor(this.minecraft, pathValue.substring("social:".length()));
                if (texture == null) {
                    return false;
                }
                graphics.blit(
                        texture.location(), screenX(left), screenY(top),
                        screenSize(width), screenSize(height), 0, 0,
                        texture.width(), texture.height(), texture.width(), texture.height()
                );
                return true;
            }
            Path path = Path.of(pathValue);
            if (!Files.exists(path)) {
                return false;
            }
            drawPhoto(graphics, path, left, top, width, height);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void selectPhoto(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString();
        if (target == SelectionTarget.AVATAR) {
            PhoneBlockPostStore.setProfileAvatarPath(normalized);
            PhoneSocialClientData.uploadBlockPostAvatar(normalized);
            showStatus(PhoneSettingsStore.text("Avatar sunucuda güncellendi", "Avatar updated on server"));
        } else if (target == SelectionTarget.COVER) {
            PhoneBlockPostStore.setProfileCoverPath(normalized);
            PhoneSocialClientData.uploadBlockPostCover(normalized);
            showStatus(PhoneSettingsStore.text("Kapak fotoğrafı sunucuda güncellendi", "Cover photo updated on server"));
        }
        target = SelectionTarget.NONE;
    }

    private void showStatus(String text) {
        statusText = text == null ? "" : text;
        statusUntil = System.currentTimeMillis() + 2300L;
    }

    private void drawStatus(GuiGraphics graphics) {
        if (statusText.isBlank() || System.currentTimeMillis() > statusUntil) {
            return;
        }
        graphics.fill(screenX(236), screenY(1320), screenX(664), screenY(1368), 0xE71A3F62);
        PhoneModernText.drawCenteredFitted(graphics, statusText, screenX(450), screenY(1333),
                screenSize(17), screenSize(400));
    }

    private int brighten(int color) {
        int red = Math.min(255, ((color >> 16) & 0xFF) + 24);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 24);
        int blue = Math.min(255, (color & 0xFF) + 24);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private boolean inside(double mouseX, double mouseY, int left, int top, int right, int bottom) {
        return mouseX >= screenX(left) && mouseX <= screenX(right)
                && mouseY >= screenY(top) && mouseY <= screenY(bottom);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
                && handleBridgeIslandClick(mouseX, mouseY)) return true;
        if (handleHomeIndicatorClick(mouseX, mouseY, button)) {
            return true;
        }
        if (inside(mouseX, mouseY, 122, 154, 190, 238)) {
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }
        if (inside(mouseX, mouseY, 132, 590, 440, 666)) {
            target = SelectionTarget.AVATAR;
            return true;
        }
        if (inside(mouseX, mouseY, 460, 590, 768, 666)) {
            PhoneBlockPostStore.requestProfileAvatarCameraCapture();
            this.minecraft.setScreen(new ZPhoneCameraScreen());
            return true;
        }
        if (inside(mouseX, mouseY, 132, 684, 440, 760)) {
            target = SelectionTarget.COVER;
            return true;
        }
        if (inside(mouseX, mouseY, 460, 684, 768, 760)) {
            PhoneBlockPostStore.requestProfileCoverCameraCapture();
            this.minecraft.setScreen(new ZPhoneCameraScreen());
            return true;
        }
        if (inside(mouseX, mouseY, 142, 830, 644, 878)) { bioFocused = true; return true; }
        if (inside(mouseX, mouseY, 660, 830, 758, 878)) {
            PhoneBlockPostStore.setProfileBio(bioDraft.toString());
            showStatus(PhoneSettingsStore.text("Biyografi sunucuda güncellendi", "Bio updated on server"));
            bioFocused = false;
            return true;
        }
        bioFocused = false;

        if (target != SelectionTarget.NONE) {
            List<Path> photos = PhoneGalleryStore.photos(this.minecraft);
            int shown = Math.min(8, photos.size());
            for (int index = 0; index < shown; index++) {
                int column = index % 4;
                int row = index / 4;
                int left = 142 + column * 154;
                int top = 1018 + row * 210;
                if (inside(mouseX, mouseY, left, top, left + 146, top + 194)) {
                    selectPhoto(photos.get(index));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (bioFocused && SharedConstants.isAllowedChatCharacter(codePoint) && bioDraft.length() < 160) {
            String value = Character.toString(codePoint);
            int remaining = 160 - bioDraft.length();
            bioDraft.append(value, 0, Math.min(remaining, value.length()));
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bioFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!bioDraft.isEmpty()) bioDraft.deleteCharAt(bioDraft.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private enum SelectionTarget {
        NONE,
        AVATAR,
        COVER
    }
}

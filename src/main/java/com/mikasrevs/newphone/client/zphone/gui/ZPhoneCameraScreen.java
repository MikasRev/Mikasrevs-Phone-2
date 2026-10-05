package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.sound.PhoneSoundManager;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;

import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore;
import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
import com.mikasrevs.newphone.client.gui.PhoneGuiScaleState;
import com.mikasrevs.newphone.client.gui.PhoneBlockPostProfileEditScreen;
import com.mikasrevs.newphone.client.gui.PhoneBlockPostScreen;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Full-screen Minecraft camera with a Z-Phone styled camera chrome. */
public final class ZPhoneCameraScreen extends Screen implements ZPhoneUiScreen {
    private static final int MIN_FOV = 30;
    private static final int MAX_FOV = 110;
    private static final int ZOOM_STEP = 10;

    private final int originalFov;
    private final boolean originalHideGui;
    private int currentFov;
    private boolean keepZoomWhenRemoved;
    private long captureFlashUntil;
    private long statusUntil;
    private String statusText = "";
    private boolean movementKeysForced;

    public ZPhoneCameraScreen() {
        this(currentGameFov(), currentGameFov(), currentHideGui());
    }

    private ZPhoneCameraScreen(int originalFov, int currentFov, boolean originalHideGui) {
        super(Component.literal(PhoneSettingsStore.text("Kamera", "Camera")));
        this.originalFov = clampFov(originalFov);
        this.currentFov = clampFov(currentFov);
        this.originalHideGui = originalHideGui;
    }

    private static boolean currentHideGui() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.options.hideGui;
    }

    private static int currentGameFov() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? 70 : minecraft.options.fov().get();
    }

    private static int clampFov(int value) {
        return Math.max(MIN_FOV, Math.min(MAX_FOV, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void added() {
        super.added();
        applyCurrentFov();
        if (this.minecraft != null) this.minecraft.options.hideGui = true;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft != null) {
            this.minecraft.mouseHandler.grabMouse();
            this.minecraft.screen = this;
            applyCurrentFov();
            this.minecraft.options.hideGui = true;
            syncMovementKeys();
            movementKeysForced = true;
        }
    }

    @Override
    public void removed() {
        releaseMovementKeys();
        if (!this.keepZoomWhenRemoved && this.minecraft != null) {
            this.minecraft.options.fov().set(this.originalFov);
            this.minecraft.options.hideGui = this.originalHideGui;
        }
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // Keep the rendered Minecraft world visible as the live viewfinder.
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft != null) {
            // A Screen normally owns keyboard input. Keep the physical movement
            // bindings synchronized while the camera overlay is open so the
            // player can walk, jump, crouch and sprint without losing mouse-look.
            syncMovementKeys();
            movementKeysForced = true;
            if (this.minecraft.player != null && this.minecraft.player.input != null) {
                // Force KeyboardInput to consume the synchronized bindings even
                // while this overlay is the active Screen.
                this.minecraft.player.input.tick(false, 1.0F);
            }
            this.minecraft.options.hideGui = true;
        }
    }

    private void syncMovementKeys() {
        syncPhysicalKey(this.minecraft.options.keyUp);
        syncPhysicalKey(this.minecraft.options.keyDown);
        syncPhysicalKey(this.minecraft.options.keyLeft);
        syncPhysicalKey(this.minecraft.options.keyRight);
        syncPhysicalKey(this.minecraft.options.keyJump);
        syncPhysicalKey(this.minecraft.options.keyShift);
        syncPhysicalKey(this.minecraft.options.keySprint);
    }

    private void releaseMovementKeys() {
        if (!movementKeysForced || this.minecraft == null) return;
        this.minecraft.options.keyUp.setDown(false);
        this.minecraft.options.keyDown.setDown(false);
        this.minecraft.options.keyLeft.setDown(false);
        this.minecraft.options.keyRight.setDown(false);
        this.minecraft.options.keyJump.setDown(false);
        this.minecraft.options.keyShift.setDown(false);
        this.minecraft.options.keySprint.setDown(false);
        movementKeysForced = false;
    }

    private void syncPhysicalKey(KeyMapping mapping) {
        if (mapping == null || this.minecraft == null) return;
        InputConstants.Key key = mapping.getKey();
        if (key == null || key.getType() != InputConstants.Type.KEYSYM) return;
        mapping.setDown(InputConstants.isKeyDown(this.minecraft.getWindow().getWindow(), key.getValue()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawCameraChrome(graphics);
    }

    private void drawCameraChrome(GuiGraphics graphics) {
        int topHeight = ui(62);
        int bottomTop = this.height - ui(154);
        graphics.fill(0, 0, this.width, topHeight, 0xB5040608);
        graphics.fill(0, bottomTop, this.width, this.height, 0xD006080B);

        int islandWidth = ui(126);
        int islandLeft = (this.width - islandWidth) / 2;
        drawRoundedRect(graphics, islandLeft, ui(12), islandLeft + islandWidth, ui(43), ui(15), 0xEF020304);
        PhoneModernText.drawCenteredColored(graphics, zoomLabel(), this.width / 2, ui(21), ui(12), 0xFFFFFFFF);

        PhoneModernText.drawColored(graphics, "Z-CAMERA", ui(22), ui(22), ui(11), 0xFFFFFFFF);
        String exit = PhoneSettingsStore.text("ESC  ÇIKIŞ", "ESC  EXIT");
        int exitSize = ui(10);
        PhoneModernText.drawColored(graphics, exit, this.width - PhoneModernText.width(exit, exitSize) - ui(22), ui(23), exitSize, 0xFFD8DEE7);

        drawThirds(graphics, topHeight, bottomTop);

        int shutterX = this.width / 2;
        int shutterY = this.height - ui(68);
        fillCircle(graphics, shutterX, shutterY, ui(35), 0xFFFFFFFF);
        fillCircle(graphics, shutterX, shutterY, ui(29), 0xFF080A0D);
        fillCircle(graphics, shutterX, shutterY, ui(25), 0xFFF8F8F8);

        drawLatestThumbnail(graphics, bottomTop);
        drawRoundedRect(graphics, this.width - ui(102), this.height - ui(91), this.width - ui(28), this.height - ui(47), ui(20), 0x75171D24);
        PhoneModernText.drawCenteredColored(graphics, "Q  −   +  E", this.width - ui(65), this.height - ui(77), ui(10), 0xFFFFFFFF);

        if (!this.statusText.isBlank() && System.currentTimeMillis() <= this.statusUntil) {
            int w = Math.min(ui(320), this.width - ui(30));
            int l = (this.width - w) / 2;
            int t = bottomTop - ui(48);
            drawRoundedRect(graphics, l, t, l + w, t + ui(34), ui(15), 0xD90B1119);
            PhoneModernText.drawCenteredColoredFitted(graphics, this.statusText, this.width / 2, t + ui(10),
                    ui(10), w - ui(20), 0xFFFFFFFF);
        }
        if (System.currentTimeMillis() <= this.captureFlashUntil) {
            graphics.fill(0, 0, this.width, this.height, 0x91FFFFFF);
        }
    }

    private void drawLatestThumbnail(GuiGraphics graphics, int bottomTop) {
        int cell = ui(62);
        int left = ui(28);
        int top = this.height - ui(99);
        drawRoundedRect(graphics, left - ui(3), top - ui(3), left + cell + ui(3), top + cell + ui(3), ui(13), 0xFF343B46);
        java.nio.file.Path latest = PhoneGalleryStore.latestPhoto(this.minecraft);
        PhoneGalleryStore.GalleryTexture texture = PhoneGalleryStore.textureFor(this.minecraft, latest);
        if (texture == null) {
            drawRoundedRect(graphics, left, top, left + cell, top + cell, ui(11), 0xFF151B23);
            PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Fotoğraflar", "Photos"), left + cell / 2, top + ui(25), ui(9), 0xFF9AA4B0);
            return;
        }
        int source = Math.min(texture.width(), texture.height());
        int sx = Math.max(0, (texture.width() - source) / 2);
        int sy = Math.max(0, (texture.height() - source) / 2);
        graphics.blit(texture.location(), left, top, cell, cell, (float) sx, (float) sy,
                source, source, texture.width(), texture.height());
    }

    private void drawThirds(GuiGraphics graphics, int top, int bottom) {
        // A dark shadow plus a light highlight keeps the composition grid readable
        // against both bright daytime skies and dark nighttime scenes.
        int one = Math.max(1, ui(1));
        int shadow = 0x50000000;
        int highlight = 0x62FFFFFF;
        int x1 = this.width / 3;
        int x2 = this.width * 2 / 3;
        int area = Math.max(1, bottom - top);
        int y1 = top + area / 3;
        int y2 = top + area * 2 / 3;
        drawGridLineVertical(graphics, x1, top, bottom, one, shadow, highlight);
        drawGridLineVertical(graphics, x2, top, bottom, one, shadow, highlight);
        drawGridLineHorizontal(graphics, y1, one, shadow, highlight);
        drawGridLineHorizontal(graphics, y2, one, shadow, highlight);
    }

    private void drawGridLineVertical(GuiGraphics graphics, int x, int top, int bottom,
                                      int width, int shadow, int highlight) {
        graphics.fill(x - width, top, x + width + 1, bottom, shadow);
        graphics.fill(x, top, x + width, bottom, highlight);
    }

    private void drawGridLineHorizontal(GuiGraphics graphics, int y, int height,
                                        int shadow, int highlight) {
        graphics.fill(0, y - height, this.width, y + height + 1, shadow);
        graphics.fill(0, y, this.width, y + height, highlight);
    }

    private void drawRoundedRect(GuiGraphics graphics, int left, int top, int right, int bottom, int radius, int color) {
        int r = Math.max(1, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
        for (int py = top; py < bottom; py++) {
            int inset = 0;
            if (py < top + r) {
                int dy = top + r - py;
                inset = r - (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            } else if (py >= bottom - r) {
                int dy = py - (bottom - r - 1);
                inset = r - (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            }
            graphics.fill(left + inset, py, right - inset, py + 1, color);
        }
    }

    private void fillCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private int ui(int pixels) {
        float scale = Math.min(this.width / 1920.0F, this.height / 1080.0F);
        scale = Math.max(0.62F, Math.min(1.35F, scale));
        return Math.max(1, Math.round(pixels * scale));
    }

    private String zoomLabel() {
        double ratio = this.originalFov / (double) this.currentFov;
        return String.format(Locale.ROOT, "%.1fx", ratio);
    }

    private void zoomIn() { setCurrentFov(this.currentFov - ZOOM_STEP); }
    private void zoomOut() { setCurrentFov(this.currentFov + ZOOM_STEP); }

    private void setCurrentFov(int value) {
        int next = clampFov(value);
        if (next == this.currentFov) return;
        this.currentFov = next;
        applyCurrentFov();
    }

    private void applyCurrentFov() {
        if (this.minecraft != null) this.minecraft.options.fov().set(this.currentFov);
    }

    private void capturePhoto() {
        if (this.minecraft == null) return;
        PhoneSoundManager.playCamera();
        Minecraft minecraft = this.minecraft;
        int preservedOriginalFov = this.originalFov;
        int preservedCurrentFov = this.currentFov;
        boolean preservedOriginalHideGui = this.originalHideGui;
        this.keepZoomWhenRemoved = true;
        PhoneGuiScaleState.holdPhoneScaleFor(2000L);
        minecraft.options.hideGui = true;
        minecraft.setScreen(null);

        CompletableFuture.delayedExecutor(120L, TimeUnit.MILLISECONDS).execute(() ->
                minecraft.execute(() -> {
                    try {
                        Screenshot.grab(
                                minecraft.gameDirectory,
                                minecraft.getMainRenderTarget(),
                                result -> minecraft.execute(() -> reopenAfterCapture(
                                        minecraft,
                                        preservedOriginalFov,
                                        preservedCurrentFov,
                                        preservedOriginalHideGui,
                                        PhoneSettingsStore.text("Fotoğraflara kaydedildi", "Saved to Photos")
                                ))
                        );
                    } catch (Exception exception) {
                        minecraft.execute(() -> reopenAfterCapture(
                                minecraft,
                                preservedOriginalFov,
                                preservedCurrentFov,
                                preservedOriginalHideGui,
                                PhoneSettingsStore.text("Fotoğraf kaydedilemedi", "Couldn't save photo")
                        ));
                    }
                })
        );
    }

    private static void reopenAfterCapture(Minecraft minecraft, int originalFov, int currentFov, boolean originalHideGui, String resultText) {
        minecraft.options.hideGui = true;
        PhoneGalleryStore.onPhotoCaptured(minecraft);
        if (PhoneBlockPostScreen.finishPendingCameraCapture(minecraft)) {
            minecraft.options.fov().set(originalFov);
            minecraft.options.hideGui = originalHideGui;
            return;
        }
        if (ZPhoneBlockGramScreen.finishPendingCameraCapture(minecraft)) {
            minecraft.options.fov().set(originalFov);
            minecraft.options.hideGui = originalHideGui;
            return;
        }
        if (PhoneBlockPostStore.applyPendingCapturedPhoto(minecraft)) {
            PhoneSocialClientData.uploadCurrentBlockPostProfile();
            minecraft.options.fov().set(originalFov);
            minecraft.options.hideGui = originalHideGui;
            PhoneNavigationState.replaceCurrent(minecraft, new PhoneBlockPostProfileEditScreen());
            return;
        }
        if (PhoneBlockGramStore.applyPendingCapturedPhoto(minecraft)) {
            PhoneSocialClientData.updateBlockGramProfile(PhoneBlockGramStore.profileBio(), PhoneBlockGramStore.profilePhotoPath());
            minecraft.options.fov().set(originalFov);
            minecraft.options.hideGui = originalHideGui;
            PhoneNavigationState.replaceCurrent(minecraft, new ZPhoneBlockGramScreen());
            return;
        }
        if (ZPhoneMessageThreadScreen.finishPendingCameraCapture(minecraft)) {
            minecraft.options.fov().set(originalFov);
            minecraft.options.hideGui = originalHideGui;
            return;
        }
        ZPhoneCameraScreen screen = new ZPhoneCameraScreen(originalFov, currentFov, originalHideGui);
        screen.captureFlashUntil = System.currentTimeMillis() + 230L;
        screen.statusText = resultText == null ? PhoneSettingsStore.text("Fotoğraflara kaydedildi", "Saved to Photos") : resultText;
        screen.statusUntil = System.currentTimeMillis() + 2200L;
        minecraft.setScreen(screen);
    }

    private boolean shutter(double mouseX, double mouseY) {
        int cx = this.width / 2;
        int cy = this.height - ui(68);
        int r = ui(42);
        double dx = mouseX - cx, dy = mouseY - cy;
        return dx * dx + dy * dy <= (double) r * r;
    }

    private boolean thumbnail(double mouseX, double mouseY) {
        int cell = ui(68);
        int left = ui(25);
        int top = this.height - ui(102);
        return mouseX >= left && mouseX <= left + cell && mouseY >= top && mouseY <= top + cell;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            capturePhoto();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && thumbnail(mouseX, mouseY)) {
            if (this.minecraft != null) this.minecraft.setScreen(new ZPhoneGalleryScreen());
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && shutter(mouseX, mouseY)) {
            capturePhoto();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_Q) { zoomIn(); return true; }
        if (keyCode == GLFW.GLFW_KEY_E) { zoomOut(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (!ZPhoneMessageThreadScreen.cancelPendingCameraCapture(this.minecraft)
                    && !PhoneBlockPostScreen.cancelPendingCameraCapture(this.minecraft)
                    && !ZPhoneBlockGramScreen.cancelPendingCameraCapture(this.minecraft)) {
                PhoneNavigationState.restoreCurrent(this.minecraft);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneMusicPlayerState;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** iPhone-inspired lightweight Control Center containing only functional controls. */
public final class ZPhoneControlCenterScreen extends AbstractZPhoneScreen {
    private static final long OPEN_DURATION_MS = 560L;
    private static final long CLOSE_DURATION_MS = 300L;
    private static final long REBOUND_DURATION_MS = 220L;

    private final Screen backgroundScreen;
    private final long openedAt;
    private final float initialReveal;
    private boolean fullyRevealed;

    private boolean draggingBrightness;
    private boolean draggingVolume;
    private boolean dismissTracking;
    private double dismissStartY;
    private double dismissDistance;
    private float dismissBaseReveal = 1.0F;
    private float dismissReveal = 1.0F;

    private boolean closing;
    private long closeStartedAt;
    private float closeStartReveal = 1.0F;
    private boolean closeCompleted;

    private boolean rebounding;
    private long reboundStartedAt;
    private float reboundStartReveal = 1.0F;

    public ZPhoneControlCenterScreen() {
        this(null, 0.0F);
    }

    public ZPhoneControlCenterScreen(Screen backgroundScreen, boolean alreadyRevealed) {
        this(backgroundScreen, alreadyRevealed ? 1.0F : 0.0F);
    }

    public ZPhoneControlCenterScreen(Screen backgroundScreen, float initialReveal) {
        super(Component.literal(PhoneSettingsStore.text("Kontrol Merkezi", "Control Center")));
        this.backgroundScreen = backgroundScreen;
        this.initialReveal = clamp(initialReveal);
        this.dismissReveal = this.initialReveal;
        this.fullyRevealed = this.initialReveal >= 0.999F;
        this.openedAt = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Render the exact source screen first. Repainting our own wallpaper here
        // used to make the app underneath disappear as soon as Control Center opened.
        if (backgroundScreen != null && backgroundScreen != this) {
            backgroundScreen.render(g, mouseX, mouseY, partialTick);
            g.flush();
        } else {
            renderBackground(g);
            drawWallpaper(g);
        }

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        float reveal = revealProgress();
        drawControlCenterScrim(g, reveal);
        int revealBottom = y(8 + Math.round(594.0F * reveal));
        if (revealBottom > y(8)) {
            beginOverlayLayer(g);
            g.enableScissor(x(8), y(8), x(292), revealBottom);
            int sheetOffsetY = -Math.round(size(138) * (1.0F - reveal));
            g.pose().pushPose();
            g.pose().translate(0.0F, sheetOffsetY, 0.0F);
            drawRoundedRect(g, x(8), y(8), x(292), y(602), size(23), 0xE20A1018);
            finishOverlayBackground(g);
            drawControls(g, mouseX, mouseY);
            g.flush();
            g.pose().popPose();
            g.disableScissor();
        }

        drawHomeIndicator(g);
        drawFrame(g);
        g.flush();
        g.pose().popPose();
    }

    private float revealProgress() {
        long now = System.currentTimeMillis();
        if (closing) {
            float raw = clamp((now - closeStartedAt) / (float) CLOSE_DURATION_MS);
            float eased = raw * raw * (3.0F - 2.0F * raw);
            return closeStartReveal * (1.0F - eased);
        }
        if (dismissTracking) return dismissReveal;
        if (rebounding) {
            float raw = clamp((now - reboundStartedAt) / (float) REBOUND_DURATION_MS);
            float eased = 1.0F - (1.0F - raw) * (1.0F - raw) * (1.0F - raw);
            return reboundStartReveal + (1.0F - reboundStartReveal) * eased;
        }
        if (fullyRevealed) return 1.0F;
        float raw = clamp((now - openedAt) / (float) OPEN_DURATION_MS);
        if (raw >= 1.0F) {
            fullyRevealed = true;
            return 1.0F;
        }
        float eased = raw * raw * (3.0F - 2.0F * raw);
        return initialReveal + (1.0F - initialReveal) * eased;
    }

    private void drawControls(GuiGraphics g, int mouseX, int mouseY) {
        drawControlCenterContents(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (revealProgress() < 0.999F) return true;

        if (inside(mouseX, mouseY, 25, 54, 78, 108)) {
            PhoneSettingsStore.setAirplaneMode(!PhoneSettingsStore.airplaneMode());
            return true;
        }
        if (inside(mouseX, mouseY, 85, 54, 139, 108)) {
            PhoneSettingsStore.setWifiEnabled(!PhoneSettingsStore.wifiEnabled());
            return true;
        }
        if (inside(mouseX, mouseY, 25, 114, 78, 169)) {
            PhoneSettingsStore.setBluetoothEnabled(!PhoneSettingsStore.bluetoothEnabled());
            return true;
        }
        if (inside(mouseX, mouseY, 85, 114, 139, 169)) {
            boolean enable = !PhoneSettingsStore.messageNotifications()
                    || PhoneSettingsStore.phoneVolume() <= 0.01F;
            PhoneSettingsStore.setMessageNotifications(enable);
            if (enable && PhoneSettingsStore.phoneVolume() <= 0.01F) {
                PhoneSettingsStore.setPhoneVolume(0.5F);
            }
            return true;
        }
        if (inside(mouseX, mouseY, 18, 197, 107, 353)) {
            PhoneSettingsStore.setDoNotDisturb(!PhoneSettingsStore.doNotDisturb());
            return true;
        }
        if (inside(mouseX, mouseY, 163, 128, 199, 171)) {
            PhoneMusicPlayerState.previous();
            return true;
        }
        if (inside(mouseX, mouseY, 199, 128, 239, 171)) {
            PhoneMusicPlayerState.toggle();
            return true;
        }
        if (inside(mouseX, mouseY, 239, 128, 275, 171)) {
            PhoneMusicPlayerState.next();
            return true;
        }
        if (inside(mouseX, mouseY, 117, 197, 190, 353)) {
            draggingBrightness = true;
            updateBrightness(mouseY);
            return true;
        }
        if (inside(mouseX, mouseY, 201, 197, 274, 353)) {
            draggingVolume = true;
            updateVolume(mouseY);
            return true;
        }
        if (inside(mouseX, mouseY, 18, 367, 282, 445)) {
            this.minecraft.setScreen(new ZPhoneNotificationCenterScreen());
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (closing) return true;
        if (draggingBrightness) {
            updateBrightness(mouseY);
            return true;
        }
        if (draggingVolume) {
            updateVolume(mouseY);
            return true;
        }

        double previousY = mouseY - dragY;
        if (!dismissTracking) {
            float currentReveal = revealProgress();
            dismissTracking = true;
            dismissStartY = previousY;
            dismissDistance = 0.0D;
            dismissBaseReveal = currentReveal;
            dismissReveal = dismissBaseReveal;
            rebounding = false;
        }
        dismissDistance = Math.max(0.0D, dismissStartY - mouseY);
        dismissReveal = clamp(dismissBaseReveal - (float) (dismissDistance / Math.max(1.0D, size(594))));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (draggingBrightness) {
            draggingBrightness = false;
            return true;
        }
        if (draggingVolume) {
            draggingVolume = false;
            return true;
        }
        if (dismissTracking) {
            float releasedReveal = dismissReveal;
            double releasedDistance = dismissDistance;
            dismissTracking = false;
            dismissDistance = 0.0D;
            if (releasedDistance >= Math.max(38.0D, size(58))) {
                requestClose(releasedReveal);
            } else {
                startRebound(releasedReveal);
            }
            return true;
        }
        return true;
    }

    private void updateBrightness(double mouseY) {
        float p = 1.0F - (float) ((mouseY - y(197)) / Math.max(1.0D, y(353) - y(197)));
        PhoneSettingsStore.setBrightness(Math.max(0.05F, Math.min(1.0F, p)));
    }

    private void updateVolume(double mouseY) {
        float p = 1.0F - (float) ((mouseY - y(197)) / Math.max(1.0D, y(353) - y(197)));
        PhoneSettingsStore.setPhoneVolume(Math.max(0.0F, Math.min(1.0F, p)));
    }

    private void startRebound(float fromReveal) {
        rebounding = true;
        reboundStartReveal = clamp(fromReveal);
        reboundStartedAt = System.currentTimeMillis();
    }

    private void requestClose(float fromReveal) {
        if (closing || closeCompleted) return;
        draggingBrightness = false;
        draggingVolume = false;
        dismissTracking = false;
        rebounding = false;
        closing = true;
        closeStartReveal = clamp(fromReveal);
        closeStartedAt = System.currentTimeMillis();
    }

    private void returnToSourceNow() {
        if (this.minecraft == null || closeCompleted) return;
        closeCompleted = true;
        if (backgroundScreen != null && backgroundScreen != this) {
            PhoneNavigationState.returnTo(this.minecraft, backgroundScreen);
        } else {
            ZPhoneClient.openHomeScreen();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (rebounding && System.currentTimeMillis() - reboundStartedAt >= REBOUND_DURATION_MS) {
            rebounding = false;
            fullyRevealed = true;
        }
        if (closing && System.currentTimeMillis() - closeStartedAt >= CLOSE_DURATION_MS) {
            returnToSourceNow();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            requestClose(revealProgress());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}

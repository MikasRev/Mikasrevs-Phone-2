package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.video.PhoneVideoStreamClient;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.zphone.ZPhoneHeldModelState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Forge 1.20.1 voice/video call UI backed by the server call state machine. */
public final class ZPhoneCallScreen extends AbstractZPhoneScreen {
    private enum CallAction { NONE, ACCEPT, DECLINE, END, MINIMIZE }

    private static final long ENTER_ANIMATION_MS = 360L;
    private static final long STATUS_ANIMATION_MS = 440L;
    private static final long PRESS_ANIMATION_MS = 260L;
    private static final long MINIMIZE_ANIMATION_MS = 280L;
    private static final int FALLBACK_SYNC_INTERVAL_TICKS = 100;

    private int syncTicks;
    private String previousStatus = "IDLE";
    private String animatedStatus = "IDLE";
    private long openedAt = System.currentTimeMillis();
    private long statusAnimationStartedAt = openedAt;
    private CallAction pressedAction = CallAction.NONE;
    private long actionPressedAt;
    private long minimizeStartedAt;
    private long endedAt;
    private boolean videoPrepared;
    private String displayedPeerUuid = "";
    private String displayedPeerName = "";
    private String displayedCallId = "";
    private boolean displayedVideoCall;

    public ZPhoneCallScreen() {
        super(Component.literal(PhoneSettingsStore.text("Arama", "Call")));
        previousStatus = PhoneClientData.callStatus();
        animatedStatus = previousStatus;
        rememberCallIdentity(previousStatus);
        PhoneClientData.requestSync();
        ZPhoneHeldModelState.lightUpHeldPhone();
    }

    @Override
    public void tick() {
        super.tick();
        if (++syncTicks >= FALLBACK_SYNC_INTERVAL_TICKS) {
            syncTicks = 0;
            PhoneClientData.requestSync();
        }
        long now = System.currentTimeMillis();
        String status = PhoneClientData.callStatus();
        rememberCallIdentity(status);
        observeStatus(status, now);
        if (PhoneClientData.isVideoCall() && "ACTIVE".equals(status) && !videoPrepared) {
            videoPrepared = true;
            PhoneVideoStreamClient.leaveWorldMiniMode();
        }
        if (!"ACTIVE".equals(status)) videoPrepared = false;
        if (!"IDLE".equals(previousStatus) && "IDLE".equals(status) && endedAt == 0L) {
            endedAt = now;
            PhoneVideoStreamClient.clearCallTextures();
        }
        previousStatus = status;
        if (!canMinimize(status) && minimizeStartedAt > 0L) minimizeStartedAt = 0L;
        if (pressedAction != CallAction.NONE && now - actionPressedAt >= PRESS_ANIMATION_MS) {
            pressedAction = CallAction.NONE;
        }
        if (minimizeStartedAt > 0L && now - minimizeStartedAt >= MINIMIZE_ANIMATION_MS) {
            finishMinimize();
            return;
        }
        if (endedAt > 0L && now - endedAt > 900L && minecraft != null) {
            PhoneNavigationState.backOrHome(minecraft);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        String status = PhoneClientData.callStatus();
        rememberCallIdentity(status);
        observeStatus(status, System.currentTimeMillis());
        if (PhoneClientData.isVideoCall() && "ACTIVE".equals(status) && PhoneVideoStreamClient.isCameraEnabled()) {
            PhoneVideoStreamClient.captureIfDue(false);
        }
        renderBackground(g);
        g.pose().pushPose();
        applyMinimizeTransform(g);
        if (videoCallForDisplay(status)) renderVideoCall(g, status);
        else renderVoiceCall(g, status);
        drawHomeIndicator(g);
        drawFrame(g);
        g.pose().popPose();
    }

    private void renderVoiceCall(GuiGraphics g, String status) {
        drawWallpaper(g);
        drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xE9060A10);
        drawStatusBar(g);
        pushContentAnimation(g);
        String peerName = peerName();
        if ("INCOMING".equals(status) || "OUTGOING".equals(status)) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 210.0D);
            fillCircle(g, x(150), y(202), size(67 + Math.round(5.0F * pulse)),
                    withAlpha(0xFF2FC566, 0.08F + pulse * 0.07F));
        }
        fillCircle(g, x(150), y(202), size(61), 0x442A3440);
        PhonePlayerHeadRenderer.draw(g, peerUuid(), x(101), y(153), size(98));
        PhoneModernText.drawCenteredColored(g, peerName, x(150), y(279), size(19), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, statusLine(status), x(150), y(310), size(8), 0xFF9AA5B2);
        String provider = PhoneClientData.voiceProvider();
        if (!provider.isBlank()) PhoneModernText.drawCenteredColored(g, provider, x(150), y(330), size(6), 0xFF65717E);
        drawCallActions(g, status, false);
        g.pose().popPose();
    }

    private void renderVideoCall(GuiGraphics g, String status) {
        drawWallpaper(g);
        drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xF504070B);
        drawStatusBar(g);
        pushContentAnimation(g);

        if (!"ACTIVE".equals(status)) {
            fillCircle(g, x(150), y(205), size(57), 0x442A3440);
            PhonePlayerHeadRenderer.draw(g, peerUuid(), x(104), y(159), size(92));
            PhoneModernText.drawCenteredColored(g, peerName(), x(150), y(284), size(18), 0xFFFFFFFF);
            PhoneModernText.drawCenteredColored(g, statusLine(status), x(150), y(313), size(8), 0xFFABB4BE);
            drawCallActions(g, status, true);
            g.pose().popPose();
            return;
        }

        int left = x(8), top = y(43), right = x(292), bottom = y(492);
        g.fill(left, top, right, bottom, 0xFF0B1118);
        if (!PhoneClientData.peerVideoCameraEnabled()) {
            drawVideoPlaceholder(g, left, top, right, bottom,
                    PhoneSettingsStore.text("Kamera kapalı", "Camera off"));
        } else if (PhoneVideoStreamClient.hasFreshRemoteFrame(peerUuid())) {
            g.enableScissor(left, top, right, bottom);
            blitCover(g, PhoneVideoStreamClient.remoteTextureId(), PhoneVideoStreamClient.remoteWidth(), PhoneVideoStreamClient.remoteHeight(), left, top, right, bottom, false);
            g.disableScissor();
        } else {
            drawVideoPlaceholder(g, left, top, right, bottom,
                    PhoneSettingsStore.text("Görüntü bağlanıyor…", "Connecting video…"));
        }

        g.fill(left, top, right, y(102), 0x9E000000);
        g.fill(left, y(424), right, bottom, 0x94000000);
        PhoneModernText.drawColored(g, peerName(), x(22), y(59), size(12), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, statusLine(status), x(22), y(82), size(7), 0xFFC7D0D9);
        PhoneModernText.drawColored(g, "FRONT".equals(PhoneClientData.peerVideoCameraMode()) ? PhoneSettingsStore.text("Ön kamera", "Front camera") : PhoneSettingsStore.text("Arka kamera", "Back camera"), x(22), y(446), size(6), 0xFFBCC5CF);

        if (PhoneVideoStreamClient.isCameraEnabled() && PhoneVideoStreamClient.hasLocalPreview()) {
            int pl = x(190), pt = y(111), pr = x(279), pb = y(178);
            drawRoundedRect(g, pl - size(2), pt - size(2), pr + size(2), pb + size(2), size(10), 0xC9000000);
            g.enableScissor(pl, pt, pr, pb);
            blitCover(g, PhoneVideoStreamClient.localTextureId(), PhoneVideoStreamClient.localWidth(), PhoneVideoStreamClient.localHeight(), pl, pt, pr, pb, PhoneVideoStreamClient.isFrontCamera());
            g.disableScissor();
        }

        int cy = 530;
        utilityButton(g, 67, cy, PhoneVideoStreamClient.isCameraEnabled() ? "CAM" : "OFF", PhoneVideoStreamClient.isCameraEnabled() ? 0xFF2C3744 : 0xFFE34C56);
        utilityButton(g, 118, cy, "↻", 0xFF2C3744);
        callButton(g, 177, cy, false, PhoneSettingsStore.text("Bitir", "End"), CallAction.END);
        drawUtilityPulse(g, 235, cy, CallAction.MINIMIZE, 0xFF2D8CFF);
        utilityButton(g, 235, cy, "−", 0xFF2D8CFF);
        PhoneModernText.drawCenteredColored(g, PhoneVideoStreamClient.isFrontCamera() ? PhoneSettingsStore.text("Ön", "Front") : PhoneSettingsStore.text("Arka", "Back"), x(118), y(563), size(6), 0xFFB8C1CB);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Küçült", "Minimize"), x(235), y(563), size(6), 0xFFAED7FF);
        g.pose().popPose();
    }

    private void drawVideoPlaceholder(GuiGraphics g, int left, int top, int right, int bottom, String text) {
        PhonePlayerHeadRenderer.draw(g, peerUuid(), (left + right) / 2 - size(36), (top + bottom) / 2 - size(55), size(72));
        PhoneModernText.drawCenteredColored(g, text, (left + right) / 2, (top + bottom) / 2 + size(34), size(8), 0xFFC2CBD4);
    }

    private void drawCallActions(GuiGraphics g, String status, boolean video) {
        int enterShift = Math.round(24.0F * (1.0F - contentProgress()));
        if ("INCOMING".equals(status)) {
            callButton(g, 92, 441 + enterShift, false, PhoneSettingsStore.text("Reddet", "Decline"), CallAction.DECLINE);
            callButton(g, 208, 441 + enterShift, true, PhoneSettingsStore.text("Kabul et", "Accept"), CallAction.ACCEPT);
        } else if ("OUTGOING".equals(status) || "ACTIVE".equals(status)) {
            callButton(g, 150, 441 + enterShift, false, PhoneSettingsStore.text("Bitir", "End"), CallAction.END);
            if (canMinimize(status)) {
                drawMinimizeButton(g, video ? PhoneSettingsStore.text("Görüntüyü küçült", "Minimize video") : PhoneSettingsStore.text("Kulağa götür", "Hold to ear"));
            }
        } else {
            String notice = endedAt > 0L
                    ? PhoneSettingsStore.text("Arama sona erdi", "Call ended")
                    : PhoneSettingsStore.localizeNetworkText(PhoneClientData.notice());
            PhoneModernText.drawCenteredColoredFitted(g, notice, x(150), y(396), size(9), size(244), 0xFFABB5C0);
        }
    }

    private String peerName() {
        String name = PhoneClientData.callPeerName();
        if (!name.isBlank()) return name;
        return displayedPeerName.isBlank() ? PhoneSettingsStore.text("Oyuncu", "Player") : displayedPeerName;
    }

    private String peerUuid() {
        String uuid = PhoneClientData.callPeerUuid();
        return uuid.isBlank() ? displayedPeerUuid : uuid;
    }

    private boolean videoCallForDisplay(String status) {
        return "IDLE".equals(status) ? displayedVideoCall : PhoneClientData.isVideoCall();
    }

    private void rememberCallIdentity(String status) {
        if ("IDLE".equals(status)) return;
        String callId = PhoneClientData.callId();
        if (!callId.isBlank() && !callId.equals(displayedCallId)) {
            displayedCallId = callId;
            displayedPeerUuid = "";
            displayedPeerName = "";
            openedAt = System.currentTimeMillis();
            statusAnimationStartedAt = openedAt;
            endedAt = 0L;
            minimizeStartedAt = 0L;
            pressedAction = CallAction.NONE;
            videoPrepared = false;
        }
        String uuid = PhoneClientData.callPeerUuid();
        String name = PhoneClientData.callPeerName();
        if (!uuid.isBlank()) displayedPeerUuid = uuid;
        if (!name.isBlank()) displayedPeerName = name;
        displayedVideoCall = PhoneClientData.isVideoCall();
    }

    private String statusLine(String status) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - PhoneClientData.callStartedAt());
        if ("ACTIVE".equals(status)) {
            long seconds = elapsed / 1000L;
            return String.format("%02d:%02d", seconds / 60L, seconds % 60L);
        }
        if ("OUTGOING".equals(status)) return videoCallForDisplay(status) ? PhoneSettingsStore.text("Görüntülü aranıyor…", "Video calling…") : PhoneSettingsStore.text("Aranıyor…", "Calling…");
        if ("INCOMING".equals(status)) return videoCallForDisplay(status) ? PhoneSettingsStore.text("Gelen görüntülü arama", "Incoming video call") : PhoneSettingsStore.text("Gelen sesli arama", "Incoming voice call");
        return "";
    }

    private void callButton(GuiGraphics g, int cx, int cy, boolean accept, String label, CallAction action) {
        int color = accept ? 0xFF2FC566 : 0xFFE64D58;
        float pulse = actionPulse(action);
        int radius = 34 + Math.round(4.0F * pulse);
        if (pulse > 0.0F) {
            fillCircle(g, x(cx), y(cy), size(radius + 7), withAlpha(color, 0.18F * pulse));
        }
        fillCircle(g, x(cx), y(cy), size(radius), color);
        drawHandset(g, x(cx), y(cy), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColoredFitted(g, label, x(cx), y(cy + 45), size(7), size(92), 0xFFE9EDF1);
    }

    private void drawMinimizeButton(GuiGraphics g, String label) {
        float pulse = actionPulse(CallAction.MINIMIZE);
        int expand = Math.round(3.0F * pulse);
        drawRoundedRect(g, x(93 - expand), y(502 - expand), x(207 + expand), y(544 + expand),
                size(17), withAlpha(0xFF57B0FF, 0.25F + pulse * 0.20F));
        drawRoundedRect(g, x(97), y(506), x(203), y(540), size(15), 0xFF247FE5);
        PhoneModernText.drawCenteredColoredFitted(g, label, x(150), y(518), size(7), size(94), 0xFFFFFFFF);
    }

    private void drawUtilityPulse(GuiGraphics g, int cx, int cy, CallAction action, int color) {
        float pulse = actionPulse(action);
        if (pulse <= 0.0F) return;
        fillCircle(g, x(cx), y(cy), size(31 + Math.round(3.0F * pulse)), withAlpha(color, 0.22F * pulse));
    }

    private void utilityButton(GuiGraphics g, int cx, int cy, String label, int color) {
        fillCircle(g, x(cx), y(cy), size(25), color);
        PhoneModernText.drawCenteredColored(g, label, x(cx), y(cy - 4), size(6), 0xFFFFFFFF);
    }

    private void drawHandset(GuiGraphics g, int cx, int cy, int color) {
        int s = Math.max(1, size(1.5F));
        g.fill(cx - 7 * s, cy - 2 * s, cx - 3 * s, cy + 3 * s, color);
        g.fill(cx - 4 * s, cy - 5 * s, cx, cy, color);
        g.fill(cx, cy - 5 * s, cx + 4 * s, cy, color);
        g.fill(cx + 3 * s, cy - 2 * s, cx + 7 * s, cy + 3 * s, color);
    }

    private void observeStatus(String status, long now) {
        String safe = status == null || status.isBlank() ? "IDLE" : status;
        if (safe.equals(animatedStatus)) return;
        animatedStatus = safe;
        statusAnimationStartedAt = now;
    }

    private float contentProgress() {
        long now = System.currentTimeMillis();
        float screen = easeOutCubic((now - openedAt) / (float) ENTER_ANIMATION_MS);
        float status = easeOutCubic((now - statusAnimationStartedAt) / (float) STATUS_ANIMATION_MS);
        return Math.min(screen, status);
    }

    private void pushContentAnimation(GuiGraphics g) {
        float progress = contentProgress();
        float scale = 0.94F + 0.06F * progress;
        float centerX = x(150);
        float centerY = y(314);
        float shiftY = size(18) * (1.0F - progress);
        g.pose().pushPose();
        g.pose().translate(centerX, centerY + shiftY, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.pose().translate(-centerX, -centerY, 0.0F);
    }

    private void applyMinimizeTransform(GuiGraphics g) {
        if (minimizeStartedAt <= 0L) return;
        float raw = Math.max(0.0F, Math.min(1.0F,
                (System.currentTimeMillis() - minimizeStartedAt) / (float) MINIMIZE_ANIMATION_MS));
        float progress = smoothStep(raw);
        float centerX = (x(0) + x(300)) * 0.5F;
        float centerY = (y(0) + y(610)) * 0.5F;
        float targetX = width - Math.max(42, size(58));
        float targetY = height - Math.max(68, size(94));
        float animatedX = centerX + (targetX - centerX) * progress;
        float animatedY = centerY + (targetY - centerY) * progress;
        float scale = 1.0F - 0.70F * progress;
        g.pose().translate(animatedX, animatedY, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.pose().translate(-centerX, -centerY, 0.0F);
    }

    private void pressAction(CallAction action) {
        pressedAction = action;
        actionPressedAt = System.currentTimeMillis();
    }

    private float actionPulse(CallAction action) {
        if (action == CallAction.NONE || pressedAction != action || actionPressedAt <= 0L) return 0.0F;
        float progress = Math.max(0.0F, Math.min(1.0F,
                (System.currentTimeMillis() - actionPressedAt) / (float) PRESS_ANIMATION_MS));
        return (float) Math.sin(Math.PI * progress);
    }

    private int withAlpha(int color, float opacity) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int alpha = Math.max(0, Math.min(255, Math.round(baseAlpha * Math.max(0.0F, Math.min(1.0F, opacity)))));
        return (color & 0x00FFFFFF) | alpha << 24;
    }

    private static float easeOutCubic(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float smoothStep(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return t * t * (3.0F - 2.0F * t);
    }

    private void blitCover(GuiGraphics g, ResourceLocation texture, int textureWidth, int textureHeight,
                           int left, int top, int right, int bottom, boolean mirror) {
        if (texture == null || textureWidth <= 0 || textureHeight <= 0 || right <= left || bottom <= top) return;
        float srcRatio = textureWidth / (float) textureHeight;
        float dstRatio = (right - left) / (float) (bottom - top);
        int sx = 0, sy = 0, sw = textureWidth, sh = textureHeight;
        if (srcRatio > dstRatio) {
            sw = Math.max(1, Math.round(textureHeight * dstRatio));
            sx = Math.max(0, (textureWidth - sw) / 2);
        } else if (srcRatio < dstRatio) {
            sh = Math.max(1, Math.round(textureWidth / dstRatio));
            sy = Math.max(0, (textureHeight - sh) / 2);
        }
        int dw = right - left, dh = bottom - top;
        if (mirror) {
            g.pose().pushPose();
            g.pose().translate(right + left, 0.0F, 0.0F);
            g.pose().scale(-1.0F, 1.0F, 1.0F);
            g.blit(texture, left, top, dw, dh, sx, sy, sw, sh, textureWidth, textureHeight);
            g.pose().popPose();
        } else {
            g.blit(texture, left, top, dw, dh, sx, sy, sw, sh, textureWidth, textureHeight);
        }
    }

    private boolean insideCircle(double mx, double my, int cx, int cy, int radius) {
        double dx = mx - x(cx), dy = my - y(cy);
        int r = size(radius);
        return dx * dx + dy * dy <= (double) r * r;
    }

    private void startMinimize() {
        if (!canMinimize(PhoneClientData.callStatus()) || minimizeStartedAt > 0L) return;
        pressAction(CallAction.MINIMIZE);
        minimizeStartedAt = System.currentTimeMillis();
    }

    private void finishMinimize() {
        if (!canMinimize(PhoneClientData.callStatus())) {
            minimizeStartedAt = 0L;
            return;
        }
        if (PhoneClientData.isVideoCall()) PhoneVideoStreamClient.enterWorldMiniMode();
        if (minecraft != null && minecraft.screen == this) {
            ZPhoneClient.markPhoneClosed();
            minecraft.setScreen(null);
        }
    }

    private static boolean canMinimize(String status) {
        return "ACTIVE".equals(status) || ("OUTGOING".equals(status) && !PhoneClientData.isVideoCall());
    }

    private void returnToPhoneHome() {
        if (minecraft == null) return;
        if (PhoneClientData.isVideoCall() && PhoneClientData.hasCall()) {
            PhoneVideoStreamClient.enterWorldMiniMode();
        }
        ZPhoneClient.openHomeScreen();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (minimizeStartedAt > 0L) return true;
        String status = PhoneClientData.callStatus();
        if (isHomeIndicatorTap(mouseX, mouseY)) {
            returnToPhoneHome();
            return true;
        }
        int enterShift = Math.round(24.0F * (1.0F - contentProgress()));
        if ("INCOMING".equals(status)) {
            if (insideCircle(mouseX, mouseY, 92, 441 + enterShift, 39)) {
                pressAction(CallAction.DECLINE);
                PhoneClientData.declineCall();
                return true;
            }
            if (insideCircle(mouseX, mouseY, 208, 441 + enterShift, 39)) {
                pressAction(CallAction.ACCEPT);
                PhoneClientData.acceptCall();
                return true;
            }
        } else if ("OUTGOING".equals(status)) {
            if (insideCircle(mouseX, mouseY, 150, 441 + enterShift, 39)) {
                pressAction(CallAction.END);
                PhoneClientData.endCall();
                return true;
            }
            if (canMinimize(status) && inside(mouseX, mouseY, 93, 502, 207, 544)) { startMinimize(); return true; }
        } else if ("ACTIVE".equals(status) && PhoneClientData.isVideoCall()) {
            if (insideCircle(mouseX, mouseY, 67, 530, 30)) { PhoneVideoStreamClient.setCameraEnabled(!PhoneVideoStreamClient.isCameraEnabled()); return true; }
            if (insideCircle(mouseX, mouseY, 118, 530, 30)) { PhoneVideoStreamClient.toggleCameraMode(); return true; }
            if (insideCircle(mouseX, mouseY, 177, 530, 39)) {
                pressAction(CallAction.END);
                PhoneClientData.endCall();
                return true;
            }
            if (insideCircle(mouseX, mouseY, 235, 530, 30)) { startMinimize(); return true; }
        } else if ("ACTIVE".equals(status)) {
            if (insideCircle(mouseX, mouseY, 150, 441 + enterShift, 39)) {
                pressAction(CallAction.END);
                PhoneClientData.endCall();
                return true;
            }
            if (inside(mouseX, mouseY, 93, 502, 207, 544)) { startMinimize(); return true; }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_X) {
            startMinimize();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (canMinimize(PhoneClientData.callStatus())) startMinimize();
            else if ("IDLE".equals(PhoneClientData.callStatus()) && minecraft != null) PhoneNavigationState.backOrHome(minecraft);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

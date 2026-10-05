package com.mikasrevs.newphone.client.video;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneCallScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Incoming-call banner plus world-mini voice/video call HUD. */
@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PhoneCallClientEvents {
    private static final int PHONE_FRAME_W = 900;
    private static final int PHONE_FRAME_H = 1600;
    private static final long INCOMING_SLIDE_MS = 760L;
    private static final long INCOMING_HOLD_MS = 3_000L;
    private static final long INCOMING_TOTAL_MS = INCOMING_SLIDE_MS * 2L + INCOMING_HOLD_MS;
    private static final long MINI_ENTER_MS = 360L;

    private static String incomingKey = "";
    private static long incomingSeenAt;
    private static boolean miniVisible;
    private static long miniShownAt;

    private PhoneCallClientEvents() { }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("mphone_call", (gui, graphics, partialTick, screenWidth, screenHeight) ->
                renderCallHud(graphics, screenWidth, screenHeight));
    }

    @Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() { }

        @SubscribeEvent
        public static void beforeHud(RenderGuiEvent.Pre event) {
            Minecraft minecraft = Minecraft.getInstance();
            boolean fullCallScreen = minecraft != null && minecraft.screen instanceof ZPhoneCallScreen;
            if ((PhoneVideoStreamClient.isWorldMini() || fullCallScreen) && PhoneClientData.isVideoCall()
                    && PhoneClientData.callActive() && PhoneVideoStreamClient.isCameraEnabled()) {
                PhoneVideoStreamClient.captureIfDue(PhoneVideoStreamClient.isWorldMini());
            }
        }

        @SubscribeEvent
        public static void afterLevel(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL
                    && PhoneVideoStreamClient.isWorldMini() && PhoneClientData.isVideoCall()
                    && PhoneClientData.callActive() && PhoneVideoStreamClient.isCameraEnabled()) {
                PhoneVideoStreamClient.captureIfDue(true);
            }
        }
    }

    private static void renderCallHud(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        boolean incoming = "INCOMING".equals(PhoneClientData.callStatus());
        if (incoming) {
            String key = PhoneClientData.callPeerUuid() + ":" + PhoneClientData.isVideoCall();
            if (!key.equals(incomingKey)) {
                incomingKey = key;
                incomingSeenAt = System.currentTimeMillis();
            }
        } else {
            trackIncoming(false);
        }

        if (minecraft.screen instanceof AbstractZPhoneScreen) {
            trackMini(false);
            return;
        }

        if (incoming) {
            if (PhoneSettingsStore.incomingCallAnimationEnabled() && !PhoneSettingsStore.doNotDisturb()) {
                long elapsed = Math.max(0L, System.currentTimeMillis() - incomingSeenAt);
                if (elapsed < INCOMING_TOTAL_MS) {
                    renderIncomingPhone(graphics, screenWidth, screenHeight, elapsed);
                    return;
                }
            }
        }

        if (!PhoneClientData.callActive() && !com.mikasrevs.newphone.phone.animation.PhoneCallMotion.shouldRaise(PhoneClientData.callStatus(),PhoneClientData.isVideoCall())) {
            trackMini(false);
            return;
        }
        if (!miniVisible) {
            miniVisible = true;
            miniShownAt = System.currentTimeMillis();
        }
        float miniProgress = easeOut((System.currentTimeMillis() - miniShownAt) / (float) MINI_ENTER_MS);
        if (PhoneClientData.isVideoCall()) {
            renderMiniVideo(graphics, screenWidth, screenHeight, miniProgress);
        } else {
            renderMiniVoice(graphics, screenWidth, screenHeight, miniProgress);
        }
    }

    private static void trackIncoming(boolean keep) {
        if (keep) return;
        incomingKey = "";
        incomingSeenAt = 0L;
    }

    private static void trackMini(boolean keep) {
        if (keep) return;
        miniVisible = false;
        miniShownAt = 0L;
    }

    /** Reference lower-right Z-Phone notification, kept physically identical at every GUI scale. */
    private static void renderIncomingPhone(GuiGraphics graphics, int screenWidth, int screenHeight, long elapsed) {
        if(com.mikasrevs.newphone.client.device.PhoneDeviceSession.hudType()==com.mikasrevs.newphone.phone.device.PhoneDeviceType.IPHONE_DUO){com.mikasrevs.newphone.client.duo.DuoCallPresentation.incoming(graphics,screenWidth,screenHeight,elapsed);return;}
        int panelWidth = hudPx(250);
        int panelHeight = hudPx(374);
        int margin = hudPx(12);
        int left = screenWidth - panelWidth - margin;
        int shownTop = screenHeight - panelHeight;
        int hiddenTop = screenHeight + hudPx(8);
        long holdUntil = INCOMING_SLIDE_MS + INCOMING_HOLD_MS;
        int top = animatedTop(elapsed, INCOMING_SLIDE_MS, holdUntil, INCOMING_TOTAL_MS, hiddenTop, shownTop);
        int bottom = top + panelHeight;

        int displayLeft = left + (int) Math.floor(panelWidth * (96.0D / PHONE_FRAME_W)) - 1;
        int displayRight = left + (int) Math.ceil(panelWidth * (804.0D / PHONE_FRAME_W)) + 1;
        int displayTop = top + (int) Math.floor(panelHeight * (78.0D / 1348.0D)) - 1;
        graphics.fill(displayLeft, displayTop, displayRight, bottom + 1, wallpaperColor());
        var model = com.mikasrevs.newphone.client.phone.model.ClientPhoneModels.local();
        graphics.blit(model.frame(), left, top, panelWidth, panelHeight,
                0, 0, PHONE_FRAME_W, 1348, PHONE_FRAME_W, PHONE_FRAME_H);
        var style = com.mikasrevs.newphone.PhoneCaseStyle.fromStack(
                com.mikasrevs.newphone.PhoneInventoryUtil.findPhone(Minecraft.getInstance().player));
        var caseFrame = com.mikasrevs.newphone.phone.model.PhoneCaseTextures.frame(model.id(), style);
        if (caseFrame != null) {
            int overflow = Math.round(panelHeight * (160F / 1348F));
            graphics.blit(caseFrame, left, top - overflow, panelWidth, panelHeight + overflow,
                    0, 0, PHONE_FRAME_W, 1508, PHONE_FRAME_W, 1760);
        }

        int centerX = left + panelWidth / 2;
        PhoneModernText.drawCenteredColored(graphics, PhoneClientData.isVideoCall() ? PhoneSettingsStore.text("MPhone Görüntülü", "MPhone Video") : PhoneSettingsStore.text("MPhone Sesli", "MPhone Audio"), centerX,
                top + hudPx(51), hudPx(10), 0xFFB8C0CA);
        String provider = PhoneClientData.voiceProvider();
        if (provider != null && !provider.isBlank()) {
            PhoneModernText.drawCenteredColored(graphics, provider, centerX,
                    top + hudPx(67), hudPx(7), 0xFF7F8A96);
        }

        float pulse = 0.5F + 0.5F * (float) Math.sin(elapsed / 180.0D);
        int haloAlpha = 20 + Math.round(24.0F * pulse);
        fillCircle(graphics, centerX, top + hudPx(160), hudPx(92), haloAlpha << 24 | 0x000D5B35);
        fillCircle(graphics, centerX, top + hudPx(145), hudPx(47), 0xFF2C3540);
        fillCircle(graphics, centerX, top + hudPx(145), hudPx(43), 0xFF3A4551);
        PhonePlayerHeadRenderer.draw(graphics, PhoneClientData.callPeerUuid(),
                centerX - hudPx(35), top + hudPx(110), hudPx(70));

        String name = PhoneClientData.callPeerName().isBlank() ? PhoneSettingsStore.text("Bilinmiyor", "Unknown") : PhoneClientData.callPeerName();
        PhoneModernText.drawCenteredColored(graphics, name, centerX,
                top + hudPx(211), hudPx(20), 0xFFFFFFFF);
        var contact = PhoneClientData.contactByUuid(PhoneClientData.callPeerUuid());
        if (contact != null && contact.phoneNumber != null && !contact.phoneNumber.isBlank()) {
            PhoneModernText.drawCenteredColored(graphics,
                    "#" + PhoneClientData.formatPhoneNumber(contact.phoneNumber), centerX,
                    top + hudPx(236), hudPx(7), 0xFF8994A0);
        }
        PhoneModernText.drawCenteredColored(graphics,
                PhoneClientData.isVideoCall() ? PhoneSettingsStore.text("Gelen görüntülü arama", "Incoming video call") : PhoneSettingsStore.text("Gelen arama", "Incoming call"),
                centerX, top + hudPx(259), hudPx(10), 0xFFB7C0C9);
        PhoneModernText.drawCenteredColored(graphics, ringingSecondsRemaining() + PhoneSettingsStore.text(" saniye", " seconds"),
                centerX, top + hudPx(280), hudPx(7), 0xFF7C8793);

        int buttonY = top + hudPx(338);
        int declineX = centerX - hudPx(48);
        int acceptX = centerX + hudPx(48);
        int radius = hudPx(17);
        fillCircle(graphics, declineX, buttonY, radius, 0xFFFF453A);
        fillCircle(graphics, acceptX, buttonY, radius, 0xFF30D158);
        drawHudHandsetIcon(graphics, declineX, buttonY, 16, 0xFFFFFFFF, true);
        drawHudHandsetIcon(graphics, acceptX, buttonY, 16, 0xFFFFFFFF, false);
    }

    private static void renderMiniVoice(GuiGraphics graphics, int screenWidth, int screenHeight, float enter) {
        int panelW = hudPxAtGuiScale2(168);
        int panelH = hudPxAtGuiScale2(58);
        int margin = hudPxAtGuiScale2(12);
        int slideY = Math.round((panelH + margin + hudPx(8)) * (1.0F - enter));
        int slideX = Math.round(hudPx(18) * (1.0F - enter));
        int left = screenWidth - panelW - margin + slideX;
        int top = screenHeight - panelH - margin + slideY;
        int right = left + panelW;
        int bottom = top + panelH;
        int radius = hudPxAtGuiScale2(14);
        drawRoundedRect(graphics, left + hudPx(1), top + hudPx(2),
                right + hudPx(1), bottom + hudPx(2), radius, 0x65000000);
        drawRoundedRect(graphics, left, top, right, bottom, radius, 0xF20A1017);

        int callX = left + hudPxAtGuiScale2(22);
        int callY = top + hudPxAtGuiScale2(29);
        fillCircle(graphics, callX, callY, hudPxAtGuiScale2(10), 0xFF31D158);
        drawHudHandsetIcon(graphics, callX, callY, 14, 0xFFFFFFFF, false);
        String name = PhoneClientData.callPeerName().isBlank() ? PhoneSettingsStore.text("Sesli arama", "Voice call") : PhoneClientData.callPeerName();
        PhoneModernText.drawColored(graphics, name,
                left + hudPxAtGuiScale2(45), top + hudPxAtGuiScale2(10),
                hudPxAtGuiScale2(13), 0xFFFFFFFF);
        PhoneModernText.drawColored(graphics, "OUTGOING".equals(PhoneClientData.callStatus()) ? PhoneSettingsStore.text("Aranıyor…", "Calling…") : formatDuration(),
                left + hudPxAtGuiScale2(45), top + hudPxAtGuiScale2(31),
                hudPxAtGuiScale2(10), 0xFF9CE9B3);
        PhoneModernText.drawColored(graphics, "X", right - hudPxAtGuiScale2(21),
                top + hudPxAtGuiScale2(22), hudPxAtGuiScale2(11), 0xFF9DA8B3);
    }

    private static void renderMiniVideo(GuiGraphics graphics, int screenWidth, int screenHeight, float enter) {
        if (!PhoneVideoStreamClient.isWorldMini()) return;
        int panelW = hudPxAtGuiScale2(104);
        int panelH = hudPxAtGuiScale2(148);
        int margin = hudPxAtGuiScale2(12);
        int slideY = Math.round((panelH + margin + hudPx(8)) * (1.0F - enter));
        int slideX = Math.round(hudPx(18) * (1.0F - enter));
        int left = screenWidth - panelW - margin + slideX;
        int top = screenHeight - panelH - margin + slideY;
        int right = left + panelW;
        int bottom = top + panelH;
        int radius = hudPxAtGuiScale2(12);
        drawRoundedRect(graphics, left + hudPx(2), top + hudPx(3),
                right + hudPx(2), bottom + hudPx(3), radius, 0x72000000);
        drawRoundedRect(graphics, left, top, right, bottom, radius, 0xF20A1017);

        int header = hudPxAtGuiScale2(20);
        int footer = hudPxAtGuiScale2(22);
        int inset = hudPxAtGuiScale2(4);
        int titleX = left + inset + hudPxAtGuiScale2(3);
        int titleHeight = hudPxAtGuiScale2(9);
        int durationHeight = hudPxAtGuiScale2(7);
        String duration = formatDuration();
        int durationX = right - inset - hudPxAtGuiScale2(3) - PhoneModernText.width(duration, durationHeight);
        String title = PhoneClientData.callPeerName().isBlank() ? PhoneSettingsStore.text("Görüntülü arama", "Video call") : PhoneClientData.callPeerName();
        title = fitText(title, Math.max(1, durationX - titleX - hudPxAtGuiScale2(4)), titleHeight);
        PhoneModernText.drawColored(graphics, title, titleX,
                top + hudPxAtGuiScale2(5), titleHeight, 0xFFFFFFFF);
        PhoneModernText.drawColored(graphics, duration, durationX,
                top + hudPxAtGuiScale2(6), durationHeight, 0xFF9CE9B3);
        int videoTop = top + header;
        int videoBottom = bottom - footer;
        int videoLeft = left + inset;
        int videoRight = right - inset;
        graphics.fill(videoLeft, videoTop, videoRight, videoBottom, 0xFF101A24);
        if (!PhoneClientData.peerVideoCameraEnabled()) {
            int head = hudPxAtGuiScale2(36);
            PhonePlayerHeadRenderer.draw(graphics, PhoneClientData.callPeerUuid(),
                    (videoLeft + videoRight - head) / 2,
                    (videoTop + videoBottom) / 2 - hudPxAtGuiScale2(22), head);
            PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Kamera kapalı", "Camera off"),
                    (videoLeft + videoRight) / 2, videoBottom - hudPxAtGuiScale2(18),
                    hudPxAtGuiScale2(7), 0xFFBBC5CE);
        } else if (PhoneVideoStreamClient.hasFreshRemoteFrame(PhoneClientData.callPeerUuid())) {
            graphics.enableScissor(videoLeft, videoTop, videoRight, videoBottom);
            blitCover(graphics, PhoneVideoStreamClient.remoteTextureId(),
                    PhoneVideoStreamClient.remoteWidth(), PhoneVideoStreamClient.remoteHeight(),
                    videoLeft, videoTop, videoRight, videoBottom);
            graphics.disableScissor();
        } else {
            int head = hudPxAtGuiScale2(36);
            PhonePlayerHeadRenderer.draw(graphics, PhoneClientData.callPeerUuid(),
                    (videoLeft + videoRight - head) / 2,
                    (videoTop + videoBottom) / 2 - hudPxAtGuiScale2(18), head);
        }
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("X  Tam ekran", "X  Full screen"), left + inset + hudPxAtGuiScale2(3),
                bottom - hudPxAtGuiScale2(15), hudPxAtGuiScale2(8), 0xFFC3CBD3);
    }

    private static String formatDuration() {
        long elapsed = Math.max(0L, System.currentTimeMillis() - PhoneClientData.callStartedAt());
        long seconds = elapsed / 1000L;
        return String.format("%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private static String fitText(String text, int maxWidth, int height) {
        String safe = text == null ? "" : text;
        if (PhoneModernText.width(safe, height) <= maxWidth) return safe;
        String suffix = "...";
        int end = safe.length();
        while (end > 0 && PhoneModernText.width(safe.substring(0, end) + suffix, height) > maxWidth) end--;
        return end <= 0 ? suffix : safe.substring(0, end) + suffix;
    }

    private static void blitCover(GuiGraphics graphics, ResourceLocation texture, int textureWidth, int textureHeight,
                                  int left, int top, int right, int bottom) {
        if (texture == null || textureWidth <= 0 || textureHeight <= 0 || right <= left || bottom <= top) return;
        float sourceRatio = textureWidth / (float) textureHeight;
        float destinationRatio = (right - left) / (float) (bottom - top);
        int sourceX = 0;
        int sourceY = 0;
        int sourceWidth = textureWidth;
        int sourceHeight = textureHeight;
        if (sourceRatio > destinationRatio) {
            sourceWidth = Math.max(1, Math.round(textureHeight * destinationRatio));
            sourceX = Math.max(0, (textureWidth - sourceWidth) / 2);
        } else if (sourceRatio < destinationRatio) {
            sourceHeight = Math.max(1, Math.round(textureWidth / destinationRatio));
            sourceY = Math.max(0, (textureHeight - sourceHeight) / 2);
        }
        graphics.blit(texture, left, top, right - left, bottom - top,
                sourceX, sourceY, sourceWidth, sourceHeight, textureWidth, textureHeight);
    }

    private static int ringingSecondsRemaining() {
        if (incomingSeenAt <= 0L) return 15;
        long elapsed = Math.max(0L, System.currentTimeMillis() - incomingSeenAt);
        return Math.max(0, 15 - (int) (elapsed / 1000L));
    }

    private static int animatedTop(long elapsed, long slideIn, long holdUntil, long slideOutEnd,
                                   int hiddenTop, int shownTop) {
        if (elapsed < slideIn) {
            return lerp(hiddenTop, shownTop, easeOut(elapsed / (float) slideIn));
        }
        if (elapsed < holdUntil) return shownTop;
        return lerp(shownTop, hiddenTop,
                easeIn((elapsed - holdUntil) / (float) Math.max(1L, slideOutEnd - holdUntil)));
    }

    private static int hudPx(int physicalPixels) {
        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = minecraft == null || minecraft.getWindow() == null
                ? 1.0D : Math.max(1.0D, minecraft.getWindow().getGuiScale());
        return Math.max(1, Math.round((float) (physicalPixels / guiScale)));
    }

    private static int hudPxAtGuiScale2(int guiScale2Units) {
        return hudPx(guiScale2Units * 2);
    }

    private static int wallpaperColor() {
        return switch (Math.floorMod(PhoneSettingsStore.wallpaperIndex(), 9)) {
            case 1 -> 0xFF11132A;
            case 2 -> 0xFF24424B;
            case 3 -> 0xFF353448;
            case 4 -> 0xFF24143E;
            case 5 -> 0xFF102C49;
            case 6 -> 0xFF081C40;
            case 7 -> 0xFF111111;
            case 8 -> 0xFF142D20;
            default -> 0xFF2A1B18;
        };
    }

    private static void drawRoundedRect(GuiGraphics graphics, int left, int top, int right, int bottom,
                                        int radius, int color) {
        if (right <= left || bottom <= top) return;
        int r = Math.max(1, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
        graphics.fill(left + r, top, right - r, bottom, color);
        graphics.fill(left, top + r, right, bottom - r, color);
        fillCircle(graphics, left + r, top + r, r, color);
        fillCircle(graphics, right - r, top + r, r, color);
        fillCircle(graphics, left + r, bottom - r, r, color);
        fillCircle(graphics, right - r, bottom - r, r, color);
    }

    private static void drawHudHandsetIcon(GuiGraphics graphics, int centerX, int centerY,
                                           int physicalWidth, int color, boolean decline) {
        int halfW = Math.max(2, hudPx(physicalWidth / 2));
        int armW = Math.max(1, hudPx(Math.max(3, physicalWidth / 4)));
        int upper = Math.max(1, hudPx(Math.max(4, physicalWidth / 3)));
        int lower = Math.max(1, hudPx(Math.max(2, physicalWidth / 6)));
        graphics.fill(centerX - halfW, centerY - lower,
                centerX - halfW + armW, centerY + lower + 1, color);
        graphics.fill(centerX + halfW - armW, centerY - lower,
                centerX + halfW, centerY + lower + 1, color);
        graphics.fill(centerX - halfW + armW - 1, centerY - upper,
                centerX, centerY, color);
        graphics.fill(centerX, centerY - upper,
                centerX + halfW - armW + 1, centerY, color);
        if (decline) {
            int tab = Math.max(1, halfW / 3);
            graphics.fill(centerX - tab, centerY + lower,
                    centerX + tab + 1, centerY + lower + Math.max(1, hudPx(2)), color);
        }
    }

    private static int lerp(int from, int to, float progress) {
        return Math.round(from + (to - from) * Math.max(0.0F, Math.min(1.0F, progress)));
    }

    private static float easeOut(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    private static float easeIn(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return t * t * t;
    }

    private static void fillCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }
}

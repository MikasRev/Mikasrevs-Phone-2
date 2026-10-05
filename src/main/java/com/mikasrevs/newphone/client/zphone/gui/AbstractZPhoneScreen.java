package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.PhoneCaseStyle;
import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhoneMusicPlayerState;
import com.mikasrevs.newphone.client.gui.PhoneWallpaperCatalog;
import com.mikasrevs.newphone.client.gui.PhoneWallpaperRenderer;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.zphone.ZPhoneHeldModelState;
import com.mikasrevs.newphone.client.zphone.ZPhoneTransitionState;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Shared MPhone shell, mapped to the exact Classic phone glass and case. */
public abstract class AbstractZPhoneScreen extends Screen implements ZPhoneUiScreen {
    protected static final int DESIGN_WIDTH = 300;
    protected static final int DESIGN_HEIGHT = 610;

    private static final int CLASSIC_TEXTURE_WIDTH = 900;
    private static final int CLASSIC_TEXTURE_HEIGHT = 1600;
    private static final int CASE_TEXTURE_HEIGHT = 1760;
    private static final int CASE_TOP_OVERFLOW = CASE_TEXTURE_HEIGHT - CLASSIC_TEXTURE_HEIGHT;
    private static final int CLASSIC_SCREEN_LEFT = 96;
    private static final int CLASSIC_SCREEN_TOP = 78;
    private static final int CLASSIC_SCREEN_RIGHT = 804;
    private static final int CLASSIC_SCREEN_BOTTOM = 1518;
    private static final int CLASSIC_SCREEN_WIDTH = CLASSIC_SCREEN_RIGHT - CLASSIC_SCREEN_LEFT;
    private static final int WORLD_BACKDROP_COLOR = 0x66000000;

    private static float animatedIslandWidth = 78.0F;
    private static long lastIslandFrameAt;

    private boolean controlSwipeTracking;
    private double controlSwipeStartY;
    private float controlSwipeProgress;
    private Path cachedWallpaperPhoto;
    private PhoneGalleryStore.GalleryTexture cachedWallpaperTexture;
    private long cachedWallpaperTextureCheckedAt;

    protected AbstractZPhoneScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        if (!(this instanceof ZPhoneCallScreen)) ZPhoneClient.markPhoneOpened();
        PhoneNavigationState.onShown(this);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        if(com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo()){
            com.mikasrevs.newphone.client.duo.DuoInterface.device(graphics,com.mikasrevs.newphone.client.duo.DuoInterface.layout(width,height));return;
        }
        graphics.fill(0, 0, this.width, this.height, WORLD_BACKDROP_COLOR);
        graphics.fill(frameX(-4), frameY(-4), frameX(DESIGN_WIDTH + 4), frameY(DESIGN_HEIGHT + 4), 0xFF07101A);
        drawClassicFrame(graphics);
    }

    protected DrawData phone() {
        return rawPhone();
    }

    protected float phoneWidthFactor() {
        return 0.54F * PhoneSettingsStore.phoneScale();
    }

    private DrawData rawPhone() {
        if(com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo()){
            var surface=com.mikasrevs.newphone.client.duo.DuoInterface.layout(width,height);
            return new DrawData(surface.x(),surface.y(),surface.size(300),surface.size(610),surface.scale());
        }
        ClassicCaseData c = classicCase();
        float contentScale = c.scale * (CLASSIC_SCREEN_WIDTH / (float) DESIGN_WIDTH);
        int contentX = c.x + Math.round(CLASSIC_SCREEN_LEFT * c.scale);
        int contentY = c.y + Math.round(CLASSIC_SCREEN_TOP * c.scale);
        int contentWidth = Math.max(1, Math.round(DESIGN_WIDTH * contentScale));
        int contentHeight = Math.max(1, Math.round(DESIGN_HEIGHT * contentScale));
        return new DrawData(contentX, contentY, contentWidth, contentHeight, contentScale);
    }

    private ClassicCaseData classicCase() {
        PhoneCaseStyle style = currentCaseStyle();
        int accessoryTop = style == PhoneCaseStyle.BUNNY_BLUSH || style == PhoneCaseStyle.BUNNY_BLOCK
                ? CASE_TOP_OVERFLOW : 0;
        float widthFactor = phoneWidthFactor();
        float heightFactor = Math.min(0.985F, 0.96F * PhoneSettingsStore.phoneScale());
        float scale = Math.min(
                (this.width * widthFactor) / CLASSIC_TEXTURE_WIDTH,
                (this.height * heightFactor) / (CLASSIC_TEXTURE_HEIGHT + accessoryTop)
        );
        int drawWidth = Math.max(1, Math.round(CLASSIC_TEXTURE_WIDTH * scale));
        int drawHeight = Math.max(1, Math.round(CLASSIC_TEXTURE_HEIGHT * scale));
        int overflow = Math.round(accessoryTop * scale);
        int drawY = (this.height - drawHeight - overflow) / 2 + overflow;
        return new ClassicCaseData((this.width - drawWidth) / 2, drawY,
                drawWidth, drawHeight, scale);
    }

    private int frameX(float designX) {
        DrawData d = rawPhone();
        return d.x + Math.round(designX * d.scale);
    }

    private int frameY(float designY) {
        DrawData d = rawPhone();
        return d.y + Math.round(designY * d.scale);
    }

    private int frameSize(float designSize) {
        return Math.max(1, Math.round(designSize * rawPhone().scale));
    }

    private boolean frameInside(double mouseX, double mouseY, float left, float top, float right, float bottom) {
        return mouseX >= frameX(left) && mouseX <= frameX(right)
                && mouseY >= frameY(top) && mouseY <= frameY(bottom);
    }

    protected int x(float designX) {
        DrawData d = phone();
        return d.x + Math.round(designX * d.scale);
    }

    protected int y(float designY) {
        DrawData d = phone();
        return d.y + Math.round(designY * d.scale);
    }

    protected int size(float designSize) {
        return Math.max(1, Math.round(designSize * phone().scale));
    }

    /**
     * Finishes the underlying screen before an opaque modal is submitted.
     * Minecraft batches GUI text separately, so opacity alone is not enough
     * when base text and a later backdrop remain in the same render batch.
     */
    protected final void drawOpaqueModalBackdrop(
            GuiGraphics graphics, int left, int top, int right, int bottom, int color
    ) {
        graphics.flush();
        graphics.fill(left, top, right, bottom, color | 0xFF000000);
        graphics.flush();
    }

    protected final void beginOverlayLayer(GuiGraphics graphics) {
        graphics.flush();
    }

    protected final void finishOverlayBackground(GuiGraphics graphics) {
        graphics.flush();
    }

    protected final void drawControlCenterScrim(GuiGraphics graphics, float reveal) {
        float progress = Math.max(0.0F, Math.min(1.0F, reveal));
        if (progress <= 0.0F) return;

        // Keep the live app visible under Control Center. Minecraft 1.20.1 does
        // not expose a cheap per-widget blur pass, so a layered frosted veil is
        // used here instead of repainting the wallpaper and erasing app content.
        graphics.flush();
        int darkAlpha = Math.round(132.0F * progress);
        graphics.fill(frameX(0), frameY(0), frameX(DESIGN_WIDTH), frameY(DESIGN_HEIGHT), darkAlpha << 24);

        // A faint neutral haze gives the background a frosted/iOS-like look
        // without introducing stripes or letting source labels compete with CC text.
        int hazeAlpha = Math.round(18.0F * progress);
        if (hazeAlpha > 0) {
            graphics.fill(frameX(0), frameY(0), frameX(DESIGN_WIDTH), frameY(DESIGN_HEIGHT),
                    (hazeAlpha << 24) | 0x00FFFFFF);
        }
        graphics.flush();
    }

    protected boolean inside(double mouseX, double mouseY, float left, float top, float right, float bottom) {
        return mouseX >= x(left) && mouseX <= x(right) && mouseY >= y(top) && mouseY <= y(bottom);
    }

    protected void drawFrame(GuiGraphics graphics) {
        if(com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo()){
            com.mikasrevs.newphone.client.duo.DuoInterface.frame(graphics,com.mikasrevs.newphone.client.duo.DuoInterface.layout(width,height),this);return;
        }
        drawClassicFrame(graphics);
    }

    private void drawClassicFrame(GuiGraphics graphics) {
        ClassicCaseData c = classicCase();
        graphics.blit(com.mikasrevs.newphone.client.phone.model.ClientPhoneModels.local().frame(), c.x, c.y, c.width, c.height,
                0.0F, 0.0F, CLASSIC_TEXTURE_WIDTH, CLASSIC_TEXTURE_HEIGHT,
                CLASSIC_TEXTURE_WIDTH, CLASSIC_TEXTURE_HEIGHT);
        drawCaseOverlay(graphics, c);
    }

    private void drawCaseOverlay(GuiGraphics graphics, ClassicCaseData c) {
        String model = com.mikasrevs.newphone.client.phone.model.ClientPhoneModels.local().id();
        ResourceLocation texture = com.mikasrevs.newphone.phone.model.PhoneCaseTextures.frame(model, currentCaseStyle());
        if (texture == null) return;

        int overflow = Math.round(CASE_TOP_OVERFLOW * c.scale);
        graphics.blit(texture, c.x, c.y - overflow, c.width, c.height + overflow,
                0.0F, 0.0F, CLASSIC_TEXTURE_WIDTH, CASE_TEXTURE_HEIGHT,
                CLASSIC_TEXTURE_WIDTH, CASE_TEXTURE_HEIGHT);
    }

    private PhoneCaseStyle currentCaseStyle() {
        return PhoneCaseStyle.fromStack(PhoneInventoryUtil.findPhone(minecraft == null ? null : minecraft.player));
    }

    private boolean showsWallpaperSurface() {
        String name = getClass().getSimpleName();
        return name.equals("ZPhoneHomeScreen")
                || name.equals("ZPhoneLockScreen")
                || name.equals("ZPhoneCameraScreen")
                || name.equals("ZPhoneCallScreen")
                || name.equals("ZPhoneControlCenterScreen")
                || name.equals("ZPhonePinEntryScreen")
                || name.equals("ZPhonePinSetupScreen")
                || name.equals("ZPhoneSetupScreen");
    }

    private void drawFullAppSurface(GuiGraphics graphics, int left, int top, int right, int bottom) {
        int total = Math.max(1, bottom - top);
        int band = Math.max(2, frameSize(3));
        for (int py = top; py < bottom; py += band) {
            int next = Math.min(bottom, py + band);
            float t = ((py + next) * 0.5F - top) / total;
            graphics.fill(left, py, right, next, blend(0xFF071018, 0xFF05080D, t));
        }
        graphics.fill(left, top, right, top + frameSize(2), 0x12FFFFFF);
        fillCircle(graphics, frameX(255), frameY(105), frameSize(86), 0x140F223A);
        fillCircle(graphics, frameX(62), frameY(500), frameSize(110), 0x100A1730);
    }

    protected void drawWallpaper(GuiGraphics graphics) {
        if(com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo()){
            com.mikasrevs.newphone.client.duo.DuoInterface.wallpaper(graphics,com.mikasrevs.newphone.client.duo.DuoInterface.layout(width,height),com.mikasrevs.newphone.client.duo.DuoInterface.wide(this),this instanceof com.mikasrevs.newphone.client.duo.DuoLockScreen||this instanceof AbstractZPhonePinScreen);return;
        }
        int left = frameX(-4);
        int top = frameY(-4);
        int right = frameX(304);
        int bottom = frameY(614);
        int clipLeft = frameX(0);
        int clipTop = frameY(0);
        int clipRight = frameX(300);
        int clipBottom = frameY(610);

        if (!showsWallpaperSurface()) {
            drawFullAppSurface(graphics, left, top, right, bottom);
            return;
        }

        boolean lockWallpaper = this instanceof ZPhoneLockScreen || this instanceof AbstractZPhonePinScreen;
        ResourceLocation texture = null;
        int textureWidth = PhoneWallpaperCatalog.TEXTURE_WIDTH;
        int textureHeight = PhoneWallpaperCatalog.TEXTURE_HEIGHT;
        Path photo = PhoneSettingsStore.wallpaperPhoto(lockWallpaper);
        if (photo != null) {
            PhoneGalleryStore.GalleryTexture galleryTexture = wallpaperTexture(photo);
            if (galleryTexture != null) {
                texture = galleryTexture.location();
                textureWidth = galleryTexture.width();
                textureHeight = galleryTexture.height();
            }
        }
        if (texture == null) {
            texture = PhoneWallpaperCatalog.texture(PhoneSettingsStore.wallpaperIndex(lockWallpaper));
        }

        PhoneWallpaperRenderer.blitCover(
                graphics,
                texture,
                clipLeft,
                clipTop,
                clipRight,
                clipBottom,
                textureWidth,
                textureHeight
        );
        drawWallpaperScrim(graphics, clipLeft, clipTop, clipRight, clipBottom);
    }

    private void drawWallpaperScrim(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, 0x22000000);

        int band = Math.max(2, frameSize(8));
        int upperBottom = frameY(210);
        int upperHeight = Math.max(1, upperBottom - top);
        for (int py = top; py < upperBottom; py += band) {
            int next = Math.min(upperBottom, py + band);
            float progress = ((py + next) * 0.5F - top) / upperHeight;
            int alpha = Math.round(76.0F * (1.0F - progress));
            graphics.fill(left, py, right, next, alpha << 24);
        }

        int lowerTop = frameY(405);
        int lowerHeight = Math.max(1, bottom - lowerTop);
        for (int py = lowerTop; py < bottom; py += band) {
            int next = Math.min(bottom, py + band);
            float progress = ((py + next) * 0.5F - lowerTop) / lowerHeight;
            int alpha = Math.round(62.0F * progress);
            graphics.fill(left, py, right, next, alpha << 24);
        }
    }

    private PhoneGalleryStore.GalleryTexture wallpaperTexture(Path photo) {
        long now = System.currentTimeMillis();
        boolean samePhoto = photo.equals(cachedWallpaperPhoto);
        if (samePhoto && now - cachedWallpaperTextureCheckedAt < 1500L) return cachedWallpaperTexture;
        cachedWallpaperPhoto = photo;
        cachedWallpaperTextureCheckedAt = now;
        cachedWallpaperTexture = PhoneGalleryStore.textureFor(minecraft, photo);
        return cachedWallpaperTexture;
    }

    protected void drawStatusBar(GuiGraphics graphics) {
        drawStatusBar(graphics, 0xFFFFFFFF, 0xFF11161D);
    }

    protected void drawStatusBar(GuiGraphics graphics, int foregroundColor, int batteryBackground) {
        if(com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo()){
            com.mikasrevs.newphone.client.duo.DuoInterface.status(graphics,com.mikasrevs.newphone.client.duo.DuoInterface.layout(width,height));return;
        }
        long now = System.currentTimeMillis();
        String callStatus = PhoneClientData.callStatus();
        boolean hasCall = !"IDLE".equals(callStatus);
        boolean music = !hasCall && PhoneMusicPlayerState.isPlaying();

        float targetWidth = hasCall ? 136.0F : music ? 116.0F : 78.0F;
        if (lastIslandFrameAt <= 0L) lastIslandFrameAt = now;
        long frameDelta = Math.max(1L, Math.min(80L, now - lastIslandFrameAt));
        lastIslandFrameAt = now;
        animatedIslandWidth += (targetWidth - animatedIslandWidth) * Math.min(1.0F, frameDelta / 120.0F);

        int islandWidth = Math.round(animatedIslandWidth);
        int islandLeft = 150 - islandWidth / 2;
        int islandRight = 150 + islandWidth / 2;
        boolean expanded = hasCall || music;
        int islandTop = expanded ? 11 : 14;
        int islandBottom = expanded ? 38 : 34;
        int islandColor = hasCall ? 0xFF071F15 : music ? 0xFF17131F : 0xFF030405;

        drawStatusBarSides(graphics, islandLeft - 4, foregroundColor, batteryBackground);

        drawRoundedRect(graphics, frameX(islandLeft - 1), frameY(islandTop + 1),
                frameX(islandRight + 1), frameY(islandBottom + 2), frameSize(13), 0x52000000);
        drawRoundedRect(graphics, frameX(islandLeft), frameY(islandTop),
                frameX(islandRight), frameY(islandBottom), frameSize(12), islandColor);

        if (hasCall) {
            int callColor = "OUTGOING".equals(callStatus) ? 0xFFFF9F0A : 0xFF31D158;
            fillCircle(graphics, frameX(islandLeft + 14), frameY(24), frameSize(5), callColor);
            String peer = PhoneClientData.callPeerName();
            if (peer == null || peer.isBlank()) peer = PhoneSettingsStore.text("Arama", "Call");
            if (peer.length() > 11) peer = peer.substring(0, 10) + "...";
            PhoneModernText.drawColored(graphics, peer, frameX(islandLeft + 26), frameY(16), frameSize(7), 0xFFFFFFFF);
            String callDetail = switch (callStatus) {
                case "OUTGOING" -> PhoneSettingsStore.text("Aranıyor", "Calling");
                case "INCOMING" -> PhoneSettingsStore.text("Gelen", "Incoming");
                default -> callDuration();
            };
            PhoneModernText.drawColored(graphics, callDetail, frameX(islandLeft + 26), frameY(27), frameSize(6), 0xFF9FE7B4);
            PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Dokun", "Tap"), frameX(islandRight - 28), frameY(20), frameSize(5), 0xFF8FE5AC);
        } else if (music) {
            fillCircle(graphics, frameX(islandLeft + 14), frameY(24), frameSize(5), 0xFFFF4FC3);
            String title = PhoneMusicPlayerState.currentTrack().title();
            if (title.length() > 12) title = title.substring(0, 11) + "...";
            PhoneModernText.drawColored(graphics, title, frameX(islandLeft + 26), frameY(18), frameSize(8), 0xFFFFFFFF);
            int barLeft = islandRight - 30;
            for (int i = 0; i < 3; i++) {
                int h = 5 + ((int) (now / 180L + i) % 3) * 3;
                graphics.fill(frameX(barLeft + i * 5), frameY(29 - h),
                        frameX(barLeft + i * 5 + 2), frameY(29), 0xFFFF87D9);
            }
        } else {
            fillCircle(graphics, frameX(islandRight - 11), frameY(24), frameSize(2), 0xFF1C2A3D);
        }
    }

    protected int totalAttentionCount() {
        int total = unreadMessageCount() + com.mikasrevs.newphone.marketplace.client.MarketplaceClient.unread();
        if (total >= 99) return 99;
        String self = PhoneClientData.selfUuid();
        total += PhoneBlockGramStore.unreadNotificationCount(self) + PhoneClientData.totalUnreadGramCount();
        total += PhoneSocialClientData.unreadNotificationCount("BLOCKPOST", self);
        total += PhoneSocialClientData.unreadNotificationCount("TINDER", self);
        total += PhoneClientData.totalUnreadTinderCount();
        return Math.min(99, total);
    }

    private int unreadMessageCount() {
        int total = 0;
        for (var contact : PhoneClientData.sortedMessagePeers()) {
            total += PhoneClientData.unreadCount(contact.uuid);
            if (total >= 99) return 99;
        }
        return total;
    }

    private List<PhoneApp> statusNotificationApps() {
        String self = PhoneClientData.selfUuid();
        List<PhoneApp> apps = new ArrayList<>(4);
        if (unreadMessageCount() > 0) apps.add(PhoneApp.MESSAGES);
        if (com.mikasrevs.newphone.marketplace.client.MarketplaceClient.unread() > 0) apps.add(PhoneApp.MARKETPLACE);
        if (PhoneBlockGramStore.unreadNotificationCount(self) + PhoneClientData.totalUnreadGramCount() > 0) {
            apps.add(PhoneApp.BLOCKGRAM);
        }
        if (PhoneSocialClientData.unreadNotificationCount("BLOCKPOST", self) > 0) {
            apps.add(PhoneApp.BLOCKPOST);
        }
        if (PhoneSocialClientData.unreadNotificationCount("TINDER", self)
                + PhoneClientData.totalUnreadTinderCount() > 0) {
            apps.add(PhoneApp.BROWSER);
        }
        return apps;
    }

    private void drawStatusBarSides(GuiGraphics graphics, int notificationRight,
                                    int foregroundColor, int batteryBackground) {
        String time = com.mikasrevs.newphone.client.gui.PhoneTimeDisplay.time(false);
        PhoneModernText.drawColored(graphics, time, frameX(20), frameY(19), frameSize(10), foregroundColor);

        List<PhoneApp> apps = statusNotificationApps();
        int iconDesignSize = 11;
        int iconGap = 2;
        int firstLeft = 55;
        int capacity = Math.max(0, (notificationRight - firstLeft + iconGap) / (iconDesignSize + iconGap));
        int visible = Math.min(apps.size(), capacity);
        int iconSize = frameSize(iconDesignSize);
        for (int i = 0; i < visible; i++) {
            int left = firstLeft + i * (iconDesignSize + iconGap);
            MPhoneAppIconRenderer.draw(graphics, apps.get(i), frameX(left), frameY(18), iconSize, false);
            fillCircle(graphics, frameX(left + 10), frameY(19), frameSize(2), 0xFFFF3B30);
        }

        boolean networkEnabled = !PhoneSettingsStore.airplaneMode();
        drawUiGlyph(graphics,
                networkEnabled ? MPhoneUiGlyphRenderer.Glyph.SIGNAL : MPhoneUiGlyphRenderer.Glyph.SIGNAL_OFF,
                207, 23, 20, foregroundColor);
        boolean muted = PhoneSettingsStore.doNotDisturb()
                || !PhoneSettingsStore.messageNotifications()
                || PhoneSettingsStore.phoneVolume() <= 0.01F;
        drawUiGlyph(graphics,
                muted ? MPhoneUiGlyphRenderer.Glyph.SOUND_OFF : MPhoneUiGlyphRenderer.Glyph.SOUND,
                224, 24, 19, foregroundColor);
        boolean wifiOff = !networkEnabled || !PhoneSettingsStore.wifiEnabled();
        drawUiGlyph(graphics,
                wifiOff ? MPhoneUiGlyphRenderer.Glyph.WIFI_OFF : MPhoneUiGlyphRenderer.Glyph.WIFI,
                244, 23, 21, foregroundColor);
        drawUiGlyph(graphics, MPhoneUiGlyphRenderer.Glyph.BATTERY,
                269, 23, 26, foregroundColor);
    }

    protected final void drawControlCenterContents(GuiGraphics graphics, int mouseX, int mouseY) {
        int accent = PhoneSettingsStore.accentColor();
        int glass = 0xE51A2028;
        int glassStrong = 0xEF202731;

        drawStatusBarSides(graphics, 108, 0xFFF7F9FC, 0xFF202731);
        drawRoundedRect(graphics, x(132), y(19), x(168), y(22), size(2), 0x66FFFFFF);

        drawRoundedRect(graphics, x(18), y(47), x(145), y(187), size(20), glass);
        drawRoundedRect(graphics, x(19), y(48), x(144), y(50), size(1), 0x18FFFFFF);
        drawConnectivityToggle(graphics, mouseX, mouseY, 51, 78,
                PhoneSettingsStore.text("Uçuş", "Airplane"), PhoneSettingsStore.airplaneMode(),
                0xFFFF9F0A, ControlGlyph.AIRPLANE, false);
        drawConnectivityToggle(graphics, mouseX, mouseY, 112, 78, "Wi-Fi",
                PhoneSettingsStore.wifiEnabled() && !PhoneSettingsStore.airplaneMode(),
                0xFF0A84FF, ControlGlyph.WIFI,
                !PhoneSettingsStore.wifiEnabled() || PhoneSettingsStore.airplaneMode());
        drawConnectivityToggle(graphics, mouseX, mouseY, 51, 138, "Bluetooth",
                PhoneSettingsStore.bluetoothEnabled() && !PhoneSettingsStore.airplaneMode(),
                0xFF0A84FF, ControlGlyph.BLUETOOTH, false);
        boolean soundEnabled = PhoneSettingsStore.messageNotifications() && PhoneSettingsStore.phoneVolume() > 0.01F;
        drawConnectivityToggle(graphics, mouseX, mouseY, 112, 138,
                PhoneSettingsStore.text("Ses", "Sound"), soundEnabled,
                0xFF30D158, ControlGlyph.SOUND, !soundEnabled);

        drawRoundedRect(graphics, x(155), y(47), x(282), y(187), size(20), glassStrong);
        drawRoundedRect(graphics, x(156), y(48), x(281), y(50), size(1), 0x1FFFFFFF);
        int musicIconSize = size(34);
        MPhoneAppIconRenderer.draw(graphics, PhoneApp.MUSIC, x(167), y(60), musicIconSize, false);
        String title = PhoneMusicPlayerState.currentTrack().title();
        String artist = PhoneMusicPlayerState.currentTrack().artist();
        PhoneModernText.drawColoredFitted(graphics, title, x(207), y(63), size(8), size(63), 0xFFFFFFFF);
        PhoneModernText.drawColoredFitted(graphics, artist, x(207), y(82), size(6), size(63), 0xFFA9B2BD);
        drawRoundedRect(graphics, x(169), y(112), x(268), y(115), size(2), 0x3DFFFFFF);
        int progressRight = 169 + Math.round(99.0F * PhoneMusicPlayerState.progress());
        if (progressRight > 169) {
            drawRoundedRect(graphics, x(169), y(112), x(progressRight), y(115), size(2), 0xE6FFFFFF);
        }
        drawUiGlyph(graphics, MPhoneUiGlyphRenderer.Glyph.PREVIOUS,
                181, 149, 22, 0xFFF7F9FC);
        boolean playHover = inside(mouseX, mouseY, 199, 128, 239, 170);
        fillCircle(graphics, x(219), y(149), size(19), playHover ? 0xFFFFFFFF : 0xFFF0F3F6);
        drawUiGlyph(graphics,
                PhoneMusicPlayerState.isPlaying()
                        ? MPhoneUiGlyphRenderer.Glyph.PAUSE
                        : MPhoneUiGlyphRenderer.Glyph.PLAY,
                219, 149, 21, 0xFF111820);
        drawUiGlyph(graphics, MPhoneUiGlyphRenderer.Glyph.NEXT,
                257, 149, 22, 0xFFF7F9FC);

        drawModeTile(graphics, mouseX, mouseY, 18, 197, 107, 353,
                PhoneSettingsStore.text("Odak", "Focus"),
                PhoneSettingsStore.doNotDisturb()
                        ? PhoneSettingsStore.text("Açık", "On")
                        : PhoneSettingsStore.text("Kapalı", "Off"),
                PhoneSettingsStore.doNotDisturb(), 0xFF7657E8, ControlGlyph.MOON);

        drawVerticalControl(graphics, mouseX, mouseY, 117, 197, 190, 353,
                PhoneSettingsStore.brightness(), 0xFFF4F6F8, ControlGlyph.SUN);
        drawVerticalControl(graphics, mouseX, mouseY, 201, 197, 274, 353,
                PhoneSettingsStore.phoneVolume(), 0xFFAFCBFF, ControlGlyph.SOUND);

        boolean notificationsHover = inside(mouseX, mouseY, 18, 367, 282, 445);
        drawRoundedRect(graphics, x(18), y(367), x(282), y(445), size(18),
                notificationsHover ? 0xF02A323D : glass);
        int unread = totalAttentionCount();
        int notificationColor = unread > 0 ? accent : 0xFF3A434E;
        fillCircle(graphics, x(47), y(406), size(17), notificationColor);
        drawUiGlyph(graphics, MPhoneUiGlyphRenderer.Glyph.BELL,
                47, 406, 22, 0xFFFFFFFF);
        PhoneModernText.drawColoredFitted(graphics,
                PhoneSettingsStore.text("Bildirimler", "Notifications"),
                x(72), y(382), size(9), size(112), 0xFFFFFFFF);
        String unreadText = unread == 0
                ? PhoneSettingsStore.text("Yeni etkinlik yok", "No new activity")
                : unread + PhoneSettingsStore.text(" okunmamış", " unread");
        PhoneModernText.drawColoredFitted(graphics, unreadText,
                x(72), y(406), size(6), size(112), 0xFFA9B2BD);
        drawNotificationAppRow(graphics, 190, 395);

        drawGlyphLine(graphics, 143, 546, 150, 539, 1.2F, 0x7FFFFFFF);
        drawGlyphLine(graphics, 150, 539, 157, 546, 1.2F, 0x7FFFFFFF);
    }

    private void drawConnectivityToggle(GuiGraphics graphics, int mouseX, int mouseY,
                                        int centerX, int centerY, String label, boolean active,
                                        int activeColor, ControlGlyph glyph, boolean crossed) {
        boolean hover = inside(mouseX, mouseY, centerX - 25, centerY - 23, centerX + 25, centerY + 31);
        int circleColor = active ? activeColor : hover ? 0xFF48515C : 0xFF353D47;
        fillCircle(graphics, x(centerX), y(centerY), size(17), circleColor);
        drawControlGlyph(graphics, glyph, centerX, centerY, 9, 0xFFFFFFFF, crossed);
        PhoneModernText.drawCenteredColoredFitted(graphics, label, x(centerX), y(centerY + 23),
                size(6), size(54), active ? 0xFFFFFFFF : 0xFFB7C0CA);
    }

    private void drawModeTile(GuiGraphics graphics, int mouseX, int mouseY,
                              int left, int top, int right, int bottom,
                              String title, String detail, boolean active,
                              int activeColor, ControlGlyph glyph) {
        boolean hover = inside(mouseX, mouseY, left, top, right, bottom);
        drawRoundedRect(graphics, x(left), y(top), x(right), y(bottom), size(17),
                hover ? 0xEF2B333E : 0xE51A2028);
        drawRoundedRect(graphics, x(left + 1), y(top + 1), x(right - 1), y(top + 3),
                size(1), 0x18FFFFFF);
        int centerX = (left + right) / 2;
        int circleColor = active ? activeColor : 0xFF39424D;
        fillCircle(graphics, x(centerX), y(top + 50), size(22), circleColor);
        drawControlGlyph(graphics, glyph, centerX, top + 50, 11,
                0xFFFFFFFF, false);
        PhoneModernText.drawCenteredColoredFitted(graphics, title, x(centerX), y(top + 94),
                size(10), size(right - left - 18), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColoredFitted(graphics, detail, x(centerX), y(top + 121),
                size(7), size(right - left - 18), active ? 0xFFD9D1FF : 0xFFA9B2BD);
        fillCircle(graphics, x(centerX), y(bottom - 14), size(2),
                active ? activeColor : 0xFF59636F);
    }

    private void drawVerticalControl(GuiGraphics graphics, int mouseX, int mouseY,
                                     int left, int top, int right, int bottom,
                                     float rawValue, int fillColor, ControlGlyph glyph) {
        float value = Math.max(0.0F, Math.min(1.0F, rawValue));
        boolean hover = inside(mouseX, mouseY, left, top, right, bottom);
        drawRoundedRect(graphics, x(left), y(top), x(right), y(bottom), size(20),
                hover ? 0xFF343C46 : 0xFF272E37);
        int fillTop = bottom - Math.round((bottom - top) * value);
        if (fillTop < bottom) {
            drawVerticalControlFill(graphics, left, top, right, bottom, fillTop, fillColor);
        }
        drawRoundedRect(graphics, x(left), y(top), x(right), y(top + 2), size(1), 0x24FFFFFF);
        int iconY = bottom - 27;
        int iconColor = fillTop <= iconY + 9 ? 0xFF27313D : 0xFFF6F8FB;
        drawControlGlyph(graphics, glyph, (left + right) / 2, iconY, 9,
                iconColor, value <= 0.01F && glyph == ControlGlyph.SOUND);
    }

    private void drawVerticalControlFill(GuiGraphics graphics, int left, int top,
                                         int right, int bottom, int fillTop, int color) {
        int pixelLeft = x(left);
        int pixelTop = y(top);
        int pixelRight = x(right);
        int pixelBottom = y(bottom);
        int pixelFillTop = Math.max(pixelTop, y(fillTop));
        int cornerRadius = size(20);
        for (int py = pixelFillTop; py < pixelBottom; py++) {
            int inset = roundedInset(py, pixelTop, pixelBottom, cornerRadius);
            if (pixelLeft + inset < pixelRight - inset) {
                graphics.fill(pixelLeft + inset, py, pixelRight - inset, py + 1, color);
            }
        }
    }

    private void drawNotificationAppRow(GuiGraphics graphics, int left, int top) {
        List<PhoneApp> apps = statusNotificationApps();
        int count = Math.min(4, apps.size());
        int iconDesignSize = 18;
        for (int i = 0; i < count; i++) {
            int iconSize = size(iconDesignSize);
            MPhoneAppIconRenderer.draw(graphics, apps.get(i),
                    x(left + i * 21), y(top), iconSize, false);
            fillCircle(graphics, x(left + i * 21 + 16), y(top + 2), size(2), 0xFFFF3B30);
        }
        if (count == 0) {
            fillCircle(graphics, x(left + 49), y(top + 11), size(13), 0xFF303944);
            drawGlyphLine(graphics, left + 43, top + 11, left + 48, top + 16, 1.2F, 0xFF8BD5A7);
            drawGlyphLine(graphics, left + 48, top + 16, left + 56, top + 6, 1.2F, 0xFF8BD5A7);
        }
    }

    private void drawControlGlyph(GuiGraphics graphics, ControlGlyph glyph,
                                  int centerX, int centerY, int radius,
                                  int color, boolean crossed) {
        MPhoneUiGlyphRenderer.Glyph resolved = switch (glyph) {
            case AIRPLANE -> MPhoneUiGlyphRenderer.Glyph.AIRPLANE;
            case WIFI -> crossed
                    ? MPhoneUiGlyphRenderer.Glyph.WIFI_OFF
                    : MPhoneUiGlyphRenderer.Glyph.WIFI;
            case BLUETOOTH -> MPhoneUiGlyphRenderer.Glyph.BLUETOOTH;
            case SOUND -> crossed
                    ? MPhoneUiGlyphRenderer.Glyph.SOUND_OFF
                    : MPhoneUiGlyphRenderer.Glyph.SOUND;
            case MOON -> MPhoneUiGlyphRenderer.Glyph.MOON;
            case SUN -> MPhoneUiGlyphRenderer.Glyph.SUN;
        };
        drawUiGlyph(graphics, resolved, centerX, centerY,
                Math.max(14, Math.round(radius * 2.55F)), color);
    }

    private void drawUiGlyph(GuiGraphics graphics, MPhoneUiGlyphRenderer.Glyph glyph,
                             int centerX, int centerY, int designSize, int color) {
        int renderedSize = size(designSize);
        MPhoneUiGlyphRenderer.draw(
                graphics,
                glyph,
                x(centerX) - renderedSize / 2,
                y(centerY) - renderedSize / 2,
                renderedSize,
                color
        );
    }

    private void drawAirplaneGlyph(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int body = Math.max(1, radius / 5);
        drawRoundedRect(graphics, x(centerX - radius), y(centerY - body),
                x(centerX + radius), y(centerY + body + 1), size(body), color);
        drawGlyphLine(graphics, centerX - 1, centerY,
                centerX + 3, centerY - radius, 1.4F, color);
        drawGlyphLine(graphics, centerX - 1, centerY,
                centerX + 3, centerY + radius, 1.4F, color);
        drawGlyphLine(graphics, centerX - radius + 2, centerY,
                centerX - radius, centerY - radius * 0.45F, 1.2F, color);
        drawGlyphLine(graphics, centerX - radius + 2, centerY,
                centerX - radius, centerY + radius * 0.45F, 1.2F, color);
    }

    private void drawBluetoothGlyph(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        drawGlyphLine(graphics, centerX, centerY - radius, centerX, centerY + radius, 1.3F, color);
        drawGlyphLine(graphics, centerX, centerY - radius, centerX + radius * 0.65F, centerY - radius * 0.35F, 1.3F, color);
        drawGlyphLine(graphics, centerX + radius * 0.65F, centerY - radius * 0.35F, centerX - radius * 0.55F, centerY + radius * 0.65F, 1.3F, color);
        drawGlyphLine(graphics, centerX - radius * 0.55F, centerY - radius * 0.65F, centerX + radius * 0.65F, centerY + radius * 0.35F, 1.3F, color);
        drawGlyphLine(graphics, centerX + radius * 0.65F, centerY + radius * 0.35F, centerX, centerY + radius, 1.3F, color);
    }

    private void drawWifiGlyph(GuiGraphics graphics, int centerX, int baseY,
                               int radius, int color, boolean off) {
        int resolvedColor = off ? withAlpha(color, 105) : color;
        drawWifiArc(graphics, centerX, baseY, radius, 1.2F, resolvedColor);
        drawWifiArc(graphics, centerX, baseY, Math.max(3, Math.round(radius * 0.58F)), 1.2F, resolvedColor);
        fillCircle(graphics, x(centerX), y(baseY), size(Math.max(1, radius / 5)), resolvedColor);
        if (off) {
            drawGlyphLine(graphics, centerX - radius * 0.72F, baseY - radius * 0.88F,
                    centerX + radius * 0.72F, baseY + radius * 0.18F, 1.2F, 0xFFFF6B6B);
        }
    }

    private void drawWifiArc(GuiGraphics graphics, int centerX, int baseY,
                             int radius, float stroke, int color) {
        for (int angle = 205; angle <= 335; angle += 5) {
            double radians = Math.toRadians(angle);
            float px = centerX + (float) Math.cos(radians) * radius;
            float py = baseY + (float) Math.sin(radians) * radius;
            fillCircle(graphics, x(px), y(py), Math.max(1, size(stroke) / 2), color);
        }
    }

    private void drawSignalGlyph(GuiGraphics graphics, int left, int bottom,
                                 int width, int color, boolean enabled) {
        int resolvedColor = enabled ? color : withAlpha(color, 95);
        int barWidth = Math.max(2, width / 6);
        for (int i = 0; i < 4; i++) {
            int height = 3 + i * 3;
            int barLeft = left + i * (barWidth + 1);
            drawRoundedRect(graphics, x(barLeft), y(bottom - height),
                    x(barLeft + barWidth), y(bottom), size(1), resolvedColor);
        }
        if (!enabled) {
            drawGlyphLine(graphics, left, bottom - 11, left + width, bottom, 1.0F, 0xFFFF6B6B);
        }
    }

    private void drawSpeakerGlyph(GuiGraphics graphics, int centerX, int centerY,
                                  int radius, int color, boolean muted) {
        int half = Math.max(2, radius / 2);
        graphics.fill(x(centerX - radius), y(centerY - half),
                x(centerX - radius / 2), y(centerY + half + 1), color);
        for (int dy = -radius; dy <= radius; dy++) {
            int inset = Math.abs(dy) * Math.max(1, radius / 4) / Math.max(1, radius);
            graphics.fill(x(centerX - radius / 2), y(centerY + dy),
                    x(centerX + 1 - inset), y(centerY + dy + 1), color);
        }
        if (muted) {
            drawGlyphLine(graphics, centerX - radius * 0.72F, centerY - radius * 0.82F,
                    centerX + radius * 0.85F, centerY + radius * 0.82F, 1.3F, 0xFFFF6B6B);
            return;
        }
        drawGlyphLine(graphics, centerX + 2, centerY - radius * 0.55F,
                centerX + radius * 0.55F, centerY - radius * 0.18F, 1.0F, color);
        drawGlyphLine(graphics, centerX + radius * 0.55F, centerY - radius * 0.18F,
                centerX + radius * 0.55F, centerY + radius * 0.18F, 1.0F, color);
        drawGlyphLine(graphics, centerX + radius * 0.55F, centerY + radius * 0.18F,
                centerX + 2, centerY + radius * 0.55F, 1.0F, color);
    }

    private void drawMoonGlyph(GuiGraphics graphics, int centerX, int centerY,
                               int radius, int color, int background) {
        fillCircle(graphics, x(centerX), y(centerY), size(radius), color);
        fillCircle(graphics, x(centerX + radius * 0.45F), y(centerY - radius * 0.35F),
                size(Math.max(2, radius - 2)), background);
    }

    private void drawSunGlyph(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        fillCircle(graphics, x(centerX), y(centerY), size(Math.max(2, radius / 3)), color);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0D * i / 8.0D;
            float innerX = centerX + (float) Math.cos(angle) * radius * 0.58F;
            float innerY = centerY + (float) Math.sin(angle) * radius * 0.58F;
            float outerX = centerX + (float) Math.cos(angle) * radius;
            float outerY = centerY + (float) Math.sin(angle) * radius;
            drawGlyphLine(graphics, innerX, innerY, outerX, outerY, 1.0F, color);
        }
    }

    private void drawBellGlyph(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int domeRadius = Math.max(3, radius - 3);
        fillCircle(graphics, x(centerX), y(centerY - 2), size(domeRadius), color);
        graphics.fill(x(centerX - domeRadius), y(centerY - 2),
                x(centerX + domeRadius + 1), y(centerY + radius - 2), color);
        drawRoundedRect(graphics, x(centerX - radius), y(centerY + radius - 3),
                x(centerX + radius), y(centerY + radius), size(1), color);
        drawRoundedRect(graphics, x(centerX - 2), y(centerY - radius - 1),
                x(centerX + 2), y(centerY - radius + 2), size(1), color);
        fillCircle(graphics, x(centerX), y(centerY + radius + 2), size(2), color);
    }

    private void drawPreviousNextGlyph(GuiGraphics graphics, int centerX, int centerY,
                                       int radius, int color, boolean next) {
        int direction = next ? 1 : -1;
        int barX = centerX + direction * radius;
        graphics.fill(x(barX - (next ? 1 : 0)), y(centerY - radius),
                x(barX + (next ? 1 : 2)), y(centerY + radius + 1), color);
        for (int dy = -radius; dy <= radius; dy++) {
            int width = radius - Math.abs(dy) + 1;
            int from = next ? centerX - width : centerX;
            int to = next ? centerX : centerX + width;
            graphics.fill(x(from), y(centerY + dy), x(to + 1), y(centerY + dy + 1), color);
        }
    }

    private void drawPlayTriangle(GuiGraphics graphics, int left, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int width = radius - Math.abs(dy) + 1;
            graphics.fill(x(left), y(centerY + dy), x(left + width), y(centerY + dy + 1), color);
        }
    }

    private void drawBatteryGlyph(GuiGraphics graphics, int left, int top, int width, int height,
                                  float rawLevel, int color, int background) {
        drawRoundedRect(graphics, x(left), y(top), x(left + width), y(top + height), size(3), color);
        drawRoundedRect(graphics, x(left + 2), y(top + 2), x(left + width - 2), y(top + height - 2),
                size(1), background);
        float level = Math.max(0.0F, Math.min(1.0F, rawLevel));
        int innerWidth = Math.max(0, Math.round((width - 5) * level));
        int fillColor = level <= 0.2F ? 0xFFFF453A : color;
        if (innerWidth > 0) {
            drawRoundedRect(graphics, x(left + 2), y(top + 2),
                    x(Math.min(left + width - 2, left + 2 + innerWidth)), y(top + height - 2),
                    size(1), fillColor);
        }
        drawRoundedRect(graphics, x(left + width + 1), y(top + 3),
                x(left + width + 3), y(top + height - 3), size(1), color);
    }

    private void drawGlyphLine(GuiGraphics graphics, float fromX, float fromY,
                               float toX, float toY, float thickness, int color) {
        float deltaX = toX - fromX;
        float deltaY = toY - fromY;
        int steps = Math.max(1, Math.round(Math.max(Math.abs(deltaX), Math.abs(deltaY)) * 2.0F));
        int radius = Math.max(1, size(thickness) / 2);
        for (int i = 0; i <= steps; i++) {
            float progress = i / (float) steps;
            fillCircle(graphics, x(fromX + deltaX * progress), y(fromY + deltaY * progress), radius, color);
        }
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private enum ControlGlyph {
        AIRPLANE,
        WIFI,
        BLUETOOTH,
        SOUND,
        MOON,
        SUN
    }

    protected boolean handleDynamicIslandClick(double mouseX, double mouseY) {
        if (this.minecraft == null) return false;
        boolean hasCall = PhoneClientData.hasCall();
        int attentionCount = totalAttentionCount();
        boolean music = !hasCall && PhoneMusicPlayerState.isPlaying();
        boolean expanded = hasCall || music;
        int islandWidth = Math.max(78, Math.round(animatedIslandWidth));
        int islandLeft = 150 - islandWidth / 2;
        int islandRight = 150 + islandWidth / 2;
        int islandTop = expanded ? 11 : 14;
        int islandBottom = expanded ? 38 : 34;
        if (!frameInside(mouseX, mouseY, islandLeft, islandTop, islandRight, islandBottom)) return false;
        if ((this instanceof ZPhoneLockScreen || this instanceof com.mikasrevs.newphone.client.duo.DuoLockScreen || this instanceof AbstractZPhonePinScreen) && !hasCall) return true;

        ZPhoneTransitionState.beginAppOpen();
        if (hasCall) {
            PhoneNavigationState.open(this.minecraft, this, new ZPhoneCallScreen());
        } else if (music) {
            PhoneNavigationState.open(this.minecraft, this, new ZPhoneMusicScreen());
        } else if (attentionCount > 0) {
            PhoneNavigationState.open(this.minecraft, this, new ZPhoneNotificationCenterScreen());
        } else {
            PhoneNavigationState.open(this.minecraft, this, new ZPhoneNotificationCenterScreen());
        }
        return true;
    }

    protected final boolean isHomeIndicatorTap(double mouseX, double mouseY) {
        if(com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo())return com.mikasrevs.newphone.client.duo.DuoInterface.layout(width,height).inside(mouseX,mouseY,com.mikasrevs.newphone.client.duo.DuoLayout.HOME);
        return frameInside(mouseX, mouseY, 102, 594, 198, 610);
    }

    protected final boolean handleSharedHomeClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (this instanceof ZPhoneLockScreen || this instanceof com.mikasrevs.newphone.client.duo.DuoLockScreen || this instanceof AbstractZPhonePinScreen) return false;
        if (!isHomeIndicatorTap(mouseX, mouseY)) return false;
        ZPhoneClient.openHomeScreen();
        return true;
    }

    protected String callDuration() {
        long startedAt = PhoneClientData.callStartedAt();
        if (startedAt <= 0L) return "00:00";
        long seconds = Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    protected void drawBackButton(GuiGraphics graphics) {
        fillCircle(graphics, x(33), y(63), size(15), 0x5FFFFFFF);
        int stroke = Math.max(1, size(2));
        for (int step = 0; step <= 6; step++) {
            int upperX = x(36 - step);
            int upperY = y(57 + step);
            graphics.fill(upperX, upperY, upperX + stroke, upperY + stroke, 0xFFFFFFFF);
            int lowerX = x(30 + step);
            int lowerY = y(63 + step);
            graphics.fill(lowerX, lowerY, lowerX + stroke, lowerY + stroke, 0xFFFFFFFF);
        }
    }

    protected void drawBackButton(GuiGraphics graphics, String label) {
        drawBackButton(graphics);
        if (label != null && !label.isBlank()) {
            PhoneModernText.drawColored(graphics, label, x(53), y(58), size(7), 0xFFEAF0F7);
        }
    }

    protected boolean handleBackButton(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, 15, 45, 96, 83)) return false;
        PhoneNavigationState.backOrHome(minecraft);
        return true;
    }

    protected void drawHomeIndicator(GuiGraphics graphics) {
        if(com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo())return;
        if (controlSwipeTracking && controlSwipeProgress > 0.08F) {
            drawControlCenterPullPreview(graphics, controlSwipeProgress);
        }
        float brightness = Math.max(0.05F, Math.min(1.0F, PhoneSettingsStore.brightness()));
        int dimAlpha = Math.round((1.0F - brightness) * 205.0F);
        if (dimAlpha > 0) {
            graphics.fill(frameX(0), frameY(0), frameX(DESIGN_WIDTH), frameY(DESIGN_HEIGHT), dimAlpha << 24);
        }
        drawRoundedRect(graphics, frameX(113), frameY(594), frameX(187), frameY(598), frameSize(2), 0xE6FFFFFF);
    }

    private void drawControlCenterPullPreview(GuiGraphics graphics, float rawProgress) {
        float progress = Math.max(0.0F, Math.min(1.0F, rawProgress));
        float eased = progress * progress * (3.0F - 2.0F * progress);
        int bottom = frameY(Math.min(602, 8 + Math.round(594.0F * eased)));
        if (bottom <= frameY(8)) return;

        // Commit the source app exactly as it is, then layer Control Center over it.
        // Repainting the wallpaper here used to erase app cards/text while the user dragged down.
        beginOverlayLayer(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        drawControlCenterScrim(graphics, eased);
        beginOverlayLayer(graphics);
        graphics.enableScissor(frameX(8), frameY(8), frameX(292), bottom);
        int sheetOffsetY = -Math.round(frameSize(138) * (1.0F - eased));
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, sheetOffsetY, 0.0F);
        drawRoundedRect(graphics, frameX(8), frameY(8), frameX(292), frameY(602), frameSize(23), 0xE20A1018);
        finishOverlayBackground(graphics);
        drawControlCenterContents(graphics, -1, -1);
        graphics.flush();
        graphics.pose().popPose();
        graphics.disableScissor();
        graphics.pose().popPose();
    }

    protected boolean isCaretVisible() {
        return (System.currentTimeMillis() / 530L) % 2L == 0L;
    }

    protected void drawText(GuiGraphics graphics, String text, float designX, float designY,
                            float designHeight, int color, boolean centered) {
        String safe = text == null ? "" : text;
        // Keep translated labels inside the phone canvas. Languages such as German,
        // Russian and Polish can be substantially wider than English.
        float designMaxWidth = centered
                ? Math.max(12.0F, 2.0F * Math.min(designX - 12.0F, 288.0F - designX))
                : Math.max(12.0F, 288.0F - designX);
        if (centered) {
            PhoneModernText.drawCenteredColoredFitted(graphics, safe,
                    x(designX), y(designY), size(designHeight), size(designMaxWidth), color);
        } else {
            PhoneModernText.drawColoredFitted(graphics, safe,
                    x(designX), y(designY), size(designHeight), size(designMaxWidth), color);
        }
    }

    protected void drawRoundedRect(GuiGraphics graphics, int left, int top, int right, int bottom,
                                   int radius, int color) {
        if (right <= left || bottom <= top) return;
        int r = Math.max(1, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
        if (r <= 1) {
            graphics.fill(left, top, right, bottom, color);
            return;
        }
        graphics.fill(left + r, top, right - r, bottom, color);
        if (bottom - top > r * 2) graphics.fill(left, top + r, right, bottom - r, color);
        for (int offset = 0; offset < r; offset++) {
            int dy = r - offset;
            int inset = r - (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            graphics.fill(left + inset, top + offset, right - inset, top + offset + 1, color);
            int bottomY = bottom - offset - 1;
            graphics.fill(left + inset, bottomY, right - inset, bottomY + 1, color);
        }
    }

    protected void fillCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int r = Math.max(1, radius);
        int band = r >= 40 ? 3 : 1;
        for (int dy = -r; dy <= r; dy += band) {
            int bandEnd = Math.min(r + 1, dy + band);
            int sampleDy = Math.min(r, dy + (bandEnd - dy) / 2);
            int halfWidth = (int) Math.sqrt(Math.max(0, r * r - sampleDy * sampleDy));
            graphics.fill(centerX - halfWidth, centerY + dy,
                    centerX + halfWidth + 1, centerY + bandEnd, color);
        }
    }

    private int roundedInset(int py, int top, int bottom, int radius) {
        int r = Math.max(1, radius);
        if (py < top + r) {
            int dy = top + r - py;
            return r - (int) Math.sqrt(Math.max(0, r * r - dy * dy));
        }
        if (py >= bottom - r) {
            int dy = py - (bottom - r - 1);
            return r - (int) Math.sqrt(Math.max(0, r * r - dy * dy));
        }
        return 0;
    }

    private void fillCircleClippedToDisplay(GuiGraphics graphics, int centerX, int centerY, int circleRadius,
                                            int color, int left, int top, int right, int bottom, int cornerRadius) {
        int r = Math.max(1, circleRadius);
        int band = r >= 40 ? 3 : 1;
        for (int dy = -r; dy <= r; dy += band) {
            int bandEnd = Math.min(r + 1, dy + band);
            int sampleDy = Math.min(r, dy + (bandEnd - dy) / 2);
            int py = centerY + dy;
            int pyEnd = centerY + bandEnd;
            if (pyEnd <= top || py >= bottom) continue;
            int circleHalf = (int) Math.sqrt(Math.max(0, r * r - sampleDy * sampleDy));
            int clippedTop = Math.max(top, py);
            int clippedBottom = Math.min(bottom, pyEnd);
            int rounded = roundedInset((clippedTop + clippedBottom) / 2, top, bottom, cornerRadius);
            int from = Math.max(centerX - circleHalf, left + rounded);
            int to = Math.min(centerX + circleHalf + 1, right - rounded);
            if (from < to && clippedTop < clippedBottom) graphics.fill(from, clippedTop, to, clippedBottom, color);
        }
    }

    private int blend(int from, int to, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo()){
            var surface=com.mikasrevs.newphone.client.duo.DuoInterface.layout(width,height);
            com.mikasrevs.newphone.client.duo.DuoAnimation.tap(Math.round((float)(mouseX-surface.x())/surface.scale()),Math.round((float)(mouseY-surface.y())/surface.scale()));
            if(com.mikasrevs.newphone.client.duo.DuoInterface.click(this,width,height,mouseX,mouseY,button))return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && handleDynamicIslandClick(mouseX, mouseY)) return true;
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.minecraft != null
                && !(this instanceof ZPhoneControlCenterScreen)
                && !(this instanceof ZPhoneLockScreen)
                && !(this instanceof com.mikasrevs.newphone.client.duo.DuoLockScreen)
                && !(this instanceof AbstractZPhonePinScreen)
                && !(this instanceof ZPhoneSetupScreen)) {
            double previousY = mouseY - dragY;
            if (!controlSwipeTracking && previousY <= frameY(36)
                    && mouseX >= frameX(205) && mouseX <= frameX(288)) {
                controlSwipeTracking = true;
                controlSwipeStartY = previousY;
                controlSwipeProgress = 0.0F;
            }
            if (controlSwipeTracking) {
                double distance = Math.max(0.0D, mouseY - controlSwipeStartY);
                double adjusted = Math.max(0.0D, distance - frameSize(18));
                controlSwipeProgress = Math.max(0.0F, Math.min(1.0F,
                        (float) (adjusted / Math.max(1.0D, frameSize(560)))));
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (controlSwipeTracking) {
            float releasedProgress = controlSwipeProgress;
            boolean open = releasedProgress >= 0.22F;
            controlSwipeTracking = false;
            controlSwipeProgress = 0.0F;
            if (open && this.minecraft != null
                    && !(this instanceof ZPhoneControlCenterScreen)
                    && !(this instanceof ZPhoneLockScreen)
                    && !(this instanceof com.mikasrevs.newphone.client.duo.DuoLockScreen)
                    && !(this instanceof AbstractZPhonePinScreen)
                    && !(this instanceof ZPhoneSetupScreen)) {
                float visibleProgress = releasedProgress * releasedProgress * (3.0F - 2.0F * releasedProgress);
                PhoneNavigationState.openControlCenter(this.minecraft, this, visibleProgress);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (minecraft == null) return;
        if(com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo()&&(this instanceof com.mikasrevs.newphone.client.duo.DuoHomeScreen||this instanceof com.mikasrevs.newphone.client.duo.DuoLockScreen)){
            com.mikasrevs.newphone.client.duo.DuoTransitionScreen.close(minecraft);return;
        }
        if (this instanceof ZPhoneHomeScreen || this instanceof ZPhoneLockScreen) {
            ZPhoneClient.markPhoneClosed();
            PhoneNavigationState.clear();
            ZPhoneHeldModelState.turnOffHeldPhone();
            super.onClose();
        } else {
            PhoneNavigationState.backOrHome(minecraft);
        }
    }

    private record ClassicCaseData(int x, int y, int width, int height, float scale) { }

    protected record DrawData(int x, int y, int width, int height, float scale) { }
}

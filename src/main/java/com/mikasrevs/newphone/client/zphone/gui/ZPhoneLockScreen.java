package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhoneMusicPlayerState;
import com.mikasrevs.newphone.client.security.PhoneSecurityStore;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.settings.PhonePrivacyStore;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.zphone.ZPhoneTransitionState;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import com.mikasrevs.newphone.network.PhoneStateData;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Native animated lock screen for Z-Phone. */
public final class ZPhoneLockScreen extends AbstractZPhoneScreen {
    private static final ResourceLocation WEATHER_ICONS = new ResourceLocation(
            NewPhone.MOD_ID, "textures/gui/phone/weather_icons.png"
    );
    private static final int WEATHER_CELL = 64;
    private static final long UNLOCK_DURATION_MS = 280L;

    private boolean swipeTracking;
    private double swipeStartY;
    private float swipeProgress;
    private boolean unlocking;
    private long unlockStartedAt;

    public ZPhoneLockScreen() {
        super(Component.translatable("screen.newphone.z_phone.lock.title"));
        PhoneClientData.requestSync();
        PhoneSocialClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawFrame(g);
        drawWallpaper(g);
        drawStatusBar(g);
        drawLockContent(g);
        drawUnlockTransition(g);
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void drawLockContent(GuiGraphics g) {
        LocalDate date = LocalDate.now();
        Locale locale = PhoneSettingsStore.locale();
        String time = com.mikasrevs.newphone.client.gui.PhoneTimeDisplay.time(false);
        String dateText = PhoneSettingsStore.useMinecraftTime()
                ? com.mikasrevs.newphone.client.gui.PhoneTimeDisplay.dayLabel()
                : date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale));
        int shift = lockShift();

        // Small native lock mark.
        drawRoundedRect(g, x(140), y(50 - shift), x(160), y(69 - shift), size(9), 0x3A000000);
        drawRoundedRect(g, x(145), y(57 - shift), x(155), y(66 - shift), size(4), 0xE8FFFFFF);
        g.fill(x(148), y(53 - shift), x(153), y(60 - shift), 0xE8FFFFFF);
        g.fill(x(150), y(55 - shift), x(155), y(60 - shift), 0xFF17334F);

        PhoneModernText.drawCenteredLight(g, time, x(150), y(76 - shift), size(48));
        PhoneModernText.drawCenteredColored(g, dateText, x(150), y(137 - shift), size(9), 0xFFF2F5F9);

        drawWeatherPill(g, 153 - shift);

        boolean music = PhoneMusicPlayerState.isPlaying();
        if (music) drawMusicActivity(g, 211 - shift);
        int noticeStart = music ? 278 : 214;
        List<Notice> notices = notices();
        if (notices.isEmpty()) {
            int top = noticeStart + 8;
            drawRoundedRect(g, x(25), y(top - shift), x(275), y(top + 56 - shift), size(17), 0x7A16202B);
            fillCircle(g, x(51), y(top + 28 - shift), size(13), 0x7731D158);
            PhoneModernText.drawCenteredColored(g, "✓", x(51), y(top + 20 - shift), size(12), 0xFFFFFFFF);
            PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Yeni bildirim yok", "No new notifications"), x(73), y(top + 14 - shift), size(9), size(188), 0xFFFFFFFF);
            PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Tüm bildirimleri gördün", "You're all caught up"), x(73), y(top + 33 - shift), size(7), size(188), 0xFFAAB5C2);
        } else {
            int shown = Math.min(music ? 3 : 3, notices.size());
            for (int i = 0; i < shown; i++) drawNotice(g, notices.get(i), noticeStart + i * 66 - shift);
            if (notices.size() > shown) {
                PhoneModernText.drawCenteredColored(g, "+" + (notices.size() - shown) + PhoneSettingsStore.text(" tane daha", " more"), x(150), y(noticeStart + shown * 66 + 2 - shift), size(7), 0xFFD3DBE6);
            }
        }

        // Bottom actions: subtle circular buttons with proper glyphs.
        fillCircle(g, x(51), y(514 - shift), size(20), 0x70070B10);
        drawFlashlightGlyph(g, 51, 514 - shift, 0xFFFFFFFF);
        fillCircle(g, x(249), y(514 - shift), size(20), 0x70070B10);
        drawCameraGlyph(g, 249, 514 - shift, 0xFFFFFFFF);
        if (PhoneSecurityStore.hasPin()) drawChangePinButton(g, 514 - shift);

        int bob = unlocking || swipeTracking ? 0 : Math.round((float) Math.sin(System.currentTimeMillis() / 230.0D) * 2.0F);
        PhoneModernText.drawCenteredColored(g, "⌃", x(150), y(535 + bob - shift), size(20), 0xEFFFFFFF);
        PhoneModernText.drawCenteredColoredFitted(
                g,
                PhoneSettingsStore.text("Kilidi açmak için yukarı kaydır veya BOŞLUK'a bas",
                        "Swipe up or press SPACE to unlock"),
                x(150),
                y(559 + bob - shift),
                size(8),
                size(260),
                0xDFFFFFFF
        );
    }

    private void drawMusicActivity(GuiGraphics g, int top) {
        PhoneMusicPlayerState.Track track = PhoneMusicPlayerState.currentTrack();
        drawRoundedRect(g, x(25), y(top), x(275), y(top + 58), size(17), 0xA816202B);
        drawRoundedRect(g, x(36), y(top + 8), x(78), y(top + 50), size(10), 0xCC293747);
        PhoneModernText.drawCenteredColored(g, "♫", x(57), y(top + 18), size(16), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, track.title(), x(91), y(top + 9), size(9), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, track.artist(), x(91), y(top + 25), size(6), 0xFFB6C1CE);
        int barLeft = x(91), barRight = x(258), barTop = y(top + 43);
        g.fill(barLeft, barTop, barRight, barTop + size(3), 0x556D7885);
        int progressRight = barLeft + Math.round((barRight - barLeft) * PhoneMusicPlayerState.progress());
        g.fill(barLeft, barTop, progressRight, barTop + size(3), 0xFFFFFFFF);
    }

    private void drawWeatherPill(GuiGraphics g, int top) {
        WeatherVisual weather = resolveWeather();
        drawRoundedRect(g, x(79), y(top), x(221), y(top + 50), size(16), 0x72152230);
        g.blit(
                WEATHER_ICONS,
                x(91), y(top + 7),
                size(36), size(36),
                (float) (weather.iconIndex * WEATHER_CELL), 0.0F,
                WEATHER_CELL, WEATHER_CELL,
                256, 64
        );
        PhoneModernText.drawColored(g, weather.temperature, x(135), y(top + 8), size(15), 0xFFFFFFFF);
        PhoneModernText.drawColoredFitted(g, weather.localizedLabel(), x(136), y(top + 29),
                size(7), size(72), 0xFFB9C5D2);
    }

    private WeatherVisual resolveWeather() {
        if (minecraft == null || minecraft.level == null) return WeatherVisual.CLEAR;
        if (minecraft.level.isThundering()) return WeatherVisual.THUNDER;
        if (minecraft.level.isRaining()) return WeatherVisual.RAIN;
        return WeatherVisual.CLEAR;
    }

    private void drawNotice(GuiGraphics g, Notice notice, int top) {
        int accent = switch (notice.kind) {
            case "MESSAGES" -> 0xFF31D158;
            case "BLOCKGRAM" -> 0xFFFF4C83;
            case "BLOCKPOST" -> 0xFF59A9FF;
            default -> PhoneSettingsStore.accentColor();
        };
        drawRoundedRect(g, x(22), y(top), x(278), y(top + 58), size(16), 0xA817212D);
        fillCircle(g, x(48), y(top + 29), size(14), 0xCC263242);
        fillCircle(g, x(48), y(top + 29), size(5), accent);
        PhoneModernText.drawColoredFitted(g, notice.title, x(70), y(top + 9), size(8), size(158), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, PhonePrivacyStore.previews() ? trim(notice.body, 32) : PhoneSettingsStore.text("Yeni bildirim", "New notification"), x(70), y(top + 27), size(7), 0xFFD1D8E1);
        PhoneModernText.drawColored(g, relativeTime(notice.when), x(239), y(top + 9), size(6), 0xFF9AA5B3);
    }

    private void drawCameraGlyph(GuiGraphics g, int cx, int cy, int color) {
        int l = cx - 9, t = cy - 6, r = cx + 9, b = cy + 7;
        drawRoundedRect(g, x(l), y(t), x(r), y(b), size(3), color);
        drawRoundedRect(g, x(l + 2), y(t + 2), x(r - 2), y(b - 2), size(2), 0xFF111820);
        g.fill(x(cx - 4), y(cy - 9), x(cx + 3), y(cy - 6), color);
        fillCircle(g, x(cx), y(cy), size(4), color);
        fillCircle(g, x(cx), y(cy), size(2), 0xFF111820);
    }

    private void drawFlashlightGlyph(GuiGraphics g, int cx, int cy, int color) {
        g.fill(x(cx - 5), y(cy - 9), x(cx + 5), y(cy - 5), color);
        g.fill(x(cx - 3), y(cy - 5), x(cx + 3), y(cy + 7), color);
        g.fill(x(cx - 5), y(cy + 7), x(cx + 5), y(cy + 10), color);
    }

    private void drawChangePinButton(GuiGraphics g, int centerY) {
        drawRoundedRect(g, x(86), y(centerY - 19), x(214), y(centerY + 19), size(18), 0x7A101722);
        drawRoundedRect(g, x(87), y(centerY - 18), x(213), y(centerY + 18), size(17), 0x2FFFFFFF);
        drawKeyGlyph(g, 106, centerY, 0xFFF4F7FB);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("PIN'i Değiştir", "Change PIN"),
                x(122), y(centerY - 5), size(7), size(80), 0xFFF4F7FB);
    }

    private void drawKeyGlyph(GuiGraphics g, int cx, int cy, int color) {
        fillCircle(g, x(cx - 3), y(cy), size(5), color);
        fillCircle(g, x(cx - 3), y(cy), size(2), 0xFF1A2330);
        g.fill(x(cx + 1), y(cy - 1), x(cx + 10), y(cy + 2), color);
        g.fill(x(cx + 6), y(cy + 1), x(cx + 8), y(cy + 5), color);
        g.fill(x(cx + 9), y(cy + 1), x(cx + 11), y(cy + 4), color);
    }

    private void drawUnlockTransition(GuiGraphics g) {
        float p = unlockProgress();
        if (p <= 0.0F) return;
        // A soft rising sheet makes both drag-to-unlock and Space unlock feel continuous.
        float eased = easeOutCubic(p);
        int top = Math.round(y(602) - (y(602) - y(40)) * eased);
        drawRoundedRect(g, x(8), top, x(292), y(602), size(23), 0xC20A1018);
    }

    private int lockShift() {
        return Math.round(118.0F * easeOutCubic(unlockProgress()));
    }

    private float unlockProgress() {
        if (unlocking) {
            float raw = (System.currentTimeMillis() - unlockStartedAt) / (float) UNLOCK_DURATION_MS;
            return Math.max(0F, Math.min(1F, raw));
        }
        return Math.max(0F, Math.min(0.78F, swipeProgress));
    }

    private static float easeOutCubic(float value) {
        float inv = 1.0F - Math.max(0F, Math.min(1F, value));
        return 1.0F - inv * inv * inv;
    }

    private List<Notice> notices() {
        return com.mikasrevs.newphone.client.gui.PhoneNotificationFeed.entries(false).stream()
                .map(n -> new Notice(n.kind(), n.title(), n.body(), n.when())).toList();
    }

    private String trim(String value, int max) {
        String safe = value == null ? "" : value.replace('\n', ' ').trim();
        return safe.length() <= max ? safe : safe.substring(0, Math.max(1, max - 1)) + "…";
    }

    private String relativeTime(long createdAt) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - createdAt) / 1000L);
        if (seconds < 60L) return PhoneSettingsStore.text("şimdi", "now");
        long minutes = seconds / 60L;
        if (minutes < 60L) return minutes + PhoneSettingsStore.text(" dk", " min");
        long hours = minutes / 60L;
        if (hours < 24L) return hours + PhoneSettingsStore.text(" sa", " hr");
        return (hours / 24L) + PhoneSettingsStore.text(" g", " d");
    }

    private void beginUnlock() {
        if (unlocking) return;
        unlocking = true;
        unlockStartedAt = System.currentTimeMillis();
        swipeTracking = false;
        swipeProgress = 1.0F;
    }

    private void finishUnlock() {
        if (PhoneSecurityStore.hasPin()) ZPhoneClient.openPinEntryScreen();
        else ZPhoneClient.unlockToHome();
    }

    @Override
    public void tick() {
        super.tick();
        if (unlocking && System.currentTimeMillis() - unlockStartedAt >= UNLOCK_DURATION_MS) {
            unlocking = false;
            finishUnlock();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || unlocking) return false;
        if (handleDynamicIslandClick(mouseX, mouseY)) return true;
        List<Notice> currentNotices = notices();
        int noticeStart = PhoneMusicPlayerState.isPlaying() ? 278 : 214;
        int shift = lockShift();
        int shown = Math.min(3, currentNotices.size());
        for (int i = 0; i < shown; i++) {
            int top = noticeStart + i * 66 - shift;
            if (inside(mouseX, mouseY, 22, top, 278, top + 58) && minecraft != null) {
                if (PhoneSecurityStore.hasPin()) {
                    beginUnlock();
                    return true;
                }
                ZPhoneTransitionState.beginAppOpen();
                minecraft.setScreen(new ZPhoneNotificationCenterScreen());
                return true;
            }
        }
        if (PhoneSecurityStore.hasPin() && inside(mouseX, mouseY, 86, 495 - shift, 214, 533 - shift)) {
            ZPhoneClient.openPinRecovery(this);
            return true;
        }
        if (inside(mouseX, mouseY, 226, 488, 274, 542)) {
            if (PhoneSecurityStore.hasPin()) {
                beginUnlock();
                return true;
            }
            ZPhoneClient.openSharedApp(PhoneApp.CAMERA);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !unlocking) {
            double previousY = mouseY - dragY;
            if (!swipeTracking && previousY >= y(430) && mouseX >= x(15) && mouseX <= x(285)) {
                swipeTracking = true;
                swipeStartY = previousY;
            }
            if (swipeTracking) {
                double distance = Math.max(0.0D, swipeStartY - mouseY);
                swipeProgress = Math.max(0F, Math.min(1F, (float) (distance / Math.max(1.0D, size(125)))));
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (swipeTracking) {
            boolean unlock = swipeProgress >= 0.34F;
            swipeTracking = false;
            if (unlock) beginUnlock();
            else swipeProgress = 0.0F;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            beginUnlock();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private enum WeatherVisual {
        CLEAR(0, "18°", "Clear"),
        RAIN(2, "14°", "Rain"),
        THUNDER(3, "12°", "Storm");

        private final int iconIndex;
        private final String temperature;
        private final String label;

        WeatherVisual(int iconIndex, String temperature, String label) {
            this.iconIndex = iconIndex;
            this.temperature = temperature;
            this.label = label;
        }

        private String localizedLabel() {
            return switch (this) {
                case CLEAR -> PhoneSettingsStore.text("Açık", "Clear");
                case RAIN -> PhoneSettingsStore.text("Yağmurlu", "Rain");
                case THUNDER -> PhoneSettingsStore.text("Fırtınalı", "Storm");
            };
        }
    }

    private record Notice(String kind, String title, String body, long when) { }
}

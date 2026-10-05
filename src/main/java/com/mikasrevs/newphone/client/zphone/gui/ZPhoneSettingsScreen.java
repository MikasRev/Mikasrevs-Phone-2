package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.security.PhoneSecurityStore;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.settings.PhonePrivacyStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Light, grouped MPhone settings screen inspired by a native mobile settings app. */
public final class ZPhoneSettingsScreen extends AbstractZPhoneScreen {
    private static final ResourceLocation STEVE_HEAD = new ResourceLocation(
            NewPhone.MOD_ID, "textures/gui/phone/steve_head.png"
    );

    private static final int PAGE_BG = 0xFF08090C;
    private static final int CARD_BG = 0xFF24252B;
    private static final int SEARCH_BG = 0xFF1C1D22;
    private static final int PRIMARY_TEXT = 0xFFF7F7FA;
    private static final int SECONDARY_TEXT = 0xFFB4B5BD;
    private static final int TERTIARY_TEXT = 0xFF85868F;
    private static final int DIVIDER = 0xFF3B3C43;
    private static final int CONTROL_KNOB = 0xFFFFFFFF;

    private static final List<Row> ROWS = List.of(
            new Row(Icon.DEVICE, "Phone Model", "Telefon modeli", "Choose your phone design", "Telefon tasarımını seç", Section.APPEARANCE, Kind.MODEL),
            new Row(Icon.SUN, "Brightness", "Parlaklık", "Visual brightness", "Ekran parlaklığı", Section.APPEARANCE, Kind.BRIGHTNESS),
            new Row(Icon.SWATCH, "Accent color", "Vurgu rengi", "Change highlight color", "Vurgu rengini değiştir", Section.APPEARANCE, Kind.ACCENT),
            new Row(Icon.PHOTO, "Wallpaper", "Duvar kağıdı", "Change background", "Arka planı değiştir", Section.APPEARANCE, Kind.WALLPAPER),
            new Row(Icon.GRID, "Home layout", "Ana ekran görünümü", "Classic or all apps", "Klasik veya tüm uygulamalar", Section.APPEARANCE, Kind.HOME_LAYOUT),
            new Row(Icon.DEVICE, "Phone size", "Telefon boyutu", "Scale the device", "Telefonu ölçeklendir", Section.APPEARANCE, Kind.SIZE),
            new Row(Icon.MOTION, "App animations", "Uygulama animasyonları", "Motion and transitions", "Geçiş ve hareketler", Section.APPEARANCE, Kind.ANIMATIONS),

            new Row(Icon.SPEAKER, "Volume", "Ses", "Alert volume", "Uyarı ses seviyesi", Section.SOUND, Kind.VOLUME),
            new Row(Icon.VIBRATE, "Vibration", "Titreşim", "Vibration feedback", "Titreşim geri bildirimi", Section.SOUND, Kind.VIBRATION),
            new Row(Icon.CALL, "Incoming call animation", "Arama animasyonu", "Fullscreen incoming calls", "Tam ekran gelen aramalar", Section.SOUND, Kind.CALL_ANIMATION),
            new Row(Icon.BELL, "Notifications", "Bildirimler", "Messages and alerts", "Mesaj ve uyarılar", Section.SOUND, Kind.NOTIFICATIONS),
            new Row(Icon.BELL, "Do not disturb", "Rahatsız etme", "Silence alerts and calls", "Bildirim ve aramaları sessize al", Section.SOUND, Kind.DND),
            new Row(Icon.CLOCK, "Time source", "Saat kaynağı", "Real time or Minecraft day/time", "Gerçek saat veya Minecraft günü/saati", Section.SOUND, Kind.TIME_SOURCE),
            new Row(Icon.CLOCK, "24-hour time", "24 saat biçimi", "Use 24h clock", "24 saat kullan", Section.SOUND, Kind.TIME_FORMAT),

            new Row(Icon.LOCK, "Lock Screen", "Kilit ekranı", "Turn lock screen on or off", "Kilit ekranını aç veya kapat", Section.PRIVACY, Kind.LOCK),
            new Row(Icon.EYE, "Lock screen previews", "Kilit ekranı önizlemesi", "Show notification content", "Bildirim içeriğini göster", Section.PRIVACY, Kind.LOCK_PREVIEWS),
            new Row(Icon.PHOTO, "My posts", "Gönderilerim", "Archive, restore and delete", "Arşivle, geri yükle ve sil", Section.PRIVACY, Kind.MY_POSTS),
            new Row(Icon.TIMER, "Auto Lock", "Otomatik kilit", "Time before locking", "Kilitleme öncesi süre", Section.PRIVACY, Kind.AUTO_LOCK),
            new Row(Icon.EYE, "Show phone number", "Telefon numarasını göster", "Show your number on profile", "Numaranı profilde göster", Section.PRIVACY, Kind.SHOW_NUMBER),
            new Row(Icon.KEY, "PIN & Security", "PIN ve Güvenlik", "Protect with a PIN", "PIN ile koru", Section.PRIVACY, Kind.PIN),

            new Row(Icon.GLOBE, "Language", "Dil", "Phone language", "Telefon dili", Section.SYSTEM, Kind.LANGUAGE),
            new Row(Icon.SETUP, "Set up MPhone again", "MPhone'u yeniden ayarla", "Run first-time setup", "İlk kurulum sihirbazını başlat", Section.SYSTEM, Kind.SETUP),
            new Row(Icon.RESET, "Reset settings", "Ayarları sıfırla", "Restore defaults", "Varsayılanları geri yükle", Section.SYSTEM, Kind.RESET)
    );

    private static final int SEARCH_TOP = 91;
    private static final int SEARCH_BOTTOM = 128;
    private static final int SCROLL_TOP = 220;
    private static final int SCROLL_BOTTOM = 582;
    private static final int SECTION_HEADER_HEIGHT = 17;
    private static final int ROW_HEIGHT = 46;
    private static final int GROUP_GAP = 13;
    private static final int EMPTY_CONTENT_HEIGHT = 96;
    private static final float SCROLL_SMOOTHING = 0.22F;
    private static final float WHEEL_SCROLL_STEP = 52.0F;

    private String searchQuery = "";
    private boolean searchFocused;
    private float currentScroll;
    private float targetScroll;
    private boolean scrollBarDragging;
    private double scrollBarGrabOffset;
    private int scrollThumbTop = SCROLL_TOP;
    private int scrollThumbBottom = SCROLL_BOTTOM;
    private Kind sliderDragging;
    private long setupConfirmUntil;

    public ZPhoneSettingsScreen() {
        super(Component.translatable("screen.newphone.app.settings"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateSmoothScroll();

        renderBackground(graphics);
        graphics.fill(x(0), y(0), x(DESIGN_WIDTH), y(DESIGN_HEIGHT), PAGE_BG);
        drawStatusBar(graphics, PRIMARY_TEXT, PAGE_BG);
        drawHeader(graphics);
        drawSettingsContent(graphics, mouseX, mouseY);
        drawScrollBar(graphics);
        drawHomeIndicator(graphics);
        if(!com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo())drawRoundedRect(graphics, x(113), y(594), x(187), y(598), size(2), 0xE6FFFFFF);
        drawFrame(graphics);
    }

    private void updateSmoothScroll() {
        float max = maxScrollPixels();
        targetScroll = clamp(targetScroll, 0.0F, max);
        currentScroll += (targetScroll - currentScroll) * SCROLL_SMOOTHING;
        if (Math.abs(targetScroll - currentScroll) < 0.45F) currentScroll = targetScroll;
    }

    private void drawHeader(GuiGraphics g) {
        drawLightBackButton(g);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Ayarlar", "Settings"),
                x(49), y(52), size(23), size(215), PRIMARY_TEXT);
        drawSearchField(g);
        drawProfileCard(g);
    }

    private void drawLightBackButton(GuiGraphics g) {
        fillCircle(g, x(27), y(64), size(13), 0xFF292A30);
        int accent = PhoneSettingsStore.accentColor();
        drawDesignLine(g, 30, 57, 23, 64, accent);
        drawDesignLine(g, 23, 64, 30, 71, accent);
    }

    private void drawSearchField(GuiGraphics g) {
        int accent = PhoneSettingsStore.accentColor();
        if (searchFocused) {
            drawRoundedRect(g, x(20), y(SEARCH_TOP), x(280), y(SEARCH_BOTTOM), size(18), accent);
            drawRoundedRect(g, x(22), y(SEARCH_TOP + 2), x(278), y(SEARCH_BOTTOM - 2), size(16), SEARCH_BG);
        } else {
            drawRoundedRect(g, x(21), y(SEARCH_TOP), x(279), y(SEARCH_BOTTOM), size(18), SEARCH_BG);
        }

        strokeCircle(g, 39, 108, 6, 2, 0xFF9A9BA4, SEARCH_BG);
        drawDesignLine(g, 43, 113, 48, 118, 0xFF9A9BA4);

        String text = searchQuery.isEmpty() ? PhoneSettingsStore.text("Ara", "Search") : searchQuery;
        int color = searchQuery.isEmpty() ? 0xFF9A9BA4 : PRIMARY_TEXT;
        int preferredHeight = size(9);
        int maxWidth = size(searchQuery.isEmpty() ? 188 : 176);
        PhoneModernText.drawColoredFitted(g, text, x(55), y(101), preferredHeight, maxWidth, color);

        if (!searchQuery.isEmpty()) {
            fillCircle(g, x(263), y(109), size(9), 0xFF5C5D65);
            drawDesignLine(g, 260, 106, 266, 112, 0xFFFFFFFF);
            drawDesignLine(g, 266, 106, 260, 112, 0xFFFFFFFF);
        } else if (searchFocused && isCaretVisible()) {
            g.fill(x(55), y(101), x(56), y(116), accent);
        }

        if (searchFocused && !searchQuery.isEmpty() && isCaretVisible()) {
            int fittedHeight = PhoneModernText.fittedHeight(searchQuery, preferredHeight, maxWidth);
            int textWidth = PhoneModernText.width(searchQuery, fittedHeight);
            int caretX = Math.min(x(242), x(55) + textWidth + size(1));
            g.fill(caretX, y(101), caretX + Math.max(1, size(1)), y(116), accent);
        }
    }

    private void drawProfileCard(GuiGraphics g) {
        drawRoundedRect(g, x(21), y(141), x(279), y(207), size(11), 0x52000000);
        drawRoundedRect(g, x(21), y(139), x(279), y(205), size(11), CARD_BG);

        int accent = PhoneSettingsStore.accentColor();
        drawRoundedRect(g, x(31), y(149), x(79), y(197), size(12), withAlpha(accent, 38));
        g.blit(STEVE_HEAD, x(39), y(157), size(32), size(32),
                0.0F, 0.0F, 32, 32, 32, 32);
        PhonePlayerHeadRenderer.draw(g, PhoneClientData.selfUuid(), x(39), y(157), size(32));

        PhoneModernText.drawColoredFitted(g, profileName(), x(91), y(150), size(12), size(163), PRIMARY_TEXT);
        PhoneModernText.drawColoredFitted(g, profilePrimaryDetail(), x(91), y(171), size(7), size(166), SECONDARY_TEXT);
        PhoneModernText.drawColoredFitted(g,
                PhoneSettingsStore.text("Minecraft hesabı ve cihaz tercihleri", "Minecraft account and device preferences"),
                x(91), y(184), size(6), size(168), TERTIARY_TEXT);
    }

    private String profileName() {
        String name = PhoneClientData.selfName();
        if ((name == null || name.isBlank()) && minecraft != null && minecraft.player != null) {
            name = minecraft.player.getName().getString();
        }
        return name == null || name.isBlank() ? "Steve" : name;
    }

    private String profilePrimaryDetail() {
        String number = PhoneClientData.formattedSelfPhoneNumber();
        if (PhoneSettingsStore.showPhoneNumber() && number != null && !number.isBlank()) return number;
        return PhoneSettingsStore.text("MPhone hesabı", "MPhone account");
    }

    private void drawSettingsContent(GuiGraphics g, int mouseX, int mouseY) {
        g.enableScissor(x(13), y(SCROLL_TOP), x(287), y(SCROLL_BOTTOM));
        int cursor = SCROLL_TOP - Math.round(currentScroll);
        boolean drewGroup = false;

        for (Section candidate : Section.values()) {
            List<Row> rows = filteredRows(candidate);
            if (rows.isEmpty()) continue;
            if (drewGroup) cursor += GROUP_GAP;

            PhoneModernText.drawColoredFitted(g, candidate.label().toUpperCase(PhoneSettingsStore.locale()),
                    x(27), y(cursor + 2), size(6), size(240), TERTIARY_TEXT);
            cursor += SECTION_HEADER_HEIGHT;

            int cardTop = cursor;
            int cardBottom = cardTop + rows.size() * ROW_HEIGHT;
            drawRoundedRect(g, x(21), y(cardTop + 2), x(279), y(cardBottom + 3), size(10), 0x66000000);
            drawRoundedRect(g, x(21), y(cardTop), x(279), y(cardBottom), size(10), CARD_BG);

            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                int rowTop = cardTop + index * ROW_HEIGHT;
                boolean hover = rowTop < SCROLL_BOTTOM && rowTop + ROW_HEIGHT > SCROLL_TOP
                        && inside(mouseX, mouseY, 21, Math.max(rowTop, SCROLL_TOP), 279,
                        Math.min(rowTop + ROW_HEIGHT, SCROLL_BOTTOM));
                if (hover) {
                    drawRoundedRect(g, x(24), y(rowTop + 2), x(276), y(rowTop + ROW_HEIGHT - 2),
                    size(6), 0x242F8FFF);
                }
                drawRow(g, row, rowTop);
                if (index + 1 < rows.size()) {
                    g.fill(x(68), y(rowTop + ROW_HEIGHT - 1), x(279), y(rowTop + ROW_HEIGHT), DIVIDER);
                }
            }

            cursor = cardBottom;
            drewGroup = true;
        }

        if (!drewGroup) drawEmptySearchState(g, cursor);
        g.disableScissor();
    }

    private void drawEmptySearchState(GuiGraphics g, int top) {
        drawRoundedRect(g, x(21), y(top + 17), x(279), y(top + 83), size(10), CARD_BG);
        strokeCircle(g, 150, top + 42, 8, 2, TERTIARY_TEXT, CARD_BG);
        drawDesignLine(g, 155, top + 48, 161, top + 54, TERTIARY_TEXT);
        PhoneModernText.drawCenteredColoredFitted(g,
                PhoneSettingsStore.text("Eşleşen ayar bulunamadı", "No matching settings"),
                x(150), y(top + 63), size(7), size(220), SECONDARY_TEXT);
    }

    private void drawRow(GuiGraphics g, Row row, int top) {
        drawSettingIcon(g, row.icon, 43, top + ROW_HEIGHT / 2);

        int titleColor = row.kind == Kind.RESET ? 0xFFFF3B4F : PRIMARY_TEXT;
        boolean slider = row.kind == Kind.BRIGHTNESS || row.kind == Kind.VOLUME;
        boolean hasValue = !slider && !isToggle(row.kind);
        int textMaxWidth = slider ? 112 : hasValue ? 114 : 151;
        PhoneModernText.drawColoredFitted(g, row.title(), x(68), y(top + 8), size(8), size(hasValue ? 190 : textMaxWidth), titleColor);
        int secondary = PhoneSettingsStore.isChinese() ? 0xFFD6D8E0 : SECONDARY_TEXT;
        PhoneModernText.drawColoredFitted(g, row.subtitle(), x(68), y(top + 25), size(5), size(textMaxWidth), secondary);

        int accent = PhoneSettingsStore.accentColor();
        if (row.kind == Kind.BRIGHTNESS || row.kind == Kind.VOLUME) {
            float metric = row.kind == Kind.BRIGHTNESS ? PhoneSettingsStore.brightness() : PhoneSettingsStore.phoneVolume();
            drawSlider(g, top, metric, accent);
        } else if (isToggle(row.kind)) {
            drawToggle(g, top, toggleValue(row.kind), accent);
        } else {
            int valueColor = row.kind == Kind.RESET ? 0xFFFF3B4F : secondary;
            PhoneModernText.drawCenteredColoredFitted(g, rowValue(row.kind), x(224), y(top + 25),
                    size(7), size(65), valueColor);
            if (row.kind != Kind.RESET) drawChevron(g, 269, top + 23, TERTIARY_TEXT);
        }
    }

    private void drawSlider(GuiGraphics g, int top, float value, int accent) {
        float safeValue = clamp(value, 0.0F, 1.0F);
        PhoneModernText.drawCenteredColoredFitted(g, Math.round(safeValue * 100.0F) + "%",
                x(222), y(top + 7), size(6), size(43), SECONDARY_TEXT);
        int left = 187;
        int right = 257;
        int fill = left + Math.round((right - left) * safeValue);
        drawRoundedRect(g, x(left), y(top + 29), x(right), y(top + 34), size(3), 0xFF55565E);
        drawRoundedRect(g, x(left), y(top + 29), x(Math.max(left + 5, fill)), y(top + 34), size(3), accent);
        fillCircle(g, x(fill), y(top + 31), size(6), 0x26000000);
        fillCircle(g, x(fill), y(top + 30), size(5), CONTROL_KNOB);
    }

    private void drawToggle(GuiGraphics g, int top, boolean enabled, int accent) {
        int left = 229;
        int right = 264;
        int toggleTop = top + 14;
        int toggleBottom = top + 33;
        drawRoundedRect(g, x(left), y(toggleTop), x(right), y(toggleBottom), size(10),
                enabled ? accent : 0xFF55565E);
        int knob = enabled ? right - 10 : left + 10;
        fillCircle(g, x(knob), y(top + 23), size(8), 0x26000000);
        fillCircle(g, x(knob), y(top + 22), size(7), CONTROL_KNOB);
    }

    private void drawChevron(GuiGraphics g, int centerX, int centerY, int color) {
        drawDesignLine(g, centerX - 2, centerY - 6, centerX + 3, centerY, color);
        drawDesignLine(g, centerX + 3, centerY, centerX - 2, centerY + 6, color);
    }

    private void drawSettingIcon(GuiGraphics g, Icon icon, int centerX, int centerY) {
        drawRoundedRect(g, x(centerX - 14), y(centerY - 14), x(centerX + 14), y(centerY + 14),
                size(7), icon.color);
        int white = 0xFFFFFFFF;

        switch (icon) {
            case SUN -> {
                fillCircle(g, x(centerX), y(centerY), size(4), white);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * i / 4.0D;
                    int x1 = centerX + (int) Math.round(Math.cos(angle) * 7.0D);
                    int y1 = centerY + (int) Math.round(Math.sin(angle) * 7.0D);
                    int x2 = centerX + (int) Math.round(Math.cos(angle) * 10.0D);
                    int y2 = centerY + (int) Math.round(Math.sin(angle) * 10.0D);
                    drawDesignLine(g, x1, y1, x2, y2, white);
                }
            }
            case SWATCH -> {
                fillCircle(g, x(centerX - 5), y(centerY + 2), size(4), white);
                fillCircle(g, x(centerX), y(centerY - 4), size(4), 0xFFE8F0FF);
                fillCircle(g, x(centerX + 6), y(centerY + 3), size(4), 0xFFFFD7EB);
            }
            case PHOTO -> {
                drawRoundedRect(g, x(centerX - 9), y(centerY - 7), x(centerX + 9), y(centerY + 8), size(2), white);
                drawRoundedRect(g, x(centerX - 7), y(centerY - 5), x(centerX + 7), y(centerY + 6), size(1), icon.color);
                fillCircle(g, x(centerX + 3), y(centerY - 2), size(2), white);
                drawDesignLine(g, centerX - 6, centerY + 5, centerX - 1, centerY, white);
                drawDesignLine(g, centerX - 1, centerY, centerX + 6, centerY + 6, white);
            }
            case GRID -> {
                iconSquare(g, centerX - 7, centerY - 7, white);
                iconSquare(g, centerX + 2, centerY - 7, white);
                iconSquare(g, centerX - 7, centerY + 2, white);
                iconSquare(g, centerX + 2, centerY + 2, white);
            }
            case DEVICE -> {
                drawRoundedRect(g, x(centerX - 6), y(centerY - 10), x(centerX + 6), y(centerY + 10), size(3), white);
                drawRoundedRect(g, x(centerX - 4), y(centerY - 8), x(centerX + 4), y(centerY + 7), size(1), icon.color);
                fillCircle(g, x(centerX), y(centerY + 8), size(1), icon.color);
            }
            case MOTION -> {
                drawDesignLine(g, centerX - 8, centerY - 7, centerX - 2, centerY, white);
                drawDesignLine(g, centerX - 2, centerY, centerX - 8, centerY + 7, white);
                drawDesignLine(g, centerX + 1, centerY - 7, centerX + 7, centerY, white);
                drawDesignLine(g, centerX + 7, centerY, centerX + 1, centerY + 7, white);
            }
            case SPEAKER -> {
                g.fill(x(centerX - 9), y(centerY - 4), x(centerX - 4), y(centerY + 5), white);
                drawDesignLine(g, centerX - 4, centerY - 4, centerX + 1, centerY - 8, white);
                drawDesignLine(g, centerX + 1, centerY - 8, centerX + 1, centerY + 8, white);
                drawDesignLine(g, centerX + 1, centerY + 8, centerX - 4, centerY + 4, white);
                drawDesignLine(g, centerX + 5, centerY - 5, centerX + 8, centerY, white);
                drawDesignLine(g, centerX + 8, centerY, centerX + 5, centerY + 5, white);
            }
            case VIBRATE -> {
                drawRoundedRect(g, x(centerX - 5), y(centerY - 9), x(centerX + 5), y(centerY + 9), size(2), white);
                drawRoundedRect(g, x(centerX - 3), y(centerY - 7), x(centerX + 3), y(centerY + 6), size(1), icon.color);
                drawDesignLine(g, centerX - 9, centerY - 6, centerX - 9, centerY + 6, white);
                drawDesignLine(g, centerX + 9, centerY - 6, centerX + 9, centerY + 6, white);
            }
            case CALL -> {
                drawDesignLine(g, centerX - 8, centerY - 7, centerX - 4, centerY + 4, white);
                drawDesignLine(g, centerX - 4, centerY + 4, centerX + 6, centerY + 8, white);
                drawDesignLine(g, centerX - 8, centerY - 7, centerX - 3, centerY - 9, white);
                drawDesignLine(g, centerX - 3, centerY - 9, centerX, centerY - 4, white);
                drawDesignLine(g, centerX + 6, centerY + 8, centerX + 9, centerY + 3, white);
            }
            case BELL -> {
                fillCircle(g, x(centerX), y(centerY - 1), size(7), white);
                g.fill(x(centerX - 7), y(centerY), x(centerX + 8), y(centerY + 7), white);
                g.fill(x(centerX - 9), y(centerY + 6), x(centerX + 10), y(centerY + 9), white);
                fillCircle(g, x(centerX), y(centerY + 10), size(2), white);
            }
            case CLOCK -> {
                strokeCircle(g, centerX, centerY, 9, 2, white, icon.color);
                drawDesignLine(g, centerX, centerY, centerX, centerY - 6, white);
                drawDesignLine(g, centerX, centerY, centerX + 5, centerY + 3, white);
            }
            case LOCK -> {
                strokeCircle(g, centerX, centerY - 5, 6, 2, white, icon.color);
                g.fill(x(centerX - 8), y(centerY - 3), x(centerX + 9), y(centerY + 9), white);
                fillCircle(g, x(centerX), y(centerY + 2), size(2), icon.color);
                g.fill(x(centerX - 1), y(centerY + 3), x(centerX + 2), y(centerY + 7), icon.color);
            }
            case TIMER -> {
                strokeCircle(g, centerX, centerY + 1, 8, 2, white, icon.color);
                g.fill(x(centerX - 3), y(centerY - 11), x(centerX + 4), y(centerY - 8), white);
                drawDesignLine(g, centerX, centerY + 1, centerX + 4, centerY - 4, white);
            }
            case EYE -> {
                drawDesignLine(g, centerX - 10, centerY, centerX - 4, centerY - 6, white);
                drawDesignLine(g, centerX - 4, centerY - 6, centerX + 4, centerY - 6, white);
                drawDesignLine(g, centerX + 4, centerY - 6, centerX + 10, centerY, white);
                drawDesignLine(g, centerX - 10, centerY, centerX - 4, centerY + 6, white);
                drawDesignLine(g, centerX - 4, centerY + 6, centerX + 4, centerY + 6, white);
                drawDesignLine(g, centerX + 4, centerY + 6, centerX + 10, centerY, white);
                fillCircle(g, x(centerX), y(centerY), size(3), white);
            }
            case KEY -> {
                strokeCircle(g, centerX - 4, centerY - 3, 5, 2, white, icon.color);
                drawDesignLine(g, centerX, centerY + 1, centerX + 9, centerY + 10, white);
                g.fill(x(centerX + 5), y(centerY + 5), x(centerX + 9), y(centerY + 8), white);
            }
            case GLOBE -> {
                strokeCircle(g, centerX, centerY, 9, 2, white, icon.color);
                drawDesignLine(g, centerX - 8, centerY, centerX + 8, centerY, white);
                drawDesignLine(g, centerX, centerY - 8, centerX, centerY + 8, white);
            }
            case SETUP -> drawCircularArrow(g, centerX, centerY, white, icon.color);
            case RESET -> {
                drawCircularArrow(g, centerX, centerY, white, icon.color);
                g.fill(x(centerX - 1), y(centerY - 4), x(centerX + 2), y(centerY + 4), white);
                g.fill(x(centerX - 1), y(centerY + 6), x(centerX + 2), y(centerY + 9), white);
            }
        }
    }

    private void iconSquare(GuiGraphics g, int left, int top, int color) {
        drawRoundedRect(g, x(left), y(top), x(left + 6), y(top + 6), size(2), color);
    }

    private void drawCircularArrow(GuiGraphics g, int centerX, int centerY, int color, int background) {
        strokeCircle(g, centerX, centerY, 8, 2, color, background);
        g.fill(x(centerX - 10), y(centerY - 10), x(centerX), y(centerY - 3), background);
        drawDesignLine(g, centerX - 8, centerY - 8, centerX - 2, centerY - 8, color);
        drawDesignLine(g, centerX - 8, centerY - 8, centerX - 8, centerY - 2, color);
    }

    private void strokeCircle(GuiGraphics g, int centerX, int centerY, int radius, int stroke,
                              int color, int background) {
        fillCircle(g, x(centerX), y(centerY), size(radius), color);
        fillCircle(g, x(centerX), y(centerY), size(Math.max(1, radius - stroke)), background);
    }

    private void drawDesignLine(GuiGraphics g, int fromX, int fromY, int toX, int toY, int color) {
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
        int pixel = Math.max(1, size(2));
        if (steps == 0) {
            g.fill(x(fromX), y(fromY), x(fromX) + pixel, y(fromY) + pixel, color);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            float progress = step / (float) steps;
            int px = x(fromX + (toX - fromX) * progress);
            int py = y(fromY + (toY - fromY) * progress);
            g.fill(px - pixel / 2, py - pixel / 2, px + (pixel + 1) / 2, py + (pixel + 1) / 2, color);
        }
    }

    private List<Row> filteredRows(Section candidate) {
        List<Row> result = new ArrayList<>();
        for (Row row : ROWS) {
            if (row.section == candidate && matchesSearch(row)) result.add(row);
        }
        return result;
    }

    private boolean matchesSearch(Row row) {
        String needle = normalized(searchQuery);
        if (needle.isBlank()) return true;
        if (normalized(row.section.label()).contains(needle)) return true;
        String value = isToggle(row.kind)
                ? (toggleValue(row.kind) ? PhoneSettingsStore.text("Açık", "On") : PhoneSettingsStore.text("Kapalı", "Off"))
                : rowValue(row.kind);
        String haystack = row.title() + " " + row.subtitle() + " " + value;
        return normalized(haystack).contains(needle);
    }

    private String normalized(String value) {
        return (value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replace("\u0307", "");
    }

    private String rowValue(Kind kind) {
        return switch (kind) {
            case TIME_SOURCE -> PhoneSettingsStore.timeSourceLabel();
            case MODEL -> com.mikasrevs.newphone.client.phone.model.ClientPhoneModels.label(com.mikasrevs.newphone.client.phone.model.ClientPhoneModels.local().name());
            case ACCENT -> PhoneSettingsStore.accentName();
            case WALLPAPER -> PhoneSettingsStore.wallpaperName();
            case HOME_LAYOUT -> PhoneSettingsStore.homeLayoutModeLabel();
            case SIZE -> PhoneSettingsStore.phoneScaleName();
            case AUTO_LOCK -> PhoneSettingsStore.autoLockLabel();
            case LANGUAGE -> PhoneSettingsStore.languageLabel();
            case PIN -> PhoneSecurityStore.hasPin()
                    ? PhoneSettingsStore.text("Etkin", "Enabled")
                    : PhoneSettingsStore.text("Ayarlanmadı", "Not set");
            case SETUP -> System.currentTimeMillis() < setupConfirmUntil
                    ? PhoneSettingsStore.text("Onayla", "Confirm")
                    : PhoneSettingsStore.text("Başlat", "Start");
            case RESET -> PhoneSettingsStore.text("Sıfırla", "Reset");
            default -> "";
        };
    }

    private boolean isToggle(Kind kind) {
        return switch (kind) {
            case DND, LOCK_PREVIEWS, NOTIFICATIONS, VIBRATION, CALL_ANIMATION, LOCK, TIME_FORMAT, ANIMATIONS, SHOW_NUMBER, PIN -> true;
            default -> false;
        };
    }

    private boolean toggleValue(Kind kind) {
        return switch (kind) {
            case DND -> PhoneSettingsStore.doNotDisturb();
            case LOCK_PREVIEWS -> PhonePrivacyStore.previews();
            case NOTIFICATIONS -> PhoneSettingsStore.messageNotifications();
            case VIBRATION -> PhoneSettingsStore.vibration();
            case CALL_ANIMATION -> PhoneSettingsStore.incomingCallAnimationEnabled();
            case LOCK -> PhoneSettingsStore.lockScreenEnabled();
            case TIME_FORMAT -> PhoneSettingsStore.use24HourClock();
            case ANIMATIONS -> PhoneSettingsStore.animationsEnabled();
            case SHOW_NUMBER -> PhoneSettingsStore.showPhoneNumber();
            case PIN -> PhoneSecurityStore.hasPin();
            default -> false;
        };
    }

    private void drawScrollBar(GuiGraphics g) {
        float max = maxScrollPixels();
        if (max <= 0.0F) {
            scrollThumbTop = SCROLL_TOP;
            scrollThumbBottom = SCROLL_BOTTOM;
            return;
        }
        drawRoundedRect(g, x(283), y(SCROLL_TOP), x(286), y(SCROLL_BOTTOM), size(2), 0x554E4F57);
        float ratio = visibleHeightPixels() / Math.max(1.0F, contentHeightPixels());
        int thumbHeight = Math.max(42, Math.round((SCROLL_BOTTOM - SCROLL_TOP) * ratio));
        int travel = (SCROLL_BOTTOM - SCROLL_TOP) - thumbHeight;
        float progress = currentScroll / max;
        scrollThumbTop = SCROLL_TOP + Math.round(travel * progress);
        scrollThumbBottom = scrollThumbTop + thumbHeight;
        drawRoundedRect(g, x(282), y(scrollThumbTop), x(287), y(scrollThumbBottom), size(3), 0xC4777881);
    }

    private float contentHeightPixels() {
        int height = 0;
        boolean hasGroup = false;
        for (Section candidate : Section.values()) {
            int count = filteredRows(candidate).size();
            if (count == 0) continue;
            if (hasGroup) height += GROUP_GAP;
            height += SECTION_HEADER_HEIGHT + count * ROW_HEIGHT;
            hasGroup = true;
        }
        return hasGroup ? height : EMPTY_CONTENT_HEIGHT;
    }

    private float visibleHeightPixels() {
        return SCROLL_BOTTOM - SCROLL_TOP;
    }

    private float maxScrollPixels() {
        return Math.max(0.0F, contentHeightPixels() - visibleHeightPixels());
    }

    private void setScrollFromMouse(double mouseY, boolean keepGrabOffset) {
        float max = maxScrollPixels();
        if (max <= 0.0F) {
            currentScroll = 0.0F;
            targetScroll = 0.0F;
            return;
        }
        int thumbHeight = Math.max(1, scrollThumbBottom - scrollThumbTop);
        int travel = Math.max(1, SCROLL_BOTTOM - SCROLL_TOP - thumbHeight);
        double designMouseY = (mouseY - phone().y()) / Math.max(0.001F, phone().scale());
        double desiredTop = keepGrabOffset ? designMouseY - scrollBarGrabOffset : designMouseY - thumbHeight / 2.0D;
        double progress = Math.max(0.0D, Math.min(1.0D, (desiredTop - SCROLL_TOP) / travel));
        targetScroll = (float) (progress * max);
        currentScroll = targetScroll;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && handleBackButton(mouseX, mouseY)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (inside(mouseX, mouseY, 248, SEARCH_TOP, 279, SEARCH_BOTTOM) && !searchQuery.isEmpty()) {
                searchQuery = "";
                resetScroll();
                searchFocused = true;
                return true;
            }
            if (inside(mouseX, mouseY, 21, SEARCH_TOP, 279, SEARCH_BOTTOM)) {
                searchFocused = true;
                return true;
            }
            searchFocused = false;

            if (maxScrollPixels() > 0.0F && inside(mouseX, mouseY, 279, SCROLL_TOP - 4, 292, SCROLL_BOTTOM + 4)) {
                scrollBarDragging = true;
                double designMouseY = (mouseY - phone().y()) / Math.max(0.001F, phone().scale());
                if (designMouseY >= scrollThumbTop && designMouseY <= scrollThumbBottom) {
                    scrollBarGrabOffset = designMouseY - scrollThumbTop;
                    setScrollFromMouse(mouseY, true);
                } else {
                    scrollBarGrabOffset = (scrollThumbBottom - scrollThumbTop) / 2.0D;
                    setScrollFromMouse(mouseY, false);
                }
                return true;
            }

            Row row = rowAt(mouseX, mouseY);
            if (row != null) {
                if (row.kind == Kind.BRIGHTNESS || row.kind == Kind.VOLUME) {
                    if (mouseX >= x(178)) {
                        sliderDragging = row.kind;
                        setSliderValue(row.kind, mouseX);
                    }
                } else {
                    activate(row.kind, mouseX);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Row rowAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, 21, SCROLL_TOP, 279, SCROLL_BOTTOM)) return null;
        int cursor = SCROLL_TOP - Math.round(currentScroll);
        boolean hasGroup = false;
        for (Section candidate : Section.values()) {
            List<Row> rows = filteredRows(candidate);
            if (rows.isEmpty()) continue;
            if (hasGroup) cursor += GROUP_GAP;
            cursor += SECTION_HEADER_HEIGHT;
            for (Row row : rows) {
                if (inside(mouseX, mouseY, 21, cursor, 279, cursor + ROW_HEIGHT)) return row;
                cursor += ROW_HEIGHT;
            }
            hasGroup = true;
        }
        return null;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && sliderDragging != null) {
            setSliderValue(sliderDragging, mouseX);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scrollBarDragging) {
            setScrollFromMouse(mouseY, true);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (sliderDragging != null) {
            sliderDragging = null;
            return true;
        }
        if (scrollBarDragging) {
            scrollBarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0.0D) {
            targetScroll = clamp(targetScroll + (delta < 0.0D ? WHEEL_SCROLL_STEP : -WHEEL_SCROLL_STEP),
                    0.0F, maxScrollPixels());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!searchFocused || !SharedConstants.isAllowedChatCharacter(codePoint)) {
            return super.charTyped(codePoint, modifiers);
        }
        if (searchQuery.codePointCount(0, searchQuery.length()) >= 28) return true;
        searchQuery += codePoint;
        resetScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!searchFocused) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            searchFocused = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            int end = searchQuery.offsetByCodePoints(searchQuery.length(), -1);
            searchQuery = searchQuery.substring(0, end);
            resetScroll();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && !searchQuery.isEmpty()) {
            searchQuery = "";
            resetScroll();
            return true;
        }
        return true;
    }

    private void resetScroll() {
        currentScroll = 0.0F;
        targetScroll = 0.0F;
    }

    private void setSliderValue(Kind kind, double mouseX) {
        float value = (float) ((mouseX - x(187)) / Math.max(1.0D, x(257) - x(187)));
        value = clamp(value, 0.0F, 1.0F);
        if (kind == Kind.BRIGHTNESS) PhoneSettingsStore.setBrightness(value);
        if (kind == Kind.VOLUME) PhoneSettingsStore.setPhoneVolume(value);
    }

    private void activate(Kind kind, double mouseX) {
        switch (kind) {
            case MODEL -> PhoneNavigationState.open(minecraft, this, new ZPhoneModelScreen());
            case BRIGHTNESS, VOLUME -> setSliderValue(kind, mouseX);
            case ACCENT -> PhoneSettingsStore.cycleAccent();
            case WALLPAPER -> PhoneNavigationState.open(minecraft, this, new ZPhoneWallpaperScreen());
            case HOME_LAYOUT -> PhoneSettingsStore.cycleHomeLayoutMode();
            case SIZE -> PhoneSettingsStore.cyclePhoneScale();
            case DND -> PhoneSettingsStore.setDoNotDisturb(!PhoneSettingsStore.doNotDisturb());
            case LOCK_PREVIEWS -> PhonePrivacyStore.togglePreviews();
            case MY_POSTS -> PhoneNavigationState.open(minecraft, this, new PhonePostManagerScreen());
            case NOTIFICATIONS -> PhoneSettingsStore.setMessageNotifications(!PhoneSettingsStore.messageNotifications());
            case VIBRATION -> PhoneSettingsStore.setVibration(!PhoneSettingsStore.vibration());
            case CALL_ANIMATION -> PhoneSettingsStore.setIncomingCallAnimationEnabled(!PhoneSettingsStore.incomingCallAnimationEnabled());
            case LOCK -> PhoneSettingsStore.setLockScreenEnabled(!PhoneSettingsStore.lockScreenEnabled());
            case AUTO_LOCK -> PhoneSettingsStore.cycleAutoLock();
            case LANGUAGE -> PhoneSettingsStore.cycleLanguage();
            case TIME_SOURCE -> PhoneSettingsStore.setUseMinecraftTime(!PhoneSettingsStore.useMinecraftTime());
            case TIME_FORMAT -> PhoneSettingsStore.setUse24HourClock(!PhoneSettingsStore.use24HourClock());
            case ANIMATIONS -> PhoneSettingsStore.setAnimationsEnabled(!PhoneSettingsStore.animationsEnabled());
            case SHOW_NUMBER -> PhoneSettingsStore.setShowPhoneNumber(!PhoneSettingsStore.showPhoneNumber());
            case PIN -> {
                if (mouseX >= x(220)) {
                    if (PhoneSecurityStore.hasPin()) {
                        if (PhoneSecurityStore.deletePin()) PhoneSettingsStore.setPinlessUnlockMode(true);
                    } else {
                        ZPhoneClient.openPinSetup(this);
                    }
                } else {
                    ZPhoneClient.openPinSetup(this);
                }
            }
            case SETUP -> {
                long now = System.currentTimeMillis();
                if (now < setupConfirmUntil) ZPhoneClient.restartSetup();
                else setupConfirmUntil = now + 3000L;
            }
            case RESET -> {
                PhoneSettingsStore.resetDefaults();
                resetScroll();
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Section {
        APPEARANCE("Appearance", "Görünüm"),
        SOUND("Sound", "Ses"),
        PRIVACY("Privacy", "Gizlilik"),
        SYSTEM("System", "Sistem");

        final String english;
        final String turkish;

        Section(String english, String turkish) {
            this.english = english;
            this.turkish = turkish;
        }

        String label() {
            return PhoneSettingsStore.text(turkish, english);
        }
    }

    private enum Kind {
        MODEL,
        DND, LOCK_PREVIEWS, MY_POSTS,
        BRIGHTNESS,
        VOLUME,
        ACCENT,
        WALLPAPER,
        HOME_LAYOUT,
        SIZE,
        NOTIFICATIONS,
        VIBRATION,
        CALL_ANIMATION,
        LOCK,
        AUTO_LOCK,
        LANGUAGE,
        TIME_SOURCE,
        TIME_FORMAT,
        ANIMATIONS,
        SHOW_NUMBER,
        PIN,
        SETUP,
        RESET
    }

    private enum Icon {
        SUN(0xFFFFB000),
        SWATCH(0xFF5E5CE6),
        PHOTO(0xFF0A84FF),
        GRID(0xFF14A9B8),
        DEVICE(0xFF8E8E93),
        MOTION(0xFF30B85A),
        SPEAKER(0xFFFF375F),
        VIBRATE(0xFFAF52DE),
        CALL(0xFF34C759),
        BELL(0xFFFF453A),
        CLOCK(0xFF636366),
        LOCK(0xFFFF3B30),
        TIMER(0xFFFF9F0A),
        EYE(0xFF007AFF),
        KEY(0xFF8E8E93),
        GLOBE(0xFF0A84FF),
        SETUP(0xFF34C759),
        RESET(0xFFFF3B30);

        final int color;

        Icon(int color) {
            this.color = color;
        }
    }

    private record Row(Icon icon, String english, String turkish, String englishSubtitle, String turkishSubtitle,
                       Section section, Kind kind) {
        String title() {
            return PhoneSettingsStore.text(turkish, english);
        }

        String subtitle() {
            return PhoneSettingsStore.text(turkishSubtitle, englishSubtitle);
        }
    }
}

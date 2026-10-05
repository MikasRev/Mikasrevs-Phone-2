package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.security.PhoneSecurityStore;
import com.mikasrevs.newphone.client.settings.PhoneAppLayoutStore;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore.HomeLayoutMode;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.zphone.ZPhoneHeldModelState;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Light, resumable first-run setup inspired by a modern phone onboarding flow. */
public final class ZPhoneSetupScreen extends AbstractZPhoneScreen {
    private static final ResourceLocation APP_ICONS = new ResourceLocation(
            NewPhone.MOD_ID, "textures/gui/phone/app_icons.png"
    );
    private static final int ICON_TEXTURE_SIZE = 384;
    private static final int ICON_CELL = 96;
    private static final long FINISH_DURATION_MS = 850L;
    private static final int LANGUAGE_TOP = 188;
    private static final int LANGUAGE_ROW_HEIGHT = 29;

    private static final int SURFACE = 0xFFF5F5F7;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF17171A;
    private static final int MUTED = 0xFF656871;
    private static final int FAINT = 0xFF9699A2;
    private static final int LINE = 0xFFE1E3E8;
    private static final int BLUE = 0xFF477FF5;
    private static final int BLUE_HOVER = 0xFF3972EA;
    private static final int SOFT_BLUE = 0xFFE8F0FF;
    private static final int SOFT_RED = 0xFFFFECEE;

    private int step;
    private boolean finishing;
    private long finishStartedAt;
    private long noticeUntil;

    private PinStage pinStage = PinStage.NONE;
    private final StringBuilder pinInput = new StringBuilder();
    private String firstPin = "";
    private String pinNotice = "";
    private boolean pinNoticeError;
    private long pinSavedAt;

    public ZPhoneSetupScreen() {
        super(Component.literal(PhoneSettingsStore.text("MPhone Kurulum", "MPhone Setup")));
        step = PhoneSettingsStore.setupStep();
    }

    @Override
    protected void drawText(GuiGraphics graphics, String text, float designX, float designY,
                            float designHeight, int color, boolean centered) {
        String value = text == null ? "" : text;
        if (centered) {
            PhoneModernText.drawCenteredColoredNoShadow(
                    graphics, value, x(designX), y(designY), size(designHeight), color
            );
        } else {
            PhoneModernText.drawColoredNoShadow(
                    graphics, value, x(designX), y(designY), size(designHeight), color
            );
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawRoundedRect(graphics, x(8), y(8), x(292), y(602), size(24), SURFACE);
        drawLightStatusBar(graphics);

        if (finishing) {
            drawFinish(graphics);
        } else {
            if (step > 0) drawBackLink(graphics, mouseX, mouseY);
            switch (step) {
                case 1 -> drawPasscode(graphics, mouseX, mouseY);
                case 2 -> drawIdentity(graphics, mouseX, mouseY);
                case 3 -> drawAppearance(graphics, mouseX, mouseY);
                case 4 -> drawQuickGuide(graphics, mouseX, mouseY);
                default -> drawLanguage(graphics, mouseX, mouseY);
            }
        }

        if (pinStage != PinStage.NONE) drawPinOverlay(graphics, mouseX, mouseY);
        drawLightHomeIndicator(graphics);
        drawFrame(graphics);
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (pinStage == PinStage.SAVED && now - pinSavedAt >= 650L) {
            closePinOverlay();
            step = 2;
            PhoneSettingsStore.setSetupStep(step);
        }
        if (finishing && now - finishStartedAt >= FINISH_DURATION_MS) {
            ZPhoneClient.showLockScreen();
        }
    }

    private void drawLightStatusBar(GuiGraphics graphics) {
        drawStatusBar(graphics, TEXT, SURFACE);
    }

    private void drawBackLink(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, 16, 47, 96, 87);
        int color = hover ? BLUE_HOVER : BLUE;
        drawLine(graphics, 29, 62, 23, 68, color, 2);
        drawLine(graphics, 23, 68, 29, 74, color, 2);
        drawText(graphics, text("Geri", "Back"), 36, 61, 7, color, false);
    }

    private void drawLanguage(GuiGraphics graphics, int mouseX, int mouseY) {
        drawGlobeIcon(graphics, 150, 128);
        drawRoundedRect(graphics, x(24), y(188), x(276), y(508), size(9), CARD);
        List<String> codes = PhoneSettingsStore.languageCodes();
        for (int i = 0; i < codes.size(); i++) {
            int top = LANGUAGE_TOP + i * LANGUAGE_ROW_HEIGHT;
            drawLanguageRow(graphics, PhoneSettingsStore.languageLabel(i), codes.get(i), top, mouseX, mouseY);
            if (i < codes.size() - 1) graphics.fill(x(40), y(top + LANGUAGE_ROW_HEIGHT - 1), x(260), y(top + LANGUAGE_ROW_HEIGHT), LINE);
        }

        drawPrimaryButton(graphics, text("Devam", "Continue"), mouseX, mouseY);
    }

    private void drawLanguageRow(GuiGraphics graphics, String label, String languageCode, int top,
                                 int mouseX, int mouseY) {
        boolean selected = PhoneSettingsStore.language().equals(languageCode);
        boolean hover = inside(mouseX, mouseY, 24, top, 276, top + LANGUAGE_ROW_HEIGHT - 1);
        if (hover) drawRoundedRect(graphics, x(27), y(top + 2), x(273), y(top + LANGUAGE_ROW_HEIGHT - 2), size(7), 0xFFF0F3F8);
        drawText(graphics, label, 42, top + 7, 8, TEXT, false);
        if (selected) {
            drawText(graphics, text("Varsayılan", "Default"), 218, top + 8, 6, BLUE, true);
            fillCircle(graphics, x(259), y(top + 14), size(8), BLUE);
            drawCheck(graphics, 259, top + 14, 0xFFFFFFFF, 1);
        } else {
            drawLine(graphics, 257, top + 9, 262, top + 14, FAINT, 1);
            drawLine(graphics, 262, top + 14, 257, top + 19, FAINT, 1);
        }
    }

    private void drawPasscode(GuiGraphics graphics, int mouseX, int mouseY) {
        drawLockIcon(graphics, 150, 132);
        drawText(graphics, text("Bir PIN Oluştur", "Create a Passcode"), 150, 193, 14, TEXT, true);
        drawText(graphics, text("PIN, telefonundaki bilgileri korur.", "A passcode helps protect the information"),
                150, 226, 7, MUTED, true);
        drawText(graphics, text("Dilersen bu adımı atlayabilirsin.", "stored on your phone. You can skip this step."),
                150, 247, 7, MUTED, true);

        boolean hasPin = PhoneSecurityStore.hasPin();
        for (int i = 0; i < 4; i++) {
            if (hasPin) {
                fillCircle(graphics, x(126 + i * 16), y(290), size(5), BLUE);
            } else {
                fillCircle(graphics, x(126 + i * 16), y(290), size(6), LINE);
                fillCircle(graphics, x(126 + i * 16), y(290), size(3), SURFACE);
            }
        }
        drawText(graphics, text("PIN Seçenekleri", "Passcode Options"), 150, 317, 6, BLUE, true);

        boolean createHover = inside(mouseX, mouseY, 46, 345, 254, 389);
        drawRoundedRect(graphics, x(46), y(345), x(254), y(389), size(8), createHover ? BLUE_HOVER : BLUE);
        drawText(graphics, hasPin ? text("PIN'i Değiştir", "Change Passcode")
                        : text("4 Haneli PIN Oluştur", "Create 4-Digit Passcode"),
                150, 360, 8, 0xFFFFFFFF, true);

        boolean skipHover = inside(mouseX, mouseY, 46, 399, 254, 443);
        int secondaryColor = hasPin
                ? skipHover ? 0xFFDCE7FC : SOFT_BLUE
                : skipHover ? 0xFFFFDFE3 : SOFT_RED;
        drawRoundedRect(graphics, x(46), y(399), x(254), y(443), size(8), secondaryColor);
        drawText(graphics, hasPin ? text("Mevcut PIN'i Koru", "Keep Current Passcode")
                        : text("PIN Kullanmadan Devam Et", "Continue Without a Passcode"),
                150, 414, 7, hasPin ? BLUE : 0xFFD54A56, true);

        if (System.currentTimeMillis() < noticeUntil) {
            drawText(graphics, text("PIN kaldırılamadı", "Could not remove the passcode"),
                    150, 464, 6, 0xFFD54A56, true);
        }
    }

    private void drawIdentity(GuiGraphics graphics, int mouseX, int mouseY) {
        drawScanFrame(graphics, 150, 151);
        String uuid = PhoneClientData.selfUuid();
        if ((uuid == null || uuid.isBlank()) && minecraft != null && minecraft.player != null) {
            uuid = minecraft.player.getStringUUID();
        }
        drawRoundedRect(graphics, x(116), y(117), x(184), y(185), size(9), SOFT_BLUE);
        PhonePlayerHeadRenderer.draw(graphics, uuid, x(120), y(121), size(60));

        String name = PhoneClientData.selfName();
        if ((name == null || name.isBlank()) && minecraft != null && minecraft.player != null) {
            name = minecraft.player.getName().getString();
        }
        if (name == null || name.isBlank()) name = text("Oyuncu", "Player");
        drawText(graphics, fitText(name, 14, 232), 150, 211, 14, TEXT, true);

        String number = PhoneClientData.formattedSelfPhoneNumber();
        if (number == null || number.isBlank()) number = text("Numara hazırlanıyor", "Preparing phone number");
        drawText(graphics, fitText(number, 8, 220), 150, 245, 8, MUTED, true);
        drawText(graphics, text("Bu kimlik aramalarında ve", "This identity appears in your calls"),
                150, 285, 7, MUTED, true);
        drawText(graphics, text("mesajlarında görünür.", "and messages."), 150, 306, 7, MUTED, true);

        drawPrimaryButton(graphics, text("Devam", "Continue"), mouseX, mouseY);
    }

    private void drawAppearance(GuiGraphics graphics, int mouseX, int mouseY) {
        drawText(graphics, text("Görünüm", "Appearance"), 150, 99, 14, TEXT, true);
        drawText(graphics, text("Uygulamaların ana ekranında nasıl", "Choose how apps are arranged"),
                150, 129, 7, MUTED, true);
        drawText(graphics, text("görüneceğini seç.", "on your Home screen."), 150, 150, 7, MUTED, true);

        boolean classic = PhoneSettingsStore.homeLayoutMode() == HomeLayoutMode.CLASSIC;
        drawHomePreview(graphics, 34, 180, true, classic, mouseX, mouseY);
        drawHomePreview(graphics, 158, 180, false, !classic, mouseX, mouseY);

        drawText(graphics, text("Klasik", "Classic"), 89, 405, 8, TEXT, true);
        drawText(graphics, text("Tek Sayfa", "All Apps"), 213, 405, 8, TEXT, true);
        drawRadio(graphics, 89, 439, classic);
        drawRadio(graphics, 213, 439, !classic);

        drawPrimaryButton(graphics, text("Devam", "Continue"), mouseX, mouseY);
    }

    private void drawHomePreview(GuiGraphics graphics, int left, int top, boolean classic, boolean selected,
                                 int mouseX, int mouseY) {
        int right = left + 108;
        boolean hover = inside(mouseX, mouseY, left - 4, top - 4, right + 4, top + 214);
        int border = selected ? BLUE : hover ? 0xFFAABFF0 : LINE;
        drawRoundedRect(graphics, x(left - 2), y(top - 2), x(right + 2), y(top + 206), size(11), border);
        drawRoundedRect(graphics, x(left), y(top), x(right), y(top + 204), size(9), 0xFF122238);
        drawRoundedRect(graphics, x(left + 4), y(top + 4), x(right - 4), y(top + 200), size(7), 0xFF183B60);
        drawRoundedRect(graphics, x(left + 45), y(top + 7), x(left + 63), y(top + 11), size(2), 0xFF0A111A);

        List<PhoneApp> apps = PhoneAppLayoutStore.visibleHomeApps();
        if (classic) {
            drawRoundedRect(graphics, x(left + 9), y(top + 18), x(right - 9), y(top + 57), size(6), 0xCC245D89);
            fillCircle(graphics, x(left + 25), y(top + 37), size(9), 0xFFFFD76B);
            drawText(graphics, "18°", left + 65, top + 29, 8, 0xFFFFFFFF, true);
            drawRoundedRect(graphics, x(left + 10), y(top + 64), x(right - 10), y(top + 75), size(5), 0x65212D3E);
            int[] xs = {left + 24, left + 54, left + 84, left + 24, left + 54, left + 84};
            int[] ys = {top + 96, top + 96, top + 96, top + 132, top + 132, top + 132};
            for (int i = 0; i < Math.min(6, apps.size()); i++) drawMiniAppIcon(graphics, apps.get(i), xs[i], ys[i], 17);
        } else {
            int max = Math.min(20, apps.size());
            for (int i = 0; i < max; i++) {
                int cx = left + 15 + (i % 4) * 26;
                int cy = top + 31 + (i / 4) * 28;
                drawMiniAppIcon(graphics, apps.get(i), cx, cy, 13);
            }
        }

        drawRoundedRect(graphics, x(left + 16), y(top + 165), x(right - 16), y(top + 192), size(8), 0x55222C3E);
        List<PhoneApp> dock = PhoneAppLayoutStore.dockApps();
        if (!dock.isEmpty()) drawMiniAppIcon(graphics, dock.get(0), left + 39, top + 178, 17);
        if (dock.size() > 1) drawMiniAppIcon(graphics, dock.get(1), left + 69, top + 178, 17);
    }

    private void drawMiniAppIcon(GuiGraphics graphics, PhoneApp app, int centerX, int centerY, int iconSize) {
        int scaled = size(iconSize);
        if (MPhoneAppIconRenderer.handles(app)) {
            MPhoneAppIconRenderer.draw(graphics, app, x(centerX) - scaled / 2, y(centerY) - scaled / 2, scaled, false);
            return;
        }
        int sourceX = (app.iconIndex() % 4) * ICON_CELL;
        int sourceY = (app.iconIndex() / 4) * ICON_CELL;
        graphics.blit(APP_ICONS, x(centerX) - scaled / 2, y(centerY) - scaled / 2,
                scaled, scaled, (float) sourceX, (float) sourceY,
                ICON_CELL, ICON_CELL, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
    }

    private void drawRadio(GuiGraphics graphics, int centerX, int centerY, boolean selected) {
        fillCircle(graphics, x(centerX), y(centerY), size(10), selected ? BLUE : 0xFFC6C9D0);
        fillCircle(graphics, x(centerX), y(centerY), size(6), selected ? BLUE : SURFACE);
        if (selected) drawCheck(graphics, centerX, centerY, 0xFFFFFFFF, 1);
    }

    private void drawQuickGuide(GuiGraphics graphics, int mouseX, int mouseY) {
        drawText(graphics, text("Kısa Rehber", "Quick Guide"), 150, 88, 14, TEXT, true);
        drawText(graphics, text("MPhone'da hızlıca gezin.", "Move around MPhone with ease."),
                150, 118, 7, MUTED, true);

        drawGuideRow(graphics, 148, TutorialIcon.ESC,
                "ESC", text("Uygulama içindeyken bir önceki", "Return to the previous screen"),
                text("ekrana dön.", "while you are inside an app."));
        drawGuideRow(graphics, 242, TutorialIcon.HOME,
                text("Ana Ekran", "Home"), text("Alt ortadaki çubuğa dokunarak", "Tap the bottom-center bar to"),
                text("ana ekrana dön.", "return Home."));
        drawGuideRow(graphics, 336, TutorialIcon.CONTROLS,
                text("Kontrol Merkezi", "Control Center"), text("Sağ üst köşeden aşağı", "Swipe down from the top-right"),
                text("kaydırarak aç.", "corner to open it."));

        drawText(graphics, text("Ana ekrana dönüş aramayı sonlandırmaz.", "Returning Home does not end a call."),
                150, 454, 6, BLUE, true);
        if (System.currentTimeMillis() < noticeUntil) {
            drawText(graphics, text("Ayarlar kaydedilemedi", "Could not save settings"),
                    150, 481, 6, 0xFFD54A56, true);
        }
        drawPrimaryButton(graphics, text("MPhone'u Aç", "Open MPhone"), mouseX, mouseY);
    }

    private void drawGuideRow(GuiGraphics graphics, int top, TutorialIcon icon, String title,
                              String firstLine, String secondLine) {
        if (top > 148) graphics.fill(x(28), y(top - 1), x(272), y(top), LINE);
        drawTutorialIcon(graphics, icon, top);
        drawText(graphics, title, 104, top + 15, 9, TEXT, false);
        drawText(graphics, firstLine, 104, top + 43, 7, MUTED, false);
        drawText(graphics, secondLine, 104, top + 64, 7, MUTED, false);
    }

    private void drawTutorialIcon(GuiGraphics graphics, TutorialIcon icon, int top) {
        if (icon == TutorialIcon.ESC) {
            drawRoundedRect(graphics, x(39), y(top + 18), x(88), y(top + 57), size(7), BLUE);
            drawRoundedRect(graphics, x(42), y(top + 21), x(85), y(top + 54), size(5), SURFACE);
            drawText(graphics, "ESC", 63.5F, top + 32, 7, BLUE, true);
            return;
        }
        drawRoundedRect(graphics, x(45), y(top + 8), x(83), y(top + 67), size(8), BLUE);
        drawRoundedRect(graphics, x(48), y(top + 11), x(80), y(top + 64), size(6), SURFACE);
        if (icon == TutorialIcon.HOME) {
            drawRoundedRect(graphics, x(54), y(top + 53), x(74), y(top + 57), size(2), BLUE);
            return;
        }
        drawLine(graphics, 64, top + 20, 64, top + 46, BLUE, 2);
        drawLine(graphics, 56, top + 38, 64, top + 46, BLUE, 2);
        drawLine(graphics, 72, top + 38, 64, top + 46, BLUE, 2);
    }

    private void drawPrimaryButton(GuiGraphics graphics, String label, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, 24, 516, 276, 566);
        drawRoundedRect(graphics, x(24), y(516), x(276), y(566), size(9), hover ? BLUE_HOVER : BLUE);
        drawText(graphics, label, 150, 533, 9, 0xFFFFFFFF, true);
    }

    private void drawGlobeIcon(GuiGraphics graphics, int centerX, int centerY) {
        fillCircle(graphics, x(centerX), y(centerY), size(27), BLUE);
        fillCircle(graphics, x(centerX), y(centerY), size(23), SURFACE);
        graphics.fill(x(centerX - 25), y(centerY - 1), x(centerX + 26), y(centerY + 2), BLUE);
        graphics.fill(x(centerX - 2), y(centerY - 25), x(centerX + 2), y(centerY + 26), BLUE);
        drawRoundedRect(graphics, x(centerX - 13), y(centerY - 24), x(centerX + 13), y(centerY + 25), size(12), BLUE);
        drawRoundedRect(graphics, x(centerX - 10), y(centerY - 22), x(centerX + 10), y(centerY + 23), size(9), SURFACE);
    }

    private void drawLockIcon(GuiGraphics graphics, int centerX, int centerY) {
        fillCircle(graphics, x(centerX), y(centerY - 10), size(18), BLUE);
        fillCircle(graphics, x(centerX), y(centerY - 10), size(13), SURFACE);
        graphics.fill(x(centerX - 20), y(centerY - 8), x(centerX + 21), y(centerY + 10), SURFACE);
        drawRoundedRect(graphics, x(centerX - 23), y(centerY - 3), x(centerX + 23), y(centerY + 34), size(8), BLUE);
        drawRoundedRect(graphics, x(centerX - 19), y(centerY + 1), x(centerX + 19), y(centerY + 30), size(6), SURFACE);
        fillCircle(graphics, x(centerX), y(centerY + 13), size(4), BLUE);
        graphics.fill(x(centerX - 2), y(centerY + 13), x(centerX + 2), y(centerY + 22), BLUE);
    }

    private void drawScanFrame(GuiGraphics graphics, int centerX, int centerY) {
        int left = centerX - 46;
        int right = centerX + 46;
        int top = centerY - 46;
        int bottom = centerY + 46;
        drawLine(graphics, left, top + 18, left, top, BLUE, 3);
        drawLine(graphics, left, top, left + 18, top, BLUE, 3);
        drawLine(graphics, right - 18, top, right, top, BLUE, 3);
        drawLine(graphics, right, top, right, top + 18, BLUE, 3);
        drawLine(graphics, left, bottom - 18, left, bottom, BLUE, 3);
        drawLine(graphics, left, bottom, left + 18, bottom, BLUE, 3);
        drawLine(graphics, right - 18, bottom, right, bottom, BLUE, 3);
        drawLine(graphics, right, bottom - 18, right, bottom, BLUE, 3);
    }

    private void drawPinOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        beginOverlayLayer(graphics);
        drawOpaqueModalBackdrop(graphics, x(8), y(8), x(292), y(602), SURFACE);
        drawLightStatusBar(graphics);
        drawText(graphics, text("PIN Oluştur", "Create Passcode"), 150, 70, 14, TEXT, true);
        String prompt = switch (pinStage) {
            case VERIFY -> text("Önce mevcut PIN'i gir", "Enter your current passcode first");
            case CONFIRM -> text("PIN'i tekrar gir", "Enter the passcode again");
            case SAVED -> text("PIN kaydedildi", "Passcode saved");
            default -> text("4 haneli bir PIN gir", "Enter a 4-digit passcode");
        };
        if (!pinNotice.isBlank()) prompt = pinNotice;
        drawText(graphics, prompt, 150, 103, 7,
                pinNoticeError ? 0xFFD54A56 : pinStage == PinStage.SAVED ? 0xFF2B9B67 : MUTED, true);

        for (int i = 0; i < 4; i++) {
            boolean filled = i < pinInput.length() || pinStage == PinStage.SAVED;
            if (filled) {
                fillCircle(graphics, x(126 + i * 16), y(141), size(5), BLUE);
            } else {
                fillCircle(graphics, x(126 + i * 16), y(141), size(5), 0xFFC7CAD1);
                fillCircle(graphics, x(126 + i * 16), y(141), size(3), SURFACE);
            }
        }

        int[][] digits = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
        for (int row = 0; row < digits.length; row++) {
            for (int column = 0; column < digits[row].length; column++) {
                int centerX = 85 + column * 65;
                int centerY = 207 + row * 64;
                drawPinKey(graphics, Integer.toString(digits[row][column]), centerX, centerY,
                        inside(mouseX, mouseY, centerX - 25, centerY - 24, centerX + 25, centerY + 24));
            }
        }
        drawPinKey(graphics, "0", 150, 399, inside(mouseX, mouseY, 125, 375, 175, 423));

        boolean cancelHover = inside(mouseX, mouseY, 39, 375, 111, 423);
        boolean deleteHover = inside(mouseX, mouseY, 189, 375, 261, 423);
        drawRoundedRect(graphics, x(39), y(375), x(111), y(423), size(8), cancelHover ? 0xFFE1E7F3 : 0xFFE9EDF4);
        drawRoundedRect(graphics, x(189), y(375), x(261), y(423), size(8), deleteHover ? 0xFFFFDFE3 : SOFT_RED);
        drawText(graphics, text("Vazgeç", "Cancel"), 75, 392, 7, BLUE, true);
        drawText(graphics, text("Sil", "Delete"), 225, 392, 7, 0xFFD54A56, true);
        drawText(graphics, text("PIN yalnızca bu cihazda saklanır", "Your passcode stays on this device"),
                150, 462, 6, FAINT, true);
    }

    private void drawPinKey(GuiGraphics graphics, String value, int centerX, int centerY, boolean hover) {
        fillCircle(graphics, x(centerX), y(centerY), size(24), hover ? 0xFFDDE7FA : 0xFFE8EBF0);
        drawText(graphics, value, centerX, centerY - 10, 13, TEXT, true);
    }

    private void drawFinish(GuiGraphics graphics) {
        float progress = Math.min(1.0F, (System.currentTimeMillis() - finishStartedAt) / 600.0F);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        int radius = Math.round(22 + 28 * eased);
        fillCircle(graphics, x(150), y(246), size(radius), BLUE);
        drawCheck(graphics, 150, 246, 0xFFFFFFFF, 4);
        drawText(graphics, text("MPhone Hazır", "MPhone is Ready"), 150, 322, 15, TEXT, true);
        drawText(graphics, text("Açmak için yukarı kaydır", "Swipe up to unlock"), 150, 354, 7, MUTED, true);
    }

    private void drawLightHomeIndicator(GuiGraphics graphics) {
        if(!com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo())drawRoundedRect(graphics, x(113), y(594), x(187), y(598), size(2), 0xFF1D1E22);
    }

    private void drawLine(GuiGraphics graphics, int fromX, int fromY, int toX, int toY, int color, int thickness) {
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
        if (steps == 0) steps = 1;
        int scaledThickness = Math.max(1, size(thickness));
        for (int i = 0; i <= steps; i++) {
            float progress = i / (float) steps;
            int px = x(Math.round(fromX + (toX - fromX) * progress));
            int py = y(Math.round(fromY + (toY - fromY) * progress));
            graphics.fill(px, py, px + scaledThickness, py + scaledThickness, color);
        }
    }

    private void drawCheck(GuiGraphics graphics, int centerX, int centerY, int color, int thickness) {
        drawLine(graphics, centerX - 6, centerY, centerX - 1, centerY + 5, color, thickness);
        drawLine(graphics, centerX - 1, centerY + 5, centerX + 8, centerY - 6, color, thickness);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || finishing) return true;
        if (pinStage != PinStage.NONE) return handlePinClick(mouseX, mouseY);

        if (step == 4 && isHomeIndicatorTap(mouseX, mouseY)) {
            advance();
            return true;
        }
        if (step > 0 && inside(mouseX, mouseY, 16, 47, 96, 87)) {
            goBack();
            return true;
        }

        if (step == 0) {
            List<String> codes = PhoneSettingsStore.languageCodes();
            for (int i = 0; i < codes.size(); i++) {
                int top = LANGUAGE_TOP + i * LANGUAGE_ROW_HEIGHT;
                if (inside(mouseX, mouseY, 24, top, 276, top + LANGUAGE_ROW_HEIGHT - 1)) {
                    PhoneSettingsStore.setLanguage(codes.get(i));
                    return true;
                }
            }
        } else if (step == 1) {
            if (inside(mouseX, mouseY, 102, 306, 198, 334)) {
                openPinOverlay();
                return true;
            }
            if (inside(mouseX, mouseY, 46, 345, 254, 389)) {
                openPinOverlay();
                return true;
            }
            if (inside(mouseX, mouseY, 46, 399, 254, 443)) {
                if (PhoneSecurityStore.hasPin()) {
                    step = 2;
                    PhoneSettingsStore.setSetupStep(step);
                } else {
                    skipPasscode();
                }
                return true;
            }
        } else if (step == 3) {
            if (inside(mouseX, mouseY, 30, 176, 146, 454)) {
                PhoneSettingsStore.setHomeLayoutMode(HomeLayoutMode.CLASSIC);
                return true;
            }
            if (inside(mouseX, mouseY, 154, 176, 272, 454)) {
                PhoneSettingsStore.setHomeLayoutMode(HomeLayoutMode.ALL_APPS);
                return true;
            }
        }

        if (step != 1 && inside(mouseX, mouseY, 24, 516, 276, 566)) advance();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (finishing) return true;
        if (pinStage != PinStage.NONE) {
            if (pinStage == PinStage.SAVED) return true;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closePinOverlay();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                removePinDigit();
                return true;
            }
            int digit = digitForKey(keyCode);
            if (digit >= 0) appendPinDigit(digit);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_LEFT) {
            if (step > 0) goBack();
            else onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (step == 1 && PhoneSecurityStore.hasPin()) {
                step = 2;
                PhoneSettingsStore.setSetupStep(step);
            } else if (step == 1) openPinOverlay();
            else advance();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft == null || finishing) return;
        PhoneSettingsStore.setSetupStep(step);
        ZPhoneClient.markPhoneClosed();
        PhoneNavigationState.clear();
        ZPhoneHeldModelState.turnOffHeldPhone();
        minecraft.setScreen(null);
    }

    private void goBack() {
        if (step <= 0) return;
        step--;
        PhoneSettingsStore.setSetupStep(step);
    }

    private void advance() {
        if (step == 1) {
            openPinOverlay();
            return;
        }
        if (step < 4) {
            step++;
            PhoneSettingsStore.setSetupStep(step);
            return;
        }
        PhoneSettingsStore.setLockScreenEnabled(true);
        if (!PhoneSettingsStore.completeSetup()) {
            noticeUntil = System.currentTimeMillis() + 3000L;
            return;
        }
        finishing = true;
        finishStartedAt = System.currentTimeMillis();
    }

    private void skipPasscode() {
        if (!PhoneSecurityStore.deletePin()) {
            noticeUntil = System.currentTimeMillis() + 3000L;
            return;
        }
        PhoneSettingsStore.setLockScreenEnabled(true);
        PhoneSettingsStore.setPinlessUnlockMode(true);
        step = 2;
        PhoneSettingsStore.setSetupStep(step);
    }

    private boolean handlePinClick(double mouseX, double mouseY) {
        if (pinStage == PinStage.SAVED) return true;
        int[][] digits = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
        for (int row = 0; row < digits.length; row++) {
            for (int column = 0; column < digits[row].length; column++) {
                int centerX = 85 + column * 65;
                int centerY = 207 + row * 64;
                if (inside(mouseX, mouseY, centerX - 26, centerY - 25, centerX + 26, centerY + 25)) {
                    appendPinDigit(digits[row][column]);
                    return true;
                }
            }
        }
        if (inside(mouseX, mouseY, 124, 374, 176, 424)) appendPinDigit(0);
        else if (inside(mouseX, mouseY, 37, 370, 113, 428)) closePinOverlay();
        else if (inside(mouseX, mouseY, 187, 370, 263, 428)) removePinDigit();
        return true;
    }

    private void openPinOverlay() {
        pinStage = PhoneSecurityStore.hasPin() ? PinStage.VERIFY : PinStage.CREATE;
        pinInput.setLength(0);
        firstPin = "";
        pinNotice = pinStage == PinStage.VERIFY
                ? text("Önce mevcut PIN'i gir", "Enter your current passcode first")
                : "";
        pinNoticeError = false;
    }

    private void closePinOverlay() {
        pinStage = PinStage.NONE;
        pinInput.setLength(0);
        firstPin = "";
        pinNotice = "";
        pinNoticeError = false;
    }

    private void appendPinDigit(int digit) {
        if (pinStage == PinStage.SAVED || pinInput.length() >= 4) return;
        pinNotice = "";
        pinNoticeError = false;
        pinInput.append(digit);
        if (pinInput.length() < 4) return;

        if (pinStage == PinStage.VERIFY) {
            if (PhoneSecurityStore.verifyPin(pinInput.toString())) {
                pinInput.setLength(0);
                pinStage = PinStage.CREATE;
                pinNotice = text("Şimdi yeni PIN'i gir", "Now enter a new passcode");
                return;
            }
            pinInput.setLength(0);
            pinNotice = text("Mevcut PIN yanlış", "Current passcode is incorrect");
            pinNoticeError = true;
            return;
        }

        if (pinStage == PinStage.CREATE) {
            firstPin = pinInput.toString();
            pinInput.setLength(0);
            pinStage = PinStage.CONFIRM;
            return;
        }
        if (!firstPin.contentEquals(pinInput)) {
            pinInput.setLength(0);
            firstPin = "";
            pinStage = PinStage.CREATE;
            pinNotice = text("PIN'ler eşleşmedi", "Passcodes did not match");
            pinNoticeError = true;
            return;
        }
        if (PhoneSecurityStore.savePin(pinInput.toString())) {
            PhoneSettingsStore.setLockScreenEnabled(true);
            PhoneSettingsStore.setPinlessUnlockMode(false);
            pinStage = PinStage.SAVED;
            pinSavedAt = System.currentTimeMillis();
            pinNotice = text("PIN kaydedildi", "Passcode saved");
            pinNoticeError = false;
        } else {
            pinInput.setLength(0);
            firstPin = "";
            pinStage = PinStage.CREATE;
            pinNotice = text("PIN kaydedilemedi", "Could not save the passcode");
            pinNoticeError = true;
        }
    }

    private void removePinDigit() {
        if (!pinInput.isEmpty()) pinInput.deleteCharAt(pinInput.length() - 1);
        pinNotice = "";
        pinNoticeError = false;
    }

    private int digitForKey(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) return keyCode - GLFW.GLFW_KEY_0;
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) return keyCode - GLFW.GLFW_KEY_KP_0;
        return -1;
    }

    private String text(String turkish, String english) {
        return PhoneSettingsStore.text(turkish, english);
    }

    private String fitText(String value, int designHeight, int designWidth) {
        String safe = value == null ? "" : value;
        if (PhoneModernText.width(safe, designHeight) <= designWidth) return safe;
        String suffix = "...";
        int end = safe.length();
        while (end > 0 && PhoneModernText.width(safe.substring(0, end) + suffix, designHeight) > designWidth) end--;
        return safe.substring(0, end) + suffix;
    }

    private enum PinStage {
        NONE,
        VERIFY,
        CREATE,
        CONFIRM,
        SAVED
    }

    private enum TutorialIcon {
        ESC,
        HOME,
        CONTROLS
    }
}

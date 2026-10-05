package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;

import com.mikasrevs.newphone.client.emergency.EmergencyClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.emergency.EmergencyCategory;
import com.mikasrevs.newphone.emergency.EmergencyRisk;
import com.mikasrevs.newphone.emergency.EmergencyService;
import com.mikasrevs.newphone.network.EmergencyStateData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Civilian-only Emergency app. Dispatch controls live exclusively on responder tablets. */
public final class ZPhoneEmergencyScreen extends AbstractZPhoneScreen {
    private static final int DESCRIPTION_LIMIT = 160;

    private final StringBuilder description = new StringBuilder();
    private Step step = Step.HOME;
    private EmergencyService selectedService;
    private EmergencyCategory selectedCategory;
    private boolean descriptionFocused;

    public ZPhoneEmergencyScreen() {
        super(Component.literal(PhoneSettingsStore.translation("screen.newphone.app.emergency")));
        EmergencyClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawWallpaper(graphics);
        drawRoundedRect(graphics, x(3), y(40), x(297), y(606), size(22), 0xF20A0D12);
        drawStatusBar(graphics);

        EmergencyStateData.DispatchEntry owned = EmergencyClientData.ownDispatch();
        if (owned != null) drawOwnedDispatch(graphics, owned);
        else if (step == Step.HOME) drawHome(graphics, mouseX, mouseY);
        else if (step == Step.CATEGORIES) drawCategories(graphics, mouseX, mouseY);
        else drawDescription(graphics, mouseX, mouseY);

        drawNotice(graphics);
        drawHomeIndicator(graphics);
        drawFrame(graphics);
    }

    private void drawHome(GuiGraphics graphics, int mouseX, int mouseY) {
        drawHeader(graphics, false, t("screen.newphone.app.emergency"));
        fillCircle(graphics, x(150), y(145), size(31), 0xFFD73545);
        drawCross(graphics, 150, 145, 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(graphics,
                t("emergency.newphone.select_service.question"), x(150), y(202), size(11), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(graphics,
                t("emergency.newphone.select_service.subtitle"), x(150), y(225), size(7), 0xFF929DA9);
        drawServiceButton(graphics, mouseX, mouseY, EmergencyService.POLICE, 265, 369, 0xFF245E9B);
        drawServiceButton(graphics, mouseX, mouseY, EmergencyService.EMS, 391, 495, 0xFFB63A45);
    }

    private void drawServiceButton(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            EmergencyService service,
            int top,
            int bottom,
            int color
    ) {
        boolean hover = inside(mouseX, mouseY, 22, top, 278, bottom);
        drawRoundedRect(graphics, x(22), y(top), x(278), y(bottom), size(16),
                hover ? lighten(color) : color);
        fillCircle(graphics, x(60), y((top + bottom) / 2), size(21), 0x2FFFFFFF);
        if (service == EmergencyService.POLICE) drawShield(graphics, 60, (top + bottom) / 2);
        else drawCross(graphics, 60, (top + bottom) / 2, 0xFFFFFFFF);
        PhoneModernText.drawColored(graphics, t(service.translationKey()), x(94), y(top + 27), size(13), 0xFFFFFFFF);
        PhoneModernText.drawColored(graphics,
                t(service == EmergencyService.POLICE
                        ? "emergency.newphone.service.police.subtitle"
                        : "emergency.newphone.service.ems.subtitle"),
                x(94), y(top + 56), size(7), 0xFFDCE5EE);
        PhoneModernText.drawColored(graphics, ">", x(250), y(top + 38), size(13), 0xFFFFFFFF);
    }

    private void drawCategories(GuiGraphics graphics, int mouseX, int mouseY) {
        drawHeader(graphics, true, t("emergency.newphone.select_type"));
        List<EmergencyCategory> categories = EmergencyCategory.forService(selectedService);
        for (int index = 0; index < categories.size(); index++) {
            EmergencyCategory category = categories.get(index);
            int top = 101 + index * 73;
            boolean hover = inside(mouseX, mouseY, 18, top, 282, top + 64);
            drawRoundedRect(graphics, x(18), y(top), x(282), y(top + 64), size(12),
                    hover ? 0xFF252D38 : 0xFF171D25);
            int riskColor = riskColor(category.risk());
            graphics.fill(x(18), y(top + 12), x(22), y(top + 52), riskColor);
            PhoneModernText.drawColoredFitted(graphics, t(category.translationKey()),
                    x(33), y(top + 11), size(9), size(166), 0xFFFFFFFF);
            PhoneModernText.drawColored(graphics, t(category.risk().translationKey()),
                    x(33), y(top + 36), size(6), riskColor);
            PhoneModernText.drawCenteredColored(graphics,
                    t("emergency.newphone.units_short", category.maximumResponders()),
                    x(240), y(top + 23), size(7), 0xFFB8C5D2);
        }
    }

    private void drawDescription(GuiGraphics graphics, int mouseX, int mouseY) {
        drawHeader(graphics, true, t("emergency.newphone.description.title"));
        if (selectedCategory == null) {
            step = Step.HOME;
            return;
        }
        drawRoundedRect(graphics, x(20), y(104), x(280), y(164), size(12), 0xFF171E27);
        PhoneModernText.drawColoredFitted(graphics, t(selectedCategory.translationKey()),
                x(34), y(116), size(10), size(180), 0xFFFFFFFF);
        PhoneModernText.drawColored(graphics, t(selectedCategory.risk().translationKey()),
                x(34), y(141), size(6), riskColor(selectedCategory.risk()));
        PhoneModernText.drawColored(graphics,
                t("emergency.newphone.units_short", selectedCategory.maximumResponders()),
                x(216), y(128), size(6), 0xFFA8B5C2);

        PhoneModernText.drawColored(graphics, t("emergency.newphone.description.optional"),
                x(23), y(185), size(8), 0xFFD7DEE6);
        drawRoundedRect(graphics, x(20), y(207), x(280), y(367), size(13),
                descriptionFocused ? 0xFF202B38 : 0xFF171E27);
        if (description.isEmpty()) {
            drawWrapped(graphics, t("emergency.newphone.description.placeholder"),
                    34, 226, 230, 8, 0xFF717E8C, 5);
        } else {
            drawWrapped(graphics, description.toString(), 34, 226, 230, 8, 0xFFFFFFFF, 7);
        }
        PhoneModernText.drawColored(graphics, description.length() + " / " + DESCRIPTION_LIMIT,
                x(226), y(345), size(6), 0xFF7E8996);

        boolean hover = inside(mouseX, mouseY, 28, 400, 272, 450);
        drawRoundedRect(graphics, x(28), y(400), x(272), y(450), size(15),
                hover ? 0xFFE24957 : 0xFFD73545);
        PhoneModernText.drawCenteredColored(graphics, t("emergency.newphone.send"),
                x(150), y(416), size(10), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(graphics, t("emergency.newphone.location_notice"),
                x(150), y(473), size(7), 0xFF8995A2);
    }

    private void drawOwnedDispatch(GuiGraphics graphics, EmergencyStateData.DispatchEntry entry) {
        drawHeader(graphics, false, t("emergency.newphone.your_emergency"));
        EmergencyCategory category = EmergencyClientData.category(entry);
        int color = riskColor(entry.risk);
        drawRoundedRect(graphics, x(20), y(103), x(280), y(200), size(16), 0xFF171D25);
        graphics.fill(x(20), y(122), x(25), y(181), color);
        PhoneModernText.drawColoredFitted(graphics,
                category == null ? entry.categoryId : t(category.translationKey()),
                x(39), y(120), size(13), size(215), 0xFFFFFFFF);
        PhoneModernText.drawColored(graphics, "#" + entry.id, x(39), y(153), size(7), 0xFF8C98A5);
        PhoneModernText.drawColored(graphics, t(riskKey(entry.risk)), x(201), y(153), size(7), color);

        drawRoundedRect(graphics, x(20), y(220), x(280), y(278), size(13), 0xFF121820);
        PhoneModernText.drawColored(graphics, t("emergency.newphone.status.label"),
                x(34), y(235), size(7), 0xFF83909D);
        PhoneModernText.drawColoredFitted(graphics, callerStatus(entry),
                x(113), y(233), size(9), size(150), statusColor(entry.status));

        drawRoundedRect(graphics, x(20), y(294), x(280), y(390), size(13), 0xFF121820);
        PhoneModernText.drawColored(graphics, t("emergency.newphone.responders"),
                x(34), y(311), size(7), 0xFF83909D);
        PhoneModernText.drawColored(graphics,
                entry.responderCount + " / " + entry.maximumResponders,
                x(220), y(307), size(10), 0xFFFFFFFF);
        PhoneModernText.drawColored(graphics, t("emergency.newphone.nearest_responder"),
                x(34), y(348), size(7), 0xFF83909D);
        PhoneModernText.drawColoredFitted(graphics, nearestLabel(entry),
                x(151), y(345), size(8), size(112), 0xFFDCE6F0);

        if (entry.description != null && !entry.description.isBlank()) {
            drawWrapped(graphics, entry.description, 31, 414, 238, 7, 0xFFB7C1CC, 3);
        }
        if (EmergencyClientData.isActive(entry)) {
            drawRoundedRect(graphics, x(35), y(507), x(265), y(552), size(14), 0xFF4C2028);
            PhoneModernText.drawCenteredColored(graphics, t("emergency.newphone.cancel"),
                    x(150), y(521), size(9), 0xFFFFB4BA);
        }
    }

    private void drawHeader(GuiGraphics graphics, boolean back, String title) {
        if (back) drawBackButton(graphics, t("emergency.newphone.back"));
        PhoneModernText.drawCenteredColoredFitted(graphics, title, x(150), y(58),
                size(12), size(back ? 130 : 240), 0xFFFFFFFF);
    }

    private void drawNotice(GuiGraphics graphics) {
        String notice = EmergencyClientData.phoneNoticeText();
        if (notice.isBlank()) return;
        drawRoundedRect(graphics, x(35), y(91), x(265), y(124), size(12), 0xF02B3541);
        PhoneModernText.drawCenteredColoredFitted(graphics, notice,
                x(150), y(101), size(7), size(210), 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        EmergencyStateData.DispatchEntry owned = EmergencyClientData.ownDispatch();
        if (owned != null) {
            if (EmergencyClientData.isActive(owned) && inside(mouseX, mouseY, 35, 507, 265, 552)) {
                EmergencyClientData.cancel(owned.id);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (step != Step.HOME && inside(mouseX, mouseY, 15, 45, 96, 83)) {
            goBack();
            return true;
        }
        if (step == Step.HOME) {
            if (inside(mouseX, mouseY, 22, 265, 278, 369)) {
                selectedService = EmergencyService.POLICE;
                step = Step.CATEGORIES;
                return true;
            }
            if (inside(mouseX, mouseY, 22, 391, 278, 495)) {
                selectedService = EmergencyService.EMS;
                step = Step.CATEGORIES;
                return true;
            }
        } else if (step == Step.CATEGORIES) {
            List<EmergencyCategory> categories = EmergencyCategory.forService(selectedService);
            for (int index = 0; index < categories.size(); index++) {
                int top = 101 + index * 73;
                if (inside(mouseX, mouseY, 18, top, 282, top + 64)) {
                    selectedCategory = categories.get(index);
                    description.setLength(0);
                    step = Step.DESCRIPTION;
                    descriptionFocused = true;
                    return true;
                }
            }
        } else {
            if (inside(mouseX, mouseY, 20, 207, 280, 367)) {
                descriptionFocused = true;
                return true;
            }
            if (inside(mouseX, mouseY, 28, 400, 272, 450)) {
                EmergencyClientData.create(selectedCategory, description.toString());
                descriptionFocused = false;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (step == Step.DESCRIPTION && descriptionFocused && character >= 32 && character != 127) {
            if (description.length() < DESCRIPTION_LIMIT) description.append(character);
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (EmergencyClientData.ownDispatch() == null && step != Step.HOME) goBack();
            else PhoneNavigationState.backOrHome(minecraft);
            return true;
        }
        if (step == Step.DESCRIPTION && descriptionFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!description.isEmpty()) description.deleteCharAt(description.length() - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void goBack() {
        descriptionFocused = false;
        if (step == Step.DESCRIPTION) step = Step.CATEGORIES;
        else if (step == Step.CATEGORIES) {
            step = Step.HOME;
            selectedService = null;
        }
    }

    private void drawWrapped(
            GuiGraphics graphics,
            String value,
            int left,
            int top,
            int maximumWidth,
            int textSize,
            int color,
            int maximumLines
    ) {
        List<String> lines = wrap(value, size(maximumWidth), size(textSize));
        for (int index = 0; index < Math.min(maximumLines, lines.size()); index++) {
            String line = lines.get(index);
            if (index == maximumLines - 1 && lines.size() > maximumLines) line = trimToWidth(line + "...", maximumWidth, textSize);
            PhoneModernText.drawColored(graphics, line, x(left), y(top + index * (textSize + 6)), size(textSize), color);
        }
    }

    private List<String> wrap(String value, int maximumWidth, int textSize) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String rawWord : (value == null ? "" : value).trim().split("\\s+")) {
            String word = rawWord;
            while (PhoneModernText.width(word, textSize) > maximumWidth) {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                int cut = word.length();
                while (cut > 1 && PhoneModernText.width(word.substring(0, cut), textSize) > maximumWidth) cut--;
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            if (word.isEmpty()) continue;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && PhoneModernText.width(candidate, textSize) > maximumWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private String trimToWidth(String value, int maximumWidth, int textSize) {
        String result = value;
        while (!result.isEmpty() && PhoneModernText.width(result, size(textSize)) > size(maximumWidth)) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String nearestLabel(EmergencyStateData.DispatchEntry entry) {
        if (entry.responderCount <= 0) return t("emergency.newphone.no_responder");
        if (entry.nearestResponderDistance >= 0) {
            return t("emergency.newphone.blocks_away", entry.nearestResponderDistance);
        }
        if (entry.nearestResponderDifferentDimension) return t("emergency.newphone.different_dimension");
        return t("emergency.newphone.locating");
    }

    private String callerStatus(EmergencyStateData.DispatchEntry entry) {
        String status = entry == null ? null : entry.status;
        String normalized = status == null ? "waiting" : status.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "accepted", "responding" -> entry.nearestResponderDistance >= 0
                    && entry.nearestResponderDistance <= 64
                    ? t("emergency.newphone.caller_status.nearby")
                    : t("emergency.newphone.caller_status.responding");
            case "on_scene" -> t("emergency.newphone.caller_status.on_scene");
            case "closed" -> t("emergency.newphone.caller_status.resolved");
            case "cancelled" -> t("emergency.newphone.caller_status.cancelled");
            default -> t("emergency.newphone.caller_status.waiting");
        };
    }

    private int statusColor(String status) {
        if ("CLOSED".equalsIgnoreCase(status)) return 0xFF65CE91;
        if ("CANCELLED".equalsIgnoreCase(status)) return 0xFFFF7C86;
        if ("ON_SCENE".equalsIgnoreCase(status)) return 0xFF55B9FF;
        return 0xFFFFC862;
    }

    private static String riskKey(String risk) {
        return "emergency.newphone.risk." + (risk == null ? "low" : risk.toLowerCase(Locale.ROOT));
    }

    private static int riskColor(EmergencyRisk risk) {
        return risk == EmergencyRisk.HIGH ? 0xFFFF5361 : risk == EmergencyRisk.MEDIUM ? 0xFFFFB84D : 0xFF5ECF8B;
    }

    private static int riskColor(String risk) {
        try {
            return riskColor(EmergencyRisk.valueOf(risk));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return riskColor(EmergencyRisk.LOW);
        }
    }

    private void drawCross(GuiGraphics graphics, int centerX, int centerY, int color) {
        graphics.fill(x(centerX - 4), y(centerY - 15), x(centerX + 4), y(centerY + 15), color);
        graphics.fill(x(centerX - 15), y(centerY - 4), x(centerX + 15), y(centerY + 4), color);
    }

    private void drawShield(GuiGraphics graphics, int centerX, int centerY) {
        int left = x(centerX - 13);
        int right = x(centerX + 13);
        int top = y(centerY - 14);
        int middle = y(centerY + 4);
        graphics.fill(left, top, right, middle, 0xFFFFFFFF);
        for (int row = 0; row < size(16); row++) {
            int inset = row / 2;
            graphics.fill(left + inset, middle + row, right - inset, middle + row + 1, 0xFFFFFFFF);
        }
    }

    private static int lighten(int color) {
        int red = Math.min(255, ((color >> 16) & 0xFF) + 18);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 18);
        int blue = Math.min(255, (color & 0xFF) + 18);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static String t(String key, Object... arguments) {
        return PhoneSettingsStore.translation(key, arguments);
    }

    private enum Step {
        HOME,
        CATEGORIES,
        DESCRIPTION
    }
}

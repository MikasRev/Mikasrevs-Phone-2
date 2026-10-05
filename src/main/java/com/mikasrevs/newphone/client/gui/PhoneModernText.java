package com.mikasrevs.newphone.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import java.util.ArrayList;
import java.util.List;

/** Shared phone typography: scalable fonts, readable labels and consistent pixel measurements. */
public final class PhoneModernText {
    private static final int NORMAL_COLOR = 0xFFF4F4F4;
    private static final int LIGHT_COLOR = 0xFFE0E4EB;
    private static final float PHONE_TEXT_SCALE = 1.08F;
    private static final int MIN_CJK_PHYSICAL_HEIGHT = 12;
    private static final int MIN_UI_PHYSICAL_HEIGHT = 11;
    private static final ResourceLocation UI = new ResourceLocation("newphone", "phone_ui_semibold");
    private static final ResourceLocation UI_HEADING = new ResourceLocation("newphone", "phone_ui_bold");
    private static final ResourceLocation CJK_SC = new ResourceLocation("newphone", "phone_cjk_sc");
    private static final ResourceLocation CJK_TC = new ResourceLocation("newphone", "phone_cjk_tc");

    private PhoneModernText() {
    }

    public static int width(String text, int height) {
        Font font = Minecraft.getInstance().font;
        String safe = text == null ? "" : text;
        int advance = font.width(component(safe, height));
        return Math.max(1, Math.round(advance * scaleFor(font, effectiveHeight(safe, height))));
    }

    /** Font selection belongs to the phone text, rather than Minecraft's global font setting. */
    public static Component component(String text) {
        return component(text, 0);
    }

    public static Component component(String text, int height) {
        String safe = text == null ? "" : text;
        Component value = Component.literal(safe);
        ResourceLocation font;
        if (hasChinese(safe)) font = "zh_tw".equals(PhoneSettingsStore.language()) ? CJK_TC : CJK_SC;
        else font = height * guiScale() >= 18 ? UI_HEADING : UI;
        return value.copy().withStyle(Style.EMPTY.withFont(font));
    }

    public static boolean hasChinese(String text) {
        return text != null && text.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
    }

    private static int effectiveHeight(String text, int height) {
        return Math.max(minimumHeight(text), height);
    }

    private static double guiScale() {
        return Math.max(1.0D, Minecraft.getInstance().getWindow().getGuiScale());
    }

    private static int minimumHeight(String text) {
        int physical = hasChinese(text) ? MIN_CJK_PHYSICAL_HEIGHT : MIN_UI_PHYSICAL_HEIGHT;
        return Math.max(1, (int) Math.ceil(physical / guiScale()));
    }

    public static int widthLight(String text, int height) {
        return width(text, height);
    }

    public static int fittedHeight(String text, int preferredHeight, int maxWidth) {
        int safeHeight = effectiveHeight(text, preferredHeight);
        int safeWidth = Math.max(1, maxWidth);
        int measuredWidth = width(text, safeHeight);
        if (measuredWidth <= safeWidth) return safeHeight;
        int fitted = Math.max(1, (int) Math.floor(safeHeight * (safeWidth / (double) measuredWidth)));
        return Math.max(minimumHeight(text), fitted);
    }

    public static void drawCentered(GuiGraphics graphics, String text, int centerX, int y, int height) {
        drawScaled(graphics, text, centerX, y, height, NORMAL_COLOR, true);
    }

    public static void drawCenteredLight(GuiGraphics graphics, String text, int centerX, int y, int height) {
        drawScaled(graphics, text, centerX, y, height, LIGHT_COLOR, true);
    }

    public static void draw(GuiGraphics graphics, String text, int x, int y, int height) {
        drawScaled(graphics, text, x, y, height, NORMAL_COLOR, false);
    }

    public static void drawLight(GuiGraphics graphics, String text, int x, int y, int height) {
        drawScaled(graphics, text, x, y, height, LIGHT_COLOR, false);
    }

    public static void drawColored(GuiGraphics graphics, String text, int x, int y, int height, int color) {
        drawScaled(graphics, text, x, y, height, color, false);
    }

    public static void drawCenteredColored(GuiGraphics graphics, String text, int centerX, int y, int height, int color) {
        drawScaled(graphics, text, centerX, y, height, color, true);
    }

    public static void drawFitted(GuiGraphics graphics, String text, int x, int y, int height, int maxWidth) {
        drawFitted(graphics, text, x, y, height, maxWidth, NORMAL_COLOR, false);
    }

    public static void drawCenteredFitted(GuiGraphics graphics, String text, int centerX, int y,
                                          int height, int maxWidth) {
        drawFitted(graphics, text, centerX, y, height, maxWidth, NORMAL_COLOR, true);
    }

    public static void drawColoredFitted(GuiGraphics graphics, String text, int x, int y,
                                         int height, int maxWidth, int color) {
        drawFitted(graphics, text, x, y, height, maxWidth, color, false);
    }

    public static void drawCenteredColoredFitted(GuiGraphics graphics, String text, int centerX, int y,
                                                 int height, int maxWidth, int color) {
        drawFitted(graphics, text, centerX, y, height, maxWidth, color, true);
    }

    public static void drawColoredNoShadow(GuiGraphics graphics, String text, int x, int y, int height, int color) {
        drawScaled(graphics, text, x, y, height, color, false);
    }

    public static void drawCenteredColoredNoShadow(GuiGraphics graphics, String text, int centerX, int y,
                                                    int height, int color) {
        drawScaled(graphics, text, centerX, y, height, color, true);
    }

    private static void drawScaled(GuiGraphics graphics, String text, int anchorX, int y, int height, int color, boolean centered) {
        Font font = Minecraft.getInstance().font;
        String safe = text == null ? "" : text;
        float scale = scaleFor(font, effectiveHeight(safe, height));
        Component value = component(safe, height);
        int advance = font.width(value);
        int localX = centered ? -advance / 2 : 0;
        graphics.pose().pushPose();
        // Keep text on the caller's layer so later modal surfaces can cover it.
        graphics.pose().translate(anchorX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        boolean darkSurface = Minecraft.getInstance().screen instanceof com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
        graphics.drawString(font, value, localX, 0, darkSurface ? readableColor(color) : color, false);
        graphics.pose().popPose();
    }

    private static void drawFitted(GuiGraphics graphics, String text, int anchorX, int y, int height,
                                   int maxWidth, int color, boolean centered) {
        int fittedHeight = fittedHeight(text, height, maxWidth);
        int adjustedY = y + Math.max(0, (height - fittedHeight) / 2);
        String shown = fit(text, fittedHeight, maxWidth);
        drawScaled(graphics, shown, anchorX, adjustedY, fittedHeight, color, centered);
    }

    /** Keep a readable size for dense scripts; a fitted label must never escape its hit region. */
    public static String fitChinese(String text, int height, int maxWidth) {
        return fit(text, height, maxWidth);
    }

    public static String fit(String text, int height, int maxWidth) {
        String safe = text == null ? "" : text;
        if (width(safe, height) <= maxWidth) return safe;
        int end = safe.length();
        while (end > 0) {
            end = safe.offsetByCodePoints(end, -1);
            String candidate = safe.substring(0, end) + "…";
            if (width(candidate, height) <= maxWidth) return candidate;
        }
        return width("…", height) <= maxWidth ? "…" : "";
    }

    /** Lift muted text on dark phone surfaces while retaining colored accents and fade alpha. */
    public static int readableColor(int color) {
        int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
        int light = (r * 2126 + g * 7152 + b * 722) / 10000;
        int spread = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
        if (light < 60 || light >= 172 || spread > 45) return color;
        int lift = 172 - light;
        return (color & 0xFF000000) | (Math.min(255, r + lift) << 16)
                | (Math.min(255, g + lift) << 8) | Math.min(255, b + lift);
    }

    /** Shared glyph-aware wrapping for every script, including unspaced text and emoji. */
    public static List<String> wrap(String text, int height, int maxWidth) {
        String safe = text == null ? "" : text;
        var lines = new ArrayList<String>();
        for (var line : new PhoneChatDraft(new StringBuilder(safe)).lines(maxWidth, s -> width(s, height))) {
            lines.add(safe.substring(line.start(), line.end()));
        }
        return lines;
    }

    private static float scaleFor(Font font, int requestedHeight) {
        return (Math.max(1, requestedHeight) * PHONE_TEXT_SCALE) / (float) Math.max(1, font.lineHeight);
    }
}

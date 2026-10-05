package com.mikasrevs.newphone.client.tablet;

import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.emergency.EmergencyService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/** Base surface for the dedicated responder tablets. The design is authored 1:1 for GUI Scale 2 at 1920x1080. */
abstract class AbstractResponderTabletScreen extends Screen {
    protected static final int RESPONDER_CONTENT_WIDTH = 920;
    protected static final int RESPONDER_CONTENT_HEIGHT = 500;
    protected static final int RESPONDER_DESIGN_WIDTH = 960;
    protected static final int RESPONDER_DESIGN_HEIGHT = 540;

    protected final EmergencyService service;
    protected final int designWidth;
    protected final int designHeight;
    protected final int contentOffsetX;
    protected final int contentOffsetY;
    private float scale = 1.0F;
    private int originX;
    private int originY;

    protected AbstractResponderTabletScreen(EmergencyService service, Component title) {
        super(title);
        this.service = service;
        this.designWidth = RESPONDER_DESIGN_WIDTH;
        this.designHeight = RESPONDER_DESIGN_HEIGHT;
        this.contentOffsetX = 20;
        this.contentOffsetY = 20;
    }

    @Override
    protected void init() {
        super.init();

        // 960x540 is exactly the logical screen size of 1920x1080 at Minecraft GUI Scale 2.
        // Keep the authored scale at 1.0 there; only shrink as a safety fallback on smaller logical screens.
        float availableX = (width - 4.0F) / designWidth;
        float availableY = (height - 4.0F) / designHeight;
        scale = Math.min(1.0F, Math.min(availableX, availableY));
        scale = Math.max(0.45F, scale);
        originX = Math.round((width - designWidth * scale) / 2.0F);
        originY = Math.round((height - designHeight * scale) / 2.0F);
    }

    protected int accentColor() {
        return service == EmergencyService.POLICE ? 0xFF3C8DDB : 0xFFE0525E;
    }

    protected int sx(float designX) {
        return canvasX(designX + contentOffsetX);
    }

    protected int sy(float designY) {
        return canvasY(designY + contentOffsetY);
    }

    protected int canvasX(float canvasX) {
        return originX + Math.round(canvasX * scale);
    }

    protected int canvasY(float canvasY) {
        return originY + Math.round(canvasY * scale);
    }

    protected int ss(float designSize) {
        return Math.max(1, Math.round(designSize * scale));
    }

    protected boolean inside(double mouseX, double mouseY, float left, float top, float right, float bottom) {
        return mouseX >= sx(left) && mouseX <= sx(right) && mouseY >= sy(top) && mouseY <= sy(bottom);
    }

    protected void text(
            GuiGraphics graphics,
            String value,
            float x,
            float y,
            float height,
            int color,
            float maximumWidth
    ) {
        PhoneModernText.drawColoredFitted(graphics, value == null ? "" : value,
                sx(x), sy(y), ss(height), ss(maximumWidth), color);
    }

    protected void textCentered(
            GuiGraphics graphics,
            String value,
            float x,
            float y,
            float height,
            int color
    ) {
        PhoneModernText.drawCenteredColored(graphics, value == null ? "" : value,
                sx(x), sy(y), ss(height), color);
    }

    protected void wrappedText(
            GuiGraphics graphics,
            String value,
            float x,
            float y,
            float height,
            int color,
            float maximumWidth,
            int maximumLines
    ) {
        List<String> lines = wrap(value, ss(maximumWidth), ss(height));
        for (int index = 0; index < Math.min(maximumLines, lines.size()); index++) {
            String line = lines.get(index);
            if (index == maximumLines - 1 && lines.size() > maximumLines) {
                line = fitWithEllipsis(line, ss(maximumWidth), ss(height));
            }
            PhoneModernText.drawColored(graphics, line, sx(x), sy(y + index * (height + 4)), ss(height), color);
        }
    }

    protected void drawRoundedRect(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int radius,
            int color
    ) {
        if (right <= left || bottom <= top) return;
        int safeRadius = Math.max(1, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
        graphics.fill(left + safeRadius, top, right - safeRadius, bottom, color);
        graphics.fill(left, top + safeRadius, right, bottom - safeRadius, color);
        fillCircle(graphics, left + safeRadius, top + safeRadius, safeRadius, color);
        fillCircle(graphics, right - safeRadius, top + safeRadius, safeRadius, color);
        fillCircle(graphics, left + safeRadius, bottom - safeRadius, safeRadius, color);
        fillCircle(graphics, right - safeRadius, bottom - safeRadius, safeRadius, color);
    }

    protected void fillCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int half = (int) Math.floor(Math.sqrt(Math.max(0, radius * radius - row * row)));
            graphics.fill(centerX - half, centerY + row, centerX + half + 1, centerY + row + 1, color);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected static String t(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private static List<String> wrap(String value, int maximumWidth, int textSize) {
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

    private static String fitWithEllipsis(String value, int maximumWidth, int textSize) {
        String suffix = "...";
        String result = value == null ? "" : value;
        while (!result.isEmpty() && PhoneModernText.width(result + suffix, textSize) > maximumWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + suffix;
    }
}

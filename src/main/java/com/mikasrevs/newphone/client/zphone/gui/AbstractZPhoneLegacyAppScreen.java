package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneModernText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Compatibility canvas for feature-rich social apps that were originally laid
 * out on the old 900x1600 phone canvas. The app is rendered inside the native
 * MPhone/Z-Phone glass and uses the same shell, status bar, Dynamic Island,
 * Control Center gesture and home indicator as every other MPhone app.
 */
public abstract class AbstractZPhoneLegacyAppScreen extends AbstractZPhoneScreen {
    private static final float OLD_SCREEN_LEFT = 96.0F;
    private static final float OLD_SCREEN_TOP = 78.0F;
    private static final float OLD_SCREEN_WIDTH = 708.0F;
    private static final float OLD_SCREEN_HEIGHT = 1440.0F;
    private static final float X_FACTOR = DESIGN_WIDTH / OLD_SCREEN_WIDTH;
    private static final float Y_FACTOR = DESIGN_HEIGHT / OLD_SCREEN_HEIGHT;
    private static final float SIZE_FACTOR = (X_FACTOR + Y_FACTOR) * 0.5F;

    protected AbstractZPhoneLegacyAppScreen(Component title) {
        super(title);
    }

    /** Draw the native MPhone shell before the legacy app content. */
    protected final void drawLegacyAppShell(GuiGraphics graphics) {
        drawFrame(graphics);
        drawWallpaper(graphics);
        drawStatusBar(graphics);
    }

    /** Draw the physical frame and exact home indicator above app content. */
    protected final void finishLegacyAppShell(GuiGraphics graphics) {
        drawStatusBar(graphics);
        drawHomeIndicator(graphics);
        drawFrame(graphics);
    }

    protected final int screenX(float oldTextureX) {
        return x((oldTextureX - OLD_SCREEN_LEFT) * X_FACTOR);
    }

    protected final int screenY(float oldTextureY) {
        return y((oldTextureY - OLD_SCREEN_TOP) * Y_FACTOR);
    }

    protected final int screenSize(float oldTextureSize) {
        return Math.max(1, size(oldTextureSize * SIZE_FACTOR));
    }

    protected final LegacyDrawData getPhoneDrawData() {
        return new LegacyDrawData(phone().scale() * SIZE_FACTOR);
    }

    protected final void drawLiveStatusTime(GuiGraphics graphics) {
        // The native MPhone status bar is already drawn by drawLegacyAppShell().
    }

    protected final void drawCaret(GuiGraphics graphics, int x, int y, int height, int color) {
        graphics.fill(x, y, x + Math.max(1, height / 16), y + height, color);
    }

    protected final boolean handleBridgeIslandClick(double mouseX, double mouseY) {
        return handleDynamicIslandClick(mouseX, mouseY);
    }

    protected final boolean isInsideHomeIndicator(double mouseX, double mouseY) {
        return isHomeIndicatorTap(mouseX, mouseY);
    }

    protected final boolean handleHomeIndicatorClick(double mouseX, double mouseY, int button) {
        return handleSharedHomeClick(mouseX, mouseY, button);
    }

    protected final void drawLegacyCenteredLabel(GuiGraphics graphics, String text, float oldX, float oldY, float oldSize, int color) {
        PhoneModernText.drawCenteredColored(graphics, text, screenX(oldX), screenY(oldY), screenSize(oldSize), color);
    }

    protected record LegacyDrawData(float scale) {
    }
}

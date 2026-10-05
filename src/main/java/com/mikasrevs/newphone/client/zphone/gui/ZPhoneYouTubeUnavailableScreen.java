package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Friendly YouTube placeholder when the optional MCEF client mod is absent. */
public final class ZPhoneYouTubeUnavailableScreen extends AbstractZPhoneScreen {
    public ZPhoneYouTubeUnavailableScreen() {
        super(Component.literal("YouTube"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(x(3), y(40), x(297), y(606), 0xFF090B10);
        drawStatusBar(graphics);
        drawBackButton(graphics, PhoneSettingsStore.text("Geri", "Back"));

        fillCircle(graphics, x(150), y(215), size(52), 0xFFFF0033);
        int unit = size(5);
        for (int column = 0; column <= 7 * unit; column++) {
            int halfHeight = Math.max(1, (7 * unit - column) / 2);
            int px = x(150) - 2 * unit + column;
            graphics.fill(px, y(215) - halfHeight, px + 1,
                    y(215) + halfHeight + 1, 0xFFFFFFFF);
        }

        drawText(graphics, PhoneSettingsStore.text("YouTube için MCEF gerekli", "MCEF is required for YouTube"),
                150, 297, 14, 0xFFFFFFFF, true);
        drawText(graphics, PhoneSettingsStore.text("MCEF tamamen opsiyoneldir.", "MCEF is completely optional."),
                150, 337, 8, 0xFFB9C3CE, true);
        drawText(graphics, PhoneSettingsStore.text("Kurulmadığında diğer telefon özellikleri çalışmaya devam eder.",
                        "All other phone features keep working without it."),
                150, 365, 7, 0xFF8793A1, true);
        drawHomeIndicator(graphics);
        drawFrame(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && handleBackButton(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

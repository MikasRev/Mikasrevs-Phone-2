package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import net.minecraft.client.gui.GuiGraphics;

/** Shared AD/PL badge rendering for MPhone social apps on Forge 1.20.1. */
public final class PhoneRoleBadgeRenderer {
    private PhoneRoleBadgeRenderer() {}

    public static void drawNameAndBadge(
            GuiGraphics graphics,
            String name,
            String badge,
            int x,
            int y,
            int textHeight,
            int nameColor
    ) {
        String safeName = name == null || name.isBlank() ? PhoneSettingsStore.text("Oyuncu", "Player") : name;
        PhoneModernText.drawColored(graphics, safeName, x, y, textHeight, nameColor);
        if (!isSupportedBadge(badge)) return;
        int badgeHeight = Math.max(7, Math.round(textHeight * 0.68F));
        int badgeY = y + Math.max(0, (textHeight - badgeHeight) / 2);
        int gap = Math.max(3, textHeight / 3);
        drawBadge(graphics, badge, x + PhoneModernText.width(safeName, textHeight) + gap, badgeY, badgeHeight);
    }

    public static void drawBadge(GuiGraphics graphics, String badge, int x, int y, int textHeight) {
        if (!isSupportedBadge(badge)) return;
        String safe = "AD".equalsIgnoreCase(badge) ? "AD" : "PL";
        int background = "AD".equals(safe) ? 0xFFE33B45 : 0xFF2678D8;
        int h = Math.max(8, textHeight);
        int padX = Math.max(3, h / 3);
        int padY = Math.max(1, h / 7);
        int w = PhoneModernText.width(safe, h) + padX * 2;
        int bottom = y + h + padY * 2;
        drawRoundedRect(graphics, x, y - padY, x + w, bottom - padY, Math.max(3, h / 3), background);
        PhoneModernText.drawColored(graphics, safe, x + padX, y, h, 0xFFFFFFFF);
    }

    private static boolean isSupportedBadge(String badge) {
        return "AD".equalsIgnoreCase(badge) || "PL".equalsIgnoreCase(badge);
    }

    private static void drawRoundedRect(GuiGraphics graphics, int left, int top, int right, int bottom, int radius, int color) {
        int r = Math.max(1, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
        for (int py = top; py < bottom; py++) {
            int inset = 0;
            if (py < top + r) {
                int dy = top + r - py;
                inset = r - (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            } else if (py >= bottom - r) {
                int dy = py - (bottom - r - 1);
                inset = r - (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            }
            graphics.fill(left + inset, py, right - inset, py + 1, color);
        }
    }
}

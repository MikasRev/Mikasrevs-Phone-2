package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.zphone.ZPhoneTransitionState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** App-colored expansion shown while a newly opened phone app becomes visible. */
final class ZPhoneLaunchOverlay {
    private static final ResourceLocation ICONS = new ResourceLocation(
            NewPhone.MOD_ID, "textures/gui/phone/app_icons.png");
    private static final int ICON_TEXTURE_SIZE = 384;
    private static final int ICON_CELL = 96;

    private ZPhoneLaunchOverlay() {
    }

    static void draw(GuiGraphics graphics, int left, int top, int right, int bottom) {
        if (ZPhoneTransitionState.kind() != ZPhoneTransitionState.Kind.APP_OPEN) return;
        float progress = ZPhoneTransitionState.progress();
        PhoneApp app = ZPhoneTransitionState.launchApp();
        int width = Math.max(1, right - left);
        int height = Math.max(1, bottom - top);
        float scale = Math.min(width / 300.0F, height / 610.0F);
        int centerX = (left + right) / 2;
        int centerY = (top + bottom) / 2;

        int veilAlpha = Math.round(178.0F * (1.0F - smooth(Math.min(1.0F, progress / 0.62F))));
        if (veilAlpha > 0) graphics.fill(left, top, right, bottom, veilAlpha << 24);

        float expand = smooth(Math.min(1.0F, progress / 0.74F));
        int startSize = Math.max(36, Math.round(68.0F * scale));
        int cardLeft = Math.round(lerp(centerX - startSize / 2.0F, left, expand));
        int cardTop = Math.round(lerp(centerY - startSize / 2.0F, top, expand));
        int cardRight = Math.round(lerp(centerX + startSize / 2.0F, right, expand));
        int cardBottom = Math.round(lerp(centerY + startSize / 2.0F, bottom, expand));
        int fadeAlpha = progress <= 0.76F ? 255
                : Math.max(0, Math.round(255.0F * (1.0F - smooth((progress - 0.76F) / 0.24F))));
        int baseColor = app == null ? 0xFF397DDB : app.launchColor();
        int color = (fadeAlpha << 24) | (baseColor & 0x00FFFFFF);
        if (fadeAlpha > 0) {
            int radius = Math.max(2, Math.round(lerp(18.0F * scale, 22.0F * scale, expand)));
            roundedRect(graphics, cardLeft, cardTop, cardRight, cardBottom, radius, color);
        }

        if (app == null) return;
        float iconLife = progress < 0.58F ? 1.0F
                : Math.max(0.0F, 1.0F - smooth((progress - 0.58F) / 0.42F));
        int iconSize = Math.round((48.0F + 16.0F * expand) * scale * iconLife);
        if (iconSize < 3) return;
        int iconLeft = centerX - iconSize / 2;
        int iconTop = centerY - iconSize / 2;
        if (MPhoneAppIconRenderer.handles(app)) {
            MPhoneAppIconRenderer.draw(graphics, app, iconLeft, iconTop, iconSize, false);
            return;
        }
        int iconIndex = app.iconIndex();
        if (iconIndex >= 0 && iconIndex < 16) {
            int u = (iconIndex % 4) * ICON_CELL;
            int v = (iconIndex / 4) * ICON_CELL;
            graphics.blit(ICONS, iconLeft, iconTop, iconSize, iconSize,
                    (float) u, (float) v, ICON_CELL, ICON_CELL, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
        }
    }

    private static float smooth(float value) {
        float v = Math.max(0.0F, Math.min(1.0F, value));
        return v * v * (3.0F - 2.0F * v);
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static void roundedRect(GuiGraphics graphics, int left, int top, int right, int bottom,
                                    int radius, int color) {
        if (right <= left || bottom <= top) return;
        int resolved = Math.max(1, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
        graphics.fill(left + resolved, top, right - resolved, bottom, color);
        graphics.fill(left, top + resolved, right, bottom - resolved, color);
        for (int offset = 0; offset < resolved; offset++) {
            int dy = resolved - offset;
            int inset = resolved - (int) Math.sqrt(Math.max(0, resolved * resolved - dy * dy));
            graphics.fill(left + inset, top + offset, right - inset, top + offset + 1, color);
            graphics.fill(left + inset, bottom - offset - 1, right - inset, bottom - offset, color);
        }
    }
}

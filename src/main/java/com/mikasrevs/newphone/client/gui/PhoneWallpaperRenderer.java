package com.mikasrevs.newphone.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shared center-crop renderer for built-in wallpapers and gallery photos. */
public final class PhoneWallpaperRenderer {
    private PhoneWallpaperRenderer() { }

    public static void blitCover(
            GuiGraphics graphics,
            ResourceLocation texture,
            int left,
            int top,
            int right,
            int bottom,
            int textureWidth,
            int textureHeight
    ) {
        if (graphics == null || texture == null || right <= left || bottom <= top) return;

        int safeTextureWidth = Math.max(1, textureWidth);
        int safeTextureHeight = Math.max(1, textureHeight);
        int destinationWidth = right - left;
        int destinationHeight = bottom - top;
        float sourceRatio = safeTextureWidth / (float) safeTextureHeight;
        float destinationRatio = destinationWidth / (float) destinationHeight;

        int sourceX = 0;
        int sourceY = 0;
        int sourceWidth = safeTextureWidth;
        int sourceHeight = safeTextureHeight;
        if (sourceRatio > destinationRatio) {
            sourceWidth = Math.max(1, Math.round(safeTextureHeight * destinationRatio));
            sourceX = Math.max(0, (safeTextureWidth - sourceWidth) / 2);
        } else if (sourceRatio < destinationRatio) {
            sourceHeight = Math.max(1, Math.round(safeTextureWidth / destinationRatio));
            sourceY = Math.max(0, (safeTextureHeight - sourceHeight) / 2);
        }

        graphics.blit(
                texture,
                left,
                top,
                destinationWidth,
                destinationHeight,
                sourceX,
                sourceY,
                sourceWidth,
                sourceHeight,
                safeTextureWidth,
                safeTextureHeight
        );
    }
}

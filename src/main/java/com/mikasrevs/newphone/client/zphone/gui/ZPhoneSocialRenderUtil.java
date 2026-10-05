package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Shared Forge 1.20.1 rendering helpers for Lumi and X. */
final class ZPhoneSocialRenderUtil {
    private ZPhoneSocialRenderUtil() { }

    static boolean drawImage(GuiGraphics g, Minecraft minecraft, String pathValue, int left, int top, int width, int height) {
        if (minecraft == null || pathValue == null || pathValue.isBlank()) return false;
        if (pathValue.startsWith("social:")) {
            PhonePhotoStore.PhotoTexture texture = PhonePhotoStore.textureFor(minecraft, pathValue.substring("social:".length()));
            if (texture == null) return false;
            g.blit(texture.location(), left, top, width, height, 0, 0, texture.width(), texture.height(), texture.width(), texture.height());
            return true;
        }
        try {
            PhoneGalleryStore.GalleryTexture texture = PhoneGalleryStore.textureFor(minecraft, Path.of(pathValue));
            if (texture == null) return false;
            g.blit(texture.location(), left, top, width, height, 0, 0, texture.width(), texture.height(), texture.width(), texture.height());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static void drawAvatar(GuiGraphics g, Minecraft minecraft, String uuid, String socialPhoto, int left, int top, int size) {
        if (!drawImage(g, minecraft, socialPhoto, left, top, size, size)) PhonePlayerHeadRenderer.draw(g, uuid, left, top, size);
    }

    static List<String> wrap(String text, int maxChars) {
        String safe = text == null ? "" : text.strip();
        if (safe.isEmpty()) return List.of();
        var screen = Minecraft.getInstance().screen;
        int textSize = screen instanceof AbstractZPhoneScreen phone ? phone.size(8) : 8;
        return wrap(safe, maxChars, approximateWidth(maxChars), textSize);
    }

    static List<String> wrap(String text, int maxChars, int maximumWidth, int textSize) {
        String safe = text == null ? "" : text.strip();
        return safe.isEmpty() ? List.of() : PhoneModernText.wrap(safe, textSize, maximumWidth);
    }

    private static int approximateWidth(int maxChars) {
        var screen = Minecraft.getInstance().screen;
        int designWidth = Math.max(1, maxChars) * 6;
        return screen instanceof AbstractZPhoneScreen phone ? phone.size(designWidth) : designWidth;
    }

    static void drawWrapped(GuiGraphics g, String text, int left, int top, int textSize, int color, int maxChars, int maxLines, int lineHeight) {
        List<String> lines = wrap(text, maxChars, approximateWidth(maxChars), textSize);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            if (i == count - 1 && lines.size() > maxLines && line.length() > 2) line = line.substring(0, line.length() - 1) + "…";
            PhoneModernText.drawColored(g, line, left, top + i * lineHeight, textSize, color);
        }
    }

    static String compactNumber(int value) {
        if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0D);
        if (value >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000.0D);
        return Integer.toString(Math.max(0, value));
    }
}

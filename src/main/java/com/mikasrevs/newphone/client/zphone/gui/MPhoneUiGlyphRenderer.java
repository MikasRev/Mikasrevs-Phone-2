package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Renders the antialiased monochrome glyphs shared by the status bar and Control Center. */
final class MPhoneUiGlyphRenderer {
    private static final int CELL_SIZE = 64;
    private static final int TEXTURE_SIZE = CELL_SIZE * 4;
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            NewPhone.MOD_ID, "textures/gui/phone/ui_glyphs.png"
    );

    private MPhoneUiGlyphRenderer() {
    }

    static void draw(GuiGraphics graphics, Glyph glyph, int left, int top, int size, int color) {
        draw(graphics, glyph, left, top, size, size, color);
    }

    static void draw(GuiGraphics graphics, Glyph glyph, int left, int top,
                     int width, int height, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(red, green, blue, alpha);
        try {
            graphics.blit(
                    TEXTURE,
                    left,
                    top,
                    width,
                    height,
                    (float) (glyph.index % 4 * CELL_SIZE),
                    (float) (glyph.index / 4 * CELL_SIZE),
                    CELL_SIZE,
                    CELL_SIZE,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE
            );
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    enum Glyph {
        AIRPLANE(0),
        WIFI(1),
        WIFI_OFF(2),
        BLUETOOTH(3),
        SOUND(4),
        SOUND_OFF(5),
        MOON(6),
        SUN(7),
        BELL(8),
        SIGNAL(9),
        SIGNAL_OFF(10),
        BATTERY(11),
        PREVIOUS(12),
        PLAY(13),
        PAUSE(14),
        NEXT(15);

        private final int index;

        Glyph(int index) {
            this.index = index;
        }
    }
}

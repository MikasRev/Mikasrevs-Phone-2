package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Custom raster and vector icons used by the MPhone home screen. */
public final class MPhoneAppIconRenderer {
    private static final int RASTER_ICON_SIZE = 64;
    private static final Map<PhoneApp, ResourceLocation> RASTER_ICONS = Map.ofEntries(
            Map.entry(PhoneApp.MESSAGES, rasterIcon("messages")),
            Map.entry(PhoneApp.CONTACTS, rasterIcon("contacts")),
            Map.entry(PhoneApp.CALCULATOR, rasterIcon("calculator")),
            Map.entry(PhoneApp.GPS, rasterIcon("gps")),
            Map.entry(PhoneApp.NOTES, rasterIcon("notes")),
            Map.entry(PhoneApp.MUSIC, rasterIcon("music")),
            Map.entry(PhoneApp.CALENDAR, rasterIcon("calendar")),
            Map.entry(PhoneApp.CLOCK, rasterIcon("clock")),
            Map.entry(PhoneApp.BLOCKPOST, rasterIcon("blockpost")),
            Map.entry(PhoneApp.BLOCKGRAM, rasterIcon("lumi")),
            Map.entry(PhoneApp.CAMERA, rasterIcon("camera")),
            Map.entry(PhoneApp.GALLERY, rasterIcon("gallery")),
            Map.entry(PhoneApp.MINESTORE, rasterIcon("minestore")),
            Map.entry(PhoneApp.MARKETPLACE, rasterIcon("marketplace")),
            Map.entry(PhoneApp.BANK, rasterIcon("bank")),
            Map.entry(PhoneApp.PHONE, rasterIcon("phone")),
            Map.entry(PhoneApp.BROWSER, rasterIcon("tinder")),
            Map.entry(PhoneApp.SETTINGS, rasterIcon("settings"))
    );

    private MPhoneAppIconRenderer() {}

    static boolean handles(PhoneApp app) {
        return app != null && (RASTER_ICONS.containsKey(app)
                || app == PhoneApp.BLOCK_SNAKE
                || app == PhoneApp.CREEPER_RUSH
                || app == PhoneApp.GEM_PAIRS
                || app == PhoneApp.YOUTUBE
                || app == PhoneApp.EMERGENCY || app == PhoneApp.ADMIN_PANEL);
    }

    public static void draw(GuiGraphics graphics, PhoneApp app, int left, int top, int iconSize, boolean hover) {
        if (app == PhoneApp.ADMIN_PANEL) {
            roundedRect(graphics, left, top, left + iconSize, top + iconSize, Math.max(4, iconSize / 4), hover ? 0xFF4175BC : 0xFF254C85);
            int cx = left + iconSize / 2;
            int u = Math.max(1, iconSize / 14);
            for (int row = 0; row < 8 * u; row++) {
                int half = row < 4 * u ? 4 * u : Math.max(1, 8 * u - row);
                graphics.fill(cx-half, top+3*u+row, cx+half, top+3*u+row+1, 0xFFE6F2FF);
            }
            graphics.fill(cx-u, top+5*u, cx+u, top+8*u, 0xFF254C85);
            return;
        }
        if (app == PhoneApp.YOUTUBE) {
            drawYouTube(graphics, left, top, iconSize, hover);
            return;
        }
        ResourceLocation rasterIcon = app == null ? null : RASTER_ICONS.get(app);
        if (rasterIcon != null) {
            if (hover) drawRasterHover(graphics, left, top, iconSize);
            graphics.blit(
                    rasterIcon,
                    left, top,
                    iconSize, iconSize,
                    0.0F, 0.0F,
                    RASTER_ICON_SIZE, RASTER_ICON_SIZE,
                    RASTER_ICON_SIZE, RASTER_ICON_SIZE
            );
            return;
        }
        if (app == PhoneApp.EMERGENCY) {
            drawEmergency(graphics, left, top, iconSize, hover);
            return;
        }
        if (app == PhoneApp.BLOCKGRAM) {
            drawLumi(graphics, left, top, iconSize, hover);
            return;
        }
        if (app == PhoneApp.BANK) {
            drawMinimalBank(graphics, left, top, iconSize, hover);
            return;
        }
        if (app == PhoneApp.MINESTORE) {
            drawMinimalMineStore(graphics, left, top, iconSize, hover);
            return;
        }
        if (app == PhoneApp.BROWSER) {
            drawTinder(graphics, left, top, iconSize, hover);
            return;
        }
        int radius = Math.max(3, iconSize / 5);
        int border = Math.max(1, iconSize / 24);
        int bg = switch (app) {
            case MINESTORE -> hover ? 0xFF238CEB : 0xFF1376D1;
            case BANK -> hover ? 0xFF22B88A : 0xFF168C69;
            case BLOCK_SNAKE -> hover ? 0xFF31D884 : 0xFF22B96A;
            case CREEPER_RUSH -> hover ? 0xFF9474FF : 0xFF7552DB;
            case GEM_PAIRS -> hover ? 0xFFFFA455 : 0xFFF08335;
            default -> hover ? 0xFF445368 : 0xFF2B3340;
        };
        roundedRect(graphics, left, top, left + iconSize, top + iconSize, radius, 0xFFCCD7E5);
        roundedRect(graphics, left + border, top + border, left + iconSize - border, top + iconSize - border,
                Math.max(2, radius - border), bg);
        int cx = left + iconSize / 2;
        int cy = top + iconSize / 2;
        int u = Math.max(1, iconSize / 14);
        switch (app) {
            case MINESTORE -> drawStore(graphics, cx, cy, u);
            case BANK -> drawBank(graphics, cx, cy, u);
            case BLOCK_SNAKE -> drawSnake(graphics, cx, cy, u);
            case CREEPER_RUSH -> drawCreeper(graphics, cx, cy, u);
            case GEM_PAIRS -> drawGem(graphics, cx, cy, u);
            default -> graphics.fill(cx - 3 * u, cy - 3 * u, cx + 3 * u, cy + 3 * u, 0xFFFFFFFF);
        }
    }

    private static ResourceLocation rasterIcon(String name) {
        return new ResourceLocation(NewPhone.MOD_ID, "textures/gui/phone/apps/" + name + ".png");
    }

    private static void drawYouTube(GuiGraphics graphics, int left, int top, int iconSize, boolean hover) {
        int radius = Math.max(5, iconSize / 4);
        roundedRect(graphics, left, top, left + iconSize, top + iconSize, radius,
                hover ? 0xFFFF264A : 0xFFFF0033);
        int cx = left + iconSize / 2;
        int cy = top + iconSize / 2;
        int unit = Math.max(1, iconSize / 18);
        for (int column = 0; column <= 7 * unit; column++) {
            int halfHeight = Math.max(1, (7 * unit - column) / 2);
            int x = cx - 2 * unit + column;
            graphics.fill(x, cy - halfHeight, x + 1, cy + halfHeight + 1, 0xFFFFFFFF);
        }
    }

    private static void drawEmergency(GuiGraphics graphics, int left, int top, int iconSize, boolean hover) {
        int radius = Math.max(5, iconSize / 4);
        roundedRect(graphics, left, top, left + iconSize, top + iconSize, radius,
                hover ? 0xFFE84A58 : 0xFFD73545);
        int unit = Math.max(1, iconSize / 12);
        int centerX = left + iconSize / 2;
        int centerY = top + iconSize / 2;
        graphics.fill(centerX - unit, centerY - 4 * unit,
                centerX + unit + 1, centerY + 4 * unit + 1, 0xFFFFFFFF);
        graphics.fill(centerX - 4 * unit, centerY - unit,
                centerX + 4 * unit + 1, centerY + unit + 1, 0xFFFFFFFF);
    }

    private static void drawRasterHover(GuiGraphics graphics, int left, int top, int iconSize) {
        int halo = Math.max(1, iconSize / 32);
        int radius = Math.max(4, iconSize * 15 / RASTER_ICON_SIZE);
        roundedRect(graphics,
                left - halo, top - halo,
                left + iconSize + halo, top + iconSize + halo,
                radius + halo, 0x66FFD45A);
        roundedRect(graphics,
                left, top,
                left + iconSize, top + iconSize,
                radius, 0xFFFFD45A);
    }

    private static void drawLumi(GuiGraphics graphics, int left, int top, int iconSize, boolean hover) {
        int inset = Math.max(2, iconSize / 18);
        left += inset;
        top += inset;
        iconSize = Math.max(12, iconSize - inset * 2);
        int radius = Math.max(5, iconSize / 4);
        int topColor = hover ? 0xFF8A45F5 : 0xFF7435D9;
        int middleColor = hover ? 0xFFFF3D9A : 0xFFF52887;
        int bottomColor = hover ? 0xFFFFA24E : 0xFFFF7D3E;
        gradientRoundedRect(graphics, left, top, left + iconSize, top + iconSize, radius,
                topColor, middleColor, bottomColor);

        int unit = Math.max(1, iconSize / 16);
        int cameraLeft = left + 4 * unit;
        int cameraTop = top + 4 * unit;
        int cameraRight = left + iconSize - 4 * unit;
        int cameraBottom = top + iconSize - 4 * unit;
        roundedRect(graphics, cameraLeft, cameraTop, cameraRight, cameraBottom, 3 * unit, 0xFFFFFFFF);
        roundedRect(graphics, cameraLeft + 2 * unit, cameraTop + 2 * unit,
                cameraRight - 2 * unit, cameraBottom - 2 * unit, Math.max(1, 2 * unit), middleColor);
        fillCircle(graphics, left + iconSize / 2, top + iconSize / 2, Math.max(2, 3 * unit), 0xFFFFFFFF);
        fillCircle(graphics, left + iconSize / 2, top + iconSize / 2, Math.max(1, unit), middleColor);
        fillCircle(graphics, cameraRight - 3 * unit, cameraTop + 3 * unit, Math.max(1, unit), 0xFFFFFFFF);
    }

    private static void drawTinder(GuiGraphics graphics, int left, int top, int iconSize, boolean hover) {
        int radius = Math.max(5, iconSize / 4);
        gradientRoundedRect(graphics, left, top, left + iconSize, top + iconSize, radius,
                hover ? 0xFFFF6A75 : 0xFFFF526A,
                hover ? 0xFFFF536F : 0xFFFF3E61,
                hover ? 0xFFFF865A : 0xFFFF7358);
        int centerX = left + iconSize / 2;
        int centerY = top + iconSize / 2;
        int unit = Math.max(1, iconSize / 18);
        fillCircle(graphics, centerX, centerY + 3 * unit, 5 * unit, 0xFFFDFEFF);
        fillCircle(graphics, centerX - 3 * unit, centerY - unit, 4 * unit, 0xFFFDFEFF);
        fillCircle(graphics, centerX + 3 * unit, centerY - unit, 4 * unit, 0xFFFDFEFF);
        graphics.fill(centerX - 2 * unit, centerY - 7 * unit,
                centerX + unit, centerY + 5 * unit, 0xFFFDFEFF);
        fillCircle(graphics, centerX + 2 * unit, centerY - 5 * unit, 3 * unit, 0xFFFDFEFF);
    }

    private static void drawMinimalBank(GuiGraphics graphics, int left, int top, int iconSize, boolean hover) {
        int radius = Math.max(5, iconSize / 4);
        gradientRoundedRect(graphics, left, top, left + iconSize, top + iconSize, radius,
                hover ? 0xFF163C59 : 0xFF102E46,
                hover ? 0xFF147E83 : 0xFF11666D,
                hover ? 0xFF19B88D : 0xFF129675);
        int unit = Math.max(1, iconSize / 18);
        int cardLeft = left + 4 * unit;
        int cardTop = top + 6 * unit;
        int cardRight = left + iconSize - 4 * unit;
        int cardBottom = top + iconSize - 5 * unit;
        roundedRect(graphics, cardLeft, cardTop, cardRight, cardBottom, 3 * unit, 0xFFF7FBFF);
        roundedRect(graphics, cardLeft + unit, cardTop + unit,
                cardRight - unit, cardBottom - unit, 2 * unit, 0x19102E46);
        graphics.fill(cardLeft + 3 * unit, cardTop + 4 * unit,
                cardRight - 3 * unit, cardTop + 5 * unit, 0xFF1A7B79);
        fillCircle(graphics, cardRight - 4 * unit, cardBottom - 4 * unit, Math.max(1, unit), 0xFF16A882);
    }

    private static void drawMinimalMineStore(GuiGraphics graphics, int left, int top, int iconSize, boolean hover) {
        int radius = Math.max(5, iconSize / 4);
        gradientRoundedRect(graphics, left, top, left + iconSize, top + iconSize, radius,
                hover ? 0xFF244C91 : 0xFF1B3D78,
                hover ? 0xFF4267DC : 0xFF3454BC,
                hover ? 0xFF7657E8 : 0xFF6245CF);
        int unit = Math.max(1, iconSize / 18);
        int bagLeft = left + 5 * unit;
        int bagTop = top + 7 * unit;
        int bagRight = left + iconSize - 5 * unit;
        int bagBottom = top + iconSize - 4 * unit;
        roundedRect(graphics, bagLeft, bagTop, bagRight, bagBottom, 3 * unit, 0xFFF8FAFF);
        graphics.fill(left + 7 * unit, top + 5 * unit, left + 8 * unit, top + 9 * unit, 0xFFF8FAFF);
        graphics.fill(left + iconSize - 8 * unit, top + 5 * unit,
                left + iconSize - 7 * unit, top + 9 * unit, 0xFFF8FAFF);
        graphics.fill(left + 7 * unit, top + 5 * unit,
                left + iconSize - 7 * unit, top + 6 * unit, 0xFFF8FAFF);
        int centerX = left + iconSize / 2;
        int centerY = top + 12 * unit;
        graphics.fill(centerX - 3 * unit, centerY - 2 * unit,
                centerX + 3 * unit, centerY + 3 * unit, 0xFF58C96B);
        graphics.fill(centerX - 3 * unit, centerY - 2 * unit, centerX, centerY, 0xFF81E58C);
        graphics.fill(centerX, centerY - 2 * unit, centerX + 3 * unit, centerY, 0xFF42A957);
    }


    private static void gradientRoundedRect(GuiGraphics graphics, int left, int top, int right, int bottom,
                                            int radius, int topColor, int middleColor, int bottomColor) {
        int height = Math.max(1, bottom - top);
        int resolvedRadius = Math.max(1, Math.min(radius, Math.min((right - left) / 2, height / 2)));
        for (int py = top; py < bottom; py++) {
            float progress = (py - top) / (float) Math.max(1, height - 1);
            int color = progress < 0.56F
                    ? lerpColor(topColor, middleColor, progress / 0.56F)
                    : lerpColor(middleColor, bottomColor, (progress - 0.56F) / 0.44F);
            int inset = 0;
            if (py < top + resolvedRadius) {
                int dy = top + resolvedRadius - py;
                inset = resolvedRadius - (int) Math.sqrt(Math.max(0,
                        resolvedRadius * resolvedRadius - dy * dy));
            } else if (py >= bottom - resolvedRadius) {
                int dy = py - (bottom - resolvedRadius - 1);
                inset = resolvedRadius - (int) Math.sqrt(Math.max(0,
                        resolvedRadius * resolvedRadius - dy * dy));
            }
            graphics.fill(left + inset, py, right - inset, py + 1, color);
        }
    }

    private static int lerpColor(int first, int second, float progress) {
        float value = Math.max(0.0F, Math.min(1.0F, progress));
        int firstRed = (first >>> 16) & 0xFF;
        int firstGreen = (first >>> 8) & 0xFF;
        int firstBlue = first & 0xFF;
        int red = Math.round(firstRed + (((second >>> 16) & 0xFF) - firstRed) * value);
        int green = Math.round(firstGreen + (((second >>> 8) & 0xFF) - firstGreen) * value);
        int blue = Math.round(firstBlue + ((second & 0xFF) - firstBlue) * value);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static void drawStore(GuiGraphics g, int cx, int cy, int u) {
        roundedRect(g, cx - 5*u, cy - 3*u, cx + 5*u, cy + 5*u, 2*u, 0xFFF5F8FC);
        g.fill(cx - 4*u, cy - 5*u, cx + 4*u, cy - 2*u, 0xFFF5F8FC);
        g.fill(cx - 2*u, cy - 7*u, cx + 2*u, cy - 5*u, 0xFFF5F8FC);
        g.fill(cx - u, cy - u, cx + u, cy + 4*u, 0xFF1376D1);
        g.fill(cx - 3*u, cy + u, cx + 3*u, cy + 3*u, 0xFF1376D1);
    }

    private static void drawBank(GuiGraphics g, int cx, int cy, int u) {
        int w = 0xFFF6FAFD;
        g.fill(cx - 6*u, cy - 4*u, cx + 6*u, cy - 2*u, w);
        g.fill(cx - 5*u, cy + 4*u, cx + 5*u, cy + 6*u, w);
        g.fill(cx - 5*u, cy - 2*u, cx - 3*u, cy + 4*u, w);
        g.fill(cx - u, cy - 2*u, cx + u, cy + 4*u, w);
        g.fill(cx + 3*u, cy - 2*u, cx + 5*u, cy + 4*u, w);
        g.fill(cx - 5*u, cy - 8*u, cx + 5*u, cy - 6*u, w);
    }

    private static void drawSnake(GuiGraphics g, int cx, int cy, int u) {
        int w = 0xFFF5FFF8;
        roundedRect(g, cx - 6*u, cy - 4*u, cx, cy, 2*u, w);
        roundedRect(g, cx - 2*u, cy - u, cx + 5*u, cy + 3*u, 2*u, w);
        roundedRect(g, cx + 2*u, cy + 2*u, cx + 6*u, cy + 6*u, 2*u, w);
        g.fill(cx + 3*u, cy + 3*u, cx + 4*u, cy + 4*u, 0xFF173421);
        fillCircle(g, cx - 4*u, cy - 2*u, Math.max(1,u), 0xFFFFD45C);
    }

    private static void drawCreeper(GuiGraphics g, int cx, int cy, int u) {
        roundedRect(g, cx - 6*u, cy - 6*u, cx + 6*u, cy + 6*u, 2*u, 0xFF9EF77D);
        g.fill(cx - 4*u, cy - 3*u, cx - u, cy, 0xFF152116);
        g.fill(cx + u, cy - 3*u, cx + 4*u, cy, 0xFF152116);
        g.fill(cx - u, cy, cx + u, cy + 3*u, 0xFF152116);
        g.fill(cx - 3*u, cy + 2*u, cx + 3*u, cy + 5*u, 0xFF152116);
        g.fill(cx - 4*u, cy + 4*u, cx - 2*u, cy + 6*u, 0xFF152116);
        g.fill(cx + 2*u, cy + 4*u, cx + 4*u, cy + 6*u, 0xFF152116);
    }

    private static void drawGem(GuiGraphics g, int cx, int cy, int u) {
        int w = 0xFFF7FBFF;
        g.fill(cx - 5*u, cy - 2*u, cx + 5*u, cy + 2*u, w);
        g.fill(cx - 3*u, cy - 5*u, cx + 3*u, cy + 5*u, w);
        g.fill(cx - 4*u, cy - 4*u, cx + 4*u, cy + 4*u, w);
        g.fill(cx - 2*u, cy - 4*u, cx, cy - 2*u, 0xFFFFD4A4);
        g.fill(cx + u, cy + u, cx + 3*u, cy + 3*u, 0xFFFF8A3D);
    }

    private static void roundedRect(GuiGraphics g, int left, int top, int right, int bottom, int radius, int color) {
        int r = Math.max(1, Math.min(radius, Math.min((right-left)/2, (bottom-top)/2)));
        g.fill(left + r, top, right - r, bottom, color);
        g.fill(left, top + r, right, bottom - r, color);
        fillCircle(g, left + r, top + r, r, color);
        fillCircle(g, right - r, top + r, r, color);
        fillCircle(g, left + r, bottom - r, r, color);
        fillCircle(g, right - r, bottom - r, r, color);
    }

    private static void fillCircle(GuiGraphics g, int cx, int cy, int radius, int color) {
        int r = Math.max(1, radius);
        for (int dy = -r; dy <= r; dy++) {
            int half = (int)Math.sqrt(Math.max(0, r*r - dy*dy));
            g.fill(cx-half, cy+dy, cx+half+1, cy+dy+1, color);
        }
    }
}

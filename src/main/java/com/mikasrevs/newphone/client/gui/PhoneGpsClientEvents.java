package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.video.PhoneVideoStreamClient;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.PhoneYouTubeState;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneCallScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneGpsScreen;
import com.mikasrevs.newphone.emergency.EmergencyService;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** X restores a minimized call first, otherwise it toggles the GPS minimap. */
public final class PhoneGpsClientEvents {
    private static final String CATEGORY = "key.category.newphone.phone";
    private static KeyMapping toggleGpsMinimap;

    private PhoneGpsClientEvents() { }

    @Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() { }
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            toggleGpsMinimap = new KeyMapping("key.newphone.gps_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
            event.register(toggleGpsMinimap);
        }
        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("gps_minimap", (gui, graphics, partialTick, screenWidth, screenHeight) -> renderMinimap(graphics, screenWidth, screenHeight));
        }
    }

    @Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() { }
        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft minecraft = Minecraft.getInstance();

            if (PhoneGpsState.finishRouteIfArrived(minecraft)) {
                if (minecraft.screen instanceof ZPhoneGpsScreen) PhoneNavigationState.backOrHome(minecraft);
            }

            if (toggleGpsMinimap == null) return;
            while (toggleGpsMinimap.consumeClick()) {
                if (PhoneYouTubeState.isModelMode()) {
                    PhoneYouTubeState.reopen(minecraft);
                    continue;
                }
                if (PhoneClientData.hasCall()) {
                    if (minecraft.screen instanceof ZPhoneCallScreen) continue;
                    if (PhoneClientData.isVideoCall() && PhoneVideoStreamClient.isWorldMini()) {
                        PhoneVideoStreamClient.leaveWorldMiniMode();
                    }
                    minecraft.setScreen(new ZPhoneCallScreen());
                    continue;
                }
                if (!PhoneGpsState.isMinimapActive() || System.currentTimeMillis() < PhoneGpsState.ignoreToggleUntil()) continue;
                PhoneGpsState.disableAndOpenGps(minecraft);
            }
        }
    }

    private static void renderMinimap(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!PhoneGpsState.isMinimapActive() || PhoneClientData.hasCall()
                || minecraft.player == null || minecraft.level == null) return;

        int margin = hudPxAtGuiScale2(12);
        int inset = hudPxAtGuiScale2(6);
        int headerH = hudPxAtGuiScale2(32);
        int footerH = hudPxAtGuiScale2(42);
        int preferredMapW = hudPxAtGuiScale2(128);
        int preferredMapH = hudPxAtGuiScale2(98);
        int availableMapHeight = screenHeight - margin * 2 - headerH - footerH;
        int availableMapWidth = screenWidth - margin * 2 - inset * 2;
        if (availableMapWidth < hudPxAtGuiScale2(64) || availableMapHeight < hudPxAtGuiScale2(48)) return;
        float fit = Math.min(1.0F, Math.min(
                availableMapWidth / (float) preferredMapW,
                availableMapHeight / (float) preferredMapH));
        int mapW = Math.max(hudPxAtGuiScale2(64), Math.round(preferredMapW * fit));
        int mapH = Math.max(hudPxAtGuiScale2(48), Math.round(preferredMapH * fit));
        int panelW = mapW + inset * 2;
        int panelH = headerH + mapH + footerH;
        int left = screenWidth - panelW - margin;
        int top = screenHeight - panelH - margin;
        int right = left + panelW;
        int bottom = top + panelH;
        int radius = hudPxAtGuiScale2(8);
        EmergencyService responderService = PhoneGpsState.responderService();
        int accent = serviceAccent(responderService);
        int accentDark = serviceAccentDark(responderService);

        drawRoundedRect(graphics, left + hudPx(2), top + hudPx(3),
                right + hudPx(2), bottom + hudPx(3), radius, 0x8C000000);
        drawRoundedRect(graphics, left, top, right, bottom, radius, 0xF5081017);
        drawRoundedRect(graphics, left + hudPx(1), top + hudPx(1), right - hudPx(1),
                bottom - hudPx(1), Math.max(1, radius - hudPx(1)), 0xFF0C1721);
        graphics.fill(left, top + radius, left + hudPxAtGuiScale2(3), bottom - radius, accent);
        graphics.fill(left + radius, top, right - radius, top + hudPx(1), 0x52FFFFFF);

        int badgeX = left + inset + hudPxAtGuiScale2(11);
        int badgeY = top + headerH / 2;
        drawServiceBadge(graphics, badgeX, badgeY, responderService, accent, accentDark);
        String title = responderService == EmergencyService.POLICE
                ? PhoneSettingsStore.text("POLİS GPS", "POLICE GPS")
                : responderService == EmergencyService.EMS
                ? PhoneSettingsStore.text("AMBULANS GPS", "AMBULANCE GPS")
                : PhoneSettingsStore.text("GPS NAVİGASYON", "GPS NAVIGATION");
        int titleX = left + inset + hudPxAtGuiScale2(27);
        PhoneModernText.drawColored(graphics, fitText(title, mapW - hudPxAtGuiScale2(49),
                        hudPxAtGuiScale2(8)), titleX,
                top + hudPxAtGuiScale2(7), hudPxAtGuiScale2(8), 0xFFF3F7FA);
        fillCircle(graphics, titleX + hudPxAtGuiScale2(2), top + hudPxAtGuiScale2(23),
                hudPxAtGuiScale2(2), 0xFF55D18B);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("CANLI BİRİM ROTASI", "LIVE UNIT ROUTE"),
                titleX + hudPxAtGuiScale2(7), top + hudPxAtGuiScale2(19), hudPxAtGuiScale2(5), 0xFF8297A8);

        int closeRadius = hudPxAtGuiScale2(7);
        int closeX = right - inset - closeRadius;
        fillCircle(graphics, closeX, badgeY, closeRadius, 0xFF202D38);
        PhoneModernText.drawCenteredColored(graphics, "X", closeX,
                top + hudPxAtGuiScale2(12), hudPxAtGuiScale2(6), 0xFFD5DEE5);

        int mapLeft = left + inset;
        int mapTop = top + headerH;
        graphics.fill(mapLeft - hudPx(2), mapTop - hudPx(2),
                mapLeft + mapW + hudPx(2), mapTop + mapH + hudPx(2), accentDark);
        PhoneGpsState.Location target = PhoneGpsState.targetLocation();
        PhoneGpsMapRenderer.drawMinimap(graphics, minecraft, mapLeft, mapTop, mapW, mapH, 0,
                target, true, PhoneGpsMapRenderer.MapStyle.CONTRAST);
        drawCompass(graphics, mapLeft + hudPxAtGuiScale2(10), mapTop + hudPxAtGuiScale2(10), accentDark);

        int footerTop = mapTop + mapH;
        graphics.fill(left + inset, footerTop, right - inset, footerTop + hudPx(1), 0x32FFFFFF);
        PhoneGpsState.Location loc = PhoneGpsState.currentLocation(minecraft);
        boolean routeHere = target != null && loc.dimension().equals(target.dimension());
        String status;
        int statusColor;
        if (routeHere) {
            long distance = Math.round(PhoneGpsState.horizontalDistance(loc, target));
            status = PhoneSettingsStore.text("HEDEF  ", "TARGET  ") + distance
                    + PhoneSettingsStore.text(" BLOK  •  ", " BLOCKS  •  ")
                    + targetDirection(loc, target) + "  •  " + etaLabel(distance);
            statusColor = accent;
        } else if (target != null) {
            status = target.friendlyDimension();
            statusColor = 0xFFFFB84D;
        } else {
            status = PhoneSettingsStore.text("MEVCUT KONUM", "CURRENT POSITION");
            statusColor = 0xFF6BB6FF;
        }
        int statusHeight = hudPxAtGuiScale2(6);
        int coordinateHeight = hudPxAtGuiScale2(7);
        PhoneModernText.drawColored(graphics, fitText(status, mapW, statusHeight), left + inset,
                footerTop + hudPxAtGuiScale2(6), statusHeight, statusColor);
        String coordinates = "X " + loc.x() + "   Z " + loc.z() + "   •   "
                + facingLabel(minecraft.player.getYRot());
        PhoneModernText.drawColored(graphics, fitText(coordinates, mapW, coordinateHeight), left + inset,
                footerTop + hudPxAtGuiScale2(17), coordinateHeight, 0xFFE5EBF0);
        String hint = PhoneSettingsStore.text("X: TAM HARİTAYI AÇ", "X: OPEN FULL MAP");
        PhoneModernText.drawColored(graphics, fitText(hint, mapW, hudPxAtGuiScale2(5)), left + inset,
                footerTop + hudPxAtGuiScale2(30), hudPxAtGuiScale2(5), 0xFF728696);
        graphics.fill(left + radius, bottom - hudPx(2), right - radius, bottom - hudPx(1), accent);
    }

    private static int hudPx(int physicalPixels) {
        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = minecraft == null || minecraft.getWindow() == null
                ? 1.0D : Math.max(1.0D, minecraft.getWindow().getGuiScale());
        return Math.max(1, Math.round((float) (physicalPixels / guiScale)));
    }

    private static int hudPxAtGuiScale2(int guiScale2Units) {
        return hudPx(guiScale2Units * 2);
    }

    private static void drawRoundedRect(
            GuiGraphics graphics, int left, int top, int right, int bottom, int radius, int color
    ) {
        if (right <= left || bottom <= top) return;
        int r = Math.max(1, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
        graphics.fill(left + r, top, right - r, bottom, color);
        graphics.fill(left, top + r, right, bottom - r, color);
        fillCircle(graphics, left + r, top + r, r, color);
        fillCircle(graphics, right - r, top + r, r, color);
        fillCircle(graphics, left + r, bottom - r, r, color);
        fillCircle(graphics, right - r, bottom - r, r, color);
    }

    private static void fillCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = (int) Math.floor(Math.sqrt(radius * radius - y * y));
            graphics.fill(centerX - halfWidth, centerY + y,
                    centerX + halfWidth + 1, centerY + y + 1, color);
        }
    }

    private static void drawLocationPin(
            GuiGraphics graphics, int centerX, int centerY, int color, int background
    ) {
        int outer = hudPxAtGuiScale2(3);
        fillCircle(graphics, centerX, centerY - hudPx(1), outer, color);
        fillCircle(graphics, centerX, centerY - hudPx(1), Math.max(1, outer / 2), background);
        graphics.fill(centerX - hudPx(1), centerY + outer - hudPx(1),
                centerX + hudPx(1) + 1, centerY + outer + hudPxAtGuiScale2(3), color);
    }

    private static void drawServiceBadge(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            EmergencyService service,
            int accent,
            int accentDark
    ) {
        int radius = hudPxAtGuiScale2(10);
        fillCircle(graphics, centerX, centerY, radius, 0xFF02070B);
        fillCircle(graphics, centerX, centerY, Math.max(1, radius - hudPx(1)), accentDark);
        fillCircle(graphics, centerX, centerY, Math.max(1, radius - hudPxAtGuiScale2(2)), 0xFF12202B);
        if (service == EmergencyService.POLICE) {
            drawPoliceShield(graphics, centerX, centerY, accent);
        } else if (service == EmergencyService.EMS) {
            drawMedicalBadge(graphics, centerX, centerY, accent);
        } else {
            fillCircle(graphics, centerX, centerY, Math.max(1, radius - hudPxAtGuiScale2(3)), accent);
            drawLocationPin(graphics, centerX, centerY, 0xFFFFFFFF, accent);
        }
    }

    private static void drawPoliceShield(GuiGraphics graphics, int centerX, int centerY, int accent) {
        int unit = hudPxAtGuiScale2(1);
        int top = centerY - unit * 7;
        int height = unit * 14;
        int maximumHalfWidth = unit * 6;
        for (int row = 0; row < height; row++) {
            int taper = row < unit * 5 ? 0 : (row - unit * 5) / 2;
            int half = Math.max(unit * 2, maximumHalfWidth - taper);
            graphics.fill(centerX - half, top + row, centerX + half + 1, top + row + 1, 0xFFF3F8FC);
        }
        int innerTop = top + unit * 2;
        int innerHeight = Math.max(1, height - unit * 5);
        for (int row = 0; row < innerHeight; row++) {
            int taper = row < unit * 3 ? 0 : (row - unit * 3) / 2;
            int half = Math.max(unit, unit * 4 - taper);
            graphics.fill(centerX - half, innerTop + row, centerX + half + 1, innerTop + row + 1, accent);
        }
        fillCircle(graphics, centerX, centerY - unit, Math.max(1, unit * 2), 0xFFF6FAFD);
        graphics.fill(centerX - unit * 3, centerY - unit, centerX + unit * 4, centerY + unit, 0xFF173147);
        graphics.fill(centerX - unit, centerY - unit * 4, centerX + unit, centerY + unit * 3, 0xFF173147);
    }

    private static void drawMedicalBadge(GuiGraphics graphics, int centerX, int centerY, int accent) {
        int unit = hudPxAtGuiScale2(1);
        fillCircle(graphics, centerX, centerY, hudPxAtGuiScale2(7), 0xFFF4F8FA);
        graphics.fill(centerX - unit * 2, centerY - unit * 6,
                centerX + unit * 3, centerY + unit * 7, accent);
        graphics.fill(centerX - unit * 6, centerY - unit * 2,
                centerX + unit * 7, centerY + unit * 3, accent);
        fillCircle(graphics, centerX, centerY, Math.max(1, unit), 0xFFF4F8FA);
    }

    private static void drawCompass(GuiGraphics graphics, int centerX, int centerY, int background) {
        int radius = hudPxAtGuiScale2(8);
        fillCircle(graphics, centerX + hudPx(1), centerY + hudPx(1), radius, 0x7A000000);
        fillCircle(graphics, centerX, centerY, radius, background);
        fillCircle(graphics, centerX, centerY, Math.max(1, radius - hudPx(1)), 0xE60A141D);
        PhoneModernText.drawCenteredColored(graphics, "N", centerX,
                centerY - hudPxAtGuiScale2(5), hudPxAtGuiScale2(5), 0xFFF2F6F8);
        int unit = hudPxAtGuiScale2(1);
        for (int row = 0; row < unit * 5; row++) {
            int half = Math.max(0, (unit * 4 - row) / 2);
            graphics.fill(centerX - half, centerY + row,
                    centerX + half + 1, centerY + row + 1, 0xFFFF676F);
        }
    }

    private static int serviceAccent(EmergencyService service) {
        return service == EmergencyService.POLICE ? 0xFF3EA7F5
                : service == EmergencyService.EMS ? 0xFFEC5360 : 0xFF4AAEFF;
    }

    private static int serviceAccentDark(EmergencyService service) {
        return service == EmergencyService.POLICE ? 0xFF165A88
                : service == EmergencyService.EMS ? 0xFF87303A : 0xFF235A7E;
    }

    private static String targetDirection(PhoneGpsState.Location current, PhoneGpsState.Location target) {
        double angle = Math.toDegrees(Math.atan2((double) target.x() - current.x(),
                -(double) target.z() + current.z()));
        int index = Math.floorMod((int) Math.round(angle / 45.0D), 8);
        String[] turkish = {"K", "KD", "D", "GD", "G", "GB", "B", "KB"};
        String[] english = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return PhoneSettingsStore.text(turkish[index], english[index]);
    }

    private static String facingLabel(float yaw) {
        int index = Math.floorMod((int) Math.floor((yaw + 22.5F) / 45.0F), 8);
        String[] turkish = {"G", "GB", "B", "KB", "K", "KD", "D", "GD"};
        String[] english = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return PhoneSettingsStore.text("YÖN " + turkish[index], "HDG " + english[index]);
    }

    private static String etaLabel(long distance) {
        long seconds = Math.max(1L, Math.round(distance / 5.0D));
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    private static String fitText(String value, int maxWidth, int height) {
        String text = value == null ? "" : value;
        if (PhoneModernText.width(text, height) <= maxWidth) return text;
        String suffix = "...";
        int end = text.length();
        while (end > 0 && PhoneModernText.width(text.substring(0, end) + suffix, height) > maxWidth) end--;
        return end <= 0 ? suffix : text.substring(0, end) + suffix;
    }
}

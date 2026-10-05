package com.mikasrevs.newphone.client.tablet;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.ResponderTabletItem;
import com.mikasrevs.newphone.client.emergency.EmergencyClientData;
import com.mikasrevs.newphone.client.gui.PhoneGpsMapRenderer;
import com.mikasrevs.newphone.client.gui.PhoneGpsState;
import com.mikasrevs.newphone.emergency.EmergencyService;
import com.mikasrevs.newphone.network.EmergencyStateData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Tablet-only navigation HUD. It never opens or mutates the civilian phone GPS. */
public final class TabletNavigationClientEvents {
    /** Entering this area ends tablet navigation and marks the responder on scene. */
    public static final double ARRIVAL_RADIUS_BLOCKS = 32.0D;
    public static final double ARRIVAL_MAX_VERTICAL_BLOCKS = 24.0D;

    private TabletNavigationClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            // Register every responder-tablet HUD element through one unique Forge overlay.
            event.registerAboveAll("responder_tablet_overlay", (gui, graphics, partialTick, width, height) -> {
                renderHud(graphics, width, height);
                EmergencyClientEvents.renderAlert(graphics, width);
            });
        }
    }

    @Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void keyInput(InputEvent.Key event) {
            if (event.getKey() != GLFW.GLFW_KEY_X || event.getAction() != GLFW.GLFW_PRESS) return;
            if (!TabletNavigationState.active()
                    || System.currentTimeMillis() < TabletNavigationState.ignoreToggleUntil()) return;
            TabletNavigationState.restoreTablet(Minecraft.getInstance());
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || !TabletNavigationState.active()) return;

            EmergencyService service = TabletNavigationState.service();
            EmergencyStateData.DispatchEntry entry = EmergencyClientData.find(
                    service, TabletNavigationState.dispatchId());
            if (minecraft.player == null
                    || service == null
                    || entry == null
                    || !EmergencyClientData.isActive(entry)
                    || !entry.acceptedBySelf
                    || !EmergencyClientData.isAuthorized(service)
                    || !ResponderTabletItem.hasTablet(minecraft.player, service)) {
                TabletNavigationState.clear();
                return;
            }

            PhoneGpsState.Location target = TabletNavigationState.target();
            if (target == null) return;

            // Manual/server-side ON_SCENE also ends the navigation HUD immediately.
            if (entry.onSceneBySelf) {
                TabletNavigationState.clear();
                return;
            }

            tryCompleteArrival(minecraft, service, entry, target);
        }
    }

    private static boolean tryCompleteArrival(
            Minecraft minecraft,
            EmergencyService service,
            EmergencyStateData.DispatchEntry entry,
            PhoneGpsState.Location target
    ) {
        if (minecraft == null || minecraft.player == null || service == null || entry == null || target == null) {
            return false;
        }
        String currentDimension = minecraft.player.level().dimension().location().toString();
        if (!currentDimension.equals(target.dimension())) return false;

        double dy = Math.abs(minecraft.player.getY() - target.y());
        if (dy > ARRIVAL_MAX_VERTICAL_BLOCKS) return false;
        double dx = minecraft.player.getX() - target.x();
        double dz = minecraft.player.getZ() - target.z();
        if (dx * dx + dz * dz > ARRIVAL_RADIUS_BLOCKS * ARRIVAL_RADIUS_BLOCKS) return false;

        // Clear first so the HUD disappears on this frame. The server performs the same
        // proximity validation and turns the reserved responder slot into ON_SCENE.
        TabletNavigationState.clear();
        EmergencyClientData.markOnScene(entry.id, service);
        minecraft.player.displayClientMessage(Component.literal(
                TabletSettingsStore.text(
                        "Olay yerine ulaştın • Navigasyon kapandı • ON SCENE",
                        "Arrived on scene • Navigation closed • ON SCENE"
                )), true);
        return true;
    }

    private static void renderHud(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (!TabletNavigationState.active() || !TabletSettingsStore.navigationHud()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;

        EmergencyService service = TabletNavigationState.service();
        PhoneGpsState.Location target = TabletNavigationState.target();
        if (service == null || target == null) return;

        EmergencyStateData.DispatchEntry entry = EmergencyClientData.find(service, TabletNavigationState.dispatchId());
        if (entry == null || !EmergencyClientData.isActive(entry) || !entry.acceptedBySelf) {
            TabletNavigationState.clear();
            return;
        }
        if (entry.onSceneBySelf) {
            TabletNavigationState.clear();
            return;
        }

        // Render-time fallback: even if a client tick/input event is skipped, reaching the scene
        // still closes the minimap immediately and submits ON_SCENE exactly once.
        if (tryCompleteArrival(minecraft, service, entry, target)) return;

        int accent = service == EmergencyService.POLICE ? 0xFF1E91FF : 0xFFE94B59;
        int panelW = Math.min(226, Math.max(176, screenWidth / 4));
        int panelH = 176;
        int left = screenWidth - panelW - 12;
        int top = screenHeight - panelH - 12;
        int right = left + panelW;
        int bottom = top + panelH;

        graphics.fill(left + 2, top + 3, right + 2, bottom + 3, 0x76000000);
        graphics.fill(left, top, right, bottom, 0xF20A121A);
        graphics.fill(left, top, left + 3, bottom, accent);
        graphics.fill(left + 3, top, right, top + 1, 0x45FFFFFF);

        String title = service == EmergencyService.POLICE
                ? TabletSettingsStore.text("POLİS NAVİGASYON", "POLICE NAVIGATION")
                : TabletSettingsStore.text("AMBULANS NAVİGASYON", "EMS NAVIGATION");
        graphics.drawString(minecraft.font, title, left + 12, top + 10, 0xFFF1F6FA, false);
        graphics.drawString(minecraft.font, "#" + TabletNavigationState.dispatchId(), right - 48, top + 10,
                0xFF8196A6, false);

        int mapLeft = left + 10;
        int mapTop = top + 30;
        int mapW = panelW - 20;
        int mapH = 104;
        PhoneGpsMapRenderer.Projection projection = PhoneGpsMapRenderer.drawMinimap(
                graphics, minecraft, mapLeft, mapTop, mapW, mapH, 0,
                target, false, PhoneGpsMapRenderer.MapStyle.CONTRAST);

        PhoneGpsState.Location here = new PhoneGpsState.Location(
                minecraft.player.level().dimension().location().toString(),
                (int) Math.floor(minecraft.player.getX()),
                (int) Math.floor(minecraft.player.getY()),
                (int) Math.floor(minecraft.player.getZ())
        );
        drawRoute(graphics, projection, here, target, accent);

        String distance;
        if (!here.dimension().equals(target.dimension())) {
            distance = TabletSettingsStore.text("Farklı boyut", "Different dimension");
        } else {
            long blocks = Math.round(Math.hypot((double) here.x() - target.x(), (double) here.z() - target.z()));
            distance = blocks + " " + TabletSettingsStore.text("blok", "blocks");
        }
        graphics.drawString(minecraft.font, distance, left + 12, top + 142, accent, false);
        graphics.drawString(minecraft.font,
                TabletSettingsStore.text("X: TABLETİ AÇ", "X: OPEN TABLET"),
                left + 12, top + 158, 0xFF879AA8, false);
    }

    private static void drawRoute(
            GuiGraphics graphics,
            PhoneGpsMapRenderer.Projection projection,
            PhoneGpsState.Location here,
            PhoneGpsState.Location target,
            int color
    ) {
        if (projection == null || here == null || target == null
                || !here.dimension().equals(target.dimension())
                || !here.dimension().equals(projection.center().dimension())) return;
        double x1 = projection.worldToScreenX(here.x());
        double y1 = projection.worldToScreenZ(here.z());
        double x2 = projection.worldToScreenX(target.x());
        double y2 = projection.worldToScreenZ(target.z());
        double distance = Math.hypot(x2 - x1, y2 - y1);
        int steps = Math.max(1, (int) Math.ceil(distance / 6.0D));
        for (int index = 0; index <= steps; index++) {
            double t = index / (double) steps;
            int x = (int) Math.round(x1 + (x2 - x1) * t);
            int y = (int) Math.round(y1 + (y2 - y1) * t);
            if (projection.contains(x, y)) graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }
}

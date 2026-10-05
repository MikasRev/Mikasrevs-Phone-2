package com.mikasrevs.newphone.client.tablet;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.ResponderTabletItem;
import com.mikasrevs.newphone.client.emergency.EmergencyClientData;
import com.mikasrevs.newphone.emergency.EmergencyCategory;
import com.mikasrevs.newphone.emergency.EmergencyService;
import com.mikasrevs.newphone.network.EmergencyStateData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** World-level tablet alerts; no civilian phone screen is required or opened. */
public final class EmergencyClientEvents {
    private EmergencyClientEvents() {
    }

    /**
     * Intentionally kept as a non-subscriber so an older compiled nested class is overwritten
     * on incremental builds. Tablet overlays are registered once by TabletNavigationClientEvents.
     */
    public static final class ModBus {
        private ModBus() {
        }
    }

    @Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.player == null) return;

            if (minecraft.screen instanceof ResponderTabletScreen tablet
                    && (!minecraft.player.isAlive()
                    || !EmergencyClientData.isAuthorized(tablet.service)
                    || !ResponderTabletItem.hasTablet(minecraft.player, tablet.service))) {
                minecraft.setScreen(null);
                return;
            }
            if (minecraft.screen != null || TabletNavigationState.active()
                    || !TabletSettingsStore.autoOpenIncoming()) return;

            EmergencyClientData.IncomingDispatch incoming = EmergencyClientData.peekIncoming();
            if (incoming == null || EmergencyClientData.activeAssignment(incoming.service()) != null
                    || !EmergencyClientData.isAuthorized(incoming.service())
                    || !ResponderTabletItem.hasTablet(minecraft.player, incoming.service())) return;
            EmergencyClientData.pollIncoming();
            ResponderTabletScreen.openIncoming(incoming.service(), incoming.dispatchId());
        }
    }

    static void renderAlert(GuiGraphics graphics, int screenWidth) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!TabletSettingsStore.incomingPopup() || minecraft == null || minecraft.player == null
                || minecraft.screen instanceof ResponderTabletScreen) return;
        EmergencyClientData.IncomingDispatch incoming = EmergencyClientData.peekIncoming();
        if (incoming == null || EmergencyClientData.activeAssignment(incoming.service()) != null) return;
        EmergencyStateData.DispatchEntry entry = EmergencyClientData.find(incoming.service(), incoming.dispatchId());
        if (entry == null) return;

        int width = Math.min(236, Math.max(180, screenWidth - 24));
        int left = screenWidth - width - 12;
        int top = 12;
        int right = screenWidth - 12;
        int accent = incoming.service() == EmergencyService.POLICE ? 0xFF3C8DDB : 0xFFE0525E;
        graphics.fill(left, top, right, top + 83, 0xEE10161D);
        graphics.fill(left, top, left + 4, top + 83, accent);
        graphics.fill(left + 4, top, right, top + 1, 0x44FFFFFF);

        EmergencyCategory category = EmergencyClientData.category(entry);
        String categoryName = category == null ? entry.categoryId
                : Component.translatable(category.translationKey()).getString();
        String heading = Component.translatable(incoming.service() == EmergencyService.POLICE
                ? "emergency.newphone.tablet.new_police_emergency"
                : "emergency.newphone.tablet.new_medical_emergency").getString();
        graphics.drawString(minecraft.font, fit(minecraft, heading, width - 24), left + 13, top + 9, 0xFFEAF0F5, false);
        graphics.drawString(minecraft.font, fit(minecraft, categoryName, width - 24), left + 13, top + 27, 0xFFFFFFFF, false);
        graphics.drawString(minecraft.font, fit(minecraft, distance(entry), width - 24), left + 13, top + 45, 0xFFB9C4CE, false);
        graphics.drawString(minecraft.font,
                fit(minecraft, Component.translatable("emergency.newphone.tablet.open_prompt").getString(), width - 24),
                left + 13, top + 64, accent, false);
    }

    private static String distance(EmergencyStateData.DispatchEntry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return Component.translatable("emergency.newphone.locating").getString();
        String dimension = minecraft.player.level().dimension().location().toString();
        if (!dimension.equals(entry.dimension)) {
            return Component.translatable("emergency.newphone.different_dimension").getString();
        }
        double dx = minecraft.player.getX() - entry.x;
        double dy = minecraft.player.getY() - entry.y;
        double dz = minecraft.player.getZ() - entry.z;
        return Component.translatable("emergency.newphone.blocks_away",
                Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz))).getString();
    }

    private static String fit(Minecraft minecraft, String value, int width) {
        return minecraft.font.width(value) <= width ? value : minecraft.font.plainSubstrByWidth(value, width - 8) + "...";
    }
}

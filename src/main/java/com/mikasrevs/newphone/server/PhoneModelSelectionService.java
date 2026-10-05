package com.mikasrevs.newphone.server;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.ZPhoneItem;
import com.mikasrevs.newphone.network.PhoneNetwork;
import com.mikasrevs.newphone.network.message.PhoneModelMessage;
import com.mikasrevs.newphone.phone.model.PhoneModelRegistry;
import com.mikasrevs.newphone.server.data.PhoneNumberSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Server-thread-only cosmetic selection. Persistence belongs to the existing phone identity save. */
@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID)
public final class PhoneModelSelectionService {
    private PhoneModelSelectionService() {}

    public static void select(ServerPlayer player, String id) {
        if (!PhoneInventoryUtil.hasPhone(player) || PhoneModelRegistry.find(id).isEmpty()) {
            send(player, player);
            return;
        }
        var data = PhoneNumberSavedData.get(player.server);
        boolean changed = data.setPhoneModel(player.getUUID(), id);
        // Mirror onto existing stacks immediately, including off-hand. Other NBT is untouched.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(NewPhone.Z_PHONE.get())) PhoneModelRegistry.applyToStack(stack, id);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        if (changed) broadcast(player);
        else send(player, player);
    }

    private static PhoneModelMessage message(ServerPlayer player) {
        return new PhoneModelMessage(player.getUUID(), PhoneNumberSavedData.get(player.server).getPhoneModel(player.getUUID()));
    }
    private static void send(ServerPlayer recipient, ServerPlayer subject) {
        PhoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> recipient), message(subject));
    }
    private static void broadcast(ServerPlayer subject) {
        PhoneNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), message(subject));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (ServerPlayer other : player.server.getPlayerList().getPlayers()) send(player, other);
            broadcast(player);
        }
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer subject)
            send(viewer, subject);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) broadcast(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) broadcast(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PhoneNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new PhoneModelMessage(player.getUUID(), ""));
    }
}

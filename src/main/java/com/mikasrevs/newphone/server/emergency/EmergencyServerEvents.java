package com.mikasrevs.newphone.server.emergency;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.emergency.EmergencyService;
import com.mikasrevs.newphone.server.data.EmergencyRoleSavedData;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Server lifecycle, cleanup, and administrator role assignment for dispatch tablets. */
@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmergencyServerEvents {
    private EmergencyServerEvents() {
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        var roleArgument = Commands.argument("role", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        List.of("police", "ems", "both", "none"), builder))
                .executes(context -> setRole(
                        EntityArgument.getPlayers(context, "targets"),
                        StringArgumentType.getString(context, "role")
                ));
        var targetsArgument = Commands.argument("targets", EntityArgument.players()).then(roleArgument);
        event.getDispatcher().register(Commands.literal("mphone")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("role")
                        .then(Commands.literal("set").then(targetsArgument)))
                .then(Commands.literal("demo")
                        .then(Commands.literal("police")
                                .executes(context -> createPoliceDemo(
                                        context.getSource().getPlayerOrException())))
                        .then(Commands.literal("ems")
                                .executes(context -> createEmsDemo(
                                        context.getSource().getPlayerOrException())))));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EmergencyDispatchManager.get(player.server).handlePlayerLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EmergencyDispatchManager.get(player.server).handlePlayerLogout(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EmergencyDispatchManager.get(player.server).handlePlayerDeath(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0) {
            EmergencyDispatchManager.get(event.getServer()).tick();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EmergencyDispatchManager.discard(event.getServer());
    }

    private static int setRole(Collection<ServerPlayer> players, String rawRole) {
        String role = rawRole == null ? "" : rawRole.trim().toLowerCase(java.util.Locale.ROOT);
        EnumSet<EmergencyService> services = switch (role) {
            case "police" -> EnumSet.of(EmergencyService.POLICE);
            case "ems" -> EnumSet.of(EmergencyService.EMS);
            case "both" -> EnumSet.allOf(EmergencyService.class);
            case "none" -> EnumSet.noneOf(EmergencyService.class);
            default -> null;
        };
        if (services == null || players.isEmpty()) return 0;

        for (ServerPlayer player : players) {
            EmergencyRoleSavedData.get(player.server).setServices(player.getUUID(), services);
            if (services.contains(EmergencyService.POLICE)) issueIfMissing(player, NewPhone.POLICE_TABLET.get());
            if (services.contains(EmergencyService.EMS)) issueIfMissing(player, NewPhone.EMS_TABLET.get());
            player.sendSystemMessage(Component.translatable("command.newphone.role.updated", role));
        }
        ServerPlayer first = players.iterator().next();
        EmergencyDispatchManager.get(first.server).revalidateRespondersAndSync();
        return players.size();
    }

    private static int createPoliceDemo(ServerPlayer player) {
        int count = EmergencyDispatchManager.get(player.server).createPoliceDemo(player);
        player.sendSystemMessage(Component.translatable("command.newphone.demo.police", count));
        return count;
    }

    private static int createEmsDemo(ServerPlayer player) {
        int count = EmergencyDispatchManager.get(player.server).createEmsDemo(player);
        player.sendSystemMessage(Component.translatable("command.newphone.demo.ems", count));
        return count;
    }

    private static void issueIfMissing(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) return;
        }
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}

package com.mikasrevs.newphone.server;

import com.mikasrevs.newphone.NewPhone;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps live phone-call sessions from leaking when a player dies or disconnects. */
@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PhoneServerEvents {
    private PhoneServerEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.mikasrevs.newphone.integration.currency.PhoneCurrencyManager.recover(player.server);
            com.mikasrevs.newphone.marketplace.trade.TradeManager.login(player);
            com.mikasrevs.newphone.marketplace.MarketplaceManager.login(player);
            com.mikasrevs.newphone.moderation.PhoneModerationManager.login(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.mikasrevs.newphone.marketplace.trade.TradeManager.unavailable(player);
            com.mikasrevs.newphone.moderation.PhoneModerationManager.logout(player);
            PhoneServerService.handlePlayerUnavailable(player);
            com.mikasrevs.newphone.marketplace.MarketplaceManager.logout(player);
            com.mikasrevs.newphone.network.PhoneRequestLimits.logout(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.mikasrevs.newphone.marketplace.trade.TradeManager.unavailable(player);
            PhoneServerService.handlePlayerUnavailable(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer player)
            PhoneServerService.maintainCallPose(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if(event.phase==TickEvent.Phase.END&&event.getServer().getTickCount()%20==0)
            com.mikasrevs.newphone.marketplace.trade.TradeManager.tick(event.getServer());
        if(event.phase==TickEvent.Phase.END&&event.getServer().getTickCount()%40==0)
            com.mikasrevs.newphone.moderation.PhoneModerationManager.tick(event.getServer());
        if(event.phase==TickEvent.Phase.END)PhoneVoiceStorage.tick(event.getServer());
        if(event.phase==TickEvent.Phase.END&&event.getServer().getTickCount()%1200==0)PhonePhotoCleanup.clean(event.getServer());
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 10 == 0) {
            PhoneServerService.tickCalls(event.getServer());
        }
    }
    @SubscribeEvent public static void onClone(PlayerEvent.Clone event){
        String key="newphone_trade_operation";
        if(event.getOriginal().getPersistentData().contains(key))event.getEntity().getPersistentData().putString(key,event.getOriginal().getPersistentData().getString(key));
        String bank="newphone_bank_operation";
        if(event.getOriginal().getPersistentData().contains(bank))event.getEntity().getPersistentData().putString(bank,event.getOriginal().getPersistentData().getString(bank));
    }
    @SubscribeEvent public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event){com.mikasrevs.newphone.integration.currency.PhoneCurrencyManager.recover(event.getServer());com.mikasrevs.newphone.marketplace.trade.TradeManager.start(event.getServer());PhoneVoiceStorage.start(event.getServer());PhonePhotoCleanup.clean(event.getServer());}
    @SubscribeEvent public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event){com.mikasrevs.newphone.integration.currency.PhoneCurrencyManager.recover(event.getServer());com.mikasrevs.newphone.integration.currency.PhoneBankJournal.stop(event.getServer());com.mikasrevs.newphone.marketplace.trade.TradeManager.stop(event.getServer());com.mikasrevs.newphone.moderation.PhoneModerationManager.stop(event.getServer());PhoneVoiceStorage.stop(event.getServer());com.mikasrevs.newphone.network.PhoneRequestLimits.stop(event.getServer());}
}

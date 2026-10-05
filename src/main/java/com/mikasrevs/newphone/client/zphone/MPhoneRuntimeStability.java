package com.mikasrevs.newphone.client.zphone;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore;
import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneBankClientData;
import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneBlockPostScreen;
import com.mikasrevs.newphone.client.gui.PhoneGpsMapRenderer;
import com.mikasrevs.newphone.client.gui.PhoneGpsState;
import com.mikasrevs.newphone.client.gui.PhoneMusicPlayerState;
import com.mikasrevs.newphone.client.network.PhoneClientNetwork;
import com.mikasrevs.newphone.client.sound.PhoneSoundManager;
import com.mikasrevs.newphone.client.emergency.EmergencyClientData;
import com.mikasrevs.newphone.ResponderTabletItem;
import com.mikasrevs.newphone.emergency.EmergencyService;
import com.mikasrevs.newphone.client.video.PhoneVideoStreamClient;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneBlockGramScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Runtime safety net for disconnects, dimension swaps, death and phone removal.
 * Persistent contacts/messages/posts/settings are never deleted here.
 */
@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, value = Dist.CLIENT)
public final class MPhoneRuntimeStability {
    private static boolean connected;
    private static boolean hadCall;
    private static boolean endRequested;
    private static ResourceKey<Level> lastDimension;

    private MPhoneRuntimeStability() {}

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        ZPhoneClient.recordPhoneActivity(event.getScreen());
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        ZPhoneClient.recordPhoneActivity(event.getScreen());
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        ZPhoneClient.recordPhoneActivity(event.getScreen());
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        ZPhoneClient.recordPhoneActivity(event.getScreen());
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        ZPhoneClient.recordPhoneActivity(event.getScreen());
    }

    @SubscribeEvent
    public static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        ZPhoneClient.recordPhoneActivity(event.getScreen());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PhoneSoundManager.tick();
        com.mikasrevs.newphone.client.voice.PhoneVoiceNotes.tick();
        com.mikasrevs.newphone.client.data.PhoneMessageClient.tick();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            if (connected) resetDisconnectedState();
            return;
        }

        if (!connected) {
            connected = true;
            PhoneClientData.requestFullSync();
            PhoneSocialClientData.requestFullSync();
            EmergencyClientData.requestSync();
        }

        ResourceKey<Level> dimension = minecraft.level.dimension();
        if (lastDimension != null && !lastDimension.equals(dimension)) {
            PhoneVideoStreamClient.clearCallTextures();
            PhonePhotoStore.resetSession();
            PhoneGpsMapRenderer.clearWorldCache();
            PhoneClientData.requestFullSync();
            PhoneSocialClientData.requestFullSync();
            EmergencyClientData.requestSync();
        }
        lastDimension = dimension;

        boolean hasPhone = PhoneInventoryUtil.hasPhone(minecraft.player);
        boolean playerUsable = minecraft.player.isAlive() && hasPhone;
        boolean hasAuthorizedTablet = (EmergencyClientData.isAuthorized(EmergencyService.POLICE)
                && ResponderTabletItem.hasTablet(minecraft.player, EmergencyService.POLICE))
                || (EmergencyClientData.isAuthorized(EmergencyService.EMS)
                && ResponderTabletItem.hasTablet(minecraft.player, EmergencyService.EMS));
        boolean navigationUsable = minecraft.player.isAlive() && (hasPhone || hasAuthorizedTablet);
        if (!hasPhone) PhoneMusicPlayerState.stop();
        if (!playerUsable) {
            ZPhoneClient.resetLockSession();
        }
        if (!navigationUsable) PhoneGpsState.disableMinimap();
        if (playerUsable) {
            ZPhoneClient.tickAutoLock(minecraft);
            com.mikasrevs.newphone.client.calendar.PhoneCalendarStore.tick(minecraft);
        }
        if (minecraft.screen instanceof AbstractZPhoneScreen && (!playerUsable||!com.mikasrevs.newphone.client.device.PhoneDeviceSession.hasSelectedDevice())) {
            resetTransientPhoneState();
            minecraft.setScreen(null);
        }

        boolean hasCall = PhoneClientData.hasCall();
        boolean invalidCall = hasCall && !playerUsable;
        if (invalidCall && !endRequested) {
            endRequested = true;
            PhoneClientData.endCall();
        } else if (!invalidCall) {
            endRequested = false;
        }

        if (hadCall && !hasCall) PhoneVideoStreamClient.clearCallTextures();
        hadCall = hasCall;
    }

    private static void resetDisconnectedState() {
        com.mikasrevs.newphone.client.device.PhoneDeviceSession.reset();
        com.mikasrevs.newphone.client.calendar.PhoneCalendarStore.resetSession();
        connected = false;
        hadCall = false;
        endRequested = false;
        lastDimension = null;
        PhoneVideoStreamClient.clearCallTextures();
        PhoneSoundManager.reset();
        PhoneMusicPlayerState.stop();
        PhoneSocialClientData.resetClientState();
        com.mikasrevs.newphone.client.voice.PhoneVoiceNotes.reset();
        com.mikasrevs.newphone.client.gui.PhoneGalleryStore.resetSession();
        PhonePhotoStore.resetSession();
        PhoneBankClientData.clear();
        PhoneClientNetwork.clearPendingStateTransfers();
        PhoneClientData.clear();
        EmergencyClientData.clear();
        PhoneGpsState.disableMinimap();
        PhoneGpsState.clearTargetLocation();
        PhoneGpsMapRenderer.clearWorldCache();
        ZPhoneClient.resetLockSession();
        resetTransientPhoneState();
    }

    private static void resetTransientPhoneState() {
        PhoneNavigationState.clear();
        ZPhoneTransitionState.clear();
        PhoneBlockPostScreen.resetTransientState();
        ZPhoneBlockGramScreen.resetTransientState();
        PhoneBlockGramStore.resetTransientState();
        PhoneBlockPostStore.resetTransientState();
    }
}

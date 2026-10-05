package com.mikasrevs.newphone.client.zphone;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.ZPhoneItem;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps the held MPhone screen/model stable and drives the audio-call phone-to-ear pose. */
@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, value = Dist.CLIENT)
public final class ZPhoneHeldModelState {
    private static boolean screenOn;
    private static float screenAnimation;
    private static boolean callPoseRequested;
    private static InteractionHand callPoseHand;
    private static String callPoseId="";
    private static long callPoseSentAt;
    private static boolean useKeyWasDown;
    private static boolean xKeyWasDown;

    private ZPhoneHeldModelState() {}

    public static void lightUpHeldPhone() { screenOn = true; }
    public static void turnOffHeldPhone() { screenOn = false; }
    public static boolean isScreenOn() { return screenOn; }

    /**
     * Reversible 0..1 screen power animation used by the item model.
     * It deliberately stays client-side and only animates the local held phone.
     */
    public static float screenAnimationFor(LivingEntity entity, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || entity != mc.player || stack == null) return 0.0F;
        if (stack != mc.player.getMainHandItem() && stack != mc.player.getOffhandItem()) return 0.0F;
        return screenAnimation;
    }

    /** Legacy item-predicate helper: only the local player's actually-held phone gets the lit model. */
    public static boolean isScreenOnFor(LivingEntity entity, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (!screenOn || mc == null || mc.player == null || entity != mc.player || stack == null) return false;
        return stack == mc.player.getMainHandItem() || stack == mc.player.getOffhandItem();
    }

    public static boolean isLandscapeActive() {
        return PhoneYouTubeState.isModelMode();
    }

    /** Mesh selection follows the smoothed visual state, never an intermittent use flag. */
    public static boolean isCallPoseFor(LivingEntity entity, ItemStack stack) {
        if (entity == null || stack == null || !(stack.getItem() instanceof ZPhoneItem)) return false;
        if(entity.getMainHandItem()==stack)return PhoneCallAnimation.progress(entity,InteractionHand.MAIN_HAND)>.001F;
        if(entity.getOffhandItem()==stack)return PhoneCallAnimation.progress(entity,InteractionHand.OFF_HAND)>.001F;
        return false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            PhoneYouTubeState.stop();
            ZPhoneClient.resetLockSession();
            screenOn = false;
            screenAnimation = 0.0F;
            callPoseRequested = false;
            callPoseHand = null;
            callPoseId="";
            callPoseSentAt=0;
            PhoneCallAnimation.reset();
            useKeyWasDown = false;
            xKeyWasDown = false;
            return;
        }

        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        boolean heldPhone = main.getItem() instanceof ZPhoneItem || off.getItem() instanceof ZPhoneItem;
        // Inventory and other temporary screens must not end the browser session.
        // Stop only when the viewing phone is no longer held or the player dies.
        if (PhoneYouTubeState.isModelMode()
                && (!heldPhone || !mc.player.isAlive())) {
            PhoneYouTubeState.stop();
        }
        boolean phoneOpen = mc.screen instanceof AbstractZPhoneScreen
                || PhoneYouTubeState.isModelMode();
        screenOn = heldPhone && (phoneOpen || PhoneClientData.hasCall());
        updateScreenAnimation(screenOn);

        // While vanilla keeps the phone in its use pose, pressing use again should reopen MPhone.
        boolean useDown = mc.options.keyUse.isDown();
        if (mc.screen == null && PhoneClientData.hasCall() && heldPhone && useDown && !useKeyWasDown) {
            if (mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof ZPhoneItem) mc.player.stopUsingItem();
            ZPhoneClient.openPhone();
        }
        useKeyWasDown = useDown;

        // World-mini calls can always be reopened with X, matching the on-screen hint.
        boolean xDown = mc.getWindow() != null
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_X);
        if (mc.screen == null && PhoneClientData.hasCall() && PhoneInventoryUtil.hasPhone(mc.player) && xDown && !xKeyWasDown) {
            ZPhoneClient.openPhone();
        }
        xKeyWasDown = xDown;

        updateCallPose(mc, main, off);
        PhoneCallAnimation.progress(mc.player,InteractionHand.MAIN_HAND);
        PhoneCallAnimation.progress(mc.player,InteractionHand.OFF_HAND);
    }

    private static void updateScreenAnimation(boolean targetOn) {
        float target = targetOn ? 1.0F : 0.0F;
        // Roughly 0.25-0.35 seconds in either direction at 20 TPS.
        screenAnimation += (target - screenAnimation) * 0.28F;
        if (Math.abs(target - screenAnimation) < 0.015F) screenAnimation = target;
        if (screenAnimation < 0.0F) screenAnimation = 0.0F;
        if (screenAnimation > 1.0F) screenAnimation = 1.0F;
    }

    private static void updateCallPose(Minecraft mc, ItemStack main, ItemStack off) {
        boolean audioActive = com.mikasrevs.newphone.phone.animation.PhoneCallMotion.shouldRaise(PhoneClientData.callStatus(),PhoneClientData.isVideoCall());
        InteractionHand phoneHand = null;
        if (main.getItem() instanceof ZPhoneItem) phoneHand = InteractionHand.MAIN_HAND;
        else if (off.getItem() instanceof ZPhoneItem) phoneHand = InteractionHand.OFF_HAND;
        boolean shouldPose = audioActive && phoneHand != null && mc.player.isAlive();

        if (shouldPose) {
            boolean restarted = false;
            if (!mc.player.isUsingItem() || mc.player.getUsedItemHand() != phoneHand) {
                if (mc.player.isUsingItem()) mc.player.stopUsingItem();
                mc.player.startUsingItem(phoneHand);
                restarted = true;
            }
            long now=System.nanoTime();
            String id=PhoneClientData.callId();
            // Vanilla releases item use when the use key is up. Restore locally,
            // but send at most one heartbeat per second for the same call/hand.
            if (!callPoseRequested || callPoseHand != phoneHand || !id.equals(callPoseId)
                    || (restarted && now-callPoseSentAt>=1_000_000_000L)) {
                callPoseRequested = true;
                callPoseHand = phoneHand;
                callPoseId=id;
                callPoseSentAt=now;
                PhoneClientData.updateCallPose(true);
            }
            return;
        }

        if (mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof ZPhoneItem) mc.player.stopUsingItem();
        if (callPoseRequested) {
            callPoseRequested = false;
            callPoseHand = null;
            callPoseId="";
            PhoneClientData.updateCallPose(false);
        }
    }
}

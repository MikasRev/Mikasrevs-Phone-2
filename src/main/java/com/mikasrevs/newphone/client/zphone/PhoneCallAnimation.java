package com.mikasrevs.newphone.client.zphone;

import com.mikasrevs.newphone.ZPhoneItem;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.phone.animation.PhoneCallMotion;
import java.util.WeakHashMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/** Per-entity/per-hand visual state; vanilla item-use remains the multiplayer signal. */
public final class PhoneCallAnimation {
    private static final WeakHashMap<LivingEntity, PhoneCallMotion[]> MOTION = new WeakHashMap<>();
    private PhoneCallAnimation() {}

    public static float progress(LivingEntity entity, InteractionHand hand) {
        if (entity == null || !entity.isAlive()) return 0;
        var stack = entity.getItemInHand(hand);
        if (!(stack.getItem() instanceof ZPhoneItem)) {
            var motions = MOTION.get(entity);
            if (motions != null) motions[hand.ordinal()] = new PhoneCallMotion();
            return 0;
        }
        boolean raised = entity.isUsingItem() && entity.getUsedItemHand() == hand;
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(entity==mc.player){
            boolean mainPhone=entity.getMainHandItem().getItem() instanceof ZPhoneItem;
            raised=PhoneCallMotion.shouldRaise(com.mikasrevs.newphone.client.data.PhoneClientData.callStatus(),com.mikasrevs.newphone.client.data.PhoneClientData.isVideoCall())
                    && (hand==InteractionHand.MAIN_HAND||!mainPhone);
        }
        var motions = MOTION.computeIfAbsent(entity, ignored -> new PhoneCallMotion[]{new PhoneCallMotion(), new PhoneCallMotion()});
        var motion=motions[hand.ordinal()];long now=System.nanoTime();
        return entity==mc.player?motion.update(raised,now,PhoneSettingsStore.animationsEnabled())
                :motion.updateRemote(raised,now,PhoneSettingsStore.animationsEnabled());
    }

    public static void reset() { MOTION.clear(); }
}

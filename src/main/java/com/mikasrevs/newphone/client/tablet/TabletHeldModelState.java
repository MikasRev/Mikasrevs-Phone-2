package com.mikasrevs.newphone.client.tablet;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.ResponderTabletItem;
import com.mikasrevs.newphone.emergency.EmergencyService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the local responder-tablet screen power animation.
 *
 * <p>The tablet intentionally mirrors the MPhone item-model approach: vanilla item predicates select
 * reversible boot-frame models, so no GeckoLib/custom renderer is required.</p>
 */
@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, value = Dist.CLIENT)
public final class TabletHeldModelState {
    private static float screenAnimation;
    private static EmergencyService animatedService;

    private TabletHeldModelState() {
    }

    /** 0..1 value used by the tablet_screen_anim item predicate. */
    public static float screenAnimationFor(LivingEntity entity, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || entity != minecraft.player || stack == null) return 0.0F;
        if (!(stack.getItem() instanceof ResponderTabletItem tablet)) return 0.0F;
        if (stack != minecraft.player.getMainHandItem() && stack != minecraft.player.getOffhandItem()) return 0.0F;
        if (animatedService == null || tablet.service() != animatedService) return 0.0F;
        return screenAnimation;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            screenAnimation = 0.0F;
            animatedService = null;
            return;
        }

        EmergencyService openService = null;
        if (minecraft.screen instanceof ResponderTabletScreen tabletScreen) {
            openService = tabletScreen.service;
        }

        boolean shouldBeOn = false;
        if (openService != null) {
            ItemStack main = minecraft.player.getMainHandItem();
            ItemStack off = minecraft.player.getOffhandItem();
            shouldBeOn = matches(main, openService) || matches(off, openService);
            if (shouldBeOn) animatedService = openService;
        }

        updateScreenAnimation(shouldBeOn);
        if (!shouldBeOn && screenAnimation <= 0.0F) animatedService = null;
    }

    private static boolean matches(ItemStack stack, EmergencyService service) {
        return stack != null
                && stack.getItem() instanceof ResponderTabletItem tablet
                && tablet.service() == service;
    }

    private static void updateScreenAnimation(boolean targetOn) {
        float target = targetOn ? 1.0F : 0.0F;
        // About the same power-on/off feel as MPhone: smooth, reversible, roughly 0.3 seconds.
        screenAnimation += (target - screenAnimation) * 0.28F;
        if (Math.abs(target - screenAnimation) < 0.015F) screenAnimation = target;
        if (screenAnimation < 0.0F) screenAnimation = 0.0F;
        if (screenAnimation > 1.0F) screenAnimation = 1.0F;
    }
}

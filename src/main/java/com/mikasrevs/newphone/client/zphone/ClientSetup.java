package com.mikasrevs.newphone.client.zphone;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.PhoneCaseStyle;
import com.mikasrevs.newphone.client.tablet.TabletHeldModelState;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registers legacy 1.20.1 item model predicates used by the lit phone model. */
@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                NewPhone.Z_PHONE.get(),
                new ResourceLocation(NewPhone.MOD_ID, "screen_on"),
                (stack, level, entity, seed) -> ZPhoneHeldModelState.isScreenOnFor(entity, stack) ? 1.0F : 0.0F
        ));
        event.enqueueWork(() -> ItemProperties.register(
                NewPhone.Z_PHONE.get(),
                new ResourceLocation(NewPhone.MOD_ID, "screen_anim"),
                (stack, level, entity, seed) -> ZPhoneHeldModelState.screenAnimationFor(entity, stack)
        ));
        event.enqueueWork(() -> ItemProperties.register(
                NewPhone.Z_PHONE.get(),
                new ResourceLocation(NewPhone.MOD_ID, "call_pose"),
                (stack, level, entity, seed) -> ZPhoneHeldModelState.isCallPoseFor(entity, stack) ? 1.0F : 0.0F
        ));
        event.enqueueWork(() -> ItemProperties.register(
                NewPhone.Z_PHONE.get(),
                new ResourceLocation(NewPhone.MOD_ID, "case_style"),
                (stack, level, entity, seed) -> PhoneCaseStyle.fromStack(stack).modelValue()
        ));
        event.enqueueWork(() -> ItemProperties.register(
                NewPhone.POLICE_TABLET.get(),
                new ResourceLocation(NewPhone.MOD_ID, "tablet_screen_anim"),
                (stack, level, entity, seed) -> TabletHeldModelState.screenAnimationFor(entity, stack)
        ));
        event.enqueueWork(() -> ItemProperties.register(
                NewPhone.EMS_TABLET.get(),
                new ResourceLocation(NewPhone.MOD_ID, "tablet_screen_anim"),
                (stack, level, entity, seed) -> TabletHeldModelState.screenAnimationFor(entity, stack)
        ));
    }
}

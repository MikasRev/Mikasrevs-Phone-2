package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneUiScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Renders MPhone itself at GUI Scale 1 internally, then restores the player's
 * original Minecraft GUI scale immediately after leaving the phone UI.
 */
@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, value = Dist.CLIENT)
public final class PhoneGuiScaleState {
    private static boolean forcingScaleOne;
    private static int previousGuiScale = 1;
    private static long holdPhoneScaleUntil;

    private PhoneGuiScaleState() { }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) return;

        boolean phoneOpen = minecraft.screen instanceof ZPhoneUiScreen
                || System.currentTimeMillis() < holdPhoneScaleUntil;
        if (phoneOpen) {
            if (!forcingScaleOne) {
                previousGuiScale = minecraft.options.guiScale().get();
                forcingScaleOne = true;
            }
            if (minecraft.options.guiScale().get() != 1) {
                minecraft.options.guiScale().set(1);
                minecraft.resizeDisplay();
            }
            return;
        }

        if (forcingScaleOne) {
            int restore = previousGuiScale;
            forcingScaleOne = false;
            previousGuiScale = 1;
            if (minecraft.options.guiScale().get() != restore) {
                minecraft.options.guiScale().set(restore);
                minecraft.resizeDisplay();
            }
        }
    }

    public static void holdPhoneScaleFor(long milliseconds) {
        holdPhoneScaleUntil = Math.max(holdPhoneScaleUntil,
                System.currentTimeMillis() + Math.max(0L, milliseconds));
    }
}

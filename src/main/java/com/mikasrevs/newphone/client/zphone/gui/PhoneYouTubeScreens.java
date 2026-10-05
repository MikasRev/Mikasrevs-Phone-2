package com.mikasrevs.newphone.client.zphone.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;

/** Loads the MCEF-backed screen only when the optional client mod is installed. */
public final class PhoneYouTubeScreens {
    private static final String SCREEN_CLASS =
            "com.mikasrevs.newphone.client.zphone.gui.ZPhoneYouTubeScreen";

    private PhoneYouTubeScreens() {
    }

    public static Screen create() {
        if (!ModList.get().isLoaded("mcef")) return new ZPhoneYouTubeUnavailableScreen();
        try {
            Class<?> type = Class.forName(SCREEN_CLASS, true, PhoneYouTubeScreens.class.getClassLoader());
            return (Screen) type.getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError exception) {
            return new ZPhoneYouTubeUnavailableScreen();
        }
    }
}

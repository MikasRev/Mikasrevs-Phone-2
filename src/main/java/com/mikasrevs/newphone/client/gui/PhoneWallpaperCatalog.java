package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.NewPhone;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Built-in MPhone wallpapers, all prepared at the phone display aspect ratio. */
public final class PhoneWallpaperCatalog {
    public static final int TEXTURE_WIDTH = 450;
    public static final int TEXTURE_HEIGHT = 915;

    private static final List<ResourceLocation> WALLPAPERS = List.of(
            texture("alpine_sunset"),
            texture("neon_city"),
            texture("tropical_sunset"),
            texture("crystal_glass"),
            texture("cosmic_nebula"),
            texture("moonlit_winter"),
            texture("electric_blue"),
            texture("black_gold"),
            texture("enchanted_forest")
    );

    private PhoneWallpaperCatalog() { }

    public static ResourceLocation texture(int index) {
        return WALLPAPERS.get(Math.floorMod(index, WALLPAPERS.size()));
    }

    public static int count() {
        return WALLPAPERS.size();
    }

    private static ResourceLocation texture(String id) {
        return new ResourceLocation(
                NewPhone.MOD_ID,
                "textures/gui/phone/wallpapers/" + id + ".png"
        );
    }
}

package com.mikasrevs.newphone.phone.model;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.PhoneCaseStyle;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Matching case frames for full phone screens and the incoming-call HUD. */
public final class PhoneCaseTextures {
    private static final Map<String, ResourceLocation> FRAMES = createFrames();
    private PhoneCaseTextures() { }

    public static ResourceLocation frame(String model, PhoneCaseStyle style) {
        if (style == null || style == PhoneCaseStyle.NONE) return null;
        return FRAMES.get(PhoneModelRegistry.get(model).id() + "/" + style.id());
    }

    private static Map<String, ResourceLocation> createFrames() {
        var frames = new HashMap<String, ResourceLocation>();
        for (var model : PhoneModelRegistry.all()) for (var style : PhoneCaseStyle.values()) {
            if (style == PhoneCaseStyle.NONE) continue;
            String key = model.id() + "/" + style.id();
            frames.put(key, new ResourceLocation(NewPhone.MOD_ID, "textures/gui/phone/cases/" + key + ".png"));
        }
        return Map.copyOf(frames);
    }
}

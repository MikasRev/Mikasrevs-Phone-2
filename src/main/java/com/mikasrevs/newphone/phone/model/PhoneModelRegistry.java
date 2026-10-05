package com.mikasrevs.newphone.phone.model;

import com.mikasrevs.newphone.NewPhone;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** One phone item, an extensible collection of visual shells, and stable save identifiers. */
public final class PhoneModelRegistry {
    public static final String STACK_TAG = "NewPhoneModel";
    private static final Map<String, PhoneModelDefinition> MODELS = new LinkedHashMap<>();
    public static final PhoneModelDefinition.ScreenSurface STANDARD_SCREEN =
            new PhoneModelDefinition.ScreenSurface(3.0376F, 9.3992F, 6.9624F, 17.504F, .681F);
    public static final PhoneModelDefinition CLASSIC = register(new PhoneModelDefinition(
            "classic", "Classic", "Graphite", resource("item/z_phone"),
            resource("item/mikasrevs_phone_2_atlas"), resource("textures/phone/classic/preview.png"),
            resource("textures/gui/phone/zphone_classic_frame.png"), STANDARD_SCREEN));
    static {
        shell("model_01", "Model 01", "Navy / Gold");
        shell("model_02", "Model 02", "Mint");
        shell("model_03", "Model 03", "Pearl / Silver");
        shell("model_04", "Model 04", "Cream / Orange");
        shell("model_05", "Model 05", "Forest / Olive");
    }
    private PhoneModelRegistry() {}

    private static void shell(String id, String name, String finish) {
        register(new PhoneModelDefinition(id, name, finish, resource("phone/" + id + "/phone"),
                resource("phone/" + id + "/atlas"), resource("textures/phone/" + id + "/preview.png"),
                resource("textures/phone/" + id + "/frame.png"), STANDARD_SCREEN));
    }

    private static PhoneModelDefinition register(PhoneModelDefinition definition) {
        if (!definition.id().matches("[a-z0-9_]{1,32}") || MODELS.putIfAbsent(definition.id(), definition) != null)
            throw new IllegalArgumentException("Invalid or duplicate phone model: " + definition.id());
        return definition;
    }

    public static List<PhoneModelDefinition> all() { return List.copyOf(MODELS.values()); }
    public static Optional<PhoneModelDefinition> find(String id) { return Optional.ofNullable(MODELS.get(id)); }
    public static PhoneModelDefinition get(String id) { return find(id).orElse(CLASSIC); }
    public static PhoneModelDefinition fromStack(ItemStack stack) {
        return stack != null && stack.hasTag() ? get(stack.getTag().getString(STACK_TAG)) : CLASSIC;
    }
    public static void applyToStack(ItemStack stack, String id) {
        String selected = get(id).id();
        if (selected.equals(CLASSIC.id())) stack.removeTagKey(STACK_TAG);
        else if (!stack.hasTag() || !selected.equals(stack.getTag().getString(STACK_TAG)))
            stack.getOrCreateTag().putString(STACK_TAG, selected);
    }
    private static ResourceLocation resource(String path) { return new ResourceLocation(NewPhone.MOD_ID, path); }
}

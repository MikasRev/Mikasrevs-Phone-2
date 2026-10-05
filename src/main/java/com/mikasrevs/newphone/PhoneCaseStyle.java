package com.mikasrevs.newphone;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Stable identifiers shared by the phone item, server validation and client renderers. */
public enum PhoneCaseStyle {
    NONE("none"),
    BUNNY_BLUSH("bunny_blush"),
    CARBON_GUARD("carbon_guard"),
    FOREST_LEATHER("forest_leather"),
    MIDNIGHT_CYBER("midnight_cyber"),
    BUNNY_BLOCK("bunny_block");

    public static final String TAG_KEY = "NewPhoneCase";

    private final String id;

    PhoneCaseStyle(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "phone_case.newphone." + id;
    }

    public float modelValue() {
        return ordinal();
    }

    public static Optional<PhoneCaseStyle> parse(String rawId) {
        if (rawId == null || rawId.isBlank()) return Optional.empty();
        String normalized = rawId.trim().toLowerCase(Locale.ROOT);
        for (PhoneCaseStyle style : values()) {
            if (style.id.equals(normalized)) return Optional.of(style);
        }
        return Optional.empty();
    }

    public static PhoneCaseStyle fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()
                || !stack.getTag().contains(TAG_KEY, Tag.TAG_STRING)) {
            return NONE;
        }
        return parse(stack.getTag().getString(TAG_KEY)).orElse(NONE);
    }

    public static void apply(ItemStack stack, PhoneCaseStyle style) {
        if (stack == null || stack.isEmpty()) return;
        PhoneCaseStyle safeStyle = style == null ? NONE : style;
        if (safeStyle == NONE) {
            stack.removeTagKey(TAG_KEY);
        } else {
            stack.getOrCreateTag().putString(TAG_KEY, safeStyle.id);
        }
    }
}

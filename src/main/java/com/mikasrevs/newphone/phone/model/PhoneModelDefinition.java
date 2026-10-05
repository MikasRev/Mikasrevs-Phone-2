package com.mikasrevs.newphone.phone.model;

import net.minecraft.resources.ResourceLocation;

/** Cosmetic resources only. Held/call transforms live in the model JSON, never in app code. */
public record PhoneModelDefinition(String id, String name, String finish,
        ResourceLocation model, ResourceLocation texture, ResourceLocation preview,
        ResourceLocation frame, ScreenSurface screen) {
    /** Minecraft model pixels. UV (0,0) is bottom-left when looking at the display. */
    public record ScreenSurface(float left, float bottom, float right, float top, float depth) {}
}

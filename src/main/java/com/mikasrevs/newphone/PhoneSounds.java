package com.mikasrevs.newphone;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Registered MPhone sound events. */
public final class PhoneSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, NewPhone.MOD_ID);

    public static final Supplier<SoundEvent> CAMERA = register("kamerasesi");
    public static final Supplier<SoundEvent> MESSAGE_RECEIVE = register("message_receive");
    public static final Supplier<SoundEvent> MESSAGE_SEND = register("message_send");
    public static final Supplier<SoundEvent> PHONE_OPEN = register("telefonacma");
    public static final Supplier<SoundEvent> NOTIFICATION = register("telefonbildirim");
    public static final Supplier<SoundEvent> OUTGOING_CALL = register("telefongidenarama");
    public static final Supplier<SoundEvent> INCOMING_CALL = register("telefontitresim");

    private PhoneSounds() {
    }

    private static Supplier<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(NewPhone.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}

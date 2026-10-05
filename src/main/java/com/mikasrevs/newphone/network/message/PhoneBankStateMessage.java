package com.mikasrevs.newphone.network.message;

import com.mikasrevs.newphone.client.network.PhoneClientNetwork;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Dedicated Bank app snapshot, kept separate from the already large phone state JSON. */
public final class PhoneBankStateMessage {
    private static final int MAX_JSON_LENGTH = 32767;
    private final String json;

    public PhoneBankStateMessage(String json) {
        this.json = json == null ? "" : json;
    }

    public String json() {
        return json;
    }

    public static void encode(PhoneBankStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.json, MAX_JSON_LENGTH);
    }

    public static PhoneBankStateMessage decode(FriendlyByteBuf buffer) {
        return new PhoneBankStateMessage(buffer.readUtf(MAX_JSON_LENGTH));
    }

    public static void handle(PhoneBankStateMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> PhoneClientNetwork.receiveBankState(message.json)
        ));
        context.setPacketHandled(true);
    }
}

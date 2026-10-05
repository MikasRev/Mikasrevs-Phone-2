package com.mikasrevs.newphone.network.message;

import com.mikasrevs.newphone.client.network.PhoneClientNetwork;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server-filtered dispatch snapshot for civilian status and authorized tablets. */
public final class EmergencyStateMessage {
    private static final int MAX_JSON_LENGTH = 131_072;
    private final String json;

    public EmergencyStateMessage(String json) {
        this.json = json == null ? "" : json;
    }

    public String json() {
        return json;
    }

    public static void encode(EmergencyStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.json, MAX_JSON_LENGTH);
    }

    public static EmergencyStateMessage decode(FriendlyByteBuf buffer) {
        return new EmergencyStateMessage(buffer.readUtf(MAX_JSON_LENGTH));
    }

    public static void handle(EmergencyStateMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> PhoneClientNetwork.receiveEmergencyState(message.json)
        ));
        context.setPacketHandled(true);
    }
}

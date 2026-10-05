package com.mikasrevs.newphone.network.message;

import com.mikasrevs.newphone.client.phone.model.ClientPhoneModels;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Only a player's cosmetic identifier crosses the wire; no phone/app state is copied. */
public record PhoneModelMessage(UUID playerId, String modelId) {
    public static void encode(PhoneModelMessage message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.playerId);
        buffer.writeUtf(message.modelId, 32);
    }
    public static PhoneModelMessage decode(FriendlyByteBuf buffer) {
        return new PhoneModelMessage(buffer.readUUID(), buffer.readUtf(32));
    }
    public static void handle(PhoneModelMessage message, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPhoneModels.receive(message.playerId, message.modelId)));
        context.setPacketHandled(true);
    }
}

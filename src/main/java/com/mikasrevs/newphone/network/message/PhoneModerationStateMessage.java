package com.mikasrevs.newphone.network.message;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record PhoneModerationStateMessage(String json) {
    public static void encode(PhoneModerationStateMessage value,FriendlyByteBuf buffer){buffer.writeUtf(value.json,16384);}
    public static PhoneModerationStateMessage decode(FriendlyByteBuf buffer){return new PhoneModerationStateMessage(buffer.readUtf(16384));}
    public static void handle(PhoneModerationStateMessage value,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();
        context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->
                com.mikasrevs.newphone.client.moderation.PhoneModerationClient.receive(value.json)));
        context.setPacketHandled(true);
    }
}

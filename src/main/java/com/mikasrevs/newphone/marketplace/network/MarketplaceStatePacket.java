package com.mikasrevs.newphone.marketplace.network;

import com.mikasrevs.newphone.marketplace.client.MarketplaceClient;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Bounded S2C page. Image bytes use the phone's existing photo transport. */
public record MarketplaceStatePacket(String json) {
    public static final int MAX_LENGTH=262144;
    public static void encode(MarketplaceStatePacket packet,FriendlyByteBuf buffer){buffer.writeUtf(packet.json,MAX_LENGTH);}
    public static MarketplaceStatePacket decode(FriendlyByteBuf buffer){return new MarketplaceStatePacket(buffer.readUtf(MAX_LENGTH));}
    public static void handle(MarketplaceStatePacket packet,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->MarketplaceClient.receive(packet.json)));
        context.setPacketHandled(true);
    }
}

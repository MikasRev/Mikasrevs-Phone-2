package com.mikasrevs.newphone.marketplace.trade;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record TradeStatePacket(String json) {
    public static final int MAX_LENGTH=4_194_304;
    public static void encode(TradeStatePacket packet,FriendlyByteBuf buffer){buffer.writeUtf(packet.json,MAX_LENGTH);}
    public static TradeStatePacket decode(FriendlyByteBuf buffer){return new TradeStatePacket(buffer.readUtf(MAX_LENGTH));}
    public static void handle(TradeStatePacket packet,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->TradeClient.receive(packet.json)));context.setPacketHandled(true);
    }
}

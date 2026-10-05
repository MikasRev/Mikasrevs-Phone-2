package com.mikasrevs.newphone.network.message;

import com.mikasrevs.newphone.client.network.PhoneClientNetwork;
import java.util.function.Supplier;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** S2C social metadata snapshot for Lumi/X (and later Tinder). */
public final class PhoneSocialStateMessage {
    private final PhoneStateMessage chunk;
    private PhoneSocialStateMessage(PhoneStateMessage chunk) { this.chunk = chunk; }
    public static List<PhoneSocialStateMessage> split(String json) {
        return PhoneStateMessage.split(json).stream().map(PhoneSocialStateMessage::new).toList();
    }
    public static void encode(PhoneSocialStateMessage m,FriendlyByteBuf b){PhoneStateMessage.encode(m.chunk,b);}
    public static PhoneSocialStateMessage decode(FriendlyByteBuf b){return new PhoneSocialStateMessage(PhoneStateMessage.decode(b));}
    public static void handle(PhoneSocialStateMessage m,Supplier<NetworkEvent.Context> supplier){
        NetworkEvent.Context c=supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> PhoneClientNetwork.receiveSocialStateChunk(
                m.chunk.transferId(), m.chunk.chunkIndex(), m.chunk.chunkCount(), m.chunk.jsonChunk())));
        c.setPacketHandled(true);
    }
}

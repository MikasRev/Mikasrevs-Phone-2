package com.mikasrevs.newphone.network.message;

import com.mikasrevs.newphone.network.PhoneVoiceCodec;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

public record PhoneVoicePacket(String kind,String request,int index,int count,byte[] bytes) {
    public static List<PhoneVoicePacket> split(String kind,String request,byte[] bytes){
        int maxWire="UPLOAD".equals(kind)?PhoneVoiceCodec.MAX_UPLOAD_WIRE:PhoneVoiceCodec.MAX_WIRE;
        if(bytes==null||bytes.length==0||bytes.length>maxWire)throw new IllegalArgumentException();
        int count=(bytes.length+PhoneVoiceCodec.CHUNK-1)/PhoneVoiceCodec.CHUNK;List<PhoneVoicePacket> packets=new ArrayList<>();
        for(int i=0;i<count;i++)packets.add(new PhoneVoicePacket(kind,request,i,count,Arrays.copyOfRange(bytes,i*PhoneVoiceCodec.CHUNK,Math.min(bytes.length,(i+1)*PhoneVoiceCodec.CHUNK))));return packets;
    }
    public static void encode(PhoneVoicePacket p,FriendlyByteBuf b){b.writeUtf(p.kind,8);b.writeUtf(p.request,1600);b.writeVarInt(p.index);b.writeVarInt(p.count);b.writeByteArray(p.bytes);}
    public static PhoneVoicePacket decode(FriendlyByteBuf b){return new PhoneVoicePacket(b.readUtf(8),b.readUtf(1600),b.readVarInt(),b.readVarInt(),b.readByteArray(PhoneVoiceCodec.CHUNK));}
    public static void handle(PhoneVoicePacket p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();var sender=c.getSender();
        if(sender!=null&&"UPLOAD".equals(p.kind)&&com.mikasrevs.newphone.network.PhoneRequestLimits.allow(sender,
                com.mikasrevs.newphone.network.PhoneRequestLimits.Kind.VOICE))c.enqueueWork(()->com.mikasrevs.newphone.server.PhoneVoiceStorage.receive(sender,p));
        else if(sender==null&&("DATA".equals(p.kind)||"ERROR".equals(p.kind)))c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.mikasrevs.newphone.client.voice.PhoneVoiceNotes.receive(p)));
        c.setPacketHandled(true);
    }
}

package com.mikasrevs.newphone.network;

import com.mikasrevs.newphone.network.message.PhoneVoicePacket;
import java.util.Arrays;
import java.util.function.Consumer;

/** Sends only the requested number of chunks; never creates a second full payload copy. */
public final class PhoneVoiceTransfer {
    private final String kind, request;
    private final byte[] bytes;
    private final int count;
    private int next;
    public PhoneVoiceTransfer(String kind,String request,byte[] bytes) {
        int max="UPLOAD".equals(kind)?PhoneVoiceCodec.MAX_UPLOAD_WIRE:PhoneVoiceCodec.MAX_WIRE;
        if(bytes==null||bytes.length==0||bytes.length>max)throw new IllegalArgumentException("Invalid voice size");
        this.kind=kind;this.request=request;this.bytes=bytes;
        count=(bytes.length+PhoneVoiceCodec.CHUNK-1)/PhoneVoiceCodec.CHUNK;
    }
    public int send(int budget,Consumer<PhoneVoicePacket> sink) {
        int sent=0;
        while(sent<budget&&next<count) {
            int start=next*PhoneVoiceCodec.CHUNK;
            sink.accept(new PhoneVoicePacket(kind,request,next,count,Arrays.copyOfRange(bytes,start,Math.min(bytes.length,start+PhoneVoiceCodec.CHUNK))));
            next++;sent++;
        }
        return sent;
    }
    public boolean complete(){return next==count;}
}

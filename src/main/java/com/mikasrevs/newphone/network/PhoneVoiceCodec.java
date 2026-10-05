package com.mikasrevs.newphone.network;

import java.io.*;
import java.util.zip.*;

/** New recordings stop at 30 seconds; playback still supports previously saved 60-second notes. */
public final class PhoneVoiceCodec {
    public static final int SAMPLE_RATE=16_000,MAX_RECORD_SECONDS=30,MAX_PCM=MAX_RECORD_SECONDS*SAMPLE_RATE*2;
    public static final int MAX_UPLOAD_WIRE=MAX_PCM+4096;
    public static final int MAX_PLAYBACK_PCM=60*SAMPLE_RATE*2,MAX_WIRE=MAX_PLAYBACK_PCM+4096,CHUNK=24_000;
    private PhoneVoiceCodec() { }
    public static byte[] encode(byte[] pcm)throws IOException {
        if(pcm==null||pcm.length<8000||pcm.length>MAX_PCM||(pcm.length&1)!=0)throw new IOException("Invalid recording length");
        var out=new ByteArrayOutputStream();try(var zip=new DeflaterOutputStream(out)){zip.write(pcm);}return out.toByteArray();
    }
    public static byte[] decode(byte[] wire)throws IOException {
        return decode(wire,MAX_PLAYBACK_PCM,MAX_WIRE);
    }
    public static byte[] decodeUpload(byte[] wire)throws IOException {
        return decode(wire,MAX_PCM,MAX_UPLOAD_WIRE);
    }
    private static byte[] decode(byte[] wire,int maxPcm,int maxWire)throws IOException {
        if(wire==null||wire.length==0||wire.length>maxWire)throw new IOException("Invalid voice payload");
        try(var in=new InflaterInputStream(new ByteArrayInputStream(wire))){byte[] pcm=in.readNBytes(maxPcm+1);
            if(pcm.length<8000||pcm.length>maxPcm||(pcm.length&1)!=0)throw new IOException("Invalid recording length");return pcm;}
    }
    public static final class Transfer {
        private final int count,maxWire;private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private int next;
        public Transfer(int count){this(count,MAX_WIRE);}
        public static Transfer forUpload(int count){return new Transfer(count,MAX_UPLOAD_WIRE);}
        private Transfer(int count,int maxWire){if(count<1||count>(maxWire+CHUNK-1)/CHUNK)throw new IllegalArgumentException();this.count=count;this.maxWire=maxWire;}
        public boolean add(int index,int count,byte[] data){if(index!=next||index>=this.count||count!=this.count||data==null||data.length==0||data.length>CHUNK||bytes.size()+data.length>maxWire)throw new IllegalArgumentException();bytes.writeBytes(data);next++;return next==count;}
        public byte[] bytes(){return bytes.toByteArray();}
    }
}

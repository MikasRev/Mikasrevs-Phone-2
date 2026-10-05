package com.mikasrevs.newphone.client.gui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Real audio fixtures and decoder isolation; no game bootstrap or actual music library. */
public final class PhoneMusicChecks {
    private static final String AUDIO=PhoneMusicAudio.class.getName();
    public static void main(String[] args) throws Exception {
        Path sandbox=Files.createTempDirectory("phone-music-checks-");
        try {
            Path mp3=sandbox.resolve("unsupported.mp3");
            Files.write(mp3,new byte[]{'I','D','3',0});
            fails(mp3,false);
            require(!PhoneMusicAudio.isSupportedExtension("mp3"),"legacy MP3 remains playable");
            Path renamed=sandbox.resolve("renamed.wav");Files.copy(mp3,renamed);fails(renamed,false);
            Path empty=sandbox.resolve("empty.wav");Files.createFile(empty);fails(empty,false);
            Path badOgg=sandbox.resolve("broken.ogg");Files.copy(mp3,badOgg);fails(badOgg,false);
            Path wave=sandbox.resolve("valid.WAV");
            for(int channels:new int[]{1,2}) {
                byte[] pcm=new byte[8000*channels*2];
                for(int i=0;i<pcm.length;i++)pcm[i]=(byte)(i%127);
                try(var input=new AudioInputStream(new ByteArrayInputStream(pcm),new AudioFormat(8000,16,channels,true,false),8000)) {
                    AudioSystem.write(input,AudioFileFormat.Type.WAVE,wave.toFile());
                }
                var info=PhoneMusicAudio.inspect(wave);
                require(info.durationMs()==1000&&info.pcmBytes()==pcm.length,"wrong WAV metadata");
                var decoded=PhoneMusicAudio.decode(wave);
                require(java.util.Arrays.equals(pcm,decoded.pcm()),"WAV PCM changed during decoding");
            }
            byte[] header=Files.readAllBytes(wave);
            Path longWave=sandbox.resolve("too-long.wav");
            // A declared 421-second stream must be rejected before any large allocation.
            ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(40,421*8000*4);
            Files.write(longWave,header);fails(longWave,true);
            Path truncated=sandbox.resolve("truncated.wav");
            Files.write(truncated,java.util.Arrays.copyOf(Files.readAllBytes(wave),44));fails(truncated,false);
            Path huge=sandbox.resolve("huge.wav");
            try(var file=FileChannel.open(huge,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                file.position(PhoneMusicAudio.MAX_FILE_BYTES);file.write(ByteBuffer.wrap(new byte[]{0}));
            }
            fails(huge,true);
            Path ogg=Path.of("src/main/resources/assets/newphone/sounds/message_send.ogg");
            var oggInfo=PhoneMusicAudio.inspect(ogg);
            var oggData=PhoneMusicAudio.decode(ogg);
            require(oggInfo.durationMs()>0&&oggData.pcm().length>0,"valid OGG stopped working");
            require(oggData.pcm().length==oggInfo.pcmBytes(),"OGG streaming decode dropped samples");
            missingDecoder(mp3,wave,ogg);
            System.out.println("Music checks passed: WAV/OGG decoding, MP3 rejection, malformed/oversized/truncated audio, missing decoder isolation.");
        } finally {
            try(var paths=Files.walk(sandbox)) {
                for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(path);
            }
        }
    }

    private static void fails(Path file,boolean limit) throws Exception {
        try { PhoneMusicAudio.inspect(file);throw new AssertionError("Invalid audio accepted: "+file); }
        catch(Exception expected) {
            if(limit)require(expected instanceof PhoneMusicAudio.AudioLimitException,"limit has wrong error: "+expected);
        }
    }

    private static void missingDecoder(Path mp3,Path wave,Path ogg) throws Exception {
        Class<?> audio=Class.forName(AUDIO,true,new DecoderIsolation());
        var inspect=audio.getDeclaredMethod("inspect",Path.class);inspect.setAccessible(true);
        inspect.invoke(null,wave);
        for(Path file:new Path[]{mp3,ogg}) {
            try {inspect.invoke(null,file);throw new AssertionError("Unavailable format accepted");}
            catch(InvocationTargetException failure) {
                Throwable cause=failure.getCause();
                require(cause instanceof IOException,"decoder failure escaped as "+cause);
                if(file.equals(ogg))require(cause.getCause() instanceof LinkageError,"native/class failure was not exercised");
            }
        }
    }

    private static final class DecoderIsolation extends ClassLoader {
        DecoderIsolation(){super(PhoneMusicChecks.class.getClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
            if(name.startsWith("javazoom.")||name.startsWith("org.tritonus.")||name.startsWith("org.lwjgl.stb."))throw new ClassNotFoundException(name);
            if(!name.equals(AUDIO)&&!name.startsWith(AUDIO+"$"))return super.loadClass(name,resolve);
            Class<?> type=findLoadedClass(name);
            if(type==null)try(InputStream input=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                if(input==null)throw new ClassNotFoundException(name);
                byte[] bytes=input.readAllBytes();type=defineClass(name,bytes,0,bytes.length);
            }catch(IOException error){throw new ClassNotFoundException(name,error);}
            if(resolve)resolveClass(type);
            return type;
        }
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

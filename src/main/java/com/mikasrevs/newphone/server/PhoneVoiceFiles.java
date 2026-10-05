package com.mikasrevs.newphone.server;

import com.mikasrevs.newphone.network.PhoneVoiceCodec;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Filesystem-only operations. Call from the voice I/O worker, never a game tick. */
public final class PhoneVoiceFiles {
    private PhoneVoiceFiles() { }
    public static Path path(Path directory,String id) {
        if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("Invalid voice ID");
        return directory.resolve(id+".voice");
    }
    public static void write(Path directory,String id,byte[] bytes)throws IOException {
        PhoneVoiceCodec.decodeUpload(bytes);
        Path file=path(directory,id);Files.createDirectories(directory);
        if(Files.exists(file)) {
            if(Files.size(file)<=PhoneVoiceCodec.MAX_WIRE&&Arrays.equals(Files.readAllBytes(file),bytes))return;
            throw new IOException("Voice reference already exists");
        }
        Path temp=Files.createTempFile(directory,"upload-",".tmp");
        try{
            try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){
                var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            Files.move(temp,file);
        }finally{Files.deleteIfExists(temp);}
    }
    public static byte[] read(Path directory,String id)throws IOException {
        Path file=path(directory,id);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>PhoneVoiceCodec.MAX_WIRE)throw new IOException("Missing voice note");
        try(var in=Files.newInputStream(file)) {
            byte[] bytes=in.readNBytes(PhoneVoiceCodec.MAX_WIRE+1);
            PhoneVoiceCodec.decode(bytes);return bytes;
        }
    }
    public static void delete(Path directory,String id)throws IOException {Files.deleteIfExists(path(directory,id));}
    /** Only recognized files are eligible; referenced recordings and unrelated files are preserved. */
    public static int cleanOrphans(Path directory,Set<String> referenced)throws IOException {
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))return 0;
        int removed=0;
        try(var files=Files.newDirectoryStream(directory)) {
            for(Path file:files) {
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                String name=file.getFileName().toString();
                if(name.startsWith("upload-")&&name.endsWith(".tmp")){if(Files.deleteIfExists(file))removed++;continue;}
                if(!name.endsWith(".voice"))continue;
                String id=name.substring(0,name.length()-6);
                try{path(directory,id);}catch(IllegalArgumentException ignored){continue;}
                if(!referenced.contains(id)&&Files.deleteIfExists(file))removed++;
            }
        }
        return removed;
    }
}

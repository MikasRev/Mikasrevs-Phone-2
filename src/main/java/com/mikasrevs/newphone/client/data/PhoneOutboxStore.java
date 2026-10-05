package com.mikasrevs.newphone.client.data;

import com.google.gson.Gson;
import com.mikasrevs.newphone.network.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Durable per-server, per-account outbox. Only call its filesystem methods from an I/O worker. */
public final class PhoneOutboxStore {
    private static final Gson GSON=new Gson();
    private final Path directory;
    public static final class Entry {
        public PhoneSendRequest request;
        public PhoneStateData.MessageEntry message;
        public boolean attachment;
        public Entry() { }
        public Entry(PhoneSendRequest request,PhoneStateData.MessageEntry message,boolean attachment){this.request=request;this.message=message;this.attachment=attachment;}
    }
    public PhoneOutboxStore(Path root,String server,String self) {
        try {
            String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((server+"\n"+self).getBytes(StandardCharsets.UTF_8)));
            directory=root.resolve(key);
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static String serialize(Entry entry){return GSON.toJson(entry);}
    private Path file(String id,String extension){if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("Invalid pending ID");return directory.resolve(id+extension);}
    public void save(String json,byte[] media)throws IOException {
        Entry entry=GSON.fromJson(json,Entry.class);validate(entry);
        Files.createDirectories(directory);
        if(entry.attachment){if(media==null||media.length==0||media.length>PhoneVoiceCodec.MAX_WIRE)throw new IOException("Invalid pending attachment");atomic(file(entry.request.id,".bin"),media);}
        // Metadata is committed last: a listed item always has a complete attachment.
        atomic(file(entry.request.id,".json"),json.getBytes(StandardCharsets.UTF_8));
    }
    public List<Entry> load()throws IOException {
        var entries=new ArrayList<Entry>();if(!Files.isDirectory(directory))return entries;
        try(var files=Files.newDirectoryStream(directory,"*.json")) {
            for(Path path:files)try {
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>128_000)continue;
                Entry entry=GSON.fromJson(Files.readString(path,StandardCharsets.UTF_8),Entry.class);validate(entry);
                if(!path.equals(file(entry.request.id,".json")))continue;
                entries.add(entry);
            }catch(RuntimeException|IOException e){System.err.println("[MPhone] Pending message could not be read: "+path.getFileName());}
        }
        entries.sort(Comparator.comparingLong(e->e.message.timestamp));return entries;
    }
    public byte[] media(String id)throws IOException {
        Path path=file(id,".bin");
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>PhoneVoiceCodec.MAX_WIRE)throw new IOException("Missing pending attachment");
        try(var input=Files.newInputStream(path)){byte[] bytes=input.readNBytes(PhoneVoiceCodec.MAX_WIRE+1);if(bytes.length>PhoneVoiceCodec.MAX_WIRE)throw new IOException("Pending attachment too large");return bytes;}
    }
    public void remove(String id)throws IOException {Files.deleteIfExists(file(id,".json"));Files.deleteIfExists(file(id,".bin"));}
    private static void validate(Entry entry)throws IOException {
        if(entry==null||entry.request==null||entry.message==null||entry.request.id==null||!entry.request.id.equals(entry.message.id)
                ||entry.request.peer==null||entry.request.type==null||entry.request.body==null||entry.request.caption==null||entry.request.replyTo==null)throw new IOException("Invalid pending message");
        UUID.fromString(entry.request.id);
    }
    private static void atomic(Path target,byte[] bytes)throws IOException {
        Path temp=Files.createTempFile(target.getParent(),"pending-",".tmp");
        try {
            try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)) {
                var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
}

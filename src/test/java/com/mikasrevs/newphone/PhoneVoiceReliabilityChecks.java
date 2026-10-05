package com.mikasrevs.newphone;

import com.mikasrevs.newphone.client.data.PhoneOutboxStore;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.server.PhoneVoiceFiles;
import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Uses a newly-created temporary directory only; never opens a game world or a microphone. */
final class PhoneVoiceReliabilityChecks {
    static void run()throws Exception {
        Path root=Files.createTempDirectory("mphone-voice-check-").toAbsolutePath().normalize();
        try{transferBudget();files(root.resolve("voice"));outbox(root.resolve("outbox"));retirement();}
        finally{
            try(var paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList()){
                    if(!path.toAbsolutePath().normalize().startsWith(root))throw new AssertionError("Unsafe test cleanup path");
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("Voice reliability checks passed (transfer budgets, file cleanup, durable outbox, account isolation, retired IDs).");
    }
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static byte[] voice(int seed)throws IOException {
        byte[] pcm=new byte[PhoneVoiceCodec.MAX_PCM];new Random(seed).nextBytes(pcm);return PhoneVoiceCodec.encode(pcm);
    }
    private static void transferBudget()throws Exception {
        byte[] bytes=voice(8);
        var send=new PhoneVoiceTransfer("UPLOAD",UUID.randomUUID().toString(),bytes);
        int count=(bytes.length+PhoneVoiceCodec.CHUNK-1)/PhoneVoiceCodec.CHUNK;
        var receiver=PhoneVoiceCodec.Transfer.forUpload(count);
        int[] chunks={0};
        require(send.send(0,p->{throw new AssertionError("zero budget sent data");})==0,"zero budget failed");
        boolean interrupted=false;
        try{send.send(1,p->{throw new IllegalStateException("network interrupted");});}catch(IllegalStateException expected){interrupted=true;}
        require(interrupted&&!send.complete(),"failed send advanced cursor");
        int ticks=0;
        while(!send.complete()){
            int sent=send.send(2,p->{require(p.index()==chunks[0]++,"chunk skipped or repeated");receiver.add(p.index(),p.count(),p.bytes());});
            require(sent>0&&sent<=2,"per-tick budget exceeded");ticks++;
        }
        require(ticks==(count+1)/2&&Arrays.equals(bytes,receiver.bytes()),"paced upload lost audio");
        require(send.send(2,p->{throw new AssertionError("finished transfer sent data");})==0,"finished transfer not inert");
    }
    private static void files(Path root)throws Exception {
        byte[] bytes=voice(12);String live=UUID.randomUUID().toString(),orphan=UUID.randomUUID().toString();
        PhoneVoiceFiles.write(root,live,bytes);PhoneVoiceFiles.write(root,orphan,bytes);
        PhoneVoiceFiles.write(root,live,bytes); // Lost acknowledgement: same ID and bytes may be retried.
        boolean collision=false;
        try{PhoneVoiceFiles.write(root,live,voice(13));}catch(IOException expected){collision=true;}
        require(collision&&Arrays.equals(bytes,PhoneVoiceFiles.read(root,live)),"retry overwrote an existing recording");
        Files.writeString(root.resolve("notes.txt"),"keep");Files.writeString(root.resolve("not-a-uuid.voice"),"keep");
        Files.writeString(root.resolve("upload-interrupted.tmp"),"incomplete");
        require(PhoneVoiceFiles.cleanOrphans(root,Set.of(live))==2,"orphan and interrupted upload not removed");
        require(Arrays.equals(bytes,PhoneVoiceFiles.read(root,live)),"cleanup changed a referenced recording");
        require(Files.exists(root.resolve("notes.txt"))&&Files.exists(root.resolve("not-a-uuid.voice")),"cleanup removed unrelated files");
        boolean traversal=false;try{PhoneVoiceFiles.path(root,"../outside");}catch(IllegalArgumentException expected){traversal=true;}
        require(traversal,"invalid voice path accepted");
        PhoneVoiceFiles.delete(root,live);PhoneVoiceFiles.delete(root,live);
        require(!Files.exists(PhoneVoiceFiles.path(root,live)),"deleted voice file remained");
    }
    private static void outbox(Path root)throws Exception {
        String self=UUID.randomUUID().toString(),peer=UUID.randomUUID().toString();
        var disk=new PhoneOutboxStore(root,"server A",self);
        var request=new PhoneSendRequest();request.id=UUID.randomUUID().toString();request.peer=peer;request.type="VOICE";
        var message=new PhoneStateData.MessageEntry();message.id=request.id;message.fromUuid=self;message.toUuid=peer;
        message.type="VOICE";message.body=request.id;message.timestamp=42;message.localStatus="SAVING";
        byte[] media=voice(25);String json=PhoneOutboxStore.serialize(new PhoneOutboxStore.Entry(request,message,true));
        disk.save(json,media);disk.save(json,media);
        var reconnected=new PhoneOutboxStore(root,"server A",self);
        var loaded=reconnected.load();require(loaded.size()==1,"retry duplicated pending messages");
        require(loaded.get(0).request.id.equals(request.id)&&loaded.get(0).request.peer.equals(peer),"reconnect changed retry identity or recipient");
        require(Arrays.equals(media,reconnected.media(request.id)),"reconnect lost recording bytes");
        require(new PhoneOutboxStore(root,"server B",self).load().isEmpty(),"pending recording leaked to another server");
        require(new PhoneOutboxStore(root,"server A",UUID.randomUUID().toString()).load().isEmpty(),"pending recording leaked to another account");
        // Keep another entry when the first is acknowledged.
        String first=request.id;request.id=UUID.randomUUID().toString();message.id=request.id;message.body=request.id;
        reconnected.save(PhoneOutboxStore.serialize(new PhoneOutboxStore.Entry(request,message,true)),media);
        reconnected.remove(first);
        require(reconnected.load().size()==1&&reconnected.load().get(0).request.id.equals(request.id),"acknowledgement deleted a different pending message");
        boolean missing=false;try{reconnected.media(first);}catch(IOException expected){missing=true;}
        require(missing,"acknowledged attachment remained on disk");
        reconnected.remove(request.id);require(reconnected.load().isEmpty(),"discarded pending message remained");
    }
    private static void retirement() {
        var data=new PhoneCommunicationSavedData();var snapshot=data.copySnapshot();
        String self=UUID.randomUUID().toString(),id=UUID.randomUUID().toString();
        snapshot.retiredMessages.put(id,self);data.replace(snapshot);
        var loaded=PhoneCommunicationSavedData.load(data.save(new CompoundTag()));
        require(self.equals(loaded.copySnapshot().retiredMessages.get(id)),"deleted message identity lost after restart");
        var legacy=new CompoundTag();legacy.putString("Json","{\"messages\":[]}");
        require(PhoneCommunicationSavedData.load(legacy).copySnapshot().retiredMessages.isEmpty(),"legacy save migration failed");
        var damaged=new CompoundTag();damaged.putString("Json","{ damaged");
        require(!PhoneCommunicationSavedData.load(damaged).canCleanVoiceArchive(),"damaged message archive allowed deleting voice files");
        var shared=new PhoneCommunicationSavedData.StoredMessage();shared.id="first";shared.type="VOICE";shared.body=id;shared.fromUuid=self;shared.toUuid="peer";
        snapshot=data.copySnapshot();snapshot.messages.add(shared);data.replace(snapshot);
        require(data.canReadVoice(self,id)&&data.canReadVoice("peer",id)&&!data.canReadVoice("stranger",id),"voice access check failed");
        require(data.referencesVoice(id)&&data.voiceReferences().contains(id),"live voice omitted from cleanup protection");
    }
}

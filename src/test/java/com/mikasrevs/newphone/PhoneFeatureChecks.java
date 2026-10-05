package com.mikasrevs.newphone;

import com.google.gson.Gson;
import com.mikasrevs.newphone.client.data.*;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.network.message.*;
import com.mikasrevs.newphone.server.data.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** Data-only tests: no world, microphone, Minecraft instance or player files. */
final class PhoneFeatureChecks {
    static void run() throws Exception {
        archives(); history(); voice(); cache();
        System.out.println("Phone feature checks passed (private archives, page boundaries, receipts, voice transport).");
    }
    private static void require(boolean condition,String failure) { if(!condition)throw new AssertionError(failure); }
    private static void archives() throws Exception {
        var social=new PhoneSocialSavedData();var assets=new PhoneSocialAssetSavedData();
        var field=PhoneSocialSavedData.class.getDeclaredField("assets");field.setAccessible(true);field.set(social,assets);
        UUID self=UUID.randomUUID(), other=UUID.randomUUID();String first="";
        for(int i=0;i<50;i++){String ref=social.addGramPost(self,"Player","post "+i,new byte[]{1,2,3},100L);if(i==0)first=ref;}
        String image=first;
        String id=social.toClientState(self,100L).blockGramPosts.stream().filter(p->image.equals(p.imageRef)).findFirst().orElseThrow().id;
        require(social.addGramPost(self,"Player","51",new byte[]{4},100L).isEmpty(),"51st post was not rejected");
        require(social.sharedGramPost(id)!=null&&assets.getImage(first)!=null,"51st post deleted old content");
        require(!social.managePost(other,"BLOCKGRAM",id,"ARCHIVE"),"foreign archive allowed");
        require(!social.managePost(self,"BLOCKGRAM",id,"INVALID"),"invalid operation changed data");
        require(social.managePost(self,"BLOCKGRAM",id,"ARCHIVE"),"archive failed");
        require(social.hasPostCapacity(self,"BLOCKGRAM")&&assets.getImage(first)!=null,"archive lost photo or did not free slot");
        require(social.sharedGramPost(id)==null,"archived post publicly shareable");
        require(social.toClientState(self,100L).archivedPosts.size()==1&&social.toClientState(other,100L).archivedPosts.isEmpty(),"archive privacy failed");
        require(!social.addGramPost(self,"Player","new",new byte[]{4},100L).isEmpty(),"archive slot unavailable");
        require(!social.managePost(self,"BLOCKGRAM",id,"RESTORE"),"restore exceeded 50 posts");
        var reloaded=PhoneSocialSavedData.load(social.save(new CompoundTag()));field.set(reloaded,assets);
        require(reloaded.toClientState(self,100L).archivedPosts.size()==1,"archive lost after reload");
        String second=social.toClientState(self,100L).blockGramPosts.get(0).id;
        require(social.managePost(self,"BLOCKGRAM",second,"ARCHIVE"),"second archive failed");
        require(social.managePost(self,"BLOCKGRAM",id,"RESTORE")&&social.sharedGramPost(id)!=null,"restore failed");
        require(social.managePost(self,"BLOCKGRAM",second,"DELETE")&&social.toClientState(self,100L).archivedPosts.isEmpty(),"archive delete failed");
        for(int i=0;i<50;i++)social.addBlockPost(self,"Player","post "+i,null,100L);
        social.addBlockPost(self,"Player","51",null,100L);
        var state=social.toClientState(self,100L);
        require(state.blockPostPosts.size()==50&&state.blockPostPosts.stream().noneMatch(p->p.body.equals("51")),"X allowance removed an old post");
        String xid=state.blockPostPosts.get(0).id;
        require(social.managePost(self,"BLOCKPOST",xid,"ARCHIVE")&&social.managePost(self,"BLOCKPOST",xid,"RESTORE"),"X archive/restore failed");
    }
    private static PhoneCommunicationSavedData.StoredMessage message(String id,String from,String to,String type) {
        var m=new PhoneCommunicationSavedData.StoredMessage();m.id=id;m.fromUuid=from;m.toUuid=to;m.type=type;m.body=id;m.timestamp=1234;return m;
    }
    private static void history() {
        var messages=new ArrayList<PhoneCommunicationSavedData.StoredMessage>();
        for(int i=0;i<137;i++){
            messages.add(message("text"+i,"peer","self","TEXT"));
            messages.add(message("gram"+i,"peer","self","GRAM_VOICE"));
            messages.add(message("private"+i,"other","stranger","TEXT"));
        }
        var state=new PhoneStateData();PhoneHistory.recent(messages,"self",state);
        require(state.messages.size()==100,"initial history must be 50 per conversation/channel");
        require(state.unreadCounts.get("MESSAGE|peer")==137&&state.unreadCounts.get("GRAM|peer")==137,"unloaded unread count wrong");
        var page=PhoneHistory.page(messages,"self","peer","MESSAGE","text87");
        require(page.messages.size()==50&&page.messages.get(0).id.equals("text37")&&page.messages.get(49).id.equals("text86"),"page overlap or timestamp collision");
        require(page.historyMore.get("MESSAGE|peer"),"older page missing");
        page=PhoneHistory.page(messages,"self","peer","MESSAGE","text37");
        require(page.messages.size()==37&&!page.historyMore.get("MESSAGE|peer"),"oldest page boundary wrong");
        require(PhoneHistory.page(messages,"self","peer","MESSAGE","private87").messages.isEmpty(),"foreign cursor accepted");
        require(messages.size()==411,"paging removed persistent history");
    }
    private static void voice() throws Exception {
        byte[] pcm=new byte[PhoneVoiceCodec.MAX_PCM];new Random(42).nextBytes(pcm);
        byte[] compressed=PhoneVoiceCodec.encode(pcm);
        require(Arrays.equals(pcm,PhoneVoiceCodec.decode(compressed)),"voice samples corrupted");
        require(PhoneVoiceCodec.MAX_PCM==30*PhoneVoiceCodec.SAMPLE_RATE*2,"capture must stop at 30 seconds");
        require(Arrays.equals(pcm,PhoneVoiceCodec.decodeUpload(compressed)),"exact 30-second upload rejected");
        boolean tooLongRejected=false;
        try{PhoneVoiceCodec.encode(new byte[pcm.length+2]);}catch(java.io.IOException expected){tooLongRejected=true;}
        require(tooLongRejected,"encoder accepted recording beyond 30 seconds");
        var longUpload=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.DeflaterOutputStream(longUpload)){zip.write(new byte[pcm.length+2]);}
        tooLongRejected=false;
        try{PhoneVoiceCodec.decodeUpload(longUpload.toByteArray());}catch(java.io.IOException expected){tooLongRejected=true;}
        require(tooLongRejected,"compressed upload bypassed duration limit");
        byte[] legacyPcm=new byte[PhoneVoiceCodec.MAX_PLAYBACK_PCM];new Random(43).nextBytes(legacyPcm);
        var legacyOutput=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.DeflaterOutputStream(legacyOutput)){zip.write(legacyPcm);}
        byte[] legacy=legacyOutput.toByteArray();
        require(Arrays.equals(legacyPcm,PhoneVoiceCodec.decode(legacy)),"legacy 60-second playback broken");
        var legacyPackets=PhoneVoicePacket.split("DATA",UUID.randomUUID().toString(),legacy);
        var legacyTransfer=new PhoneVoiceCodec.Transfer(legacyPackets.size());
        for(var p:legacyPackets)legacyTransfer.add(p.index(),p.count(),p.bytes());
        require(Arrays.equals(legacy,legacyTransfer.bytes()),"legacy download broken");
        tooLongRejected=false;
        try{PhoneVoiceCodec.Transfer.forUpload(legacyPackets.size());}catch(IllegalArgumentException expected){tooLongRejected=true;}
        require(tooLongRejected,"upload allowed legacy-sized transfer");
        var packets=PhoneVoicePacket.split("DATA",UUID.randomUUID().toString(),compressed);
        var transfer=PhoneVoiceCodec.Transfer.forUpload(packets.size());
        for(int i=0;i<packets.size();i++){
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try{PhoneVoicePacket.encode(packets.get(i),buf);var p=PhoneVoicePacket.decode(buf);
                require(transfer.add(p.index(),p.count(),p.bytes())==(i==packets.size()-1),"voice completed early");
            }finally{buf.release();}
        }
        require(Arrays.equals(compressed,transfer.bytes()),"voice chunks corrupted");
        boolean rejected=false;
        try{new PhoneVoiceCodec.Transfer(Integer.MAX_VALUE);}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"unbounded chunk count accepted");rejected=false;
        try{new PhoneVoiceCodec.Transfer(2).add(1,2,new byte[]{1});}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"out of order chunk accepted");
        var bomb=new java.io.ByteArrayOutputStream();try(var deflater=new java.util.zip.DeflaterOutputStream(bomb)){deflater.write(new byte[PhoneVoiceCodec.MAX_PLAYBACK_PCM+2]);}
        rejected=false;try{PhoneVoiceCodec.decode(bomb.toByteArray());}catch(java.io.IOException expected){rejected=true;}
        require(rejected,"oversized decompressed audio accepted");
        var request=new PhoneSendRequest();request.id=UUID.randomUUID().toString();request.peer=UUID.randomUUID().toString();request.body="<".repeat(4096);
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try{String json=new Gson().toJson(request);PhoneActionMessage.encode(new PhoneActionMessage("SEND_MESSAGE",request.peer,json),buf);
            require(PhoneActionMessage.decode(buf).body().equals(json),"escaped 4096-character request failed");
        }finally{buf.release();}
    }
    private static void cache() {
        PhoneMessageClient.clear();var state=new PhoneStateData();state.selfUuid="self";
        for(int i=87;i<137;i++)state.messages.add(PhoneHistory.entry(message("m"+i,"self","peer","TEXT")));
        PhoneClientData.setState(state);
        var page=new PhoneStateData();page.historyOnly=true;page.historyKey="MESSAGE|peer";
        for(int i=37;i<87;i++)page.messages.add(PhoneHistory.entry(message("m"+i,"self","peer","TEXT")));
        PhoneMessageClient.update(page);require(state.messages.size()==100,"older page replaced recent history");
        require(state.messages.get(0).id.equals("m37"),"same-millisecond older page sorted after recent messages");
        var refreshed=new PhoneStateData();refreshed.selfUuid="self";refreshed.messages.add(PhoneHistory.entry(message("m137","self","peer","TEXT")));
        PhoneClientData.setState(refreshed);require(refreshed.messages.size()==101,"sync erased loaded pages");
        var receipt=new PhoneStateData();receipt.readBy="peer";receipt.readPeer="self";receipt.readChannel="MESSAGE";
        PhoneMessageClient.update(receipt);require(refreshed.messages.stream().allMatch(m->m.read&&m.delivered),"read receipts did not update old pages");
        var deletion=new PhoneStateData();deletion.deletedMessageIds.add("m37");PhoneMessageClient.update(deletion);
        require(refreshed.messages.size()==100&&refreshed.messages.stream().noneMatch(m->m.id.equals("m37")),"deleted message remained cached");
        PhoneMessageClient.clear();
    }
}

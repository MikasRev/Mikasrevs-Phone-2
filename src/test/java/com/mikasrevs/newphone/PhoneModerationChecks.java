package com.mikasrevs.newphone;

import com.google.gson.Gson;
import com.mikasrevs.newphone.moderation.*;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.network.message.PhoneModerationStateMessage;
import com.mikasrevs.newphone.client.moderation.PhoneModerationClient;
import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;

final class PhoneModerationChecks {
    static void run() {
        UUID admin=UUID.randomUUID(),moderator=UUID.randomUUID(),player=UUID.randomUUID(),other=UUID.randomUUID();
        var data=new PhoneModerationData();
        data.remember(admin,"Admin");data.remember(moderator,"Moderator");data.remember(player,"Player");data.remember(other,"Other");
        require(!data.canManage(player,false),"normal player gained panel access");
        require(!data.setModerator(false,player,true),"forged moderator request accepted");
        require(!data.setBan(player,false,other,false,"BLOCKGRAM",true),"normal player can ban");
        require(data.setModerator(true,moderator,true),"OP cannot appoint moderator");
        require(data.canManage(moderator,false),"moderator has no panel access");
        require(!data.setModerator(false,other,true),"moderator can delegate OP authority");
        require(!data.setModerator(false,moderator,false),"moderator can revoke roles");
        require(!data.setBan(moderator,false,admin,true,"BLOCKPOST",true),"moderator can ban OP");
        require(!data.setBan(admin,true,moderator,false,"TINDER",true),"staff protection missing");
        require(!data.setBan(admin,true,player,false,"UNKNOWN",true),"unknown app ban accepted");
        require(!data.setBan(admin,true,UUID.randomUUID(),false,"BLOCKGRAM",true),"unregistered target accepted");
        require(data.setBan(moderator,false,player,false,"BLOCKGRAM",true),"moderator cannot ban");
        require(data.banned(player,"BLOCKGRAM")&&!data.banned(player,"BLOCKPOST")&&!data.banned(player,"TINDER"),"ban leaked across apps");
        require(!data.banned(other,"BLOCKGRAM"),"ban leaked across players");
        data=PhoneModerationData.load(data.save(new CompoundTag()));
        require(data.moderator(moderator)&&data.banned(player,"BLOCKGRAM"),"role/ban lost on world reload");
        data.remember(player,"RenamedPlayer");require(data.banned(player,"BLOCKGRAM"),"rename bypassed ban");
        require(data.setBan(moderator,false,player,false,"BLOCKGRAM",false)&&!data.banned(player,"BLOCKGRAM"),"unban failed");
        require(data.setModerator(true,moderator,false),"role removal failed");
        require(!data.canManage(moderator,false)&&!data.setBan(moderator,false,other,false,"TINDER",true),"revoked moderator can still ban");
        data=PhoneModerationData.load(data.save(new CompoundTag()));require(!data.moderator(moderator),"revocation lost after reload");
        for(String platform:PhoneModerationData.PLATFORMS)require(data.setBan(admin,true,player,false,platform,true),"OP ban failed");
        scopeChecks();redaction(data,player,other);clientState();voiceRead(data,player,other);packet();
        require(!new PhoneModerationData().banned(player,"BLOCKGRAM"),"moderation leaked into another world");
        System.out.println("Moderation checks passed: OP/mod permissions, revocation, persistent per-app bans, malformed scopes, data/media filtering, client access reset and packet limits.");
    }
    private static void scopeChecks() {
        for(String action:List.of("SEND_GRAM_TEXT","SEND_GRAM_REPLY","SOCIAL_GRAM_BIO","SOCIAL_GRAM_COMMENT","MARK_GRAM_READ","DELETE_GRAM_MESSAGE"))
            require(PhoneModerationData.actionPlatform(action,"").equals("BLOCKGRAM"),"unprotected action "+action);
        for(String action:List.of("SEND_TINDER_TEXT","TINDER_SWIPE","TINDER_ENABLED","MARK_TINDER_READ","DELETE_TINDER_MESSAGE"))
            require(PhoneModerationData.actionPlatform(action,"").equals("TINDER"),"unprotected action "+action);
        require(PhoneModerationData.actionPlatform("SOCIAL_POST_REPLY","").equals("BLOCKPOST"),"reply bypass");
        require(PhoneModerationData.actionPlatform("SOCIAL_MANAGE_POST","BLOCKPOST|RESTORE").equals("BLOCKPOST"),"archive bypass");
        for(String body:List.of("","unknown","BLOCKPOST_fake","TINDER|junk"))
            require(PhoneModerationData.actionPlatform("SOCIAL_FOLLOW_REQUEST",body).equals("BLOCKGRAM"),"legacy fallback bypass "+body);
        require(PhoneModerationData.actionPlatform("SOCIAL_NOTIFICATIONS_CLEAR"," tinder ").equals("TINDER"),"platform normalization");
        require(PhoneModerationData.actionPlatform("HISTORY_PAGE","GRAM|cursor").equals("BLOCKGRAM"),"history bypass");
        for(String type:List.of("GRAM_PHOTO","GRAM_VOICE","GRAM_PROFILE","BLOCKGRAM_STORY"))require(PhoneModerationData.platform(type).equals("BLOCKGRAM"),"media bypass");
        require(PhoneModerationData.platform("TINDER_VOICE").equals("TINDER"),"Minder voice bypass");
        require(PhoneModerationData.actionPlatform("SEND_TEXT","").isEmpty(),"regular messages restricted");
    }
    private static void redaction(PhoneModerationData data,UUID player,UUID other) {
        var state=new PhoneSocialStateData();
        state.blockGramPosts.add(new PhoneSocialStateData.GramPostEntry());state.blockPostPosts.add(new PhoneSocialStateData.BlockPostEntry());
        state.tinderLikedUuids.add(other.toString());state.tinderMatchUuids.add(other.toString());
        var profile=new PhoneSocialStateData.ProfileEntry();profile.playerUuid=other.toString();profile.blockGramPhotoRef="gram";
        profile.blockPostAvatarRef="post";profile.tinderPhotoRef="tinder";profile.tinderBio="bio";state.profiles.add(profile);
        PhoneModerationManager.filter(data,player,state);
        require(state.blockGramPosts.isEmpty()&&state.blockPostPosts.isEmpty()&&state.tinderMatchUuids.isEmpty(),"banned feed still sent");
        require(profile.blockGramPhotoRef.isEmpty()&&profile.blockPostAvatarRef.isEmpty()&&profile.tinderPhotoRef.isEmpty()&&!profile.tinderEnabled,"banned profile assets still sent");
        var messages=new PhoneStateData();
        for(String type:List.of("GRAM_TEXT","TINDER_TEXT","TEXT","GROUP_TEXT")){var message=new PhoneStateData.MessageEntry();message.type=type;messages.messages.add(message);}
        messages.unreadCounts.put("GRAM|"+other,2);messages.unreadCounts.put("MESSAGE|"+other,3);
        PhoneModerationManager.filterMessages(data,player,messages);
        require(messages.messages.size()==2&&messages.messages.stream().noneMatch(m->m.type.equals("GRAM_TEXT")||m.type.equals("TINDER_TEXT")),"banned DM still sent");
        require(messages.unreadCounts.size()==1&&messages.unreadCounts.containsKey("MESSAGE|"+other),"DM badge filtering broke normal messages");
    }
    private static void clientState() {
        PhoneModerationClient.logout(null);require(!PhoneModerationClient.canManage()&&!PhoneModerationClient.ready(),"new connection inherits role");
        var state=new PhoneModerationState();state.moderator=true;state.players=new ArrayList<>();state.players.add(new PhoneModerationState.Player());
        PhoneModerationClient.receive(new Gson().toJson(state));require(PhoneModerationClient.canManage(),"appointed moderator cannot see panel");
        state.players=null;PhoneModerationClient.receive(new Gson().toJson(state));require(PhoneModerationClient.state().players.size()==1,"access refresh discarded roster");
        state.moderator=false;state.bans=Set.of("TINDER");PhoneModerationClient.receive(new Gson().toJson(state));
        require(!PhoneModerationClient.canManage()&&PhoneModerationClient.state().players==null&&PhoneModerationClient.banned("TINDER"),"revocation retained staff data");
        PhoneModerationClient.logout(null);require(!PhoneModerationClient.ready()&&!PhoneModerationClient.banned("TINDER"),"ban leaked across servers");
    }
    private static void voiceRead(PhoneModerationData moderation,UUID player,UUID other) {
        var data=new PhoneCommunicationSavedData();var snapshot=data.copySnapshot();
        var voice=new PhoneCommunicationSavedData.StoredMessage();voice.id="v";voice.body="clip";voice.type="GRAM_VOICE";voice.fromUuid=other.toString();voice.toUuid=player.toString();snapshot.messages.add(voice);data.replace(snapshot);
        require(data.canReadVoice(player.toString(),"clip"),"fixture not visible to recipient");
        require(!data.canReadVoice(player.toString(),"clip",type->!moderation.banned(player,PhoneModerationData.platform(type))),"banned voice download allowed");
    }
    private static void packet(){
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{
            String json="{\"moderator\":true,\"bans\":[\"TINDER\"]}";
            PhoneModerationStateMessage.encode(new PhoneModerationStateMessage(json),buffer);
            require(PhoneModerationStateMessage.decode(buffer).json().equals(json),"moderation packet changed");
            buffer.clear();buffer.writeUtf("x".repeat(16385),20000);
            try{PhoneModerationStateMessage.decode(buffer);throw new AssertionError("oversized packet accepted");}catch(io.netty.handler.codec.DecoderException expected){}
        }finally{buffer.release();}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

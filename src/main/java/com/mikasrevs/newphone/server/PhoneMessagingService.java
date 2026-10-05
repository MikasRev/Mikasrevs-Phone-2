package com.mikasrevs.newphone.server;

import com.google.gson.Gson;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.server.data.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Authoritative acknowledgements, delivery receipts and paged history. */
public final class PhoneMessagingService {
    private static final Gson GSON=new Gson();
    private PhoneMessagingService() { }
    public static void handle(ServerPlayer player,String action,String peer,String body) {
        try {
            if(action.equals("SEND_MESSAGE")) { send(player,GSON.fromJson(body,PhoneSendRequest.class),null); return; }
            var data=PhoneCommunicationSavedData.get(player.server);
            String self=player.getUUID().toString();
            if(action.equals("HISTORY_PAGE")) {
                String[] parts=body.split("\\|",2);
                if(parts.length!=2||!Set.of("MESSAGE","GRAM","TINDER","GROUP").contains(parts[0]))return;
                UUID.fromString(peer);
                if(!com.mikasrevs.newphone.moderation.PhoneModerationManager.require(player,parts[0]))return;
                update(player,data.historyPage(self,peer,parts[0],parts[1])); return;
            }
            if(action.equals("DELIVERY_ACK")) {
                PhoneGroupService.receipts(player,body);
                var snapshot=data.copySnapshot();
                Set<String> ids=new HashSet<>(Arrays.asList(body.split(",")));
                if(ids.size()>100)return;
                Map<String,PhoneStateData> updates=new HashMap<>(); boolean changed=false;
                for(var m:snapshot.messages) if(!PhoneGroups.isGroup(m)&&self.equals(m.toUuid)&&!m.delivered&&ids.contains(m.id)) {
                    m.delivered=true; changed=true;
                    var packet=updates.computeIfAbsent(m.fromUuid,k->event()); packet.messages.add(PhoneHistory.entry(m));
                }
                if(changed){snapshot.messageRevision++;data.replace(snapshot);}
                updates.forEach((uuid,packet)->{var sender=player.server.getPlayerList().getPlayer(UUID.fromString(uuid));if(sender!=null)update(sender,packet);});
            }
        } catch(RuntimeException ignored) { /* Malformed requests cannot mutate another conversation. */ }
    }
    public static boolean canMessage(ServerPlayer sender,String peer,String type) {
        try {
            UUID target=UUID.fromString(peer); String self=sender.getUUID().toString();
            if(type.startsWith("GROUP_"))return PhoneGroups.member(PhoneCommunicationSavedData.get(sender.server).copySnapshot(),peer,self);
            if(target.equals(sender.getUUID()))return false;
            var data=PhoneCommunicationSavedData.get(sender.server);
            if(data.eitherBlocks(self,peer))return false;
            String channel=PhoneHistory.channel(type);
            if(!com.mikasrevs.newphone.moderation.PhoneModerationManager.pairAllowed(sender,target,channel))return false;
            var social=PhoneSocialSavedData.get(sender.server);
            if(channel.equals("TINDER"))return social.isTinderMatch(sender.getUUID(),target);
            if(channel.equals("GRAM"))return social.isAcceptedFollow("BLOCKGRAM",sender.getUUID(),target)&&social.isAcceptedFollow("BLOCKGRAM",target,sender.getUUID());
            return data.hasContact(self,peer) || com.mikasrevs.newphone.marketplace.data.MarketplaceSavedData.get(sender.server).canMessage(self,peer);
        } catch(RuntimeException ignored){return false;}
    }
    public static void send(ServerPlayer sender,PhoneSendRequest request,byte[] attachment) {
        send(sender,request,attachment,false);
    }
    static void sendPreparedVoice(ServerPlayer sender,PhoneSendRequest request) {
        send(sender,request,null,true);
    }
    private static void send(ServerPlayer sender,PhoneSendRequest request,byte[] attachment,boolean voicePrepared) {
        if(request==null)return;
        String id=request.id==null?"":request.id;
        try {
            if(!UUID.fromString(id).toString().equals(id))return;
            if(request.peer==null||request.type==null||request.body==null||request.caption==null||request.replyTo==null)throw new IllegalArgumentException();
            if(!Set.of("TEXT","GRAM_TEXT","TINDER_TEXT","PHOTO","GRAM_PHOTO","GRAM_POST","LOCATION","VOICE","GRAM_VOICE","TINDER_VOICE","GROUP_TEXT","GROUP_PHOTO","GROUP_VOICE").contains(request.type))throw new IllegalArgumentException();
            if(request.body.length()>PhoneMessageLimits.TEXT_LENGTH||request.caption.length()>240||request.replyTo.length()>64)throw new IllegalArgumentException();
            if(!com.mikasrevs.newphone.moderation.PhoneModerationManager.require(sender,request.type)) { fail(sender,id,"You are banned. Talk to an admin.");return; }
            var data=PhoneCommunicationSavedData.get(sender.server); var snapshot=data.copySnapshot();
            String self=sender.getUUID().toString(), hash=hash(GSON.toJson(request));
            boolean group=request.type.startsWith("GROUP_");
            if(group&&!PhoneGroups.member(snapshot,request.peer,self)){fail(sender,id,"Artık bu grubun üyesi değilsin.");return;}
            if(snapshot.retiredMessages.containsKey(id)){
                if(self.equals(snapshot.retiredMessages.get(id))){var state=event();state.deletedMessageIds.add(id);update(sender,state);}
                else fail(sender,id,"Bu mesaj kimliği kullanılamıyor.");
                return;
            }
            for(var existing:snapshot.messages)if(id.equals(existing.id)) {
                if(self.equals(existing.fromUuid)&&hash.equals(existing.requestHash)){
                    if(group){
                        var packet=event();packet.requestId=id;packet.requestResult="ACCEPTED";
                        if(PhoneGroups.visible(snapshot,existing,self))packet.messages.add(PhoneGroups.entry(existing,self));
                        update(sender,packet);
                    }else ack(sender,id,"ACCEPTED","",existing);
                    return;
                }
                fail(sender,id,"Bu mesaj kimliği zaten kullanılıyor.");return;
            }
            if(!canMessage(sender,request.peer,request.type)){fail(sender,id,"Bu kişiyle mesajlaşma kullanılamıyor.");return;}
            String body=request.body;
            if(request.type.endsWith("TEXT") && body.isBlank())throw new IllegalArgumentException();
            if(request.type.equals("GRAM_POST")) {
                var social=PhoneSocialSavedData.get(sender.server); var shared=social.sharedGramPost(body);
                if(shared==null){fail(sender,id,"Gönderi artık kullanılamıyor.");return;}
                String author=social.interactionOwnerUuid("BLOCKGRAM","SHARE",body);
                if(snapshot.blocked.getOrDefault(request.peer,List.of()).contains(author)||snapshot.blocked.getOrDefault(author,List.of()).contains(request.peer)
                        ||snapshot.blocked.getOrDefault(self,List.of()).contains(author)||snapshot.blocked.getOrDefault(author,List.of()).contains(self))throw new IllegalArgumentException();
                body=shared.encode();
            }
            if(request.type.endsWith("PHOTO")) {
                if(!PhoneImageLimits.accepts(attachment))throw new IllegalArgumentException();
                body=group?"group-"+id:request.type.startsWith("GRAM_")?"gram-"+id:id;
                var photos=PhonePhotoSavedData.get(sender.server);
                if(photos.contains(body)){fail(sender,id,"Fotoğraf kimliği zaten kullanılıyor.");return;}
                photos.put(body,attachment);
            }
            if(request.type.endsWith("VOICE")) {
                if(!voicePrepared){if(attachment==null)throw new IllegalArgumentException();PhoneVoiceStorage.save(sender,request,attachment);return;}
                body=id;
            }
            var target=group?null:sender.server.getPlayerList().getPlayer(UUID.fromString(request.peer));
            var m=new PhoneCommunicationSavedData.StoredMessage();
            m.id=id;m.fromUuid=self;m.fromName=sender.getGameProfile().getName();m.toUuid=request.peer;
            m.toName=target==null?snapshot.knownNames.getOrDefault(request.peer,"Player"):target.getGameProfile().getName();
            if(group){var g=snapshot.groups.get(request.peer);m.toName=g.name;m.groupSequence=++g.sequence;m.recipients.addAll(g.members.keySet());m.readBy.add(self);m.deliveredTo.add(self);}
            m.type=request.type;m.body=body;m.caption=request.caption;m.requestHash=hash;m.timestamp=System.currentTimeMillis();
            for(var old:snapshot.messages)if(old.id.equals(request.replyTo)&&PhoneHistory.channel(old.type).equals(PhoneHistory.channel(m.type))
                    &&(group?(old.toUuid.equals(request.peer)&&PhoneGroups.visible(snapshot,old,self)):((self.equals(old.fromUuid)&&request.peer.equals(old.toUuid))||(self.equals(old.toUuid)&&request.peer.equals(old.fromUuid))))) {
                m.replyToId=old.id; m.replyPreview=old.type.endsWith("TEXT")?old.body.substring(0,Math.min(48,old.body.length())):"↪";break;
            }
            snapshot.messages.add(m);snapshot.messageRevision++;data.replace(snapshot);
            if(group){PhoneGroupService.broadcast(sender,snapshot.groups.get(request.peer).members.keySet(),"");return;}
            ack(sender,id,"ACCEPTED","",m); PhoneServerService.sendState(sender,"");
            if(target!=null)PhoneServerService.sendState(target,"");
        } catch(Exception exception){fail(sender,id,"Mesaj gönderilemedi. Yeniden deneyebilirsin.");}
    }
    private static String hash(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
    public static PhoneStateData event(){var state=new PhoneStateData();state.historyOnly=true;return state;}
    public static void update(ServerPlayer player,PhoneStateData state){com.mikasrevs.newphone.moderation.PhoneModerationManager.filterMessages(player,state);PhoneNetwork.sendState(player,GSON.toJson(state));}
    public static void fail(ServerPlayer player,String id,String reason){ack(player,id,"FAILED",reason,null);}
    private static void ack(ServerPlayer player,String id,String result,String reason,PhoneCommunicationSavedData.StoredMessage message) {
        var state=event();state.requestId=id;state.requestResult=result;state.notice=reason;
        if(message!=null)state.messages.add(PhoneHistory.entry(message)); update(player,state);
    }
}

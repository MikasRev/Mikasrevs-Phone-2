package com.mikasrevs.newphone.server;

import com.google.gson.Gson;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.network.message.PhonePhotoUploadMessage;
import com.mikasrevs.newphone.server.data.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** All group mutations run on the server thread. The owner manages membership. */
public final class PhoneGroupService {
    private static final Gson GSON=new Gson();
    private PhoneGroupService(){}
    public static final class Edit {
        public String name="",member="";
        public List<String> members=new ArrayList<>();
    }
    public static void handle(ServerPlayer player,String action,String id,String body){
        try{
            UUID.fromString(id);
            var data=PhoneCommunicationSavedData.get(player.server);var s=data.copySnapshot();
            String self=player.getUUID().toString();
            if(action.equals("GROUP_DELETE")){
                var removed=s.messages.stream().filter(m->m.id.equals(id)&&PhoneGroups.isGroup(m)&&m.fromUuid.equals(self)&&PhoneGroups.visible(s,m,self)).findFirst().orElse(null);
                boolean cancelled=removed==null&&PhoneVoiceStorage.cancelPending(player,id);
                if(removed==null&&!cancelled)return;
                s.retiredMessages.put(id,self);if(removed!=null)s.messages.remove(removed);
                s.messageRevision++;data.replace(s);
                var packet=PhoneMessagingService.event();packet.deletedMessageIds.add(id);
                var recipients=removed==null?Set.of(self):s.groups.get(removed.toUuid).members.keySet();
                for(String member:recipients){var p=player.server.getPlayerList().getPlayer(UUID.fromString(member));if(p!=null)PhoneMessagingService.update(p,packet);}
                broadcast(player,recipients,"");
                if(removed!=null&&removed.type.endsWith("VOICE"))PhoneVoiceStorage.cleanupIfUnused(player.server,removed.body);
                return;
            }
            var g=s.groups.get(id);
            if(action.equals("GROUP_CREATE")){
                if(g!=null){if(g.owner.equals(self))PhoneServerService.sendState(player,"");return;}
                Edit edit=GSON.fromJson(body,Edit.class);validateName(edit.name);
                if(edit.members==null||edit.members.isEmpty())throw new IllegalArgumentException("En az bir kişi seç.");
                g=new PhoneGroups.Group();g.id=id;g.name=edit.name.trim();g.owner=self;g.members.put(self,++g.sequence);
                for(String member:new LinkedHashSet<>(edit.members)){
                    UUID.fromString(member);
                    if(!PhoneGroups.canAdd(s,g,self,member))throw new IllegalArgumentException("Yalnızca uygun kişilerin gruba eklenebilir. Engellemeleri kontrol et.");
                    g.members.put(member,++g.sequence);
                }
                s.groups.put(id,g);
            }else{
                if(g==null||!g.members.containsKey(self))throw new IllegalArgumentException("Artık bu grubun üyesi değilsin.");
                if(action.equals("GROUP_READ")){
                    var changed=PhoneGroups.markRead(s,id,self);
                    if(!changed.isEmpty()){
                        s.messageRevision++;data.replace(s);
                        sendReceipts(player,s,changed,g.members.keySet());
                        broadcast(player,g.members.keySet(),"");
                    }
                    return;
                }
                if(action.equals("GROUP_LEAVE")){
                    PhoneGroups.remove(g,self);
                    // Keep an empty group as a tombstone: a delayed creation retry cannot recreate it.
                    s.messageRevision++;data.replace(s);
                    var affected=new HashSet<>(g.members.keySet());affected.add(self);
                    broadcast(player,affected,"");return;
                }
                if(!g.owner.equals(self))throw new IllegalArgumentException("Bu işlemi yalnızca grup yöneticisi yapabilir.");
                Edit edit=GSON.fromJson(body,Edit.class);
                switch(action){
                    case "GROUP_RENAME"->{validateName(edit.name);g.name=edit.name.trim();}
                    case "GROUP_ADD"->{
                        UUID.fromString(edit.member);
                        if(!PhoneGroups.canAdd(s,g,self,edit.member))throw new IllegalArgumentException("Bu kişi eklenemiyor. Kişilerini ve engellemeleri kontrol et.");
                        g.members.put(edit.member,++g.sequence);
                    }
                    case "GROUP_REMOVE"->{
                        if(edit.member.equals(self)||!g.members.containsKey(edit.member))return;
                        PhoneGroups.remove(g,edit.member);s.messageRevision++;data.replace(s);
                        var affected=new HashSet<>(g.members.keySet());affected.add(edit.member);broadcast(player,affected,"");return;
                    }
                    default->{return;}
                }
            }
            s.knownNames.put(self,player.getGameProfile().getName());
            s.messageRevision++;data.replace(s);broadcast(player,g.members.keySet(),"");
        }catch(RuntimeException e){PhoneServerService.sendState(player,e instanceof IllegalArgumentException&&e.getMessage()!=null&&!e.getMessage().startsWith("Invalid UUID")?e.getMessage():"Grup işlemi tamamlanamadı.");}
    }
    private static void validateName(String name){
        if(name==null||name.isBlank()||name.length()>40||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Grup adı 1–40 karakter olmalı.");
    }
    public static void receipts(ServerPlayer player,String body){
        Set<String> ids=new HashSet<>(Arrays.asList(body.split(",")));if(ids.size()>100)return;
        var data=PhoneCommunicationSavedData.get(player.server);var s=data.copySnapshot();String self=player.getUUID().toString();
        var changed=PhoneGroups.acknowledge(s,ids,self);
        if(!changed.isEmpty()){
            s.messageRevision++;data.replace(s);Set<String> senders=new HashSet<>();
            for(var m:changed)senders.add(m.fromUuid);
            senders.add(self);sendReceipts(player,s,changed,senders);
        }
    }
    private static void sendReceipts(ServerPlayer source,PhoneCommunicationSavedData.Snapshot s,List<PhoneCommunicationSavedData.StoredMessage> changed,Collection<String> members){
        for(String member:members){
            var player=source.server.getPlayerList().getPlayer(UUID.fromString(member));if(player==null)continue;
            for(int i=0;i<changed.size();i+=100){
                var packet=PhoneGroups.receipts(s,changed.subList(i,Math.min(changed.size(),i+100)),member);
                if(!packet.messageReceipts.isEmpty())PhoneMessagingService.update(player,packet);
            }
        }
    }
    public static void broadcast(ServerPlayer source,Collection<String> members,String notice){
        for(String member:members){var p=source.server.getPlayerList().getPlayer(UUID.fromString(member));if(p!=null)PhoneServerService.sendState(p,notice);}
    }
    public static void avatar(ServerPlayer player,PhonePhotoUploadMessage request){
        var data=PhoneCommunicationSavedData.get(player.server);var s=data.copySnapshot();var g=s.groups.get(request.targetUuid());
        if(g==null||!g.owner.equals(player.getUUID().toString())||!g.members.containsKey(g.owner))return;
        try{UUID.fromString(request.photoId().substring(7));}catch(RuntimeException e){return;}
        if(!PhoneImageLimits.accepts(request.imageData()))return;
        var photos=PhonePhotoSavedData.get(player.server);if(photos.contains(request.photoId()))return;
        photos.put(request.photoId(),request.imageData());g.photoId=request.photoId();s.messageRevision++;data.replace(s);broadcast(player,g.members.keySet(),"");
    }
    public static void photo(ServerPlayer player,String id){
        var s=PhoneCommunicationSavedData.get(player.server).copySnapshot();String self=player.getUUID().toString();
        boolean allowed=s.groups.values().stream().anyMatch(g->g.photoId.equals(id)&&g.members.containsKey(self))
                ||s.messages.stream().anyMatch(m->m.type.equals("GROUP_PHOTO")&&m.body.equals(id)&&PhoneGroups.visible(s,m,self));
        if(allowed){byte[] bytes=PhonePhotoSavedData.get(player.server).getPhoto(id);if(bytes!=null&&bytes.length>0&&bytes.length<=30_000)PhoneNetwork.sendPhotoData(player,id,bytes);}
    }
}

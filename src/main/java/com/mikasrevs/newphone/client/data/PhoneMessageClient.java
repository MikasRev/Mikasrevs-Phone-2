package com.mikasrevs.newphone.client.data;

import com.google.gson.Gson;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.network.message.PhonePhotoUploadMessage;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Loaded history and an idempotent outbox, independent of which chat screen is open. */
public final class PhoneMessageClient {
    private static final Gson GSON=new Gson();
    private static final java.util.concurrent.ThreadPoolExecutor IO=new java.util.concurrent.ThreadPoolExecutor(
            1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(32),
            job->{Thread t=new Thread(job,"MPhone pending messages I/O");t.setDaemon(true);return t;});
    private static PhoneOutboxStore store;
    private static String scope="";
    private static Object boundConnection;
    private static String boundSelf="";
    private static long session;
    private static boolean dispatching;
    static {
        java.lang.Runtime.getRuntime().addShutdownHook(new Thread(()->{
            IO.shutdown();
            try{IO.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        },"MPhone pending messages shutdown"));
    }
    private static final Map<String,PhoneStateData.MessageEntry> CACHE=new LinkedHashMap<>();
    private static final Map<String,Pending> OUTBOX=new LinkedHashMap<>();
    private static final Map<String,Boolean> MORE=new HashMap<>();
    private static final Map<String,Long> GROUP_JOINS=new HashMap<>();
    private static final Map<String,Long> REQUESTS=new HashMap<>(), ACKS=new HashMap<>();
    private PhoneMessageClient() { }
    public static String channel(String type){return type.startsWith("GROUP_")?"GROUP":type.startsWith("GRAM_")?"GRAM":type.startsWith("TINDER_")?"TINDER":"MESSAGE";}
    public static void merge(PhoneStateData state) {
        var groups=new HashMap<String,PhoneStateData.GroupEntry>();for(var g:state.groups)groups.put(g.id,g);
        for(var g:state.groups)if(!Objects.equals(GROUP_JOINS.get(g.id),g.joinedSequence)){
            MORE.remove("GROUP|"+g.id);REQUESTS.remove("GROUP|"+g.id);
        }
        GROUP_JOINS.clear();for(var g:state.groups)GROUP_JOINS.put(g.id,g.joinedSequence);
        CACHE.values().removeIf(m->m.type.startsWith("GROUP_")&&(!groups.containsKey(m.toUuid)||m.groupSequence<groups.get(m.toUuid).joinedSequence));
        MORE.keySet().removeIf(k->k.startsWith("GROUP|")&&!groups.containsKey(k.substring(6)));
        REQUESTS.keySet().removeIf(k->k.startsWith("GROUP|")&&!groups.containsKey(k.substring(6)));
        for(var p:OUTBOX.values())if(p.request.type.startsWith("GROUP_")&&!groups.containsKey(p.request.peer))fail(p,"Artık bu grubun üyesi değilsin.");
        for(var e:state.messages){CACHE.put(e.id,e);accepted(e.id);}
        state.historyMore.forEach(MORE::putIfAbsent);
        state.messages=all(); acknowledge(state.messages);
    }
    public static void update(PhoneStateData packet) {
        if(packet.historyKey.startsWith("GROUP|")){
            var group=PhoneGroupClient.group(packet.historyKey.substring(6));
            if(group==null||group.joinedSequence!=packet.historyJoinedSequence)return;
        }
        for(String id:packet.deletedMessageIds){CACHE.remove(id);accepted(id);}
        packet.messages.removeIf(m->m.type.startsWith("GROUP_")&&(PhoneGroupClient.group(m.toUuid)==null||m.groupSequence<PhoneGroupClient.group(m.toUuid).joinedSequence));
        if(!packet.historyKey.isBlank()) {
            // A page precedes the current window, including messages sharing a millisecond.
            var older=new LinkedHashMap<String,PhoneStateData.MessageEntry>();
            for(var m:packet.messages)older.put(m.id,m);
            older.putAll(CACHE);CACHE.clear();CACHE.putAll(older);
        }
        for(var m:packet.messages){CACHE.put(m.id,m);accepted(m.id);}
        for(var receipt:packet.messageReceipts){
            var m=CACHE.get(receipt.id);
            if(m!=null){m.read=receipt.read;m.delivered=receipt.delivered;}
        }
        if("ACCEPTED".equals(packet.requestResult))accepted(packet.requestId);
        if(!packet.historyKey.isBlank()) { MORE.putAll(packet.historyMore); REQUESTS.remove(packet.historyKey); }
        if(!packet.readBy.isBlank()) for(var m:CACHE.values()) {
            if(m.toUuid.equals(packet.readBy)&&m.fromUuid.equals(packet.readPeer)&&channel(m.type).equals(packet.readChannel)) {m.read=true;m.delivered=true;}
        }
        Pending pending=OUTBOX.get(packet.requestId);
        if(pending!=null&&"FAILED".equals(packet.requestResult)) fail(pending,packet.notice);
        if(!packet.notice.isBlank())PhoneClientData.state().notice=packet.notice;
        PhoneClientData.state().messages=all(); acknowledge(packet.messages);
    }
    public static void older(String peer,String channel) {
        String key=channel+"|"+peer; long now=System.currentTimeMillis();
        if(!MORE.getOrDefault(key,true)||now-REQUESTS.getOrDefault(key,0L)<3000)return;
        var conversation=CACHE.values().stream().filter(m->channel(m.type).equals(channel)&&(peer.equals(m.fromUuid)||peer.equals(m.toUuid)))
                .min(Comparator.comparingLong(m->m.timestamp));
        REQUESTS.put(key,now);
        PhoneNetwork.sendAction("HISTORY_PAGE",peer,channel+"|"+conversation.map(m->m.id).orElse(""));
    }
    public static void send(String peer,String type,String body,String reply,String caption,byte[] media) {
        var r=new PhoneSendRequest();r.id=UUID.randomUUID().toString();r.peer=peer;r.type=type;r.body=body;r.replyTo=reply;r.caption=caption;
        var e=new PhoneStateData.MessageEntry();e.id=r.id;e.fromUuid=PhoneClientData.selfUuid();e.toUuid=peer;
        e.fromName=PhoneClientData.selfName();var contact=PhoneClientData.contactByUuid(peer);e.toName=contact==null?"Player":contact.name;
        e.type=type;e.body=type.endsWith("PHOTO")?(type.startsWith("GROUP_")?"group-":type.startsWith("GRAM_")?"gram-":"")+r.id:type.endsWith("VOICE")?r.id:body;
        if(type.startsWith("GROUP_"))e.groupSequence=Long.MAX_VALUE;
        e.caption=caption;e.timestamp=System.currentTimeMillis();e.replyToId=reply;e.localStatus="SENDING";
        if(type.endsWith("PHOTO")&&media!=null)PhonePhotoStore.receive(e.body,media);
        restoreForConnection();
        Pending pending=new Pending(r,e,media,media!=null);OUTBOX.put(r.id,pending);
        persist(pending);PhoneClientData.state().messages=all();
    }
    /** Called only by the network client, after the authenticated self UUID is known. */
    public static void prepareConnection(String self){
        Object connection=Minecraft.getInstance().getConnection();
        if(boundConnection!=null&&(boundConnection!=connection||!boundSelf.equals(self))){
            PhoneClientData.clear();com.mikasrevs.newphone.client.voice.PhoneVoiceNotes.reset();
        }
        boundConnection=connection;boundSelf=self;
    }
    public static void restoreForConnection() {
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.getConnection()==null||PhoneClientData.selfUuid().isBlank())return;
        String server=mc.getCurrentServer()!=null?"remote:"+mc.getCurrentServer().ip
                :mc.getSingleplayerServer()!=null?"local:"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize():"";
        if(server.isEmpty())return;
        String next=server+"|"+PhoneClientData.selfUuid();if(next.equals(scope))return;
        scope=next;long token=++session;
        PhoneOutboxStore disk=new PhoneOutboxStore(mc.gameDirectory.toPath().resolve("newphone/outbox"),server,PhoneClientData.selfUuid());
        store=disk;
        try{IO.execute(()->{
            try{
                var saved=disk.load();
                mc.execute(()->{if(session!=token||store!=disk)return;
                    for(var entry:saved){
                        if(CACHE.containsKey(entry.request.id)){removeSaved(disk,entry.request.id);continue;}
                        if(OUTBOX.containsKey(entry.request.id))continue;
                        entry.message.localStatus="FAILED";
                        entry.message.localError=PhoneSettingsStore.text("Gönderim tamamlanmamış. Yeniden göndermek için dokun.","Sending was interrupted. Tap to retry.");
                        Pending pending=new Pending(entry.request,entry.message,null,entry.attachment);pending.persisted=true;pending.sentAt=1;
                        OUTBOX.put(entry.request.id,pending);
                    }
                    PhoneClientData.state().messages=all();
                });
            }catch(Exception e){mc.execute(()->{if(session==token)PhoneClientData.state().notice=PhoneSettingsStore.text("Bekleyen mesajlar okunamadı; dosyalar korundu.","Could not read pending messages; files were preserved.");});}
        });}catch(java.util.concurrent.RejectedExecutionException e){scope="";}
    }

    private static void persist(Pending p) {
        p.entry.localStatus="SAVING";p.entry.localError="";p.working=true;
        PhoneOutboxStore disk=store;long token=session;
        if(disk==null){p.working=false;fail(p,PhoneSettingsStore.text("Bağlantı hazır değil. Yeniden dene.","Connection is not ready. Try again."));return;}
        String json=PhoneOutboxStore.serialize(new PhoneOutboxStore.Entry(p.request,p.entry,p.attachment));
        byte[] media=p.media;
        try{IO.execute(()->{
            String failure="";
            try{disk.save(json,media);}catch(Exception e){failure=PhoneSettingsStore.text("Kayıt diske yazılamadı. Boş alanı kontrol edip yeniden dene.","Could not save locally. Check free space and retry.");}
            String result=failure;
            Minecraft.getInstance().execute(()->{
                if(token!=session||OUTBOX.get(p.request.id)!=p)return;
                p.working=false;
                if(!result.isEmpty()){fail(p,result);return;}
                p.persisted=true;p.media=null;p.entry.localStatus="QUEUED";
            });
        });}catch(java.util.concurrent.RejectedExecutionException e){p.working=false;fail(p,PhoneSettingsStore.text("Kaydetme sırası dolu. Yeniden dene.","Save queue is busy. Try again."));}
    }

    public static boolean retry(String id) {
        Pending p=OUTBOX.get(id);if(p==null||!"FAILED".equals(p.entry.localStatus))return false;
        p.entry.localError="";
        if(p.persisted)p.entry.localStatus="QUEUED";else persist(p);
        return true;
    }
    public static boolean discard(String id){
        Pending pending=OUTBOX.get(id);if(pending==null)return false;
        com.mikasrevs.newphone.client.voice.PhoneVoiceNotes.cancelUpload(id);
        accepted(id);PhoneClientData.state().messages=all();
        if(pending.sentAt>0)PhoneNetwork.sendAction(switch(channel(pending.request.type)){
            case "GROUP"->"GROUP_DELETE";case "GRAM"->"DELETE_GRAM_MESSAGE";case "TINDER"->"DELETE_TINDER_MESSAGE";default->"DELETE_MESSAGE";
        },id,"");
        return true;
    }
    private static void dispatch(Pending p) {
        PhoneOutboxStore disk=store;long token=session;dispatching=true;p.working=true;
        try{IO.execute(()->{
            byte[] media=null;String failure="";
            try{if(p.attachment)media=disk.media(p.request.id);}
            catch(Exception e){failure=PhoneSettingsStore.text("Kaydın yerel dosyası okunamadı.","Could not read the locally saved recording.");}
            byte[] bytes=media;String result=failure;
            Minecraft.getInstance().execute(()->{
                if(token!=session)return;dispatching=false;p.working=false;
                if(OUTBOX.get(p.request.id)!=p)return;
                if(!result.isEmpty()){fail(p,result);return;}
                transmit(p,bytes);
            });
        });}catch(java.util.concurrent.RejectedExecutionException e){dispatching=false;p.working=false;fail(p,PhoneSettingsStore.text("İşlem sırası dolu. Yeniden dene.","Processing queue is busy. Try again."));}
    }
    private static void transmit(Pending p,byte[] media) {
        if(p.request.type.startsWith("GROUP_")&&PhoneGroupClient.group(p.request.peer)==null){
            fail(p,PhoneSettingsStore.text("Artık bu grubun üyesi değilsin.","You are no longer a member of this group."));return;
        }
        p.entry.localStatus="SENDING";p.sentAt=System.currentTimeMillis();
        try {
            if(Minecraft.getInstance().getConnection()==null||Minecraft.getInstance().getConnection()!=boundConnection)throw new IllegalStateException();
            if(p.request.type.endsWith("PHOTO"))PhoneNetwork.CHANNEL.sendToServer(new PhonePhotoUploadMessage(p.request.peer,p.entry.body,media,p.request.caption));
            else if(p.request.type.endsWith("VOICE"))com.mikasrevs.newphone.client.voice.PhoneVoiceNotes.upload(p.request,media);
            else PhoneNetwork.sendAction("SEND_MESSAGE",p.request.peer,GSON.toJson(p.request));
        }catch(RuntimeException e){fail(p,PhoneSettingsStore.text("Gönderim kesildi. Kayıt korundu; yeniden dene.","Sending was interrupted. Your recording is saved; retry."));}
    }
    private static void fail(Pending p,String reason){
        p.entry.localStatus="FAILED";p.entry.localError=reason==null?"":reason;
        if(p.request.type.endsWith("VOICE"))com.mikasrevs.newphone.client.voice.PhoneVoiceNotes.cancelUpload(p.request.id);
    }
    public static void uploadFailed(String id,String reason){Pending p=OUTBOX.get(id);if(p!=null)fail(p,reason);}
    private static void accepted(String id){
        Pending p=OUTBOX.remove(id);if(p!=null&&store!=null)removeSaved(store,id);
    }
    private static void removeSaved(PhoneOutboxStore disk,String id){
        try{IO.execute(()->{try{disk.remove(id);}catch(Exception e){System.err.println("[MPhone] Could not clear acknowledged pending message: "+e.getMessage());}});}
        catch(java.util.concurrent.RejectedExecutionException ignored){/* Retry is still deduplicated by the server. */}
    }
    public static void tick(){
        long now=System.currentTimeMillis();
        for(var p:OUTBOX.values())if("SENDING".equals(p.entry.localStatus)){
            if(com.mikasrevs.newphone.client.voice.PhoneVoiceNotes.uploading(p.request.id))p.sentAt=now;
            else if(now-p.sentAt>60_000)fail(p,PhoneSettingsStore.text("Sunucu onayı gelmedi. Kayıt korundu; yeniden dene.","No server acknowledgement. Your recording is saved; retry."));
        }
        if(store==null||dispatching||Minecraft.getInstance().getConnection()==null||Minecraft.getInstance().getConnection()!=boundConnection)return;
        for(var p:OUTBOX.values())if("QUEUED".equals(p.entry.localStatus)&&!p.working){
            if(p.request.type.endsWith("VOICE")&&com.mikasrevs.newphone.client.voice.PhoneVoiceNotes.uploading())continue;
            dispatch(p);break;
        }
    }
    public static String status(PhoneStateData.MessageEntry m){
        if("SAVING".equals(m.localStatus))return PhoneSettingsStore.text("Kaydediliyor…","Saving…");
        if("QUEUED".equals(m.localStatus))return PhoneSettingsStore.text("Sırada…","Queued…");
        if("SENDING".equals(m.localStatus))return PhoneSettingsStore.text("Gönderiliyor…","Sending…");
        if("FAILED".equals(m.localStatus))return PhoneSettingsStore.text("Gönderilemedi · dokun","Failed · tap to retry");
        if(m.type.startsWith("GROUP_")){if(m.read)return PhoneSettingsStore.text("Herkes gördü","Read by everyone");if(m.delivered)return PhoneSettingsStore.text("Herkese teslim edildi","Delivered to everyone");}
        if(m.read)return PhoneSettingsStore.text("Görüldü","Read");
        if(m.delivered)return PhoneSettingsStore.text("Teslim edildi","Delivered");
        return PhoneSettingsStore.text("Sunucuya ulaştı","Sent to server");
    }
    private static List<PhoneStateData.MessageEntry> all(){var all=new ArrayList<>(CACHE.values());for(var p:OUTBOX.values())if(!p.request.type.startsWith("GROUP_")||PhoneGroupClient.group(p.request.peer)!=null)all.add(p.entry);all.sort(Comparator.comparingLong(m->m.timestamp));return all;}
    private static void acknowledge(List<PhoneStateData.MessageEntry> messages) {
        String self=PhoneClientData.selfUuid();List<String> ids=new ArrayList<>();long now=System.currentTimeMillis();
        for(var m:messages)if((self.equals(m.toUuid)||(m.type.startsWith("GROUP_")&&!self.equals(m.fromUuid)))&&!m.delivered&&now-ACKS.getOrDefault(m.id,0L)>3000){ids.add(m.id);ACKS.put(m.id,now);}
        for(int i=0;i<ids.size();i+=100)PhoneNetwork.sendAction("DELIVERY_ACK","",String.join(",",ids.subList(i,Math.min(ids.size(),i+100))));
        ACKS.entrySet().removeIf(e->now-e.getValue()>30_000);
    }
    public static void clear(){session++;scope="";store=null;boundConnection=null;boundSelf="";dispatching=false;CACHE.clear();OUTBOX.clear();MORE.clear();GROUP_JOINS.clear();REQUESTS.clear();ACKS.clear();}
    private static final class Pending {
        final PhoneSendRequest request;final PhoneStateData.MessageEntry entry;final boolean attachment;
        byte[] media;long sentAt;boolean persisted,working;
        Pending(PhoneSendRequest request,PhoneStateData.MessageEntry entry,byte[] media,boolean attachment){this.request=request;this.entry=entry;this.media=media;this.attachment=attachment;}
    }
}

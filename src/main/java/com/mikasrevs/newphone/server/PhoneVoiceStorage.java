package com.mikasrevs.newphone.server;

import com.google.gson.Gson;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.network.message.PhoneVoicePacket;
import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.PacketDistributor;

/** World state stays on the server thread; decoding and filesystem work run on a bounded worker. */
public final class PhoneVoiceStorage {
    private static final Gson GSON=new Gson();
    private static final int MAX_TRANSFERS=16;
    // Serial across world reopenings too: a late delete from an old session cannot overtake a new write.
    private static final ThreadPoolExecutor FILE_IO=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),job->{Thread t=new Thread(job,"MPhone server voice I/O");t.setDaemon(true);return t;});
    private static final Map<MinecraftServer,Runtime> RUNTIMES=new IdentityHashMap<>();
    private PhoneVoiceStorage() { }

    private static final class Runtime {
        final Path directory;
        final ThreadPoolExecutor io=FILE_IO;
        final Map<UUID,Upload> uploads=new HashMap<>();
        final Map<String,UUID> writes=new HashMap<>();
        final Map<UUID,String> loading=new HashMap<>();
        final LinkedHashMap<UUID,Download> downloads=new LinkedHashMap<>();
        volatile boolean closed;
        Runtime(MinecraftServer server){directory=server.getWorldPath(LevelResource.ROOT).resolve("data/newphone_voice");}
    }
    private record Download(ServerPlayer player,String id,PhoneVoiceTransfer transfer) { }
    private static final class Upload {
        final String json;final PhoneVoiceCodec.Transfer transfer;long lastAt;
        Upload(String json,int count,long now){this.json=json;transfer=PhoneVoiceCodec.Transfer.forUpload(count);lastAt=now;}
    }

    public static void start(MinecraftServer server) {
        if(RUNTIMES.containsKey(server))return;
        Runtime r=new Runtime(server);RUNTIMES.put(server,r);
        var data=PhoneCommunicationSavedData.get(server);
        if(data.canCleanVoiceArchive()) {
            Set<String> referenced=data.voiceReferences();
            try{r.io.execute(()->{if(r.closed)return;try{PhoneVoiceFiles.cleanOrphans(r.directory,referenced);}catch(Exception e){log("clean old voice files",e);}});}
            catch(RejectedExecutionException e){log("queue startup voice cleanup",e);}
        }
    }
    private static Runtime runtime(MinecraftServer server){start(server);return RUNTIMES.get(server);}
    private static boolean current(MinecraftServer server,Runtime r){return !r.closed&&RUNTIMES.get(server)==r;}
    private static boolean online(MinecraftServer server,ServerPlayer player){return server.getPlayerList().getPlayer(player.getUUID())==player;}
    private static void log(String operation,Exception e){System.err.println("[MPhone] Could not "+operation+": "+e.getMessage());}

    public static void save(ServerPlayer sender,PhoneSendRequest request,byte[] bytes) {
        MinecraftServer server=sender.server;Runtime r=runtime(server);
        if(r.writes.containsKey(request.id))return; // A retry waits for the original write/acknowledgement.
        if(r.writes.size()>=MAX_TRANSFERS){PhoneMessagingService.fail(sender,request.id,"Ses işlemleri yoğun. Kayıt korundu; yeniden deneyebilirsin.");return;}
        r.writes.put(request.id,sender.getUUID());
        try {
            r.io.execute(()->{
                if(r.closed)return;
                String failure="";
                try{PhoneVoiceFiles.write(r.directory,request.id,bytes);}
                catch(Exception e){failure="Ses kaydedilemedi. Kayıt cihazında korundu; yeniden deneyebilirsin.";log("save voice note",e);}
                String result=failure;
                if(!r.closed)server.execute(()->{
                    if(!current(server,r))return;
                    r.writes.remove(request.id);
                    if(result.isEmpty()&&online(server,sender))PhoneMessagingService.sendPreparedVoice(sender,request);
                    else if(!result.isEmpty()&&online(server,sender))PhoneMessagingService.fail(sender,request.id,result);
                    cleanupIfUnused(server,request.id);
                });
            });
        }catch(RejectedExecutionException e){r.writes.remove(request.id);PhoneMessagingService.fail(sender,request.id,"Ses işlemleri yoğun. Yeniden deneyebilirsin.");}
    }

    public static void receive(ServerPlayer sender,PhoneVoicePacket packet) {
        Runtime r=runtime(sender.server);String id="";
        try {
            var request=GSON.fromJson(packet.request(),PhoneSendRequest.class);
            id=request.id;
            if(!UUID.fromString(id).toString().equals(id)||!Set.of("VOICE","GRAM_VOICE","TINDER_VOICE","GROUP_VOICE").contains(request.type))throw new IllegalArgumentException();
            long now=System.currentTimeMillis();
            if(packet.index()==0){
                if(!PhoneMessagingService.canMessage(sender,request.peer,request.type))throw new IllegalArgumentException();
                if(!r.uploads.containsKey(sender.getUUID())&&r.uploads.size()>=MAX_TRANSFERS){
                    PhoneMessagingService.fail(sender,id,"Ses yüklemeleri yoğun. Kayıt korundu; yeniden deneyebilirsin.");return;
                }
                r.uploads.put(sender.getUUID(),new Upload(packet.request(),packet.count(),now));
            }
            Upload upload=r.uploads.get(sender.getUUID());
            if(upload==null)return; // Remaining chunks of a rejected transfer do not create repeated notices.
            if(!upload.json.equals(packet.request()))throw new IllegalArgumentException();
            upload.lastAt=now;
            if(upload.transfer.add(packet.index(),packet.count(),packet.bytes())){
                r.uploads.remove(sender.getUUID());
                PhoneMessagingService.send(sender,request,upload.transfer.bytes());
            }
        }catch(RuntimeException e){r.uploads.remove(sender.getUUID());PhoneMessagingService.fail(sender,id,"Sesli mesaj yüklenemedi. Yeniden deneyebilirsin.");}
    }

    public static void download(ServerPlayer player,String id) {
        MinecraftServer server=player.server;Runtime r=runtime(server);
        try{PhoneVoiceFiles.path(r.directory,id);}catch(RuntimeException e){error(player,id,"Ses kaydı bulunamadı.");return;}
        if(!PhoneCommunicationSavedData.get(server).canReadVoice(player.getUUID().toString(),id,type->com.mikasrevs.newphone.moderation.PhoneModerationManager.allowed(player,type))){error(player,id,"Ses kaydı artık kullanılamıyor.");return;}
        if(id.equals(r.loading.get(player.getUUID())))return;
        cancelDownload(player);
        if(r.loading.size()+r.downloads.size()>=MAX_TRANSFERS){error(player,id,"Ses indirmeleri yoğun. Yeniden dokun.");return;}
        r.loading.put(player.getUUID(),id);
        try {
            r.io.execute(()->{
                if(r.closed)return;
                byte[] bytes=null;String failure="";
                try{bytes=PhoneVoiceFiles.read(r.directory,id);}
                catch(Exception e){failure="Ses okunamadı veya dosya bulunamadı. Yeniden dokun.";}
                byte[] result=bytes;String reason=failure;
                if(!r.closed)server.execute(()->{
                    if(!current(server,r)||!online(server,player)||!id.equals(r.loading.get(player.getUUID())))return;
                    r.loading.remove(player.getUUID());
                    if(result==null){error(player,id,reason);return;}
                    if(!PhoneCommunicationSavedData.get(server).canReadVoice(player.getUUID().toString(),id,type->com.mikasrevs.newphone.moderation.PhoneModerationManager.allowed(player,type))){error(player,id,"Ses kaydı silinmiş.");return;}
                    r.downloads.put(player.getUUID(),new Download(player,id,new PhoneVoiceTransfer("DATA",id,result)));
                });
            });
        }catch(RejectedExecutionException e){r.loading.remove(player.getUUID());error(player,id,"Ses işlemleri yoğun. Yeniden dokun.");}
    }
    public static void cancelDownload(ServerPlayer player) {
        Runtime r=RUNTIMES.get(player.server);if(r==null)return;
        r.loading.remove(player.getUUID());r.downloads.remove(player.getUUID());
    }
    public static boolean cancelPending(ServerPlayer player,String id){
        Runtime r=RUNTIMES.get(player.server);if(r==null)return false;
        boolean owned=player.getUUID().equals(r.writes.get(id));
        Upload upload=r.uploads.get(player.getUUID());
        if(upload!=null&&id.equals(GSON.fromJson(upload.json,PhoneSendRequest.class).id)){r.uploads.remove(player.getUUID());owned=true;}
        return owned;
    }
    public static void tick(MinecraftServer server) {
        Runtime r=RUNTIMES.get(server);if(r==null||r.closed)return;
        long now=System.currentTimeMillis();
        r.uploads.entrySet().removeIf(e->{
            if(now-e.getValue().lastAt<=30_000)return false;
            ServerPlayer player=server.getPlayerList().getPlayer(e.getKey());
            if(player!=null){var request=GSON.fromJson(e.getValue().json,PhoneSendRequest.class);PhoneMessagingService.fail(player,request.id,"Ses yüklemesi zaman aşımına uğradı. Yeniden deneyebilirsin.");}
            return true;
        });
        // Round-robin: at most two chunks per player and eight chunks in total per tick.
        int budget=8,turns=r.downloads.size();
        while(budget>0&&turns-->0&&!r.downloads.isEmpty()){
            UUID playerId=r.downloads.keySet().iterator().next();Download download=r.downloads.remove(playerId);
            if(!online(server,download.player))continue;
            if(!PhoneCommunicationSavedData.get(server).canReadVoice(playerId.toString(),download.id,type->com.mikasrevs.newphone.moderation.PhoneModerationManager.allowed(download.player,type))){
                error(download.player,download.id,"Ses kaydı artık kullanılamıyor.");continue;
            }
            try{
                budget-=download.transfer.send(Math.min(2,budget),p->PhoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->download.player),p));
                if(!download.transfer.complete())r.downloads.put(playerId,download);
            }catch(RuntimeException e){error(download.player,download.id,"Ses aktarımı kesildi. Yeniden dokun.");}
        }
    }
    public static void cleanupIfUnused(MinecraftServer server,String id) {
        Runtime r=runtime(server);
        if(r.writes.containsKey(id)||PhoneCommunicationSavedData.get(server).referencesVoice(id))return;
        r.downloads.entrySet().removeIf(e->e.getValue().id.equals(id));
        r.loading.entrySet().removeIf(e->e.getValue().equals(id));
        try{r.io.execute(()->{if(r.closed)return;try{PhoneVoiceFiles.delete(r.directory,id);}catch(Exception e){log("delete unused voice file",e);}});}
        catch(RejectedExecutionException e){log("queue voice cleanup (will retry on next server start)",e);}
    }
    public static void error(ServerPlayer player,String id,String message) {
        PhoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),
                new PhoneVoicePacket("ERROR",id,0,1,message.getBytes(StandardCharsets.UTF_8)));
    }
    public static void logout(ServerPlayer player){
        Runtime r=RUNTIMES.get(player.server);if(r==null)return;r.uploads.remove(player.getUUID());cancelDownload(player);
    }
    public static void stop(MinecraftServer server) {
        Runtime r=RUNTIMES.remove(server);if(r==null)return;
        r.closed=true;r.uploads.clear();r.downloads.clear();r.loading.clear();r.writes.clear();
    }
}

package com.mikasrevs.newphone.moderation;

import com.google.gson.Gson;
import com.mikasrevs.newphone.network.PhoneNetwork;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import com.mikasrevs.newphone.network.message.PhoneModerationStateMessage;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class PhoneModerationManager {
    private static final Gson GSON=new Gson();
    private static final Map<MinecraftServer,Map<UUID,String>> LAST=new WeakHashMap<>();
    private PhoneModerationManager(){}
    public static boolean op(ServerPlayer player){return player.server.getPlayerList().isOp(player.getGameProfile());}
    private static boolean op(MinecraftServer server,UUID id,String name){return server.getPlayerList().isOp(new GameProfile(id,name));}
    public static void login(ServerPlayer player){PhoneModerationData.get(player.server).remember(player.getUUID(),player.getGameProfile().getName());sendAccess(player);}
    public static void logout(ServerPlayer player){var map=LAST.get(player.server);if(map!=null)map.remove(player.getUUID());}
    public static void stop(MinecraftServer server){LAST.remove(server);}
    public static void tick(MinecraftServer server){for(var player:server.getPlayerList().getPlayers()){
        var state=access(player);String signature=GSON.toJson(state);
        if(!signature.equals(LAST.computeIfAbsent(server,k->new HashMap<>()).get(player.getUUID())))sendAccess(player);
    }}
    private static PhoneModerationState access(ServerPlayer player){
        var data=PhoneModerationData.get(player.server);var state=new PhoneModerationState();
        state.operator=op(player);state.moderator=data.moderator(player.getUUID());state.bans=data.bans(player.getUUID());return state;
    }
    public static void sendAccess(ServerPlayer player){
        var state=access(player);LAST.computeIfAbsent(player.server,k->new HashMap<>()).put(player.getUUID(),GSON.toJson(state));send(player,state);
    }
    private static void send(ServerPlayer player,PhoneModerationState state){PhoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new PhoneModerationStateMessage(GSON.toJson(state)));}
    public static boolean allowed(ServerPlayer player,String platform){
        return !PhoneModerationData.get(player.server).banned(player.getUUID(),PhoneModerationData.platform(platform));
    }
    public static boolean require(ServerPlayer player,String platform){if(allowed(player,platform))return true;sendAccess(player);return false;}
    public static boolean pairAllowed(ServerPlayer player,UUID target,String channel){
        String platform=PhoneModerationData.platform(channel);var data=PhoneModerationData.get(player.server);
        return !data.banned(player.getUUID(),platform)&&!data.banned(target,platform);
    }
    public static void handle(ServerPlayer sender,String action,String target,String body) {
        var data=PhoneModerationData.get(sender.server);
        data.remember(sender.getUUID(),sender.getGameProfile().getName());
        if(action.equals("MOD_SYNC")) { sendAccess(sender); return; }
        if(!action.equals("MOD_PANEL")) return;
        PhoneModerationRequest request;
        try { request=GSON.fromJson(body,PhoneModerationRequest.class); }
        catch(com.google.gson.JsonParseException invalid) { return; }
        if(request!=null && "LIST".equals(request.action) && data.canManage(sender.getUUID(),op(sender))) {
            var known=com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData.get(sender.server).copyMetadata().knownNames;
            known.forEach((id,name)->{try{data.remember(UUID.fromString(id),name);}catch(IllegalArgumentException ignored){}});
            for(var player:sender.server.getPlayerList().getPlayers()) data.remember(player.getUUID(),player.getGameProfile().getName());
        }
        var response=PhoneModerationPanel.respond(data,sender.getUUID(),op(sender),request,
                id->op(sender.server,id,data.players().get(id)),
                id->sender.server.getPlayerList().getPlayer(id)!=null);
        if("UPDATED".equals(response.notice)) {
            for(var player:sender.server.getPlayerList().getPlayers()) sendAccess(player);
            com.mikasrevs.newphone.server.PhoneServerService.refreshModeration(sender.server);
        }
        send(sender,response);
    }
    public static void filter(MinecraftServer server,UUID viewer,PhoneSocialStateData state){
        filter(PhoneModerationData.get(server),viewer,state);
    }
    public static void filter(PhoneModerationData data,UUID viewer,PhoneSocialStateData state){
        if(data.banned(viewer,"BLOCKGRAM")){state.blockGramPosts.clear();state.blockGramStories.clear();state.blockGramComments.clear();}
        if(data.banned(viewer,"BLOCKPOST")){state.blockPostPosts.clear();state.blockPostComments.clear();}
        if(data.banned(viewer,"TINDER")){state.tinderLikedUuids.clear();state.tinderPassedUuids.clear();state.tinderMatchUuids.clear();}
        state.archivedPosts.removeIf(p->data.banned(viewer,p.platform));
        state.follows.removeIf(f->data.banned(viewer,f.platform));state.notifications.removeIf(n->data.banned(viewer,PhoneModerationData.platform(n.platform)));
        for(var profile:state.profiles){
            if(data.banned(viewer,"BLOCKGRAM")){profile.blockGramBio="";profile.blockGramPhotoRef="";}
            if(data.banned(viewer,"BLOCKPOST")){profile.blockPostBio="";profile.blockPostAvatarRef="";profile.blockPostCoverRef="";}
            if(data.banned(viewer,"TINDER")){profile.tinderBio="";profile.tinderPhotoRef="";profile.tinderEnabled=false;}
        }
    }
    public static void filterMessages(ServerPlayer player,com.mikasrevs.newphone.network.PhoneStateData state){
        filterMessages(PhoneModerationData.get(player.server),player.getUUID(),state);
    }
    public static void filterMessages(PhoneModerationData data,UUID viewer,com.mikasrevs.newphone.network.PhoneStateData state){
        state.messages.removeIf(m->data.banned(viewer,PhoneModerationData.platform(m.type)));
        state.typing.removeIf(t->data.banned(viewer,PhoneModerationData.platform(t.channel)));
        state.unreadCounts.keySet().removeIf(k->data.banned(viewer,PhoneModerationData.platform(k.split("\\|",2)[0])));
        state.historyMore.keySet().removeIf(k->data.banned(viewer,PhoneModerationData.platform(k.split("\\|",2)[0])));
    }
}

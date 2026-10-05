package com.mikasrevs.newphone.network;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Bounded, per-player admission before enqueueWork; arbitrary packet strings never become map keys. */
public final class PhoneRequestLimits {
    private static final Map<MinecraftServer,Map<UUID,Budget>> SERVERS = new WeakHashMap<>();
    private PhoneRequestLimits() { }

    public enum Kind {
        SYNC(4, 1, 8), SOCIAL(4, 1, 8), MESSAGE(12, 6, 4), HISTORY(8, 4, 4),
        GROUP(8, 3, 4), PHOTO(6, 3, 6), MEDIA_READ(32, 16, 2),
        VOICE(80, 45, .25), VIDEO(30, 20, .5), RECEIPT(16, 10, 1), CALL_POSE(4, 2, .25), CONTROL(20, 10, 2);
        final double burst, perSecond, cost;
        Kind(double burst,double perSecond,double cost){this.burst=burst;this.perSecond=perSecond;this.cost=cost;}
    }

    public static Kind kind(String raw) {
        String action=raw==null?"":raw.trim().toUpperCase(Locale.ROOT);
        if(action.equals("CALL_POSE_START")||action.equals("CALL_POSE_STOP"))return Kind.CALL_POSE;
        if(action.equals("SYNC")||action.equals("SYNC_FULL")||action.equals("EMERGENCY_SYNC"))return Kind.SYNC;
        if(action.startsWith("SOCIAL_SYNC")||action.equals("SOCIAL_PROFILE_ASSETS"))return Kind.SOCIAL;
        if(action.startsWith("SEND_"))return Kind.MESSAGE;
        if(action.equals("HISTORY_PAGE"))return Kind.HISTORY;
        if(action.equals("DELIVERY_ACK")||action.contains("MARK_")||action.equals("GROUP_READ"))return Kind.RECEIPT;
        if(action.startsWith("GROUP_"))return Kind.GROUP;
        if(action.equals("PHOTO_GET")||action.equals("VOICE_GET"))return Kind.MEDIA_READ;
        return Kind.CONTROL;
    }
    public static boolean allow(ServerPlayer player,String action){return allow(player,kind(action));}
    public static synchronized boolean allow(ServerPlayer player,Kind kind) {
        if(player==null)return false;
        return SERVERS.computeIfAbsent(player.server,s->new HashMap<>())
                .computeIfAbsent(player.getUUID(),id->new Budget()).allow(kind,System.nanoTime());
    }
    public static synchronized void logout(ServerPlayer player){
        var players=SERVERS.get(player.server);if(players!=null)players.remove(player.getUUID());
    }
    public static synchronized void stop(MinecraftServer server){SERVERS.remove(server);}

    /** Clock supplied explicitly so boundaries and voice throughput can be regression-tested. */
    public static final class Budget {
        private final EnumMap<Kind,Bucket> lanes=new EnumMap<>(Kind.class);
        private final Bucket total=new Bucket(120,60);
        public synchronized boolean allow(Kind kind,long now) {
            Bucket lane=lanes.computeIfAbsent(kind,k->new Bucket(k.burst,k.perSecond));
            lane.refill(now);total.refill(now);
            if(lane.tokens<1||total.tokens<kind.cost)return false;
            lane.tokens--;total.tokens-=kind.cost;return true;
        }
    }
    private static final class Bucket {
        final double capacity,rate;
        double tokens;
        long last;boolean initialized;
        Bucket(double capacity,double rate){this.capacity=capacity;this.rate=rate;tokens=capacity;}
        void refill(long now){
            if(initialized)tokens=Math.min(capacity,tokens+Math.max(0,now-last)/1_000_000_000.0*rate);
            if(!initialized||now>last)last=now;
            initialized=true;
        }
    }
}

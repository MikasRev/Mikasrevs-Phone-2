package com.mikasrevs.newphone.moderation;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server/world-scoped roles and independent social-app bans, keyed by stable player UUID. */
public final class PhoneModerationData extends SavedData {
    public static final Set<String> PLATFORMS = Set.of("BLOCKPOST", "BLOCKGRAM", "TINDER");
    private final Map<UUID,String> players = new LinkedHashMap<>();
    private final Set<UUID> moderators = new HashSet<>();
    private final Map<UUID,Set<String>> bans = new HashMap<>();
    public static PhoneModerationData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PhoneModerationData::load, PhoneModerationData::new, "newphone_moderation");
    }
    public void remember(UUID id,String name) {
        if (id == null || name == null || name.isBlank()) return;
        String safe = name.substring(0,Math.min(64,name.length()));
        if (!safe.equals(players.put(id,safe))) setDirty();
    }
    public Map<UUID,String> players() { return Collections.unmodifiableMap(players); }
    public boolean moderator(UUID id) { return moderators.contains(id); }
    public boolean canManage(UUID id,boolean op) { return op || moderator(id); }
    public boolean banned(UUID id,String platform) { return bans.getOrDefault(id,Set.of()).contains(platform); }
    public Set<String> bans(UUID id) { return Set.copyOf(bans.getOrDefault(id,Set.of())); }
    public boolean setModerator(boolean actorOp,UUID target,boolean enabled) {
        if (!actorOp || target == null || !players.containsKey(target)) return false;
        if (enabled) moderators.add(target); else moderators.remove(target);
        setDirty(); return true;
    }
    public boolean setBan(UUID actor,boolean actorOp,UUID target,boolean targetOp,String platform,boolean enabled) {
        if (!canManage(actor,actorOp) || target == null || !players.containsKey(target) || platform == null || !PLATFORMS.contains(platform)) return false;
        // Staff cannot lock each other out. Remove an appointed role before banning that player.
        if (enabled && (targetOp || moderator(target))) return false;
        if (enabled) bans.computeIfAbsent(target,k->new HashSet<>()).add(platform);
        else { var values=bans.get(target); if(values!=null){values.remove(platform);if(values.isEmpty())bans.remove(target);} }
        setDirty(); return true;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        CompoundTag rows=new CompoundTag();
        for(var entry:players.entrySet()) {
            CompoundTag row=new CompoundTag();row.putString("name",entry.getValue());
            row.putBoolean("moderator",moderator(entry.getKey()));
            for(String platform:PLATFORMS)row.putBoolean(platform,banned(entry.getKey(),platform));
            rows.put(entry.getKey().toString(),row);
        }
        tag.put("players",rows);return tag;
    }
    public static PhoneModerationData load(CompoundTag tag) {
        var data=new PhoneModerationData();CompoundTag rows=tag.getCompound("players");
        for(String key:rows.getAllKeys())try {
            UUID id=UUID.fromString(key);CompoundTag row=rows.getCompound(key);
            String name=row.getString("name");data.players.put(id,name.isBlank()?id.toString():name.substring(0,Math.min(64,name.length())));
            if(row.getBoolean("moderator"))data.moderators.add(id);
            for(String platform:PLATFORMS)if(row.getBoolean(platform))data.bans.computeIfAbsent(id,k->new HashSet<>()).add(platform);
        }catch(IllegalArgumentException ignored) { }
        return data;
    }
    public static String platform(String value) {
        if(value==null)return "";
        String key=value.trim().toUpperCase(Locale.ROOT);
        if(key.startsWith("BLOCKGRAM")||key.startsWith("GRAM"))return "BLOCKGRAM";
        if(key.startsWith("BLOCKPOST")||key.startsWith("POST"))return "BLOCKPOST";
        if(key.startsWith("TINDER"))return "TINDER";
        return "";
    }
    public static String actionPlatform(String action,String body) {
        if(action.startsWith("SOCIAL_GRAM_")||action.contains("_GRAM_"))return "BLOCKGRAM";
        if(action.startsWith("SOCIAL_POST_"))return "BLOCKPOST";
        if(action.startsWith("TINDER_")||action.contains("_TINDER_"))return "TINDER";
        if(action.startsWith("SOCIAL_FOLLOW_")||action.startsWith("SOCIAL_NOTIFICATIONS_")) {
            // Match the social service's legacy default exactly; unknown names mean BlockGram.
            String value=body==null?"":body.trim().toUpperCase(Locale.ROOT);
            return value.equals("BLOCKPOST")?"BLOCKPOST":value.equals("TINDER")?"TINDER":"BLOCKGRAM";
        }
        if(action.equals("SOCIAL_MANAGE_POST")||action.equals("TYPING")||action.equals("HISTORY_PAGE"))
            return platform(body==null?"":body.split("\\|",2)[0]);
        return "";
    }
}

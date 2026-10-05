package com.mikasrevs.newphone.server.data;

import com.google.gson.Gson;
import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import com.mikasrevs.newphone.network.PhoneSharedPostData;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent server-wide social network for BlockGram and BlockPost. */
public final class PhoneSocialSavedData extends SavedData {
    private static final Gson GSON = new Gson();
    private static final int MAX_GRAM_STORIES = 240;
    private static final int MAX_GRAM_COMMENTS = 900;
    private static final int MAX_BLOCKPOST_COMMENTS = 1200;
    private static final int MAX_NOTIFICATIONS = 1600;
    private static final int MAX_GRAM_POSTS_PER_PROFILE = 50;
    private static final int MAX_BLOCKPOST_POSTS_PER_PROFILE = 50;
    private static final long POST_LIFETIME_TICKS = 4L * 24_000L;
    private static final long STORY_LIFETIME_TICKS = 2L * 24_000L;

    private static final String DATA_NAME = NewPhone.MOD_ID + "_social_data";


    private final List<StoredGramPost> gramPosts = new ArrayList<>();
    private final List<StoredGramPost> archivedGramPosts = new ArrayList<>();
    private final List<StoredBlockPost> archivedBlockPosts = new ArrayList<>();
    private final List<StoredGramStory> gramStories = new ArrayList<>();
    private final List<StoredGramComment> gramComments = new ArrayList<>();
    private final List<StoredBlockPost> blockPosts = new ArrayList<>();
    private final List<StoredBlockComment> blockComments = new ArrayList<>();
    private final List<StoredFollow> follows = new ArrayList<>();
    private final List<StoredNotification> notifications = new ArrayList<>();
    /** Persistent anti-spam memory: one X like notification per actor + target + post. */
    private final Set<String> blockPostLikeNotificationKeys = new LinkedHashSet<>();
    private final Map<String, StoredProfile> profiles = new HashMap<>();
    /** Tinder choices are private; only the requesting player's own sets are synced. */
    private final Map<String, Set<String>> tinderLikes = new HashMap<>();
    private final Map<String, Set<String>> tinderPasses = new HashMap<>();
    private transient PhoneSocialAssetSavedData assets;
    private long revision;
    private boolean recoveryRequired;
    private net.minecraft.nbt.CompoundTag recoveryBackup;

    public PhoneSocialSavedData() {
    }

    private void loadJson(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            var root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            recoverRows(root,"gramPosts",StoredGramPost.class,gramPosts);
            recoverRows(root,"archivedGramPosts",StoredGramPost.class,archivedGramPosts);
            recoverRows(root,"archivedBlockPosts",StoredBlockPost.class,archivedBlockPosts);
            recoverRows(root,"gramStories",StoredGramStory.class,gramStories);
            recoverRows(root,"gramComments",StoredGramComment.class,gramComments);
            recoverRows(root,"blockPosts",StoredBlockPost.class,blockPosts);
            recoverRows(root,"blockComments",StoredBlockComment.class,blockComments);
            recoverRows(root,"follows",StoredFollow.class,follows);
            recoverRows(root,"notifications",StoredNotification.class,notifications);
            readStrings(root.get("blockPostLikeNotificationKeys"),blockPostLikeNotificationKeys);
            // Backfill worlds created before the dedicated anti-spam set existed.
            for (StoredNotification notification : notifications) {
                if (notification != null && "BLOCKPOST".equals(notification.platform) && "LIKE".equals(notification.type)
                        && notification.postId != null && !notification.postId.isBlank()) {
                    blockPostLikeNotificationKeys.add(likeNotificationKey(notification.actorUuid, notification.targetUuid, notification.postId));
                }
            }
            var profileRows=root.get("profiles");
            if(profileRows!=null&&profileRows.isJsonObject())for(var entry:profileRows.getAsJsonObject().entrySet()) {
                try { var profile=recoverRecord(entry.getValue(),StoredProfile.class);
                    profile.playerUuid=entry.getKey(); profiles.put(entry.getKey(),profile);
                } catch(RuntimeException failure) { recoveryRequired=true; }
            } else if(profileRows!=null) recoveryRequired=true;
            recoverSets(root,"tinderLikes",tinderLikes);
            recoverSets(root,"tinderPasses",tinderPasses);
            try { if(root.has("revision"))revision=Math.max(0L,root.get("revision").getAsLong()); }
            catch(RuntimeException failure){recoveryRequired=true;}
            normalizeLoadedData();
            trim();
        } catch (RuntimeException failure) {
            recoveryRequired=true;
            com.mojang.logging.LogUtils.getLogger().warn("Could not completely decode social archive",failure);
        }
        }

    private <T> void recoverRows(com.google.gson.JsonObject root,String key,Class<T> type,List<T> target) {
        var rows=root.get(key);if(rows==null)return;
        if(!rows.isJsonArray()){recoveryRequired=true;return;}
        for(var row:rows.getAsJsonArray())try{target.add(recoverRecord(row,type));}
        catch(RuntimeException failure){recoveryRequired=true;}
    }
    private <T> T recoverRecord(com.google.gson.JsonElement row,Class<T> type) {
        var object=row.getAsJsonObject().deepCopy();
        var defaults=GSON.toJsonTree(GSON.fromJson("{}",type)).getAsJsonObject();
        for(var field:defaults.entrySet()) {
            var value=object.get(field.getKey());
            if(value==null)continue;
            if(value.isJsonNull()){object.add(field.getKey(),field.getValue());recoveryRequired=true;}
            else if(field.getValue().isJsonArray()) {
                var strings=new LinkedHashSet<String>();readStrings(value,strings);
                object.add(field.getKey(),GSON.toJsonTree(strings));
            }
        }
        return GSON.fromJson(object,type);
    }
    private void readStrings(com.google.gson.JsonElement value,Set<String> into) {
        if(value==null)return;
        if(!value.isJsonArray()){recoveryRequired=true;return;}
        for(var item:value.getAsJsonArray()) {
            if(item.isJsonPrimitive()&&item.getAsJsonPrimitive().isString())into.add(item.getAsString());
            else recoveryRequired=true;
        }
    }
    private void recoverSets(com.google.gson.JsonObject root,String key,Map<String,Set<String>> target) {
        var value=root.get(key);if(value==null)return;
        if(!value.isJsonObject()){recoveryRequired=true;return;}
        for(var entry:value.getAsJsonObject().entrySet()){
            var set=new LinkedHashSet<String>();readStrings(entry.getValue(),set);target.put(entry.getKey(),set);
        }
    }

    public static PhoneSocialSavedData load(net.minecraft.nbt.CompoundTag tag) {
        PhoneSocialSavedData data = new PhoneSocialSavedData();
        data.recoveryRequired=tag.getBoolean("UnreadableArchive");
        if(tag.contains("RecoveryBackup",net.minecraft.nbt.Tag.TAG_COMPOUND))data.recoveryBackup=tag.getCompound("RecoveryBackup").copy();
        String json = "";
        byte[] compressed = tag.getByteArray("JsonGzip");
        if (compressed.length > 0) json = gunzip(compressed);
        if(compressed.length>0&&json.isBlank())data.recoveryRequired=true;
        if (json.isBlank()) json = tag.getString("Json"); // backward-compatible older checkpoint
        data.loadJson(json);
        if(data.recoveryRequired){
            if(data.recoveryBackup==null){data.recoveryBackup=tag.copy();data.recoveryBackup.remove("RecoveryBackup");}
            com.mojang.logging.LogUtils.getLogger().warn("Recovered damaged social records; original data retained and asset cleanup blocked");
            data.setDirty();
        }
        return data;
    }

    @Override
    public synchronized net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) {
        if(recoveryBackup!=null)tag.put("RecoveryBackup",recoveryBackup.copy());
        byte[] compressed = gzip(encodeJson());
        if (compressed.length > 0) tag.putByteArray("JsonGzip", compressed);
        return tag;
    }


    public static PhoneSocialSavedData get(MinecraftServer server) {
        PhoneSocialSavedData data = PhoneSavedDataRecovery.get(server, DATA_NAME,
                PhoneSocialSavedData::load, PhoneSocialSavedData::new);
        data.assets = PhoneSocialAssetSavedData.get(server);
        return data;
    }

    public synchronized void rememberPlayer(UUID playerId, String playerName) {
        String key = playerId.toString();
        StoredProfile profile = profiles.computeIfAbsent(key, ignored -> new StoredProfile());
        profile.playerUuid = key;
        profile.playerName = safeName(playerName);
        if (profile.roleBadge == null || profile.roleBadge.isBlank()) profile.roleBadge = "PL";
    }

    public synchronized void rememberPlayer(UUID playerId, String playerName, String roleBadge) {
        rememberPlayer(playerId, playerName);
        StoredProfile profile = profiles.get(playerId.toString());
        profile.roleBadge = "AD".equalsIgnoreCase(roleBadge) ? "AD" : "PL";
    }

    public synchronized boolean hasProfile(UUID playerId) {
        return playerId != null && profiles.containsKey(playerId.toString());
    }

    public synchronized String profileName(UUID playerId) {
        StoredProfile profile = playerId == null ? null : profiles.get(playerId.toString());
        return profile == null ? "Player" : safeName(profile.playerName);
    }

    public synchronized boolean hasPostCapacity(UUID owner,String platform) {
        if(owner==null)return false;String id=owner.toString();
        return "BLOCKGRAM".equals(platform)?gramPosts.stream().filter(p->id.equals(p.authorUuid)).count()<MAX_GRAM_POSTS_PER_PROFILE
                :blockPosts.stream().filter(p->id.equals(p.authorUuid)).count()<MAX_BLOCKPOST_POSTS_PER_PROFILE;
    }
    public synchronized boolean managePost(UUID owner,String platform,String id,String operation) {
        if(owner==null||id==null||operation==null||!java.util.Set.of("ARCHIVE","RESTORE","DELETE").contains(operation))return false;
        String self=owner.toString();boolean restore="RESTORE".equals(operation), delete="DELETE".equals(operation);
        if(restore&&!hasPostCapacity(owner,platform))return false;
        if("BLOCKGRAM".equals(platform)) {
            var source=restore||delete?archivedGramPosts:gramPosts;
            var post=source.stream().filter(p->id.equals(p.id)&&self.equals(p.authorUuid)).findFirst().orElse(null);
            if(post==null)return false;source.remove(post);
            if(!delete)(restore?gramPosts:archivedGramPosts).add(post);
            else gramComments.removeIf(c->id.equals(c.postId));
        } else if("BLOCKPOST".equals(platform)) {
            var source=restore||delete?archivedBlockPosts:blockPosts;
            var post=source.stream().filter(p->id.equals(p.id)&&self.equals(p.authorUuid)).findFirst().orElse(null);
            if(post==null)return false;source.remove(post);
            if(!delete)(restore?blockPosts:archivedBlockPosts).add(post);
            else blockComments.removeIf(c->id.equals(c.postId));
        } else return false;
        removeUnreferencedImages();changed();return true;
    }

    public synchronized String addGramPost(UUID playerId, String playerName, String caption, byte[] imageData) {
        return addGramPost(playerId, playerName, caption, imageData, 0L);
    }

    public synchronized String addGramPost(UUID playerId, String playerName, String caption, byte[] imageData, long gameTime) {
        if(!hasPostCapacity(playerId,"BLOCKGRAM"))return "";
        if (imageData == null || imageData.length == 0) return "";
        rememberPlayer(playerId, playerName);
        StoredGramPost post = new StoredGramPost();
        post.id = "gram-" + UUID.randomUUID();
        post.authorUuid = playerId.toString();
        post.authorName = safeName(playerName);
        post.caption = trimText(caption, 300);
        post.imageRef = storeImage(imageData);
        post.createdAt = System.currentTimeMillis();
        post.createdGameTime = Math.max(0L, gameTime);
        gramPosts.add(post);
        changed();
        return post.imageRef;
    }

    public synchronized String addGramStory(UUID playerId, String playerName, byte[] imageData) {
        return addGramStory(playerId, playerName, imageData, 0L);
    }

    public synchronized String addGramStory(UUID playerId, String playerName, byte[] imageData, long gameTime) {
        if (imageData == null || imageData.length == 0) return "";
        rememberPlayer(playerId, playerName);
        StoredGramStory story = new StoredGramStory();
        story.id = "gram-story-" + UUID.randomUUID();
        story.authorUuid = playerId.toString();
        story.authorName = safeName(playerName);
        story.imageRef = storeImage(imageData);
        story.createdAt = System.currentTimeMillis();
        story.createdGameTime = Math.max(0L, gameTime);
        gramStories.add(story);

        List<StoredGramStory> ownStories = gramStories.stream()
                .filter(entry -> story.authorUuid.equals(entry.authorUuid))
                .sorted(Comparator.comparingLong(entry -> entry.createdAt))
                .toList();
        int removeCount = Math.max(0, ownStories.size() - 12);
        for (int i = 0; i < removeCount; i++) {
            String removeId = ownStories.get(i).id;
            gramStories.removeIf(entry -> removeId.equals(entry.id));
        }
        changed();
        return story.imageRef;
    }

    public synchronized String addBlockPost(UUID playerId, String playerName, String body, byte[] imageData) {
        return addBlockPost(playerId, playerName, body, imageData, 0L);
    }

    public synchronized String addBlockPost(UUID playerId, String playerName, String body, byte[] imageData, long gameTime) {
        if(!hasPostCapacity(playerId,"BLOCKPOST"))return "";
        String safeBody = trimText(body, 300);
        if (safeBody.isBlank() && (imageData == null || imageData.length == 0)) return "";
        rememberPlayer(playerId, playerName);
        StoredBlockPost post = new StoredBlockPost();
        post.id = "blockpost-" + UUID.randomUUID();
        post.authorUuid = playerId.toString();
        post.authorName = safeName(playerName);
        post.handle = handleFor(playerName);
        post.body = safeBody;
        post.imageRef = imageData == null || imageData.length == 0 ? "" : storeImage(imageData);
        post.createdAt = System.currentTimeMillis();
        post.createdGameTime = Math.max(0L, gameTime);
        blockPosts.add(post);
        changed();
        return post.imageRef;
    }

    /** Deletes a Lumi/BlockGram post only when the requesting player owns it. */
    public synchronized boolean deleteGramPost(UUID actorId, String postId) {
        if (actorId == null || postId == null || postId.isBlank()) return false;
        String actor = actorId.toString();
        StoredGramPost target = findGramPost(postId);
        if (target == null || !actor.equals(target.authorUuid)) return false;

        gramPosts.remove(target);
        gramComments.removeIf(comment -> postId.equals(comment.postId));
        notifications.removeIf(notification -> postId.equals(notification.postId));
        removeUnreferencedImages();
        changed();
        return true;
    }

    /** Deletes an X/BlockPost entry only when the requesting player owns it. */
    public synchronized boolean deleteBlockPost(UUID actorId, String postId) {
        if (actorId == null || postId == null || postId.isBlank()) return false;
        String actor = actorId.toString();
        StoredBlockPost target = findBlockPost(postId);
        if (target == null || !actor.equals(target.authorUuid)) return false;

        blockPosts.remove(target);
        blockComments.removeIf(comment -> postId.equals(comment.postId));
        notifications.removeIf(notification -> postId.equals(notification.postId));
        blockPostLikeNotificationKeys.removeIf(key -> key.endsWith("|" + postId));
        removeUnreferencedImages();
        changed();
        return true;
    }

    public synchronized boolean toggleGramLike(UUID actorId, String actorName, String postId) {
        StoredGramPost post = findGramPost(postId);
        if (post == null) return false;
        String actor = actorId.toString();
        boolean liked;
        if (post.likedBy.remove(actor)) {
            liked = false;
        } else {
            post.likedBy.add(actor);
            liked = true;
        }
        rememberPlayer(actorId, actorName);
        if (liked && !actor.equals(post.authorUuid)) {
            addNotification("BLOCKGRAM", "LIKE", actor, actorName, post.authorUuid,
                    safeName(actorName) + " gönderini beğendi", post.id);
        }
        changed();
        return true;
    }

    public synchronized boolean addGramComment(UUID actorId, String actorName, String postId, String body) {
        StoredGramPost post = findGramPost(postId);
        String safeBody = trimText(body, 280);
        if (post == null || safeBody.isBlank()) return false;
        rememberPlayer(actorId, actorName);
        StoredGramComment comment = new StoredGramComment();
        comment.id = "gram-comment-" + UUID.randomUUID();
        comment.postId = post.id;
        comment.authorUuid = actorId.toString();
        comment.authorName = safeName(actorName);
        comment.body = safeBody;
        comment.createdAt = System.currentTimeMillis();
        gramComments.add(comment);
        if (!comment.authorUuid.equals(post.authorUuid)) {
            addNotification("BLOCKGRAM", "COMMENT", comment.authorUuid, comment.authorName, post.authorUuid,
                    comment.authorName + " gönderine yorum yaptı", post.id);
        }
        changed();
        return true;
    }

    public synchronized boolean toggleGramCommentLike(UUID actorId, String actorName, String commentId) {
        StoredGramComment comment = findGramComment(commentId);
        if (comment == null) return false;
        String actor = actorId.toString();
        boolean liked;
        if (comment.likedBy.remove(actor)) {
            liked = false;
        } else {
            comment.likedBy.add(actor);
            liked = true;
        }
        rememberPlayer(actorId, actorName);
        if (liked && !actor.equals(comment.authorUuid)) {
            addNotification("BLOCKGRAM", "COMMENT_LIKE", actor, actorName, comment.authorUuid,
                    safeName(actorName) + " yorumunu beğendi", comment.postId);
        }
        changed();
        return true;
    }

    public synchronized boolean recordGramShare(UUID actorId, String actorName, String postId) {
        StoredGramPost post = findGramPost(postId);
        if (post == null) return false;
        rememberPlayer(actorId, actorName);
        if (!actorId.toString().equals(post.authorUuid)) {
            addNotification("BLOCKGRAM", "SHARE", actorId.toString(), actorName, post.authorUuid,
                    safeName(actorName) + " gönderini paylaştı", post.id);
            changed();
        }
        return true;
    }

    public synchronized boolean toggleBlockPostLike(UUID actorId, String actorName, String postId) {
        StoredBlockPost post = findBlockPost(postId);
        if (post == null) return false;
        String actor = actorId.toString();
        boolean liked;
        if (post.likedBy.remove(actor)) {
            liked = false;
        } else {
            post.likedBy.add(actor);
            liked = true;
        }
        rememberPlayer(actorId, actorName);
        if (liked && !actor.equals(post.authorUuid)) {
            addNotification("BLOCKPOST", "LIKE", actor, actorName, post.authorUuid,
                    safeName(actorName) + " gönderini beğendi", post.id);
        }
        changed();
        return true;
    }

    public synchronized boolean toggleBlockPostRepost(UUID actorId, String actorName, String postId) {
        StoredBlockPost post = findBlockPost(postId);
        if (post == null) return false;
        String actor = actorId.toString();
        boolean reposted;
        if (post.repostedBy.remove(actor)) {
            reposted = false;
        } else {
            post.repostedBy.add(actor);
            reposted = true;
        }
        rememberPlayer(actorId, actorName);
        if (reposted && !actor.equals(post.authorUuid)) {
            addNotification("BLOCKPOST", "REPOST", actor, actorName, post.authorUuid,
                    safeName(actorName) + " gönderini yeniden paylaştı", post.id);
        }
        changed();
        return true;
    }

    public synchronized boolean addBlockPostComment(UUID actorId, String actorName, String postId, String parentCommentId, String body) {
        StoredBlockPost post = findBlockPost(postId);
        String safeBody = trimText(body, 280);
        if (post == null || safeBody.isBlank()) return false;
        String parent = parentCommentId == null ? "" : parentCommentId.trim();
        StoredBlockComment parentComment = parent.isBlank() ? null : findBlockComment(parent);
        if (parentComment != null && !post.id.equals(parentComment.postId)) parentComment = null;

        rememberPlayer(actorId, actorName);
        StoredBlockComment comment = new StoredBlockComment();
        comment.id = "blockpost-comment-" + UUID.randomUUID();
        comment.postId = post.id;
        comment.parentCommentId = parentComment == null ? "" : parentComment.id;
        comment.authorUuid = actorId.toString();
        comment.authorName = safeName(actorName);
        comment.handle = handleFor(actorName);
        comment.body = safeBody;
        comment.createdAt = System.currentTimeMillis();
        blockComments.add(comment);

        String target = parentComment != null ? parentComment.authorUuid : post.authorUuid;
        if (!comment.authorUuid.equals(target)) {
            String type = parentComment == null ? "COMMENT" : "REPLY";
            String text = parentComment == null
                    ? comment.authorName + " gönderine yanıt verdi"
                    : comment.authorName + " yorumuna yanıt verdi";
            addNotification("BLOCKPOST", type, comment.authorUuid, comment.authorName, target, text, post.id);
        }
        changed();
        return true;
    }

    public synchronized boolean toggleBlockPostCommentLike(UUID actorId, String actorName, String commentId) {
        StoredBlockComment comment = findBlockComment(commentId);
        if (comment == null) return false;
        String actor = actorId.toString();
        boolean liked;
        if (comment.likedBy.remove(actor)) {
            liked = false;
        } else {
            comment.likedBy.add(actor);
            liked = true;
        }
        rememberPlayer(actorId, actorName);
        if (liked && !actor.equals(comment.authorUuid)) {
            addNotification("BLOCKPOST", "COMMENT_LIKE", actor, actorName, comment.authorUuid,
                    safeName(actorName) + " yanıtını beğendi", comment.postId);
        }
        changed();
        return true;
    }

    public synchronized boolean sendFollowRequest(String platform, UUID fromId, String fromName, UUID toId, String toName) {
        String safePlatform = platform(platform);
        if (fromId.equals(toId)) return false;
        rememberPlayer(fromId, fromName);
        rememberPlayer(toId, toName);
        StoredFollow existing = latestFollow(safePlatform, fromId.toString(), toId.toString());
        if (existing != null && ("PENDING".equals(existing.status) || "ACCEPTED".equals(existing.status))) return false;

        StoredFollow follow = new StoredFollow();
        follow.id = "follow-" + UUID.randomUUID();
        follow.platform = safePlatform;
        follow.fromUuid = fromId.toString();
        follow.fromName = safeName(fromName);
        follow.toUuid = toId.toString();
        follow.toName = safeName(toName);
        follow.status = "PENDING";
        follow.createdAt = System.currentTimeMillis();
        follows.add(follow);
        addNotification(safePlatform, "FOLLOW_REQUEST", fromId.toString(), fromName, toId.toString(),
                safeName(fromName) + " seni takip etmek istiyor", "");
        changed();
        return true;
    }

    public synchronized boolean updateFollowRequest(String platform, UUID actorId, String actorName, String requestId, boolean accept) {
        String safePlatform = platform(platform);
        for (StoredFollow follow : follows) {
            if (!requestId.equals(follow.id) || !safePlatform.equals(follow.platform) || !actorId.toString().equals(follow.toUuid)
                    || !"PENDING".equals(follow.status)) continue;
            follow.status = accept ? "ACCEPTED" : "DECLINED";
            follow.toName = safeName(actorName);
            if (accept) {
                addNotification(safePlatform, "FOLLOW_ACCEPTED", actorId.toString(), actorName, follow.fromUuid,
                        safeName(actorName) + " takip isteğini kabul etti", "");
            }
            changed();
            return true;
        }
        return false;
    }

    public synchronized boolean updateFollowRequestAndFollowBack(
            String platform, UUID actorId, String actorName, String requestId
    ) {
        String safePlatform = platform(platform);
        for (StoredFollow follow : follows) {
            if (!requestId.equals(follow.id) || !safePlatform.equals(follow.platform)
                    || !actorId.toString().equals(follow.toUuid) || !"PENDING".equals(follow.status)) {
                continue;
            }

            follow.status = "ACCEPTED";
            follow.toName = safeName(actorName);
            addNotification(safePlatform, "FOLLOW_ACCEPTED", actorId.toString(), actorName, follow.fromUuid,
                    safeName(actorName) + " takip isteğini kabul etti", "");

            StoredFollow reverse = latestFollow(safePlatform, actorId.toString(), follow.fromUuid);
            if (reverse == null || (!"PENDING".equals(reverse.status) && !"ACCEPTED".equals(reverse.status))) {
                StoredFollow followBack = new StoredFollow();
                followBack.id = "follow-" + UUID.randomUUID();
                followBack.platform = safePlatform;
                followBack.fromUuid = actorId.toString();
                followBack.fromName = safeName(actorName);
                followBack.toUuid = follow.fromUuid;
                followBack.toName = safeName(follow.fromName);
                followBack.status = "PENDING";
                followBack.createdAt = System.currentTimeMillis();
                follows.add(followBack);
            }
            changed();
            return true;
        }
        return false;
    }

    public synchronized boolean removeFollow(String platform, UUID actorId, UUID targetId) {
        String safePlatform = platform(platform);
        StoredFollow latest = latestFollow(safePlatform, actorId.toString(), targetId.toString());
        if (latest == null || !("ACCEPTED".equals(latest.status) || "PENDING".equals(latest.status))) return false;
        latest.status = "REMOVED";
        changed();
        return true;
    }

    public synchronized boolean markNotificationsRead(String platform, UUID playerId) {
        String safePlatform = platform(platform);
        boolean changed = false;
        for (StoredNotification notification : notifications) {
            if (safePlatform.equals(notification.platform)
                    && playerId.toString().equals(notification.targetUuid)
                    && !notification.read) {
                notification.read = true;
                changed = true;
            }
        }
        if (changed) changed();
        return changed;
    }

    public synchronized boolean clearNotifications(String platform, UUID playerId) {
        String safePlatform = platform(platform);
        String target = playerId.toString();
        boolean removed = notifications.removeIf(notification -> safePlatform.equals(notification.platform)
                && target.equals(notification.targetUuid));
        if (removed) changed();
        return removed;
    }

    public synchronized boolean markGramStoryViewed(UUID viewerId, String storyId) {
        if (viewerId == null || storyId == null || storyId.isBlank()) return false;
        for (StoredGramStory story : gramStories) {
            if (!storyId.equals(story.id)) continue;
            if (!canViewStory(viewerId, story.authorUuid)) return false;
            if (story.seenBy == null) story.seenBy = new LinkedHashSet<>();
            if (story.seenBy.add(viewerId.toString())) { changed(); return true; }
            return false;
        }
        return false;
    }

    public synchronized void setTinderBio(UUID playerId, String playerName, String bio) {
        rememberPlayer(playerId, playerName);
        profiles.get(playerId.toString()).tinderBio = trimText(bio, 180);
        changed();
    }

    public synchronized void setTinderEnabled(UUID playerId, String playerName, boolean enabled) {
        rememberPlayer(playerId, playerName);
        profiles.get(playerId.toString()).tinderEnabled = enabled;
        changed();
    }

    public synchronized void setTinderSettings(
            UUID playerId,
            String playerName,
            int age,
            String gender,
            String showGender,
            int minAge,
            int maxAge
    ) {
        rememberPlayer(playerId, playerName);
        StoredProfile profile = profiles.get(playerId.toString());
        profile.tinderAge = clampAge(age);
        profile.tinderGender = normalizeGender(gender);
        profile.tinderShowGender = normalizeShowGender(showGender);
        int safeMin = clampAge(minAge);
        int safeMax = clampAge(maxAge);
        if (safeMin > safeMax) {
            int swap = safeMin;
            safeMin = safeMax;
            safeMax = swap;
        }
        profile.tinderMinAge = safeMin;
        profile.tinderMaxAge = safeMax;
        changed();
    }

    public synchronized void clearTinderPhoto(UUID playerId, String playerName) {
        rememberPlayer(playerId, playerName);
        profiles.get(playerId.toString()).tinderPhotoRef = "";
        removeUnreferencedImages();
        changed();
    }

    /** Records a Tinder-style swipe and returns true when it creates/keeps a mutual match. */
    public synchronized boolean swipeTinder(UUID actorId, String actorName, UUID targetId, boolean like) {
        if (actorId == null || targetId == null || actorId.equals(targetId)) return false;
        rememberPlayer(actorId, actorName);
        String actor = actorId.toString();
        String target = targetId.toString();
        if (!profiles.containsKey(target) || !profiles.get(target).tinderEnabled) return false;
        StoredProfile actorProfile = profiles.get(actor);
        StoredProfile targetProfile = profiles.get(target);
        if (!tinderProfilesCompatible(actorProfile, targetProfile)) return false;
        Set<String> likes = tinderLikes.computeIfAbsent(actor, ignored -> new LinkedHashSet<>());
        Set<String> passes = tinderPasses.computeIfAbsent(actor, ignored -> new LinkedHashSet<>());
        if (like) {
            passes.remove(target);
            likes.add(target);
        } else {
            likes.remove(target);
            passes.add(target);
        }
        boolean match = like && tinderLikes.getOrDefault(target, Set.of()).contains(actor);
        if (match) {
            String targetName = displayNameForUuid(target);
            addNotification("TINDER", "MATCH", target, targetName, actor, "Yeni bir eşleşmen var: " + targetName, "");
            addNotification("TINDER", "MATCH", actor, safeName(actorName), target, "Yeni bir eşleşmen var: " + safeName(actorName), "");
        }
        changed();
        return match;
    }

    public synchronized void resetTinderPasses(UUID playerId) {
        if (playerId == null) return;
        tinderPasses.remove(playerId.toString());
        changed();
    }

    public synchronized boolean isTinderMatch(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) return false;
        String a = first.toString();
        String b = second.toString();
        return tinderLikes.getOrDefault(a, Set.of()).contains(b)
                && tinderLikes.getOrDefault(b, Set.of()).contains(a);
    }

    public synchronized void setBlockPostBio(UUID playerId, String playerName, String bio) {
        rememberPlayer(playerId, playerName);
        profiles.get(playerId.toString()).blockPostBio = trimText(bio, 160);
        changed();
    }

    public synchronized void setBlockGramBio(UUID playerId, String playerName, String bio) {
        rememberPlayer(playerId, playerName);
        profiles.get(playerId.toString()).blockGramBio = trimText(bio, 160);
        changed();
    }

    public synchronized void clearBlockGramPhoto(UUID playerId, String playerName) {
        rememberPlayer(playerId, playerName);
        profiles.get(playerId.toString()).blockGramPhotoRef = "";
        removeUnreferencedImages();
        changed();
    }

    public synchronized String setProfileImage(UUID playerId, String playerName, String kind, byte[] imageData) {
        if (imageData == null || imageData.length == 0) return "";
        String safeKind = kind == null ? "" : kind.toUpperCase(Locale.ROOT);
        if (!Set.of("GRAM_PROFILE", "POST_AVATAR", "POST_COVER", "TINDER_PROFILE").contains(safeKind)) return "";
        rememberPlayer(playerId, playerName);
        String imageRef = storeImage(imageData);
        StoredProfile profile = profiles.get(playerId.toString());
        switch (safeKind) {
            case "GRAM_PROFILE" -> profile.blockGramPhotoRef = imageRef;
            case "POST_AVATAR" -> profile.blockPostAvatarRef = imageRef;
            case "POST_COVER" -> profile.blockPostCoverRef = imageRef;
            case "TINDER_PROFILE" -> profile.tinderPhotoRef = imageRef;
            default -> { return ""; }
        }
        removeUnreferencedImages();
        changed();
        return imageRef;
    }

    private String displayNameForUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) return "Player";
        StoredProfile profile = profiles.get(uuid);
        if (profile != null && profile.playerName != null && !profile.playerName.isBlank()) return profile.playerName;
        return "Player";
    }

    public synchronized PhoneSocialStateData toClientState(UUID viewerId) {
        return toClientState(viewerId, Long.MAX_VALUE);
    }

    public synchronized PhoneSocialStateData toClientState(UUID viewerId, long worldTime) {
        expireTimedContent(worldTime);
        PhoneSocialStateData state = new PhoneSocialStateData();
        state.revision = revision;
        if(viewerId!=null) {
            String self=viewerId.toString();
            for(var p:archivedGramPosts)if(self.equals(p.authorUuid))state.archivedPosts.add(new PhoneSocialStateData.ArchivedPostEntry(p.id,"BLOCKGRAM",p.caption,p.imageRef,p.createdAt));
            for(var p:archivedBlockPosts)if(self.equals(p.authorUuid))state.archivedPosts.add(new PhoneSocialStateData.ArchivedPostEntry(p.id,"BLOCKPOST",p.body,p.imageRef,p.createdAt));
        }
        gramPosts.stream().sorted(Comparator.comparingLong((StoredGramPost post) -> post.createdAt).reversed())
                .forEach(post -> state.blockGramPosts.add(new PhoneSocialStateData.GramPostEntry(
                        post.id, post.authorUuid, post.authorName, post.caption, post.imageRef, post.createdAt,
                        isFeedVisible(post.createdGameTime, worldTime), new ArrayList<>(post.likedBy))));
        gramStories.stream()
                .filter(story -> canViewStory(viewerId, story.authorUuid))
                .filter(story -> isStoryLive(story.createdGameTime, worldTime))
                .sorted(Comparator.comparingLong((StoredGramStory story) -> story.createdAt).reversed())
                .forEach(story -> {
                    boolean ownStory = viewerId != null && viewerId.toString().equals(story.authorUuid);
                    List<String> seenByNames = ownStory && story.seenBy != null
                            ? story.seenBy.stream()
                            .filter(id -> !id.equals(story.authorUuid))
                            .map(this::displayNameForUuid)
                            .filter(name -> name != null && !name.isBlank())
                            .distinct()
                            .toList()
                            : List.of();
                    state.blockGramStories.add(new PhoneSocialStateData.GramStoryEntry(
                            story.id, story.authorUuid, story.authorName, story.imageRef, story.createdAt,
                            story.seenBy != null && viewerId != null && story.seenBy.contains(viewerId.toString()), seenByNames));
                });
        gramComments.stream().filter(comment->findGramPost(comment.postId)!=null).sorted(Comparator.comparingLong(comment -> comment.createdAt))
                .forEach(comment -> state.blockGramComments.add(new PhoneSocialStateData.GramCommentEntry(
                        comment.id, comment.postId, comment.authorUuid, comment.authorName, comment.body,
                        comment.createdAt, new ArrayList<>(comment.likedBy))));
        blockPosts.stream().sorted(Comparator.comparingLong((StoredBlockPost post) -> post.createdAt).reversed())
                .forEach(post -> state.blockPostPosts.add(new PhoneSocialStateData.BlockPostEntry(
                        post.id, post.authorUuid, post.authorName, post.handle, post.body, post.imageRef, post.createdAt,
                        isFeedVisible(post.createdGameTime, worldTime), new ArrayList<>(post.likedBy), new ArrayList<>(post.repostedBy))));
        blockComments.stream().filter(comment->findBlockPost(comment.postId)!=null).sorted(Comparator.comparingLong(comment -> comment.createdAt))
                .forEach(comment -> state.blockPostComments.add(new PhoneSocialStateData.BlockPostCommentEntry(
                        comment.id, comment.postId, comment.parentCommentId, comment.authorUuid, comment.authorName,
                        comment.handle, comment.body, comment.createdAt, new ArrayList<>(comment.likedBy))));
        follows.stream().sorted(Comparator.comparingLong((StoredFollow follow) -> follow.createdAt).reversed())
                .forEach(follow -> state.follows.add(new PhoneSocialStateData.FollowEntry(
                        follow.id, follow.platform, follow.fromUuid, follow.fromName, follow.toUuid, follow.toName,
                        follow.status, follow.createdAt)));
        String viewerUuid = viewerId == null ? "" : viewerId.toString();
        notifications.stream()
                // Notifications are private. Sending every player's notification list to every
                // client wasted bandwidth and exposed metadata that the UI later filtered anyway.
                .filter(notification -> viewerId == null || viewerUuid.equals(notification.targetUuid))
                .sorted(Comparator.comparingLong((StoredNotification notification) -> notification.createdAt).reversed())
                .forEach(notification -> state.notifications.add(new PhoneSocialStateData.NotificationEntry(
                        notification.id, notification.platform, notification.type, notification.actorUuid,
                        notification.actorName, notification.targetUuid, notification.text, notification.postId,
                        notification.createdAt, notification.read)));
        profiles.values().stream().sorted(Comparator.comparing(profile -> profile.playerName.toLowerCase(Locale.ROOT)))
                .forEach(profile -> state.profiles.add(new PhoneSocialStateData.ProfileEntry(
                        profile.playerUuid, profile.playerName, profile.blockGramBio, profile.blockGramPhotoRef,
                        profile.blockPostBio, profile.blockPostAvatarRef, profile.blockPostCoverRef,
                        profile.tinderBio, profile.tinderPhotoRef, profile.tinderEnabled,
                        profile.tinderAge, profile.tinderGender, profile.tinderShowGender,
                        profile.tinderMinAge, profile.tinderMaxAge, profile.roleBadge)));
        if (viewerId != null) {
            String viewer = viewerId.toString();
            state.tinderLikedUuids.addAll(tinderLikes.getOrDefault(viewer, Set.of()));
            state.tinderPassedUuids.addAll(tinderPasses.getOrDefault(viewer, Set.of()));
            for (String liked : tinderLikes.getOrDefault(viewer, Set.of())) {
                if (tinderLikes.getOrDefault(liked, Set.of()).contains(viewer)) state.tinderMatchUuids.add(liked);
            }
        }
        return state;
    }

    public synchronized byte[] imageBytes(String ref) {
        if (ref == null || ref.isBlank() || assets == null) return null;
        return assets.getImage(ref);
    }

    public synchronized PhoneSharedPostData sharedGramPost(String postId) {
        for (StoredGramPost post : gramPosts) {
            if (post.id.equals(postId)) {
                return new PhoneSharedPostData(post.id, post.authorName, post.caption,
                        post.imageRef.isBlank() ? "" : "social:" + post.imageRef);
            }
        }
        return null;
    }

    public synchronized Map<String, byte[]> imagesForState(PhoneSocialStateData state) {
        return imagesForState(state, null);
    }

    public synchronized Map<String, byte[]> imagesForState(PhoneSocialStateData state, UUID viewerId) {
        Map<String, byte[]> result = new HashMap<>();
        if (state == null) return result;
        String viewer = viewerId == null ? "" : viewerId.toString();
        // Network optimization: old profile media is not resent to every player on
        // every full sync. Feed-visible media + the viewer's own history is sent now;
        // another player's older profile media is fetched on demand when their profile opens.
        for (PhoneSocialStateData.GramPostEntry post : state.blockGramPosts) {
            if (post.feedVisible || viewer.equals(post.authorUuid)) addDecodedImage(result, post.imageRef);
        }
        for (PhoneSocialStateData.GramStoryEntry story : state.blockGramStories) addDecodedImage(result, story.imageRef);
        for (PhoneSocialStateData.BlockPostEntry post : state.blockPostPosts) {
            if (post.feedVisible || viewer.equals(post.authorUuid)) addDecodedImage(result, post.imageRef);
        }
        for (PhoneSocialStateData.ProfileEntry profile : state.profiles) {
            addDecodedImage(result, profile.blockGramPhotoRef);
            addDecodedImage(result, profile.blockPostAvatarRef);
            addDecodedImage(result, profile.blockPostCoverRef);
            addDecodedImage(result, profile.tinderPhotoRef);
        }
        return result;
    }

    public synchronized Map<String, byte[]> imagesForProfile(UUID targetId) {
        Map<String, byte[]> result = new HashMap<>();
        if (targetId == null) return result;
        String target = targetId.toString();
        for (StoredGramPost post : gramPosts) if (target.equals(post.authorUuid)) addDecodedImage(result, post.imageRef);
        for (StoredBlockPost post : blockPosts) if (target.equals(post.authorUuid)) addDecodedImage(result, post.imageRef);
        StoredProfile profile = profiles.get(target);
        if (profile != null) {
            addDecodedImage(result, profile.blockGramPhotoRef);
            addDecodedImage(result, profile.blockPostAvatarRef);
            addDecodedImage(result, profile.blockPostCoverRef);
            addDecodedImage(result, profile.tinderPhotoRef);
        }
        return result;
    }

    /** Returns the player UUID that owns the social object an interaction targets. */
    public synchronized String interactionOwnerUuid(String platformRaw, String typeRaw, String targetId) {
        String platform = platformRaw == null ? "" : platformRaw.trim().toUpperCase(Locale.ROOT);
        String type = typeRaw == null ? "" : typeRaw.trim().toUpperCase(Locale.ROOT);
        if ("BLOCKGRAM".equals(platform)) {
            if ("COMMENT_LIKE".equals(type)) {
                StoredGramComment comment = findGramComment(targetId);
                return comment == null ? "" : comment.authorUuid;
            }
            StoredGramPost post = findGramPost(targetId);
            return post == null ? "" : post.authorUuid;
        }
        if ("BLOCKPOST".equals(platform)) {
            if ("COMMENT_LIKE".equals(type)) {
                StoredBlockComment comment = findBlockComment(targetId);
                return comment == null ? "" : comment.authorUuid;
            }
            StoredBlockPost post = findBlockPost(targetId);
            return post == null ? "" : post.authorUuid;
        }
        return "";
    }

    public synchronized String gramStoryOwnerUuid(String storyId) {
        if (storyId == null || storyId.isBlank()) return "";
        for (StoredGramStory story : gramStories) {
            if (storyId.equals(story.id)) return story.authorUuid == null ? "" : story.authorUuid;
        }
        return "";
    }

    /** Blocking is global across MPhone social apps: remove direct social relationships immediately. */
    public synchronized boolean removeSocialRelationsBetween(UUID firstId, UUID secondId) {
        if (firstId == null || secondId == null || firstId.equals(secondId)) return false;
        String first = firstId.toString();
        String second = secondId.toString();
        boolean changedAny = follows.removeIf(follow ->
                (first.equals(follow.fromUuid) && second.equals(follow.toUuid))
                        || (second.equals(follow.fromUuid) && first.equals(follow.toUuid)));
        changedAny |= notifications.removeIf(notification ->
                (first.equals(notification.actorUuid) && second.equals(notification.targetUuid))
                        || (second.equals(notification.actorUuid) && first.equals(notification.targetUuid)));
        changedAny |= removeFromSetMap(tinderLikes, first, second);
        changedAny |= removeFromSetMap(tinderLikes, second, first);
        changedAny |= removeFromSetMap(tinderPasses, first, second);
        changedAny |= removeFromSetMap(tinderPasses, second, first);
        if (changedAny) changed();
        return changedAny;
    }

    private static boolean removeFromSetMap(Map<String, Set<String>> map, String owner, String target) {
        Set<String> values = map.get(owner);
        if (values == null) return false;
        boolean removed = values.remove(target);
        if (values.isEmpty()) map.remove(owner);
        return removed;
    }

    private StoredGramPost findGramPost(String id) {
        if (id == null) return null;
        for (StoredGramPost post : gramPosts) if (id.equals(post.id)) return post;
        return null;
    }

    private StoredGramComment findGramComment(String id) {
        if (id == null) return null;
        for (StoredGramComment comment : gramComments) if (id.equals(comment.id)) return comment;
        return null;
    }

    private StoredBlockPost findBlockPost(String id) {
        if (id == null) return null;
        for (StoredBlockPost post : blockPosts) if (id.equals(post.id)) return post;
        return null;
    }

    private StoredBlockComment findBlockComment(String id) {
        if (id == null) return null;
        for (StoredBlockComment comment : blockComments) if (id.equals(comment.id)) return comment;
        return null;
    }

    private StoredFollow latestFollow(String platform, String fromUuid, String toUuid) {
        return follows.stream()
                .filter(follow -> platform.equals(follow.platform) && fromUuid.equals(follow.fromUuid) && toUuid.equals(follow.toUuid))
                .max(Comparator.comparingLong(follow -> follow.createdAt))
                .orElse(null);
    }

    public synchronized boolean isAcceptedFollow(String platform, UUID fromId, UUID toId) {
        if (fromId == null || toId == null) return false;
        StoredFollow follow = latestFollow(platform(platform), fromId.toString(), toId.toString());
        return follow != null && "ACCEPTED".equals(follow.status);
    }

    public synchronized boolean canViewGramStory(UUID viewerId, UUID authorId) {
        return authorId != null && canViewStory(viewerId, authorId.toString());
    }

    private boolean canViewStory(UUID viewerId, String authorUuid) {
        if (viewerId == null || authorUuid == null || authorUuid.isBlank()) return false;
        String viewer = viewerId.toString();
        if (viewer.equals(authorUuid)) return true;
        StoredFollow follow = latestFollow("BLOCKGRAM", viewer, authorUuid);
        return follow != null && "ACCEPTED".equals(follow.status);
    }

    private static boolean isFeedVisible(long createdWorldTime, long currentWorldTime) {
        if (currentWorldTime == Long.MAX_VALUE || createdWorldTime <= 0L) return true;
        long age = Math.max(0L, currentWorldTime - createdWorldTime);
        return age <= POST_LIFETIME_TICKS;
    }

    private static boolean isStoryLive(long createdWorldTime, long currentWorldTime) {
        if (currentWorldTime == Long.MAX_VALUE || createdWorldTime <= 0L) return true;
        long age = Math.max(0L, currentWorldTime - createdWorldTime);
        return age <= STORY_LIFETIME_TICKS;
    }

    /**
     * Stories expire permanently after two Minecraft days. Lumi and X posts leave their home
     * feeds after four Minecraft days but remain in profile history until the owner deletes
     * them (up to 50 posts per player). This intentionally uses the overworld clock, because
     * /time add changes that clock but does not advance Level#getGameTime().
     */
    private void expireTimedContent(long currentWorldTime) {
        if (currentWorldTime == Long.MAX_VALUE || currentWorldTime < 0L) return;
        boolean modified = false;

        for (StoredGramPost post : gramPosts) {
            if (post.createdGameTime <= 0L) { post.createdGameTime = currentWorldTime; modified = true; }
        }
        for (StoredBlockPost post : blockPosts) {
            if (post.createdGameTime <= 0L) { post.createdGameTime = currentWorldTime; modified = true; }
        }
        for (StoredGramStory story : gramStories) {
            if (story.createdGameTime <= 0L) { story.createdGameTime = currentWorldTime; modified = true; }
        }

        Set<String> removedStories = new LinkedHashSet<>();
        // Lumi and BlockPost entries are intentionally retained for profile history. Their
        // home-feed visibility is calculated by isFeedVisible(...) during sync.
        gramStories.removeIf(story -> {
            boolean expired = currentWorldTime - story.createdGameTime > STORY_LIFETIME_TICKS;
            if (expired) removedStories.add(story.id);
            return expired;
        });

        if (!removedStories.isEmpty()) modified = true;
        if (modified) {
            notifications.removeIf(notification -> removedStories.contains(notification.postId));
            removeUnreferencedImages();
            changed();
        }
    }

    private void removeUnreferencedImages() {
        if (assets == null || recoveryRequired || recoveryBackup != null) return;
        assets.retainOnly(referencedImageRefs());
    }

    private Set<String> referencedImageRefs() {
        Set<String> referenced = new LinkedHashSet<>();
        for(var p:archivedGramPosts)if(p.imageRef!=null&&!p.imageRef.isBlank())referenced.add(p.imageRef);
        for(var p:archivedBlockPosts)if(p.imageRef!=null&&!p.imageRef.isBlank())referenced.add(p.imageRef);
        for (StoredGramPost post : gramPosts) if (post.imageRef != null && !post.imageRef.isBlank()) referenced.add(post.imageRef);
        for (StoredGramStory story : gramStories) if (story.imageRef != null && !story.imageRef.isBlank()) referenced.add(story.imageRef);
        for (StoredBlockPost post : blockPosts) if (post.imageRef != null && !post.imageRef.isBlank()) referenced.add(post.imageRef);
        for (StoredProfile profile : profiles.values()) {
            if (profile.blockGramPhotoRef != null && !profile.blockGramPhotoRef.isBlank()) referenced.add(profile.blockGramPhotoRef);
            if (profile.blockPostAvatarRef != null && !profile.blockPostAvatarRef.isBlank()) referenced.add(profile.blockPostAvatarRef);
            if (profile.blockPostCoverRef != null && !profile.blockPostCoverRef.isBlank()) referenced.add(profile.blockPostCoverRef);
            if (profile.tinderPhotoRef != null && !profile.tinderPhotoRef.isBlank()) referenced.add(profile.tinderPhotoRef);
        }
        return referenced;
    }





    private void addNotification(String platform, String type, String actorUuid, String actorName, String targetUuid, String text, String postId) {
        if (targetUuid == null || targetUuid.isBlank()) return;
        String safePlatform = platform(platform);
        String safeType = type == null || type.isBlank() ? "INFO" : type;
        String safeActor = actorUuid == null ? "" : actorUuid;
        String safePostId = postId == null ? "" : postId;

        // A BlockPost like notification is unique per actor + target + post and this
        // memory survives notification clearing/restarts, so unlike/like cannot spam.
        if ("BLOCKPOST".equals(safePlatform) && "LIKE".equals(safeType) && !safePostId.isBlank()) {
            if (!blockPostLikeNotificationKeys.add(likeNotificationKey(safeActor, targetUuid, safePostId))) return;
        }

        StoredNotification notification = new StoredNotification();
        notification.id = "social-notification-" + UUID.randomUUID();
        notification.platform = safePlatform;
        notification.type = safeType;
        notification.actorUuid = safeActor;
        notification.actorName = safeName(actorName);
        notification.targetUuid = targetUuid;
        notification.text = trimText(text, 220);
        notification.postId = safePostId;
        notification.createdAt = System.currentTimeMillis();
        notifications.add(notification);
    }

    private static String likeNotificationKey(String actorUuid, String targetUuid, String postId) {
        return (actorUuid == null ? "" : actorUuid) + "|"
                + (targetUuid == null ? "" : targetUuid) + "|"
                + (postId == null ? "" : postId);
    }

    private void addDecodedImage(Map<String, byte[]> result, String ref) {
        if (ref == null || ref.isBlank() || result.containsKey(ref) || assets == null) return;
        byte[] bytes = assets.getImage(ref);
        if (bytes != null && bytes.length > 0) result.put(ref, bytes);
    }

    private String storeImage(byte[] imageData) {
        if (assets == null || imageData == null || imageData.length == 0) return "";
        String ref = "social-" + UUID.randomUUID();
        assets.put(ref, imageData);
        return ref;
    }

    private void changed() {
        revision++;
        trim();
        setDirty();
    }

    private void normalizeLoadedData() {
        for (StoredGramPost post : gramPosts) if (post.likedBy == null) post.likedBy = new LinkedHashSet<>();
        for (StoredGramStory story : gramStories) if (story.seenBy == null) story.seenBy = new LinkedHashSet<>();
        for (StoredGramComment comment : gramComments) if (comment.likedBy == null) comment.likedBy = new LinkedHashSet<>();
        for (StoredBlockPost post : blockPosts) {
            if (post.likedBy == null) post.likedBy = new LinkedHashSet<>();
            if (post.repostedBy == null) post.repostedBy = new LinkedHashSet<>();
        }
        for (StoredBlockComment comment : blockComments) if (comment.likedBy == null) comment.likedBy = new LinkedHashSet<>();
        for (StoredFollow follow : follows) follow.platform = platform(follow.platform);
        for (StoredNotification notification : notifications) notification.platform = platform(notification.platform);
        for (StoredProfile profile : profiles.values()) {
            if (profile.roleBadge == null || profile.roleBadge.isBlank()) profile.roleBadge = "PL";
            if (profile.blockPostBio == null) profile.blockPostBio = "Minecraft oyuncusu • X kullanıcısı";
            boolean legacyTinderProfile = profile.tinderBio == null && profile.tinderPhotoRef == null;
            if (profile.tinderBio == null || profile.tinderBio.isBlank()) profile.tinderBio = "Minecraft oyuncusu • Yeni insanlarla tanışmaya açık.";
            if (profile.tinderPhotoRef == null) profile.tinderPhotoRef = "";
            if (legacyTinderProfile) profile.tinderEnabled = true;
            profile.tinderAge = clampAge(profile.tinderAge <= 0 ? 18 : profile.tinderAge);
            profile.tinderGender = normalizeGender(profile.tinderGender);
            profile.tinderShowGender = normalizeShowGender(profile.tinderShowGender);
            profile.tinderMinAge = clampAge(profile.tinderMinAge <= 0 ? 18 : profile.tinderMinAge);
            profile.tinderMaxAge = clampAge(profile.tinderMaxAge <= 0 ? 99 : profile.tinderMaxAge);
            if (profile.tinderMinAge > profile.tinderMaxAge) {
                int swap = profile.tinderMinAge;
                profile.tinderMinAge = profile.tinderMaxAge;
                profile.tinderMaxAge = swap;
            }
        }
        tinderLikes.replaceAll((key, value) -> value == null ? new LinkedHashSet<>() : new LinkedHashSet<>(value));
        tinderPasses.replaceAll((key, value) -> value == null ? new LinkedHashSet<>() : new LinkedHashSet<>(value));
    }

    private void trim() {
        while (gramStories.size() > MAX_GRAM_STORIES) gramStories.remove(0);
        // Posts are capped per profile instead of globally, so every player can retain
        // their own last 50 Lumi posts regardless of server population.
        while (gramComments.size() > MAX_GRAM_COMMENTS) gramComments.remove(0);
        while (blockComments.size() > MAX_BLOCKPOST_COMMENTS) blockComments.remove(0);
        while (notifications.size() > MAX_NOTIFICATIONS) notifications.remove(0);
        trimImages();
    }

    private void trimImages() {
        removeUnreferencedImages();
    }

    private String encodeJson() {
        Snapshot snapshot = new Snapshot();
        snapshot.revision = revision;
        snapshot.gramPosts = new ArrayList<>(gramPosts);
        snapshot.archivedGramPosts = new ArrayList<>(archivedGramPosts);
        snapshot.archivedBlockPosts = new ArrayList<>(archivedBlockPosts);
        snapshot.gramStories = new ArrayList<>(gramStories);
        snapshot.gramComments = new ArrayList<>(gramComments);
        snapshot.blockPosts = new ArrayList<>(blockPosts);
        snapshot.blockComments = new ArrayList<>(blockComments);
        snapshot.follows = new ArrayList<>(follows);
        snapshot.notifications = new ArrayList<>(notifications);
        snapshot.blockPostLikeNotificationKeys = new LinkedHashSet<>(blockPostLikeNotificationKeys);
        snapshot.profiles = new HashMap<>(profiles);
        snapshot.tinderLikes = copySetMap(tinderLikes);
        snapshot.tinderPasses = copySetMap(tinderPasses);
        return GSON.toJson(snapshot);
    }

    private static byte[] gzip(String json) {
        if (json == null || json.isEmpty()) return new byte[0];
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return out.toByteArray();
        } catch (IOException ignored) { return new byte[0]; }
    }

    private static String gunzip(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            gzip.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException ignored) { return ""; }
    }

    private static Map<String, Set<String>> copySetMap(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, value == null ? new LinkedHashSet<>() : new LinkedHashSet<>(value)));
        return copy;
    }

    private static int clampAge(int age) {
        return Math.max(18, Math.min(99, age));
    }

    private static String normalizeGender(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MALE", "FEMALE", "OTHER" -> normalized;
            default -> "UNSPECIFIED";
        };
    }

    private static String normalizeShowGender(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MALE", "FEMALE" -> normalized;
            default -> "EVERYONE";
        };
    }

    private static boolean tinderProfilesCompatible(StoredProfile viewer, StoredProfile candidate) {
        if (viewer == null || candidate == null) return false;
        if (!candidate.tinderEnabled) return false;
        int candidateAge = clampAge(candidate.tinderAge);
        int minAge = clampAge(viewer.tinderMinAge);
        int maxAge = clampAge(viewer.tinderMaxAge);
        if (minAge > maxAge) {
            int swap = minAge;
            minAge = maxAge;
            maxAge = swap;
        }
        if (candidateAge < minAge || candidateAge > maxAge) return false;
        return genderPreferenceAllows(viewer.tinderShowGender, candidate.tinderGender);
    }

    private static boolean genderPreferenceAllows(String preference, String gender) {
        String safePreference = normalizeShowGender(preference);
        if ("EVERYONE".equals(safePreference)) return true;
        return safePreference.equals(normalizeGender(gender));
    }

    private static String platform(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("BLOCKPOST".equals(normalized)) return "BLOCKPOST";
        if ("TINDER".equals(normalized)) return "TINDER";
        return "BLOCKGRAM";
    }

    private static String trimText(String value, int maxLength) {
        String safe = value == null ? "" : value.replace("\r", "").strip();
        return safe.length() > maxLength ? safe.substring(0, maxLength) : safe;
    }

    private static String safeName(String value) {
        return value == null || value.isBlank() ? "Player" : trimText(value, 64);
    }

    private static String handleFor(String name) {
        String safe = safeName(name).toLowerCase(Locale.ROOT)
                .replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i')
                .replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
                .replaceAll("[^a-z0-9_]", "");
        return "@" + (safe.isBlank() ? "player" : safe);
    }

    private static final class Snapshot {
        List<StoredGramPost> archivedGramPosts = new ArrayList<>();
        List<StoredBlockPost> archivedBlockPosts = new ArrayList<>();
        long revision;
        List<StoredGramPost> gramPosts = new ArrayList<>();
        List<StoredGramStory> gramStories = new ArrayList<>();
        List<StoredGramComment> gramComments = new ArrayList<>();
        List<StoredBlockPost> blockPosts = new ArrayList<>();
        List<StoredBlockComment> blockComments = new ArrayList<>();
        List<StoredFollow> follows = new ArrayList<>();
        List<StoredNotification> notifications = new ArrayList<>();
        Set<String> blockPostLikeNotificationKeys = new LinkedHashSet<>();
        Map<String, StoredProfile> profiles = new HashMap<>();
        Map<String, Set<String>> tinderLikes = new HashMap<>();
        Map<String, Set<String>> tinderPasses = new HashMap<>();
    }

    private static final class StoredGramPost {
        String id = "";
        String authorUuid = "";
        String authorName = "";
        String caption = "";
        String imageRef = "";
        long createdAt;
        long createdGameTime;
        Set<String> likedBy = new LinkedHashSet<>();
    }

    private static final class StoredGramStory {
        String id = "";
        String authorUuid = "";
        String authorName = "";
        String imageRef = "";
        long createdAt;
        long createdGameTime;
        Set<String> seenBy = new LinkedHashSet<>();
    }

    private static final class StoredGramComment {
        String id = "";
        String postId = "";
        String authorUuid = "";
        String authorName = "";
        String body = "";
        long createdAt;
        Set<String> likedBy = new LinkedHashSet<>();
    }

    private static final class StoredBlockPost {
        String id = "";
        String authorUuid = "";
        String authorName = "";
        String handle = "";
        String body = "";
        String imageRef = "";
        long createdAt;
        long createdGameTime;
        Set<String> likedBy = new LinkedHashSet<>();
        Set<String> repostedBy = new LinkedHashSet<>();
    }

    private static final class StoredBlockComment {
        String id = "";
        String postId = "";
        String parentCommentId = "";
        String authorUuid = "";
        String authorName = "";
        String handle = "";
        String body = "";
        long createdAt;
        Set<String> likedBy = new LinkedHashSet<>();
    }

    private static final class StoredFollow {
        String id = "";
        String platform = "BLOCKGRAM";
        String fromUuid = "";
        String fromName = "";
        String toUuid = "";
        String toName = "";
        String status = "PENDING";
        long createdAt;
    }

    private static final class StoredNotification {
        String id = "";
        String platform = "BLOCKGRAM";
        String type = "INFO";
        String actorUuid = "";
        String actorName = "";
        String targetUuid = "";
        String text = "";
        String postId = "";
        long createdAt;
        boolean read;
    }

    private static final class StoredProfile {
        String playerUuid = "";
        String playerName = "Player";
        String blockGramBio = "Minecraft oyuncusu\nYap, keşfet ve paylaş.";
        String blockGramPhotoRef = "";
        String blockPostBio = "Minecraft oyuncusu • X kullanıcısı";
        String blockPostAvatarRef = "";
        String blockPostCoverRef = "";
        String tinderBio = "Minecraft oyuncusu • Yeni insanlarla tanışmaya açık.";
        String tinderPhotoRef = "";
        boolean tinderEnabled = true;
        int tinderAge = 18;
        String tinderGender = "UNSPECIFIED";
        String tinderShowGender = "EVERYONE";
        int tinderMinAge = 18;
        int tinderMaxAge = 99;
        String roleBadge = "PL";
    }
}

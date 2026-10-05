package com.mikasrevs.newphone.client.blockgram;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class PhoneBlockGramStore {
    public static final int MAX_CAPTION_LENGTH = 300;
    public static final int MAX_BIO_LENGTH = 160;
    public static final int MAX_COMMENT_LENGTH = 280;

    private static final Path DATA_FILE = Path.of(
            System.getProperty("user.dir"),
            "config",
            "newphone",
            "blockgram.properties"
    );

    private static final List<PostEntry> POSTS = new ArrayList<>();
    private static final List<PostEntry> SERVER_POSTS = new ArrayList<>();
    private static final Set<String> SERVER_POST_IDS = new LinkedHashSet<>();
    private static final Set<String> SERVER_FEED_POST_IDS = new LinkedHashSet<>();
    private static final List<CommentEntry> COMMENTS = new ArrayList<>();
    private static final List<CommentEntry> SERVER_COMMENTS = new ArrayList<>();
    private static final List<StoryEntry> STORIES = new ArrayList<>();
    private static final List<FollowRequestEntry> FOLLOW_REQUESTS = new ArrayList<>();
    private static final List<FollowRequestEntry> SERVER_FOLLOW_REQUESTS = new ArrayList<>();
    private static final List<NotificationEntry> NOTIFICATIONS = new ArrayList<>();
    private static final List<NotificationEntry> SERVER_NOTIFICATIONS = new ArrayList<>();
    private static final Set<String> VIEWED_STORY_IDS = new LinkedHashSet<>();
    private static final Set<String> SAVED_POST_IDS = new LinkedHashSet<>();
    private static final Set<String> LEGACY_SAMPLE_POST_IDS = Set.of(
            "blockgram-sample-1",
            "blockgram-sample-2",
            "blockgram-sample-3"
    );
    private static final Set<String> LEGACY_SAMPLE_STORY_IDS = Set.of(
            "blockgram-story-sample-1",
            "blockgram-story-sample-2"
    );

    private static boolean loaded;
    private static String profileBio = "Minecraft oyuncusu\nYap, keşfet ve paylaş.";
    private static String profilePhotoPath = "";
    private static boolean pendingProfileCameraCapture;

    private PhoneBlockGramStore() {
    }

    /** Clears only one-shot capture state; saved Lumi content remains untouched. */
    public static void resetTransientState() {
        pendingProfileCameraCapture = false;
    }

    public static List<PostEntry> all() {
        ensureLoaded();
        // Merge by id in linear time. The previous removeIf-per-server-post approach
        // became increasingly expensive while Lumi was open with a long post history.
        Map<String, PostEntry> mergedById = new LinkedHashMap<>();
        for (PostEntry post : POSTS) mergedById.put(post.id(), post);
        for (PostEntry serverPost : SERVER_POSTS) mergedById.put(serverPost.id(), serverPost);
        return mergedById.values().stream()
                .sorted(Comparator.comparingLong(PostEntry::createdAt).reversed())
                .toList();
    }

    public static PostEntry findPost(String postId) {
        ensureLoaded();
        if (postId == null) return null;
        for (PostEntry post : SERVER_POSTS) if (postId.equals(post.id())) return post;
        for (PostEntry post : POSTS) if (postId.equals(post.id())) return post;
        return null;
    }

    public static void applyServerPosts(List<PhoneSocialStateData.GramPostEntry> posts, String selfUuid) {
        ensureLoaded();
        SERVER_POSTS.clear();
        SERVER_POST_IDS.clear();
        SERVER_FEED_POST_IDS.clear();
        if (posts == null) return;
        for (PhoneSocialStateData.GramPostEntry post : posts) {
            if (post == null || post.id == null || post.id.isBlank()) continue;
            String imagePath = post.imageRef == null || post.imageRef.isBlank() ? "" : "social:" + post.imageRef;
            boolean liked = post.likedBy != null && post.likedBy.contains(selfUuid);
            int likes = post.likedBy == null ? 0 : post.likedBy.size();
            SERVER_POST_IDS.add(post.id);
            SERVER_POSTS.add(new PostEntry(
                    post.id, post.authorUuid == null ? "" : post.authorUuid, safeName(post.authorName),
                    normalize(post.caption, MAX_CAPTION_LENGTH), imagePath, post.createdAt, likes, liked, SAVED_POST_IDS.contains(post.id)
            ));
            if (post.feedVisible) SERVER_FEED_POST_IDS.add(post.id);
        }
    }

    public static void applyServerStories(List<PhoneSocialStateData.GramStoryEntry> stories) {
        ensureLoaded();
        STORIES.clear();
        if (stories == null) return;
        for (PhoneSocialStateData.GramStoryEntry story : stories) {
            if (story == null || story.id == null || story.id.isBlank()) continue;
            String imagePath = story.imageRef == null || story.imageRef.isBlank() ? "" : "social:" + story.imageRef;
            STORIES.add(new StoryEntry(story.id, story.authorUuid == null ? "" : story.authorUuid,
                    safeName(story.authorName), imagePath, story.createdAt, story.seenByNames));
            if (story.seen) VIEWED_STORY_IDS.add(story.id); else VIEWED_STORY_IDS.remove(story.id);
        }
    }

    public static void applyServerComments(List<PhoneSocialStateData.GramCommentEntry> comments, String selfUuid) {
        SERVER_COMMENTS.clear();
        if (comments == null) return;
        for (PhoneSocialStateData.GramCommentEntry comment : comments) {
            if (comment == null || comment.id == null || comment.id.isBlank()) continue;
            boolean liked = comment.likedBy != null && comment.likedBy.contains(selfUuid);
            int likes = comment.likedBy == null ? 0 : comment.likedBy.size();
            SERVER_COMMENTS.add(new CommentEntry(comment.id, comment.postId, comment.authorUuid, safeName(comment.authorName),
                    normalize(comment.body, MAX_COMMENT_LENGTH), comment.createdAt, likes, liked));
        }
    }

    public static void applyServerFollows(List<PhoneSocialStateData.FollowEntry> follows) {
        SERVER_FOLLOW_REQUESTS.clear();
        if (follows == null) return;
        for (PhoneSocialStateData.FollowEntry follow : follows) {
            if (follow == null || !"BLOCKGRAM".equalsIgnoreCase(follow.platform)) continue;
            SERVER_FOLLOW_REQUESTS.add(new FollowRequestEntry(follow.id, follow.fromUuid, safeName(follow.fromName),
                    follow.toUuid, safeName(follow.toName), follow.status, follow.createdAt));
        }
    }

    public static void applyServerNotifications(List<PhoneSocialStateData.NotificationEntry> notifications) {
        SERVER_NOTIFICATIONS.clear();
        if (notifications == null) return;
        for (PhoneSocialStateData.NotificationEntry notification : notifications) {
            if (notification == null || !"BLOCKGRAM".equalsIgnoreCase(notification.platform)) continue;
            SERVER_NOTIFICATIONS.add(new NotificationEntry(notification.id, notification.type, notification.actorUuid,
                    safeName(notification.actorName), notification.targetUuid, notification.text, notification.postId,
                    notification.createdAt, notification.read));
        }
    }

    public static void applyServerProfile(String bio, String photoPath) {
        ensureLoaded();
        profileBio = normalizeMultiline(bio, MAX_BIO_LENGTH);
        profilePhotoPath = photoPath == null ? "" : photoPath;
    }

    public static List<PostEntry> byAuthor(String authorUuid) {
        ensureLoaded();
        if (authorUuid == null) {
            return List.of();
        }
        return all().stream().filter(post -> authorUuid.equals(post.authorUuid())).limit(50).toList();
    }

    public static List<PostEntry> feedPosts() {
        ensureLoaded();
        return all().stream()
                .filter(post -> !SERVER_POST_IDS.contains(post.id()) || SERVER_FEED_POST_IDS.contains(post.id()))
                .toList();
    }

    /**
     * Hikâye satırında her kullanıcı bir kez görünür. Dönen kayıt o kullanıcının en yeni hikâyesidir.
     */
    public static List<StoryEntry> stories() {
        ensureLoaded();
        Map<String, StoryEntry> latestByAuthor = new LinkedHashMap<>();
        STORIES.stream()
                .sorted(Comparator.comparingLong(StoryEntry::createdAt).reversed())
                .forEach(story -> latestByAuthor.putIfAbsent(story.authorUuid(), story));
        return List.copyOf(latestByAuthor.values());
    }

    /**
     * Instagram mantığı: aynı oyuncunun hikâyeleri eskiden yeniye sırayla oynatılır.
     */
    public static List<StoryEntry> storiesByAuthor(String authorUuid) {
        ensureLoaded();
        if (authorUuid == null) {
            return List.of();
        }
        return STORIES.stream()
                .filter(story -> authorUuid.equals(story.authorUuid()))
                .sorted(Comparator.comparingLong(StoryEntry::createdAt))
                .toList();
    }

    public static boolean hasUnseenStories(String authorUuid) {
        ensureLoaded();
        return storiesByAuthor(authorUuid).stream().anyMatch(story -> !VIEWED_STORY_IDS.contains(story.id()));
    }

    public static void markStoryViewed(String storyId) {
        ensureLoaded();
        if (storyId == null || storyId.isBlank()) return;
        if (VIEWED_STORY_IDS.add(storyId)) {
            persist();
            PhoneSocialClientData.markGramStoryViewed(storyId);
        }
    }

    public static boolean isStoryViewed(String storyId) {
        ensureLoaded();
        return storyId != null && VIEWED_STORY_IDS.contains(storyId);
    }

    public static PostEntry create(String authorUuid, String authorName, String caption, String imagePath) {
        ensureLoaded();
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        String normalizedCaption = normalize(caption, MAX_CAPTION_LENGTH);
        String safeName = authorName == null || authorName.isBlank() ? "Player" : authorName.strip();
        PostEntry entry = new PostEntry(
                UUID.randomUUID().toString(),
                authorUuid == null ? "" : authorUuid,
                safeName,
                normalizedCaption,
                imagePath,
                System.currentTimeMillis(),
                0,
                false,
                false
        );
        POSTS.add(entry);
        persist();
        return entry;
    }

    public static StoryEntry createStory(String authorUuid, String authorName, String imagePath) {
        ensureLoaded();
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        String safeName = authorName == null || authorName.isBlank() ? "Player" : authorName.strip();
        StoryEntry entry = new StoryEntry(
                UUID.randomUUID().toString(),
                authorUuid == null ? "" : authorUuid,
                safeName,
                imagePath,
                System.currentTimeMillis()
        );
        STORIES.add(entry);
        // Bir kullanıcı için son 12 hikâyeyi tut.
        List<StoryEntry> authorStories = storiesByAuthor(entry.authorUuid());
        if (authorStories.size() > 12) {
            int removeCount = authorStories.size() - 12;
            for (int i = 0; i < removeCount; i++) {
                StoryEntry old = authorStories.get(i);
                STORIES.removeIf(story -> story.id().equals(old.id()));
                VIEWED_STORY_IDS.remove(old.id());
            }
        }
        persist();
        return entry;
    }

    public static void toggleLike(String id) {
        toggleLike(id, "", "");
    }

    public static void toggleLike(String id, String actorUuid, String actorName) {
        ensureLoaded();
        if (SERVER_POSTS.stream().anyMatch(post -> post.id().equals(id))) {
            PhoneSocialClientData.toggleGramLike(id);
            return;
        }
        for (int index = 0; index < POSTS.size(); index++) {
            PostEntry post = POSTS.get(index);
            if (!post.id().equals(id)) {
                continue;
            }
            boolean liked = !post.liked();
            int likes = Math.max(0, post.likes() + (liked ? 1 : -1));
            POSTS.set(index, post.withLike(liked, likes));
            if (liked && !post.authorUuid().equals(actorUuid)) {
                addNotification(
                        "LIKE",
                        actorUuid,
                        safeName(actorName),
                        post.authorUuid(),
                        safeName(actorName) + " gönderini beğendi",
                        post.id()
                );
            }
            persist();
            return;
        }
    }

    public static void toggleSaved(String id) {
        ensureLoaded();
        if (id == null || id.isBlank()) return;
        for (int index = 0; index < SERVER_POSTS.size(); index++) {
            PostEntry post = SERVER_POSTS.get(index);
            if (post.id().equals(id)) {
                boolean saved = !post.saved();
                SERVER_POSTS.set(index, post.withSaved(saved));
                if (saved) SAVED_POST_IDS.add(id); else SAVED_POST_IDS.remove(id);
                persist();
                return;
            }
        }
        for (int index = 0; index < POSTS.size(); index++) {
            PostEntry post = POSTS.get(index);
            if (post.id().equals(id)) {
                boolean saved = !post.saved();
                POSTS.set(index, post.withSaved(saved));
                if (saved) SAVED_POST_IDS.add(id); else SAVED_POST_IDS.remove(id);
                persist();
                return;
            }
        }
    }

    public static void removePostLocally(String postId) {
        ensureLoaded();
        if (postId == null || postId.isBlank()) return;
        POSTS.removeIf(post -> postId.equals(post.id()));
        SERVER_POSTS.removeIf(post -> postId.equals(post.id()));
        COMMENTS.removeIf(comment -> postId.equals(comment.postId()));
        SERVER_COMMENTS.removeIf(comment -> postId.equals(comment.postId()));
        SERVER_FEED_POST_IDS.remove(postId);
        SAVED_POST_IDS.remove(postId);
        persist();
    }

    public static List<PostEntry> savedPosts() {
        ensureLoaded();
        return all().stream().filter(PostEntry::saved).toList();
    }

    public static List<CommentEntry> comments(String postId) {
        ensureLoaded();
        if (postId == null) {
            return List.of();
        }
        Map<String, CommentEntry> mergedById = new LinkedHashMap<>();
        for (CommentEntry comment : COMMENTS) mergedById.put(comment.id(), comment);
        for (CommentEntry serverComment : SERVER_COMMENTS) mergedById.put(serverComment.id(), serverComment);
        return mergedById.values().stream().filter(comment -> postId.equals(comment.postId()))
                .sorted(Comparator.comparingLong(CommentEntry::createdAt)).toList();
    }

    public static int commentCount(String postId) {
        return comments(postId).size();
    }

    public static CommentEntry addComment(String postId, String authorUuid, String authorName, String body) {
        ensureLoaded();
        PostEntry post = findPost(postId);
        String normalized = normalize(body, MAX_COMMENT_LENGTH);
        if (post != null && SERVER_POSTS.stream().anyMatch(serverPost -> serverPost.id().equals(postId))) {
            if (!normalized.isBlank()) PhoneSocialClientData.addGramComment(postId, normalized);
            return null;
        }
        if (post == null || normalized.isBlank()) {
            return null;
        }
        CommentEntry entry = new CommentEntry(
                UUID.randomUUID().toString(),
                postId,
                authorUuid == null ? "" : authorUuid,
                safeName(authorName),
                normalized,
                System.currentTimeMillis(),
                0,
                false
        );
        COMMENTS.add(entry);
        if (!post.authorUuid().equals(entry.authorUuid())) {
            addNotification(
                    "COMMENT",
                    entry.authorUuid(),
                    entry.authorName(),
                    post.authorUuid(),
                    entry.authorName() + " gönderine yorum yaptı",
                    post.id()
            );
        }
        persist();
        return entry;
    }

    public static void toggleCommentLike(String commentId) {
        ensureLoaded();
        if (SERVER_COMMENTS.stream().anyMatch(comment -> comment.id().equals(commentId))) {
            PhoneSocialClientData.toggleGramCommentLike(commentId);
            return;
        }
        for (int index = 0; index < COMMENTS.size(); index++) {
            CommentEntry comment = COMMENTS.get(index);
            if (!comment.id().equals(commentId)) {
                continue;
            }
            boolean liked = !comment.liked();
            COMMENTS.set(index, comment.withLike(liked, Math.max(0, comment.likes() + (liked ? 1 : -1))));
            persist();
            return;
        }
    }

    public static void recordShare(String postId, String actorUuid, String actorName) {
        ensureLoaded();
        PostEntry post = findPost(postId);
        if (post != null && SERVER_POSTS.stream().anyMatch(serverPost -> serverPost.id().equals(postId))) {
            PhoneSocialClientData.recordGramShare(postId);
            return;
        }
        if (post == null) {
            return;
        }
        if (!post.authorUuid().equals(actorUuid)) {
            addNotification(
                    "SHARE",
                    actorUuid,
                    safeName(actorName),
                    post.authorUuid(),
                    safeName(actorName) + " gönderini paylaştı",
                    post.id()
            );
        }
        persist();
    }

    public static String profileBio() {
        ensureLoaded();
        return profileBio;
    }

    public static String profilePhotoPath() {
        ensureLoaded();
        return profilePhotoPath;
    }

    public static void updateProfile(String bio, String photoPath) {
        ensureLoaded();
        profileBio = normalizeMultiline(bio, MAX_BIO_LENGTH);
        profilePhotoPath = photoPath == null ? "" : photoPath;
        persist();
    }

    public static void requestProfileCameraCapture() {
        ensureLoaded();
        pendingProfileCameraCapture = true;
    }

    public static boolean applyPendingCapturedPhoto(Minecraft minecraft) {
        ensureLoaded();
        if (!pendingProfileCameraCapture) {
            return false;
        }
        boolean applied = false;
        Path latest = PhoneGalleryStore.latestPhoto(minecraft);
        if (latest != null) {
            profilePhotoPath = latest.toAbsolutePath().normalize().toString();
            persist();
            applied = true;
        }
        pendingProfileCameraCapture = false;
        return applied;
    }

    public static List<SuggestedPlayer> suggestedPlayers() {
        ensureLoaded();
        List<SuggestedPlayer> result = new ArrayList<>();
        String self = PhoneClientData.selfUuid();
        for (PhoneSocialStateData.ProfileEntry profile : PhoneSocialClientData.profiles()) {
            if (profile == null || profile.playerUuid == null || profile.playerUuid.isBlank() || profile.playerUuid.equals(self)) continue;
            String bio = profile.blockGramBio == null || profile.blockGramBio.isBlank() ? PhoneSettingsStore.text("Lumi kullanıcısı", "Lumi user") : PhoneSettingsStore.localizeNetworkText(profile.blockGramBio.replace('\n', ' '));
            result.add(new SuggestedPlayer(profile.playerUuid, safeName(profile.playerName), bio));
        }
        return result;
    }

    public static void sendFollowRequest(String fromUuid, String fromName, String toUuid, String toName) {
        ensureLoaded();
        if (PhoneSocialClientData.hasServerProfile(toUuid)) {
            PhoneSocialClientData.sendFollowRequest("BLOCKGRAM", toUuid);
            return;
        }
        if (toUuid == null || toUuid.isBlank() || fromUuid == null || fromUuid.isBlank()) {
            return;
        }
        boolean exists = FOLLOW_REQUESTS.stream().anyMatch(request ->
                request.fromUuid().equals(fromUuid)
                        && request.toUuid().equals(toUuid)
                        && !"DECLINED".equals(request.status())
        );
        if (exists) {
            return;
        }
        FOLLOW_REQUESTS.add(new FollowRequestEntry(
                UUID.randomUUID().toString(),
                fromUuid,
                safeName(fromName),
                toUuid,
                safeName(toName),
                "PENDING",
                System.currentTimeMillis()
        ));
        addNotification("FOLLOW_REQUEST", fromUuid, safeName(fromName), toUuid, safeName(fromName) + " takip isteği gönderdi", "");
        persist();
    }

    public static String followState(String selfUuid, String targetUuid) {
        ensureLoaded();
        String server = PhoneSocialClientData.followState("BLOCKGRAM", selfUuid, targetUuid);
        if (!"NONE".equals(server)) return server;
        return FOLLOW_REQUESTS.stream()
                .filter(request -> request.fromUuid().equals(selfUuid) && request.toUuid().equals(targetUuid))
                .max(Comparator.comparingLong(FollowRequestEntry::createdAt))
                .map(FollowRequestEntry::status)
                .orElse("NONE");
    }

    public static List<FollowRequestEntry> incomingRequests(String selfUuid) {
        ensureLoaded();
        Map<String, FollowRequestEntry> mergedById = new LinkedHashMap<>();
        for (FollowRequestEntry request : FOLLOW_REQUESTS) mergedById.put(request.id(), request);
        for (FollowRequestEntry request : SERVER_FOLLOW_REQUESTS) mergedById.put(request.id(), request);
        return mergedById.values().stream().filter(request -> "PENDING".equals(request.status()))
                .filter(request -> request.toUuid().equals(selfUuid) || "*".equals(request.toUuid()))
                .sorted(Comparator.comparingLong(FollowRequestEntry::createdAt).reversed()).toList();
    }

    public static int incomingRequestCount(String selfUuid) {
        return incomingRequests(selfUuid).size();
    }

    public static void acceptFollowRequest(String id, String selfUuid) {
        ensureLoaded();
        if (SERVER_FOLLOW_REQUESTS.stream().anyMatch(request -> request.id().equals(id))) {
            PhoneSocialClientData.acceptFollowRequest("BLOCKGRAM", id);
            return;
        }
        updateRequest(id, selfUuid, "ACCEPTED");
    }

    public static void acceptAndFollowBack(String id, String selfUuid) {
        ensureLoaded();
        if (SERVER_FOLLOW_REQUESTS.stream().anyMatch(request -> request.id().equals(id))) {
            PhoneSocialClientData.acceptAndFollowBack("BLOCKGRAM", id);
            return;
        }
        FollowRequestEntry request = FOLLOW_REQUESTS.stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
        if (request == null) return;
        updateRequest(id, selfUuid, "ACCEPTED");
        sendFollowRequest(selfUuid, request.toName(), request.fromUuid(), request.fromName());
    }

    public static void declineFollowRequest(String id, String selfUuid) {
        ensureLoaded();
        if (SERVER_FOLLOW_REQUESTS.stream().anyMatch(request -> request.id().equals(id))) {
            PhoneSocialClientData.declineFollowRequest("BLOCKGRAM", id);
            return;
        }
        updateRequest(id, selfUuid, "DECLINED");
    }

    private static void updateRequest(String id, String selfUuid, String status) {
        for (int index = 0; index < FOLLOW_REQUESTS.size(); index++) {
            FollowRequestEntry request = FOLLOW_REQUESTS.get(index);
            if (!request.id().equals(id)) {
                continue;
            }
            String target = "*".equals(request.toUuid()) ? selfUuid : request.toUuid();
            FOLLOW_REQUESTS.set(index, request.withStatusAndTarget(status, target));
            if ("ACCEPTED".equals(status)) {
                addNotification("FOLLOW_ACCEPTED", target, request.toName(), request.fromUuid(), request.toName() + " takip isteğini kabul etti", "");
            }
            persist();
            return;
        }
    }

    public static int followerCount(String selfUuid) {
        ensureLoaded();
        int server = PhoneSocialClientData.followerCount("BLOCKGRAM", selfUuid);
        int local = (int) FOLLOW_REQUESTS.stream().filter(request -> "ACCEPTED".equals(request.status()))
                .filter(request -> request.toUuid().equals(selfUuid)).count();
        return server + local;
    }

    public static int followingCount(String selfUuid) {
        ensureLoaded();
        int server = PhoneSocialClientData.followingCount("BLOCKGRAM", selfUuid);
        int local = (int) FOLLOW_REQUESTS.stream().filter(request -> "ACCEPTED".equals(request.status()))
                .filter(request -> request.fromUuid().equals(selfUuid)).count();
        return server + local;
    }

    public static List<NotificationEntry> notifications(String selfUuid) {
        ensureLoaded();
        Map<String, NotificationEntry> mergedById = new LinkedHashMap<>();
        for (NotificationEntry notification : NOTIFICATIONS) mergedById.put(notification.id(), notification);
        for (NotificationEntry notification : SERVER_NOTIFICATIONS) mergedById.put(notification.id(), notification);
        return mergedById.values().stream().filter(notification -> notification.targetUuid().equals(selfUuid) || "*".equals(notification.targetUuid()))
                .sorted(Comparator.comparingLong(NotificationEntry::createdAt).reversed()).toList();
    }

    public static int unreadNotificationCount(String selfUuid) {
        ensureLoaded();
        int normal = (int) notifications(selfUuid).stream().filter(notification -> !notification.read()).count();
        return normal + incomingRequestCount(selfUuid);
    }

    public static void markNotificationsRead(String selfUuid) {
        ensureLoaded();
        PhoneSocialClientData.markNotificationsRead("BLOCKGRAM");
        boolean changed = false;
        for (int index = 0; index < NOTIFICATIONS.size(); index++) {
            NotificationEntry notification = NOTIFICATIONS.get(index);
            if (!notification.read() && (notification.targetUuid().equals(selfUuid) || "*".equals(notification.targetUuid()))) {
                NOTIFICATIONS.set(index, notification.withRead(true));
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }

    public static void clearNotifications(String selfUuid) {
        ensureLoaded();
        PhoneSocialClientData.clearNotifications("BLOCKGRAM");
        SERVER_NOTIFICATIONS.removeIf(notification -> notification.targetUuid().equals(selfUuid)
                || "*".equals(notification.targetUuid()));
        boolean removed = NOTIFICATIONS.removeIf(notification -> notification.targetUuid().equals(selfUuid)
                || "*".equals(notification.targetUuid()));
        if (removed) persist();
    }

    private static void addNotification(String type, String actorUuid, String actorName, String targetUuid, String text, String postId) {
        NOTIFICATIONS.add(new NotificationEntry(
                UUID.randomUUID().toString(),
                type == null ? "INFO" : type,
                actorUuid == null ? "" : actorUuid,
                safeName(actorName),
                targetUuid == null || targetUuid.isBlank() ? "*" : targetUuid,
                text == null ? "" : text,
                postId == null ? "" : postId,
                System.currentTimeMillis(),
                false
        ));
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        POSTS.clear();
        COMMENTS.clear();
        STORIES.clear();
        FOLLOW_REQUESTS.clear();
        NOTIFICATIONS.clear();
        VIEWED_STORY_IDS.clear();
        SAVED_POST_IDS.clear();

        Properties properties = new Properties();
        if (Files.exists(DATA_FILE)) {
            try (InputStream input = Files.newInputStream(DATA_FILE)) {
                properties.load(input);
            } catch (IOException ignored) {
            }
        }

        profileBio = decode(properties.getProperty("profile.bio", encode("Minecraft oyuncusu\nYap, keşfet ve paylaş.")));
        profilePhotoPath = decode(properties.getProperty("profile.photo", ""));

        for (String id : properties.getProperty("ids", "").split(",")) {
            if (id.isBlank()) {
                continue;
            }
            String prefix = "post." + id + ".";
            String authorUuid = decode(properties.getProperty(prefix + "authorUuid", ""));
            String authorName = decode(properties.getProperty(prefix + "authorName", ""));
            String caption = decode(properties.getProperty(prefix + "caption", ""));
            String imagePath = decode(properties.getProperty(prefix + "imagePath", ""));
            long createdAt = parseLong(properties.getProperty(prefix + "createdAt", "0"), 0L);
            int likes = (int) parseLong(properties.getProperty(prefix + "likes", "0"), 0L);
            boolean liked = Boolean.parseBoolean(properties.getProperty(prefix + "liked", "false"));
            boolean saved = Boolean.parseBoolean(properties.getProperty(prefix + "saved", "false"));
            if (!imagePath.isBlank()) {
                POSTS.add(new PostEntry(id, authorUuid, authorName, caption, imagePath, createdAt, likes, liked, saved));
            }
        }

        for (String id : properties.getProperty("commentIds", "").split(",")) {
            if (id.isBlank()) {
                continue;
            }
            String prefix = "comment." + id + ".";
            COMMENTS.add(new CommentEntry(
                    id,
                    decode(properties.getProperty(prefix + "postId", "")),
                    decode(properties.getProperty(prefix + "authorUuid", "")),
                    decode(properties.getProperty(prefix + "authorName", "")),
                    decode(properties.getProperty(prefix + "body", "")),
                    parseLong(properties.getProperty(prefix + "createdAt", "0"), 0L),
                    (int) parseLong(properties.getProperty(prefix + "likes", "0"), 0L),
                    Boolean.parseBoolean(properties.getProperty(prefix + "liked", "false"))
            ));
        }

        for (String id : properties.getProperty("storyIds", "").split(",")) {
            if (id.isBlank()) {
                continue;
            }
            String prefix = "story." + id + ".";
            String authorUuid = decode(properties.getProperty(prefix + "authorUuid", ""));
            String authorName = decode(properties.getProperty(prefix + "authorName", ""));
            String imagePath = decode(properties.getProperty(prefix + "imagePath", ""));
            long createdAt = parseLong(properties.getProperty(prefix + "createdAt", "0"), 0L);
            if (!imagePath.isBlank()) {
                STORIES.add(new StoryEntry(id, authorUuid, authorName, imagePath, createdAt));
            }
        }

        for (String id : properties.getProperty("viewedStoryIds", "").split(",")) {
            if (!id.isBlank()) {
                VIEWED_STORY_IDS.add(id);
            }
        }

        for (String id : properties.getProperty("savedPostIds", "").split(",")) {
            if (!id.isBlank()) SAVED_POST_IDS.add(id);
        }

        for (String id : properties.getProperty("followRequestIds", "").split(",")) {
            if (id.isBlank()) {
                continue;
            }
            String prefix = "follow." + id + ".";
            FOLLOW_REQUESTS.add(new FollowRequestEntry(
                    id,
                    decode(properties.getProperty(prefix + "fromUuid", "")),
                    decode(properties.getProperty(prefix + "fromName", "")),
                    decode(properties.getProperty(prefix + "toUuid", "")),
                    decode(properties.getProperty(prefix + "toName", "")),
                    properties.getProperty(prefix + "status", "PENDING"),
                    parseLong(properties.getProperty(prefix + "createdAt", "0"), 0L)
            ));
        }

        for (String id : properties.getProperty("notificationIds", "").split(",")) {
            if (id.isBlank()) {
                continue;
            }
            String prefix = "notification." + id + ".";
            NOTIFICATIONS.add(new NotificationEntry(
                    id,
                    properties.getProperty(prefix + "type", "INFO"),
                    decode(properties.getProperty(prefix + "actorUuid", "")),
                    decode(properties.getProperty(prefix + "actorName", "")),
                    decode(properties.getProperty(prefix + "targetUuid", "*")),
                    decode(properties.getProperty(prefix + "text", "")),
                    decode(properties.getProperty(prefix + "postId", "")),
                    parseLong(properties.getProperty(prefix + "createdAt", "0"), 0L),
                    Boolean.parseBoolean(properties.getProperty(prefix + "read", "false"))
            ));
        }

        boolean changed = POSTS.removeIf(post -> LEGACY_SAMPLE_POST_IDS.contains(post.id()));
        changed |= COMMENTS.removeIf(comment -> "blockgram-comment-sample-1".equals(comment.id())
                || LEGACY_SAMPLE_POST_IDS.contains(comment.postId()));
        changed |= STORIES.removeIf(story -> LEGACY_SAMPLE_STORY_IDS.contains(story.id()));
        changed |= SAVED_POST_IDS.removeIf(LEGACY_SAMPLE_POST_IDS::contains);
        changed |= VIEWED_STORY_IDS.removeIf(LEGACY_SAMPLE_STORY_IDS::contains);
        changed |= FOLLOW_REQUESTS.removeIf(request -> "blockgram-follow-sample".equals(request.id()));
        changed |= NOTIFICATIONS.removeIf(notification -> "blockgram-notification-like".equals(notification.id())
                || LEGACY_SAMPLE_POST_IDS.contains(notification.postId()));
        if (changed || !Files.exists(DATA_FILE)) {
            persist();
        }
    }

    private static void persist() {
        Properties properties = new Properties();
        properties.setProperty("profile.bio", encode(profileBio));
        properties.setProperty("profile.photo", encode(profilePhotoPath));

        properties.setProperty("ids", String.join(",", POSTS.stream().map(PostEntry::id).toList()));
        for (PostEntry post : POSTS) {
            String prefix = "post." + post.id() + ".";
            properties.setProperty(prefix + "authorUuid", encode(post.authorUuid()));
            properties.setProperty(prefix + "authorName", encode(post.authorName()));
            properties.setProperty(prefix + "caption", encode(post.caption()));
            properties.setProperty(prefix + "imagePath", encode(post.imagePath()));
            properties.setProperty(prefix + "createdAt", Long.toString(post.createdAt()));
            properties.setProperty(prefix + "likes", Integer.toString(post.likes()));
            properties.setProperty(prefix + "liked", Boolean.toString(post.liked()));
            properties.setProperty(prefix + "saved", Boolean.toString(post.saved()));
        }

        properties.setProperty("commentIds", String.join(",", COMMENTS.stream().map(CommentEntry::id).toList()));
        for (CommentEntry comment : COMMENTS) {
            String prefix = "comment." + comment.id() + ".";
            properties.setProperty(prefix + "postId", encode(comment.postId()));
            properties.setProperty(prefix + "authorUuid", encode(comment.authorUuid()));
            properties.setProperty(prefix + "authorName", encode(comment.authorName()));
            properties.setProperty(prefix + "body", encode(comment.body()));
            properties.setProperty(prefix + "createdAt", Long.toString(comment.createdAt()));
            properties.setProperty(prefix + "likes", Integer.toString(comment.likes()));
            properties.setProperty(prefix + "liked", Boolean.toString(comment.liked()));
        }

        properties.setProperty("storyIds", String.join(",", STORIES.stream().map(StoryEntry::id).toList()));
        for (StoryEntry story : STORIES) {
            String prefix = "story." + story.id() + ".";
            properties.setProperty(prefix + "authorUuid", encode(story.authorUuid()));
            properties.setProperty(prefix + "authorName", encode(story.authorName()));
            properties.setProperty(prefix + "imagePath", encode(story.imagePath()));
            properties.setProperty(prefix + "createdAt", Long.toString(story.createdAt()));
        }
        properties.setProperty("viewedStoryIds", String.join(",", VIEWED_STORY_IDS));
        properties.setProperty("savedPostIds", String.join(",", SAVED_POST_IDS));

        properties.setProperty("followRequestIds", String.join(",", FOLLOW_REQUESTS.stream().map(FollowRequestEntry::id).toList()));
        for (FollowRequestEntry request : FOLLOW_REQUESTS) {
            String prefix = "follow." + request.id() + ".";
            properties.setProperty(prefix + "fromUuid", encode(request.fromUuid()));
            properties.setProperty(prefix + "fromName", encode(request.fromName()));
            properties.setProperty(prefix + "toUuid", encode(request.toUuid()));
            properties.setProperty(prefix + "toName", encode(request.toName()));
            properties.setProperty(prefix + "status", request.status());
            properties.setProperty(prefix + "createdAt", Long.toString(request.createdAt()));
        }

        properties.setProperty("notificationIds", String.join(",", NOTIFICATIONS.stream().map(NotificationEntry::id).toList()));
        for (NotificationEntry notification : NOTIFICATIONS) {
            String prefix = "notification." + notification.id() + ".";
            properties.setProperty(prefix + "type", notification.type());
            properties.setProperty(prefix + "actorUuid", encode(notification.actorUuid()));
            properties.setProperty(prefix + "actorName", encode(notification.actorName()));
            properties.setProperty(prefix + "targetUuid", encode(notification.targetUuid()));
            properties.setProperty(prefix + "text", encode(notification.text()));
            properties.setProperty(prefix + "postId", encode(notification.postId()));
            properties.setProperty(prefix + "createdAt", Long.toString(notification.createdAt()));
            properties.setProperty(prefix + "read", Boolean.toString(notification.read()));
        }

        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(DATA_FILE)) {
                properties.store(output, "New Phone BlockGram data");
            }
        } catch (IOException ignored) {
        }
    }

    private static String safeName(String value) {
        return value == null || value.isBlank() ? "Player" : value.strip();
    }

    private static String normalize(String value, int maxLength) {
        String normalized = value == null ? "" : value.strip();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private static String normalizeMultiline(String value, int maxLength) {
        String normalized = value == null ? "" : value.replace("\r", "").strip();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    public record PostEntry(
            String id,
            String authorUuid,
            String authorName,
            String caption,
            String imagePath,
            long createdAt,
            int likes,
            boolean liked,
            boolean saved
    ) {
        public PostEntry withLike(boolean newLiked, int newLikes) {
            return new PostEntry(id, authorUuid, authorName, caption, imagePath, createdAt, newLikes, newLiked, saved);
        }

        public PostEntry withSaved(boolean newSaved) {
            return new PostEntry(id, authorUuid, authorName, caption, imagePath, createdAt, likes, liked, newSaved);
        }
    }

    public record CommentEntry(
            String id,
            String postId,
            String authorUuid,
            String authorName,
            String body,
            long createdAt,
            int likes,
            boolean liked
    ) {
        public CommentEntry withLike(boolean newLiked, int newLikes) {
            return new CommentEntry(id, postId, authorUuid, authorName, body, createdAt, newLikes, newLiked);
        }
    }

    public record StoryEntry(
            String id,
            String authorUuid,
            String authorName,
            String imagePath,
            long createdAt,
            List<String> seenByNames
    ) {
        public StoryEntry(String id, String authorUuid, String authorName, String imagePath, long createdAt) {
            this(id, authorUuid, authorName, imagePath, createdAt, List.of());
        }

        public StoryEntry {
            seenByNames = seenByNames == null ? List.of() : List.copyOf(seenByNames);
        }
    }

    public record SuggestedPlayer(String uuid, String name, String bio) {
    }

    public record FollowRequestEntry(
            String id,
            String fromUuid,
            String fromName,
            String toUuid,
            String toName,
            String status,
            long createdAt
    ) {
        public FollowRequestEntry withStatusAndTarget(String newStatus, String newTargetUuid) {
            return new FollowRequestEntry(id, fromUuid, fromName, newTargetUuid, toName, newStatus, createdAt);
        }
    }

    public record NotificationEntry(
            String id,
            String type,
            String actorUuid,
            String actorName,
            String targetUuid,
            String text,
            String postId,
            long createdAt,
            boolean read
    ) {
        public NotificationEntry withRead(boolean newRead) {
            return new NotificationEntry(id, type, actorUuid, actorName, targetUuid, text, postId, createdAt, newRead);
        }
    }
}

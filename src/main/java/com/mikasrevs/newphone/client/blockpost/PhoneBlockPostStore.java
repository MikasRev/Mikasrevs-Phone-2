package com.mikasrevs.newphone.client.blockpost;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
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
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class PhoneBlockPostStore {
    public static final int MAX_POST_LENGTH = 300;
    public static final int MAX_COMMENT_LENGTH = 280;

    private static final Path POSTS_FILE = Path.of(
            System.getProperty("user.dir"),
            "config",
            "newphone",
            "blockpost.properties"
    );

    private static final List<PostEntry> POSTS = new ArrayList<>();
    private static final List<PostEntry> SERVER_POSTS = new ArrayList<>();
    private static final java.util.Set<String> SERVER_POST_IDS = new java.util.LinkedHashSet<>();
    private static final java.util.Set<String> SERVER_FEED_POST_IDS = new java.util.LinkedHashSet<>();
    private static final List<CommentEntry> COMMENTS = new ArrayList<>();
    private static final List<CommentEntry> SERVER_COMMENTS = new ArrayList<>();
    private static final Set<String> LEGACY_SAMPLE_POST_IDS = Set.of(
            "blockpost-welcome",
            "blockpost-builds",
            "blockpost-nether",
            "blockpost-redstone"
    );
    private static boolean loaded;
    private static String profileAvatarPath = "";
    private static String profileCoverPath = "";
    private static String profileBio = "Minecraft oyuncusu • X kullanıcısı";
    private static String pendingCaptureTarget = "";

    private PhoneBlockPostStore() {
    }

    /** Clears only one-shot profile capture state; saved X content remains untouched. */
    public static void resetTransientState() {
        pendingCaptureTarget = "";
    }

    public static List<PostEntry> all() {
        ensureLoaded();
        // Merge by id in linear time. The previous removeIf-per-server-post loop became
        // quadratic as profile history grew toward the 50-post-per-player limit.
        java.util.Map<String, PostEntry> mergedById = new java.util.LinkedHashMap<>();
        for (PostEntry post : POSTS) mergedById.put(post.id(), post);
        for (PostEntry serverPost : SERVER_POSTS) mergedById.put(serverPost.id(), serverPost);
        return mergedById.values().stream()
                .sorted(Comparator.comparingLong(PostEntry::createdAt).reversed())
                .toList();
    }

    public static List<PostEntry> feedPosts() {
        ensureLoaded();
        return all().stream()
                .filter(post -> !SERVER_POST_IDS.contains(post.id()) || SERVER_FEED_POST_IDS.contains(post.id()))
                .toList();
    }

    public static PostEntry find(String id) {
        ensureLoaded();
        if (id == null) return null;
        for (PostEntry post : SERVER_POSTS) if (id.equals(post.id())) return post;
        for (PostEntry post : POSTS) if (id.equals(post.id())) return post;
        return null;
    }

    public static void applyServerPosts(List<PhoneSocialStateData.BlockPostEntry> posts, String selfUuid) {
        SERVER_POSTS.clear();
        SERVER_POST_IDS.clear();
        SERVER_FEED_POST_IDS.clear();
        if (posts == null) return;
        for (PhoneSocialStateData.BlockPostEntry post : posts) {
            if (post == null || post.id == null || post.id.isBlank()) continue;
            String imagePath = post.imageRef == null || post.imageRef.isBlank() ? "" : "social:" + post.imageRef;
            boolean liked = post.likedBy != null && post.likedBy.contains(selfUuid);
            boolean reposted = post.repostedBy != null && post.repostedBy.contains(selfUuid);
            SERVER_POST_IDS.add(post.id);
            SERVER_POSTS.add(new PostEntry(
                    post.id, post.authorUuid == null ? "" : post.authorUuid,
                    post.authorName == null || post.authorName.isBlank() ? "Player" : post.authorName,
                    post.handle == null || post.handle.isBlank() ? handleFor(post.authorName) : post.handle,
                    normalizeBody(post.body, MAX_POST_LENGTH), imagePath, post.createdAt,
                    post.likedBy == null ? 0 : post.likedBy.size(),
                    post.repostedBy == null ? 0 : post.repostedBy.size(), liked, reposted, false
            ));
            if (post.feedVisible) SERVER_FEED_POST_IDS.add(post.id);
        }
    }

    public static void applyServerComments(List<PhoneSocialStateData.BlockPostCommentEntry> comments, String selfUuid) {
        SERVER_COMMENTS.clear();
        if (comments == null) return;
        for (PhoneSocialStateData.BlockPostCommentEntry comment : comments) {
            if (comment == null || comment.id == null || comment.id.isBlank()) continue;
            boolean liked = comment.likedBy != null && comment.likedBy.contains(selfUuid);
            SERVER_COMMENTS.add(new CommentEntry(comment.id, comment.postId, comment.parentCommentId, comment.authorUuid,
                    comment.authorName == null || comment.authorName.isBlank() ? "Player" : comment.authorName,
                    comment.handle == null || comment.handle.isBlank() ? handleFor(comment.authorName) : comment.handle,
                    normalizeBody(comment.body, MAX_COMMENT_LENGTH), comment.createdAt,
                    comment.likedBy == null ? 0 : comment.likedBy.size(), liked));
        }
    }

    public static void applyServerProfile(String bio, String avatarPath, String coverPath) {
        ensureLoaded();
        profileBio = bio == null || bio.isBlank() ? PhoneSettingsStore.text("Minecraft oyuncusu • X kullanıcısı", "Minecraft player • X user") : PhoneSettingsStore.localizeNetworkText(normalizeBody(bio, 160));
        profileAvatarPath = avatarPath == null ? "" : avatarPath;
        profileCoverPath = coverPath == null ? "" : coverPath;
    }

    public static void applyServerProfile(String avatarPath, String coverPath) {
        applyServerProfile(profileBio, avatarPath, coverPath);
    }

    public static List<CommentEntry> comments(String postId) {
        ensureLoaded();
        if (postId == null) return List.of();
        return commentsForAllPosts().stream()
                .filter(comment -> postId.equals(comment.postId()))
                .sorted(Comparator.comparingLong(CommentEntry::createdAt))
                .toList();
    }

    public static int commentCount(String postId) {
        return comments(postId).size();
    }

    public static int replyCount(String commentId) {
        ensureLoaded();
        if (commentId == null || commentId.isBlank()) {
            return 0;
        }
        return (int) commentsForAllPosts().stream()
                .filter(comment -> commentId.equals(comment.parentCommentId())).count();
    }

    private static List<CommentEntry> commentsForAllPosts() {
        java.util.Map<String, CommentEntry> mergedById = new java.util.LinkedHashMap<>();
        for (CommentEntry comment : COMMENTS) mergedById.put(comment.id(), comment);
        for (CommentEntry serverComment : SERVER_COMMENTS) mergedById.put(serverComment.id(), serverComment);
        return List.copyOf(mergedById.values());
    }

    public static void toggleCommentLike(String commentId) {
        ensureLoaded();
        if (SERVER_COMMENTS.stream().anyMatch(comment -> comment.id().equals(commentId))) {
            PhoneSocialClientData.toggleBlockPostCommentLike(commentId);
            return;
        }
        if (commentId == null) {
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


    public static String profileBio() {
        ensureLoaded();
        return profileBio == null ? "" : profileBio;
    }

    public static void setProfileBio(String bio) {
        ensureLoaded();
        profileBio = normalizeBody(bio, 160);
        persist();
        PhoneSocialClientData.updateBlockPostBio(profileBio);
    }

    public static String profileAvatarPath() {
        ensureLoaded();
        return profileAvatarPath == null ? "" : profileAvatarPath;
    }

    public static String profileCoverPath() {
        ensureLoaded();
        return profileCoverPath == null ? "" : profileCoverPath;
    }

    public static void setProfileAvatarPath(String path) {
        ensureLoaded();
        profileAvatarPath = path == null ? "" : path;
        persist();
    }

    public static void setProfileCoverPath(String path) {
        ensureLoaded();
        profileCoverPath = path == null ? "" : path;
        persist();
    }

    public static void requestProfileAvatarCameraCapture() {
        ensureLoaded();
        pendingCaptureTarget = "avatar";
    }

    public static void requestProfileCoverCameraCapture() {
        ensureLoaded();
        pendingCaptureTarget = "cover";
    }

    public static boolean hasPendingProfileCapture() {
        ensureLoaded();
        return pendingCaptureTarget != null && !pendingCaptureTarget.isBlank();
    }

    public static boolean applyPendingCapturedPhoto(Minecraft minecraft) {
        ensureLoaded();
        if (pendingCaptureTarget == null || pendingCaptureTarget.isBlank()) {
            return false;
        }
        boolean applied = false;
        java.nio.file.Path latest = PhoneGalleryStore.latestPhoto(minecraft);
        if (latest != null) {
            String normalized = latest.toAbsolutePath().normalize().toString();
            if ("avatar".equals(pendingCaptureTarget)) {
                profileAvatarPath = normalized;
                applied = true;
            } else if ("cover".equals(pendingCaptureTarget)) {
                profileCoverPath = normalized;
                applied = true;
            }
            if (applied) {
                persist();
            }
        }
        pendingCaptureTarget = "";
        return applied;
    }

    public static PostEntry create(String authorUuid, String authorName, String body, String imagePath) {
        ensureLoaded();
        String normalizedBody = normalizeBody(body, MAX_POST_LENGTH);
        String normalizedImage = imagePath == null ? "" : imagePath;
        if (normalizedBody.isBlank() && normalizedImage.isBlank()) {
            return null;
        }

        String safeName = authorName == null || authorName.isBlank() ? "Player" : authorName.strip();
        PostEntry entry = new PostEntry(
                UUID.randomUUID().toString(),
                authorUuid == null ? "" : authorUuid,
                safeName,
                handleFor(safeName),
                normalizedBody,
                normalizedImage,
                System.currentTimeMillis(),
                0,
                0,
                false,
                false,
                false
        );
        POSTS.add(entry);
        persist();
        return entry;
    }

    public static CommentEntry addComment(String postId, String authorUuid, String authorName, String body) {
        return addComment(postId, "", authorUuid, authorName, body);
    }

    public static CommentEntry addReply(
            String postId,
            String parentCommentId,
            String authorUuid,
            String authorName,
            String body
    ) {
        return addComment(postId, parentCommentId, authorUuid, authorName, body);
    }

    private static CommentEntry addComment(
            String postId,
            String parentCommentId,
            String authorUuid,
            String authorName,
            String body
    ) {
        ensureLoaded();
        if (find(postId) == null) {
            return null;
        }
        String normalizedParent = parentCommentId == null ? "" : parentCommentId;
        if (!normalizedParent.isBlank()) {
            boolean parentExists = false;
            for (CommentEntry comment : commentsForAllPosts()) {
                if (comment.id().equals(normalizedParent)) {
                    parentExists = true;
                    break;
                }
            }
            if (!parentExists) {
                normalizedParent = "";
            }
        }
        String normalizedBody = normalizeBody(body, MAX_COMMENT_LENGTH);
        if (SERVER_POSTS.stream().anyMatch(serverPost -> serverPost.id().equals(postId))) {
            if (!normalizedBody.isBlank()) {
                if (normalizedParent.isBlank()) PhoneSocialClientData.addBlockPostComment(postId, normalizedBody);
                else PhoneSocialClientData.addBlockPostReply(postId, normalizedParent, normalizedBody);
            }
            return null;
        }
        if (normalizedBody.isBlank()) {
            return null;
        }
        String safeName = authorName == null || authorName.isBlank() ? "Player" : authorName.strip();
        CommentEntry entry = new CommentEntry(
                UUID.randomUUID().toString(),
                postId,
                normalizedParent,
                authorUuid == null ? "" : authorUuid,
                safeName,
                handleFor(safeName),
                normalizedBody,
                System.currentTimeMillis(),
                0,
                false
        );
        COMMENTS.add(entry);
        persist();
        return entry;
    }

    public static void toggleLike(String id) {
        ensureLoaded();
        if (SERVER_POSTS.stream().anyMatch(post -> post.id().equals(id))) {
            PhoneSocialClientData.toggleBlockPostLike(id);
            return;
        }
        for (int i = 0; i < POSTS.size(); i++) {
            PostEntry post = POSTS.get(i);
            if (!post.id().equals(id)) {
                continue;
            }
            boolean liked = !post.liked();
            POSTS.set(i, post.withLike(liked, Math.max(0, post.likes() + (liked ? 1 : -1))));
            persist();
            return;
        }
    }

    public static void toggleRepost(String id) {
        ensureLoaded();
        if (SERVER_POSTS.stream().anyMatch(post -> post.id().equals(id))) {
            PhoneSocialClientData.toggleBlockPostRepost(id);
            return;
        }
        for (int i = 0; i < POSTS.size(); i++) {
            PostEntry post = POSTS.get(i);
            if (!post.id().equals(id)) {
                continue;
            }
            boolean reposted = !post.reposted();
            POSTS.set(i, post.withRepost(reposted, Math.max(0, post.reposts() + (reposted ? 1 : -1))));
            persist();
            return;
        }
    }

    public static void toggleBookmark(String id) {
        ensureLoaded();
        for (int i = 0; i < POSTS.size(); i++) {
            PostEntry post = POSTS.get(i);
            if (post.id().equals(id)) {
                POSTS.set(i, post.withBookmarked(!post.bookmarked()));
                persist();
                return;
            }
        }
    }

    public static void delete(String id, String selfUuid) {
        ensureLoaded();
        if (id == null || id.isBlank() || selfUuid == null || selfUuid.isBlank()) return;
        PostEntry post = find(id);
        if (post == null || !selfUuid.equals(post.authorUuid())) return;
        if (SERVER_POSTS.stream().anyMatch(serverPost -> serverPost.id().equals(id))) {
            PhoneSocialClientData.deleteBlockPost(id);
            return;
        }
        if (POSTS.removeIf(localPost -> localPost.id().equals(id) && localPost.authorUuid().equals(selfUuid))) {
            COMMENTS.removeIf(comment -> comment.postId().equals(id));
            persist();
        }
    }

    private static String normalizeBody(String body, int maxLength) {
        String value = body == null ? "" : body.strip();
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
        }
        return value;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        POSTS.clear();
        COMMENTS.clear();

        if (!Files.exists(POSTS_FILE)) {
            persist();
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(POSTS_FILE)) {
            properties.load(input);
        } catch (IOException ignored) {
            return;
        }

        profileBio = decode(properties.getProperty("profile.bio", "Minecraft oyuncusu • X kullanıcısı"));
        profileAvatarPath = decode(properties.getProperty("profile.avatarPath", ""));
        profileCoverPath = decode(properties.getProperty("profile.coverPath", ""));

        String ids = properties.getProperty("ids", "");
        for (String id : ids.split(",")) {
            if (id.isBlank()) {
                continue;
            }
            String prefix = "post." + id + ".";
            String authorUuid = decode(properties.getProperty(prefix + "authorUuid", ""));
            String authorName = decode(properties.getProperty(prefix + "authorName", ""));
            String handle = decode(properties.getProperty(prefix + "handle", ""));
            String body = decode(properties.getProperty(prefix + "body", ""));
            String imagePath = decode(properties.getProperty(prefix + "imagePath", ""));
            long createdAt = parseLong(properties.getProperty(prefix + "createdAt", "0"), 0L);
            int likes = (int) parseLong(properties.getProperty(prefix + "likes", "0"), 0L);
            int reposts = (int) parseLong(properties.getProperty(prefix + "reposts", "0"), 0L);
            boolean liked = Boolean.parseBoolean(properties.getProperty(prefix + "liked", "false"));
            boolean reposted = Boolean.parseBoolean(properties.getProperty(prefix + "reposted", "false"));
            boolean bookmarked = Boolean.parseBoolean(properties.getProperty(prefix + "bookmarked", "false"));
            POSTS.add(new PostEntry(id, authorUuid, authorName, handle, body, imagePath, createdAt, likes, reposts, liked, reposted, bookmarked));
        }

        String commentIds = properties.getProperty("commentIds", "");
        for (String id : commentIds.split(",")) {
            if (id.isBlank()) {
                continue;
            }
            String prefix = "comment." + id + ".";
            String postId = decode(properties.getProperty(prefix + "postId", ""));
            String parentCommentId = decode(properties.getProperty(prefix + "parentCommentId", ""));
            String authorUuid = decode(properties.getProperty(prefix + "authorUuid", ""));
            String authorName = decode(properties.getProperty(prefix + "authorName", ""));
            String handle = decode(properties.getProperty(prefix + "handle", ""));
            String body = decode(properties.getProperty(prefix + "body", ""));
            long createdAt = parseLong(properties.getProperty(prefix + "createdAt", "0"), 0L);
            int likes = (int) parseLong(properties.getProperty(prefix + "likes", "0"), 0L);
            boolean liked = Boolean.parseBoolean(properties.getProperty(prefix + "liked", "false"));
            if (!postId.isBlank() && !body.isBlank()) {
                COMMENTS.add(new CommentEntry(id, postId, parentCommentId, authorUuid, authorName, handle, body, createdAt, likes, liked));
            }
        }

        boolean changed = POSTS.removeIf(post -> LEGACY_SAMPLE_POST_IDS.contains(post.id()));
        changed |= COMMENTS.removeIf(comment -> "comment-welcome-1".equals(comment.id())
                || LEGACY_SAMPLE_POST_IDS.contains(comment.postId()));
        if (changed) {
            persist();
        }
    }

    private static void persist() {
        Properties properties = new Properties();
        properties.setProperty("profile.bio", encode(profileBio));
        properties.setProperty("profile.avatarPath", encode(profileAvatarPath));
        properties.setProperty("profile.coverPath", encode(profileCoverPath));
        properties.setProperty("ids", String.join(",", POSTS.stream().map(PostEntry::id).toList()));
        for (PostEntry post : POSTS) {
            String prefix = "post." + post.id() + ".";
            properties.setProperty(prefix + "authorUuid", encode(post.authorUuid()));
            properties.setProperty(prefix + "authorName", encode(post.authorName()));
            properties.setProperty(prefix + "handle", encode(post.handle()));
            properties.setProperty(prefix + "body", encode(post.body()));
            properties.setProperty(prefix + "imagePath", encode(post.imagePath()));
            properties.setProperty(prefix + "createdAt", Long.toString(post.createdAt()));
            properties.setProperty(prefix + "likes", Integer.toString(post.likes()));
            properties.setProperty(prefix + "reposts", Integer.toString(post.reposts()));
            properties.setProperty(prefix + "liked", Boolean.toString(post.liked()));
            properties.setProperty(prefix + "reposted", Boolean.toString(post.reposted()));
            properties.setProperty(prefix + "bookmarked", Boolean.toString(post.bookmarked()));
        }

        properties.setProperty("commentIds", String.join(",", COMMENTS.stream().map(CommentEntry::id).toList()));
        for (CommentEntry comment : COMMENTS) {
            String prefix = "comment." + comment.id() + ".";
            properties.setProperty(prefix + "postId", encode(comment.postId()));
            properties.setProperty(prefix + "parentCommentId", encode(comment.parentCommentId()));
            properties.setProperty(prefix + "authorUuid", encode(comment.authorUuid()));
            properties.setProperty(prefix + "authorName", encode(comment.authorName()));
            properties.setProperty(prefix + "handle", encode(comment.handle()));
            properties.setProperty(prefix + "body", encode(comment.body()));
            properties.setProperty(prefix + "createdAt", Long.toString(comment.createdAt()));
            properties.setProperty(prefix + "likes", Integer.toString(comment.likes()));
            properties.setProperty(prefix + "liked", Boolean.toString(comment.liked()));
        }

        try {
            Files.createDirectories(POSTS_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(POSTS_FILE)) {
                properties.store(output, "Z-Phone BlockPost feed");
            }
        } catch (IOException ignored) {
        }
    }

    private static String handleFor(String name) {
        String normalized = name.toLowerCase()
                .replace('ç', 'c')
                .replace('ğ', 'g')
                .replace('ı', 'i')
                .replace('ö', 'o')
                .replace('ş', 's')
                .replace('ü', 'u')
                .replaceAll("[^a-z0-9_]", "");
        if (normalized.isBlank()) {
            normalized = "player";
        }
        return "@" + normalized;
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
            String handle,
            String body,
            String imagePath,
            long createdAt,
            int likes,
            int reposts,
            boolean liked,
            boolean reposted,
            boolean bookmarked
    ) {
        public PostEntry withLike(boolean newLiked, int newLikes) {
            return new PostEntry(id, authorUuid, authorName, handle, body, imagePath, createdAt, newLikes, reposts, newLiked, reposted, bookmarked);
        }

        public PostEntry withRepost(boolean newReposted, int newReposts) {
            return new PostEntry(id, authorUuid, authorName, handle, body, imagePath, createdAt, likes, newReposts, liked, newReposted, bookmarked);
        }

        public PostEntry withBookmarked(boolean newBookmarked) {
            return new PostEntry(id, authorUuid, authorName, handle, body, imagePath, createdAt, likes, reposts, liked, reposted, newBookmarked);
        }
    }

    public record CommentEntry(
            String id,
            String postId,
            String parentCommentId,
            String authorUuid,
            String authorName,
            String handle,
            String body,
            long createdAt,
            int likes,
            boolean liked
    ) {
        public CommentEntry withLike(boolean newLiked, int newLikes) {
            return new CommentEntry(
                    id,
                    postId,
                    parentCommentId,
                    authorUuid,
                    authorName,
                    handle,
                    body,
                    createdAt,
                    newLikes,
                    newLiked
            );
        }
    }
}

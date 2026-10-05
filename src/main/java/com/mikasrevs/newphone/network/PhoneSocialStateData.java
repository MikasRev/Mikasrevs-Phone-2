package com.mikasrevs.newphone.network;

import java.util.ArrayList;
import java.util.List;

/** Server-authoritative social data used by BlockGram and BlockPost. */
public final class PhoneSocialStateData {
    public long revision;
    public List<ArchivedPostEntry> archivedPosts = new ArrayList<>();
    public static final class ArchivedPostEntry {
        public String id, platform, body, imageRef;
        public long createdAt;
        public ArchivedPostEntry(String id,String platform,String body,String imageRef,long createdAt) {
            this.id=id;this.platform=platform;this.body=body;this.imageRef=imageRef;this.createdAt=createdAt;
        }
    }
    public List<GramPostEntry> blockGramPosts = new ArrayList<>();
    public List<GramStoryEntry> blockGramStories = new ArrayList<>();
    public List<GramCommentEntry> blockGramComments = new ArrayList<>();
    public List<BlockPostEntry> blockPostPosts = new ArrayList<>();
    public List<BlockPostCommentEntry> blockPostComments = new ArrayList<>();
    public List<FollowEntry> follows = new ArrayList<>();
    public List<NotificationEntry> notifications = new ArrayList<>();
    public List<ProfileEntry> profiles = new ArrayList<>();
    /** Viewer-specific Tinder state; never exposes another player's private likes. */
    public List<String> tinderLikedUuids = new ArrayList<>();
    public List<String> tinderPassedUuids = new ArrayList<>();
    public List<String> tinderMatchUuids = new ArrayList<>();

    public static final class GramPostEntry {
        public String id = "";
        public String authorUuid = "";
        public String authorName = "";
        public String caption = "";
        public String imageRef = "";
        public long createdAt;
        public boolean feedVisible = true;
        public List<String> likedBy = new ArrayList<>();

        public GramPostEntry() {
        }

        public GramPostEntry(String id, String authorUuid, String authorName, String caption, String imageRef, long createdAt, List<String> likedBy) {
            this(id, authorUuid, authorName, caption, imageRef, createdAt, true, likedBy);
        }

        public GramPostEntry(String id, String authorUuid, String authorName, String caption, String imageRef, long createdAt, boolean feedVisible, List<String> likedBy) {
            this.id = id;
            this.authorUuid = authorUuid;
            this.authorName = authorName;
            this.caption = caption;
            this.imageRef = imageRef;
            this.createdAt = createdAt;
            this.feedVisible = feedVisible;
            this.likedBy = likedBy == null ? new ArrayList<>() : new ArrayList<>(likedBy);
        }
    }

    public static final class GramStoryEntry {
        public String id = "";
        public String authorUuid = "";
        public String authorName = "";
        public String imageRef = "";
        public long createdAt;
        public boolean seen;
        public List<String> seenByNames = new ArrayList<>();

        public GramStoryEntry() {
        }

        public GramStoryEntry(String id, String authorUuid, String authorName, String imageRef, long createdAt) {
            this(id, authorUuid, authorName, imageRef, createdAt, false, List.of());
        }

        public GramStoryEntry(String id, String authorUuid, String authorName, String imageRef, long createdAt, boolean seen) {
            this(id, authorUuid, authorName, imageRef, createdAt, seen, List.of());
        }

        public GramStoryEntry(String id, String authorUuid, String authorName, String imageRef, long createdAt, boolean seen, List<String> seenByNames) {
            this.id = id;
            this.authorUuid = authorUuid;
            this.authorName = authorName;
            this.imageRef = imageRef;
            this.createdAt = createdAt;
            this.seen = seen;
            this.seenByNames = seenByNames == null ? new ArrayList<>() : new ArrayList<>(seenByNames);
        }
    }

    public static final class GramCommentEntry {
        public String id = "";
        public String postId = "";
        public String authorUuid = "";
        public String authorName = "";
        public String body = "";
        public long createdAt;
        public List<String> likedBy = new ArrayList<>();

        public GramCommentEntry() {
        }

        public GramCommentEntry(String id, String postId, String authorUuid, String authorName, String body, long createdAt, List<String> likedBy) {
            this.id = id;
            this.postId = postId;
            this.authorUuid = authorUuid;
            this.authorName = authorName;
            this.body = body;
            this.createdAt = createdAt;
            this.likedBy = likedBy == null ? new ArrayList<>() : new ArrayList<>(likedBy);
        }
    }

    public static final class BlockPostEntry {
        public String id = "";
        public String authorUuid = "";
        public String authorName = "";
        public String handle = "";
        public String body = "";
        public String imageRef = "";
        public long createdAt;
        public boolean feedVisible = true;
        public List<String> likedBy = new ArrayList<>();
        public List<String> repostedBy = new ArrayList<>();

        public BlockPostEntry() {
        }

        public BlockPostEntry(
                String id,
                String authorUuid,
                String authorName,
                String handle,
                String body,
                String imageRef,
                long createdAt,
                List<String> likedBy,
                List<String> repostedBy
        ) {
            this(id, authorUuid, authorName, handle, body, imageRef, createdAt, true, likedBy, repostedBy);
        }

        public BlockPostEntry(
                String id, String authorUuid, String authorName, String handle, String body, String imageRef,
                long createdAt, boolean feedVisible, List<String> likedBy, List<String> repostedBy
        ) {
            this.id = id;
            this.authorUuid = authorUuid;
            this.authorName = authorName;
            this.handle = handle;
            this.body = body;
            this.imageRef = imageRef;
            this.createdAt = createdAt;
            this.feedVisible = feedVisible;
            this.likedBy = likedBy == null ? new ArrayList<>() : new ArrayList<>(likedBy);
            this.repostedBy = repostedBy == null ? new ArrayList<>() : new ArrayList<>(repostedBy);
        }
    }

    public static final class BlockPostCommentEntry {
        public String id = "";
        public String postId = "";
        public String parentCommentId = "";
        public String authorUuid = "";
        public String authorName = "";
        public String handle = "";
        public String body = "";
        public long createdAt;
        public List<String> likedBy = new ArrayList<>();

        public BlockPostCommentEntry() {
        }

        public BlockPostCommentEntry(
                String id,
                String postId,
                String parentCommentId,
                String authorUuid,
                String authorName,
                String handle,
                String body,
                long createdAt,
                List<String> likedBy
        ) {
            this.id = id;
            this.postId = postId;
            this.parentCommentId = parentCommentId;
            this.authorUuid = authorUuid;
            this.authorName = authorName;
            this.handle = handle;
            this.body = body;
            this.createdAt = createdAt;
            this.likedBy = likedBy == null ? new ArrayList<>() : new ArrayList<>(likedBy);
        }
    }

    public static final class FollowEntry {
        public String id = "";
        public String platform = "BLOCKGRAM";
        public String fromUuid = "";
        public String fromName = "";
        public String toUuid = "";
        public String toName = "";
        public String status = "PENDING";
        public long createdAt;

        public FollowEntry() {
        }

        public FollowEntry(String id, String platform, String fromUuid, String fromName, String toUuid, String toName, String status, long createdAt) {
            this.id = id;
            this.platform = platform;
            this.fromUuid = fromUuid;
            this.fromName = fromName;
            this.toUuid = toUuid;
            this.toName = toName;
            this.status = status;
            this.createdAt = createdAt;
        }
    }

    public static final class NotificationEntry {
        public String id = "";
        public String platform = "BLOCKGRAM";
        public String type = "INFO";
        public String actorUuid = "";
        public String actorName = "";
        public String targetUuid = "";
        public String text = "";
        public String postId = "";
        public long createdAt;
        public boolean read;

        public NotificationEntry() {
        }

        public NotificationEntry(
                String id,
                String platform,
                String type,
                String actorUuid,
                String actorName,
                String targetUuid,
                String text,
                String postId,
                long createdAt,
                boolean read
        ) {
            this.id = id;
            this.platform = platform;
            this.type = type;
            this.actorUuid = actorUuid;
            this.actorName = actorName;
            this.targetUuid = targetUuid;
            this.text = text;
            this.postId = postId;
            this.createdAt = createdAt;
            this.read = read;
        }
    }

    public static final class ProfileEntry {
        public String playerUuid = "";
        public String playerName = "";
        public String blockGramBio = "";
        public String blockGramPhotoRef = "";
        public String blockPostBio = "";
        public String blockPostAvatarRef = "";
        public String blockPostCoverRef = "";
        public String tinderBio = "";
        public String tinderPhotoRef = "";
        public boolean tinderEnabled = true;
        /** Tinder profile age. Always normalized to 18-99 by the server. */
        public int tinderAge = 18;
        /** MALE, FEMALE, OTHER or UNSPECIFIED. */
        public String tinderGender = "UNSPECIFIED";
        /** Discovery preference: MALE, FEMALE or EVERYONE. */
        public String tinderShowGender = "EVERYONE";
        public int tinderMinAge = 18;
        public int tinderMaxAge = 99;
        public String roleBadge = "PL";

        public ProfileEntry() {
        }

        public ProfileEntry(
                String playerUuid, String playerName, String blockGramBio, String blockGramPhotoRef,
                String blockPostAvatarRef, String blockPostCoverRef
        ) {
            this(playerUuid, playerName, blockGramBio, blockGramPhotoRef, "", blockPostAvatarRef, blockPostCoverRef, "PL");
        }

        public ProfileEntry(
                String playerUuid, String playerName, String blockGramBio, String blockGramPhotoRef,
                String blockPostBio, String blockPostAvatarRef, String blockPostCoverRef, String roleBadge
        ) {
            this(playerUuid, playerName, blockGramBio, blockGramPhotoRef, blockPostBio, blockPostAvatarRef,
                    blockPostCoverRef, "Minecraft oyuncusu • Yeni insanlarla tanışmaya açık.", "", true,
                    18, "UNSPECIFIED", "EVERYONE", 18, 99, roleBadge);
        }

        public ProfileEntry(
                String playerUuid, String playerName, String blockGramBio, String blockGramPhotoRef,
                String blockPostBio, String blockPostAvatarRef, String blockPostCoverRef,
                String tinderBio, String tinderPhotoRef, boolean tinderEnabled, String roleBadge
        ) {
            this(playerUuid, playerName, blockGramBio, blockGramPhotoRef, blockPostBio, blockPostAvatarRef,
                    blockPostCoverRef, tinderBio, tinderPhotoRef, tinderEnabled,
                    18, "UNSPECIFIED", "EVERYONE", 18, 99, roleBadge);
        }

        public ProfileEntry(
                String playerUuid, String playerName, String blockGramBio, String blockGramPhotoRef,
                String blockPostBio, String blockPostAvatarRef, String blockPostCoverRef,
                String tinderBio, String tinderPhotoRef, boolean tinderEnabled,
                int tinderAge, String tinderGender, String tinderShowGender,
                int tinderMinAge, int tinderMaxAge, String roleBadge
        ) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.blockGramBio = blockGramBio;
            this.blockGramPhotoRef = blockGramPhotoRef;
            this.blockPostBio = blockPostBio;
            this.blockPostAvatarRef = blockPostAvatarRef;
            this.blockPostCoverRef = blockPostCoverRef;
            this.tinderBio = tinderBio == null ? "" : tinderBio;
            this.tinderPhotoRef = tinderPhotoRef == null ? "" : tinderPhotoRef;
            this.tinderEnabled = tinderEnabled;
            this.tinderAge = tinderAge;
            this.tinderGender = tinderGender == null ? "UNSPECIFIED" : tinderGender;
            this.tinderShowGender = tinderShowGender == null ? "EVERYONE" : tinderShowGender;
            this.tinderMinAge = tinderMinAge;
            this.tinderMaxAge = tinderMaxAge;
            this.roleBadge = roleBadge == null || roleBadge.isBlank() ? "PL" : roleBadge;
        }
    }
}

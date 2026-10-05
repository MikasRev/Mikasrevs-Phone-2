package com.mikasrevs.newphone.client.data;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.google.gson.Gson;
import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore;
import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import com.mikasrevs.newphone.network.PhoneNetwork;
import com.mikasrevs.newphone.network.message.PhoneSocialAssetUploadMessage;
import com.mikasrevs.newphone.network.message.PhoneSocialPostUploadMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Client-side bridge for the server-authoritative BlockGram/BlockPost social state. */
public final class PhoneSocialClientData {
    private static final Gson GSON = new Gson();
    private static final Map<String, PhoneSocialStateData.ProfileEntry> PROFILES = new HashMap<>();
    private static final List<PhoneSocialStateData.FollowEntry> FOLLOWS = new ArrayList<>();
    private static final List<PhoneSocialStateData.NotificationEntry> NOTIFICATIONS = new ArrayList<>();
    private static final Set<String> TINDER_LIKES = new LinkedHashSet<>();
    private static final Set<String> TINDER_PASSES = new LinkedHashSet<>();
    private static final Set<String> TINDER_MATCHES = new LinkedHashSet<>();
    private static List<PhoneSocialStateData.ProfileEntry> SORTED_PROFILES = List.of();
    private static final Map<String, Map<String, PhoneSocialStateData.FollowEntry>> LATEST_RELATIONS_BY_PLATFORM = new HashMap<>();
    private static long revision = -1L;
    private static PhoneSocialStateData latest = new PhoneSocialStateData();
    public static PhoneSocialStateData latestState() { return latest; }

    private PhoneSocialClientData() {
    }

    /** Lightweight social metadata refresh; cached images are not resent. */
    public static void requestSync() {
        sendAction("SOCIAL_SYNC", "", "");
    }

    /** Full social refresh used once after joining/reconnecting to restore image caches. */
    public static void requestFullSync() {
        sendAction("SOCIAL_SYNC_FULL", "", "");
    }

    public static void requestProfileAssets(String targetUuid) {
        if (targetUuid == null || targetUuid.isBlank()) return;
        sendAction("SOCIAL_PROFILE_ASSETS", targetUuid, "");
    }

    public static void applyStateJson(String json) {
        PhoneSocialStateData state = GSON.fromJson(json, PhoneSocialStateData.class);
        if (state == null) state = new PhoneSocialStateData();
        latest = state;
        revision = state.revision;
        PROFILES.clear();
        FOLLOWS.clear();
        NOTIFICATIONS.clear();
        TINDER_LIKES.clear();
        TINDER_PASSES.clear();
        TINDER_MATCHES.clear();
        if (state.profiles != null) {
            for (PhoneSocialStateData.ProfileEntry profile : state.profiles) {
                if (profile != null && profile.playerUuid != null && !profile.playerUuid.isBlank()) {
                    PROFILES.put(profile.playerUuid, profile);
                }
            }
        }
        if (state.follows != null) FOLLOWS.addAll(state.follows);
        if (state.notifications != null) NOTIFICATIONS.addAll(state.notifications);
        if (state.tinderLikedUuids != null) TINDER_LIKES.addAll(state.tinderLikedUuids);
        if (state.tinderPassedUuids != null) TINDER_PASSES.addAll(state.tinderPassedUuids);
        if (state.tinderMatchUuids != null) TINDER_MATCHES.addAll(state.tinderMatchUuids);
        rebuildIndexes();

        String selfUuid = PhoneClientData.selfUuid();
        PhoneBlockGramStore.applyServerPosts(state.blockGramPosts, selfUuid);
        PhoneBlockGramStore.applyServerStories(state.blockGramStories);
        PhoneBlockGramStore.applyServerComments(state.blockGramComments, selfUuid);
        PhoneBlockGramStore.applyServerFollows(state.follows);
        PhoneBlockGramStore.applyServerNotifications(state.notifications);
        PhoneBlockPostStore.applyServerPosts(state.blockPostPosts, selfUuid);
        PhoneBlockPostStore.applyServerComments(state.blockPostComments, selfUuid);

        PhoneSocialStateData.ProfileEntry self = PROFILES.get(selfUuid);
        if (self != null) {
            PhoneBlockGramStore.applyServerProfile(self.blockGramBio, socialPath(self.blockGramPhotoRef));
            PhoneBlockPostStore.applyServerProfile(self.blockPostBio, socialPath(self.blockPostAvatarRef), socialPath(self.blockPostCoverRef));
        }
    }

    public static List<PhoneSocialStateData.NotificationEntry> notificationSnapshot() {
        return new ArrayList<>(NOTIFICATIONS);
    }

    public static List<PhoneSocialStateData.FollowEntry> followSnapshot() {
        return new ArrayList<>(FOLLOWS);
    }

    public static long revision() { return revision; }
    public static boolean hasServerProfile(String playerUuid) { return playerUuid != null && PROFILES.containsKey(playerUuid); }
    public static PhoneSocialStateData.ProfileEntry profile(String playerUuid) { return playerUuid == null ? null : PROFILES.get(playerUuid); }

    public static List<PhoneSocialStateData.ProfileEntry> profiles() {
        return SORTED_PROFILES;
    }

    public static String followState(String platform, String selfUuid, String targetUuid) {
        if (selfUuid == null || targetUuid == null) return "NONE";
        return FOLLOWS.stream()
                .filter(f -> platform.equalsIgnoreCase(f.platform) && selfUuid.equals(f.fromUuid) && targetUuid.equals(f.toUuid))
                .max(Comparator.comparingLong(f -> f.createdAt))
                .map(f -> f.status == null ? "NONE" : f.status)
                .orElse("NONE");
    }

    public static int followerCount(String platform, String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) return 0;
        return (int) latestRelations(platform).values().stream()
                .filter(f -> "ACCEPTED".equals(f.status) && playerUuid.equals(f.toUuid)).count();
    }

    public static int followingCount(String platform, String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) return 0;
        return (int) latestRelations(platform).values().stream()
                .filter(f -> "ACCEPTED".equals(f.status) && playerUuid.equals(f.fromUuid)).count();
    }

    public static List<PhoneSocialStateData.ProfileEntry> followers(String platform, String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) return List.of();
        return latestRelations(platform).values().stream()
                .filter(f -> "ACCEPTED".equals(f.status) && playerUuid.equals(f.toUuid))
                .map(f -> PROFILES.get(f.fromUuid))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(profile -> profile.playerName == null ? "" : profile.playerName.toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    public static List<PhoneSocialStateData.ProfileEntry> following(String platform, String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) return List.of();
        return latestRelations(platform).values().stream()
                .filter(f -> "ACCEPTED".equals(f.status) && playerUuid.equals(f.fromUuid))
                .map(f -> PROFILES.get(f.toUuid))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(profile -> profile.playerName == null ? "" : profile.playerName.toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    private static Map<String, PhoneSocialStateData.FollowEntry> latestRelations(String platform) {
        if (platform == null || platform.isBlank()) return Map.of();
        return LATEST_RELATIONS_BY_PLATFORM.getOrDefault(platform.toUpperCase(Locale.ROOT), Map.of());
    }

    private static void rebuildIndexes() {
        SORTED_PROFILES = PROFILES.values().stream()
                .sorted(Comparator.comparing(profile ->
                        profile.playerName == null ? "" : profile.playerName.toLowerCase(Locale.ROOT)))
                .toList();

        LATEST_RELATIONS_BY_PLATFORM.clear();
        for (PhoneSocialStateData.FollowEntry follow : FOLLOWS) {
            if (follow == null || follow.platform == null || follow.platform.isBlank()) continue;
            String platform = follow.platform.toUpperCase(Locale.ROOT);
            Map<String, PhoneSocialStateData.FollowEntry> latest =
                    LATEST_RELATIONS_BY_PLATFORM.computeIfAbsent(platform, ignored -> new HashMap<>());
            String key = (follow.fromUuid == null ? "" : follow.fromUuid)
                    + "\u0000"
                    + (follow.toUuid == null ? "" : follow.toUuid);
            PhoneSocialStateData.FollowEntry current = latest.get(key);
            if (current == null || follow.createdAt >= current.createdAt) latest.put(key, follow);
        }
    }

    /** Clears server-specific social state when disconnecting or switching worlds. */
    public static void resetClientState() {
        latest = new PhoneSocialStateData();
        revision = -1L;
        PROFILES.clear();
        FOLLOWS.clear();
        NOTIFICATIONS.clear();
        TINDER_LIKES.clear();
        TINDER_PASSES.clear();
        TINDER_MATCHES.clear();
        SORTED_PROFILES = List.of();
        LATEST_RELATIONS_BY_PLATFORM.clear();

        PhoneBlockGramStore.applyServerPosts(List.of(), "");
        PhoneBlockGramStore.applyServerStories(List.of());
        PhoneBlockGramStore.applyServerComments(List.of(), "");
        PhoneBlockGramStore.applyServerFollows(List.of());
        PhoneBlockGramStore.applyServerNotifications(List.of());
        PhoneBlockPostStore.applyServerPosts(List.of(), "");
        PhoneBlockPostStore.applyServerComments(List.of(), "");
    }

    public static List<PhoneSocialStateData.ProfileEntry> profilesMatching(String queryRaw) {
        String query = queryRaw == null ? "" : queryRaw.trim().toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty()) return profiles();
        return profiles().stream().filter(profile -> {
            String name = profile.playerName == null ? "" : profile.playerName.toLowerCase(java.util.Locale.ROOT);
            String gramBio = profile.blockGramBio == null ? "" : profile.blockGramBio.toLowerCase(java.util.Locale.ROOT);
            String postBio = profile.blockPostBio == null ? "" : profile.blockPostBio.toLowerCase(java.util.Locale.ROOT);
            return name.contains(query) || gramBio.contains(query) || postBio.contains(query)
                    || ("@" + name.replace(" ", "")).contains(query);
        }).toList();
    }

    public static List<PhoneSocialStateData.FollowEntry> incomingFollowRequests(String platform, String selfUuid) {
        return FOLLOWS.stream().filter(f -> platform.equalsIgnoreCase(f.platform)
                && "PENDING".equals(f.status) && selfUuid.equals(f.toUuid))
                .sorted(Comparator.comparingLong((PhoneSocialStateData.FollowEntry f) -> f.createdAt).reversed()).toList();
    }

    public static List<PhoneSocialStateData.FollowEntry> outgoingFollowRequests(String platform, String selfUuid) {
        return FOLLOWS.stream().filter(f -> platform.equalsIgnoreCase(f.platform)
                && "PENDING".equals(f.status) && selfUuid.equals(f.fromUuid))
                .sorted(Comparator.comparingLong((PhoneSocialStateData.FollowEntry f) -> f.createdAt).reversed()).toList();
    }

    public static PhoneSocialStateData.ProfileEntry profileByNickname(String nicknameRaw) {
        String nickname = nicknameRaw == null ? "" : nicknameRaw.trim();
        if (nickname.startsWith("@")) nickname = nickname.substring(1);
        for (PhoneSocialStateData.ProfileEntry profile : PROFILES.values()) {
            String name = profile.playerName == null ? "" : profile.playerName;
            if (name.equalsIgnoreCase(nickname) || name.replace(" ", "").equalsIgnoreCase(nickname.replace(" ", ""))) return profile;
        }
        return null;
    }

    public static List<PhoneSocialStateData.NotificationEntry> notifications(String platform, String selfUuid) {
        return NOTIFICATIONS.stream().filter(n -> platform.equalsIgnoreCase(n.platform) && selfUuid.equals(n.targetUuid))
                .sorted(Comparator.comparingLong((PhoneSocialStateData.NotificationEntry n) -> n.createdAt).reversed()).toList();
    }

    public static int unreadNotificationCount(String platform, String selfUuid) {
        int unread = (int) notifications(platform, selfUuid).stream().filter(n -> !n.read).count();
        int requests = incomingFollowRequests(platform, selfUuid).size();
        return unread + requests;
    }

    public static void toggleGramLike(String postId) { sendAction("SOCIAL_GRAM_TOGGLE_LIKE", postId, ""); }
    public static void addGramComment(String postId, String body) { sendAction("SOCIAL_GRAM_COMMENT", postId, body); }
    public static void toggleGramCommentLike(String commentId) { sendAction("SOCIAL_GRAM_COMMENT_LIKE", commentId, ""); }
    public static void recordGramShare(String postId) { sendAction("SOCIAL_GRAM_SHARE", postId, ""); }
    public static void deleteBlockGram(String postId) { sendAction("SOCIAL_GRAM_DELETE", postId, ""); }
    public static void markGramStoryViewed(String storyId) { sendAction("SOCIAL_GRAM_STORY_VIEW", storyId, ""); }

    public static void toggleBlockPostLike(String postId) { sendAction("SOCIAL_POST_TOGGLE_LIKE", postId, ""); }
    public static void toggleBlockPostRepost(String postId) { sendAction("SOCIAL_POST_TOGGLE_REPOST", postId, ""); }
    public static void addBlockPostComment(String postId, String body) { sendAction("SOCIAL_POST_COMMENT", postId, body); }
    public static void addBlockPostReply(String postId, String parentCommentId, String body) {
        sendAction("SOCIAL_POST_REPLY", postId, (parentCommentId == null ? "" : parentCommentId) + "\n" + (body == null ? "" : body));
    }
    public static void toggleBlockPostCommentLike(String commentId) { sendAction("SOCIAL_POST_COMMENT_LIKE", commentId, ""); }
    public static void deleteBlockPost(String postId) { sendAction("SOCIAL_POST_DELETE", postId, ""); }

    public static void sendFollowRequest(String platform, String targetUuid) { sendAction("SOCIAL_FOLLOW_REQUEST", targetUuid, platform); }
    public static void acceptFollowRequest(String platform, String requestId) { sendAction("SOCIAL_FOLLOW_ACCEPT", requestId, platform); }
    public static void acceptAndFollowBack(String platform, String requestId) { sendAction("SOCIAL_FOLLOW_ACCEPT_BACK", requestId, platform); }
    public static void declineFollowRequest(String platform, String requestId) { sendAction("SOCIAL_FOLLOW_DECLINE", requestId, platform); }
    public static void removeFollow(String platform, String targetUuid) { sendAction("SOCIAL_FOLLOW_REMOVE", targetUuid, platform); }
    public static void markNotificationsRead(String platform) { sendAction("SOCIAL_NOTIFICATIONS_READ", "", platform); }

    public static void clearNotifications(String platform) {
        String safePlatform = platform == null ? "" : platform.trim();
        String selfUuid = PhoneClientData.selfUuid();
        NOTIFICATIONS.removeIf(notification -> safePlatform.equalsIgnoreCase(notification.platform)
                && selfUuid.equals(notification.targetUuid));
        PhoneBlockGramStore.applyServerNotifications(NOTIFICATIONS);
        sendAction("SOCIAL_NOTIFICATIONS_CLEAR", "", safePlatform);
    }

    private static void sendAction(String action, String target, String body) {
        PhoneNetwork.sendAction(action, target == null ? "" : target, body == null ? "" : body);
    }

    public static String blockGramPhoto(String playerUuid) {
        PhoneSocialStateData.ProfileEntry profile = profile(playerUuid);
        return profile == null ? "" : socialPath(profile.blockGramPhotoRef);
    }

    public static String blockPostAvatar(String playerUuid) {
        PhoneSocialStateData.ProfileEntry profile = profile(playerUuid);
        return profile == null ? "" : socialPath(profile.blockPostAvatarRef);
    }

    public static String blockPostCover(String playerUuid) {
        PhoneSocialStateData.ProfileEntry profile = profile(playerUuid);
        return profile == null ? "" : socialPath(profile.blockPostCoverRef);
    }

    public static String blockPostBio(String playerUuid) {
        PhoneSocialStateData.ProfileEntry profile = profile(playerUuid);
        return profile == null || profile.blockPostBio == null ? "" : PhoneSettingsStore.localizeNetworkText(profile.blockPostBio);
    }

    public static String roleBadge(String playerUuid) {
        PhoneSocialStateData.ProfileEntry profile = profile(playerUuid);
        return profile == null || profile.roleBadge == null || profile.roleBadge.isBlank() ? "PL" : profile.roleBadge;
    }

    public static boolean isAcceptedFollow(String platform, String fromUuid, String toUuid) {
        return "ACCEPTED".equals(followState(platform, fromUuid, toUuid));
    }

    public static boolean isMutualFollow(String platform, String firstUuid, String secondUuid) {
        if (firstUuid == null || firstUuid.isBlank() || secondUuid == null || secondUuid.isBlank()) return false;
        return isAcceptedFollow(platform, firstUuid, secondUuid)
                && isAcceptedFollow(platform, secondUuid, firstUuid);
    }

    /** Lumi inbox is built only from mutual Lumi follows; normal Contacts is unrelated. */
    public static List<PhoneSocialStateData.ProfileEntry> mutualBlockGramProfiles() {
        String self = PhoneClientData.selfUuid();
        if (self == null || self.isBlank()) return List.of();
        return profiles().stream()
                .filter(profile -> profile != null && profile.playerUuid != null && !self.equals(profile.playerUuid))
                .filter(profile -> isMutualFollow("BLOCKGRAM", self, profile.playerUuid))
                .toList();
    }

    public static boolean canMessageBlockGram(String targetUuid) {
        if (targetUuid == null || targetUuid.isBlank() || targetUuid.equals(PhoneClientData.selfUuid())) return false;
        return isMutualFollow("BLOCKGRAM", PhoneClientData.selfUuid(), targetUuid);
    }

    public static List<PhoneSocialStateData.ProfileEntry> tinderCandidates() {
        String self = PhoneClientData.selfUuid();
        PhoneSocialStateData.ProfileEntry selfProfile = PROFILES.get(self);
        List<PhoneSocialStateData.ProfileEntry> result = new ArrayList<>();
        for (PhoneSocialStateData.ProfileEntry profile : profiles()) {
            if (profile == null || !profile.tinderEnabled) continue;
            if (profile.playerUuid == null || profile.playerUuid.isBlank() || profile.playerUuid.equals(self)) continue;
            if (PhoneClientData.isBlocked(profile.playerUuid)) continue;
            if (TINDER_LIKES.contains(profile.playerUuid) || TINDER_PASSES.contains(profile.playerUuid)) continue;
            if (tinderProfilesCompatible(selfProfile, profile)) result.add(profile);
        }
        return List.copyOf(result);
    }

    public static List<PhoneSocialStateData.ProfileEntry> tinderMatches() {
        return TINDER_MATCHES.stream()
                .map(PROFILES::get)
                .filter(java.util.Objects::nonNull)
                .filter(profile -> !PhoneClientData.isBlocked(profile.playerUuid))
                .sorted(Comparator.comparing(profile -> profile.playerName == null ? "" : profile.playerName.toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    public static boolean isTinderMatch(String playerUuid) {
        return playerUuid != null && TINDER_MATCHES.contains(playerUuid);
    }

    public static Set<String> tinderMatchUuids() {
        return Set.copyOf(TINDER_MATCHES);
    }

    public static boolean isSocialBlocked(String playerUuid) {
        return playerUuid != null && !playerUuid.isBlank() && PhoneClientData.isBlocked(playerUuid);
    }

    public static void blockSocialUser(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank() || playerUuid.equals(PhoneClientData.selfUuid())) return;
        PhoneClientData.blockPlayer(playerUuid);
    }

    public static void unblockSocialUser(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) return;
        PhoneClientData.unblockPlayer(playerUuid);
    }

    public static void tinderSwipe(String playerUuid, boolean like) {
        if (playerUuid == null || playerUuid.isBlank()) return;
        sendAction("TINDER_SWIPE", playerUuid, like ? "LIKE" : "PASS");
    }

    public static void resetTinderPasses() {
        sendAction("TINDER_RESET_PASSES", "", "");
    }

    public static void updateTinderProfile(String bio, String selectedPhotoPath, boolean enabled) {
        sendAction("TINDER_BIO", "", bio == null ? "" : bio);
        sendAction("TINDER_ENABLED", "", enabled ? "1" : "0");
        if (selectedPhotoPath == null || selectedPhotoPath.isBlank()) {
            sendAction("TINDER_PHOTO_CLEAR", "", "");
        } else if (!selectedPhotoPath.startsWith("social:")) {
            uploadAsset("TINDER_PROFILE", selectedPhotoPath);
        }
    }

    public static void updateTinderSettings(int age, String gender, String showGender, int minAge, int maxAge) {
        int safeAge = clampTinderAge(age);
        int safeMin = clampTinderAge(minAge);
        int safeMax = clampTinderAge(maxAge);
        if (safeMin > safeMax) {
            int swap = safeMin;
            safeMin = safeMax;
            safeMax = swap;
        }
        String body = safeAge + "|" + normalizeGender(gender) + "|" + normalizeShowGender(showGender)
                + "|" + safeMin + "|" + safeMax;
        sendAction("TINDER_SETTINGS", "", body);
    }

    private static boolean tinderProfilesCompatible(
            PhoneSocialStateData.ProfileEntry viewer,
            PhoneSocialStateData.ProfileEntry candidate
    ) {
        if (viewer == null || candidate == null) return true;
        int candidateAge = clampTinderAge(candidate.tinderAge);
        int min = clampTinderAge(viewer.tinderMinAge);
        int max = clampTinderAge(viewer.tinderMaxAge);
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        if (candidateAge < min || candidateAge > max) return false;
        return genderPreferenceAllows(viewer.tinderShowGender, candidate.tinderGender);
    }

    private static boolean genderPreferenceAllows(String preference, String gender) {
        String safePreference = normalizeShowGender(preference);
        if ("EVERYONE".equals(safePreference)) return true;
        return safePreference.equals(normalizeGender(gender));
    }

    private static int clampTinderAge(int age) {
        return Math.max(18, Math.min(99, age <= 0 ? 18 : age));
    }

    private static String normalizeGender(String value) {
        String safe = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (safe) {
            case "MALE", "FEMALE", "OTHER" -> safe;
            default -> "UNSPECIFIED";
        };
    }

    private static String normalizeShowGender(String value) {
        String safe = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (safe) {
            case "MALE", "FEMALE" -> safe;
            default -> "EVERYONE";
        };
    }

    private static boolean canPublish(String platform) {
        String self = PhoneClientData.selfUuid();
        long count = "BLOCKGRAM".equals(platform)
                ? latest.blockGramPosts.stream().filter(p -> self.equals(p.authorUuid)).count()
                : latest.blockPostPosts.stream().filter(p -> self.equals(p.authorUuid)).count();
        if (count < 50) return true;
        PhoneClientData.state().notice = PhoneSettingsStore.text(
                "50 aktif gönderin var. Yer açmak için bir gönderiyi arşivle.",
                "You have 50 active posts. Archive a post to make room.");
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null) com.mikasrevs.newphone.client.zphone.PhoneNavigationState.open(mc, mc.screen,
                new com.mikasrevs.newphone.client.zphone.gui.PhonePostManagerScreen());
        return false;
    }
    public static boolean publishBlockGram(String caption, Path photo) {
        if (!canPublish("BLOCKGRAM")) return false;
        String safeCaption = caption == null ? "" : caption;
        int imageBudget = PhoneSocialPostUploadMessage.maxImageBytesFor("BLOCKGRAM", safeCaption);
        byte[] image = PhonePhotoStore.encodeForMessage(photo, imageBudget);
        if (image == null || image.length == 0) return false;
        PhoneNetwork.sendSocialPostUpload("BLOCKGRAM", safeCaption, image);
        return true;
    }

    public static void publishBlockGramStory(Path photo) {
        int imageBudget = PhoneSocialPostUploadMessage.maxImageBytesFor("BLOCKGRAM_STORY", "");
        byte[] image = PhonePhotoStore.encodeForMessage(photo, imageBudget);
        if (image == null || image.length == 0) return;
        PhoneNetwork.sendSocialPostUpload("BLOCKGRAM_STORY", "", image);
    }

    public static boolean publishBlockPost(String body, String imagePath) {
        if (!canPublish("BLOCKPOST")) return false;
        String safeBody = body == null ? "" : body;
        int imageBudget = PhoneSocialPostUploadMessage.maxImageBytesFor("BLOCKPOST", safeBody);
        byte[] image = encodeOptionalLocalImage(imagePath, imageBudget);
        PhoneNetwork.sendSocialPostUpload("BLOCKPOST", safeBody, image);
        return true;
    }

    public static void updateBlockGramProfile(String bio, String selectedPhotoPath) {
        sendAction("SOCIAL_GRAM_BIO", "", bio == null ? "" : bio);
        if (selectedPhotoPath == null || selectedPhotoPath.isBlank()) {
            sendAction("SOCIAL_GRAM_PHOTO_CLEAR", "", "");
            return;
        }
        if (!selectedPhotoPath.startsWith("social:")) uploadAsset("GRAM_PROFILE", selectedPhotoPath);
    }

    public static void updateBlockPostBio(String bio) { sendAction("SOCIAL_POST_BIO", "", bio == null ? "" : bio); }
    public static void uploadBlockPostAvatar(String pathValue) { uploadAsset("POST_AVATAR", pathValue); }
    public static void uploadBlockPostCover(String pathValue) { uploadAsset("POST_COVER", pathValue); }

    public static void uploadCurrentBlockPostProfile() {
        String avatar = PhoneBlockPostStore.profileAvatarPath();
        String cover = PhoneBlockPostStore.profileCoverPath();
        if (avatar != null && !avatar.isBlank() && !avatar.startsWith("social:")) uploadAsset("POST_AVATAR", avatar);
        if (cover != null && !cover.isBlank() && !cover.startsWith("social:")) uploadAsset("POST_COVER", cover);
    }

    private static void uploadAsset(String kind, String pathValue) {
        byte[] image;
        if ("TINDER_PROFILE".equalsIgnoreCase(kind)) {
            try {
                Path path = Path.of(pathValue);
                image = PhonePhotoStore.encodeForProfile(path, PhoneSocialAssetUploadMessage.MAX_IMAGE_BYTES);
            } catch (RuntimeException ignored) {
                image = null;
            }
        } else {
            image = encodeOptionalLocalImage(pathValue, PhoneSocialAssetUploadMessage.MAX_IMAGE_BYTES);
        }
        if (image == null || image.length == 0) return;
        PhoneNetwork.sendSocialAssetUpload(kind, image);
    }

    private static byte[] encodeOptionalLocalImage(String pathValue) {
        return encodeOptionalLocalImage(pathValue, PhoneSocialAssetUploadMessage.MAX_IMAGE_BYTES);
    }

    private static byte[] encodeOptionalLocalImage(String pathValue, int maxBytes) {
        if (pathValue == null || pathValue.isBlank() || pathValue.startsWith("social:")) return new byte[0];
        try {
            Path path = Path.of(pathValue);
            if (!Files.isRegularFile(path)) return new byte[0];
            byte[] image = PhonePhotoStore.encodeForMessage(path, maxBytes);
            return image == null ? new byte[0] : image;
        } catch (RuntimeException ignored) { return new byte[0]; }
    }

    public static String socialPath(String imageRef) { return imageRef == null || imageRef.isBlank() ? "" : "social:" + imageRef; }
    public static String socialRef(String pathValue) { return pathValue != null && pathValue.startsWith("social:") ? pathValue.substring("social:".length()) : ""; }
}

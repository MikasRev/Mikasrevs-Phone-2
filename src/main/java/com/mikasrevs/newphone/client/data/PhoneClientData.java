package com.mikasrevs.newphone.client.data;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.network.PhoneNetwork;
import com.mikasrevs.newphone.network.PhoneNumberUtil;
import com.mikasrevs.newphone.network.PhoneStateData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.file.Path;
import java.util.UUID;
import com.mikasrevs.newphone.client.data.PhonePhotoStore;

/** Client-side communication snapshot and helpers for the Forge 1.20.1 MPhone port. */
public final class PhoneClientData {
    private static volatile PhoneStateData state = new PhoneStateData();
    private static final Map<String, Long> LAST_OPENED_TIMESTAMPS = new HashMap<>();
    private static final Map<String, Long> LAST_OPENED_GRAM_TIMESTAMPS = new HashMap<>();
    private static final Map<String, Long> LAST_READ_MARK_SENT = new HashMap<>();
    private static final Map<String, Long> LAST_TYPING_SENT_AT = new HashMap<>();
    private static final Map<String, Boolean> TYPING_SENT_STATE = new HashMap<>();

    private PhoneClientData() {
    }

    public static PhoneStateData state() { return state; }
    public static void setState(PhoneStateData newState) {
        state=newState==null?new PhoneStateData():newState;
        if(newState!=null)PhoneMessageClient.merge(newState);
    }
    public static void clear() { com.mikasrevs.newphone.marketplace.client.MarketplaceClient.clear(); PhoneMessageClient.clear(); state = new PhoneStateData(); LAST_OPENED_TIMESTAMPS.clear(); LAST_OPENED_GRAM_TIMESTAMPS.clear(); LAST_READ_MARK_SENT.clear(); TYPING_SENT_STATE.clear(); }

    public static void requestSync() { sendAction("SYNC", "", ""); }
    public static void requestFullSync() { sendAction("SYNC_FULL", "", ""); }
    public static void sendAction(String action, String targetUuid, String body) { PhoneNetwork.sendAction(action, targetUuid, body); }
    public static void updateCallPose(boolean active) { sendAction(active ? "CALL_POSE_START" : "CALL_POSE_STOP", callId(), ""); }

    public static void sendFriendRequest(String nicknameOrNumber) { sendAction("FRIEND_REQUEST", "", safe(nicknameOrNumber)); }
    public static void acceptFriendRequest(String uuid) { sendAction("ACCEPT_FRIEND_REQUEST", uuid, ""); }
    public static void declineFriendRequest(String uuid) { sendAction("DECLINE_FRIEND_REQUEST", uuid, ""); }
    public static void cancelFriendRequest(String uuid) { sendAction("CANCEL_FRIEND_REQUEST", uuid, ""); }
    public static void removeContact(String uuid) { sendAction("REMOVE_CONTACT", uuid, ""); }
    public static void blockPlayer(String uuid) { if (uuid != null && !uuid.isBlank()) sendAction("BLOCK_CONTACT", uuid, ""); }
    public static void unblockPlayer(String uuid) { if (uuid != null && !uuid.isBlank()) sendAction("UNBLOCK_CONTACT", uuid, ""); }

    public static void sendText(String uuid, String text) {
        String trimmed = safe(text).trim();
        if (uuid == null || uuid.isBlank() || trimmed.isEmpty()) return;
        PhoneMessageClient.send(uuid,"TEXT",trimmed,"","",null);
    }

    public static void sendGramText(String uuid, String text) {
        String trimmed = safe(text).trim();
        if (uuid == null || uuid.isBlank() || trimmed.isEmpty() || !PhoneSocialClientData.canMessageBlockGram(uuid)) return;
        PhoneMessageClient.send(uuid,"GRAM_TEXT",trimmed,"","",null);
    }

    public static void sendTinderText(String uuid, String text) {
        String trimmed = safe(text).trim();
        if (uuid == null || uuid.isBlank() || trimmed.isEmpty() || !PhoneSocialClientData.isTinderMatch(uuid)) return;
        PhoneMessageClient.send(uuid,"TINDER_TEXT",trimmed,"","",null);
    }

    public static boolean sendPhoto(String uuid, Path photo) { return sendPhoto(uuid,photo,false,""); }
    public static boolean sendPhoto(String uuid,Path photo,boolean gram,String caption) {
        if(uuid==null||uuid.isBlank()||photo==null||caption.length()>240)return false;
        if(gram&&!PhoneSocialClientData.canMessageBlockGram(uuid))return false;
        byte[] image=PhonePhotoStore.encodeForMessage(photo);if(image==null||image.length==0)return false;
        PhoneMessageClient.send(uuid,gram?"GRAM_PHOTO":"PHOTO","","",caption,image);return true;
    }

    public static boolean sendGramPhoto(String uuid, Path photo) { return sendPhoto(uuid,photo,true,""); }

    public static boolean sendGramSharedPost(String uuid, com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore.PostEntry post) {
        if (uuid == null || uuid.isBlank() || post == null || !PhoneSocialClientData.canMessageBlockGram(uuid)) return false;
        PhoneMessageClient.send(uuid,"GRAM_POST",post.id(),"","",null);
        return true;
    }

    public static void sendLocation(String uuid, String encodedLocation) {
        if (uuid == null || uuid.isBlank()) return;
        String location = safe(encodedLocation).trim();
        if (!location.isEmpty()) PhoneMessageClient.send(uuid,"LOCATION",location,"","",null);
    }

    public static void sendReply(String uuid, String text, String replyToId) {
        sendReply(uuid, text, replyToId, false);
    }

    public static void sendReply(String uuid, String text, String replyToId, boolean gram) {
        String trimmed = safe(text).trim();
        if (uuid == null || uuid.isBlank() || trimmed.isEmpty()) return;
        if (gram && !PhoneSocialClientData.canMessageBlockGram(uuid)) return;
        String reply = safe(replyToId).replace("\n", "").trim();
        PhoneMessageClient.send(uuid,gram?"GRAM_TEXT":"TEXT",trimmed,reply,"",null);
    }

    public static void deleteMessage(String messageId) {
        deleteMessage(messageId, false);
    }

    public static void deleteMessage(String messageId, boolean gram) {
        if(PhoneMessageClient.discard(messageId))return;
        if (messageId != null && !messageId.isBlank()) sendAction(gram ? "DELETE_GRAM_MESSAGE" : "DELETE_MESSAGE", messageId, "");
    }

    public static void deleteTinderMessage(String messageId) {
        if(PhoneMessageClient.discard(messageId))return;
        if (messageId != null && !messageId.isBlank()) sendAction("DELETE_TINDER_MESSAGE", messageId, "");
    }

    public static void markConversationRead(String otherUuid) {
        markConversationRead(otherUuid, false);
    }

    public static void markConversationRead(String otherUuid, boolean gram) {
        if (otherUuid == null || otherUuid.isBlank()) return;
        long newestUnread = 0L;
        List<PhoneStateData.MessageEntry> messages = gram ? gramConversation(otherUuid) : conversation(otherUuid);
        for (PhoneStateData.MessageEntry message : messages) {
            if (!isSelfMessageUuid(message.fromUuid) && !message.read) newestUnread = Math.max(newestUnread, message.timestamp);
        }
        String key = (gram ? "G:" : "M:") + otherUuid;
        if (newestUnread <= 0L || newestUnread <= LAST_READ_MARK_SENT.getOrDefault(key, 0L)) return;
        LAST_READ_MARK_SENT.put(key, newestUnread);
        sendAction(gram ? "MARK_GRAM_READ" : "MARK_READ", otherUuid, "");
    }

    public static void markTinderConversationRead(String otherUuid) {
        if (otherUuid == null || otherUuid.isBlank()) return;
        long newestUnread = 0L;
        for (PhoneStateData.MessageEntry message : tinderConversation(otherUuid)) {
            if (!isSelfMessageUuid(message.fromUuid) && !message.read) newestUnread = Math.max(newestUnread, message.timestamp);
        }
        String key = "T:" + otherUuid;
        if (newestUnread <= 0L || newestUnread <= LAST_READ_MARK_SENT.getOrDefault(key, 0L)) return;
        LAST_READ_MARK_SENT.put(key, newestUnread);
        sendAction("MARK_TINDER_READ", otherUuid, "");
    }

    public static void updateTyping(String otherUuid, boolean typing) {
        updateTyping(otherUuid, false, typing);
    }

    public static void updateTyping(String otherUuid, boolean gram, boolean typing) {
        if (otherUuid == null || otherUuid.isBlank() || (gram && typing && !PhoneSocialClientData.canMessageBlockGram(otherUuid))) return;
        String key = (gram ? "G:" : "M:") + otherUuid;
        long now = System.currentTimeMillis();
        boolean previous = TYPING_SENT_STATE.getOrDefault(key, false);
        long last = LAST_TYPING_SENT_AT.getOrDefault(key, 0L);
        if (typing && previous && now - last < 650L) return;
        if (!typing && !previous) return;
        TYPING_SENT_STATE.put(key, typing);
        LAST_TYPING_SENT_AT.put(key, now);
        sendAction("TYPING", otherUuid, (gram ? "GRAM" : "MESSAGE") + "|" + (typing ? "1" : "0"));
    }

    public static void updateTinderTyping(String otherUuid, boolean typing) {
        if (otherUuid == null || otherUuid.isBlank() || (typing && !PhoneSocialClientData.isTinderMatch(otherUuid))) return;
        String key = "T:" + otherUuid;
        long now = System.currentTimeMillis();
        boolean previous = TYPING_SENT_STATE.getOrDefault(key, false);
        long last = LAST_TYPING_SENT_AT.getOrDefault(key, 0L);
        if (typing && previous && now - last < 650L) return;
        if (!typing && !previous) return;
        TYPING_SENT_STATE.put(key, typing);
        LAST_TYPING_SENT_AT.put(key, now);
        sendAction("TYPING", otherUuid, "TINDER|" + (typing ? "1" : "0"));
    }

    public static boolean isTyping(String otherUuid) {
        return isTyping(otherUuid, false);
    }

    public static boolean isTyping(String otherUuid, boolean gram) {
        if (state.typing == null || otherUuid == null) return false;
        String channel = gram ? "GRAM" : "MESSAGE";
        for (PhoneStateData.TypingEntry entry : state.typing) {
            if (entry != null && otherUuid.equals(entry.playerUuid) && channel.equals(entry.channel)) return true;
        }
        return false;
    }

    public static boolean isTinderTyping(String otherUuid) {
        if (state.typing == null || otherUuid == null) return false;
        for (PhoneStateData.TypingEntry entry : state.typing) {
            if (entry != null && otherUuid.equals(entry.playerUuid) && "TINDER".equals(entry.channel)) return true;
        }
        return false;
    }

    public static void startCall(String uuid) { if (uuid != null && !uuid.isBlank()) sendAction("CALL_START", uuid, ""); }
    public static void startVideoCall(String uuid) { if (uuid != null && !uuid.isBlank()) sendAction("CALL_START_VIDEO", uuid, ""); }
    public static void updateVideoCameraState(boolean enabled, String mode) {
        if (!isVideoCall() || !callActive()) return;
        sendAction("CALL_VIDEO_CAMERA_STATE", callId(), (enabled ? "1" : "0") + "|" + ("FRONT".equalsIgnoreCase(mode) ? "FRONT" : "BACK"));
    }
    public static void acceptCall() { sendAction("CALL_ACCEPT", callId(), ""); }
    public static void declineCall() { sendAction("CALL_DECLINE", callId(), ""); }
    public static void endCall() { sendAction("CALL_END", callId(), ""); }
    public static String callStatus() { String value = safe(state.callStatus).toUpperCase(); return value.isBlank() ? "IDLE" : value; }
    public static boolean hasCall() { return !"IDLE".equals(callStatus()); }
    public static boolean callActive() { return "ACTIVE".equals(callStatus()); }
    public static String callId() { return safe(state.callId); }
    public static String callPeerUuid() { return safe(state.callPeerUuid); }
    public static String callPeerName() { return safe(state.callPeerName); }
    public static long callStartedAt() { return Math.max(0L, state.callStartedAt); }
    public static boolean isVideoCall() { return state.callVideo; }
    public static boolean peerVideoCameraEnabled() { return state.callPeerCameraEnabled; }
    public static String peerVideoCameraMode() { String mode = safe(state.callPeerCameraMode).toUpperCase(); return "FRONT".equals(mode) ? "FRONT" : "BACK"; }
    public static boolean canCall(String uuid) {
        return uuid != null && !uuid.isBlank() && !isBlocked(uuid)
                && (isContact(uuid) || PhoneSocialClientData.isTinderMatch(uuid));
    }

    public static String selfUuid() { return safe(state.selfUuid); }
    public static String selfName() { return safe(state.selfName); }
    public static String selfPhoneNumber() { return safe(state.selfPhoneNumber); }
    public static String formattedSelfPhoneNumber() { return formatPhoneNumber(selfPhoneNumber()); }
    public static String notice() { return PhoneSettingsStore.localizeNetworkText(safe(state.notice)); }
    public static boolean voiceCallingAvailable() { return state.voiceCallingAvailable; }
    public static String voiceProvider() { return safe(state.voiceProvider); }
    public static String formatPhoneNumber(String raw) { return PhoneNumberUtil.format(raw); }
    public static String normalizePhoneNumber(String raw) { return PhoneNumberUtil.normalize(raw); }

    public static List<PhoneStateData.PlayerEntry> contacts() { return state.contacts == null ? List.of() : List.copyOf(state.contacts); }
    public static List<PhoneStateData.PlayerEntry> incomingRequests() { return state.incomingRequests == null ? List.of() : List.copyOf(state.incomingRequests); }
    public static List<PhoneStateData.PlayerEntry> outgoingRequests() { return state.outgoingRequests == null ? List.of() : List.copyOf(state.outgoingRequests); }
    public static List<PhoneStateData.PlayerEntry> blockedPlayers() { return state.blockedPlayers == null ? List.of() : List.copyOf(state.blockedPlayers); }
    public static List<PhoneStateData.MessageEntry> messagesSnapshot() { return state.messages == null ? List.of() : new ArrayList<>(state.messages); }

    public static boolean isContact(String uuid) { return contacts().stream().anyMatch(entry -> Objects.equals(entry.uuid, uuid)); }
    public static boolean isBlocked(String uuid) { return blockedPlayers().stream().anyMatch(entry -> Objects.equals(entry.uuid, uuid)); }
    public static boolean isBot(String uuid) { return false; }
    public static boolean isSelfMessageUuid(String uuid) { return Objects.equals(uuid, selfUuid()); }

    public static List<PhoneStateData.PlayerEntry> sortedGramContacts() {
        List<PhoneStateData.PlayerEntry> result = new ArrayList<>();
        for (var profile : PhoneSocialClientData.mutualBlockGramProfiles()) {
            String uuid = profile.playerUuid == null ? "" : profile.playerUuid;
            if (uuid.isBlank()) continue;
            String name = profile.playerName == null || profile.playerName.isBlank() ? "Player" : profile.playerName;
            PhoneStateData.PlayerEntry known = contactByUuid(uuid);
            result.add(new PhoneStateData.PlayerEntry(uuid, name, known == null ? "" : known.phoneNumber, known != null && known.online));
        }
        result.sort((a, b) -> {
            long ta = lastGramMessageTimestamp(a.uuid), tb = lastGramMessageTimestamp(b.uuid);
            if (ta != tb) return Long.compare(tb, ta);
            int unread = Integer.compare(unreadGramCount(b.uuid), unreadGramCount(a.uuid));
            return unread != 0 ? unread : safe(a.name).compareToIgnoreCase(safe(b.name));
        });
        return List.copyOf(result);
    }

    public static List<PhoneStateData.PlayerEntry> sortedMessagePeers() {
        List<PhoneStateData.PlayerEntry> result = new ArrayList<>(contacts());
        java.util.Set<String> peers = new java.util.HashSet<>();
        result.forEach(p -> peers.add(p.uuid));
        for (var message : state.messages) {
            if (isGramMessageType(message.type) || isTinderMessageType(message.type) || message.type.startsWith("GROUP_")) continue;
            boolean outgoing = Objects.equals(message.fromUuid, selfUuid());
            if (!outgoing && !Objects.equals(message.toUuid, selfUuid())) continue;
            String id = outgoing ? message.toUuid : message.fromUuid;
            String name = outgoing ? message.toName : message.fromName;
            if (!id.equals(selfUuid()) && peers.add(id)) result.add(new PhoneStateData.PlayerEntry(id,name,"",false));
        }
        for(var g:state.groups)result.add(new PhoneStateData.PlayerEntry(g.id,g.name,"",false));
        result.sort((a, b) -> {
            long ta = lastMessageTimestamp(a.uuid), tb = lastMessageTimestamp(b.uuid);
            if (ta != tb) return Long.compare(tb, ta);
            int unread = Integer.compare(unreadCount(b.uuid), unreadCount(a.uuid));
            return unread != 0 ? unread : safe(a.name).compareToIgnoreCase(safe(b.name));
        });
        return List.copyOf(result);
    }

    public static List<PhoneStateData.PlayerEntry> sortedContacts() {
        List<PhoneStateData.PlayerEntry> result = new ArrayList<>(contacts());
        result.sort(Comparator.comparing(entry -> safe(entry.name).toLowerCase()));
        return List.copyOf(result);
    }

    public static PhoneStateData.PlayerEntry contactByUuid(String uuid) {
        for (PhoneStateData.PlayerEntry entry : contacts()) if (Objects.equals(entry.uuid, uuid)) return entry;
        return null;
    }

    public static PhoneStateData.PlayerEntry contactByNumber(String rawNumber) {
        String normalized = normalizePhoneNumber(rawNumber);
        if (normalized.isEmpty()) return null;
        for (PhoneStateData.PlayerEntry entry : contacts()) {
            if (normalized.equals(normalizePhoneNumber(entry.phoneNumber))) return entry;
        }
        return null;
    }

    public static List<PhoneStateData.MessageEntry> conversation(String otherUuid) {
        List<PhoneStateData.MessageEntry> result = new ArrayList<>();
        String self = selfUuid();
        if (state.messages != null) for (PhoneStateData.MessageEntry message : state.messages) {
            if (isGramMessageType(message.type) || isTinderMessageType(message.type) || message.type.startsWith("GROUP_")) continue;
            boolean forward = Objects.equals(message.fromUuid, self) && Objects.equals(message.toUuid, otherUuid);
            boolean backward = Objects.equals(message.fromUuid, otherUuid) && Objects.equals(message.toUuid, self);
            if (forward || backward) result.add(message);
        }
        result.sort(Comparator.comparingLong(entry -> entry.timestamp));
        return result;
    }

    public static List<PhoneStateData.MessageEntry> gramConversation(String otherUuid) {
        List<PhoneStateData.MessageEntry> result = new ArrayList<>();
        if (state.messages == null || otherUuid == null || otherUuid.isBlank()) return result;
        String self = selfUuid();
        for (PhoneStateData.MessageEntry message : state.messages) {
            if (!isGramMessageType(message.type)) continue;
            boolean forward = Objects.equals(message.fromUuid, self) && Objects.equals(message.toUuid, otherUuid);
            boolean backward = Objects.equals(message.fromUuid, otherUuid) && Objects.equals(message.toUuid, self);
            if (forward || backward) result.add(message);
        }
        result.sort(Comparator.comparingLong(entry -> entry.timestamp));
        return result;
    }

    public static List<PhoneStateData.MessageEntry> tinderConversation(String otherUuid) {
        List<PhoneStateData.MessageEntry> result = new ArrayList<>();
        if (state.messages == null || otherUuid == null || otherUuid.isBlank()) return result;
        String self = selfUuid();
        for (PhoneStateData.MessageEntry message : state.messages) {
            if (!isTinderMessageType(message.type)) continue;
            boolean forward = Objects.equals(message.fromUuid, self) && Objects.equals(message.toUuid, otherUuid);
            boolean backward = Objects.equals(message.fromUuid, otherUuid) && Objects.equals(message.toUuid, self);
            if (forward || backward) result.add(message);
        }
        result.sort(Comparator.comparingLong(entry -> entry.timestamp));
        return result;
    }

    public static PhoneStateData.MessageEntry lastTinderMessage(String otherUuid) {
        List<PhoneStateData.MessageEntry> messages = tinderConversation(otherUuid);
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public static long lastTinderMessageTimestamp(String otherUuid) {
        PhoneStateData.MessageEntry message = lastTinderMessage(otherUuid);
        return message == null ? 0L : message.timestamp;
    }

    public static int unreadTinderCount(String otherUuid) { return state.unreadCounts.getOrDefault("TINDER|"+otherUuid,0); }

    public static int totalUnreadTinderCount() { return state.unreadCounts.entrySet().stream().filter(e->e.getKey().startsWith("TINDER|")).mapToInt(java.util.Map.Entry::getValue).sum(); }

    public static PhoneStateData.MessageEntry lastGramMessage(String otherUuid) {
        List<PhoneStateData.MessageEntry> messages = gramConversation(otherUuid);
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public static long lastGramMessageTimestamp(String otherUuid) {
        PhoneStateData.MessageEntry message = lastGramMessage(otherUuid);
        return message == null ? 0L : message.timestamp;
    }

    public static int unreadGramCount(String otherUuid) { return state.unreadCounts.getOrDefault("GRAM|"+otherUuid,0); }

    public static int totalUnreadGramCount() { return state.unreadCounts.entrySet().stream().filter(e->e.getKey().startsWith("GRAM|")).mapToInt(java.util.Map.Entry::getValue).sum(); }

    public static void markGramConversationOpened(String otherUuid) {
        List<PhoneStateData.MessageEntry> messages = gramConversation(otherUuid);
        long seenAt = System.currentTimeMillis();
        if (!messages.isEmpty()) seenAt = Math.max(seenAt, messages.get(messages.size() - 1).timestamp);
        LAST_OPENED_GRAM_TIMESTAMPS.put(otherUuid, seenAt);
    }

    public static boolean isGramMessageType(String type) { return type != null && type.startsWith("GRAM_"); }
    public static boolean isTinderMessageType(String type) { return type != null && type.startsWith("TINDER_"); }
    public static String baseMessageType(String type) {
        if(type!=null&&type.startsWith("GROUP_"))return type.substring(6);
        if (type == null) return "TEXT";
        if (type.startsWith("GRAM_")) return type.substring("GRAM_".length());
        if (type.startsWith("TINDER_")) return type.substring("TINDER_".length());
        return type;
    }

    public static PhoneStateData.MessageEntry lastMessage(String otherUuid) {
        if(PhoneGroupClient.group(otherUuid)!=null)return PhoneGroupClient.last(otherUuid);
        List<PhoneStateData.MessageEntry> messages = conversation(otherUuid);
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public static long lastMessageTimestamp(String otherUuid) {
        PhoneStateData.MessageEntry last = lastMessage(otherUuid);
        return last == null ? 0L : last.timestamp;
    }

    public static int unreadCount(String otherUuid) { if(PhoneGroupClient.group(otherUuid)!=null)return PhoneGroupClient.unread(otherUuid);return state.unreadCounts.getOrDefault("MESSAGE|"+otherUuid,0); }

    public static void markConversationOpened(String otherUuid) {
        if(PhoneGroupClient.group(otherUuid)!=null){PhoneNetwork.sendAction("GROUP_READ",otherUuid,"");return;}
        if (otherUuid == null || otherUuid.isBlank()) return;
        List<PhoneStateData.MessageEntry> conversation = conversation(otherUuid);
        long seen = System.currentTimeMillis();
        if (!conversation.isEmpty()) seen = Math.max(seen, conversation.get(conversation.size() - 1).timestamp);
        LAST_OPENED_TIMESTAMPS.put(otherUuid, seen);
        markConversationRead(otherUuid);
    }

    public static String messagePreview(PhoneStateData.MessageEntry last) {
        if (last == null) return PhoneSettingsStore.text("Bir konuşma başlat", "Start a conversation");
        String type = baseMessageType(last.type);
        if ("VOICE".equals(type)) return PhoneSettingsStore.text("Sesli mesaj", "Voice message");
        if ("PHOTO".equals(type)) return last.caption!=null&&!last.caption.isBlank()?last.caption:PhoneSettingsStore.text("Fotoğraf", "Photo");
        if ("LOCATION".equals(type)) return PhoneSettingsStore.text("Konum", "Location");
        if ("POST".equals(type)) return PhoneSettingsStore.text("Lumi gönderisi", "Lumi post");
        return safe(last.body);
    }

    public static List<PhoneStateData.CallEntry> callHistory() {
        if (state.callHistory == null || state.callHistory.isEmpty()) return List.of();
        List<PhoneStateData.CallEntry> result = new ArrayList<>(state.callHistory);
        result.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        return List.copyOf(result);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}

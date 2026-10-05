package com.mikasrevs.newphone.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-visible communication state. Kept deliberately close to the modern MPhone
 * data shape so later app ports do not need another protocol migration.
 */
public final class PhoneStateData {
    public String selfUuid = "";
    public String selfName = "";
    public String selfPhoneNumber = "";
    public String notice = "";
    public boolean voiceCallingAvailable;
    public String voiceProvider = "";
    public String callStatus = "IDLE";
    public String callId = "";
    public String callPeerUuid = "";
    public String callPeerName = "";
    public boolean callVideo;
    public boolean callPeerCameraEnabled = true;
    public String callPeerCameraMode = "BACK";
    public long callStartedAt;
    public List<PlayerEntry> contacts = new ArrayList<>();
    public List<PlayerEntry> incomingRequests = new ArrayList<>();
    public List<PlayerEntry> outgoingRequests = new ArrayList<>();
    public List<PlayerEntry> blockedPlayers = new ArrayList<>();
    public List<MessageEntry> messages = new ArrayList<>();
    public List<GroupEntry> groups = new ArrayList<>();
    public boolean messagesUnchanged;
    public java.util.Map<String, Integer> unreadCounts = new java.util.HashMap<>();
    public java.util.Map<String, Boolean> historyMore = new java.util.HashMap<>();
    public boolean historyOnly;
    public String historyKey = "";
    public long historyJoinedSequence;
    public String requestId = "";
    public String requestResult = "";
    public String readBy = "", readPeer = "", readChannel = "";
    public List<String> deletedMessageIds = new ArrayList<>();
    public List<MessageReceipt> messageReceipts = new ArrayList<>();
    public List<TypingEntry> typing = new ArrayList<>();
    public List<CallEntry> callHistory = new ArrayList<>();

    /** Receipt updates never pull unloaded message bodies into the history cache. */
    public static final class MessageReceipt {
        public String id = "";
        public boolean read, delivered;
    }

    public static final class GroupEntry {
        public String id="", name="", owner="", photoId="";
        public long joinedSequence;
        public List<PlayerEntry> members=new ArrayList<>();
    }
    public static final class PlayerEntry {
        public String uuid = "";
        public String name = "";
        public String phoneNumber = "";
        public boolean online;

        public PlayerEntry() {
        }

        public PlayerEntry(String uuid, String name, String phoneNumber, boolean online) {
            this.uuid = uuid;
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.online = online;
        }
    }

    public static final class MessageEntry {
        public String id = "";
        public String fromUuid = "";
        public String fromName = "";
        public String toUuid = "";
        public String toName = "";
        public String type = "TEXT";
        public String body = "";
        public long timestamp;
        public boolean read;
        public boolean delivered;
        public String caption = "";
        public long groupSequence;
        public String localStatus = "";
        public String localError = "";
        public String replyToId = "";
        public String replyPreview = "";

        public MessageEntry() {
        }

        public MessageEntry(String id, String fromUuid, String fromName, String toUuid, String toName,
                            String type, String body, long timestamp, boolean read,
                            String replyToId, String replyPreview) {
            this.id = id;
            this.fromUuid = fromUuid;
            this.fromName = fromName;
            this.toUuid = toUuid;
            this.toName = toName;
            this.type = type;
            this.body = body;
            this.timestamp = timestamp;
            this.read = read;
            this.replyToId = replyToId;
            this.replyPreview = replyPreview;
        }
    }

    public static final class TypingEntry {
        public String playerUuid = "";
        public String playerName = "";
        public String channel = "MESSAGE";

        public TypingEntry() {
        }

        public TypingEntry(String playerUuid, String playerName, String channel) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.channel = channel;
        }
    }

    public static final class CallEntry {
        public String id = "";
        public String peerUuid = "";
        public String peerName = "";
        public String peerPhoneNumber = "";
        public String direction = "INCOMING";
        public String result = "COMPLETED";
        public long timestamp;
        public long durationMs;

        public CallEntry() {
        }
    }
}

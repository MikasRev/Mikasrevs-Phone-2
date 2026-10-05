package com.mikasrevs.newphone.network;

import java.util.ArrayList;
import java.util.List;

/** Minimal server-filtered dispatch snapshot for one client. */
public final class EmergencyStateData {
    public List<String> authorizedServices = new ArrayList<>();
    public List<DispatchEntry> dispatches = new ArrayList<>();
    public List<ChatEntry> chatMessages = new ArrayList<>();
    public String noticeKey = "";
    public String noticeArgument = "";
    public long serverTime;

    public static final class ChatEntry {
        public String service = "";
        public String senderUuid = "";
        public String senderName = "";
        public String message = "";
        public long timestamp;
    }

    public static final class DispatchEntry {
        public long id;
        public String service = "";
        public String categoryId = "";
        public String risk = "LOW";
        public int priority;
        public String callerName = "";
        public String description = "";
        public String dimension = "minecraft:overworld";
        public int x;
        public int y;
        public int z;
        public long createdAt;
        public int maximumResponders;
        public int responderCount;
        /** Number of accepted responders that have physically reached the incident area. */
        public int onSceneResponderCount;
        public String status = "WAITING";
        public boolean callerOwned;
        public boolean acceptedBySelf;
        public boolean declinedBySelf;
        public boolean onSceneBySelf;
        public int nearestResponderDistance = -1;
        public boolean nearestResponderDifferentDimension;
    }
}

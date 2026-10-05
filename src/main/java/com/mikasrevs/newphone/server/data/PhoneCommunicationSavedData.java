package com.mikasrevs.newphone.server.data;

import com.google.gson.Gson;
import com.mikasrevs.newphone.NewPhone;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent contacts/messages base used by the 1.20.1 port. */
public final class PhoneCommunicationSavedData extends SavedData {
    private static final Gson GSON = new Gson();
    private static final String DATA_NAME = NewPhone.MOD_ID + "_communication_data";
    private static final String JSON_UTF8_KEY = "JsonUtf8";
    private static final String LEGACY_JSON_KEY = "Json";

    private Snapshot snapshot = new Snapshot();
    private boolean voiceCleanupSafe=true;
    private boolean photoCleanupSafe=true;
    public boolean canCleanVoiceArchive(){return voiceCleanupSafe;}
    public boolean canCleanPhotoArchive(){return photoCleanupSafe;}

    public static PhoneCommunicationSavedData get(MinecraftServer server) {
        return PhoneSavedDataRecovery.get(server,DATA_NAME,PhoneCommunicationSavedData::load,PhoneCommunicationSavedData::new);
    }

    public static PhoneCommunicationSavedData load(CompoundTag tag) {
        PhoneCommunicationSavedData data = new PhoneCommunicationSavedData();
        // An existing but empty/unreadable archive is not proof that voice files are orphaned.
        data.voiceCleanupSafe = false;
        data.photoCleanupSafe = false;
        byte[] jsonUtf8 = tag.getByteArray(JSON_UTF8_KEY);
        if (jsonUtf8.length > 0
                && data.decodeJson(new String(jsonUtf8, StandardCharsets.UTF_8))) {
            data.voiceCleanupSafe &= !tag.getBoolean("VoiceCleanupBlocked");
            data.photoCleanupSafe &= !tag.getBoolean("PhotoCleanupBlocked");
            return data;
        }

        String legacyJson = tag.getString(LEGACY_JSON_KEY);
        if (!legacyJson.isBlank() && data.decodeJson(legacyJson)) {
            data.setDirty();
        }
        data.voiceCleanupSafe &= !tag.getBoolean("VoiceCleanupBlocked");
        data.photoCleanupSafe &= !tag.getBoolean("PhotoCleanupBlocked");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        byte[] jsonUtf8 = GSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
        tag.putByteArray(JSON_UTF8_KEY, jsonUtf8);
        tag.remove(LEGACY_JSON_KEY);
        tag.putBoolean("VoiceCleanupBlocked", !voiceCleanupSafe);
        tag.putBoolean("PhotoCleanupBlocked", !photoCleanupSafe);
        return tag;
    }

    private boolean decodeJson(String json) {
        try {
            Snapshot decoded = GSON.fromJson(json, Snapshot.class);
            if (decoded == null) {voiceCleanupSafe=false;return false;}
            // If a broken record could have owned media, absence is not evidence of an orphan.
            photoCleanupSafe = decoded.messages != null && decoded.groups != null && decoded.photosBase64 != null
                    && decoded.messages.stream().allMatch(m -> m != null && m.type != null && m.body != null)
                    && decoded.groups.values().stream().allMatch(g -> g != null && g.photoId != null && g.members != null
                    && g.members.values().stream().allMatch(java.util.Objects::nonNull));
            snapshot = normalize(decoded);
            voiceCleanupSafe=true;
            return true;
        } catch (RuntimeException exception) {
            voiceCleanupSafe=false;
            photoCleanupSafe=false;
            System.err.println("[MPhone] Could not decode persisted communication data: " + exception.getMessage());
            return false;
        }
    }

    public synchronized Snapshot copySnapshot() {
        return copySnapshot(true);
    }
    public synchronized Snapshot copyMetadata() { return copySnapshot(false); }
    private Snapshot copySnapshot(boolean includeMessages) {
        Snapshot copy = new Snapshot();
        snapshot.contacts.forEach((key, value) -> copy.contacts.put(key, new ArrayList<>(value)));
        snapshot.incomingRequests.forEach((key, value) -> copy.incomingRequests.put(key, new ArrayList<>(value)));
        snapshot.blocked.forEach((key, value) -> copy.blocked.put(key, new ArrayList<>(value)));
        copy.knownNames.putAll(snapshot.knownNames);
        copy.photosBase64.putAll(snapshot.photosBase64);
        copy.messageRevision = snapshot.messageRevision;
        copy.retiredMessages.putAll(snapshot.retiredMessages);
        snapshot.groups.forEach((id,g)->copy.groups.put(id,g.copy()));
        if(includeMessages) for (StoredMessage original : snapshot.messages) {
            if (original == null) continue;
            StoredMessage message = new StoredMessage();
            message.id = original.id; message.fromUuid = original.fromUuid; message.fromName = original.fromName;
            message.toUuid = original.toUuid; message.toName = original.toName; message.type = original.type;
            message.body = original.body; message.timestamp = original.timestamp; message.read = original.read;
            message.delivered = original.delivered; message.caption = original.caption; message.requestHash = original.requestHash;
            message.groupSequence=original.groupSequence;
            message.recipients=new ArrayList<>(original.recipients);message.readBy=new ArrayList<>(original.readBy);message.deliveredTo=new ArrayList<>(original.deliveredTo);
            message.replyToId = original.replyToId; message.replyPreview = original.replyPreview;
            copy.messages.add(message);
        }
        snapshot.callHistory.forEach((key, value) -> {
            List<StoredCallHistory> calls = new ArrayList<>();
            for (StoredCallHistory original : value) {
                if (original == null) continue;
                StoredCallHistory call = new StoredCallHistory();
                call.id = original.id; call.peerUuid = original.peerUuid; call.peerName = original.peerName;
                call.peerPhoneNumber = original.peerPhoneNumber; call.direction = original.direction;
                call.result = original.result; call.timestamp = original.timestamp; call.durationMs = original.durationMs;
                calls.add(call);
            }
            copy.callHistory.put(key, calls);
        });
        return copy;
    }

    public synchronized void rememberName(String id,String name) {
        if(!java.util.Objects.equals(snapshot.knownNames.put(id,name),name))setDirty();
    }
    public synchronized void recentFor(String self,com.mikasrevs.newphone.network.PhoneStateData state) {
        PhoneHistory.recent(snapshot.messages,self,state);PhoneGroups.recent(snapshot,self,state);
    }
    public synchronized com.mikasrevs.newphone.network.PhoneStateData historyPage(String self,String peer,String channel,String before) {
        return channel.equals("GROUP")?PhoneGroups.page(snapshot,self,peer,before):PhoneHistory.page(snapshot.messages,self,peer,channel,before);
    }
    public synchronized java.util.Set<String> photoReferences() {
        var refs=new java.util.HashSet<String>(snapshot.photosBase64.keySet());
        for(var m:snapshot.messages)if(m!=null&&m.type!=null&&m.type.endsWith("PHOTO")&&m.body!=null)refs.add(m.body);
        for(var g:snapshot.groups.values())if(!g.members.isEmpty()&&g.photoId!=null&&!g.photoId.isBlank())refs.add(g.photoId);
        return refs;
    }

    public synchronized boolean canReadVoice(String self,String id) {
        return canReadVoice(self,id,type->true);
    }
    public synchronized boolean canReadVoice(String self,String id,java.util.function.Predicate<String> allowedType) {
        return snapshot.messages.stream().anyMatch(m->m!=null&&m.type!=null&&m.type.endsWith("VOICE")&&id.equals(m.body)
                &&allowedType.test(m.type)
                &&(PhoneGroups.isGroup(m)?PhoneGroups.visible(snapshot,m,self):(self.equals(m.fromUuid)||self.equals(m.toUuid))));
    }
    public synchronized boolean referencesVoice(String id) {
        return snapshot.messages.stream().anyMatch(m->m!=null&&m.type!=null&&m.type.endsWith("VOICE")&&id.equals(m.body));
    }
    public synchronized java.util.Set<String> voiceReferences() {
        var refs=new java.util.HashSet<String>();
        for(var m:snapshot.messages)if(m!=null&&m.type!=null&&m.type.endsWith("VOICE"))refs.add(m.body);
        return refs;
    }
    public synchronized boolean eitherBlocks(String self,String peer){return snapshot.blocked.getOrDefault(self,List.of()).contains(peer)||snapshot.blocked.getOrDefault(peer,List.of()).contains(self);}
    public synchronized boolean hasContact(String self,String peer){return snapshot.contacts.getOrDefault(self,List.of()).contains(peer);}

    public synchronized void replace(Snapshot value) {
        snapshot = normalize(value == null ? new Snapshot() : value);
        setDirty();
    }

    private static Snapshot normalize(Snapshot value) {
        if(value.groups==null)value.groups=new LinkedHashMap<>();
        value.groups.values().removeIf(java.util.Objects::isNull);
        for (var group : value.groups.values()) {
            if (group.members == null) group.members = new LinkedHashMap<>();
            group.members.values().removeIf(java.util.Objects::isNull);
        }
        if (value.contacts == null) value.contacts = new HashMap<>();
        if (value.incomingRequests == null) value.incomingRequests = new HashMap<>();
        if (value.blocked == null) value.blocked = new HashMap<>();
        if (value.callHistory == null) value.callHistory = new HashMap<>();
        if (value.knownNames == null) value.knownNames = new HashMap<>();
        if (value.messages == null) value.messages = new ArrayList<>();
        // Recover healthy records independently instead of rejecting the whole archive.
        value.messages.removeIf(java.util.Objects::isNull);
        for (var message : value.messages) {
            if (message.recipients == null) message.recipients = new ArrayList<>();
            if (message.readBy == null) message.readBy = new ArrayList<>();
            if (message.deliveredTo == null) message.deliveredTo = new ArrayList<>();
        }
        if (value.retiredMessages == null) value.retiredMessages = new HashMap<>();
        if (value.photosBase64 == null) value.photosBase64 = new LinkedHashMap<>();
        value.contacts.replaceAll((key, list) -> list == null ? new ArrayList<>() : new ArrayList<>(list));
        value.incomingRequests.replaceAll((key, list) -> list == null ? new ArrayList<>() : new ArrayList<>(list));
        value.blocked.replaceAll((key, list) -> list == null ? new ArrayList<>() : new ArrayList<>(list));
        value.callHistory.replaceAll((key, list) -> list == null ? new ArrayList<>() : new ArrayList<>(list));
        return value;
    }

    public static final class Snapshot {
        public Map<String,PhoneGroups.Group> groups=new LinkedHashMap<>();
        public Map<String,String> retiredMessages = new HashMap<>();
        public long messageRevision;
        public Map<String, List<String>> contacts = new HashMap<>();
        public Map<String, List<String>> incomingRequests = new HashMap<>();
        public Map<String, List<String>> blocked = new HashMap<>();
        public Map<String, List<StoredCallHistory>> callHistory = new HashMap<>();
        public Map<String, String> knownNames = new HashMap<>();
        public List<StoredMessage> messages = new ArrayList<>();
        public LinkedHashMap<String, String> photosBase64 = new LinkedHashMap<>();
    }

    public static final class StoredMessage {
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
        public String requestHash = "";
        public long groupSequence;
        public List<String> recipients=new ArrayList<>(), readBy=new ArrayList<>(), deliveredTo=new ArrayList<>();
        public String replyToId = "";
        public String replyPreview = "";
    }

    public static final class StoredCallHistory {
        public String id = "";
        public String peerUuid = "";
        public String peerName = "";
        public String peerPhoneNumber = "";
        public String direction = "INCOMING";
        public String result = "COMPLETED";
        public long timestamp;
        public long durationMs;
    }
}

package com.mikasrevs.newphone.client.network;

import com.google.gson.Gson;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneBankClientData;
import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.network.PhoneStateData;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneCallScreen;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.video.PhoneVideoStreamClient;
import com.mikasrevs.newphone.client.sound.PhoneSoundManager;
import com.mikasrevs.newphone.client.emergency.EmergencyClientData;
import com.mikasrevs.newphone.network.message.PhoneStateMessage;
import com.mikasrevs.newphone.network.message.PhoneVideoFrameDataMessage;
import net.minecraft.client.Minecraft;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client packet sink kept separate from the common SimpleChannel registration. */
public final class PhoneClientNetwork {
    private static final Gson GSON = new Gson();
    private static final int MAX_PENDING_PHONE_STATES = 8;
    private static final long PHONE_STATE_TIMEOUT_MS = 10_000L;
    private static final Map<String, PendingPhoneState> PENDING_PHONE_STATES = new LinkedHashMap<>();

    private PhoneClientNetwork() {
    }

    public static void receivePhoto(String photoId, byte[] imageData) {
        PhonePhotoStore.receive(photoId, imageData);
    }

    public static void receiveBankState(String json) {
        PhoneBankClientData.applyStateJson(json);
    }

    public static void receiveEmergencyState(String json) {
        EmergencyClientData.applyStateJson(json);
    }

    public static void receiveStateChunk(String transferId, int chunkIndex, int chunkCount, String jsonChunk) {
        receiveChunk(false, transferId, chunkIndex, chunkCount, jsonChunk);
    }

    public static void receiveSocialStateChunk(String transferId, int chunkIndex, int chunkCount, String jsonChunk) {
        receiveChunk(true, transferId, chunkIndex, chunkCount, jsonChunk);
    }

    private static void receiveChunk(boolean social, String transferId, int chunkIndex, int chunkCount, String jsonChunk) {
        if (jsonChunk == null || jsonChunk.length() > PhoneStateMessage.MAX_CHUNK_LENGTH) {
            System.err.println("[MPhone] Rejected invalid phone state chunk.");
            return;
        }
        if (chunkCount == 1 && chunkIndex == 0) {
            if (social) receiveSocialState(jsonChunk); else receiveState(jsonChunk);
            return;
        }
        if (transferId == null || transferId.isBlank()
                || chunkCount < 2 || chunkCount > PhoneStateMessage.MAX_CHUNK_COUNT
                || chunkIndex < 0 || chunkIndex >= chunkCount) {
            System.err.println("[MPhone] Rejected invalid phone state chunk.");
            return;
        }

        String completeJson = null;
        synchronized (PENDING_PHONE_STATES) {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, PendingPhoneState>> iterator = PENDING_PHONE_STATES.entrySet().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().getValue().createdAt > PHONE_STATE_TIMEOUT_MS) iterator.remove();
            }

            String key = (social ? "social:" : "phone:") + transferId;
            PendingPhoneState pending = PENDING_PHONE_STATES.get(key);
            if (chunkIndex == 0) {
                pending = new PendingPhoneState(chunkCount, now);
                PENDING_PHONE_STATES.put(key, pending);
            }
            if (pending == null || pending.chunkCount != chunkCount || pending.received != chunkIndex) {
                PENDING_PHONE_STATES.remove(key);
                return;
            }
            pending.add(chunkIndex, jsonChunk);
            pending.createdAt = now;
            if (pending.complete()) {
                completeJson = pending.join();
                PENDING_PHONE_STATES.remove(key);
            }
            while (PENDING_PHONE_STATES.size() > MAX_PENDING_PHONE_STATES) {
                Iterator<String> ids = PENDING_PHONE_STATES.keySet().iterator();
                if (!ids.hasNext()) break;
                ids.next();
                ids.remove();
            }
        }
        if (completeJson != null) {
            if (social) receiveSocialState(completeJson); else receiveState(completeJson);
        }
    }

    public static void clearPendingStateTransfers() {
        synchronized (PENDING_PHONE_STATES) {
            PENDING_PHONE_STATES.clear();
        }
    }

    public static void receiveState(String json) {
        if (json == null || json.isBlank()) return;
        try {
            String previousCallStatus = PhoneClientData.callStatus();
            List<PhoneStateData.MessageEntry> previousMessages = PhoneClientData.messagesSnapshot();
            List<PhoneStateData.PlayerEntry> previousRequests = PhoneClientData.incomingRequests();
            PhoneStateData state = GSON.fromJson(json, PhoneStateData.class);
            if (state != null && state.historyOnly) {
                com.mikasrevs.newphone.client.data.PhoneMessageClient.update(state);
                return;
            }
            if (state != null) {
                com.mikasrevs.newphone.client.data.PhoneMessageClient.prepareConnection(state.selfUuid);
                PhoneClientData.setState(state);
                com.mikasrevs.newphone.client.data.PhoneMessageClient.restoreForConnection();
                String currentCallStatus = PhoneClientData.callStatus();
                PhoneSoundManager.onPhoneStateUpdated(
                        previousCallStatus, currentCallStatus,
                        previousMessages, PhoneClientData.messagesSnapshot(),
                        previousRequests, PhoneClientData.incomingRequests()
                );
                if ("IDLE".equals(previousCallStatus) && !"IDLE".equals(currentCallStatus)) {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft != null && !(minecraft.screen instanceof ZPhoneCallScreen) && !(minecraft.screen instanceof com.mikasrevs.newphone.client.duo.DuoTransitionScreen)) {
                        // Incoming calls stay as a HUD banner while the player is in-world. If MPhone
                        // is already open, push the call screen so ending it returns to that exact page.
                        if ("INCOMING".equals(currentCallStatus)) {
                            if (minecraft.screen instanceof AbstractZPhoneScreen) {
                                PhoneNavigationState.open(minecraft, minecraft.screen, new ZPhoneCallScreen());
                            }
                        } else if (minecraft.screen instanceof AbstractZPhoneScreen) {
                            PhoneNavigationState.open(minecraft, minecraft.screen, new ZPhoneCallScreen());
                        } else {
                            com.mikasrevs.newphone.client.device.PhoneDeviceSession.select(com.mikasrevs.newphone.PhoneInventoryUtil.findPhone(minecraft.player));
                            minecraft.setScreen(new ZPhoneCallScreen());
                        }
                    }
                }
                if (!"IDLE".equals(previousCallStatus) && "IDLE".equals(currentCallStatus)) {
                    PhoneVideoStreamClient.clearCallTextures();
                } else if (PhoneClientData.isVideoCall() && "ACTIVE".equals(currentCallStatus)) {
                    PhoneVideoStreamClient.prepareForVideoCall();
                }
            }
        } catch (RuntimeException exception) {
            System.err.println("[MPhone] Could not decode phone state: " + exception.getMessage());
        }
    }
    public static void receiveSocialState(String json) {
        var previousNotifications = PhoneSocialClientData.notificationSnapshot();
        var previousFollows = PhoneSocialClientData.followSnapshot();
        PhoneSocialClientData.applyStateJson(json == null ? "" : json);
        PhoneSoundManager.onSocialStateUpdated(
                previousNotifications, PhoneSocialClientData.notificationSnapshot(),
                previousFollows, PhoneSocialClientData.followSnapshot()
        );
    }

    public static void receiveVideoFrame(PhoneVideoFrameDataMessage message) {
        PhoneVideoStreamClient.receiveFrame(message);
    }

    private static final class PendingPhoneState {
        private final StringBuilder json = new StringBuilder();
        private final int chunkCount;
        private long createdAt;
        private int received;

        private PendingPhoneState(int chunkCount, long createdAt) {
            this.chunkCount = chunkCount;
            this.createdAt = createdAt;
        }

        private void add(int index, String chunk) {
            json.append(chunk);
            received++;
        }

        private boolean complete() {
            return received == chunkCount;
        }

        private String join() {
            return json.toString();
        }
    }

}

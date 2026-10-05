package com.mikasrevs.newphone.client.sound;

import com.mikasrevs.newphone.PhoneSounds;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.settings.PhonePrivacyStore;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneMessageThreadScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneTinderChatScreen;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import com.mikasrevs.newphone.network.PhoneStateData;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Central client-side MPhone sound controller for the Forge 1.20.1 port. */
public final class PhoneSoundManager {
    private static final long INCOMING_REPEAT_DELAY_MS = 760L;
    private static final float OUTGOING_CALL_VOLUME_MULTIPLIER = 0.16F;

    private static boolean phoneNotificationsInitialized;
    private static boolean socialNotificationsInitialized;
    private static long nextIncomingRepeatAt;

    private PhoneSoundManager() {}

    public static void playCamera() {
        if (PhoneSettingsStore.cameraSound()) playUi(PhoneSounds.CAMERA);
    }

    public static void playMessageSend() {
        playUi(PhoneSounds.MESSAGE_SEND);
    }

    public static void playPhoneOpen() {
        playUi(PhoneSounds.PHONE_OPEN);
    }

    public static void playEmergencyNotification() {
        if (notificationsAllowed()) playUi(PhoneSounds.NOTIFICATION);
    }

    public static void onPhoneStateUpdated(
            String previousStatus,
            String currentStatus,
            List<PhoneStateData.MessageEntry> previousMessages,
            List<PhoneStateData.MessageEntry> currentMessages,
            List<PhoneStateData.PlayerEntry> previousRequests,
            List<PhoneStateData.PlayerEntry> currentRequests
    ) {
        handleCallTransition(previousStatus, currentStatus);

        if (phoneNotificationsInitialized) {
            PhoneStateData.MessageEntry incomingMessage = newIncomingMessage(previousMessages, currentMessages);
            if (incomingMessage != null) {
                if (isOpenMessageThreadFor(incomingMessage)) playMessageReceive();
                else playNotification();
            }
            if (hasNewIncomingRequest(previousRequests, currentRequests)) playNotification();
        }
        phoneNotificationsInitialized = true;
    }

    public static void onSocialStateUpdated(
            List<PhoneSocialStateData.NotificationEntry> previousNotifications,
            List<PhoneSocialStateData.NotificationEntry> currentNotifications,
            List<PhoneSocialStateData.FollowEntry> previousFollows,
            List<PhoneSocialStateData.FollowEntry> currentFollows
    ) {
        if (socialNotificationsInitialized
                && (hasNewSocialNotification(previousNotifications, currentNotifications)
                || hasNewIncomingFollow(previousFollows, currentFollows))) {
            playNotification();
        }
        socialNotificationsInitialized = true;
    }

    public static void tick() {
        if (nextIncomingRepeatAt <= 0L || System.currentTimeMillis() < nextIncomingRepeatAt) return;
        nextIncomingRepeatAt = 0L;
        if ("INCOMING".equals(PhoneClientData.callStatus())) playIncomingCallOnce();
    }

    public static void reset() {
        phoneNotificationsInitialized = false;
        socialNotificationsInitialized = false;
        nextIncomingRepeatAt = 0L;
        stop(PhoneSounds.OUTGOING_CALL);
        stop(PhoneSounds.INCOMING_CALL);
    }

    private static void handleCallTransition(String previousStatus, String currentStatus) {
        String previous = previousStatus == null ? "IDLE" : previousStatus;
        String current = currentStatus == null ? "IDLE" : currentStatus;

        if (!"OUTGOING".equals(current)) stop(PhoneSounds.OUTGOING_CALL);
        if (!"INCOMING".equals(current)) {
            nextIncomingRepeatAt = 0L;
            stop(PhoneSounds.INCOMING_CALL);
        }

        if ("OUTGOING".equals(current) && !"OUTGOING".equals(previous)) {
            playUi(PhoneSounds.OUTGOING_CALL, OUTGOING_CALL_VOLUME_MULTIPLIER);
        }
        if ("INCOMING".equals(current) && !"INCOMING".equals(previous)) {
            if (playIncomingCallOnce()) nextIncomingRepeatAt = System.currentTimeMillis() + INCOMING_REPEAT_DELAY_MS;
        }
    }

    private static boolean playIncomingCallOnce() {
        if (PhoneSettingsStore.doNotDisturb() || PhonePrivacyStore.muted(PhoneClientData.callPeerUuid())) return false;
        return playUi(PhoneSounds.INCOMING_CALL);
    }

    private static void playMessageReceive() {
        if (notificationsAllowed()) playUi(PhoneSounds.MESSAGE_RECEIVE);
    }

    public static void playCalendarNotification() { playNotification(); }

    private static void playNotification() {
        if (notificationsAllowed()) playUi(PhoneSounds.NOTIFICATION);
    }

    private static boolean notificationsAllowed() {
        return PhoneSettingsStore.messageNotifications() && !PhoneSettingsStore.doNotDisturb();
    }

    private static boolean playUi(Supplier<SoundEvent> sound) {
        return playUi(sound, 1.0F);
    }

    private static boolean playUi(Supplier<SoundEvent> sound, float volumeMultiplier) {
        Minecraft minecraft = Minecraft.getInstance();
        float volume = PhoneSettingsStore.phoneVolume() * Math.max(0.0F, volumeMultiplier);
        if (minecraft == null || sound == null || volume <= 0.0F) return false;
        try {
            SoundEvent event = sound.get();
            if (event == null) return false;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, volume));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void stop(Supplier<SoundEvent> sound) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || sound == null) return;
        try {
            SoundEvent event = sound.get();
            if (event != null) minecraft.getSoundManager().stop(event.getLocation(), SoundSource.MASTER);
        } catch (RuntimeException ignored) {
            // Registration may not be ready during an early disconnect/reset.
        }
    }

    private static PhoneStateData.MessageEntry newIncomingMessage(
            List<PhoneStateData.MessageEntry> previous,
            List<PhoneStateData.MessageEntry> current
    ) {
        Set<String> previousIds = messageIds(previous);
        if (current == null) return null;
        PhoneStateData.MessageEntry newest = null;
        for (PhoneStateData.MessageEntry message : current) {
            if (message == null || message.id == null || message.id.isBlank() || previousIds.contains(message.id)) continue;
            if (PhoneClientData.isSelfMessageUuid(message.fromUuid)) continue;
            if (message.read || PhonePrivacyStore.muted(message.type.startsWith("GROUP_")?message.toUuid:message.fromUuid)) continue;
            if (newest == null || message.timestamp > newest.timestamp) newest = message;
        }
        return newest;
    }

    private static boolean isOpenMessageThreadFor(PhoneStateData.MessageEntry message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || message == null) return false;
        if(message.type.startsWith("GROUP_"))return minecraft.screen instanceof com.mikasrevs.newphone.client.zphone.gui.PhoneGroupThreadScreen group&&group.matchesTarget(message.toUuid);
        if (PhoneClientData.isTinderMessageType(message.type)) {
            return minecraft.screen instanceof ZPhoneTinderChatScreen tinder && tinder.matchesTarget(message.fromUuid);
        }
        return minecraft.screen instanceof ZPhoneMessageThreadScreen thread && thread.matchesTarget(message.fromUuid);
    }

    private static Set<String> messageIds(List<PhoneStateData.MessageEntry> messages) {
        Set<String> ids = new HashSet<>();
        if (messages == null) return ids;
        for (PhoneStateData.MessageEntry message : messages) {
            if (message != null && message.id != null && !message.id.isBlank()) ids.add(message.id);
        }
        return ids;
    }

    private static boolean hasNewIncomingRequest(
            List<PhoneStateData.PlayerEntry> previous,
            List<PhoneStateData.PlayerEntry> current
    ) {
        Set<String> previousIds = playerIds(previous);
        if (current == null) return false;
        for (PhoneStateData.PlayerEntry player : current) {
            if (player != null && player.uuid != null && !player.uuid.isBlank() && !previousIds.contains(player.uuid) && !PhonePrivacyStore.muted(player.uuid)) return true;
        }
        return false;
    }

    private static Set<String> playerIds(List<PhoneStateData.PlayerEntry> players) {
        Set<String> ids = new HashSet<>();
        if (players == null) return ids;
        for (PhoneStateData.PlayerEntry player : players) {
            if (player != null && player.uuid != null && !player.uuid.isBlank()) ids.add(player.uuid);
        }
        return ids;
    }

    private static boolean hasNewSocialNotification(
            List<PhoneSocialStateData.NotificationEntry> previous,
            List<PhoneSocialStateData.NotificationEntry> current
    ) {
        Set<String> previousIds = socialNotificationIds(previous);
        String self = PhoneClientData.selfUuid();
        if (current == null) return false;
        for (PhoneSocialStateData.NotificationEntry notification : current) {
            if (notification == null || notification.id == null || notification.id.isBlank()) continue;
            if (previousIds.contains(notification.id) || notification.read) continue;
            if (notification.targetUuid != null && !notification.targetUuid.isBlank()
                    && !Objects.equals(self, notification.targetUuid)) continue;
            if (Objects.equals(self, notification.actorUuid) || PhonePrivacyStore.muted(notification.actorUuid)) continue;
            return true;
        }
        return false;
    }

    private static Set<String> socialNotificationIds(List<PhoneSocialStateData.NotificationEntry> notifications) {
        Set<String> ids = new HashSet<>();
        if (notifications == null) return ids;
        for (PhoneSocialStateData.NotificationEntry notification : notifications) {
            if (notification != null && notification.id != null && !notification.id.isBlank()) ids.add(notification.id);
        }
        return ids;
    }

    private static boolean hasNewIncomingFollow(
            List<PhoneSocialStateData.FollowEntry> previous,
            List<PhoneSocialStateData.FollowEntry> current
    ) {
        Set<String> previousIds = followIds(previous);
        String self = PhoneClientData.selfUuid();
        if (current == null) return false;
        for (PhoneSocialStateData.FollowEntry follow : current) {
            if (follow == null || follow.id == null || follow.id.isBlank() || previousIds.contains(follow.id)) continue;
            if (!Objects.equals(self, follow.toUuid) || Objects.equals(self, follow.fromUuid)) continue;
            if (!"PENDING".equalsIgnoreCase(follow.status) || PhonePrivacyStore.muted(follow.fromUuid)) continue;
            return true;
        }
        return false;
    }

    private static Set<String> followIds(List<PhoneSocialStateData.FollowEntry> follows) {
        Set<String> ids = new HashSet<>();
        if (follows == null) return ids;
        for (PhoneSocialStateData.FollowEntry follow : follows) {
            if (follow != null && follow.id != null && !follow.id.isBlank()) ids.add(follow.id);
        }
        return ids;
    }
}

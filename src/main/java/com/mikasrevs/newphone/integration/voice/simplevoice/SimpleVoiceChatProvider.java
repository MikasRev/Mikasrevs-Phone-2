package com.mikasrevs.newphone.integration.voice.simplevoice;

import com.mikasrevs.newphone.integration.voice.PhoneVoiceManager;
import com.mikasrevs.newphone.integration.voice.PhoneVoiceProvider;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Simple Voice Chat bridge that behaves like a real phone call:
 *
 * - The original microphone packet is NOT cancelled, so normal proximity/group
 *   voice keeps working exactly as Simple Voice Chat normally handles it.
 * - During an active New Phone call, the same microphone audio is additionally
 *   sent to the other participant as a non-directional static sound packet.
 * - No Simple Voice Chat group is created or modified.
 */
final class SimpleVoiceChatProvider implements PhoneVoiceProvider {
    private final VoicechatServerApi api;
    private final ConcurrentMap<UUID, PhoneAudioSession> sessionsByPlayer = new ConcurrentHashMap<>();

    SimpleVoiceChatProvider(VoicechatServerApi api) {
        this.api = api;
    }

    @Override
    public String id() {
        return "simple_voice_chat";
    }

    @Override
    public String displayName() {
        return "Simple Voice Chat";
    }

    @Override
    public String playerProblem(UUID playerUuid) {
        VoicechatConnection connection = api.getConnectionOf(playerUuid);
        if (connection == null || !connection.isInstalled()) {
            return "Simple Voice Chat bu oyuncuda yüklü değil.";
        }
        if (!connection.isConnected()) {
            return "Bu oyuncu Simple Voice Chat sunucusuna bağlı değil.";
        }
        if (connection.isDisabled()) {
            return "Bu oyuncunun Simple Voice Chat bağlantısı devre dışı.";
        }
        return "";
    }

    @Override
    public PhoneVoiceManager.StartResult startCall(UUID firstPlayer, UUID secondPlayer) {
        if (firstPlayer == null || secondPlayer == null || firstPlayer.equals(secondPlayer)) {
            return PhoneVoiceManager.StartResult.failure("Geçersiz telefon görüşmesi.");
        }

        String firstProblem = playerProblem(firstPlayer);
        if (!firstProblem.isEmpty()) {
            return PhoneVoiceManager.StartResult.failure(firstProblem);
        }
        String secondProblem = playerProblem(secondPlayer);
        if (!secondProblem.isEmpty()) {
            return PhoneVoiceManager.StartResult.failure(secondProblem);
        }

        // PhoneServerService already prevents overlapping calls. This second
        // guard keeps the voice bridge safe even if another caller reaches it.
        if (sessionsByPlayer.containsKey(firstPlayer) || sessionsByPlayer.containsKey(secondPlayer)) {
            return PhoneVoiceManager.StartResult.failure("Oyunculardan biri zaten telefon görüşmesinde.");
        }

        PhoneAudioSession session = new PhoneAudioSession(
                UUID.randomUUID(),
                firstPlayer,
                secondPlayer,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        PhoneAudioSession firstExisting = sessionsByPlayer.putIfAbsent(firstPlayer, session);
        if (firstExisting != null) {
            return PhoneVoiceManager.StartResult.failure("İlk oyuncu zaten telefon görüşmesinde.");
        }

        PhoneAudioSession secondExisting = sessionsByPlayer.putIfAbsent(secondPlayer, session);
        if (secondExisting != null) {
            sessionsByPlayer.remove(firstPlayer, session);
            return PhoneVoiceManager.StartResult.failure("İkinci oyuncu zaten telefon görüşmesinde.");
        }

        return PhoneVoiceManager.StartResult.success(session.id().toString());
    }

    @Override
    public void endCall(String sessionId, UUID firstPlayer, UUID secondPlayer) {
        UUID expectedSessionId = parseUuid(sessionId);
        removeSessionFor(firstPlayer, expectedSessionId);
        removeSessionFor(secondPlayer, expectedSessionId);
    }

    /**
     * Called for every microphone packet that arrives at the Simple Voice Chat
     * server. The event is deliberately left untouched so Simple Voice Chat can
     * still deliver the original packet through proximity/group chat.
     */
    void routeMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();
        if (senderConnection == null || senderConnection.getPlayer() == null) {
            return;
        }

        UUID senderId = senderConnection.getPlayer().getUuid();
        PhoneAudioSession session = sessionsByPlayer.get(senderId);
        if (session == null) {
            return;
        }

        UUID receiverId = session.other(senderId);
        if (receiverId == null) {
            return;
        }

        VoicechatConnection receiverConnection = api.getConnectionOf(receiverId);
        if (receiverConnection == null
                || !receiverConnection.isInstalled()
                || !receiverConnection.isConnected()
                || receiverConnection.isDisabled()) {
            return;
        }

        UUID channelId = session.channelForSender(senderId);
        if (channelId == null) {
            return;
        }

        try {
            // Building from the microphone packet preserves its Opus audio and
            // packet sequence. StaticSoundPacket makes it non-directional and
            // independent from the players' distance/dimension.
            StaticSoundPacket phonePacket = event.getPacket()
                    .staticSoundPacketBuilder()
                    .channelId(channelId)
                    .sender(senderId)
                    .build();
            api.sendStaticSoundPacketTo(receiverConnection, phonePacket);
        } catch (RuntimeException ignored) {
            // A transient voice connection failure must never break normal
            // proximity voice or New Phone's server networking.
        }
    }

    void shutdown() {
        sessionsByPlayer.clear();
    }

    private void removeSessionFor(UUID playerId, UUID expectedSessionId) {
        if (playerId == null) {
            return;
        }
        PhoneAudioSession session = sessionsByPlayer.get(playerId);
        if (session == null) {
            return;
        }
        if (expectedSessionId != null && !expectedSessionId.equals(session.id())) {
            return;
        }
        sessionsByPlayer.remove(session.firstPlayer(), session);
        sessionsByPlayer.remove(session.secondPlayer(), session);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record PhoneAudioSession(
            UUID id,
            UUID firstPlayer,
            UUID secondPlayer,
            UUID firstToSecondChannel,
            UUID secondToFirstChannel
    ) {
        UUID other(UUID playerId) {
            if (firstPlayer.equals(playerId)) {
                return secondPlayer;
            }
            if (secondPlayer.equals(playerId)) {
                return firstPlayer;
            }
            return null;
        }

        UUID channelForSender(UUID playerId) {
            if (firstPlayer.equals(playerId)) {
                return firstToSecondChannel;
            }
            if (secondPlayer.equals(playerId)) {
                return secondToFirstChannel;
            }
            return null;
        }
    }
}

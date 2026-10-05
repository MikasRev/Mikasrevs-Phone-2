package com.mikasrevs.newphone.integration.voice;

import java.util.UUID;

/**
 * Runtime bridge for optional voice-chat integrations.
 *
 * This interface intentionally has no classes from any external voice-chat API
 * in its signature, so New Phone can load when no supported voice mod exists.
 */
public interface PhoneVoiceProvider {
    String id();

    String displayName();

    /**
     * @return an empty string when the player is ready, otherwise a user-facing reason.
     */
    String playerProblem(UUID playerUuid);

    /**
     * Starts phone-audio routing between the two players without changing their
     * normal proximity/group voice-chat state.
     */
    PhoneVoiceManager.StartResult startCall(UUID firstPlayer, UUID secondPlayer);

    /**
     * Stops only New Phone's extra phone-audio routing.
     */
    void endCall(String sessionId, UUID firstPlayer, UUID secondPlayer);
}

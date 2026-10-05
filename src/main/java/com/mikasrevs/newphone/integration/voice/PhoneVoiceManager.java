package com.mikasrevs.newphone.integration.voice;

import java.util.UUID;

public final class PhoneVoiceManager {
    private static volatile PhoneVoiceProvider provider;

    private PhoneVoiceManager() {
    }

    public static void install(PhoneVoiceProvider newProvider) {
        provider = newProvider;
    }

    public static void uninstall(PhoneVoiceProvider expectedProvider) {
        if (provider == expectedProvider) {
            provider = null;
        }
    }

    public static boolean isAvailable() {
        return provider != null;
    }

    public static String providerId() {
        PhoneVoiceProvider current = provider;
        return current == null ? "" : current.id();
    }

    public static String providerDisplayName() {
        PhoneVoiceProvider current = provider;
        return current == null ? "" : current.displayName();
    }

    public static String playerProblem(UUID playerUuid) {
        PhoneVoiceProvider current = provider;
        if (current == null) {
            return "Simple Voice Chat yüklü değil veya sunucuda aktif değil.";
        }
        return current.playerProblem(playerUuid);
    }

    public static StartResult startCall(UUID firstPlayer, UUID secondPlayer) {
        PhoneVoiceProvider current = provider;
        if (current == null) {
            return StartResult.failure("Simple Voice Chat yüklü değil veya sunucuda aktif değil.");
        }
        return current.startCall(firstPlayer, secondPlayer);
    }

    public static void endCall(String sessionId, UUID firstPlayer, UUID secondPlayer) {
        PhoneVoiceProvider current = provider;
        if (current == null) {
            return;
        }
        current.endCall(sessionId, firstPlayer, secondPlayer);
    }

    public record StartResult(boolean success, String sessionId, String error) {
        public static StartResult success(String sessionId) {
            return new StartResult(true, sessionId == null ? "" : sessionId, "");
        }

        public static StartResult failure(String error) {
            return new StartResult(false, "", error == null ? "Sesli arama başlatılamadı." : error);
        }
    }
}

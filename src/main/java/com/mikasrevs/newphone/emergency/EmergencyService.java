package com.mikasrevs.newphone.emergency;

import java.util.Locale;

public enum EmergencyService {
    POLICE("emergency.newphone.service.police"),
    EMS("emergency.newphone.service.ems");

    private final String translationKey;

    EmergencyService(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public static EmergencyService fromId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

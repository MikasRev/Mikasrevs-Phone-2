package com.mikasrevs.newphone.emergency;

public enum EmergencyRisk {
    LOW(1, "emergency.newphone.risk.low"),
    MEDIUM(2, "emergency.newphone.risk.medium"),
    HIGH(3, "emergency.newphone.risk.high");

    private final int priority;
    private final String translationKey;

    EmergencyRisk(int priority, String translationKey) {
        this.priority = priority;
        this.translationKey = translationKey;
    }

    public int priority() {
        return priority;
    }

    public String translationKey() {
        return translationKey;
    }
}

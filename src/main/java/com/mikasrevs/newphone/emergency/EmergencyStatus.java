package com.mikasrevs.newphone.emergency;

public enum EmergencyStatus {
    WAITING,
    ACCEPTED,
    RESPONDING,
    ON_SCENE,
    CLOSED,
    CANCELLED;

    public boolean active() {
        return this != CLOSED && this != CANCELLED;
    }
}

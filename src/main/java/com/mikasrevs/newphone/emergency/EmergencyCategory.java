package com.mikasrevs.newphone.emergency;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Single source of truth for caller-selectable dispatch policy. */
public enum EmergencyCategory {
    SUSPICIOUS_ACTIVITY("suspicious_activity", EmergencyService.POLICE, EmergencyRisk.LOW, 2),
    FIGHT_ASSAULT("fight_assault", EmergencyService.POLICE, EmergencyRisk.LOW, 2),
    VEHICLE_INCIDENT("vehicle_incident", EmergencyService.POLICE, EmergencyRisk.LOW, 2),
    ARMED_THREAT("armed_threat", EmergencyService.POLICE, EmergencyRisk.HIGH, 4),
    ROBBERY("robbery", EmergencyService.POLICE, EmergencyRisk.MEDIUM, 3),
    POLICE_OTHER("police_other", EmergencyService.POLICE, EmergencyRisk.MEDIUM, 3),

    INJURY("injury", EmergencyService.EMS, EmergencyRisk.LOW, 1),
    TRAFFIC_ACCIDENT("traffic_accident", EmergencyService.EMS, EmergencyRisk.MEDIUM, 2),
    GUNSHOT_WOUND("gunshot_wound", EmergencyService.EMS, EmergencyRisk.HIGH, 3),
    SEVERE_BLEEDING("severe_bleeding", EmergencyService.EMS, EmergencyRisk.HIGH, 2),
    UNCONSCIOUS_PERSON("unconscious_person", EmergencyService.EMS, EmergencyRisk.MEDIUM, 2),
    EMS_OTHER("ems_other", EmergencyService.EMS, EmergencyRisk.MEDIUM, 2);

    private final String id;
    private final EmergencyService service;
    private final EmergencyRisk risk;
    private final int maximumResponders;

    EmergencyCategory(String id, EmergencyService service, EmergencyRisk risk, int maximumResponders) {
        this.id = id;
        this.service = service;
        this.risk = risk;
        this.maximumResponders = maximumResponders;
    }

    public String id() {
        return id;
    }

    public EmergencyService service() {
        return service;
    }

    public EmergencyRisk risk() {
        return risk;
    }

    public int priority() {
        return risk.priority();
    }

    public int maximumResponders() {
        return maximumResponders;
    }

    public String translationKey() {
        return "emergency.newphone.category." + id;
    }

    public static EmergencyCategory fromId(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (EmergencyCategory category : values()) {
            if (category.id.equals(normalized)) return category;
        }
        return null;
    }

    public static List<EmergencyCategory> forService(EmergencyService service) {
        if (service == null) return List.of();
        return Arrays.stream(values()).filter(category -> category.service == service).toList();
    }
}

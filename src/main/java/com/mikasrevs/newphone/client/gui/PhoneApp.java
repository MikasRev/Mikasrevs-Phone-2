package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;

public enum PhoneApp {
    MESSAGES("screen.newphone.app.messages", 0, 0xFF34C759),
    CONTACTS("screen.newphone.app.contacts", 1, 0xFF2F80FF),
    JOURNAL("screen.newphone.app.journal", 2, 0xFF7357D8),
    CALCULATOR("screen.newphone.app.calculator", 2, 0xFF1D65B8),
    GPS("screen.newphone.app.gps", 3, 0xFF2E89D8),
    TASKS("screen.newphone.app.tasks", 4, 0xFFF2994A),
    NOTES("screen.newphone.app.notes", 4, 0xFFB97835),
    MUSIC("screen.newphone.app.music", 7, 0xFF2E72D2),
    YOUTUBE("screen.newphone.app.youtube", -1, 0xFFFF0033),
    CALENDAR("screen.newphone.app.calendar", 8, 0xFF2E89D8),
    CLOCK("screen.newphone.app.clock", 9, 0xFF176ED0),
    BLOCKPOST("screen.newphone.app.blockpost", 15, 0xFF05070A),
    BLOCKGRAM("screen.newphone.app.blockgram", 14, 0xFF9C1BCF),
    CAMERA("screen.newphone.app.camera", 5, 0xFF397DDB),
    GALLERY("screen.newphone.app.gallery", 6, 0xFF4DA3FF),
    EVIDENCE("screen.newphone.app.evidence", 6, 0xFFB98B48),
    STORE("screen.newphone.app.store", 7, 0xFF805AD5),
    MINESTORE("screen.newphone.app.minestore", -1, 0xFF1578D4),
    BANK("screen.newphone.app.bank", -1, 0xFF15926D),
    MARKETPLACE("screen.newphone.app.marketplace", -1, 0xFF32AD48),
    BLOCK_SNAKE("screen.newphone.app.block_snake", -1, 0xFF26C56F),
    CREEPER_RUSH("screen.newphone.app.creeper_rush", -1, 0xFF7F5AF0),
    GEM_PAIRS("screen.newphone.app.gem_pairs", -1, 0xFFFF8A3D),
    CODEX("screen.newphone.app.codex", 8, 0xFF607D8B),
    FORUMS("screen.newphone.app.forums", 9, 0xFF536DFE),
    NEWS("screen.newphone.app.news", 10, 0xFFD94B4B),
    SETTINGS("screen.newphone.app.settings", 11, 0xFF7958D8),
    PHONE("screen.newphone.app.phone", 12, 0xFF2DBE68),
    EMERGENCY("screen.newphone.app.emergency", -1, 0xFFD73A49),
    BROWSER("screen.newphone.app.browser", 13, 0xFFFF4D67),
    SOCIAL("screen.newphone.app.social", 14, 0xFF8A5BD8),
    ADMIN_PANEL("screen.newphone.app.admin_panel", -1, 0xFF335F9E),
    CHAT("screen.newphone.app.chat", 15, 0xFF3586E8);

    private final String translationKey;
    private final int iconIndex;
    private final int launchColor;

    PhoneApp(String translationKey, int iconIndex, int launchColor) {
        this.translationKey = translationKey;
        this.iconIndex = iconIndex;
        this.launchColor = launchColor;
    }

    public String displayName() {
        return switch (this) {
            case MESSAGES -> PhoneSettingsStore.text("Mesajlar", "Messages");
            case CONTACTS -> PhoneSettingsStore.text("Kişiler", "Contacts");
            case JOURNAL -> PhoneSettingsStore.text("Günlük", "Journal");
            case CALCULATOR -> PhoneSettingsStore.text("Hesap Makinesi", "Calculator");
            case GPS -> "GPS";
            case TASKS -> PhoneSettingsStore.text("Görevler", "Tasks");
            case NOTES -> PhoneSettingsStore.text("Notlar", "Notes");
            case MUSIC -> PhoneSettingsStore.text("Müzik", "Music");
            case YOUTUBE -> "YouTube";
            case CALENDAR -> PhoneSettingsStore.text("Takvim", "Calendar");
            case CLOCK -> PhoneSettingsStore.text("Saat", "Clock");
            case BLOCKPOST -> "BlockPost";
            case BLOCKGRAM -> "Lumi";
            case CAMERA -> PhoneSettingsStore.text("Kamera", "Camera");
            case GALLERY -> PhoneSettingsStore.text("Galeri", "Gallery");
            case EVIDENCE -> PhoneSettingsStore.text("Kanıtlar", "Evidence");
            case STORE -> PhoneSettingsStore.text("Mağaza", "Store");
            case MINESTORE -> "MineStore";
            case BANK -> PhoneSettingsStore.text("Banka", "Bank");
            case MARKETPLACE -> "Marketplace";
            case BLOCK_SNAKE -> "Block Snake";
            case CREEPER_RUSH -> "Creeper Rush";
            case GEM_PAIRS -> "Gem Pairs";
            case CODEX -> PhoneSettingsStore.text("Rehber", "Codex");
            case FORUMS -> PhoneSettingsStore.text("Forum", "Forums");
            case NEWS -> PhoneSettingsStore.text("Haberler", "News");
            case ADMIN_PANEL -> PhoneSettingsStore.text("Yönetim", "Admin Panel");
            case SETTINGS -> PhoneSettingsStore.text("Ayarlar", "Settings");
            case PHONE -> PhoneSettingsStore.text("Telefon", "Phone");
            case EMERGENCY -> PhoneSettingsStore.translation(translationKey);
            case BROWSER -> "Minder";
            case SOCIAL -> PhoneSettingsStore.text("Sosyal", "Social");
            case CHAT -> PhoneSettingsStore.text("Sohbet", "Chat");
        };
    }

    public int iconIndex() {
        return this.iconIndex;
    }

    public int launchColor() {
        return this.launchColor;
    }
}

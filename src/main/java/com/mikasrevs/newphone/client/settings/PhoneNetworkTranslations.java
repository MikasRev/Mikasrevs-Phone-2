package com.mikasrevs.newphone.client.settings;

/** Exact system notices only; arbitrary player messages remain untouched. */
final class PhoneNetworkTranslations {
    private PhoneNetworkTranslations() {}
    static String translate(String raw) {
        return switch (raw) {
            case "En az bir kişi seç." -> PhoneSettingsStore.text("En az bir kişi seç.", "Select at least one contact.");
            case "Artık bu grubun üyesi değilsin." -> PhoneSettingsStore.text("Artık bu grubun üyesi değilsin.", "You are no longer a member of this group.");
            case "Yalnızca uygun kişilerin gruba eklenebilir. Engellemeleri kontrol et." -> PhoneSettingsStore.text(raw, "Only eligible contacts can join the group. Check blocked players.");
            case "Bu işlemi yalnızca grup yöneticisi yapabilir." -> PhoneSettingsStore.text(raw, "Only the group admin can do this.");
            case "Bu kişi eklenemiyor. Kişilerini ve engellemeleri kontrol et." -> PhoneSettingsStore.text(raw, "Cannot add this player. Check your contacts and blocked players.");
            case "Grup adı 1–40 karakter olmalı." -> PhoneSettingsStore.text(raw, "Group name must be 1–40 characters.");
            case "Grup işlemi tamamlanamadı." -> PhoneSettingsStore.text(raw, "Could not complete the group action.");
            case "Gönderi güncellendi." -> PhoneSettingsStore.text(raw, "Post updated.");
            case "Lumi gönderisi paylaşılamadı." -> PhoneSettingsStore.text(raw, "Could not share the Lumi post.");
            case "Fotoğraf verisi okunamadı." -> PhoneSettingsStore.text(raw, "Could not read the photo data.");
            case "Profilin dolu (50 gönderi). Yeni paylaşım için bir gönderiyi sil veya Ayarlar > Gönderilerim bölümünden arşivle." -> PhoneSettingsStore.text(raw, "Profile full (50 posts). Delete a post or archive it in Settings > My posts.");
            default -> null;
        };
    }
}

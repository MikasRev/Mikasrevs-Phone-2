package com.mikasrevs.newphone.client.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Locale;

public final class PhoneSettingsStore {
    private static final int SETUP_FLOW_VERSION = 2;
    private static final java.util.List<String> LANGUAGE_CODES = java.util.List.of(
            "tr", "en", "es", "pt_br", "de", "fr", "ru", "pl", "it", "zh_cn", "zh_tw");
    private static final java.util.List<String> LANGUAGE_LABELS = java.util.List.of(
            "Türkçe", "English", "Español", "Português (Brasil)", "Deutsch", "Français",
            "Русский", "Polski", "Italiano", "简体中文", "繁體中文");

    public enum HomeLayoutMode {
        CLASSIC,
        ALL_APPS
    }

    public enum WallpaperTarget {
        HOME,
        LOCK,
        BOTH
    }

    private static final Path SETTINGS_FILE = Path.of(
            System.getProperty("user.dir"),
            "config",
            "newphone",
            "phone_settings.properties"
    );

    private static final int[] ACCENT_COLORS = {
            0xFF4C9DFF,
            0xFF8B6CFF,
            0xFF35C98A,
            0xFFFF8A4C
    };
    private static final String[] ACCENT_NAMES_TR = {"Mavi", "Mor", "Yeşil", "Turuncu"};
    private static final String[] ACCENT_NAMES_EN = {"Blue", "Purple", "Green", "Orange"};
    private static final String[] ACCENT_NAMES_ES = {"Azul", "Morado", "Verde", "Naranja"};
    private static final String[] ACCENT_NAMES_PT_BR = {"Azul", "Roxo", "Verde", "Laranja"};
    private static final String[] ACCENT_NAMES_DE = {"Blau", "Lila", "Grün", "Orange"};
    private static final String[] ACCENT_NAMES_FR = {"Bleu", "Violet", "Vert", "Orange"};
    private static final String[] ACCENT_NAMES_RU = {"Синий", "Фиолетовый", "Зелёный", "Оранжевый"};
    private static final String[] ACCENT_NAMES_PL = {"Niebieski", "Fioletowy", "Zielony", "Pomarańczowy"};
    private static final String[] ACCENT_NAMES_IT = {"Blu", "Viola", "Verde", "Arancione"};
    private static final float[] PHONE_SCALES = {0.90F, 1.00F, 1.08F};
    private static final String[] PHONE_SCALE_NAMES_TR = {"Küçük", "Normal", "Büyük"};
    private static final String[] PHONE_SCALE_NAMES_EN = {"Small", "Normal", "Large"};
    private static final String[] PHONE_SCALE_NAMES_ES = {"Pequeño", "Normal", "Grande"};
    private static final String[] PHONE_SCALE_NAMES_PT_BR = {"Pequeno", "Normal", "Grande"};
    private static final String[] PHONE_SCALE_NAMES_DE = {"Klein", "Normal", "Groß"};
    private static final String[] PHONE_SCALE_NAMES_FR = {"Petit", "Normal", "Grand"};
    private static final String[] PHONE_SCALE_NAMES_RU = {"Маленький", "Обычный", "Большой"};
    private static final String[] PHONE_SCALE_NAMES_PL = {"Mały", "Normalny", "Duży"};
    private static final String[] PHONE_SCALE_NAMES_IT = {"Piccolo", "Normale", "Grande"};
    private static final int[] AUTO_LOCK_MINUTES = {0, 1, 5, 15};
    private static final String[] WALLPAPER_NAMES_TR = {
            "Dağ Gün Batımı", "Neon Şehir", "Tropik Gün Batımı", "Kristal",
            "Kozmik Bulutsu", "Ay Işığında Kış", "Elektrik Mavisi", "Siyah ve Altın", "Büyülü Orman"
    };
    private static final String[] WALLPAPER_NAMES_EN = {
            "Alpine Sunset", "Neon City", "Tropical Sunset", "Crystal Glass",
            "Cosmic Nebula", "Moonlit Winter", "Electric Blue", "Black & Gold", "Enchanted Forest"
    };
    private static final String[] WALLPAPER_NAMES_ES = {
            "Atardecer Alpino", "Ciudad Neón", "Atardecer Tropical", "Cristal",
            "Nebulosa Cósmica", "Invierno Lunar", "Azul Eléctrico", "Negro y Dorado", "Bosque Encantado"
    };
    private static final String[] WALLPAPER_NAMES_PT_BR = {
            "Pôr do Sol Alpino", "Cidade Neon", "Pôr do Sol Tropical", "Cristal",
            "Nebulosa Cósmica", "Inverno ao Luar", "Azul Elétrico", "Preto e Dourado", "Floresta Encantada"
    };
    private static final String[] WALLPAPER_NAMES_DE = {
            "Alpenglühen", "Neonstadt", "Tropischer Sonnenuntergang", "Kristallglas",
            "Kosmischer Nebel", "Mondwinter", "Elektroblau", "Schwarz & Gold", "Zauberwald"
    };
    private static final String[] WALLPAPER_NAMES_FR = {
            "Coucher Alpin", "Ville Néon", "Coucher Tropical", "Cristal",
            "Nébuleuse Cosmique", "Hiver au Clair de Lune", "Bleu Électrique", "Noir et Or", "Forêt Enchantée"
    };
    private static final String[] WALLPAPER_NAMES_RU = {
            "Альпийский закат", "Неоновый город", "Тропический закат", "Хрустальное стекло",
            "Космическая туманность", "Лунная зима", "Электрический синий", "Чёрный и золотой", "Зачарованный лес"
    };
    private static final String[] WALLPAPER_NAMES_PL = {
            "Alpejski zachód", "Neonowe miasto", "Tropikalny zachód", "Kryształ",
            "Kosmiczna mgławica", "Księżycowa zima", "Elektryczny błękit", "Czerń i złoto", "Zaczarowany las"
    };
    private static final String[] WALLPAPER_NAMES_IT = {
            "Tramonto Alpino", "Città Neon", "Tramonto Tropicale", "Cristallo",
            "Nebulosa Cosmica", "Inverno al Chiaro di Luna", "Blu Elettrico", "Nero e Oro", "Foresta Incantata"
    };

    private static boolean loaded;
    private static float brightness = 0.86F;
    private static int accentIndex;
    private static int phoneScaleIndex = 1;
    private static float phoneVolume = 0.78F;
    private static boolean messageNotifications = true;
    private static boolean vibration = true;
    private static boolean lockScreenEnabled = true;
    private static int autoLockIndex = 2;
    private static String language = "tr";
    private static boolean use24HourClock = true;
    private static boolean useMinecraftTime;
    private static boolean showPhoneNumber = true;
    private static boolean animationsEnabled = true;
    private static boolean incomingCallAnimationEnabled = true;
    private static int wallpaperIndex;
    private static int lockWallpaperIndex;
    private static String homeWallpaperPhotoPath = "";
    private static String lockWallpaperPhotoPath = "";
    private static boolean pinlessUnlockMode;
    private static boolean airplaneMode;
    private static boolean wifiEnabled = true;
    private static boolean bluetoothEnabled = true;
    private static boolean doNotDisturb;
    private static boolean setupCompleted;
    private static int setupStep;
    private static int setupFlowVersion = SETUP_FLOW_VERSION;
    private static HomeLayoutMode homeLayoutMode = HomeLayoutMode.CLASSIC;

    private PhoneSettingsStore() {
    }

    public static boolean setupCompleted() {
        ensureLoaded();
        return setupCompleted;
    }

    public static int setupStep() {
        ensureLoaded();
        return setupStep;
    }

    public static void setSetupStep(int value) {
        ensureLoaded();
        setupStep = clamp(value, 0, 4);
        save();
    }

    public static void restartSetup() {
        ensureLoaded();
        setupCompleted = false;
        setupStep = 0;
        setupFlowVersion = SETUP_FLOW_VERSION;
        save();
    }

    public static boolean completeSetup() {
        ensureLoaded();
        setupCompleted = true;
        setupStep = 0;
        if (save()) return true;
        setupCompleted = false;
        setupStep = 4;
        return false;
    }

    public static HomeLayoutMode homeLayoutMode() {
        ensureLoaded();
        return homeLayoutMode;
    }

    public static void setHomeLayoutMode(HomeLayoutMode value) {
        ensureLoaded();
        homeLayoutMode = value == null ? HomeLayoutMode.CLASSIC : value;
        save();
    }

    public static void cycleHomeLayoutMode() {
        setHomeLayoutMode(homeLayoutMode() == HomeLayoutMode.CLASSIC
                ? HomeLayoutMode.ALL_APPS
                : HomeLayoutMode.CLASSIC);
    }

    public static String homeLayoutModeLabel() {
        return homeLayoutMode() == HomeLayoutMode.ALL_APPS
                ? text("Tek Sayfa", "All Apps")
                : text("Klasik", "Classic");
    }

    /** Theme follows the phone's built-in appearance; the old light-theme switch is intentionally disabled. */
    public static boolean lightTheme() {
        ensureLoaded();
        return false;
    }

    /** Kept for old callers/config compatibility. */
    public static void setLightTheme(boolean value) {
        ensureLoaded();
    }

    public static float brightness() {
        ensureLoaded();
        return brightness;
    }

    public static void setBrightness(float value) {
        ensureLoaded();
        brightness = clamp(value, 0.25F, 1.0F);
        save();
    }

    public static int accentColor() {
        ensureLoaded();
        return ACCENT_COLORS[accentIndex];
    }

    public static int accentIndex() {
        ensureLoaded();
        return accentIndex;
    }

    public static int accentCount() {
        return ACCENT_COLORS.length;
    }

    public static int accentColor(int index) {
        return ACCENT_COLORS[clamp(index, 0, ACCENT_COLORS.length - 1)];
    }

    public static void setAccentIndex(int value) {
        ensureLoaded();
        accentIndex = clamp(value, 0, ACCENT_COLORS.length - 1);
        save();
    }

    public static void cycleAccent() {
        ensureLoaded();
        accentIndex = (accentIndex + 1) % ACCENT_COLORS.length;
        save();
    }

    public static String accentName() {
        ensureLoaded();
        if (isChinese()) return text("", ACCENT_NAMES_EN[accentIndex]);
        return isTurkish() ? ACCENT_NAMES_TR[accentIndex]
                : isSpanish() ? ACCENT_NAMES_ES[accentIndex]
                : isPortugueseBrazil() ? ACCENT_NAMES_PT_BR[accentIndex]
                : isGerman() ? ACCENT_NAMES_DE[accentIndex]
                : isFrench() ? ACCENT_NAMES_FR[accentIndex]
                : isRussian() ? ACCENT_NAMES_RU[accentIndex]
                : isPolish() ? ACCENT_NAMES_PL[accentIndex]
                : isItalian() ? ACCENT_NAMES_IT[accentIndex]
                : ACCENT_NAMES_EN[accentIndex];
    }

    public static String accentName(int index) {
        ensureLoaded();
        int safeIndex = clamp(index, 0, ACCENT_COLORS.length - 1);
        if (isChinese()) return text("", ACCENT_NAMES_EN[safeIndex]);
        return isTurkish() ? ACCENT_NAMES_TR[safeIndex]
                : isSpanish() ? ACCENT_NAMES_ES[safeIndex]
                : isPortugueseBrazil() ? ACCENT_NAMES_PT_BR[safeIndex]
                : isGerman() ? ACCENT_NAMES_DE[safeIndex]
                : isFrench() ? ACCENT_NAMES_FR[safeIndex]
                : isRussian() ? ACCENT_NAMES_RU[safeIndex]
                : isPolish() ? ACCENT_NAMES_PL[safeIndex]
                : isItalian() ? ACCENT_NAMES_IT[safeIndex]
                : ACCENT_NAMES_EN[safeIndex];
    }

    public static float phoneScale() {
        ensureLoaded();
        return PHONE_SCALES[phoneScaleIndex];
    }

    public static int phoneScaleIndex() {
        ensureLoaded();
        return phoneScaleIndex;
    }

    public static int phoneScaleCount() {
        return PHONE_SCALES.length;
    }

    public static void setPhoneScaleIndex(int value) {
        ensureLoaded();
        phoneScaleIndex = clamp(value, 0, PHONE_SCALES.length - 1);
        save();
    }

    public static void cyclePhoneScale() {
        ensureLoaded();
        phoneScaleIndex = (phoneScaleIndex + 1) % PHONE_SCALES.length;
        save();
    }

    public static String phoneScaleName() {
        ensureLoaded();
        if (isChinese()) return text("", PHONE_SCALE_NAMES_EN[phoneScaleIndex]);
        return isTurkish() ? PHONE_SCALE_NAMES_TR[phoneScaleIndex]
                : isSpanish() ? PHONE_SCALE_NAMES_ES[phoneScaleIndex]
                : isPortugueseBrazil() ? PHONE_SCALE_NAMES_PT_BR[phoneScaleIndex]
                : isGerman() ? PHONE_SCALE_NAMES_DE[phoneScaleIndex]
                : isFrench() ? PHONE_SCALE_NAMES_FR[phoneScaleIndex]
                : isRussian() ? PHONE_SCALE_NAMES_RU[phoneScaleIndex]
                : isPolish() ? PHONE_SCALE_NAMES_PL[phoneScaleIndex]
                : isItalian() ? PHONE_SCALE_NAMES_IT[phoneScaleIndex]
                : PHONE_SCALE_NAMES_EN[phoneScaleIndex];
    }

    public static String phoneScaleName(int index) {
        ensureLoaded();
        int safeIndex = clamp(index, 0, PHONE_SCALES.length - 1);
        if (isChinese()) return text("", PHONE_SCALE_NAMES_EN[safeIndex]);
        return isTurkish() ? PHONE_SCALE_NAMES_TR[safeIndex]
                : isSpanish() ? PHONE_SCALE_NAMES_ES[safeIndex]
                : isPortugueseBrazil() ? PHONE_SCALE_NAMES_PT_BR[safeIndex]
                : isGerman() ? PHONE_SCALE_NAMES_DE[safeIndex]
                : isFrench() ? PHONE_SCALE_NAMES_FR[safeIndex]
                : isRussian() ? PHONE_SCALE_NAMES_RU[safeIndex]
                : isPolish() ? PHONE_SCALE_NAMES_PL[safeIndex]
                : isItalian() ? PHONE_SCALE_NAMES_IT[safeIndex]
                : PHONE_SCALE_NAMES_EN[safeIndex];
    }

    public static float phoneVolume() {
        ensureLoaded();
        return phoneVolume;
    }

    public static void setPhoneVolume(float value) {
        ensureLoaded();
        phoneVolume = clamp(value, 0.0F, 1.0F);
        save();
    }

    public static boolean messageNotifications() {
        ensureLoaded();
        return messageNotifications;
    }

    public static void setMessageNotifications(boolean value) {
        ensureLoaded();
        messageNotifications = value;
        save();
    }

    /** Camera sound is no longer exposed as a setting; retained as always enabled for compatibility. */
    public static boolean cameraSound() {
        ensureLoaded();
        return true;
    }

    public static void setCameraSound(boolean value) {
        ensureLoaded();
    }

    public static boolean vibration() {
        ensureLoaded();
        return vibration;
    }

    public static void setVibration(boolean value) {
        ensureLoaded();
        vibration = value;
        save();
    }

    public static boolean lockScreenEnabled() {
        ensureLoaded();
        return lockScreenEnabled;
    }

    public static void setLockScreenEnabled(boolean value) {
        ensureLoaded();
        lockScreenEnabled = value;
        save();
    }

    public static int autoLockMinutes() {
        ensureLoaded();
        return AUTO_LOCK_MINUTES[autoLockIndex];
    }

    public static void cycleAutoLock() {
        ensureLoaded();
        autoLockIndex = (autoLockIndex + 1) % AUTO_LOCK_MINUTES.length;
        save();
    }

    public static void setAutoLockIndex(int value) {
        ensureLoaded();
        autoLockIndex = clamp(value, 0, AUTO_LOCK_MINUTES.length - 1);
        save();
    }

    public static String autoLockLabel() {
        ensureLoaded();
        int minutes = AUTO_LOCK_MINUTES[autoLockIndex];
        if (minutes == 0) {
            return text("Kapalı", "Off");
        }
        return minutes + " " + text("dk", "min");
    }

    public static String language() {
        ensureLoaded();
        return language;
    }

    public static void cycleLanguage() {
        ensureLoaded();
        language = LANGUAGE_CODES.get((LANGUAGE_CODES.indexOf(language) + 1) % LANGUAGE_CODES.size());
        save();
    }

    public static java.util.List<String> languageCodes() { return LANGUAGE_CODES; }
    public static String languageLabel(int index) { return LANGUAGE_LABELS.get(index); }

    static String normalizeLanguage(String value) {
        String code = value == null ? "" : value.toLowerCase(Locale.ROOT).replace('-', '_');
        if (LANGUAGE_CODES.contains(code)) return code;
        return switch (code) {
            case "en_us" -> "en";
            case "es_es" -> "es";
            case "pt" -> "pt_br";
            case "de_de" -> "de";
            case "fr_fr" -> "fr";
            case "ru_ru" -> "ru";
            case "pl_pl" -> "pl";
            case "it_it" -> "it";
            case "zh", "zh_hans" -> "zh_cn";
            case "zh_hant", "zh_hk" -> "zh_tw";
            default -> "tr";
        };
    }

    public static void setLanguage(String value) {
        ensureLoaded();
        language = normalizeLanguage(value);
        save();
    }

    public static boolean isTurkish() {
        ensureLoaded();
        return "tr".equals(language);
    }

    public static boolean isSpanish() {
        ensureLoaded();
        return "es".equals(language);
    }

    public static boolean isPortugueseBrazil() {
        ensureLoaded();
        return "pt_br".equals(language);
    }

    public static boolean isGerman() {
        ensureLoaded();
        return "de".equals(language);
    }

    public static boolean isFrench() {
        ensureLoaded();
        return "fr".equals(language);
    }

    public static boolean isRussian() {
        ensureLoaded();
        return "ru".equals(language);
    }

    public static boolean isPolish() {
        ensureLoaded();
        return "pl".equals(language);
    }

    public static boolean isItalian() {
        ensureLoaded();
        return "it".equals(language);
    }

    public static boolean isChinese() {
        return "zh_cn".equals(language()) || "zh_tw".equals(language());
    }

    public static Locale locale() {
        ensureLoaded();
        if (isTurkish()) return Locale.forLanguageTag("tr-TR");
        if (isSpanish()) return Locale.forLanguageTag("es-ES");
        if (isPortugueseBrazil()) return Locale.forLanguageTag("pt-BR");
        if (isGerman()) return Locale.GERMANY;
        if (isFrench()) return Locale.FRANCE;
        if (isRussian()) return Locale.forLanguageTag("ru-RU");
        if (isPolish()) return Locale.forLanguageTag("pl-PL");
        if (isItalian()) return Locale.ITALY;
        if ("zh_cn".equals(language)) return Locale.SIMPLIFIED_CHINESE;
        if ("zh_tw".equals(language)) return Locale.TRADITIONAL_CHINESE;
        return Locale.ENGLISH;
    }

    public static String languageLabel() {
        ensureLoaded();
        return LANGUAGE_LABELS.get(LANGUAGE_CODES.indexOf(language));
    }

    public static String text(String tr, String en) {
        ensureLoaded();
        if (isTurkish()) return tr;
        if (isSpanish()) return PhoneSpanishTranslations.text(tr, en);
        if (isPortugueseBrazil()) return PhonePortugueseTranslations.text(tr, en);
        if (isGerman()) return PhoneGermanTranslations.text(tr, en);
        if (isFrench()) return PhoneFrenchTranslations.text(tr, en);
        if (isRussian()) return PhoneRussianTranslations.text(tr, en);
        if (isPolish()) return PhonePolishTranslations.text(tr, en);
        if (isItalian()) return PhoneItalianTranslations.text(tr, en);
        if (isChinese()) return PhoneChineseTranslations.text(tr, en, "zh_tw".equals(language));
        return en;
    }

    public static String translation(String key, Object... arguments) {
        return PhoneResourceTranslations.text(language(), key, arguments);
    }

    public static String text(String tr, String en, String es) {
        ensureLoaded();
        if (isTurkish()) return tr;
        if (isSpanish()) return es;
        if (isPortugueseBrazil()) return PhonePortugueseTranslations.text(tr, en);
        if (isGerman()) return PhoneGermanTranslations.text(tr, en);
        if (isFrench()) return PhoneFrenchTranslations.text(tr, en);
        if (isRussian()) return PhoneRussianTranslations.text(tr, en);
        if (isPolish()) return PhonePolishTranslations.text(tr, en);
        if (isItalian()) return PhoneItalianTranslations.text(tr, en);
        if (isChinese()) return PhoneChineseTranslations.text(tr, en, "zh_tw".equals(language));
        return en;
    }


    /** Localizes server/social strings that are still transported as Turkish text. */
    public static String localizeNetworkText(String raw) {
        ensureLoaded();
        if (raw == null || raw.isBlank() || isTurkish()) return raw == null ? "" : raw;
        String updated = PhoneNetworkTranslations.translate(raw);
        if (updated != null) return updated;
        if (isSpanish()) return PhoneSpanishTranslations.network(raw);
        if (isPortugueseBrazil()) return PhonePortugueseTranslations.network(raw);
        if (isGerman()) return PhoneGermanTranslations.network(raw);
        if (isFrench()) return PhoneFrenchTranslations.network(raw);
        if (isRussian()) return PhoneRussianTranslations.network(raw);
        if (isPolish()) return PhonePolishTranslations.network(raw);
        if (isItalian()) return PhoneItalianTranslations.network(raw);

        String english = englishNetworkText(raw);
        return isChinese() ? PhoneChineseTranslations.network(raw, english, "zh_tw".equals(language)) : english;
    }

    private static String englishNetworkText(String raw) {

        String exact = switch (raw) {
            case "Fotoğraf gönderilemedi." -> "Photo could not be sent.";
            case "Bu kişiyle fotoğraf paylaşımı engellendi." -> "Photo sharing with this person is blocked.";
            case "Lumi'de fotoğraf için iki hesabın da birbirini takip etmesi gerekir." -> "Both accounts must follow each other to share photos on Lumi.";
            case "Fotoğraf göndermek için kişi ekli olmalı." -> "Add this person to contacts before sending a photo.";
            case "İçerik gönderilemedi." -> "Content could not be sent.";
            case "Bu kişiyle paylaşım engellendi." -> "Sharing with this person is blocked.";
            case "Göndermek için kişi ekli olmalı." -> "Add this person to contacts before sending.";
            case "Fotoğraf verisi bulunamadı." -> "Photo data was not found.";
            case "Oyuncu veya telefon numarası bulunamadı." -> "Player or phone number not found.";
            case "Bu kişiyle iletişim engellendi." -> "Communication with this person is blocked.";
            case "Bu kişi zaten kişilerinde." -> "This person is already in your contacts.";
            case "Bu kişiye zaten bekleyen bir isteğin var." -> "You already have a pending request to this person.";
            case "İstek gönderildi." -> "Request sent.";
            case "Yeni kişi isteği." -> "New contact request.";
            case "Geçersiz kişi." -> "Invalid contact.";
            case "İstek bulunamadı." -> "Request not found.";
            case "Kişi eklendi." -> "Contact added.";
            case "Kişi isteğin kabul edildi." -> "Your contact request was accepted.";
            case "İstek reddedildi." -> "Request declined.";
            case "İstek iptal edildi." -> "Request cancelled.";
            case "Kişi kaldırıldı." -> "Contact removed.";
            case "Kişi engellendi." -> "Contact blocked.";
            case "Engel kaldırıldı." -> "Block removed.";
            case "Lumi gönderisi paylaşılamadı." -> "Lumi post could not be shared.";
            case "Mesaj gönderilemedi." -> "Message could not be sent.";
            case "Bu kişiyle mesajlaşma engellendi." -> "Messaging with this person is blocked.";
            case "Lumi'de mesajlaşmak için iki hesabın da birbirini takip etmesi gerekir." -> "Both accounts must follow each other to message on Lumi.";
            case "Minder mesajı için aktif eşleşme gerekli." -> "An active Minder match is required to send a message.";
            case "Mesaj göndermek için kişi ekli olmalı." -> "Add this person to contacts before sending a message.";
            case "Yalnız kendi mesajını silebilirsin." -> "You can only delete your own message.";
            case "Aramak için kişi veya Minder eşleşmesi olmalısınız." -> "You must be contacts or a Minder match to call.";
            case "Bu kişiyle arama yapılamıyor." -> "You cannot call this person.";
            case "Sesli arama için Simple Voice Chat gerekli." -> "Simple Voice Chat is required for voice calls.";
            case "Bu oyuncu şu anda çevrimdışı." -> "This player is currently offline.";
            case "Zaten devam eden bir araman var." -> "You already have an active call.";
            case "Bu oyuncu şu anda meşgul." -> "This player is currently busy.";
            case "Kabul edilecek bir arama yok." -> "There is no call to accept.";
            case "Cevapsız arama." -> "Missed call.";
            case "Aramaya 15 saniye içinde cevap verilmedi." -> "The call was not answered within 15 seconds.";
            case "Arayan oyuncu artık çevrimiçi değil." -> "The caller is no longer online.";
            case "Görüşme başladı." -> "Call started.";
            case "Arama reddedildi." -> "Call declined.";
            case "Telefon görüşmesi kesildi." -> "Phone call disconnected.";
            case "Karşı taraf ayrıldığı için görüşme sona erdi." -> "The call ended because the other player left.";
            case "Gönderi görseli çok büyük." -> "The post image is too large.";
            case "Lumi gönderisi için fotoğraf gerekli." -> "A photo is required for a Lumi post.";
            case "Lumi gönderisi paylaşıldı." -> "Lumi post shared.";
            case "Story için fotoğraf gerekli." -> "A photo is required for a Story.";
            case "Story paylaşıldı." -> "Story shared.";
            case "Gönderi boş olamaz." -> "Post cannot be empty.";
            case "X gönderisi paylaşıldı." -> "X post shared.";
            case "Bilinmeyen sosyal medya uygulaması." -> "Unknown social media app.";
            case "Bu oyuncuyla sosyal etkileşim engellendi." -> "Social interaction with this player is blocked.";
            case "Lumi gönderisi silindi." -> "Lumi post deleted.";
            case "Yalnızca kendi Lumi gönderini silebilirsin." -> "You can only delete your own Lumi post.";
            case "X gönderisi silindi." -> "X post deleted.";
            case "Yalnızca kendi X gönderini silebilirsin." -> "You can only delete your own X post.";
            case "Bu oyuncunun sosyal profili bulunamadı." -> "This player's social profile was not found.";
            case "Bu oyuncu Minder'da kullanılamıyor." -> "This player is unavailable on Minder.";
            case "Simple Voice Chat yüklü değil veya sunucuda aktif değil." -> "Simple Voice Chat is not installed or active on the server.";
            case "Sesli arama başlatılamadı." -> "Voice call could not be started.";
            case "Simple Voice Chat bu oyuncuda yüklü değil." -> "Simple Voice Chat is not installed for this player.";
            case "Bu oyuncu Simple Voice Chat sunucusuna bağlı değil." -> "This player is not connected to the Simple Voice Chat server.";
            case "Bu oyuncunun Simple Voice Chat bağlantısı devre dışı." -> "This player's Simple Voice Chat connection is disabled.";
            case "Geçersiz telefon görüşmesi." -> "Invalid phone call.";
            case "Oyunculardan biri zaten telefon görüşmesinde." -> "One of the players is already in a phone call.";
            case "İlk oyuncu zaten telefon görüşmesinde." -> "The first player is already in a phone call.";
            case "İkinci oyuncu zaten telefon görüşmesinde." -> "The second player is already in a phone call.";
            case "BlockGram kullanıcısı" -> "Lumi user";
            case "Minecraft oyuncusu • X kullanıcısı" -> "Minecraft player • X user";
            case "Minecraft oyuncusu\nYap, keşfet ve paylaş." -> "Minecraft player\nCreate, explore and share.";
            default -> raw;
        };
        if (!exact.equals(raw)) return exact;

        if (raw.startsWith("Yeni bir gönderi paylaşmak için ") && raw.endsWith(" saniye bekle.")) {
            String seconds = raw.substring("Yeni bir gönderi paylaşmak için ".length(), raw.length() - " saniye bekle.".length());
            return "Wait " + seconds + " seconds before sharing another post.";
        }
        if (raw.startsWith("Yeni bir eşleşmen var: ")) return "You have a new match: " + raw.substring("Yeni bir eşleşmen var: ".length());
        if (raw.endsWith(" görüntülü aranıyor...")) return "Video calling " + raw.substring(0, raw.length() - " görüntülü aranıyor...".length()) + "...";
        if (raw.endsWith(" aranıyor...")) return "Calling " + raw.substring(0, raw.length() - " aranıyor...".length()) + "...";
        if (raw.endsWith(" seni görüntülü arıyor.")) return raw.substring(0, raw.length() - " seni görüntülü arıyor.".length()) + " is video calling you.";
        if (raw.endsWith(" seni arıyor.")) return raw.substring(0, raw.length() - " seni arıyor.".length()) + " is calling you.";
        if (raw.endsWith(" aramayı reddetti.")) return raw.substring(0, raw.length() - " aramayı reddetti.".length()) + " declined the call.";
        if (raw.endsWith(" görüşmeyi sonlandırdı.")) return raw.substring(0, raw.length() - " görüşmeyi sonlandırdı.".length()) + " ended the call.";
        if (raw.endsWith(" takip isteği gönderdi")) return raw.substring(0, raw.length() - " takip isteği gönderdi".length()) + " sent you a follow request";
        if (raw.endsWith(" takip isteğini kabul etti")) return raw.substring(0, raw.length() - " takip isteğini kabul etti".length()) + " accepted your follow request";
        if (raw.endsWith(" gönderini yeniden paylaştı")) return raw.substring(0, raw.length() - " gönderini yeniden paylaştı".length()) + " reposted your post";
        if (raw.endsWith(" gönderini paylaştı")) return raw.substring(0, raw.length() - " gönderini paylaştı".length()) + " shared your post";
        if (raw.endsWith(" gönderini beğendi")) return raw.substring(0, raw.length() - " gönderini beğendi".length()) + " liked your post";
        if (raw.endsWith(" gönderine yorum yaptı")) return raw.substring(0, raw.length() - " gönderine yorum yaptı".length()) + " commented on your post";
        if (raw.endsWith(" gönderine yanıt verdi")) return raw.substring(0, raw.length() - " gönderine yanıt verdi".length()) + " replied to your post";
        if (raw.endsWith(" yorumuna yanıt verdi")) return raw.substring(0, raw.length() - " yorumuna yanıt verdi".length()) + " replied to your comment";
        if (raw.endsWith(" yorumunu beğendi")) return raw.substring(0, raw.length() - " yorumunu beğendi".length()) + " liked your comment";
        if (raw.endsWith(" yanıtını beğendi")) return raw.substring(0, raw.length() - " yanıtını beğendi".length()) + " liked your reply";
        return raw;
    }

    public static boolean useMinecraftTime() { ensureLoaded(); return useMinecraftTime; }

    public static void setUseMinecraftTime(boolean value) {
        ensureLoaded(); useMinecraftTime = value; save();
    }

    public static String timeSourceLabel() {
        return useMinecraftTime() ? text("Minecraft saati", "Minecraft time") : text("Gerçek saat", "Real time");
    }

    public static boolean use24HourClock() {
        ensureLoaded();
        return use24HourClock;
    }

    public static void setUse24HourClock(boolean value) {
        ensureLoaded();
        use24HourClock = value;
        save();
    }

    public static boolean showPhoneNumber() {
        ensureLoaded();
        return showPhoneNumber;
    }

    public static void setShowPhoneNumber(boolean value) {
        ensureLoaded();
        showPhoneNumber = value;
        save();
    }

    public static boolean animationsEnabled() {
        ensureLoaded();
        return animationsEnabled;
    }

    public static void setAnimationsEnabled(boolean value) {
        ensureLoaded();
        animationsEnabled = value;
        save();
    }

    public static boolean incomingCallAnimationEnabled() {
        ensureLoaded();
        return incomingCallAnimationEnabled;
    }

    public static void setIncomingCallAnimationEnabled(boolean value) {
        ensureLoaded();
        incomingCallAnimationEnabled = value;
        save();
    }

    public static int wallpaperIndex() {
        ensureLoaded();
        return wallpaperIndex;
    }

    public static int wallpaperIndex(boolean lockScreen) {
        ensureLoaded();
        return lockScreen ? lockWallpaperIndex : wallpaperIndex;
    }

    public static int wallpaperCount() {
        return WALLPAPER_NAMES_TR.length;
    }

    public static void setWallpaperIndex(int value) {
        setBuiltInWallpaper(value, WallpaperTarget.BOTH);
    }

    public static void cycleWallpaper() {
        ensureLoaded();
        setBuiltInWallpaper((wallpaperIndex + 1) % WALLPAPER_NAMES_TR.length, WallpaperTarget.BOTH);
    }

    public static void setBuiltInWallpaper(int value, WallpaperTarget target) {
        ensureLoaded();
        int safeIndex = clamp(value, 0, WALLPAPER_NAMES_TR.length - 1);
        WallpaperTarget safeTarget = target == null ? WallpaperTarget.BOTH : target;
        if (safeTarget == WallpaperTarget.HOME || safeTarget == WallpaperTarget.BOTH) {
            wallpaperIndex = safeIndex;
            homeWallpaperPhotoPath = "";
        }
        if (safeTarget == WallpaperTarget.LOCK || safeTarget == WallpaperTarget.BOTH) {
            lockWallpaperIndex = safeIndex;
            lockWallpaperPhotoPath = "";
        }
        save();
    }

    public static boolean setPhotoWallpaper(Path photo, WallpaperTarget target) {
        ensureLoaded();
        if (photo == null) return false;
        Path normalized;
        try {
            normalized = photo.toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return false;
        }
        if (!Files.isRegularFile(normalized)) return false;

        String storedPath = normalized.toString();
        WallpaperTarget safeTarget = target == null ? WallpaperTarget.BOTH : target;
        if (safeTarget == WallpaperTarget.HOME || safeTarget == WallpaperTarget.BOTH) {
            homeWallpaperPhotoPath = storedPath;
        }
        if (safeTarget == WallpaperTarget.LOCK || safeTarget == WallpaperTarget.BOTH) {
            lockWallpaperPhotoPath = storedPath;
        }
        return save();
    }

    public static Path wallpaperPhoto(boolean lockScreen) {
        ensureLoaded();
        String value = lockScreen ? lockWallpaperPhotoPath : homeWallpaperPhotoPath;
        if (value == null || value.isBlank()) return null;
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isBuiltInWallpaperSelected(int index, WallpaperTarget target) {
        ensureLoaded();
        int safeIndex = clamp(index, 0, WALLPAPER_NAMES_TR.length - 1);
        WallpaperTarget safeTarget = target == null ? WallpaperTarget.BOTH : target;
        boolean homeMatches = wallpaperPhoto(false) == null && wallpaperIndex == safeIndex;
        boolean lockMatches = wallpaperPhoto(true) == null && lockWallpaperIndex == safeIndex;
        return switch (safeTarget) {
            case HOME -> homeMatches;
            case LOCK -> lockMatches;
            case BOTH -> homeMatches && lockMatches;
        };
    }

    public static boolean isPhotoWallpaperSelected(Path photo, WallpaperTarget target) {
        ensureLoaded();
        if (photo == null) return false;
        Path normalized;
        try {
            normalized = photo.toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return false;
        }
        Path homePhoto = wallpaperPhoto(false);
        Path lockPhoto = wallpaperPhoto(true);
        boolean homeMatches = normalized.equals(homePhoto);
        boolean lockMatches = normalized.equals(lockPhoto);
        WallpaperTarget safeTarget = target == null ? WallpaperTarget.BOTH : target;
        return switch (safeTarget) {
            case HOME -> homeMatches;
            case LOCK -> lockMatches;
            case BOTH -> homeMatches && lockMatches;
        };
    }

    public static String wallpaperName() {
        ensureLoaded();
        Path homePhoto = wallpaperPhoto(false);
        Path lockPhoto = wallpaperPhoto(true);
        if (homePhoto != null || lockPhoto != null) {
            if (homePhoto != null && homePhoto.equals(lockPhoto)) return text("Fotoğrafım", "My Photo");
            return text("Ayrı", "Separate");
        }
        if (wallpaperIndex != lockWallpaperIndex) return text("Ayrı", "Separate");
        return wallpaperName(wallpaperIndex);
    }

    public static String wallpaperName(int index) {
        ensureLoaded();
        int safeIndex = clamp(index, 0, WALLPAPER_NAMES_TR.length - 1);
        if (isChinese()) return text("", WALLPAPER_NAMES_EN[safeIndex]);
        return isTurkish() ? WALLPAPER_NAMES_TR[safeIndex]
                : isSpanish() ? WALLPAPER_NAMES_ES[safeIndex]
                : isPortugueseBrazil() ? WALLPAPER_NAMES_PT_BR[safeIndex]
                : isGerman() ? WALLPAPER_NAMES_DE[safeIndex]
                : isFrench() ? WALLPAPER_NAMES_FR[safeIndex]
                : isRussian() ? WALLPAPER_NAMES_RU[safeIndex]
                : isPolish() ? WALLPAPER_NAMES_PL[safeIndex]
                : isItalian() ? WALLPAPER_NAMES_IT[safeIndex]
                : WALLPAPER_NAMES_EN[safeIndex];
    }

    public static boolean pinlessUnlockMode() {
        ensureLoaded();
        return pinlessUnlockMode;
    }

    public static void setPinlessUnlockMode(boolean value) {
        ensureLoaded();
        pinlessUnlockMode = value;
        save();
    }

    public static boolean airplaneMode() {
        ensureLoaded();
        return airplaneMode;
    }

    public static void setAirplaneMode(boolean value) {
        ensureLoaded();
        airplaneMode = value;
        if (value) {
            wifiEnabled = false;
            bluetoothEnabled = false;
        }
        save();
    }

    public static boolean wifiEnabled() {
        ensureLoaded();
        return wifiEnabled;
    }

    public static void setWifiEnabled(boolean value) {
        ensureLoaded();
        wifiEnabled = value;
        if (value) {
            airplaneMode = false;
        }
        save();
    }

    public static boolean bluetoothEnabled() {
        ensureLoaded();
        return bluetoothEnabled;
    }

    public static void setBluetoothEnabled(boolean value) {
        ensureLoaded();
        bluetoothEnabled = value;
        if (value) {
            airplaneMode = false;
        }
        save();
    }

    public static boolean doNotDisturb() {
        ensureLoaded();
        return doNotDisturb;
    }

    public static void setDoNotDisturb(boolean value) {
        ensureLoaded();
        doNotDisturb = value;
        save();
    }

    public static void resetDefaults() {
        ensureLoaded();
        boolean keepSetupCompleted = setupCompleted;
        int keepSetupStep = setupStep;
        brightness = 0.86F;
        accentIndex = 0;
        phoneScaleIndex = 1;
        phoneVolume = 0.78F;
        messageNotifications = true;
        vibration = true;
        lockScreenEnabled = true;
        autoLockIndex = 2;
        language = "tr";
        use24HourClock = true;
        useMinecraftTime = false;
        showPhoneNumber = true;
        animationsEnabled = true;
        incomingCallAnimationEnabled = true;
        wallpaperIndex = 0;
        lockWallpaperIndex = 0;
        homeWallpaperPhotoPath = "";
        lockWallpaperPhotoPath = "";
        pinlessUnlockMode = false;
        airplaneMode = false;
        wifiEnabled = true;
        bluetoothEnabled = true;
        doNotDisturb = false;
        homeLayoutMode = HomeLayoutMode.CLASSIC;
        setupCompleted = keepSetupCompleted;
        setupStep = keepSetupStep;
        setupFlowVersion = SETUP_FLOW_VERSION;
        loaded = true;
        save();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(SETTINGS_FILE)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(SETTINGS_FILE)) {
            properties.load(inputStream);
            brightness = clamp(parseFloat(properties, "brightness", brightness), 0.25F, 1.0F);
            accentIndex = clamp(parseInt(properties, "accentIndex", accentIndex), 0, ACCENT_COLORS.length - 1);
            phoneScaleIndex = clamp(parseInt(properties, "phoneScaleIndex", phoneScaleIndex), 0, PHONE_SCALES.length - 1);
            phoneVolume = clamp(parseFloat(properties, "phoneVolume", phoneVolume), 0.0F, 1.0F);
            messageNotifications = parseBoolean(properties, "messageNotifications", messageNotifications);
            vibration = parseBoolean(properties, "vibration", vibration);
            lockScreenEnabled = parseBoolean(properties, "lockScreenEnabled", lockScreenEnabled);
            autoLockIndex = clamp(parseInt(properties, "autoLockIndex", autoLockIndex), 0, AUTO_LOCK_MINUTES.length - 1);
            String storedLanguage = properties.getProperty("language", "tr");
            language = normalizeLanguage(storedLanguage);
            use24HourClock = parseBoolean(properties, "use24HourClock", use24HourClock);
            useMinecraftTime = parseBoolean(properties, "useMinecraftTime", false);
            showPhoneNumber = parseBoolean(properties, "showPhoneNumber", showPhoneNumber);
            animationsEnabled = parseBoolean(properties, "animationsEnabled", animationsEnabled);
            incomingCallAnimationEnabled = parseBoolean(properties, "incomingCallAnimationEnabled", incomingCallAnimationEnabled);
            int legacyWallpaperIndex = clamp(
                    parseInt(properties, "wallpaperIndex", wallpaperIndex),
                    0,
                    WALLPAPER_NAMES_TR.length - 1
            );
            wallpaperIndex = clamp(
                    parseInt(properties, "homeWallpaperIndex", legacyWallpaperIndex),
                    0,
                    WALLPAPER_NAMES_TR.length - 1
            );
            lockWallpaperIndex = clamp(
                    parseInt(properties, "lockWallpaperIndex", legacyWallpaperIndex),
                    0,
                    WALLPAPER_NAMES_TR.length - 1
            );
            homeWallpaperPhotoPath = properties.getProperty("homeWallpaperPhotoPath", "").trim();
            lockWallpaperPhotoPath = properties.getProperty("lockWallpaperPhotoPath", "").trim();
            if (!isUsableWallpaperPhoto(homeWallpaperPhotoPath)) homeWallpaperPhotoPath = "";
            if (!isUsableWallpaperPhoto(lockWallpaperPhotoPath)) lockWallpaperPhotoPath = "";
            pinlessUnlockMode = parseBoolean(properties, "pinlessUnlockMode", pinlessUnlockMode);
            airplaneMode = parseBoolean(properties, "airplaneMode", airplaneMode);
            wifiEnabled = parseBoolean(properties, "wifiEnabled", wifiEnabled);
            bluetoothEnabled = parseBoolean(properties, "bluetoothEnabled", bluetoothEnabled);
            doNotDisturb = parseBoolean(properties, "doNotDisturb", doNotDisturb);
            setupCompleted = parseBoolean(properties, "setupCompleted", setupCompleted);
            setupStep = clamp(parseInt(properties, "setupStep", setupStep), 0, 4);
            int storedSetupVersion = parseInt(properties, "setupFlowVersion", 0);
            if (storedSetupVersion < SETUP_FLOW_VERSION && !setupCompleted) setupStep = 0;
            setupFlowVersion = SETUP_FLOW_VERSION;
            try {
                homeLayoutMode = HomeLayoutMode.valueOf(
                        properties.getProperty("homeLayoutMode", homeLayoutMode.name())
                );
            } catch (IllegalArgumentException ignored) {
                homeLayoutMode = HomeLayoutMode.CLASSIC;
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean save() {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            Properties properties = new Properties();
            properties.setProperty("brightness", Float.toString(brightness));
            properties.setProperty("accentIndex", Integer.toString(accentIndex));
            properties.setProperty("phoneScaleIndex", Integer.toString(phoneScaleIndex));
            properties.setProperty("phoneVolume", Float.toString(phoneVolume));
            properties.setProperty("messageNotifications", Boolean.toString(messageNotifications));
            properties.setProperty("vibration", Boolean.toString(vibration));
            properties.setProperty("lockScreenEnabled", Boolean.toString(lockScreenEnabled));
            properties.setProperty("autoLockIndex", Integer.toString(autoLockIndex));
            properties.setProperty("language", language);
            properties.setProperty("use24HourClock", Boolean.toString(use24HourClock));
            properties.setProperty("useMinecraftTime", Boolean.toString(useMinecraftTime));
            properties.setProperty("showPhoneNumber", Boolean.toString(showPhoneNumber));
            properties.setProperty("animationsEnabled", Boolean.toString(animationsEnabled));
            properties.setProperty("incomingCallAnimationEnabled", Boolean.toString(incomingCallAnimationEnabled));
            properties.setProperty("wallpaperIndex", Integer.toString(wallpaperIndex));
            properties.setProperty("homeWallpaperIndex", Integer.toString(wallpaperIndex));
            properties.setProperty("lockWallpaperIndex", Integer.toString(lockWallpaperIndex));
            properties.setProperty("homeWallpaperPhotoPath", homeWallpaperPhotoPath);
            properties.setProperty("lockWallpaperPhotoPath", lockWallpaperPhotoPath);
            properties.setProperty("pinlessUnlockMode", Boolean.toString(pinlessUnlockMode));
            properties.setProperty("airplaneMode", Boolean.toString(airplaneMode));
            properties.setProperty("wifiEnabled", Boolean.toString(wifiEnabled));
            properties.setProperty("bluetoothEnabled", Boolean.toString(bluetoothEnabled));
            properties.setProperty("doNotDisturb", Boolean.toString(doNotDisturb));
            properties.setProperty("setupCompleted", Boolean.toString(setupCompleted));
            properties.setProperty("setupStep", Integer.toString(setupStep));
            properties.setProperty("setupFlowVersion", Integer.toString(setupFlowVersion));
            properties.setProperty("homeLayoutMode", homeLayoutMode.name());
            try (OutputStream outputStream = Files.newOutputStream(SETTINGS_FILE)) {
                properties.store(outputStream, "Z-Phone settings");
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean parseBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int parseInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(Properties properties, String key, float fallback) {
        try {
            return Float.parseFloat(properties.getProperty(key, Float.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean isUsableWallpaperPhoto(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            return Files.isRegularFile(Path.of(value).toAbsolutePath().normalize());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

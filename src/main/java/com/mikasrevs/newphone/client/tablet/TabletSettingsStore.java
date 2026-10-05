package com.mikasrevs.newphone.client.tablet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;

/** Client-only settings dedicated to Police/EMS tablets. It does not modify civilian phone settings. */
public final class TabletSettingsStore {
    public enum Language {
        GAME,
        ENGLISH,
        TURKISH;

        public Language next() {
            Language[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("newphone-tablet.properties");
    private static boolean loaded;
    private static Language language = Language.GAME;
    private static boolean incomingPopup = true;
    private static boolean autoOpenIncoming = true;
    private static boolean autoFocusAccepted = true;
    private static boolean navigationHud = true;

    private TabletSettingsStore() {
    }

    public static Language language() {
        ensureLoaded();
        return language;
    }

    public static boolean incomingPopup() {
        ensureLoaded();
        return incomingPopup;
    }

    public static boolean autoOpenIncoming() {
        ensureLoaded();
        return autoOpenIncoming;
    }

    public static boolean autoFocusAccepted() {
        ensureLoaded();
        return autoFocusAccepted;
    }

    public static boolean navigationHud() {
        ensureLoaded();
        return navigationHud;
    }

    public static void cycleLanguage() {
        ensureLoaded();
        language = language.next();
        save();
    }

    public static void toggleIncomingPopup() {
        ensureLoaded();
        incomingPopup = !incomingPopup;
        save();
    }

    public static void toggleAutoOpenIncoming() {
        ensureLoaded();
        autoOpenIncoming = !autoOpenIncoming;
        save();
    }

    public static void toggleAutoFocusAccepted() {
        ensureLoaded();
        autoFocusAccepted = !autoFocusAccepted;
        save();
    }

    public static void toggleNavigationHud() {
        ensureLoaded();
        navigationHud = !navigationHud;
        save();
    }

    public static void reset() {
        language = Language.GAME;
        incomingPopup = true;
        autoOpenIncoming = true;
        autoFocusAccepted = true;
        navigationHud = true;
        loaded = true;
        save();
    }

    public static String languageLabel() {
        return switch (language()) {
            case GAME -> text("Oyun dili", "Game language");
            case ENGLISH -> "English";
            case TURKISH -> "Türkçe";
        };
    }

    public static String text(String turkish, String english) {
        ensureLoaded();
        boolean turkishActive = switch (language) {
            case TURKISH -> true;
            case ENGLISH -> false;
            case GAME -> currentGameLanguageIsTurkish();
        };
        return turkishActive ? turkish : english;
    }

    private static boolean currentGameLanguageIsTurkish() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getLanguageManager() == null) return false;
        String selected = minecraft.getLanguageManager().getSelected();
        return selected != null && selected.toLowerCase(Locale.ROOT).startsWith("tr_");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Properties properties = new Properties();
        if (Files.isRegularFile(FILE)) {
            try (InputStream stream = Files.newInputStream(FILE)) {
                properties.load(stream);
            } catch (IOException ignored) {
            }
        }
        try {
            language = Language.valueOf(properties.getProperty("language", "GAME").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            language = Language.GAME;
        }
        incomingPopup = bool(properties, "incomingPopup", true);
        autoOpenIncoming = bool(properties, "autoOpenIncoming", true);
        autoFocusAccepted = bool(properties, "autoFocusAccepted", true);
        navigationHud = bool(properties, "navigationHud", true);
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("language", language.name());
        properties.setProperty("incomingPopup", Boolean.toString(incomingPopup));
        properties.setProperty("autoOpenIncoming", Boolean.toString(autoOpenIncoming));
        properties.setProperty("autoFocusAccepted", Boolean.toString(autoFocusAccepted));
        properties.setProperty("navigationHud", Boolean.toString(navigationHud));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream stream = Files.newOutputStream(FILE)) {
                properties.store(stream, "MPhone responder tablet settings");
            }
        } catch (IOException ignored) {
        }
    }
}

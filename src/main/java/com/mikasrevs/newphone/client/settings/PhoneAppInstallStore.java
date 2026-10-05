package com.mikasrevs.newphone.client.settings;

import com.mikasrevs.newphone.client.gui.PhoneApp;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** Persistent per-client install order for MineStore applications. */
public final class PhoneAppInstallStore {
    private static final Path FILE = Path.of(
            System.getProperty("user.dir"),
            "config",
            "newphone",
            "installed_apps.properties"
    );

    private static final List<PhoneApp> AVAILABLE_GAMES = List.of(
            PhoneApp.BLOCK_SNAKE,
            PhoneApp.CREEPER_RUSH,
            PhoneApp.GEM_PAIRS
    );
    private static final List<PhoneApp> installed = new ArrayList<>();
    private static boolean loaded;

    private PhoneAppInstallStore() {
    }

    public static List<PhoneApp> availableGames() {
        return AVAILABLE_GAMES;
    }

    public static List<PhoneApp> installedGames() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(installed));
    }

    public static boolean isInstalled(PhoneApp app) {
        ensureLoaded();
        return installed.contains(app);
    }

    public static boolean install(PhoneApp app) {
        ensureLoaded();
        if (!AVAILABLE_GAMES.contains(app) || installed.contains(app)) return false;
        installed.add(app);
        save();
        return true;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(FILE)) return;

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            String raw = properties.getProperty("installed", "");
            for (String token : raw.split(",")) {
                if (token.isBlank()) continue;
                try {
                    PhoneApp app = PhoneApp.valueOf(token.trim());
                    if (AVAILABLE_GAMES.contains(app) && !installed.contains(app)) installed.add(app);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties properties = new Properties();
            properties.setProperty("installed", installed.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse(""));
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "MineStore installed apps");
            }
        } catch (IOException ignored) {
        }
    }
}

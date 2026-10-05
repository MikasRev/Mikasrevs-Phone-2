package com.mikasrevs.newphone.client.settings;

import com.mikasrevs.newphone.client.gui.PhoneApp;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Persistent, user-editable MPhone Home layout. */
public final class PhoneAppLayoutStore {
    private static final int MIN_HOME_SLOTS = 15;
    private static final String EMPTY_SLOT = "_";
    private static final Path FILE = Path.of(
            System.getProperty("user.dir"),
            "config",
            "newphone",
            "home_layout.properties"
    );

    private static final List<PhoneApp> DEFAULT_HOME = List.of(
            PhoneApp.GPS,
            PhoneApp.GALLERY,
            PhoneApp.SETTINGS,
            PhoneApp.CAMERA,
            PhoneApp.BLOCKPOST,
            PhoneApp.BLOCKGRAM,
            PhoneApp.NOTES,
            PhoneApp.CALCULATOR,
            PhoneApp.MUSIC,
            PhoneApp.YOUTUBE,
            PhoneApp.CALENDAR,
            PhoneApp.CLOCK,
            PhoneApp.BROWSER,
            PhoneApp.PHONE,
            PhoneApp.EMERGENCY,
            PhoneApp.BANK,
            PhoneApp.MARKETPLACE,
            PhoneApp.MINESTORE
    );
    private static final List<PhoneApp> DEFAULT_DOCK = List.of(PhoneApp.MESSAGES, PhoneApp.CONTACTS);
    private static final List<PhoneApp> homeSlots = new ArrayList<>();
    private static final Set<PhoneApp> removedApps = new LinkedHashSet<>();
    private static boolean loaded;

    private PhoneAppLayoutStore() {
    }

    public static synchronized List<PhoneApp> homeSlots() {
        ensureLoaded();
        appendNewlyInstalledApps();
        ensureMinimumSlots();
        List<PhoneApp> visible = new ArrayList<>(homeSlots);
        visible.replaceAll(app -> app == PhoneApp.ADMIN_PANEL ? null : app);
        if (com.mikasrevs.newphone.client.moderation.PhoneModerationClient.canManage()) visible.add(PhoneApp.ADMIN_PANEL);
        return Collections.unmodifiableList(visible);
    }

    public static synchronized List<PhoneApp> visibleHomeApps() {
        ensureLoaded();
        appendNewlyInstalledApps();
        List<PhoneApp> visible = new ArrayList<>();
        for (PhoneApp app : homeSlots()) if (app != null) visible.add(app);
        return Collections.unmodifiableList(visible);
    }

    public static List<PhoneApp> dockApps() {
        return DEFAULT_DOCK;
    }

    public static synchronized PhoneApp homeAppAt(int slot) {
        ensureLoaded();
        List<PhoneApp> visible = homeSlots();
        if (slot < 0 || slot >= visible.size()) return null;
        return visible.get(slot);
    }

    /** Removes only the Home icon. The empty slot is intentionally preserved. */
    public static synchronized boolean removeHomeAppAt(int slot) {
        ensureLoaded();
        if (slot < 0 || slot >= homeSlots.size() || homeSlots.get(slot) == null) return false;
        PhoneApp removed = homeSlots.get(slot);
        homeSlots.set(slot, null);
        removedApps.add(removed);
        save();
        return true;
    }

    /** Moves an app to an empty slot, or swaps two occupied slots. */
    public static synchronized boolean moveHomeApp(int fromSlot, int toSlot) {
        ensureLoaded();
        if (fromSlot < 0 || fromSlot >= homeSlots.size() || toSlot < 0) return false;
        while (homeSlots.size() <= toSlot) homeSlots.add(null);
        if (fromSlot == toSlot || homeSlots.get(fromSlot) == null) return false;
        PhoneApp from = homeSlots.get(fromSlot);
        PhoneApp to = homeSlots.get(toSlot);
        homeSlots.set(toSlot, from);
        homeSlots.set(fromSlot, to);
        ensureMinimumSlots();
        save();
        return true;
    }

    /** Apps that are available on Home but currently removed from every slot. */
    public static synchronized List<PhoneApp> removedHomeApps() {
        ensureLoaded();
        appendNewlyInstalledApps();
        List<PhoneApp> removed = availableHomeApps();
        removed.removeIf(homeSlots::contains);
        return Collections.unmodifiableList(removed);
    }

    /** Restores one removed app into the exact empty slot selected by the player. */
    public static synchronized boolean addHomeAppAt(int slot, PhoneApp app) {
        ensureLoaded();
        if (slot < 0 || app == null || homeSlots.contains(app) || !availableHomeApps().contains(app)) return false;
        while (homeSlots.size() <= slot) homeSlots.add(null);
        if (homeSlots.get(slot) != null) return false;
        homeSlots.set(slot, app);
        removedApps.remove(app);
        ensureMinimumSlots();
        save();
        return true;
    }

    public static synchronized void resetHomeLayout() {
        loaded = true;
        homeSlots.clear();
        removedApps.clear();
        homeSlots.addAll(DEFAULT_HOME);
        for (PhoneApp game : PhoneAppInstallStore.installedGames()) {
            if (!homeSlots.contains(game)) homeSlots.add(game);
        }
        ensureMinimumSlots();
        save();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(FILE)) {
            homeSlots.addAll(DEFAULT_HOME);
            for (PhoneApp game : PhoneAppInstallStore.installedGames()) {
                if (!homeSlots.contains(game)) homeSlots.add(game);
            }
            ensureMinimumSlots();
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            String removedRaw = properties.getProperty("removed", "");
            for (String token : removedRaw.split(",")) {
                if (token.isBlank()) continue;
                try { removedApps.add(PhoneApp.valueOf(token.trim())); }
                catch (IllegalArgumentException ignored) { }
            }
            String raw = properties.getProperty("slots", "");
            if (!raw.isBlank()) {
                for (String token : raw.split("\\|", -1)) {
                    String value = token.trim();
                    if (value.isBlank() || EMPTY_SLOT.equals(value)) {
                        homeSlots.add(null);
                        continue;
                    }
                    try {
                        PhoneApp app = PhoneApp.valueOf(value);
                        homeSlots.add(homeSlots.contains(app) ? null : app);
                    } catch (IllegalArgumentException ignored) {
                        homeSlots.add(null);
                    }
                }
            }
        } catch (IOException ignored) {
        }

        if (homeSlots.isEmpty()) homeSlots.addAll(DEFAULT_HOME);
        appendNewlyInstalledApps();
        ensureMinimumSlots();
    }

    private static List<PhoneApp> availableHomeApps() {
        List<PhoneApp> available = new ArrayList<>(DEFAULT_HOME);
        for (PhoneApp game : PhoneAppInstallStore.installedGames()) {
            if (!available.contains(game)) available.add(game);
        }
        return available;
    }

    /** New MineStore installs are appended; they never auto-fill a hole the player intentionally left. */
    private static void appendNewlyInstalledApps() {
        boolean changed = false;
        for (PhoneApp app : DEFAULT_HOME) {
            if (!homeSlots.contains(app) && !removedApps.contains(app)) {
                homeSlots.add(app);
                changed = true;
            }
        }
        for (PhoneApp game : PhoneAppInstallStore.installedGames()) {
            if (!homeSlots.contains(game) && !removedApps.contains(game)) {
                homeSlots.add(game);
                changed = true;
            }
        }
        if (changed) save();
    }

    private static void ensureMinimumSlots() {
        while (homeSlots.size() < MIN_HOME_SLOTS) homeSlots.add(null);
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties properties = new Properties();
            List<String> encoded = new ArrayList<>();
            for (PhoneApp app : homeSlots) encoded.add(app == null ? EMPTY_SLOT : app.name());
            properties.setProperty("slots", String.join("|", encoded));
            properties.setProperty("removed", removedApps.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse(""));
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "MPhone Home layout. '_' means an intentionally empty slot.");
            }
        } catch (IOException ignored) {
        }
    }
}

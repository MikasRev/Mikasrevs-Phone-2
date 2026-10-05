package com.mikasrevs.newphone.client.zphone;

import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;

/** Lightweight MPhone screen transition state shared by the 1.20.1 UI. */
public final class ZPhoneTransitionState {
    public enum Kind { NONE, APP_OPEN, HOME_RETURN }

    private static final long APP_OPEN_DURATION_MS = 760L;
    private static final long HOME_RETURN_DURATION_MS = 240L;
    private static Kind kind = Kind.NONE;
    private static long startedAt;
    private static PhoneApp launchApp;

    private ZPhoneTransitionState() {}

    public static void beginAppOpen() { beginAppOpen(null); }
    public static void beginAppOpen(PhoneApp app) { launchApp = app; begin(Kind.APP_OPEN); }
    public static void beginHomeReturn() { launchApp = null; begin(Kind.HOME_RETURN); }
    public static void clear() { kind = Kind.NONE; startedAt = 0L; launchApp = null; }

    private static void begin(Kind next) {
        if (!PhoneSettingsStore.animationsEnabled()) {
            clear();
            return;
        }
        kind = next;
        startedAt = System.currentTimeMillis();
    }

    public static Kind kind() {
        update();
        return kind;
    }

    public static float progress() {
        update();
        if (kind == Kind.NONE || startedAt <= 0L) return 1.0F;
        float raw = Math.max(0.0F, Math.min(1.0F,
                (System.currentTimeMillis() - startedAt) / (float) durationMs()));
        float inverse = 1.0F - raw;
        return 1.0F - inverse * inverse * inverse * inverse;
    }

    public static PhoneApp launchApp() {
        update();
        return launchApp;
    }

    private static void update() {
        if (kind != Kind.NONE && startedAt > 0L
                && System.currentTimeMillis() - startedAt >= durationMs()) clear();
    }

    private static long durationMs() {
        return kind == Kind.APP_OPEN ? APP_OPEN_DURATION_MS : HOME_RETURN_DURATION_MS;
    }
}

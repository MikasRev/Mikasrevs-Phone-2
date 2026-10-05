package com.mikasrevs.newphone.client.zphone;

import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneCallScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneControlCenterScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneHomeScreen;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Preserves concrete MPhone screens so Back returns to the exact previous view. */
public final class PhoneNavigationState {
    private static final int MAX_HISTORY = 48;
    private static final Deque<Screen> HISTORY = new ArrayDeque<>();
    private static Screen current;
    private static boolean restoring;

    private PhoneNavigationState() { }

    public static void onShown(Screen screen) {
        if (!isPhoneScreen(screen)) return;
        if (restoring) {
            restoring = false;
            current = screen;
            return;
        }
        if (current == screen) return;
        if (removeThroughExisting(screen)) {
            current = screen;
            return;
        }
        if (current != null && isPhoneScreen(current)) {
            Screen snapshot = navigationSnapshot(current);
            if (snapshot != null && HISTORY.peekLast() != snapshot) HISTORY.addLast(snapshot);
            while (HISTORY.size() > MAX_HISTORY) HISTORY.removeFirst();
        }
        current = screen;
    }

    /** Compatibility entry used by the Forge screens; init() records the navigation. */
    public static void open(Minecraft minecraft, Screen from, Screen next) {
        if (minecraft == null || next == null) return;
        if (from != null) onShown(from);
        minecraft.setScreen(next);
    }

    /** App launch animation belongs to Home, so this overload intentionally adds no overlay. */
    public static void open(Minecraft minecraft, Screen from, Screen next, PhoneApp app) {
        open(minecraft, from, next);
    }

    public static void openControlCenter(Minecraft minecraft, Screen from) {
        openControlCenter(minecraft, from, 0.0F);
    }

    public static void openControlCenter(Minecraft minecraft, Screen from, float initialProgress) {
        if (minecraft == null) return;
        if (from != null) onShown(from);
        minecraft.setScreen(new ZPhoneControlCenterScreen(from, initialProgress));
    }

    public static boolean backOrHome(Minecraft minecraft) {
        if (minecraft == null) return false;
        if (minecraft.screen instanceof ZPhoneCallScreen) {
            Screen interrupted = navigationSnapshot(current);
            boolean canRestore = ZPhoneClient.canRestorePhoneContent();
            if (interrupted != null && isPhoneScreen(interrupted) && canRestore) {
                showWithoutHistory(minecraft, interrupted);
                return true;
            }
            if (!canRestore) {
                HISTORY.clear();
                current = null;
                showWithoutHistory(minecraft, rootScreen());
                return true;
            }
        }
        Screen target = null;
        while (!HISTORY.isEmpty() && target == null) {
            Screen candidate = navigationSnapshot(HISTORY.removeLast());
            if (candidate != null && candidate != current && isPhoneScreen(candidate)) target = candidate;
        }
        if (target == null) {
            HISTORY.clear();
            target = rootScreen();
        }
        showWithoutHistory(minecraft, target);
        return true;
    }

    public static boolean returnTo(Minecraft minecraft, Screen target) {
        if (minecraft == null) return false;
        if (target == null || !isPhoneScreen(target)) return backOrHome(minecraft);
        removeThroughExisting(target);
        showWithoutHistory(minecraft, navigationSnapshot(target));
        return true;
    }

    public static boolean replaceCurrent(Minecraft minecraft, Screen replacement) {
        if (minecraft == null || replacement == null) return false;
        showWithoutHistory(minecraft, navigationSnapshot(replacement));
        return true;
    }

    /** Camera is temporary and leaves current pointing at the app that launched it. */
    public static boolean restoreCurrent(Minecraft minecraft) {
        if (minecraft == null) return false;
        Screen target = navigationSnapshot(current);
        if (target == null || !isPhoneScreen(target)) return backOrHome(minecraft);
        showWithoutHistory(minecraft, target);
        return true;
    }

    public static void openHome(Minecraft minecraft) {
        if (minecraft == null) return;
        HISTORY.clear();
        showWithoutHistory(minecraft, rootScreen());
    }

    public static void goHome(Minecraft minecraft) {
        openHome(minecraft);
    }

    public static void clear() {
        HISTORY.clear();
        current = null;
        restoring = false;
    }

    private static void showWithoutHistory(Minecraft minecraft, Screen target) {
        if (target == null) target = rootScreen();
        restoring = true;
        current = target;
        minecraft.setScreen(target);
    }

    private static Screen rootScreen() {
        return ZPhoneClient.createRootScreen();
    }

    private static boolean removeThroughExisting(Screen target) {
        if (target == null || HISTORY.isEmpty()) return false;
        boolean found = false;
        for (Screen screen : HISTORY) {
            if (screen == target) {
                found = true;
                break;
            }
        }
        if (!found) return false;
        while (!HISTORY.isEmpty()) {
            Screen removed = HISTORY.removeLast();
            if (removed == target) break;
        }
        return true;
    }

    private static Screen navigationSnapshot(Screen screen) {
        if(screen instanceof com.mikasrevs.newphone.client.duo.DuoHomeScreen home)return new com.mikasrevs.newphone.client.duo.DuoHomeScreen(home.currentPage());
        if (screen instanceof ZPhoneHomeScreen home) return new ZPhoneHomeScreen(home.currentPage());
        return screen;
    }

    private static boolean isPhoneScreen(Screen screen) {
        return screen instanceof AbstractZPhoneScreen
                && !(screen instanceof com.mikasrevs.newphone.client.duo.DuoTransitionScreen)
                && !(screen instanceof ZPhoneControlCenterScreen)
                && !(screen instanceof ZPhoneCallScreen);
    }
}

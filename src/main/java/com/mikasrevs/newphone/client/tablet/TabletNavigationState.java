package com.mikasrevs.newphone.client.tablet;

import com.mikasrevs.newphone.client.gui.PhoneGpsState;
import com.mikasrevs.newphone.emergency.EmergencyService;
import net.minecraft.client.Minecraft;

/** Navigation state dedicated to responder tablets. It never opens or mutates the civilian phone GPS screen. */
public final class TabletNavigationState {
    private static boolean active;
    private static EmergencyService service;
    private static long dispatchId;
    private static PhoneGpsState.Location target;
    private static long ignoreToggleUntil;

    private TabletNavigationState() {
    }

    public static boolean active() {
        return active;
    }

    public static EmergencyService service() {
        return service;
    }

    public static long dispatchId() {
        return dispatchId;
    }

    public static PhoneGpsState.Location target() {
        return target;
    }

    public static long ignoreToggleUntil() {
        return ignoreToggleUntil;
    }

    public static void begin(
            Minecraft minecraft,
            EmergencyService responderService,
            long responderDispatchId,
            PhoneGpsState.Location location
    ) {
        if (minecraft == null || minecraft.player == null || responderService == null
                || responderDispatchId <= 0L || location == null) return;
        service = responderService;
        dispatchId = responderDispatchId;
        target = location;
        active = true;
        ignoreToggleUntil = System.currentTimeMillis() + 350L;
        minecraft.setScreen(null);
    }

    public static void restoreTablet(Minecraft minecraft) {
        if (!active || minecraft == null || service == null || dispatchId <= 0L) return;
        EmergencyService currentService = service;
        long currentDispatch = dispatchId;
        active = false;
        ignoreToggleUntil = System.currentTimeMillis() + 350L;
        ResponderTabletScreen.openFocused(currentService, currentDispatch);
    }

    public static boolean matches(EmergencyService candidateService, long candidateDispatchId) {
        return target != null && service == candidateService && dispatchId == candidateDispatchId;
    }

    public static void clear() {
        active = false;
        service = null;
        dispatchId = 0L;
        target = null;
        ignoreToggleUntil = System.currentTimeMillis() + 250L;
    }
}

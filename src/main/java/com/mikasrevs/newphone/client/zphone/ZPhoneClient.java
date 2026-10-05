package com.mikasrevs.newphone.client.zphone;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.gui.PhoneBlockPostScreen;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneHomeScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneLockScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneSettingsScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhonePhoneScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneContactsScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneMessagesScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneNotesScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneGpsScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneGalleryScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneCameraScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneBlockGramScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneBrowserScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneMineStoreScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneBankScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneBlockSnakeScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneCreeperRushScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneGemPairsScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneCalculatorScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneMusicScreen;
import com.mikasrevs.newphone.client.zphone.gui.PhoneYouTubeScreens;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneCalendarScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneClockScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneGenericAppScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneWeatherScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhonePinEntryScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhonePinSetupScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneCallScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneEmergencyScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneSetupScreen;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
import com.mikasrevs.newphone.client.security.PhoneSecurityStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client entry point for the cumulative Forge 1.20.1 MPhone port. */
public final class ZPhoneClient {
    private static boolean sessionUnlocked;
    public static boolean isSessionUnlocked() { return sessionUnlocked || !PhoneSettingsStore.lockScreenEnabled(); }
    private static long phoneClosedAt;
    private static long lastActivityAt;
    private static double lastMouseX = Double.NaN;
    private static double lastMouseY = Double.NaN;

    private ZPhoneClient() {
    }

    public static void openPhone() {
        openPhone(com.mikasrevs.newphone.PhoneInventoryUtil.findPhone(Minecraft.getInstance().player));
    }

    public static void openPhone(net.minecraft.world.item.ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        com.mikasrevs.newphone.client.device.PhoneDeviceSession.select(stack);
        PhoneClientData.requestFullSync();
        if(com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo()){
            PhoneNavigationState.clear();minecraft.setScreen(new com.mikasrevs.newphone.client.duo.DuoTransitionScreen(()->PhoneClientData.hasCall()?new ZPhoneCallScreen():createRootScreen()));return;
        }
        if (PhoneClientData.hasCall()) {
            minecraft.setScreen(new ZPhoneCallScreen());
            return;
        }
        PhoneNavigationState.clear();
        minecraft.setScreen(createRootScreen());
    }

    public static void unlockToHome() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        sessionUnlocked = true;
        recordActivity();
        PhoneNavigationState.openHome(minecraft);
    }

    public static void openHomeScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        if (!(minecraft.screen instanceof ZPhoneHomeScreen)) ZPhoneTransitionState.beginHomeReturn();
        PhoneNavigationState.openHome(minecraft);
    }

    public static void restartSetup() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        PhoneSettingsStore.restartSetup();
        PhoneNavigationState.clear();
        minecraft.setScreen(new ZPhoneSetupScreen());
    }

    public static void markPhoneClosed() {
        if (phoneClosedAt <= 0L) phoneClosedAt = System.currentTimeMillis();
        if (PhoneSettingsStore.setupCompleted() && PhoneSettingsStore.lockScreenEnabled()) {
            sessionUnlocked = false;
        }
    }

    public static void showLockScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        sessionUnlocked = false;
        phoneClosedAt = 0L;
        lastActivityAt = 0L;
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
        PhoneNavigationState.clear();
        minecraft.setScreen(PhoneClientData.hasCall() ? new ZPhoneCallScreen() : createLockScreen());
    }

    public static void markPhoneOpened() {
        phoneClosedAt = 0L;
        recordActivity();
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
    }

    public static void resetLockSession() {
        sessionUnlocked = false;
        phoneClosedAt = 0L;
        lastActivityAt = 0L;
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
    }

    public static void authorizeCurrentSession() {
        sessionUnlocked = true;
        recordActivity();
    }

    public static void recordPhoneActivity(Screen screen) {
        if (screen instanceof AbstractZPhoneScreen) recordActivity();
    }

    public static void tickAutoLock(Minecraft minecraft) {
        if (minecraft == null || !(minecraft.screen instanceof AbstractZPhoneScreen)
                || minecraft.screen instanceof ZPhoneLockScreen
                || minecraft.screen instanceof com.mikasrevs.newphone.client.duo.DuoLockScreen
                || minecraft.screen instanceof com.mikasrevs.newphone.client.duo.DuoTransitionScreen
                || minecraft.screen instanceof ZPhonePinEntryScreen
                || minecraft.screen instanceof ZPhoneCallScreen
                || (!PhoneSettingsStore.setupCompleted() && !PhoneSecurityStore.hasPin())
                || !PhoneSettingsStore.lockScreenEnabled()
                || !sessionUnlocked
                || PhoneClientData.hasCall()) {
            return;
        }

        long now = System.currentTimeMillis();
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        boolean mouseMoved = Double.isNaN(lastMouseX)
                || Math.abs(mouseX - lastMouseX) > 0.1D
                || Math.abs(mouseY - lastMouseY) > 0.1D;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (mouseMoved) recordActivity();

        int minutes = PhoneSettingsStore.autoLockMinutes();
        if (minutes <= 0 || lastActivityAt <= 0L || now - lastActivityAt < minutes * 60_000L) return;
        sessionUnlocked = false;
        PhoneNavigationState.clear();
        minecraft.setScreen(createLockScreen());
    }

    public static boolean canRestorePhoneContent() {
        return !shouldShowLockScreen();
    }

    static Screen createRootScreen() {
        if (shouldShowLockScreen()) return createLockScreen();
        if (PhoneSettingsStore.setupCompleted() && !PhoneSettingsStore.lockScreenEnabled()) {
            sessionUnlocked = true;
        }
        return PhoneSettingsStore.setupCompleted() ? (com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo()?new com.mikasrevs.newphone.client.duo.DuoHomeScreen():new ZPhoneHomeScreen()) : new ZPhoneSetupScreen();
    }
    private static Screen createLockScreen(){return com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo()?new com.mikasrevs.newphone.client.duo.DuoLockScreen():new ZPhoneLockScreen();}

    private static boolean shouldShowLockScreen() {
        if (!PhoneSettingsStore.setupCompleted()) {
            if (!PhoneSecurityStore.hasPin()) return false;
            if (!sessionUnlocked) return true;
            return autoLockExpired();
        }
        if (!PhoneSettingsStore.lockScreenEnabled()) return false;
        if (!sessionUnlocked) return true;
        return autoLockExpired();
    }

    private static boolean autoLockExpired() {
        int minutes = PhoneSettingsStore.autoLockMinutes();
        if (minutes <= 0 || phoneClosedAt <= 0L) return false;
        if (System.currentTimeMillis() - phoneClosedAt < minutes * 60_000L) return false;
        sessionUnlocked = false;
        return true;
    }

    private static void recordActivity() {
        lastActivityAt = System.currentTimeMillis();
    }

    /** Called after the lock-screen gesture so a configured PIN cannot be bypassed. */
    public static void finishLockUnlock() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        if (PhoneSecurityStore.hasPin()) minecraft.setScreen(new ZPhonePinEntryScreen());
        else unlockToHome();
    }

    public static void openPinEntryScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.setScreen(new ZPhonePinEntryScreen());
    }

    public static void openPinSetupScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.setScreen(new ZPhonePinSetupScreen());
    }

    public static void openWeather(Screen from) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) PhoneNavigationState.open(minecraft, from, new ZPhoneWeatherScreen());
    }

    public static void openPinSetup(Screen from) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) PhoneNavigationState.open(minecraft, from, new ZPhonePinSetupScreen());
    }

    public static void openPinRecovery(Screen from) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) PhoneNavigationState.open(minecraft, from, new ZPhonePinSetupScreen(true));
    }

    public static void openSharedApp(PhoneApp app) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        openSharedApp(app, minecraft.screen);
    }

    public static void openSharedApp(PhoneApp app, Screen from) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || app == null) return;
        if (app == PhoneApp.ADMIN_PANEL && !com.mikasrevs.newphone.client.moderation.PhoneModerationClient.canManage()) return;
        String platform = com.mikasrevs.newphone.client.moderation.PhoneModerationClient.platform(app);
        if (!platform.isEmpty() && (!com.mikasrevs.newphone.client.moderation.PhoneModerationClient.ready()
                || com.mikasrevs.newphone.client.moderation.PhoneModerationClient.banned(platform))) {
            PhoneNavigationState.open(minecraft, from, new com.mikasrevs.newphone.client.zphone.gui.ZPhoneAppAccessScreen(platform)); return;
        }
        Screen next = switch (app) {
            case ADMIN_PANEL -> new com.mikasrevs.newphone.client.zphone.gui.ZPhoneAdminScreen();
            case SETTINGS -> new ZPhoneSettingsScreen();
            case MESSAGES -> new ZPhoneMessagesScreen();
            case CONTACTS -> new ZPhoneContactsScreen();
            case PHONE -> new ZPhonePhoneScreen();
            case EMERGENCY -> new ZPhoneEmergencyScreen();
            case CAMERA -> new ZPhoneCameraScreen();
            case GALLERY -> new ZPhoneGalleryScreen();
            case GPS -> new ZPhoneGpsScreen();
            case NOTES -> new ZPhoneNotesScreen();
            case CALCULATOR -> new ZPhoneCalculatorScreen();
            case MUSIC -> new ZPhoneMusicScreen();
            case YOUTUBE -> PhoneYouTubeScreens.create();
            case CALENDAR -> new ZPhoneCalendarScreen();
            case CLOCK -> new ZPhoneClockScreen();
            case BLOCKGRAM -> new ZPhoneBlockGramScreen();
            case BLOCKPOST -> new PhoneBlockPostScreen();
            case BROWSER -> new ZPhoneBrowserScreen();
            case MINESTORE -> new ZPhoneMineStoreScreen();
            case BANK -> new ZPhoneBankScreen();
            case MARKETPLACE -> new com.mikasrevs.newphone.marketplace.client.MarketplaceScreen();
            case BLOCK_SNAKE -> new ZPhoneBlockSnakeScreen();
            case CREEPER_RUSH -> new ZPhoneCreeperRushScreen();
            case GEM_PAIRS -> new ZPhoneGemPairsScreen();
            default -> new ZPhoneGenericAppScreen(app);
        };
        PhoneNavigationState.open(minecraft, from, next);
    }
}

package com.mikasrevs.newphone.client.calendar;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.sound.PhoneSoundManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

public final class PhoneCalendarStore {
    private static String loadedScope = "";
    private static PhoneCalendarReminders book;
    private static int ticks;
    private PhoneCalendarStore() { }

    public static boolean hasReminder(long day) { return reminders().contains(day); }
    public static Set<Long> reminders() {
        var current = current(); return current == null ? Set.of() : current.days();
    }
    public static boolean toggleReminder(long day) {
        var current = current(); if (current == null) return false;
        try { return current.toggle(day); }
        catch (IOException failure) { warn(failure); return current.days().contains(day); }
    }
    public static void tick(Minecraft minecraft) {
        if (++ticks < 20) return;
        ticks = 0;
        if (PhoneSettingsStore.doNotDisturb() || !PhoneSettingsStore.messageNotifications()) return;
        var current = current(); if (current == null) return;
        long today = Math.floorDiv(minecraft.level.getDayTime(), 24000L) + 1;
        var due = current.due(today); if (due.isEmpty()) return;
        String dayText = due.size() == 1 ? String.valueOf(due.get(0))
                : due.get(0) + " – " + due.get(due.size() - 1) + " (" + due.size() + ")";
        minecraft.player.displayClientMessage(Component.literal(PhoneSettingsStore.text(
                "Takvim hatırlatıcısı — Minecraft günü: ", "Calendar reminder — Minecraft day: ") + dayText), false);
        PhoneSoundManager.playCalendarNotification();
        try { current.acknowledge(due); } catch (IOException failure) { warn(failure); }
    }
    public static void resetSession() { book = null; loadedScope = ""; ticks = 0; }
    private static PhoneCalendarReminders current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return null;
        String location;
        if (mc.getSingleplayerServer() != null) location = "world:" + mc.getSingleplayerServer()
                .getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else if (mc.getCurrentServer() != null) location = "server:" + mc.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
        else return null;
        String scope = location + "\nplayer:" + mc.player.getUUID();
        if (!scope.equals(loadedScope) || book == null) {
            book = null;
            try {
                book = new PhoneCalendarReminders(Path.of(System.getProperty("user.dir"), "config", "newphone"), scope);
                loadedScope = scope;
            } catch (IOException failure) { warn(failure); }
        }
        return book;
    }
    private static void warn(IOException failure) {
        com.mojang.logging.LogUtils.getLogger().warn("Could not persist phone calendar reminders", failure);
    }
}
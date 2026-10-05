package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;

/** Display time only; countdowns, cooldowns and message timestamps keep their own clocks. */
public final class PhoneTimeDisplay {
    private PhoneTimeDisplay() {}
    public static LocalTime minecraftTime(long ticks) {
        long withinDay = Math.floorMod(ticks, 24_000L);
        long seconds = (withinDay * 86_400L / 24_000L + 21_600L) % 86_400L;
        return LocalTime.ofSecondOfDay(seconds);
    }
    public static long minecraftDay(long ticks) { return Math.floorDiv(ticks, 24_000L) + 1; }
    private static long ticks() {
        var minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.level == null ? 0 : minecraft.level.getDayTime();
    }
    public static String time(boolean seconds) {
        LocalTime time = PhoneSettingsStore.useMinecraftTime() ? minecraftTime(ticks()) : LocalTime.now();
        String pattern = PhoneSettingsStore.use24HourClock() ? (seconds ? "HH:mm:ss" : "HH:mm") : (seconds ? "h:mm:ss a" : "h:mm");
        return time.format(DateTimeFormatter.ofPattern(pattern, PhoneSettingsStore.locale()));
    }
    public static String dayLabel() {
        return PhoneSettingsStore.text("Minecraft Günü ", "Minecraft Day ") + minecraftDay(ticks());
    }
}

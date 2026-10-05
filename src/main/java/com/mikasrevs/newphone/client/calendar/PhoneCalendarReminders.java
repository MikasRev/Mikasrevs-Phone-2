package com.mikasrevs.newphone.client.calendar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Persistent reminder state, independent of a running client. Days are one-based Minecraft days. */
public final class PhoneCalendarReminders {
    private final Path file;
    private final Set<Long> days = new HashSet<>(), notified = new HashSet<>();

    public PhoneCalendarReminders(Path directory, String scope) throws IOException {
        String id = UUID.nameUUIDFromBytes(scope.getBytes(StandardCharsets.UTF_8)).toString();
        file = directory.resolve("calendars").resolve(id + ".properties");
        Properties properties = read(file);
        Path legacy = directory.resolve("calendar.properties");
        if (!Files.exists(file) && Files.exists(legacy)) {
            Properties old = read(legacy);
            String assigned = old.getProperty("migratedTo", "");
            if (assigned.isEmpty() || assigned.equals(id)) {
                // Claim first, so a later server cannot import the same legacy reminders.
                old.setProperty("migratedTo", id);
                write(legacy, old);
                properties.setProperty("days", old.getProperty("days", ""));
                write(file, properties);
            }
        }
        parse(properties.getProperty("days", ""), days);
        parse(properties.getProperty("notified", ""), notified);
        notified.retainAll(days);
    }
    public Set<Long> days() { return Set.copyOf(days); }
    public boolean toggle(long day) throws IOException {
        if (day < 1) return false;
        boolean enabled = !days.remove(day);
        boolean wasNotified = notified.remove(day);
        if (enabled) days.add(day);
        try { persist(); }
        catch (IOException failure) {
            if (enabled) days.remove(day); else days.add(day);
            if (wasNotified) notified.add(day);
            throw failure;
        }
        return enabled;
    }
    public List<Long> due(long today) {
        return days.stream().filter(day -> day <= today && !notified.contains(day)).sorted().toList();
    }
    public void acknowledge(Collection<Long> delivered) throws IOException {
        Set<Long> added = new HashSet<>(delivered);
        added.retainAll(days); added.removeAll(notified);
        if (added.isEmpty()) return;
        notified.addAll(added);
        try { persist(); } catch (IOException failure) { notified.removeAll(added); throw failure; }
    }
    private void persist() throws IOException {
        Properties p = new Properties();
        p.setProperty("days", join(days)); p.setProperty("notified", join(notified));
        write(file, p);
    }
    private static String join(Set<Long> values) {
        return values.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }
    private static void parse(String value, Set<Long> into) {
        for (String part : value.split(",")) {
            try { long day = Long.parseLong(part.trim()); if (day > 0) into.add(day); }
            catch (NumberFormatException ignored) { }
        }
    }
    private static Properties read(Path file) throws IOException {
        Properties p = new Properties();
        if (Files.exists(file)) try (var input = Files.newInputStream(file)) { p.load(input); }
        return p;
    }
    private static void write(Path file, Properties p) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "calendar-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temp)) { p.store(output, "Phone calendar reminders"); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}

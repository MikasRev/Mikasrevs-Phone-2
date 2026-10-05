package com.mikasrevs.newphone.client.notes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Client-local notes store. Notes intentionally remain private to the local game profile. */
public final class PhoneNotesStore {
    private static final Path NOTES_FILE = Path.of(System.getProperty("user.dir"), "config", "newphone", "notes.properties");
    private static final List<NoteEntry> NOTES = new ArrayList<>();
    private static boolean loaded;

    private PhoneNotesStore() { }

    public static List<NoteEntry> all() {
        ensureLoaded();
        return NOTES.stream().sorted(Comparator.comparingLong(NoteEntry::updatedAt).reversed()).toList();
    }

    public static NoteEntry find(String id) {
        ensureLoaded();
        if (id == null) return null;
        return NOTES.stream().filter(note -> note.id().equals(id)).findFirst().orElse(null);
    }

    public static NoteEntry save(String id, String title, String body) {
        ensureLoaded();
        String normalizedTitle = title == null ? "" : title.strip();
        String normalizedBody = body == null ? "" : body.stripTrailing();
        if (normalizedTitle.isBlank()) normalizedTitle = "Başlıksız Not";
        String noteId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        NOTES.removeIf(note -> note.id().equals(noteId));
        NoteEntry entry = new NoteEntry(noteId, normalizedTitle, normalizedBody, System.currentTimeMillis());
        NOTES.add(entry);
        persist();
        return entry;
    }

    public static void delete(String id) {
        ensureLoaded();
        if (id != null && NOTES.removeIf(note -> note.id().equals(id))) persist();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        NOTES.clear();
        if (!Files.exists(NOTES_FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(NOTES_FILE)) { properties.load(input); }
        catch (IOException ignored) { return; }
        for (String id : properties.getProperty("ids", "").split(",")) {
            if (id.isBlank()) continue;
            String title = decode(properties.getProperty("note." + id + ".title", ""));
            String body = decode(properties.getProperty("note." + id + ".body", ""));
            long updated;
            try { updated = Long.parseLong(properties.getProperty("note." + id + ".updated", "0")); }
            catch (NumberFormatException ignored) { updated = 0L; }
            NOTES.add(new NoteEntry(id, title, body, updated));
        }
    }

    private static void persist() {
        Properties properties = new Properties();
        properties.setProperty("ids", String.join(",", NOTES.stream().map(NoteEntry::id).toList()));
        for (NoteEntry note : NOTES) {
            properties.setProperty("note." + note.id() + ".title", encode(note.title()));
            properties.setProperty("note." + note.id() + ".body", encode(note.body()));
            properties.setProperty("note." + note.id() + ".updated", Long.toString(note.updatedAt()));
        }
        try {
            Files.createDirectories(NOTES_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(NOTES_FILE)) { properties.store(output, "MPhone notes"); }
        } catch (IOException ignored) { }
    }

    private static String encode(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String decode(String value) {
        try { return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ignored) { return ""; }
    }

    public record NoteEntry(String id, String title, String body, long updatedAt) { }
}

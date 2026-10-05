package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Persistent music library containing vanilla discs and locally imported OGG/WAV files. */
public final class PhoneMusicPlayerState {
    private static final Path MUSIC_DIRECTORY = Path.of(
            System.getProperty("user.dir"), "config", "newphone", "music"
    );
    private static final Path LIBRARY_FILE = MUSIC_DIRECTORY.resolve("library.properties");
    private static final Path FAVORITES_FILE = MUSIC_DIRECTORY.getParent().resolve("music_favorites.properties");

    private static final List<Track> TRACKS = new ArrayList<>(List.of(
            builtIn("Cat", "C418", SoundEvents.MUSIC_DISC_CAT, 185_343L),
            builtIn("Pigstep", "Lena Raine", SoundEvents.MUSIC_DISC_PIGSTEP, 148_235L),
            builtIn("Otherside", "Lena Raine", SoundEvents.MUSIC_DISC_OTHERSIDE, 195_652L),
            builtIn("Relic", "Aaron Cherof", SoundEvents.MUSIC_DISC_RELIC, 218_967L)
    ));

    private static int currentTrack;
    private static SimpleSoundInstance currentSound;
    private static SourceDataLine localLine;
    private static Thread localPlaybackThread;
    private static int playbackGeneration;
    private static boolean localPlaybackRequested;
    private static long startedAt;
    private static final Set<String> FAVORITE_TRACKS = new LinkedHashSet<>();
    private static boolean favoritesLoaded;
    private static boolean libraryLoaded;

    private PhoneMusicPlayerState() {}

    public static synchronized Track[] tracks() {
        ensureLibraryLoaded();
        return TRACKS.toArray(Track[]::new);
    }

    public static synchronized int currentTrackIndex() {
        ensureLibraryLoaded();
        currentTrack = Math.max(0, Math.min(TRACKS.size() - 1, currentTrack));
        return currentTrack;
    }

    public static synchronized Track currentTrack() {
        ensureLibraryLoaded();
        return TRACKS.get(currentTrackIndex());
    }

    public static synchronized boolean isFavorite(int index) {
        ensureLibraryLoaded();
        ensureFavoritesLoaded();
        if (index < 0 || index >= TRACKS.size()) return false;
        return FAVORITE_TRACKS.contains(trackKey(TRACKS.get(index)));
    }

    public static synchronized boolean isCurrentFavorite() {
        return isFavorite(currentTrackIndex());
    }

    public static synchronized void toggleFavorite(int index) {
        ensureLibraryLoaded();
        ensureFavoritesLoaded();
        if (index < 0 || index >= TRACKS.size()) return;
        String key = trackKey(TRACKS.get(index));
        if (!FAVORITE_TRACKS.remove(key)) FAVORITE_TRACKS.add(key);
        saveFavorites();
    }

    public static synchronized int[] favoriteTrackIndexes() {
        ensureLibraryLoaded();
        ensureFavoritesLoaded();
        int[] result = new int[TRACKS.size()];
        int count = 0;
        for (int index = 0; index < TRACKS.size(); index++) {
            if (FAVORITE_TRACKS.contains(trackKey(TRACKS.get(index)))) result[count++] = index;
        }
        return Arrays.copyOf(result, count);
    }

    public static synchronized boolean isPlaying() {
        ensureLibraryLoaded();
        if (localPlaybackRequested) return true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || currentSound == null) return false;
        boolean active = minecraft.getSoundManager().isActive(currentSound);
        if (!active) currentSound = null;
        return active;
    }

    public static synchronized void play(int index) {
        ensureLibraryLoaded();
        if (TRACKS.isEmpty()) return;
        stop();
        currentTrack = Math.floorMod(index, TRACKS.size());
        Track track = TRACKS.get(currentTrack);
        startedAt = System.currentTimeMillis();
        if (track.localFile() != null) {
            playLocal(track);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || track.sound() == null) return;
        currentSound = createSound(track);
        minecraft.getSoundManager().play(currentSound);
    }

    public static synchronized void toggle() {
        if (isPlaying()) stop();
        else play(currentTrackIndex());
    }

    public static synchronized void next() { play(currentTrackIndex() + 1); }
    public static synchronized void previous() { play(currentTrackIndex() - 1); }

    public static synchronized void stop() {
        playbackGeneration++;
        localPlaybackRequested = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && currentSound != null) minecraft.getSoundManager().stop(currentSound);
        currentSound = null;

        SourceDataLine line = localLine;
        localLine = null;
        if (line != null) {
            try { line.stop(); } catch (RuntimeException ignored) {}
            try { line.flush(); } catch (RuntimeException ignored) {}
            try { line.close(); } catch (RuntimeException ignored) {}
        }
        Thread worker = localPlaybackThread;
        localPlaybackThread = null;
        if (worker != null && worker != Thread.currentThread()) worker.interrupt();
    }

    public static synchronized long elapsedMs() {
        return isPlaying() ? Math.min(currentTrack().durationMs(), Math.max(0L, System.currentTimeMillis() - startedAt)) : 0L;
    }

    public static synchronized float progress() {
        return Math.min(1.0F, elapsedMs() / (float) Math.max(1L, currentTrack().durationMs()));
    }

    public static synchronized ImportResult importTrack(Path source) {
        ensureLibraryLoaded();
        if (source == null) return ImportResult.failure(ImportError.INVALID_FILE);
        Path normalized;
        try {
            normalized = source.toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return ImportResult.failure(ImportError.INVALID_FILE);
        }
        if (!Files.isRegularFile(normalized)) return ImportResult.failure(ImportError.INVALID_FILE);

        String extension = extension(normalized);
        if (!isSupportedExtension(extension)) {
            return ImportResult.failure(ImportError.UNSUPPORTED_FORMAT);
        }
        try {
            if (Files.size(normalized) > PhoneMusicAudio.MAX_FILE_BYTES) return ImportResult.failure(ImportError.FILE_TOO_LARGE);
        } catch (IOException ignored) {
            return ImportResult.failure(ImportError.READ_FAILED);
        }

        PhoneMusicAudio.AudioInfo info;
        try {
            info = PhoneMusicAudio.inspect(normalized);
        } catch (PhoneMusicAudio.AudioLimitException ignored) {
            return ImportResult.failure(ImportError.AUDIO_TOO_LONG);
        } catch (Exception ignored) {
            return ImportResult.failure(ImportError.INVALID_AUDIO);
        }
        if (info.durationMs() <= 0L || info.durationMs() > PhoneMusicAudio.MAX_DURATION_MS
                || info.pcmBytes() <= 0L || info.pcmBytes() > PhoneMusicAudio.MAX_PCM_BYTES) {
            return ImportResult.failure(ImportError.AUDIO_TOO_LONG);
        }

        Path destination = null;
        Track addedTrack = null;
        try {
            Files.createDirectories(MUSIC_DIRECTORY);
            destination = uniqueDestination(normalized.getFileName().toString());
            Files.copy(normalized, destination, StandardCopyOption.COPY_ATTRIBUTES);
            String[] metadata = titleAndArtist(normalized.getFileName().toString());
            addedTrack = new Track(metadata[0], metadata[1], null, destination, info.durationMs());
            TRACKS.add(addedTrack);
            if (!saveLibrary()) throw new IOException("Could not save music library");
            return ImportResult.success(TRACKS.size() - 1);
        } catch (IOException | RuntimeException ignored) {
            if (addedTrack != null) TRACKS.remove(addedTrack);
            if (destination != null) {
                try { Files.deleteIfExists(destination); } catch (IOException ignoredDelete) {}
            }
            return ImportResult.failure(ImportError.COPY_FAILED);
        }
    }

    private static void playLocal(Track track) {
        int generation = ++playbackGeneration;
        localPlaybackRequested = true;
        float volume = PhoneSettingsStore.phoneVolume();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) volume *= minecraft.options.getSoundSourceVolume(SoundSource.MASTER)
                * minecraft.options.getSoundSourceVolume(SoundSource.RECORDS);

        float finalVolume = Math.max(0.0F, Math.min(1.0F, volume));
        Thread worker = new Thread(() -> runLocalPlayback(track, generation, finalVolume), "MPhone music player");
        worker.setDaemon(true);
        localPlaybackThread = worker;
        worker.start();
    }

    private static void runLocalPlayback(Track track, int generation, float volume) {
        SourceDataLine line = null;
        try {
            PhoneMusicAudio.AudioData audio = PhoneMusicAudio.decode(track.localFile());
            if (!isCurrentPlayback(generation)) return;
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audio.format());
            line = (SourceDataLine) AudioSystem.getLine(lineInfo);
            line.open(audio.format());
            applyVolume(line, volume);
            synchronized (PhoneMusicPlayerState.class) {
                if (!isCurrentPlayback(generation)) return;
                localLine = line;
            }
            line.start();
            byte[] pcm = audio.pcm();
            int offset = 0;
            while (offset < pcm.length && isCurrentPlayback(generation) && !Thread.currentThread().isInterrupted()) {
                offset += line.write(pcm, offset, Math.min(32_768, pcm.length - offset));
            }
            if (isCurrentPlayback(generation)) line.drain();
        } catch (Exception | LinkageError | java.util.ServiceConfigurationError error) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not play imported phone music: {}", track.localFile(), error);
        } finally {
            if (line != null) {
                try { line.stop(); } catch (RuntimeException ignored) {}
                try { line.close(); } catch (RuntimeException ignored) {}
            }
            synchronized (PhoneMusicPlayerState.class) {
                if (generation == playbackGeneration) {
                    localLine = null;
                    localPlaybackThread = null;
                    localPlaybackRequested = false;
                }
            }
        }
    }

    private static synchronized boolean isCurrentPlayback(int generation) {
        return localPlaybackRequested && generation == playbackGeneration;
    }

    private static void applyVolume(SourceDataLine line, float volume) {
        if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
        float decibels = volume <= 0.0001F ? gain.getMinimum() : (float) (20.0D * Math.log10(volume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
    }

    private static SimpleSoundInstance createSound(Track track) {
        return new SimpleSoundInstance(
                track.sound().getLocation(), SoundSource.RECORDS, PhoneSettingsStore.phoneVolume(), 1.0F,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE,
                0.0D, 0.0D, 0.0D, true
        );
    }

    private static Track builtIn(String title, String artist, SoundEvent sound, long durationMs) {
        return new Track(title, artist, sound, null, durationMs);
    }

    private static String trackKey(Track track) {
        if (track.localFile() != null) return "local:" + track.localFile().getFileName();
        return track.sound().getLocation().toString();
    }

    private static void ensureLibraryLoaded() {
        if (libraryLoaded) return;
        libraryLoaded = true;
        if (!Files.isRegularFile(LIBRARY_FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(LIBRARY_FILE)) {
            properties.load(input);
            int count = Math.max(0, Math.min(1_000, Integer.parseInt(properties.getProperty("count", "0"))));
            for (int index = 0; index < count; index++) {
                String fileName = properties.getProperty("track." + index + ".file", "").trim();
                if (fileName.isEmpty()) continue;
                Path file = MUSIC_DIRECTORY.resolve(Path.of(fileName).getFileName()).normalize();
                if (!file.startsWith(MUSIC_DIRECTORY.normalize()) || !Files.isRegularFile(file)) continue;
                String extension = extension(file);
                if (!isSupportedExtension(extension)) continue;
                String title = properties.getProperty("track." + index + ".title", stripExtension(fileName));
                String artist = properties.getProperty("track." + index + ".artist", "Imported");
                long duration = Long.parseLong(properties.getProperty("track." + index + ".duration", "0"));
                if (duration > 0L) TRACKS.add(new Track(title, artist, null, file, duration));
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static boolean saveLibrary() {
        try {
            Files.createDirectories(MUSIC_DIRECTORY);
            Properties properties = new Properties();
            int imported = Math.max(0, TRACKS.size() - 4);
            properties.setProperty("count", Integer.toString(imported));
            for (int index = 0; index < imported; index++) {
                Track track = TRACKS.get(index + 4);
                properties.setProperty("track." + index + ".file", track.localFile().getFileName().toString());
                properties.setProperty("track." + index + ".title", track.title());
                properties.setProperty("track." + index + ".artist", track.artist());
                properties.setProperty("track." + index + ".duration", Long.toString(track.durationMs()));
            }
            try (OutputStream output = Files.newOutputStream(LIBRARY_FILE)) {
                properties.store(output, "MPhone imported music library");
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void ensureFavoritesLoaded() {
        if (favoritesLoaded) return;
        favoritesLoaded = true;
        FAVORITE_TRACKS.clear();
        if (!Files.isRegularFile(FAVORITES_FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FAVORITES_FILE)) {
            properties.load(input);
            for (String key : properties.getProperty("tracks", "").split(",")) {
                String trimmed = key.trim();
                if (!trimmed.isEmpty()) FAVORITE_TRACKS.add(trimmed);
            }
        } catch (IOException | IllegalArgumentException ignored) {
        }
    }

    private static void saveFavorites() {
        try {
            Files.createDirectories(FAVORITES_FILE.getParent());
            Properties properties = new Properties();
            properties.setProperty("tracks", String.join(",", FAVORITE_TRACKS));
            try (OutputStream output = Files.newOutputStream(FAVORITES_FILE)) {
                properties.store(output, "MPhone music favorites");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path uniqueDestination(String originalName) {
        String safeName = originalName.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (safeName.isBlank()) safeName = "track." + extension(originalName);
        Path candidate = MUSIC_DIRECTORY.resolve(safeName);
        String stem = stripExtension(safeName);
        String suffix = "." + extension(safeName);
        int copy = 2;
        while (Files.exists(candidate)) candidate = MUSIC_DIRECTORY.resolve(stem + " (" + copy++ + ")" + suffix);
        return candidate;
    }

    private static String[] titleAndArtist(String fileName) {
        String title = stripExtension(fileName).replace('_', ' ').trim();
        String artist = "Imported";
        int separator = title.indexOf(" - ");
        if (separator > 0 && separator + 3 < title.length()) {
            artist = title.substring(0, separator).trim();
            title = title.substring(separator + 3).trim();
        }
        if (title.isBlank()) title = "Imported track";
        return new String[]{title, artist};
    }

    private static String extension(Path path) { return extension(path.getFileName().toString()); }

    private static boolean isSupportedExtension(String extension) {
        return PhoneMusicAudio.isSupportedExtension(extension);
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    public record Track(String title, String artist, SoundEvent sound, Path localFile, long durationMs) {}

    public record ImportResult(boolean success, int trackIndex, ImportError error) {
        private static ImportResult success(int index) { return new ImportResult(true, index, null); }
        private static ImportResult failure(ImportError error) { return new ImportResult(false, -1, error); }
    }

    public enum ImportError {
        INVALID_FILE, UNSUPPORTED_FORMAT, FILE_TOO_LARGE, INVALID_AUDIO,
        AUDIO_TOO_LONG, READ_FAILED, COPY_FAILED
    }
}

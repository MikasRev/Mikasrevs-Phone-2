package com.mikasrevs.newphone.client.gui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ServiceConfigurationError;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryUtil;

/** Bounded local WAV/OGG decoding, independent of the world and optional MP3 libraries. */
final class PhoneMusicAudio {
    static final long MAX_FILE_BYTES = 50L * 1024L * 1024L;
    static final long MAX_PCM_BYTES = 256L * 1024L * 1024L;
    static final long MAX_DURATION_MS = 7L * 60L * 1_000L;

    private PhoneMusicAudio() {}

    static boolean isSupportedExtension(String extension) {
        return extension.equals("ogg") || extension.equals("wav");
    }

    private static String extension(Path file) throws IOException {
        if (file == null || file.getFileName() == null) throw new IOException("Invalid audio path");
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!isSupportedExtension(extension)) throw new IOException("Only OGG and WAV are supported");
        if (!Files.isRegularFile(file) || Files.size(file) <= 0) throw new IOException("Empty or missing audio file");
        if (Files.size(file) > MAX_FILE_BYTES) throw new AudioLimitException();
        return extension;
    }

    static AudioInfo inspect(Path file) throws Exception {
        try {
            String extension = extension(file);
            AudioInfo info = extension.equals("ogg") ? inspectOgg(file) : inspectWav(file);
            if (info.durationMs() <= 0 || info.pcmBytes() <= 0) throw new IOException("Empty audio stream");
            if (info.durationMs() > MAX_DURATION_MS || info.pcmBytes() > MAX_PCM_BYTES) throw new AudioLimitException();
            return info;
        } catch (LinkageError | ServiceConfigurationError failure) {
            throw new IOException("Audio decoder is unavailable", failure);
        }
    }

    static AudioData decode(Path file) throws Exception {
        try {
            inspect(file); // Recheck files restored from disk or modified after import.
            if (extension(file).equals("ogg")) return decodeOgg(file);
            try (AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile())) {
                AudioFormat pcmFormat = pcmFormat(source.getFormat());
                try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, source)) {
                    return new AudioData(readLimited(pcm, pcmLimit(pcmFormat)), pcmFormat);
                }
            }
        } catch (LinkageError | ServiceConfigurationError failure) {
            throw new IOException("Audio decoder is unavailable", failure);
        }
    }

    private static AudioFormat pcmFormat(AudioFormat original) throws IOException {
        int channels = original.getChannels();
        float rate = original.getSampleRate();
        if (channels < 1 || channels > 2 || !Float.isFinite(rate) || rate <= 0)
            throw new IOException("Unsupported audio format");
        AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels, channels * 2, rate, false);
        if (!AudioSystem.isConversionSupported(pcm, original)) throw new IOException("Unsupported WAV encoding");
        return pcm;
    }

    private static AudioInfo inspectWav(Path file) throws Exception {
        AudioFileFormat container = AudioSystem.getAudioFileFormat(file.toFile());
        if (!AudioFileFormat.Type.WAVE.equals(container.getType())) throw new IOException("Not a WAV file");
        AudioFormat original = container.getFormat();
        AudioFormat pcm = pcmFormat(original);
        long frames = container.getFrameLength();
        float frameRate = original.getFrameRate();
        if (frames <= 0 || !Float.isFinite(frameRate) || frameRate <= 0) throw new IOException("Invalid WAV duration");
        long duration = Math.round(frames * 1000.0D / frameRate);
        long pcmFrames = Math.round(frames * (double) pcm.getSampleRate() / frameRate);
        if (duration > MAX_DURATION_MS) throw new AudioLimitException();
        if (original.getFrameSize() <= 0 || Files.size(file) < Math.multiplyExact(frames, original.getFrameSize()))
            throw new IOException("Truncated WAV data");
        return new AudioInfo(duration, Math.multiplyExact(pcmFrames, pcm.getFrameSize()));
    }

    private static AudioInfo inspectOgg(Path file) throws IOException {
        long decoder = openOgg(file);
        try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
            STBVorbis.stb_vorbis_get_info(decoder, info);
            int channels = info.channels(), rate = info.sample_rate();
            int samples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);
            if (channels < 1 || channels > 2 || rate <= 0 || samples <= 0) throw new IOException("Invalid OGG stream");
            return new AudioInfo(Math.round(samples * 1000.0D / rate), Math.multiplyExact((long) samples, channels * 2L));
        } finally {
            STBVorbis.stb_vorbis_close(decoder);
        }
    }

    private static long openOgg(Path file) throws IOException {
        int[] error = new int[1];
        long decoder = STBVorbis.stb_vorbis_open_filename(file.toString(), error, null);
        if (decoder == MemoryUtil.NULL) throw new IOException("Invalid OGG file: " + error[0]);
        return decoder;
    }

    private static AudioData decodeOgg(Path file) throws IOException {
        long decoder = openOgg(file);
        ShortBuffer samples = null;
        try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
            STBVorbis.stb_vorbis_get_info(decoder, info);
            int channels = info.channels(), rate = info.sample_rate();
            if (channels < 1 || channels > 2 || rate <= 0) throw new IOException("Unsupported OGG stream");
            AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
            long limit = pcmLimit(format);
            samples = MemoryUtil.memAllocShort(16_384);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] block = new byte[32_768];
            int frames;
            // Decode in fixed chunks: native allocation must not depend on untrusted duration metadata.
            while ((frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, samples)) > 0) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Playback cancelled");
                int length = frames * channels;
                if ((long) output.size() + length * 2L > limit) throw new AudioLimitException();
                for (int i = 0; i < length; i++) {
                    short sample = samples.get(i);
                    block[i * 2] = (byte) sample;
                    block[i * 2 + 1] = (byte) (sample >>> 8);
                }
                output.write(block, 0, length * 2);
            }
            if (STBVorbis.stb_vorbis_get_error(decoder) != 0 || output.size() == 0) throw new IOException("Invalid OGG data");
            return new AudioData(output.toByteArray(), format);
        } finally {
            if (samples != null) MemoryUtil.memFree(samples);
            STBVorbis.stb_vorbis_close(decoder);
        }
    }

    private static long pcmLimit(AudioFormat format) {
        return Math.min(MAX_PCM_BYTES, (long) (format.getSampleRate() * (double) MAX_DURATION_MS / 1000) * format.getFrameSize());
    }

    private static byte[] readLimited(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[32_768];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Playback cancelled");
            if (read == 0) continue;
            if ((long) output.size() + read > limit) throw new AudioLimitException();
            output.write(buffer, 0, read);
        }
        if (output.size() == 0) throw new IOException("Empty audio data");
        return output.toByteArray();
    }

    record AudioInfo(long durationMs, long pcmBytes) {}
    record AudioData(byte[] pcm, AudioFormat format) {}
    static final class AudioLimitException extends IOException {
        AudioLimitException() { super("Audio exceeds the import limits"); }
    }
}

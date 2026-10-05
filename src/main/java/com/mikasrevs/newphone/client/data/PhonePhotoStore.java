package com.mikasrevs.newphone.client.data;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.network.message.PhonePhotoUploadMessage;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import com.mikasrevs.newphone.network.PhoneNetwork;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Shared social/DM photo cache and packet-budget-aware screenshot encoder. */
public final class PhonePhotoStore {
    private static final int[] MESSAGE_MAX_EDGE_STEPS = {768, 704, 640, 576, 512, 448, 384, 320, 256, 224, 192};
    private static final double[] PROFILE_SCALE_STEPS = {1.0D, 0.875D, 0.75D, 0.625D, 0.5D};
    private static final int PROFILE_WIDTH = 384;
    private static final int PROFILE_HEIGHT = 480;
    private static final float MAX_JPEG_QUALITY = 0.94F;
    private static final float MIN_JPEG_QUALITY = 0.58F;
    private static final float EMERGENCY_JPEG_QUALITY = 0.40F;
    private static final int QUALITY_SEARCH_STEPS = 7;
    private static final Map<String, byte[]> PHOTO_BYTES = new LinkedHashMap<>(256, 0.75F, true);
    private static final Map<String, PhotoTexture> TEXTURES = new LinkedHashMap<>(48, 0.75F, true);
    private static final Map<String, Long> REQUESTED_AT = new LinkedHashMap<>();
    private static final Map<ResourceLocation, DynamicTexture> LIVE_TEXTURES = new HashMap<>();
    private static final AtomicLong TEXTURE_SEQUENCE = new AtomicLong();

    private PhonePhotoStore() { }

    /** Portrait crop used by profile-style apps. */
    public static byte[] encodeForProfile(Path path) {
        return encodeForProfile(path, PhonePhotoUploadMessage.MAX_IMAGE_BYTES);
    }

    public static byte[] encodeForProfile(Path path, int maxBytes) {
        return encodeCropped(path, PROFILE_WIDTH, PROFILE_HEIGHT, maxBytes);
    }

    private static byte[] encodeCropped(Path path, int targetWidth, int targetHeight, int maxBytes) {
        if (path == null || !Files.isRegularFile(path) || maxBytes <= 0) return null;
        try {
            BufferedImage source = ImageIO.read(path.toFile());
            if (source == null) return null;
            int safeLimit = safeImageLimit(maxBytes);
            for (double step : PROFILE_SCALE_STEPS) {
                int width = Math.max(1, (int) Math.round(targetWidth * step));
                int height = Math.max(1, (int) Math.round(targetHeight * step));
                BufferedImage cropped = renderCover(source, width, height);
                float minimumQuality = step > 0.5D ? MIN_JPEG_QUALITY : EMERGENCY_JPEG_QUALITY;
                byte[] encoded = encodeBestJpegUnderLimit(cropped, safeLimit, minimumQuality);
                if (encoded != null) return encoded;
            }
            return null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    public static byte[] encodeForMessage(Path path) {
        return encodeForMessage(path, PhonePhotoUploadMessage.MAX_IMAGE_BYTES);
    }

    public static byte[] encodeForMessage(Path path, int maxBytes) {
        if (path == null || !Files.isRegularFile(path) || maxBytes <= 0) return null;
        try {
            BufferedImage source = ImageIO.read(path.toFile());
            if (source == null) return null;
            int safeLimit = safeImageLimit(maxBytes);
            int sourceMaxEdge = Math.max(source.getWidth(), source.getHeight());
            int previousWidth = -1;
            int previousHeight = -1;
            for (int maxEdge : MESSAGE_MAX_EDGE_STEPS) {
                double scale = Math.min(1.0D, maxEdge / (double) sourceMaxEdge);
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                if (width == previousWidth && height == previousHeight) continue;
                previousWidth = width;
                previousHeight = height;

                BufferedImage scaled = renderScaled(source, width, height);
                float minimumQuality = maxEdge > 224 ? MIN_JPEG_QUALITY : EMERGENCY_JPEG_QUALITY;
                byte[] encoded = encodeBestJpegUnderLimit(scaled, safeLimit, minimumQuality);
                if (encoded != null) return encoded;
            }
            return null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    /**
     * PNG screenshots can easily exceed Minecraft 1.20.1's 32 KiB serverbound
     * custom-payload ceiling. Resolution and JPEG quality are both adapted so
     * the image uses the available packet budget without crossing that ceiling.
     */
    private static byte[] encodeBestJpegUnderLimit(BufferedImage image, int maxBytes, float minimumQuality) {
        byte[] highQuality = encodeJpeg(image, MAX_JPEG_QUALITY);
        if (fits(highQuality, maxBytes)) return highQuality;

        float lower = Math.max(0.05F, Math.min(MAX_JPEG_QUALITY, minimumQuality));
        byte[] best = encodeJpeg(image, lower);
        if (!fits(best, maxBytes)) return null;

        float upper = MAX_JPEG_QUALITY;
        for (int i = 0; i < QUALITY_SEARCH_STEPS; i++) {
            float quality = (lower + upper) * 0.5F;
            byte[] candidate = encodeJpeg(image, quality);
            if (fits(candidate, maxBytes)) {
                best = candidate;
                lower = quality;
            } else {
                upper = quality;
            }
        }
        return best;
    }

    private static boolean fits(byte[] data, int maxBytes) {
        return data != null && data.length > 0 && data.length <= maxBytes;
    }

    private static int safeImageLimit(int maxBytes) {
        return Math.max(1, Math.min(maxBytes, PhonePhotoUploadMessage.MAX_IMAGE_BYTES));
    }

    private static BufferedImage renderScaled(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            applyHighQualityRendering(graphics);
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static BufferedImage renderCover(BufferedImage source, int targetWidth, int targetHeight) {
        double scale = Math.max(targetWidth / (double) source.getWidth(), targetHeight / (double) source.getHeight());
        int drawWidth = Math.max(targetWidth, (int) Math.round(source.getWidth() * scale));
        int drawHeight = Math.max(targetHeight, (int) Math.round(source.getHeight() * scale));
        int drawX = (targetWidth - drawWidth) / 2;
        int drawY = (targetHeight - drawHeight) / 2;
        BufferedImage cropped = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = cropped.createGraphics();
        try {
            applyHighQualityRendering(graphics);
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, drawX, drawY, drawWidth, drawHeight, null);
        } finally {
            graphics.dispose();
        }
        return cropped;
    }

    private static void applyHighQualityRendering(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) return null;
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(16_384);
             MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(Math.max(0.05F, Math.min(1.0F, quality)));
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
            return output.toByteArray();
        } catch (IOException | RuntimeException ignored) {
            return null;
        } finally {
            writer.dispose();
        }
    }

    public static synchronized void receive(String photoId, byte[] imageData) {
        if (photoId == null || photoId.isBlank() || imageData == null || imageData.length == 0
                || imageData.length > PhonePhotoUploadMessage.MAX_IMAGE_BYTES) return;
        byte[] existing = PHOTO_BYTES.get(photoId);
        if (existing != null && Arrays.equals(existing, imageData)) return;
        if (!com.mikasrevs.newphone.network.PhoneImageLimits.accepts(imageData)) return;
        PHOTO_BYTES.put(photoId, Arrays.copyOf(imageData, imageData.length));
        REQUESTED_AT.remove(photoId);
        while (PHOTO_BYTES.size() > 256) {
            String oldest = PHOTO_BYTES.keySet().iterator().next();
            PHOTO_BYTES.remove(oldest);
            PhotoTexture texture = TEXTURES.remove(oldest);
            if (texture != null) release(texture.location());
        }
        PhotoTexture old = TEXTURES.remove(photoId);
        if (old != null) release(old.location());
    }

    public static synchronized PhotoTexture textureFor(Minecraft minecraft, String photoId) {
        if (minecraft == null || photoId == null || photoId.isBlank() || "SKIN_PORTRAIT".equals(photoId)) return null;
        PhotoTexture cached = TEXTURES.get(photoId);
        if (cached != null) return cached;
        byte[] data = PHOTO_BYTES.get(photoId);
        if (data == null || data.length == 0) {
            long now = System.currentTimeMillis();
            if (minecraft.getConnection() != null && now - REQUESTED_AT.getOrDefault(photoId, 0L) >= 2000L) {
                REQUESTED_AT.put(photoId, now);
                while (REQUESTED_AT.size() > 256) REQUESTED_AT.remove(REQUESTED_AT.keySet().iterator().next());
                PhoneNetwork.sendAction("PHOTO_GET", photoId, "");
            }
            return null;
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
            NativeImage image = NativeImage.read(input);
            int width = Math.max(1, image.getWidth());
            int height = Math.max(1, image.getHeight());
            String name = "dm_photo/runtime_" + Integer.toUnsignedString(photoId.hashCode(), 16)
                    + "_" + Long.toUnsignedString(TEXTURE_SEQUENCE.incrementAndGet(), 36);
            ResourceLocation location = new ResourceLocation(NewPhone.MOD_ID, name);
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, false);
            minecraft.getTextureManager().register(location, texture);
            LIVE_TEXTURES.put(location, texture);
            PhotoTexture result = new PhotoTexture(location, width, height);
            TEXTURES.put(photoId, result);
            while (TEXTURES.size() > 48) {
                String oldest = TEXTURES.keySet().iterator().next();
                release(TEXTURES.remove(oldest).location());
            }
            return result;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    public static synchronized void resetSession() {
        for (ResourceLocation location : new ArrayList<>(LIVE_TEXTURES.keySet())) release(location);
        PHOTO_BYTES.clear();
        TEXTURES.clear();
        LIVE_TEXTURES.clear();
        REQUESTED_AT.clear();
    }

    private static void release(ResourceLocation location) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && location != null) {
            try { minecraft.getTextureManager().release(location); } catch (RuntimeException ignored) { }
        }
        LIVE_TEXTURES.remove(location);
    }

    public record PhotoTexture(ResourceLocation location, int width, int height) { }
}

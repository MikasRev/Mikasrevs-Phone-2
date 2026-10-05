package com.mikasrevs.newphone.network;

import com.mikasrevs.newphone.network.message.PhonePhotoUploadMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Inspect image headers before any raster/native allocation, including legacy server images. */
public final class PhoneImageLimits {
    private static final int MAX_EDGE = 4096;
    private static final long MAX_PIXELS = 4_194_304L;

    private PhoneImageLimits() { }

    public static boolean accepts(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > PhonePhotoUploadMessage.MAX_IMAGE_BYTES) return false;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!"PNG".equalsIgnoreCase(format) && !"JPEG".equalsIgnoreCase(format)) return false;
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                return width > 0 && height > 0 && width <= MAX_EDGE && height <= MAX_EDGE
                        && (long) width * height <= MAX_PIXELS;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }
}

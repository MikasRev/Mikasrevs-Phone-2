package com.mikasrevs.newphone.network;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record PhoneSharedPostData(String postId, String authorName, String caption, String imageRef) {
    public String encode() {
        return enc(postId) + "|" + enc(authorName) + "|" + enc(caption) + "|" + enc(imageRef);
    }

    public static PhoneSharedPostData decode(String value) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.split("\\|", -1);
        if (parts.length != 4) return null;
        try {
            return new PhoneSharedPostData(dec(parts[0]), dec(parts[1]), dec(parts[2]), dec(parts[3]));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String enc(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String dec(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}

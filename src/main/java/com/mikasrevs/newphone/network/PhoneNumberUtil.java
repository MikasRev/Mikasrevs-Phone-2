package com.mikasrevs.newphone.network;

/** Shared helpers for MPhone's four-digit server phone numbers. */
public final class PhoneNumberUtil {
    private PhoneNumberUtil() {
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder digits = new StringBuilder(4);
        for (int i = 0; i < raw.length() && digits.length() < 4; i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
        }
        return digits.toString();
    }

    public static String format(String raw) {
        return normalize(raw);
    }

    public static boolean isValid(String raw) {
        String normalized = normalize(raw);
        return normalized.length() == 4;
    }
}

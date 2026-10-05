package com.mikasrevs.newphone.client.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class PhoneSecurityStore {
    private static final Path SECURITY_FILE = Path.of(
            System.getProperty("user.dir"),
            "config",
            "newphone",
            "phone_security.dat"
    );
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String VERSION = "v1";

    private PhoneSecurityStore() {
    }

    public static boolean hasPin() {
        try {
            if (!Files.isRegularFile(SECURITY_FILE)) {
                return false;
            }
            String[] parts = Files.readString(SECURITY_FILE, StandardCharsets.UTF_8).trim().split(":");
            return parts.length == 3 && VERSION.equals(parts[0]);
        } catch (IOException exception) {
            return false;
        }
    }

    public static boolean savePin(String pin) {
        if (!isValidPin(pin)) {
            return false;
        }

        try {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] hash = hashPin(pin, salt);
            Files.createDirectories(SECURITY_FILE.getParent());
            String value = VERSION + ":"
                    + Base64.getEncoder().encodeToString(salt) + ":"
                    + Base64.getEncoder().encodeToString(hash);
            Files.writeString(SECURITY_FILE, value, StandardCharsets.UTF_8);
            return true;
        } catch (IOException | NoSuchAlgorithmException exception) {
            return false;
        }
    }


    public static boolean deletePin() {
        try {
            return !Files.exists(SECURITY_FILE) || Files.deleteIfExists(SECURITY_FILE);
        } catch (IOException exception) {
            return false;
        }
    }

    public static boolean verifyPin(String pin) {
        if (!isValidPin(pin)) {
            return false;
        }

        try {
            String[] parts = Files.readString(SECURITY_FILE, StandardCharsets.UTF_8).trim().split(":");
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = hashPin(pin, salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (IOException | IllegalArgumentException | NoSuchAlgorithmException exception) {
            return false;
        }
    }

    private static boolean isValidPin(String pin) {
        return pin != null && pin.matches("\\d{4}");
    }

    private static byte[] hashPin(String pin, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        digest.update(pin.getBytes(StandardCharsets.UTF_8));
        return digest.digest();
    }
}

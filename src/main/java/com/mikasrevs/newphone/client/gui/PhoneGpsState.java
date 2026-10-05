package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneGpsScreen;
import com.mikasrevs.newphone.emergency.EmergencyService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class PhoneGpsState {
    private static final int MAX_WORLD_COORDINATE = 30_000_000;
    private static final Pattern COORDINATE_NUMBER = Pattern.compile("[-+]?\\d+");

    private static boolean minimapActive;
    private static long ignoreToggleUntil;
    private static Location targetLocation;
    private static ZPhoneGpsScreen minimizedScreen;
    private static EmergencyService responderService;

    private PhoneGpsState() { }

    public static boolean isMinimapActive() { return minimapActive; }
    public static long ignoreToggleUntil() { return ignoreToggleUntil; }
    public static Location targetLocation() { return targetLocation; }
    public static boolean hasTargetLocation() { return targetLocation != null; }
    public static EmergencyService responderService() { return responderService; }

    public static void enableMinimap(Minecraft minecraft) {
        activateMinimap(minecraft, null);
    }

    public static void enableResponderMinimap(Minecraft minecraft, EmergencyService service) {
        activateMinimap(minecraft, service);
    }

    private static void activateMinimap(Minecraft minecraft, EmergencyService service) {
        if (minecraft == null || minecraft.player == null) return;
        minimizedScreen = minecraft.screen instanceof ZPhoneGpsScreen gps ? gps : null;
        responderService = service;
        minimapActive = true;
        ignoreToggleUntil = System.currentTimeMillis() + 350L;
        minecraft.setScreen(null);
    }

    public static void disableMinimap() {
        minimapActive = false;
        minimizedScreen = null;
        responderService = null;
    }

    public static void disableAndOpenGps(Minecraft minecraft) {
        minimapActive = false;
        responderService = null;
        ZPhoneGpsScreen gpsScreen = minimizedScreen == null ? new ZPhoneGpsScreen() : minimizedScreen;
        minimizedScreen = null;
        if (minecraft == null) return;
        PhoneNavigationState.replaceCurrent(minecraft, gpsScreen);
    }

    public static void openSharedLocation(Minecraft minecraft, Location location) {
        if (location == null) return;
        setTargetLocation(location);
        minimapActive = false;
        minimizedScreen = null;
        responderService = null;
        if (minecraft != null) PhoneNavigationState.open(minecraft, minecraft.screen, new ZPhoneGpsScreen());
    }

    public static void setTargetLocation(Location location) { targetLocation = location; }

    public static void clearTargetLocation() {
        targetLocation = null;
        responderService = null;
    }

    public static boolean targetMatchesDimension(Minecraft minecraft) {
        return minecraft != null && minecraft.player != null && targetLocation != null
                && currentLocation(minecraft).dimension().equals(targetLocation.dimension());
    }

    /** GPS pins identify a horizontal map position; terrain height must not block arrival. */
    public static boolean hasArrived(Minecraft minecraft) {
        if (!targetMatchesDimension(minecraft)) return false;
        Location here = currentLocation(minecraft);
        Location target = targetLocation;
        if (target == null) return false;
        long dx = (long) here.x() - target.x();
        long dz = (long) here.z() - target.z();
        return dx * dx + dz * dz <= 16L;
    }

    /** Only active navigation finishes automatically; editing a nearby pin must not close Maps. */
    public static boolean finishRouteIfArrived(Minecraft minecraft) {
        if (!minimapActive || !hasArrived(minecraft)) return false;
        minimapActive = false;
        minimizedScreen = null;
        targetLocation = null;
        responderService = null;
        ignoreToggleUntil = System.currentTimeMillis() + 700L;
        return true;
    }

    public static Location currentLocation(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return new Location("minecraft:overworld", 0, 0, 0);
        }
        return new Location(
                minecraft.player.level().dimension().location().toString(),
                Mth.floor(minecraft.player.getX()),
                Mth.floor(minecraft.player.getY()),
                Mth.floor(minecraft.player.getZ())
        );
    }

    public static String clipboardText(Location location) {
        Location safe = location == null ? new Location("minecraft:overworld", 0, 0, 0) : location;
        return PhoneSettingsStore.text("GPS Konumu - ", "GPS Location - ") + safe.friendlyDimension() + " | X: " + safe.x()
                + " Y: " + safe.y() + " Z: " + safe.z();
    }

    /** Accepts X/Z or X/Y/Z, with optional labels, commas, or whitespace. */
    public static Optional<Location> parseCoordinates(String input, Location reference) {
        if (input == null || input.isBlank()) return Optional.empty();
        Location base = reference == null
                ? new Location("minecraft:overworld", 0, 64, 0)
                : reference;
        Matcher matcher = COORDINATE_NUMBER.matcher(input);
        List<Integer> values = new ArrayList<>(3);
        while (matcher.find()) {
            if (values.size() == 3) return Optional.empty();
            try {
                long parsed = Long.parseLong(matcher.group());
                if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) return Optional.empty();
                values.add((int) parsed);
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        if (values.size() != 2 && values.size() != 3) return Optional.empty();

        int x = values.get(0);
        int y = values.size() == 3 ? values.get(1) : base.y();
        int z = values.size() == 3 ? values.get(2) : values.get(1);
        if (Math.abs((long) x) > MAX_WORLD_COORDINATE || Math.abs((long) z) > MAX_WORLD_COORDINATE) {
            return Optional.empty();
        }
        return Optional.of(new Location(base.dimension(), x, y, z));
    }

    public static double horizontalDistance(Location first, Location second) {
        if (first == null || second == null || !first.dimension().equals(second.dimension())) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.hypot((double) first.x() - second.x(), (double) first.z() - second.z());
    }

    public record Location(String dimension, int x, int y, int z) {
        public String encode() { return dimension + "|" + x + "|" + y + "|" + z; }

        public String compactCoordinates() { return "X " + x + "   Y " + y + "   Z " + z; }

        public String friendlyDimension() {
            if (dimension == null || dimension.isBlank()) return PhoneSettingsStore.text("Dünya", "World");
            return switch (dimension) {
                case "minecraft:the_nether" -> "Nether";
                case "minecraft:the_end" -> "The End";
                case "minecraft:overworld" -> "Overworld";
                default -> dimension;
            };
        }

        public static Location decode(String encoded) {
            if (encoded == null || encoded.isBlank()) {
                return new Location("minecraft:overworld", 0, 0, 0);
            }
            String[] parts = encoded.split("\\|", 4);
            if (parts.length != 4) return new Location("minecraft:overworld", 0, 0, 0);
            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                if (parts[0].isBlank() || Math.abs((long) x) > MAX_WORLD_COORDINATE
                        || Math.abs((long) z) > MAX_WORLD_COORDINATE) {
                    return new Location("minecraft:overworld", 0, 0, 0);
                }
                return new Location(parts[0], x, y, z);
            } catch (NumberFormatException ignored) {
                return new Location("minecraft:overworld", 0, 0, 0);
            }
        }
    }
}

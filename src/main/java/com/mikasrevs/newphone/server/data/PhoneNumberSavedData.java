package com.mikasrevs.newphone.server.data;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.network.PhoneNumberUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent phone identities and cosmetic choices, keyed by Minecraft UUID. */
public final class PhoneNumberSavedData extends SavedData {
    private static final String DATA_NAME = NewPhone.MOD_ID + "_phone_numbers";
    private static final int MIN_NUMBER = 1000;
    private static final int MAX_NUMBER = 9999;
    private static final int NUMBER_SPACE = MAX_NUMBER - MIN_NUMBER + 1;
    private static final int RANDOM_ATTEMPTS = 256;
    private static final Set<String> RESERVED_NUMBERS = Set.of("0001");

    private final Map<UUID, String> numberByPlayer = new HashMap<>();
    private final Map<String, UUID> playerByNumber = new HashMap<>();
    private final Map<UUID, String> phoneModels = new HashMap<>();

    public static PhoneNumberSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PhoneNumberSavedData::load,
                PhoneNumberSavedData::new,
                DATA_NAME
        );
    }

    public static PhoneNumberSavedData load(CompoundTag tag) {
        PhoneNumberSavedData data = new PhoneNumberSavedData();
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        Set<String> claimed = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            UUID playerId = entry.getUUID("Player");
            String number = PhoneNumberUtil.normalize(entry.getString("Number"));
            if (!isStoredNumberValid(number) || !claimed.add(number)) continue;
            data.numberByPlayer.put(playerId, number);
            data.playerByNumber.put(number, playerId);
        }
        ListTag models = tag.getList("PhoneModels", Tag.TAG_COMPOUND);
        for (int i = 0; i < models.size(); i++) {
            CompoundTag entry = models.getCompound(i);
            if (entry.hasUUID("Player")) {
                String id = com.mikasrevs.newphone.phone.model.PhoneModelRegistry.get(entry.getString("Model")).id();
                if (!id.equals("classic")) data.phoneModels.put(entry.getUUID("Player"), id);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, String> value : numberByPlayer.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", value.getKey());
            entry.putString("Number", value.getValue());
            entries.add(entry);
        }
        tag.put("Entries", entries);
        ListTag models = new ListTag();
        phoneModels.forEach((player, model) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putString("Model", model);
            models.add(entry);
        });
        tag.put("PhoneModels", models);
        return tag;
    }

    public String getPhoneModel(UUID player) { return phoneModels.getOrDefault(player, "classic"); }

    public boolean setPhoneModel(UUID player, String id) {
        if (com.mikasrevs.newphone.phone.model.PhoneModelRegistry.find(id).isEmpty()
                || getPhoneModel(player).equals(id)) return false;
        if (id.equals("classic")) phoneModels.remove(player);
        else phoneModels.put(player, id);
        setDirty();
        return true;
    }

    public synchronized String getOrAssign(UUID playerId) {
        String existing = numberByPlayer.get(playerId);
        if (existing != null) return existing;

        String generated = generateUniqueNumber(playerId);
        numberByPlayer.put(playerId, generated);
        playerByNumber.put(generated, playerId);
        setDirty();
        return generated;
    }

    public synchronized String getNumber(UUID playerId) {
        return numberByPlayer.get(playerId);
    }

    public synchronized UUID findOwner(String rawNumber) {
        String number = PhoneNumberUtil.normalize(rawNumber);
        if (number.length() != 4) return null;
        return playerByNumber.get(number);
    }

    private String generateUniqueNumber(UUID playerId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < RANDOM_ATTEMPTS; attempt++) {
            String candidate = formatNumber(random.nextInt(MIN_NUMBER, MAX_NUMBER + 1));
            if (isAvailable(candidate)) return candidate;
        }

        int start = MIN_NUMBER + Math.floorMod(playerId.hashCode(), NUMBER_SPACE);
        for (int offset = 0; offset < NUMBER_SPACE; offset++) {
            int value = MIN_NUMBER + Math.floorMod((start - MIN_NUMBER) + offset, NUMBER_SPACE);
            String candidate = formatNumber(value);
            if (isAvailable(candidate)) return candidate;
        }
        throw new IllegalStateException("MPhone four-digit number pool is full");
    }

    private boolean isAvailable(String candidate) {
        return !RESERVED_NUMBERS.contains(candidate) && !playerByNumber.containsKey(candidate);
    }

    private static boolean isStoredNumberValid(String number) {
        if (number.length() != 4 || RESERVED_NUMBERS.contains(number)) return false;
        try {
            int value = Integer.parseInt(number);
            return value >= MIN_NUMBER && value <= MAX_NUMBER;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String formatNumber(int value) {
        return String.format(Locale.ROOT, "%04d", value);
    }
}

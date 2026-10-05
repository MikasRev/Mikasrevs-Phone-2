package com.mikasrevs.newphone.server.data;

import com.google.gson.Gson;
import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.emergency.EmergencyService;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent UUID-based service authorization, independent of player names. */
public final class EmergencyRoleSavedData extends SavedData {
    private static final Gson GSON = new Gson();
    private static final String DATA_NAME = NewPhone.MOD_ID + "_emergency_roles";

    private Snapshot snapshot = new Snapshot();

    public static EmergencyRoleSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                EmergencyRoleSavedData::load,
                EmergencyRoleSavedData::new,
                DATA_NAME
        );
    }

    public static EmergencyRoleSavedData load(CompoundTag tag) {
        EmergencyRoleSavedData data = new EmergencyRoleSavedData();
        String json = tag.getString("Json");
        if (!json.isBlank()) {
            try {
                Snapshot decoded = GSON.fromJson(json, Snapshot.class);
                if (decoded != null) data.snapshot = normalize(decoded);
            } catch (RuntimeException ignored) {
                data.snapshot = new Snapshot();
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("Json", GSON.toJson(snapshot));
        return tag;
    }

    public synchronized EnumSet<EmergencyService> services(UUID playerId) {
        EnumSet<EmergencyService> result = EnumSet.noneOf(EmergencyService.class);
        if (playerId == null) return result;
        String encoded = snapshot.roles.getOrDefault(playerId.toString(), "");
        for (String token : encoded.split(",")) {
            EmergencyService service = EmergencyService.fromId(token);
            if (service != null) result.add(service);
        }
        return result;
    }

    public synchronized boolean canServe(UUID playerId, EmergencyService service) {
        return service != null && services(playerId).contains(service);
    }

    public synchronized void setServices(UUID playerId, EnumSet<EmergencyService> services) {
        if (playerId == null) return;
        EnumSet<EmergencyService> safe = services == null
                ? EnumSet.noneOf(EmergencyService.class)
                : EnumSet.copyOf(services);
        if (safe.isEmpty()) snapshot.roles.remove(playerId.toString());
        else snapshot.roles.put(playerId.toString(), safe.stream().map(Enum::name)
                .reduce((first, second) -> first + "," + second).orElse(""));
        setDirty();
    }

    private static Snapshot normalize(Snapshot value) {
        if (value.roles == null) value.roles = new HashMap<>();
        return value;
    }

    private static final class Snapshot {
        Map<String, String> roles = new HashMap<>();
    }
}

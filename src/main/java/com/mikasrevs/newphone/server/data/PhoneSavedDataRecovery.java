package com.mikasrevs.newphone.server.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Preserve an unreadable NBT file before Minecraft replaces it with an empty SavedData. */
public final class PhoneSavedDataRecovery {
    private PhoneSavedDataRecovery() { }

    public static <T extends SavedData> T get(MinecraftServer server, String name,
                                             Function<CompoundTag,T> loader, Supplier<T> factory) {
        var storage = server.overworld().getDataStorage();
        T data = storage.get(loader, name);
        if (data != null) return data;
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(name + ".dat");
        boolean unreadable = Files.exists(file);
        backupUnreadable(file);
        if (unreadable) {
            CompoundTag recovery = new CompoundTag(); recovery.putBoolean("UnreadableArchive", true);
            data = loader.apply(recovery);
        } else data = factory.get();
        storage.set(name, data);
        return data;
    }

    public static void backupUnreadable(Path file) {
        if (!Files.exists(file)) return;
        Path backup = file.resolveSibling(file.getFileName() + ".recovery-" + UUID.randomUUID());
        try {
            Files.copy(file, backup);
            com.mojang.logging.LogUtils.getLogger().warn("Preserved unreadable phone data at {}", backup);
        } catch (IOException failure) {
            // Never install a writable empty archive if preservation failed.
            throw new IllegalStateException("Cannot preserve unreadable phone data: " + file, failure);
        }
    }
}

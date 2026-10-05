package com.mikasrevs.newphone.server.data;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Saves one player through vanilla, including IntegratedPlayerList's host cache. */
public final class PhonePlayerStorage {
    private static final Method SAVE = ObfuscationReflectionHelper.findMethod(PlayerList.class, "m_6765_", ServerPlayer.class);
    private static final Field PLAYER_IO = ObfuscationReflectionHelper.findField(PlayerList.class, "f_11204_");
    private PhonePlayerStorage() { }

    public static void save(ServerPlayer player, String marker, String operation) throws IOException {
        try {
            SAVE.invoke(player.server.getPlayerList(), player);
            // PlayerList skips players without a connection; recovery actors use vanilla's file writer directly.
            if (player.connection == null) ((PlayerDataStorage) PLAYER_IO.get(player.server.getPlayerList())).save(player);
        }
        catch (ReflectiveOperationException | RuntimeException error) { throw new IOException("Player save failed", error); }
        Path file = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + ".dat");
        CompoundTag saved = NbtIo.readCompressed(file.toFile());
        if (!operation.equals(saved.getCompound("ForgeData").getString(marker)))
            throw new IOException("Player operation did not reach storage");
        PhoneAtomicStorage.force(file);
        if (player.server.getSingleplayerProfile() != null
                && player.getUUID().equals(player.server.getSingleplayerProfile().getId())) {
            Path level = player.server.getWorldPath(LevelResource.ROOT).resolve("level.dat");
            CompoundTag root = NbtIo.readCompressed(level.toFile());
            root.getCompound("Data").put("Player", saved.copy());
            PhoneAtomicStorage.write(level, root);
        }
    }
}

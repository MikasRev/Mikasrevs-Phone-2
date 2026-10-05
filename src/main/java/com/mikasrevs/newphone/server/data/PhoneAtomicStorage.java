package com.mikasrevs.newphone.server.data;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Durable replacement: a failed write leaves the previous committed NBT intact. */
public final class PhoneAtomicStorage {
    private PhoneAtomicStorage() { }

    public static void write(Path target, CompoundTag data) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".pending");
        NbtIo.writeCompressed(data, temp.toFile());
        force(temp);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        force(target);
    }

    public static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.force(true); }
    }
}

package com.mikasrevs.newphone.server.data;

import com.mikasrevs.newphone.NewPhone;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Binary image store for Lumi/X/Tinder assets. Keeps large PNG data out of JSON strings. */
public final class PhoneSocialAssetSavedData extends SavedData {
    private static final String DATA_NAME = NewPhone.MOD_ID + "_social_assets";
    private final LinkedHashMap<String, byte[]> images = new LinkedHashMap<>();

    public static PhoneSocialAssetSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PhoneSocialAssetSavedData::load,
                PhoneSocialAssetSavedData::new,
                DATA_NAME
        );
    }

    public static PhoneSocialAssetSavedData load(CompoundTag tag) {
        PhoneSocialAssetSavedData data = new PhoneSocialAssetSavedData();
        ListTag list = tag.getList("Images", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String ref = entry.getString("Ref");
            byte[] bytes = entry.getByteArray("Data");
            if (!ref.isBlank() && bytes.length > 0) data.images.put(ref, bytes);
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<String, byte[]> entry : images.entrySet()) {
            CompoundTag image = new CompoundTag();
            image.putString("Ref", entry.getKey());
            image.putByteArray("Data", entry.getValue());
            list.add(image);
        }
        tag.put("Images", list);
        return tag;
    }

    public synchronized void put(String ref, byte[] bytes) {
        if (ref == null || ref.isBlank() || bytes == null || bytes.length == 0) return;
        images.remove(ref);
        images.put(ref, bytes.clone());
        setDirty();
    }

    public synchronized byte[] getImage(String ref) {
        byte[] bytes = images.get(ref);
        return bytes == null ? null : bytes.clone();
    }

    public synchronized void retainOnly(Set<String> referenced) {
        if (referenced == null) return;
        if (images.keySet().removeIf(ref -> !referenced.contains(ref))) setDirty();
    }

}

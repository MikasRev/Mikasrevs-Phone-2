package com.mikasrevs.newphone.server.data;

import com.mikasrevs.newphone.NewPhone;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Binary photo storage kept outside PhoneCommunicationSavedData's JSON payload.
 * Individual byte arrays avoid the NBT UTF/string size ceiling that Base64 JSON
 * would hit quickly when real screenshots are sent through Messages.
 */
public final class PhonePhotoSavedData extends SavedData {
    private static final String DATA_NAME = NewPhone.MOD_ID + "_photo_data";

    private final LinkedHashMap<String, byte[]> photos = new LinkedHashMap<>();

    public static PhonePhotoSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PhonePhotoSavedData::load,
                PhonePhotoSavedData::new,
                DATA_NAME
        );
    }

    public static PhonePhotoSavedData load(CompoundTag tag) {
        PhonePhotoSavedData data = new PhonePhotoSavedData();
        ListTag list = tag.getList("Photos", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString("Id");
            byte[] bytes = entry.getByteArray("Data");
            if (!id.isBlank() && bytes.length > 0) {
                data.photos.put(id, bytes);
            }
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<String, byte[]> entry : photos.entrySet()) {
            CompoundTag photo = new CompoundTag();
            photo.putString("Id", entry.getKey());
            photo.putByteArray("Data", entry.getValue());
            list.add(photo);
        }
        tag.put("Photos", list);
        return tag;
    }

    public synchronized void put(String id, byte[] bytes) {
        if (id == null || id.isBlank() || bytes == null || bytes.length == 0) return;
        if (photos.containsKey(id)) return;
        photos.put(id, bytes.clone());
        setDirty();
    }

    public synchronized byte[] getPhoto(String id) {
        byte[] bytes = photos.get(id);
        return bytes == null ? null : bytes.clone();
    }

    public synchronized boolean contains(String id) {
        return id != null && photos.containsKey(id);
    }

    public synchronized void retainOnly(Iterable<String> ids) {
        java.util.Set<String> keep = new java.util.HashSet<>();
        if (ids != null) for (String id : ids) if (id != null) keep.add(id);
        if (photos.keySet().removeIf(id -> !keep.contains(id))) setDirty();
    }

}

package com.mikasrevs.newphone.server;

import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import com.mikasrevs.newphone.server.data.PhonePhotoSavedData;
import net.minecraft.server.MinecraftServer;

/** Run only on the server thread; photo uploads and their message commits are synchronous there. */
public final class PhonePhotoCleanup {
    private PhonePhotoCleanup() { }
    public static void clean(MinecraftServer server) {
        prune(PhoneCommunicationSavedData.get(server),PhonePhotoSavedData.get(server));
    }
    public static void prune(PhoneCommunicationSavedData messages,PhonePhotoSavedData photos) {
        if(messages.canCleanPhotoArchive())photos.retainOnly(messages.photoReferences());
    }
}

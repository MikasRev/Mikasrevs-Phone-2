package com.mikasrevs.newphone.network.message;

import com.mikasrevs.newphone.server.PhoneServerService;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C2S binary image upload for a DM photo. */
public final class PhonePhotoUploadMessage {
    public static final int MAX_IMAGE_BYTES = 30_000;

    private final String targetUuid;
    private final String photoId;
    private final byte[] imageData;
    private final String caption;

    public PhonePhotoUploadMessage(String targetUuid, String photoId, byte[] imageData) {
        this(targetUuid,photoId,imageData,"");
    }
    public PhonePhotoUploadMessage(String targetUuid, String photoId, byte[] imageData,String caption) {
        this.targetUuid = targetUuid == null ? "" : targetUuid;
        this.photoId = photoId == null ? "" : photoId;
        this.imageData = imageData == null ? new byte[0] : imageData;
        this.caption = caption == null ? "" : caption;
    }

    public String targetUuid() { return targetUuid; }
    public String photoId() { return photoId; }
    public byte[] imageData() { return imageData; }
    public String caption() { return caption; }

    public static void encode(PhonePhotoUploadMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.targetUuid, 64);
        buffer.writeUtf(message.photoId, 64);
        buffer.writeByteArray(message.imageData);
        buffer.writeUtf(message.caption,240);
    }

    public static PhonePhotoUploadMessage decode(FriendlyByteBuf buffer) {
        return new PhonePhotoUploadMessage(buffer.readUtf(64), buffer.readUtf(64), buffer.readByteArray(MAX_IMAGE_BYTES),buffer.readUtf(240));
    }

    public static void handle(PhonePhotoUploadMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null && com.mikasrevs.newphone.network.PhoneRequestLimits.allow(sender,
                com.mikasrevs.newphone.network.PhoneRequestLimits.Kind.PHOTO)) context.enqueueWork(() -> PhoneServerService.handlePhotoUpload(sender, message));
        context.setPacketHandled(true);
    }
}

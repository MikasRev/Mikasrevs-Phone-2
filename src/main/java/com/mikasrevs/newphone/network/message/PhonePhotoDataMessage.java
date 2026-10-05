package com.mikasrevs.newphone.network.message;

import com.mikasrevs.newphone.client.network.PhoneClientNetwork;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** S2C photo bytes for DM rendering. */
public final class PhonePhotoDataMessage {
    private final String photoId;
    private final byte[] imageData;

    public PhonePhotoDataMessage(String photoId, byte[] imageData) {
        this.photoId = photoId == null ? "" : photoId;
        this.imageData = imageData == null ? new byte[0] : imageData;
    }

    public String photoId() { return photoId; }
    public byte[] imageData() { return imageData; }

    public static void encode(PhonePhotoDataMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.photoId, 64);
        buffer.writeByteArray(message.imageData);
    }

    public static PhonePhotoDataMessage decode(FriendlyByteBuf buffer) {
        return new PhonePhotoDataMessage(buffer.readUtf(64), buffer.readByteArray(PhonePhotoUploadMessage.MAX_IMAGE_BYTES));
    }

    public static void handle(PhonePhotoDataMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> PhoneClientNetwork.receivePhoto(message.photoId, message.imageData)
        ));
        context.setPacketHandled(true);
    }
}

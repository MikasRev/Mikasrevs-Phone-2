package com.mikasrevs.newphone.network.message;

import com.mikasrevs.newphone.client.network.PhoneClientNetwork;
import java.util.Arrays;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** One compressed live-video frame relayed by the server to the other call participant. */
public final class PhoneVideoFrameDataMessage {
    private final String callId;
    private final String senderUuid;
    private final long sequence;
    private final int width;
    private final int height;
    private final byte[] jpegData;

    public PhoneVideoFrameDataMessage(String callId, String senderUuid, long sequence, int width, int height, byte[] jpegData) {
        this.callId = callId == null ? "" : callId;
        this.senderUuid = senderUuid == null ? "" : senderUuid;
        this.sequence = sequence;
        this.width = width;
        this.height = height;
        this.jpegData = jpegData == null ? new byte[0] : Arrays.copyOf(jpegData, jpegData.length);
    }

    public String callId() { return callId; }
    public String senderUuid() { return senderUuid; }
    public long sequence() { return sequence; }
    public int width() { return width; }
    public int height() { return height; }
    public byte[] jpegData() { return jpegData; }

    public static void encode(PhoneVideoFrameDataMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.callId, 64);
        buffer.writeUtf(message.senderUuid, 64);
        buffer.writeVarLong(message.sequence);
        buffer.writeVarInt(message.width);
        buffer.writeVarInt(message.height);
        buffer.writeByteArray(message.jpegData);
    }

    public static PhoneVideoFrameDataMessage decode(FriendlyByteBuf buffer) {
        return new PhoneVideoFrameDataMessage(
                buffer.readUtf(64),
                buffer.readUtf(64),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readByteArray(PhoneVideoFrameUploadMessage.MAX_FRAME_BYTES)
        );
    }

    public static void handle(PhoneVideoFrameDataMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> PhoneClientNetwork.receiveVideoFrame(message)));
        context.setPacketHandled(true);
    }
}

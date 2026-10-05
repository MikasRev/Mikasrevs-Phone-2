package com.mikasrevs.newphone.network.message;

import com.mikasrevs.newphone.server.PhoneServerService;
import java.util.Arrays;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** One compressed live-video frame sent from the client to its active call peer. */
public final class PhoneVideoFrameUploadMessage {
    public static final int MAX_FRAME_BYTES = 30_000;
    public static final int FRAME_WIDTH = 256;
    public static final int FRAME_HEIGHT = 144;
    public static final int MAX_FRAME_WIDTH = FRAME_WIDTH;
    public static final int MAX_FRAME_HEIGHT = FRAME_HEIGHT;

    private final String callId;
    private final String targetUuid;
    private final long sequence;
    private final int width;
    private final int height;
    private final byte[] jpegData;

    public PhoneVideoFrameUploadMessage(String callId, String targetUuid, long sequence, int width, int height, byte[] jpegData) {
        this.callId = callId == null ? "" : callId;
        this.targetUuid = targetUuid == null ? "" : targetUuid;
        this.sequence = sequence;
        this.width = width;
        this.height = height;
        this.jpegData = jpegData == null ? new byte[0] : Arrays.copyOf(jpegData, jpegData.length);
    }

    public String callId() { return callId; }
    public String targetUuid() { return targetUuid; }
    public long sequence() { return sequence; }
    public int width() { return width; }
    public int height() { return height; }
    public byte[] jpegData() { return jpegData; }

    public static void encode(PhoneVideoFrameUploadMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.callId, 64);
        buffer.writeUtf(message.targetUuid, 64);
        buffer.writeVarLong(message.sequence);
        buffer.writeVarInt(message.width);
        buffer.writeVarInt(message.height);
        buffer.writeByteArray(message.jpegData);
    }

    public static PhoneVideoFrameUploadMessage decode(FriendlyByteBuf buffer) {
        return new PhoneVideoFrameUploadMessage(
                buffer.readUtf(64),
                buffer.readUtf(64),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readByteArray(MAX_FRAME_BYTES)
        );
    }

    public static void handle(PhoneVideoFrameUploadMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null && com.mikasrevs.newphone.network.PhoneRequestLimits.allow(sender,
                com.mikasrevs.newphone.network.PhoneRequestLimits.Kind.VIDEO)) context.enqueueWork(() -> PhoneServerService.handleVideoFrameUpload(sender, message));
        context.setPacketHandled(true);
    }
}

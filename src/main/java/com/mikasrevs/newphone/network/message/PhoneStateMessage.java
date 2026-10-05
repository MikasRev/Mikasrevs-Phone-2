package com.mikasrevs.newphone.network.message;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import com.mikasrevs.newphone.client.network.PhoneClientNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server-to-client JSON snapshot for phone communication state. */
public final class PhoneStateMessage {
    public static final int MAX_CHUNK_LENGTH = 30_000;
    // Covers any Java String without imposing a quota on stored conversations.
    public static final int MAX_CHUNK_COUNT = 100_000;
    private static final int MAX_TRANSFER_ID_LENGTH = 36;

    private final String transferId;
    private final int chunkIndex;
    private final int chunkCount;
    private final String jsonChunk;

    public PhoneStateMessage(String json) {
        this("", 0, 1, json);
    }

    public String transferId() { return transferId; }
    public int chunkIndex() { return chunkIndex; }
    public int chunkCount() { return chunkCount; }
    public String jsonChunk() { return jsonChunk; }

    private PhoneStateMessage(String transferId, int chunkIndex, int chunkCount, String jsonChunk) {
        this.transferId = transferId == null ? "" : transferId;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.jsonChunk = jsonChunk == null ? "" : jsonChunk;
    }

    public static List<PhoneStateMessage> split(String json) {
        String safeJson = json == null ? "" : json;
        if (safeJson.length() <= MAX_CHUNK_LENGTH) return List.of(new PhoneStateMessage(safeJson));

        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < safeJson.length();) {
            int end = Math.min(safeJson.length(), start + MAX_CHUNK_LENGTH);
            if (end < safeJson.length()
                    && Character.isHighSurrogate(safeJson.charAt(end - 1))
                    && Character.isLowSurrogate(safeJson.charAt(end))) {
                end--;
            }
            chunks.add(safeJson.substring(start, end));
            start = end;
        }
        if (chunks.size() > MAX_CHUNK_COUNT) {
            throw new IllegalArgumentException("Phone state exceeds the maximum chunk count: " + chunks.size());
        }

        String transferId = UUID.randomUUID().toString();
        List<PhoneStateMessage> messages = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            messages.add(new PhoneStateMessage(transferId, i, chunks.size(), chunks.get(i)));
        }
        return messages;
    }

    public static void encode(PhoneStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.transferId, MAX_TRANSFER_ID_LENGTH);
        buffer.writeVarInt(message.chunkIndex);
        buffer.writeVarInt(message.chunkCount);
        buffer.writeUtf(message.jsonChunk, MAX_CHUNK_LENGTH);
    }

    public static PhoneStateMessage decode(FriendlyByteBuf buffer) {
        return new PhoneStateMessage(
                buffer.readUtf(MAX_TRANSFER_ID_LENGTH),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(MAX_CHUNK_LENGTH)
        );
    }

    public static void handle(PhoneStateMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> PhoneClientNetwork.receiveStateChunk(
                        message.transferId, message.chunkIndex, message.chunkCount, message.jsonChunk
                )
        ));
        context.setPacketHandled(true);
    }
}

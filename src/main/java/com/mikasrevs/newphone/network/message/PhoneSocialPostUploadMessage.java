package com.mikasrevs.newphone.network.message;

import com.mikasrevs.newphone.server.PhoneServerService;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C2S new Lumi/X post or Lumi story with an optional image. */
public final class PhoneSocialPostUploadMessage {
    /**
     * Serverbound custom payloads in 1.20.1 are capped at 32,767 bytes. Keep a
     * little headroom for the SimpleChannel discriminator and packet metadata.
     */
    private static final int SAFE_SERVERBOUND_PAYLOAD_BYTES = 30_720;
    private static final int PACKET_OVERHEAD_BYTES = 96;
    public static final int MAX_IMAGE_BYTES = 30_000;

    private final String platform;
    private final String body;
    private final byte[] imageData;

    public PhoneSocialPostUploadMessage(String platform, String body, byte[] imageData) {
        this.platform = platform == null ? "" : platform;
        this.body = body == null ? "" : body;
        this.imageData = imageData == null ? new byte[0] : imageData;
    }

    public String platform() { return platform; }
    public String body() { return body; }
    public byte[] imageData() { return imageData; }

    /** Maximum image bytes that still keep this particular post safely under the C2S payload cap. */
    public static int maxImageBytesFor(String platform, String body) {
        int platformBytes = utf8Length(platform);
        int bodyBytes = utf8Length(body);
        int remaining = SAFE_SERVERBOUND_PAYLOAD_BYTES - PACKET_OVERHEAD_BYTES - platformBytes - bodyBytes;
        return Math.max(0, Math.min(MAX_IMAGE_BYTES, remaining));
    }

    public static boolean fitsPayload(String platform, String body, byte[] imageData) {
        int length = imageData == null ? 0 : imageData.length;
        return length <= maxImageBytesFor(platform, body);
    }

    private static int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    public static void encode(PhoneSocialPostUploadMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.platform, 24);
        buffer.writeUtf(message.body, 1024);
        buffer.writeByteArray(message.imageData);
    }

    public static PhoneSocialPostUploadMessage decode(FriendlyByteBuf buffer) {
        return new PhoneSocialPostUploadMessage(
                buffer.readUtf(24),
                buffer.readUtf(1024),
                buffer.readByteArray(MAX_IMAGE_BYTES)
        );
    }

    public static void handle(PhoneSocialPostUploadMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null && com.mikasrevs.newphone.network.PhoneRequestLimits.allow(sender,
                com.mikasrevs.newphone.network.PhoneRequestLimits.Kind.PHOTO)) context.enqueueWork(() -> PhoneServerService.handleSocialPostUpload(sender, message));
        context.setPacketHandled(true);
    }
}

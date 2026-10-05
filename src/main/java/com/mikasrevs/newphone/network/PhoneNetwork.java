package com.mikasrevs.newphone.network;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.network.message.PhoneActionMessage;
import com.mikasrevs.newphone.network.message.PhoneStateMessage;
import com.mikasrevs.newphone.network.message.PhonePhotoUploadMessage;
import com.mikasrevs.newphone.network.message.PhonePhotoDataMessage;
import com.mikasrevs.newphone.network.message.PhoneSocialStateMessage;
import com.mikasrevs.newphone.network.message.PhoneSocialPostUploadMessage;
import com.mikasrevs.newphone.network.message.PhoneSocialAssetUploadMessage;
import com.mikasrevs.newphone.network.message.PhoneVideoFrameUploadMessage;
import com.mikasrevs.newphone.network.message.PhoneVideoFrameDataMessage;
import com.mikasrevs.newphone.network.message.PhoneBankStateMessage;
import com.mikasrevs.newphone.network.message.EmergencyStateMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge 1.20.1 SimpleChannel equivalent of the modern MPhone payload layer. */
public final class PhoneNetwork {
    private static final String PROTOCOL_VERSION = "17";
    private static int nextPacketId;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NewPhone.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private PhoneNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextPacketId++, com.mikasrevs.newphone.marketplace.trade.TradeStatePacket.class,
                com.mikasrevs.newphone.marketplace.trade.TradeStatePacket::encode,
                com.mikasrevs.newphone.marketplace.trade.TradeStatePacket::decode,
                com.mikasrevs.newphone.marketplace.trade.TradeStatePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextPacketId++, com.mikasrevs.newphone.network.message.PhoneModerationStateMessage.class,
                com.mikasrevs.newphone.network.message.PhoneModerationStateMessage::encode,
                com.mikasrevs.newphone.network.message.PhoneModerationStateMessage::decode,
                com.mikasrevs.newphone.network.message.PhoneModerationStateMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextPacketId++, com.mikasrevs.newphone.network.message.PhoneModelMessage.class,
                com.mikasrevs.newphone.network.message.PhoneModelMessage::encode,
                com.mikasrevs.newphone.network.message.PhoneModelMessage::decode,
                com.mikasrevs.newphone.network.message.PhoneModelMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextPacketId++, com.mikasrevs.newphone.marketplace.network.MarketplaceStatePacket.class,
                com.mikasrevs.newphone.marketplace.network.MarketplaceStatePacket::encode,
                com.mikasrevs.newphone.marketplace.network.MarketplaceStatePacket::decode,
                com.mikasrevs.newphone.marketplace.network.MarketplaceStatePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextPacketId++,com.mikasrevs.newphone.network.message.PhoneVoicePacket.class,
                com.mikasrevs.newphone.network.message.PhoneVoicePacket::encode,
                com.mikasrevs.newphone.network.message.PhoneVoicePacket::decode,
                com.mikasrevs.newphone.network.message.PhoneVoicePacket::handle);
        CHANNEL.registerMessage(
                nextPacketId++,
                PhoneActionMessage.class,
                PhoneActionMessage::encode,
                PhoneActionMessage::decode,
                PhoneActionMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                nextPacketId++,
                PhoneStateMessage.class,
                PhoneStateMessage::encode,
                PhoneStateMessage::decode,
                PhoneStateMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                nextPacketId++,
                PhonePhotoUploadMessage.class,
                PhonePhotoUploadMessage::encode,
                PhonePhotoUploadMessage::decode,
                PhonePhotoUploadMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                nextPacketId++,
                PhonePhotoDataMessage.class,
                PhonePhotoDataMessage::encode,
                PhonePhotoDataMessage::decode,
                PhonePhotoDataMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                nextPacketId++,
                PhoneSocialStateMessage.class,
                PhoneSocialStateMessage::encode,
                PhoneSocialStateMessage::decode,
                PhoneSocialStateMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                nextPacketId++,
                PhoneSocialPostUploadMessage.class,
                PhoneSocialPostUploadMessage::encode,
                PhoneSocialPostUploadMessage::decode,
                PhoneSocialPostUploadMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                nextPacketId++,
                PhoneSocialAssetUploadMessage.class,
                PhoneSocialAssetUploadMessage::encode,
                PhoneSocialAssetUploadMessage::decode,
                PhoneSocialAssetUploadMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                nextPacketId++,
                PhoneVideoFrameUploadMessage.class,
                PhoneVideoFrameUploadMessage::encode,
                PhoneVideoFrameUploadMessage::decode,
                PhoneVideoFrameUploadMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                nextPacketId++,
                PhoneVideoFrameDataMessage.class,
                PhoneVideoFrameDataMessage::encode,
                PhoneVideoFrameDataMessage::decode,
                PhoneVideoFrameDataMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                nextPacketId++,
                PhoneBankStateMessage.class,
                PhoneBankStateMessage::encode,
                PhoneBankStateMessage::decode,
                PhoneBankStateMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                nextPacketId++,
                EmergencyStateMessage.class,
                EmergencyStateMessage::encode,
                EmergencyStateMessage::decode,
                EmergencyStateMessage::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static void sendAction(String action, String targetUuid, String body) {
        CHANNEL.sendToServer(new PhoneActionMessage(action, targetUuid, body));
    }

    public static void sendState(ServerPlayer player, String json) {
        for (PhoneStateMessage message : PhoneStateMessage.split(json)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    public static void sendBankState(ServerPlayer player, String json) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PhoneBankStateMessage(json));
    }

    public static void sendEmergencyState(ServerPlayer player, String json) {
        if (player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EmergencyStateMessage(json));
    }

    public static void sendPhotoUpload(String targetUuid, String photoId, byte[] imageData) {
        if (imageData == null || imageData.length == 0 || imageData.length > PhonePhotoUploadMessage.MAX_IMAGE_BYTES) return;
        CHANNEL.sendToServer(new PhonePhotoUploadMessage(targetUuid, photoId, imageData));
    }

    public static void sendPhotoData(ServerPlayer player, String photoId, byte[] imageData) {
        if (player == null || imageData == null || imageData.length == 0
                || imageData.length > PhonePhotoUploadMessage.MAX_IMAGE_BYTES) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PhonePhotoDataMessage(photoId, imageData));
    }

    public static void sendSocialState(ServerPlayer player, String json) {
        for (PhoneSocialStateMessage message : PhoneSocialStateMessage.split(json)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    public static void sendSocialPostUpload(String platform, String body, byte[] imageData) {
        byte[] safeImage = imageData == null ? new byte[0] : imageData;
        if (!PhoneSocialPostUploadMessage.fitsPayload(platform, body, safeImage)) return;
        CHANNEL.sendToServer(new PhoneSocialPostUploadMessage(platform, body, safeImage));
    }

    public static void sendSocialAssetUpload(String kind, byte[] imageData) {
        if (imageData == null || imageData.length == 0 || imageData.length > PhoneSocialAssetUploadMessage.MAX_IMAGE_BYTES) return;
        CHANNEL.sendToServer(new PhoneSocialAssetUploadMessage(kind, imageData));
    }

    public static void sendVideoFrameUpload(String callId, String targetUuid, long sequence, int width, int height, byte[] jpegData) {
        if (jpegData == null || jpegData.length == 0 || jpegData.length > PhoneVideoFrameUploadMessage.MAX_FRAME_BYTES) return;
        CHANNEL.sendToServer(new PhoneVideoFrameUploadMessage(callId, targetUuid, sequence, width, height, jpegData));
    }

    public static void sendVideoFrameData(ServerPlayer player, String callId, String senderUuid, long sequence,
                                          int width, int height, byte[] jpegData) {
        if (player == null || jpegData == null || jpegData.length == 0
                || jpegData.length > PhoneVideoFrameUploadMessage.MAX_FRAME_BYTES) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PhoneVideoFrameDataMessage(callId, senderUuid, sequence, width, height, jpegData));
    }
}

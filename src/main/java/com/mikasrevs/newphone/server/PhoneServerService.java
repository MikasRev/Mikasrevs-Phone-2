package com.mikasrevs.newphone.server;

import com.google.gson.Gson;
import com.mikasrevs.newphone.ZPhoneItem;
import com.mikasrevs.newphone.integration.currency.PhoneCurrencyIntegration;
import com.mikasrevs.newphone.integration.currency.PhoneCurrencyManager;
import com.mikasrevs.newphone.integration.voice.PhoneVoiceManager;
import com.mikasrevs.newphone.network.PhoneBankStateData;
import com.mikasrevs.newphone.network.PhoneBankTransferRequest;
import com.mikasrevs.newphone.network.PhoneStateData;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import com.mikasrevs.newphone.moderation.PhoneModerationManager;
import com.mikasrevs.newphone.moderation.PhoneModerationData;
import com.mikasrevs.newphone.network.PhoneNetwork;
import com.mikasrevs.newphone.network.message.PhoneActionMessage;
import com.mikasrevs.newphone.network.message.PhonePhotoUploadMessage;
import com.mikasrevs.newphone.network.message.PhoneSocialPostUploadMessage;
import com.mikasrevs.newphone.network.message.PhoneSocialAssetUploadMessage;
import com.mikasrevs.newphone.network.message.PhoneVideoFrameUploadMessage;
import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import com.mikasrevs.newphone.server.data.PhoneNumberSavedData;
import com.mikasrevs.newphone.server.data.PhonePhotoSavedData;
import com.mikasrevs.newphone.server.data.PhoneSocialSavedData;
import com.mikasrevs.newphone.server.emergency.EmergencyDispatchManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Forge 1.20.1 communication core. Stage 1 ports sync, contacts, blocking and text
 * messages; calls/social/media are layered on top in later stages.
 */
public final class PhoneServerService {
    private static final Gson GSON = new Gson();
    private static final Map<String, TypingState> TYPING = new HashMap<>();
    private static final Map<String, Long> LAST_SOCIAL_UPLOAD_TICK = new HashMap<>();
    private static final long SOCIAL_UPLOAD_COOLDOWN_TICKS = 160L;
    private static final long RING_TIMEOUT_MS = 15_000L;
    private static final int MAX_CALL_HISTORY_PER_PLAYER = 50;
    private static final Map<UUID, ServerCall> CALLS = new HashMap<>();
    private static final Map<UUID, Long> SENT_MESSAGE_REVISIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_VIDEO_FRAME_AT = new HashMap<>();
    private static final Map<UUID, Long> LAST_VIDEO_SEQUENCE = new HashMap<>();
    private static final long VIDEO_FRAME_MIN_INTERVAL_MS = 70L;
    private static final Map<String, Long> LAST_ACTION_AT = new HashMap<>();
    private static final long ATTACHMENT_MIN_INTERVAL_MS = 750L;
    private static final long CONTACT_REQUEST_MIN_INTERVAL_MS = 2_500L;
    private static final long TYPING_MIN_INTERVAL_MS = 280L;
    private static final long CALL_START_MIN_INTERVAL_MS = 2_500L;
    private static final long SOCIAL_INTERACTION_MIN_INTERVAL_MS = 220L;
    private static final long FOLLOW_REQUEST_MIN_INTERVAL_MS = 1_500L;
    private static final long TINDER_SWIPE_MIN_INTERVAL_MS = 280L;
    private static final long PROFILE_ASSET_MIN_INTERVAL_MS = 1_500L;
    private static final long BANK_TRANSFER_MIN_INTERVAL_MS = 750L;
    private static final long BANK_SYNC_MIN_INTERVAL_MS = 350L;
    private static final long BANK_REQUEST_TTL_MS = 300_000L;
    private static final Map<MinecraftServer, Map<String, CachedBankTransfer>> BANK_REQUEST_RESULTS = new java.util.WeakHashMap<>();

    private PhoneServerService() {
    }

    public static void handleAction(ServerPlayer sender, PhoneActionMessage message) {
        if (sender == null || sender.server == null) return;
        String action = message.action().trim().toUpperCase(Locale.ROOT);
        if (action.equals("MARKET_TRADE")) { com.mikasrevs.newphone.marketplace.trade.TradeManager.handle(sender,message.body()); return; }
        if (action.startsWith("MOD_")) { PhoneModerationManager.handle(sender, action, message.targetUuid(), message.body()); return; }
        if (!PhoneModerationManager.require(sender, PhoneModerationData.actionPlatform(action, message.body()))) return;
        if (action.equals("MARKETPLACE")) {
            com.mikasrevs.newphone.marketplace.MarketplaceManager.handle(sender, message.body()); return;
        }
        if (action.startsWith("EMERGENCY_")) {
            EmergencyDispatchManager.handleAction(sender, action, message.targetUuid(), message.body());
            return;
        }
        if(action.startsWith("GROUP_")){PhoneGroupService.handle(sender,action,message.targetUuid(),message.body());return;}
        if (Set.of("SEND_MESSAGE","HISTORY_PAGE","DELIVERY_ACK").contains(action)) {
            PhoneMessagingService.handle(sender,action,message.targetUuid(),message.body()); return;
        }
        switch (action) {
            case "PHONE_MODEL" -> {
                if (!rateLimited(sender, "PHONE_MODEL", 150L)) PhoneModelSelectionService.select(sender, message.body());
            }
            case "SYNC" -> sendState(sender, "");
            case "SYNC_FULL" -> sendFullState(sender, "");
            case "PHOTO_GET" -> sendRequestedPhoto(sender, message.targetUuid());
            case "VOICE_GET" -> {
                if(!rateLimited(sender,"VOICE_GET",500L))PhoneVoiceStorage.download(sender,message.targetUuid());
                else PhoneVoiceStorage.error(sender,message.targetUuid(),"Biraz bekleyip yeniden dokun.");
            }
            case "VOICE_CANCEL" -> PhoneVoiceStorage.cancelDownload(sender);
            case "SOCIAL_SYNC" -> sendSocialStateMetadata(sender);
            case "SOCIAL_SYNC_FULL" -> sendSocialState(sender);
            case "SOCIAL_MANAGE_POST" -> {
                String[] parts=message.body().split("\\|",2);
                if(parts.length==2&&Set.of("ARCHIVE","RESTORE","DELETE").contains(parts[1])) {
                    boolean success=PhoneSocialSavedData.get(sender.server).managePost(sender.getUUID(),parts[0],message.targetUuid(),parts[1]);
                    sendState(sender,success?"Gönderi güncellendi.":"İşlem yapılamadı. Profilde 50 gönderi varsa önce yer aç.");
                    broadcastSocialStateMetadata(sender.server);
                }
            }
            case "SOCIAL_PROFILE_ASSETS" -> sendSocialProfileAssets(sender, message.targetUuid());
            case "TINDER_BIO" -> updateTinderBio(sender, message.body());
            case "TINDER_ENABLED" -> updateTinderEnabled(sender, message.body());
            case "TINDER_SETTINGS" -> updateTinderSettings(sender, message.body());
            case "TINDER_PHOTO_CLEAR" -> clearTinderPhoto(sender);
            case "TINDER_SWIPE" -> handleTinderSwipe(sender, message.targetUuid(), message.body());
            case "TINDER_RESET_PASSES" -> resetTinderPasses(sender);
            case "SOCIAL_GRAM_BIO" -> updateSocialGramBio(sender, message.body());
            case "SOCIAL_GRAM_PHOTO_CLEAR" -> clearSocialGramPhoto(sender);
            case "SOCIAL_GRAM_TOGGLE_LIKE" -> handleSocialInteraction(sender, "BLOCKGRAM", "LIKE", message.targetUuid(), "", "");
            case "SOCIAL_GRAM_COMMENT" -> handleSocialInteraction(sender, "BLOCKGRAM", "COMMENT", message.targetUuid(), "", message.body());
            case "SOCIAL_GRAM_COMMENT_LIKE" -> handleSocialInteraction(sender, "BLOCKGRAM", "COMMENT_LIKE", message.targetUuid(), "", "");
            case "SOCIAL_GRAM_SHARE" -> handleSocialInteraction(sender, "BLOCKGRAM", "SHARE", message.targetUuid(), "", "");
            case "SOCIAL_GRAM_DELETE" -> handleSocialGramDelete(sender, message.targetUuid());
            case "SOCIAL_GRAM_STORY_VIEW" -> handleSocialStoryView(sender, message.targetUuid());
            case "SOCIAL_POST_TOGGLE_LIKE" -> handleSocialInteraction(sender, "BLOCKPOST", "LIKE", message.targetUuid(), "", "");
            case "SOCIAL_POST_TOGGLE_REPOST" -> handleSocialInteraction(sender, "BLOCKPOST", "REPOST", message.targetUuid(), "", "");
            case "SOCIAL_POST_COMMENT" -> handleSocialInteraction(sender, "BLOCKPOST", "COMMENT", message.targetUuid(), "", message.body());
            case "SOCIAL_POST_REPLY" -> handleSocialReply(sender, message.targetUuid(), message.body());
            case "SOCIAL_POST_COMMENT_LIKE" -> handleSocialInteraction(sender, "BLOCKPOST", "COMMENT_LIKE", message.targetUuid(), "", "");
            case "SOCIAL_POST_DELETE" -> handleSocialPostDelete(sender, message.targetUuid());
            case "SOCIAL_POST_BIO" -> updateSocialPostBio(sender, message.body());
            case "SOCIAL_FOLLOW_REQUEST" -> handleSocialFollowRequest(sender, message.body(), message.targetUuid());
            case "SOCIAL_FOLLOW_ACCEPT" -> handleSocialFollowDecision(sender, message.body(), message.targetUuid(), true);
            case "SOCIAL_FOLLOW_ACCEPT_BACK" -> handleSocialFollowAcceptBack(sender, message.body(), message.targetUuid());
            case "SOCIAL_FOLLOW_DECLINE" -> handleSocialFollowDecision(sender, message.body(), message.targetUuid(), false);
            case "SOCIAL_FOLLOW_REMOVE" -> handleSocialFollowRemove(sender, message.body(), message.targetUuid());
            case "SOCIAL_NOTIFICATIONS_READ" -> handleSocialNotificationsRead(sender, message.body());
            case "SOCIAL_NOTIFICATIONS_CLEAR" -> handleSocialNotificationsClear(sender, message.body());
            case "FRIEND_REQUEST" -> sendFriendRequest(sender, message.body());
            case "ACCEPT_FRIEND_REQUEST" -> acceptFriendRequest(sender, message.targetUuid());
            case "DECLINE_FRIEND_REQUEST" -> declineFriendRequest(sender, message.targetUuid());
            case "CANCEL_FRIEND_REQUEST" -> cancelFriendRequest(sender, message.targetUuid());
            case "REMOVE_CONTACT" -> removeContact(sender, message.targetUuid());
            case "BLOCK_CONTACT" -> blockContact(sender, message.targetUuid());
            case "UNBLOCK_CONTACT" -> unblockContact(sender, message.targetUuid());
            case "SEND_TEXT" -> sendText(sender, message.targetUuid(), message.body(), "", "TEXT");
            case "SEND_GRAM_TEXT" -> sendText(sender, message.targetUuid(), message.body(), "", "GRAM_TEXT");
            case "SEND_TINDER_TEXT" -> sendText(sender, message.targetUuid(), message.body(), "", "TINDER_TEXT");
            case "SEND_GRAM_POST" -> sendGramPost(sender, message.targetUuid(), message.body());
            case "SEND_REPLY" -> sendReply(sender, message.targetUuid(), message.body(), false);
            case "SEND_GRAM_REPLY" -> sendReply(sender, message.targetUuid(), message.body(), true);
            case "SEND_LOCATION" -> sendAttachment(sender, message.targetUuid(), "LOCATION", message.body());
            case "DELETE_MESSAGE" -> deleteMessage(sender, message.targetUuid(), false);
            case "DELETE_GRAM_MESSAGE" -> deleteMessage(sender, message.targetUuid(), true);
            case "DELETE_TINDER_MESSAGE" -> deleteMessageInChannel(sender, message.targetUuid(), "TINDER");
            case "MARK_READ" -> markRead(sender, message.targetUuid(), false);
            case "MARK_GRAM_READ" -> markRead(sender, message.targetUuid(), true);
            case "MARK_TINDER_READ" -> markReadInChannel(sender, message.targetUuid(), "TINDER");
            case "TYPING" -> updateTyping(sender, message.targetUuid(), message.body());
            case "CALL_START" -> startCall(sender, message.targetUuid(), false);
            case "CALL_START_VIDEO" -> startCall(sender, message.targetUuid(), true);
            case "CALL_VIDEO_CAMERA_STATE" -> updateVideoCameraState(sender, message.targetUuid(), message.body());
            case "CALL_ACCEPT" -> acceptVoiceCall(sender, message.targetUuid());
            case "CALL_DECLINE" -> declineVoiceCall(sender, message.targetUuid());
            case "CALL_END" -> endVoiceCallFromClient(sender, message.targetUuid());
            case "CALL_POSE_START" -> updateCallPose(sender, message.targetUuid(), true);
            case "CALL_POSE_STOP" -> updateCallPose(sender, message.targetUuid(), false);
            case "BANK_SYNC" -> {
                if (!rateLimited(sender, "BANK_SYNC", BANK_SYNC_MIN_INTERVAL_MS)) {
                    sendBankState(sender, "", "", "", "");
                }
            }
            case "BANK_TRANSFER" -> handleBankTransfer(sender, message.targetUuid(), message.body());
            default -> { }
        }
    }

    private static void handleBankTransfer(ServerPlayer sender, String targetRaw, String body) {
        if (rateLimited(sender, "BANK_TRANSFER", BANK_TRANSFER_MIN_INTERVAL_MS)) return;
        PhoneBankTransferRequest request = PhoneBankTransferRequest.decode(body);
        if (request == null) {
            sendBankState(sender, "INVALID_AMOUNT", "", "", "");
            return;
        }
        CachedBankTransfer cached = cachedBankTransfer(sender, request.requestId);
        if (cached != null) {
            sendBankState(sender, cached.resultCode, cached.peerName, cached.amountText, request.requestId);
            return;
        }

        UUID targetId = parseUuid(targetRaw);
        if (targetId == null) {
            completeBankRequest(sender, request.requestId, "INVALID_TARGET", "", "");
            return;
        }
        if (targetId.equals(sender.getUUID())) {
            completeBankRequest(sender, request.requestId, "SELF_TRANSFER", sender.getGameProfile().getName(), "");
            return;
        }

        PhoneCommunicationSavedData.Snapshot phoneState =
                PhoneCommunicationSavedData.get(sender.server).copySnapshot();
        String self = sender.getUUID().toString();
        String target = targetId.toString();
        if (!phoneState.contacts.getOrDefault(self, List.of()).contains(target)
                || isBlockedEither(sender.server, sender.getUUID(), targetId)) {
            completeBankRequest(sender, request.requestId, "NOT_CONTACT", "", "");
            return;
        }

        ServerPlayer onlineTarget = sender.server.getPlayerList().getPlayer(targetId);
        String targetName = onlineTarget != null ? onlineTarget.getGameProfile().getName()
                : phoneState.knownNames.getOrDefault(target, "Player");
        PhoneCurrencyIntegration.TransferResult result = PhoneCurrencyManager.transfer(
                sender, targetId, request.currencyId, request.coreAmount, request.requestId
        );
        String resultCode = bankResultCode(result);
        if (result.status() == PhoneCurrencyIntegration.TransferStatus.PENDING)
            sendBankState(sender, resultCode, targetName, result.amountText(), request.requestId);
        else completeBankRequest(sender, request.requestId, resultCode, targetName, result.amountText());
        if (result.success() && onlineTarget != null) {
            sendBankState(onlineTarget, "RECEIVED", sender.getGameProfile().getName(), result.amountText(), "");
        }
    }

    private static String bankResultCode(PhoneCurrencyIntegration.TransferResult result) {
        return switch (result.status()) {
            case SUCCESS -> "SENT";
            case UNAVAILABLE -> "MOD_MISSING";
            case INVALID_AMOUNT -> "INVALID_AMOUNT";
            case INSUFFICIENT_FUNDS -> "INSUFFICIENT_FUNDS";
            case ACCOUNT_UNAVAILABLE -> "ACCOUNT_UNAVAILABLE";
            case TARGET_OFFLINE -> "TARGET_OFFLINE";
            case FAILED -> "FAILED";
            case PENDING -> "PENDING";
        };
    }

    private static CachedBankTransfer cachedBankTransfer(ServerPlayer sender, String requestId) {
        long now = System.currentTimeMillis();
        var results = BANK_REQUEST_RESULTS.computeIfAbsent(sender.server, ignored -> new HashMap<>());
        results.entrySet().removeIf(entry -> now - entry.getValue().completedAt > BANK_REQUEST_TTL_MS);
        String key = sender.getUUID() + "|" + requestId;
        CachedBankTransfer cached = results.get(key);
        if (cached != null) return cached;
        try {
            var receipt = com.mikasrevs.newphone.integration.currency.PhoneBankJournal.get(sender.server)
                    .find(sender.getUUID(), requestId);
            if (receipt == null || receipt.result() == null) return null;
            ServerPlayer target = sender.server.getPlayerList().getPlayer(receipt.target);
            String name = target != null ? target.getGameProfile().getName()
                    : PhoneCommunicationSavedData.get(sender.server).copyMetadata().knownNames.getOrDefault(receipt.target.toString(), "Player");
            cached = new CachedBankTransfer(bankResultCode(receipt.result()), name, receipt.result().amountText(), now);
            results.put(key, cached);
            return cached;
        } catch (java.io.IOException error) {
            com.mojang.logging.LogUtils.getLogger().warn("Bank receipt lookup unavailable", error);
            return new CachedBankTransfer("PENDING", "", "", now);
        }
    }

    private static void completeBankRequest(ServerPlayer sender, String requestId, String resultCode,
                                            String peerName, String amountText) {
        String key = sender.getUUID() + "|" + requestId;
        BANK_REQUEST_RESULTS.computeIfAbsent(sender.server, ignored -> new HashMap<>()).put(key, new CachedBankTransfer(
                resultCode, peerName, amountText, System.currentTimeMillis()
        ));
        sendBankState(sender, resultCode, peerName, amountText, requestId);
    }


    public static void handlePhotoUpload(ServerPlayer sender, PhonePhotoUploadMessage message) {
        if(sender==null||message==null)return;
        if(message.photoId().startsWith("market-")){com.mikasrevs.newphone.marketplace.MarketplaceManager.upload(sender,message);return;}
        if(message.photoId().startsWith("avatar-")){PhoneGroupService.avatar(sender,message);return;}
        var request=new com.mikasrevs.newphone.network.PhoneSendRequest();
        boolean group=message.photoId().startsWith("group-");
        boolean gram=message.photoId().startsWith("gram-");
        request.id=group?message.photoId().substring(6):gram?message.photoId().substring(5):message.photoId();
        request.peer=message.targetUuid();request.type=group?"GROUP_PHOTO":gram?"GRAM_PHOTO":"PHOTO";request.caption=message.caption();
        PhoneMessagingService.send(sender,request,message.imageData());
    }

    private static void sendAttachment(ServerPlayer sender, String rawUuid, String type, String rawBody) {
        UUID targetId = parseUuid(rawUuid);
        String body = rawBody == null ? "" : rawBody.trim();
        if (targetId == null || targetId.equals(sender.getUUID()) || body.isEmpty()) {
            sendState(sender, "İçerik gönderilemedi.");
            return;
        }
        if (!("PHOTO".equals(type) || "LOCATION".equals(type))) return;
        if ("LOCATION".equals(type) && rateLimited(sender, "LOCATION", ATTACHMENT_MIN_INTERVAL_MS)) return;

        MinecraftServer server = sender.server;
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        String self = sender.getUUID().toString();
        String target = targetId.toString();
        if (contains(snapshot.blocked, self, target) || contains(snapshot.blocked, target, self)) {
            sendState(sender, "Bu kişiyle paylaşım engellendi.");
            return;
        }
        if (!contains(snapshot.contacts, self, target)) {
            sendState(sender, "Göndermek için kişi ekli olmalı.");
            return;
        }
        if ("PHOTO".equals(type) && !PhonePhotoSavedData.get(server).contains(body)) {
            sendState(sender, "Fotoğraf verisi bulunamadı.");
            return;
        }
        if (body.length() > 160) body = body.substring(0, 160);

        rememberOnlinePlayers(server, snapshot);
        PhoneCommunicationSavedData.StoredMessage stored = new PhoneCommunicationSavedData.StoredMessage();
        stored.id = UUID.randomUUID().toString();
        stored.fromUuid = self;
        stored.fromName = sender.getGameProfile().getName();
        stored.toUuid = target;
        stored.toName = snapshot.knownNames.getOrDefault(target, "Player");
        stored.type = type;
        stored.body = body;
        stored.timestamp = System.currentTimeMillis();
        stored.read = false;
        snapshot.messages.add(stored);
        snapshot.messageRevision++;
        data.replace(snapshot);
        removeTyping(sender.getUUID(), "MESSAGE");
        sendState(sender, "");
        sendStateIfOnline(server, targetId, "");
    }

    private static void sendFriendRequest(ServerPlayer sender, String rawNumber) {
        if (rateLimited(sender, "CONTACT_REQUEST", CONTACT_REQUEST_MIN_INTERVAL_MS)) return;
        MinecraftServer server = sender.server;
        PhoneNumberSavedData numbers = PhoneNumberSavedData.get(server);
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        rememberOnlinePlayers(server, snapshot);
        UUID targetId = resolvePlayer(server, numbers, snapshot, rawNumber);
        if (targetId == null || targetId.equals(sender.getUUID())) {
            sendState(sender, "Oyuncu veya telefon numarası bulunamadı.");
            return;
        }
        String senderKey = sender.getUUID().toString();
        String targetKey = targetId.toString();
        if (contains(snapshot.blocked, senderKey, targetKey) || contains(snapshot.blocked, targetKey, senderKey)) {
            sendState(sender, "Bu kişiyle iletişim engellendi.");
            return;
        }
        if (contains(snapshot.contacts, senderKey, targetKey)) {
            sendState(sender, "Bu kişi zaten kişilerinde.");
            return;
        }

        List<String> incoming = snapshot.incomingRequests.computeIfAbsent(targetKey, ignored -> new ArrayList<>());
        if (incoming.contains(senderKey)) {
            sendState(sender, "Bu kişiye zaten bekleyen bir isteğin var.");
            return;
        }
        incoming.add(senderKey);
        data.replace(snapshot);
        sendState(sender, "İstek gönderildi.");
        sendStateIfOnline(server, targetId, "Yeni kişi isteği.");
    }

    private static void acceptFriendRequest(ServerPlayer sender, String rawUuid) {
        UUID targetId = parseUuid(rawUuid);
        if (targetId == null) {
            sendState(sender, "Geçersiz kişi.");
            return;
        }
        MinecraftServer server = sender.server;
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        String self = sender.getUUID().toString();
        String target = targetId.toString();
        List<String> incoming = snapshot.incomingRequests.computeIfAbsent(self, ignored -> new ArrayList<>());
        if (!incoming.remove(target)) {
            sendState(sender, "İstek bulunamadı.");
            return;
        }
        addUnique(snapshot.contacts, self, target);
        addUnique(snapshot.contacts, target, self);
        data.replace(snapshot);
        sendState(sender, "Kişi eklendi.");
        sendStateIfOnline(server, targetId, "Kişi isteğin kabul edildi.");
    }

    private static void declineFriendRequest(ServerPlayer sender, String rawUuid) {
        UUID targetId = parseUuid(rawUuid);
        if (targetId == null) return;
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(sender.server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        List<String> incoming = snapshot.incomingRequests.computeIfAbsent(sender.getUUID().toString(), ignored -> new ArrayList<>());
        incoming.remove(targetId.toString());
        data.replace(snapshot);
        sendState(sender, "İstek reddedildi.");
    }

    private static void cancelFriendRequest(ServerPlayer sender, String rawUuid) {
        UUID targetId = parseUuid(rawUuid);
        if (targetId == null) return;
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(sender.server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        snapshot.incomingRequests.computeIfAbsent(targetId.toString(), ignored -> new ArrayList<>())
                .remove(sender.getUUID().toString());
        data.replace(snapshot);
        sendState(sender, "İstek iptal edildi.");
        sendStateIfOnline(sender.server, targetId, "");
    }

    private static void removeContact(ServerPlayer sender, String rawUuid) {
        UUID targetId = parseUuid(rawUuid);
        if (targetId == null) return;
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(sender.server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        removeValue(snapshot.contacts, sender.getUUID().toString(), targetId.toString());
        removeValue(snapshot.contacts, targetId.toString(), sender.getUUID().toString());
        data.replace(snapshot);
        sendState(sender, "Kişi kaldırıldı.");
        sendStateIfOnline(sender.server, targetId, "");
    }

    private static void blockContact(ServerPlayer sender, String rawUuid) {
        UUID targetId = parseUuid(rawUuid);
        if (targetId == null || targetId.equals(sender.getUUID())) return;
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(sender.server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        String self = sender.getUUID().toString();
        String target = targetId.toString();
        addUnique(snapshot.blocked, self, target);
        removeValue(snapshot.contacts, self, target);
        removeValue(snapshot.contacts, target, self);
        removeValue(snapshot.incomingRequests, self, target);
        removeValue(snapshot.incomingRequests, target, self);
        data.replace(snapshot);
        removeTyping(sender.getUUID(), "MESSAGE");
        removeTyping(sender.getUUID(), "GRAM");
        removeTyping(sender.getUUID(), "TINDER");
        ServerCall activeCall = CALLS.get(sender.getUUID());
        if (activeCall != null && targetId.equals(activeCall.other(sender.getUUID()))) {
            endVoiceCall(sender, "Engelleme nedeniyle görüşme sona erdi.");
        }
        PhoneSocialSavedData.get(sender.server).removeSocialRelationsBetween(sender.getUUID(), targetId);
        broadcastSocialStateMetadata(sender.server);
        sendState(sender, "Kişi engellendi.");
        sendStateIfOnline(sender.server, targetId, "");
    }

    private static void unblockContact(ServerPlayer sender, String rawUuid) {
        UUID targetId = parseUuid(rawUuid);
        if (targetId == null) return;
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(sender.server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        removeValue(snapshot.blocked, sender.getUUID().toString(), targetId.toString());
        data.replace(snapshot);
        sendState(sender, "Engel kaldırıldı.");
    }

    private static void sendGramPost(ServerPlayer sender, String rawUuid, String encoded) {
        UUID targetId = parseUuid(rawUuid);
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        var shared = social.sharedGramPost(encoded);
        UUID author = parseUuid(social.interactionOwnerUuid("BLOCKGRAM", "SHARE", encoded));
        if (targetId == null || targetId.equals(sender.getUUID()) || shared == null || author == null
                || isBlockedEither(sender.server, sender.getUUID(), author)
                || isBlockedEither(sender.server, targetId, author)) {
            sendState(sender, "Lumi gönderisi paylaşılamadı.");
            return;
        }
        sendStoredMessage(sender, targetId, "GRAM_POST", shared.encode(), "");
    }

    private static void sendText(ServerPlayer sender, String rawUuid, String rawBody, String replyToId, String type) {
        UUID targetId = parseUuid(rawUuid);
        String body = rawBody == null ? "" : rawBody.trim();
        if (targetId == null || targetId.equals(sender.getUUID()) || body.isEmpty()) {
            sendState(sender, "Mesaj gönderilemedi.");
            return;
        }
        if (body.length() > com.mikasrevs.newphone.network.PhoneMessageLimits.TEXT_LENGTH) {
            sendState(sender, "Mesaj çok uzun.");
            return;
        }
        sendStoredMessage(sender, targetId, type, body, replyToId);
    }

    private static void sendStoredMessage(ServerPlayer sender, UUID targetId, String type, String body, String replyToId) {
        MinecraftServer server = sender.server;
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        String self = sender.getUUID().toString();
        String target = targetId.toString();
        if (contains(snapshot.blocked, self, target) || contains(snapshot.blocked, target, self)) {
            sendState(sender, "Bu kişiyle mesajlaşma engellendi.");
            return;
        }
        boolean gram = type != null && type.startsWith("GRAM_");
        if (!PhoneModerationManager.pairAllowed(sender, targetId, type)) return;
        boolean tinder = type != null && type.startsWith("TINDER_");
        if (gram) {
            if (!canUseLumiDm(PhoneSocialSavedData.get(server), sender.getUUID(), targetId)) {
                sendState(sender, "Lumi'de mesajlaşmak için iki hesabın da birbirini takip etmesi gerekir.");
                return;
            }
        } else if (tinder) {
            if (!PhoneSocialSavedData.get(server).isTinderMatch(sender.getUUID(), targetId)) {
                sendState(sender, "Minder mesajı için aktif eşleşme gerekli.");
                return;
            }
        } else if (!contains(snapshot.contacts, self, target)) {
            sendState(sender, "Mesaj göndermek için kişi ekli olmalı.");
            return;
        }
        if ("PHOTO".equals(baseMessageType(type)) && !PhonePhotoSavedData.get(server).contains(body)) {
            sendState(sender, "Fotoğraf verisi bulunamadı.");
            return;
        }

        rememberOnlinePlayers(server, snapshot);
        String safeReplyId = replyToId == null ? "" : replyToId.trim();
        String replyPreview = "";
        if (!safeReplyId.isEmpty()) {
            String channel = messageChannel(type);
            for (PhoneCommunicationSavedData.StoredMessage existing : snapshot.messages) {
                if (!safeReplyId.equals(existing.id) || !channel.equals(messageChannel(existing.type))) continue;
                boolean sameConversation = (self.equals(existing.fromUuid) && target.equals(existing.toUuid))
                        || (target.equals(existing.fromUuid) && self.equals(existing.toUuid));
                if (sameConversation) { replyPreview = previewFor(existing); break; }
            }
        }

        PhoneCommunicationSavedData.StoredMessage stored = new PhoneCommunicationSavedData.StoredMessage();
        stored.id = UUID.randomUUID().toString();
        stored.fromUuid = self;
        stored.fromName = sender.getGameProfile().getName();
        stored.toUuid = target;
        stored.toName = snapshot.knownNames.getOrDefault(target, "Player");
        stored.type = type;
        stored.body = body == null ? "" : body;
        stored.timestamp = System.currentTimeMillis();
        stored.read = false;
        stored.replyToId = safeReplyId;
        stored.replyPreview = replyPreview;
        snapshot.messages.add(stored);
        snapshot.messageRevision++;
        data.replace(snapshot);

        removeTyping(sender.getUUID(), messageChannel(type));
        sendState(sender, "");
        sendStateIfOnline(server, targetId, "");
    }

    private static void sendReply(ServerPlayer sender, String rawUuid, String encoded, boolean gram) {
        String safe = encoded == null ? "" : encoded;
        int split = safe.indexOf('\n');
        String replyTo = split < 0 ? "" : safe.substring(0, split).trim();
        String text = split < 0 ? safe : safe.substring(split + 1);
        sendText(sender, rawUuid, text, replyTo, gram ? "GRAM_TEXT" : "TEXT");
    }

    private static void deleteMessage(ServerPlayer sender, String messageId, boolean gram) {
        deleteMessageInChannel(sender, messageId, gram ? "GRAM" : "MESSAGE");
    }

    private static void deleteMessageInChannel(ServerPlayer sender, String messageId, String channel) {
        if (messageId == null || messageId.isBlank()) return;
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(sender.server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        PhoneCommunicationSavedData.StoredMessage removed = null;
        for (int i = snapshot.messages.size() - 1; i >= 0; i--) {
            PhoneCommunicationSavedData.StoredMessage message = snapshot.messages.get(i);
            if (messageId.equals(message.id) && channel.equals(messageChannel(message.type))
                    && sender.getUUID().toString().equals(message.fromUuid)) {
                removed = snapshot.messages.remove(i);
                break;
            }
        }
        boolean cancelled=removed==null&&PhoneVoiceStorage.cancelPending(sender,messageId);
        if (removed != null || cancelled) {
            snapshot.retiredMessages.put(messageId,sender.getUUID().toString());
            snapshot.messageRevision++; data.replace(snapshot);
        }
        if(cancelled){var update=PhoneMessagingService.event();update.deletedMessageIds.add(messageId);PhoneMessagingService.update(sender,update);}
        if (removed != null) {
            if(removed.type.endsWith("VOICE"))PhoneVoiceStorage.cleanupIfUnused(sender.server,removed.body);
            var update=PhoneMessagingService.event();update.deletedMessageIds.add(removed.id);
            PhoneMessagingService.update(sender,update);
            var otherPlayer=sender.server.getPlayerList().getPlayer(UUID.fromString(removed.toUuid));
            if(otherPlayer!=null)PhoneMessagingService.update(otherPlayer,update);
        }
        sendState(sender, removed == null && !cancelled ? "Yalnız kendi mesajını silebilirsin." : "");
        if (removed != null) {
            UUID other = parseUuid(removed.toUuid);
            if (other != null) sendStateIfOnline(sender.server, other, "");
        }
    }

    private static void markRead(ServerPlayer sender, String rawUuid, boolean gram) {
        markReadInChannel(sender, rawUuid, gram ? "GRAM" : "MESSAGE");
    }

    private static void markReadInChannel(ServerPlayer sender, String rawUuid, String channel) {
        UUID peerId = parseUuid(rawUuid);
        if (peerId == null) return;
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(sender.server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        String self = sender.getUUID().toString();
        String peer = peerId.toString();
        boolean changed = false;
        for (PhoneCommunicationSavedData.StoredMessage message : snapshot.messages) {
            if (channel.equals(messageChannel(message.type)) && !message.read && peer.equals(message.fromUuid) && self.equals(message.toUuid)) {
                message.read = true;
                changed = true;
            }
        }
        if (changed) {
            snapshot.messageRevision++; data.replace(snapshot);
            var update=PhoneMessagingService.event();update.readBy=self;update.readPeer=peer;update.readChannel=channel;
            PhoneMessagingService.update(sender,update);
            var otherPlayer=sender.server.getPlayerList().getPlayer(peerId);
            if(otherPlayer!=null)PhoneMessagingService.update(otherPlayer,update);
        }
        sendState(sender, "");
        if (changed) sendStateIfOnline(sender.server, peerId, "");
    }

    private static void updateTyping(ServerPlayer sender, String targetRaw, String body) {
        if (rateLimited(sender, "TYPING", TYPING_MIN_INTERVAL_MS)) return;
        UUID targetId = parseUuid(targetRaw);
        if (targetId == null || targetId.equals(sender.getUUID())) return;
        PhoneCommunicationSavedData.Snapshot snapshot = PhoneCommunicationSavedData.get(sender.server).copySnapshot();
        String self = sender.getUUID().toString();
        String target = targetId.toString();
        if (contains(snapshot.blocked, self, target) || contains(snapshot.blocked, target, self)) return;
        String safe = body == null ? "" : body.toUpperCase(Locale.ROOT);
        String channel = safe.startsWith("GRAM|") ? "GRAM" : safe.startsWith("TINDER|") ? "TINDER" : "MESSAGE";
        if (!PhoneModerationManager.pairAllowed(sender, targetId, channel)) return;
        if ("GRAM".equals(channel)) {
            if (!canUseLumiDm(PhoneSocialSavedData.get(sender.server), sender.getUUID(), targetId)) return;
        } else if ("TINDER".equals(channel)) {
            if (!PhoneSocialSavedData.get(sender.server).isTinderMatch(sender.getUUID(), targetId)) return;
        } else if (!contains(snapshot.contacts, self, target)) return;
        boolean typing = safe.endsWith("|1") || "1".equals(safe);
        String key = sender.getUUID() + "|" + channel;
        if (typing) TYPING.put(key, new TypingState(sender.getUUID(), sender.getGameProfile().getName(), targetId, channel, System.currentTimeMillis() + 2200L));
        else TYPING.remove(key);
        sendStateIfOnline(sender.server, targetId, "");
    }

    private static void removeTyping(UUID senderId, String channel) {
        if (senderId != null) TYPING.remove(senderId + "|" + channel);
    }

    /** Relays only validated frames between the two participants of the same active video call. */
    public static void handleVideoFrameUpload(ServerPlayer sender, PhoneVideoFrameUploadMessage message) {
        if (sender == null || sender.server == null || message == null || message.jpegData() == null) return;
        byte[] frame = message.jpegData();
        if (frame.length == 0 || frame.length > PhoneVideoFrameUploadMessage.MAX_FRAME_BYTES
                || message.width() != PhoneVideoFrameUploadMessage.FRAME_WIDTH
                || message.height() != PhoneVideoFrameUploadMessage.FRAME_HEIGHT
                || frame.length < 4 || (frame[0] & 0xFF) != 0xFF || (frame[1] & 0xFF) != 0xD8) return;
        UUID senderId = sender.getUUID();
        UUID requestedCallId = parseUuid(message.callId());
        UUID requestedTarget = parseUuid(message.targetUuid());
        ServerCall call = CALLS.get(senderId);
        if (call == null || !call.video || call.phase != CallPhase.ACTIVE
                || requestedCallId == null || !call.id.equals(requestedCallId)
                || requestedTarget == null || !call.cameraEnabled(senderId)) return;
        UUID realPeer = call.other(senderId);
        if (realPeer == null || !realPeer.equals(requestedTarget)) return;
        long previousSequence = LAST_VIDEO_SEQUENCE.getOrDefault(senderId, -1L);
        if (message.sequence() <= previousSequence) return;
        long now = System.currentTimeMillis();
        if (now - LAST_VIDEO_FRAME_AT.getOrDefault(senderId, 0L) < VIDEO_FRAME_MIN_INTERVAL_MS) return;
        LAST_VIDEO_SEQUENCE.put(senderId, message.sequence());
        LAST_VIDEO_FRAME_AT.put(senderId, now);
        ServerPlayer peer = sender.server.getPlayerList().getPlayer(realPeer);
        if (peer == null) return;
        PhoneNetwork.sendVideoFrameData(peer, call.id.toString(), senderId.toString(), message.sequence(),
                message.width(), message.height(), java.util.Arrays.copyOf(frame, frame.length));
    }

    private static void updateVideoCameraState(ServerPlayer sender, String callId, String body) {
        ServerCall call = CALLS.get(sender.getUUID());
        if (!matchesCallId(call, callId) || !call.video || call.phase != CallPhase.ACTIVE) return;
        String[] parts = (body == null ? "" : body).split("\\|", 2);
        boolean enabled = parts.length > 0 && ("1".equals(parts[0]) || "true".equalsIgnoreCase(parts[0]));
        String mode = parts.length > 1 && "FRONT".equalsIgnoreCase(parts[1]) ? "FRONT" : "BACK";
        if (!call.setCameraState(sender.getUUID(), enabled, mode)) return;
        sendState(sender, "");
        sendStateIfOnline(sender.server, call.other(sender.getUUID()), "");
    }

    private static void updateCallPose(ServerPlayer sender, String callId, boolean active) {
        ServerCall call = CALLS.get(sender.getUUID());
        if (!active) { clearPhoneUsePose(sender); return; }
        if (!matchesCallId(call, callId) || call.video
                || (call.phase != CallPhase.ACTIVE && !sender.getUUID().equals(call.callerId))) { clearPhoneUsePose(sender); return; }
        InteractionHand hand = null;
        if (sender.getMainHandItem().getItem() instanceof ZPhoneItem) hand = InteractionHand.MAIN_HAND;
        else if (sender.getOffhandItem().getItem() instanceof ZPhoneItem) hand = InteractionHand.OFF_HAND;
        if (hand != null && (!sender.isUsingItem() || sender.getUsedItemHand() != hand)) {
            if (sender.isUsingItem()) sender.stopUsingItem();
            sender.startUsingItem(hand);
        }
    }

    private static void clearPhoneUsePose(ServerPlayer player) {
        if (player != null && player.isUsingItem() && player.getUseItem().getItem() instanceof ZPhoneItem) player.stopUsingItem();
    }

    /** Keep remote observers' vanilla pose stable after the caller releases the use key. */
    public static void maintainCallPose(ServerPlayer player) {
        ServerCall call=CALLS.get(player.getUUID());
        if(call==null||call.video||!player.isAlive()
                ||(call.phase!=CallPhase.ACTIVE&&!player.getUUID().equals(call.callerId))){
            clearPhoneUsePose(player);return;
        }
        // Other held items may still be used during a call.
        if(player.isUsingItem()&&!(player.getUseItem().getItem() instanceof ZPhoneItem))return;
        InteractionHand hand=null;
        if(player.getMainHandItem().getItem() instanceof ZPhoneItem)hand=InteractionHand.MAIN_HAND;
        else if(player.getOffhandItem().getItem() instanceof ZPhoneItem)hand=InteractionHand.OFF_HAND;
        if(hand==null){clearPhoneUsePose(player);return;}
        if(!player.isUsingItem()||player.getUsedItemHand()!=hand){
            clearPhoneUsePose(player);player.startUsingItem(hand);
        }
    }

    private static void startCall(ServerPlayer sender, String targetRaw, boolean video) {
        if (rateLimited(sender, "CALL_START", CALL_START_MIN_INTERVAL_MS)) return;
        MinecraftServer server = sender.server;
        UUID callerId = sender.getUUID();
        UUID targetId = parseUuid(targetRaw);
        if (targetId == null || targetId.equals(callerId)) {
            sendState(sender, "Geçersiz kişi.");
            return;
        }

        PhoneCommunicationSavedData.Snapshot snapshot = PhoneCommunicationSavedData.get(server).copySnapshot();
        String caller = callerId.toString();
        String targetKey = targetId.toString();
        boolean normalContact = contains(snapshot.contacts, caller, targetKey);
        boolean tinderMatch = PhoneModerationManager.pairAllowed(sender, targetId, "TINDER")
                && PhoneSocialSavedData.get(server).isTinderMatch(callerId, targetId);
        if (!normalContact && !tinderMatch) {
            sendState(sender, "Aramak için kişi veya Minder eşleşmesi olmalısınız.");
            return;
        }
        if (contains(snapshot.blocked, caller, targetKey) || contains(snapshot.blocked, targetKey, caller)) {
            sendState(sender, "Bu kişiyle arama yapılamıyor.");
            return;
        }
        if (!PhoneVoiceManager.isAvailable()) {
            sendState(sender, "Sesli arama için Simple Voice Chat gerekli.");
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            addSingleCallHistory(server, callerId, targetId, "OUTGOING", "OFFLINE", System.currentTimeMillis(), 0L);
            sendState(sender, "Bu oyuncu şu anda çevrimdışı.");
            return;
        }
        if (CALLS.containsKey(callerId)) {
            sendState(sender, "Zaten devam eden bir araman var.");
            return;
        }
        if (CALLS.containsKey(targetId)) {
            addSingleCallHistory(server, callerId, targetId, "OUTGOING", "BUSY", System.currentTimeMillis(), 0L);
            sendState(sender, "Bu oyuncu şu anda meşgul.");
            return;
        }

        String callerProblem = PhoneVoiceManager.playerProblem(callerId);
        if (!callerProblem.isEmpty()) {
            sendState(sender, callerProblem);
            return;
        }
        String targetProblem = PhoneVoiceManager.playerProblem(targetId);
        if (!targetProblem.isEmpty()) {
            sendState(sender, targetProblem);
            return;
        }

        ServerCall call = new ServerCall(UUID.randomUUID(), callerId, targetId, video);
        CALLS.put(callerId, call);
        CALLS.put(targetId, call);
        sendState(sender, target.getGameProfile().getName() + (video ? " görüntülü aranıyor..." : " aranıyor..."));
        sendState(target, sender.getGameProfile().getName() + (video ? " seni görüntülü arıyor." : " seni arıyor."));
    }

    private static void acceptVoiceCall(ServerPlayer sender, String callId) {
        ServerCall call = CALLS.get(sender.getUUID());
        if (!matchesCallId(call, callId) || call.phase != CallPhase.RINGING || !sender.getUUID().equals(call.calleeId)) {
            sendState(sender, "Kabul edilecek bir arama yok.");
            return;
        }
        MinecraftServer server = sender.server;
        ServerPlayer caller = server.getPlayerList().getPlayer(call.callerId);
        if (System.currentTimeMillis() - call.ringStartedAt >= RING_TIMEOUT_MS) {
            recordCallHistory(server, call, "NO_ANSWER", "MISSED", 0L);
            removeCall(server, call);
            sendState(sender, "Cevapsız arama.");
            if (caller != null) sendState(caller, "Aramaya 15 saniye içinde cevap verilmedi.");
            return;
        }
        if (caller == null) {
            recordCallHistory(server, call, "CANCELLED", "MISSED", 0L);
            removeCall(server, call);
            sendState(sender, "Arayan oyuncu artık çevrimiçi değil.");
            return;
        }

        PhoneVoiceManager.StartResult result = PhoneVoiceManager.startCall(call.callerId, call.calleeId);
        if (!result.success()) {
            removeCall(server, call);
            sendState(caller, result.error());
            sendState(sender, result.error());
            return;
        }
        call.phase = CallPhase.ACTIVE;
        call.startedAt = System.currentTimeMillis();
        call.voiceSessionId = result.sessionId();
        sendState(caller, "Görüşme başladı.");
        sendState(sender, "Görüşme başladı.");
    }

    private static void declineVoiceCall(ServerPlayer sender, String callId) {
        ServerCall call = CALLS.get(sender.getUUID());
        if (!matchesCallId(call, callId) || call.phase != CallPhase.RINGING) {
            sendState(sender, "");
            return;
        }
        MinecraftServer server = sender.server;
        UUID otherId = call.other(sender.getUUID());
        String callerResult = sender.getUUID().equals(call.callerId) ? "CANCELLED" : "DECLINED";
        String calleeResult = sender.getUUID().equals(call.callerId) ? "MISSED" : "DECLINED";
        recordCallHistory(server, call, callerResult, calleeResult, 0L);
        removeCall(server, call);
        sendState(sender, "Arama reddedildi.");
        sendStateIfOnline(server, otherId, sender.getGameProfile().getName() + " aramayı reddetti.");
    }

    private static void endVoiceCall(ServerPlayer sender, String selfNotice) {
        ServerCall call = CALLS.get(sender.getUUID());
        if (call == null) {
            sendState(sender, "");
            return;
        }
        MinecraftServer server = sender.server;
        UUID otherId = call.other(sender.getUUID());
        if (call.phase == CallPhase.ACTIVE) {
            long duration = Math.max(0L, System.currentTimeMillis() - call.startedAt);
            PhoneVoiceManager.endCall(call.voiceSessionId, call.callerId, call.calleeId);
            recordCallHistory(server, call, "COMPLETED", "COMPLETED", duration);
        } else {
            String callerResult = sender.getUUID().equals(call.callerId) ? "CANCELLED" : "DECLINED";
            String calleeResult = sender.getUUID().equals(call.callerId) ? "MISSED" : "DECLINED";
            recordCallHistory(server, call, callerResult, calleeResult, 0L);
        }
        removeCall(server, call);
        sendState(sender, selfNotice);
        sendStateIfOnline(server, otherId, sender.getGameProfile().getName() + " görüşmeyi sonlandırdı.");
    }

    private static void endVoiceCallFromClient(ServerPlayer sender, String callId) {
        ServerCall call = CALLS.get(sender.getUUID());
        if (!matchesCallId(call, callId)) {
            sendState(sender, "");
            return;
        }
        endVoiceCall(sender, "Görüşme sonlandırıldı.");
    }

    private static boolean matchesCallId(ServerCall call, String rawCallId) {
        UUID parsed = parseUuid(rawCallId);
        return call != null && parsed != null && call.id.equals(parsed);
    }

    private static void validateCallForPlayer(ServerPlayer player) {
        ServerCall call = CALLS.get(player.getUUID());
        if (call == null) return;
        MinecraftServer server = player.server;
        UUID otherId = call.other(player.getUUID());
        ServerPlayer other = server.getPlayerList().getPlayer(otherId);
        long now = System.currentTimeMillis();

        if (call.phase == CallPhase.RINGING && now - call.ringStartedAt >= RING_TIMEOUT_MS) {
            recordCallHistory(server, call, "NO_ANSWER", "MISSED", 0L);
            removeCall(server, call);
            if (other != null) sendState(other, other.getUUID().equals(call.callerId)
                    ? "Aramaya 15 saniye içinde cevap verilmedi." : "Cevapsız arama.");
            return;
        }

        boolean interrupted = other == null || !player.isAlive() || !PhoneVoiceManager.isAvailable();
        if (!interrupted && call.phase == CallPhase.ACTIVE) {
            interrupted = !PhoneVoiceManager.playerProblem(player.getUUID()).isEmpty()
                    || !PhoneVoiceManager.playerProblem(otherId).isEmpty();
        }
        if (!interrupted) return;

        if (call.phase == CallPhase.ACTIVE) {
            long duration = Math.max(0L, now - call.startedAt);
            PhoneVoiceManager.endCall(call.voiceSessionId, call.callerId, call.calleeId);
            recordCallHistory(server, call, "INTERRUPTED", "INTERRUPTED", duration);
        } else {
            recordCallHistory(server, call, "CANCELLED", "MISSED", 0L);
        }
        removeCall(server, call);
        if (other != null) sendState(other, "Telefon görüşmesi kesildi.");
    }

    /** Advances call timeouts independently of client screen polling. Runs on the server thread. */
    public static void tickCalls(MinecraftServer server) {
        if (server == null || CALLS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (ServerCall call : new HashSet<>(CALLS.values())) {
            if (CALLS.get(call.callerId) != call || CALLS.get(call.calleeId) != call) continue;
            ServerPlayer caller = server.getPlayerList().getPlayer(call.callerId);
            ServerPlayer callee = server.getPlayerList().getPlayer(call.calleeId);

            if (call.phase == CallPhase.RINGING && now - call.ringStartedAt >= RING_TIMEOUT_MS) {
                recordCallHistory(server, call, "NO_ANSWER", "MISSED", 0L);
                removeCall(server, call);
                if (caller != null) sendState(caller, "Aramaya 15 saniye içinde cevap verilmedi.");
                if (callee != null) sendState(callee, "Cevapsız arama.");
                continue;
            }

            boolean interrupted = caller == null || callee == null || !caller.isAlive() || !callee.isAlive()
                    || !PhoneVoiceManager.isAvailable();
            if (!interrupted && call.phase == CallPhase.ACTIVE) {
                interrupted = !PhoneVoiceManager.playerProblem(call.callerId).isEmpty()
                        || !PhoneVoiceManager.playerProblem(call.calleeId).isEmpty();
            }
            if (!interrupted) continue;

            if (call.phase == CallPhase.ACTIVE) {
                long duration = Math.max(0L, now - call.startedAt);
                PhoneVoiceManager.endCall(call.voiceSessionId, call.callerId, call.calleeId);
                recordCallHistory(server, call, "INTERRUPTED", "INTERRUPTED", duration);
            } else {
                recordCallHistory(server, call, "CANCELLED", "MISSED", 0L);
            }
            removeCall(server, call);
            if (caller != null) sendState(caller, "Telefon görüşmesi kesildi.");
            if (callee != null) sendState(callee, "Telefon görüşmesi kesildi.");
        }
    }

    public static void handlePlayerUnavailable(ServerPlayer player) {
        if(player!=null)PhoneVoiceStorage.logout(player);
        if (player == null || player.server == null) return;
        clearTransientPlayerState(player.getUUID());
        ServerCall call = CALLS.get(player.getUUID());
        if (call == null) return;
        MinecraftServer server = player.server;
        UUID otherId = call.other(player.getUUID());
        if (call.phase == CallPhase.ACTIVE) {
            long duration = Math.max(0L, System.currentTimeMillis() - call.startedAt);
            PhoneVoiceManager.endCall(call.voiceSessionId, call.callerId, call.calleeId);
            recordCallHistory(server, call, "INTERRUPTED", "INTERRUPTED", duration);
        } else {
            recordCallHistory(server, call, "CANCELLED", "MISSED", 0L);
        }
        removeCall(server, call);
        sendStateIfOnline(server, otherId, "Karşı taraf ayrıldığı için görüşme sona erdi.");
    }

    private static void recordCallHistory(MinecraftServer server, ServerCall call,
                                          String callerResult, String calleeResult, long durationMs) {
        if (server == null || call == null) return;
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        rememberOnlinePlayers(server, snapshot);
        long timestamp = call.startedAt > 0L ? call.startedAt : call.ringStartedAt;
        addCallHistoryToSnapshot(server, snapshot, call.callerId, call.calleeId, "OUTGOING", callerResult, timestamp, durationMs);
        addCallHistoryToSnapshot(server, snapshot, call.calleeId, call.callerId, "INCOMING", calleeResult, timestamp, durationMs);
        data.replace(snapshot);
    }

    private static void addSingleCallHistory(MinecraftServer server, UUID ownerId, UUID peerId,
                                             String direction, String result, long timestamp, long durationMs) {
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(server);
        PhoneCommunicationSavedData.Snapshot snapshot = data.copySnapshot();
        rememberOnlinePlayers(server, snapshot);
        addCallHistoryToSnapshot(server, snapshot, ownerId, peerId, direction, result, timestamp, durationMs);
        data.replace(snapshot);
    }

    private static void addCallHistoryToSnapshot(MinecraftServer server, PhoneCommunicationSavedData.Snapshot snapshot,
                                                  UUID ownerId, UUID peerId, String direction, String result,
                                                  long timestamp, long durationMs) {
        if (ownerId == null || peerId == null) return;
        PhoneCommunicationSavedData.StoredCallHistory stored = new PhoneCommunicationSavedData.StoredCallHistory();
        stored.id = UUID.randomUUID().toString();
        stored.peerUuid = peerId.toString();
        ServerPlayer online = server.getPlayerList().getPlayer(peerId);
        stored.peerName = online != null ? online.getGameProfile().getName()
                : snapshot.knownNames.getOrDefault(peerId.toString(), "Player");
        stored.peerPhoneNumber = PhoneNumberSavedData.get(server).getOrAssign(peerId);
        stored.direction = direction == null ? "INCOMING" : direction;
        stored.result = result == null ? "COMPLETED" : result;
        stored.timestamp = timestamp <= 0L ? System.currentTimeMillis() : timestamp;
        stored.durationMs = Math.max(0L, durationMs);
        List<PhoneCommunicationSavedData.StoredCallHistory> history = snapshot.callHistory
                .computeIfAbsent(ownerId.toString(), ignored -> new ArrayList<>());
        history.add(stored);
        while (history.size() > MAX_CALL_HISTORY_PER_PLAYER) history.remove(0);
    }

    private static void removeCall(MinecraftServer server, ServerCall call) {
        if (call == null) return;
        clearPhoneUsePose(server.getPlayerList().getPlayer(call.callerId));
        clearPhoneUsePose(server.getPlayerList().getPlayer(call.calleeId));
        CALLS.remove(call.callerId, call);
        CALLS.remove(call.calleeId, call);
        LAST_VIDEO_FRAME_AT.remove(call.callerId);
        LAST_VIDEO_FRAME_AT.remove(call.calleeId);
        LAST_VIDEO_SEQUENCE.remove(call.callerId);
        LAST_VIDEO_SEQUENCE.remove(call.calleeId);
    }


    public static void handleSocialPostUpload(ServerPlayer sender, PhoneSocialPostUploadMessage message) {
        if (sender == null || sender.server == null || message == null) return;
        long gameTime = sender.server.overworld().getGameTime();
        long worldTime = sender.server.overworld().getDayTime();
        String platform = message.platform() == null ? "" : message.platform().trim().toUpperCase(Locale.ROOT);
        if (!PhoneModerationManager.require(sender, platform)) return;
        boolean storyUpload = "BLOCKGRAM_STORY".equals(platform);
        String socialUploadKey = sender.getUUID() + "|" + platform;
        long last = LAST_SOCIAL_UPLOAD_TICK.getOrDefault(socialUploadKey, Long.MIN_VALUE);
        if (!storyUpload && last != Long.MIN_VALUE && gameTime >= last
                && gameTime - last < SOCIAL_UPLOAD_COOLDOWN_TICKS) {
            long remainingTicks = SOCIAL_UPLOAD_COOLDOWN_TICKS - (gameTime - last);
            long remainingSeconds = Math.max(1L, (remainingTicks + 19L) / 20L);
            sendState(sender, "Yeni bir gönderi paylaşmak için " + remainingSeconds + " saniye bekle.");
            return;
        }
        byte[] image = message.imageData();
        if (image != null && image.length > PhoneSocialPostUploadMessage.MAX_IMAGE_BYTES) {
            sendState(sender, "Gönderi görseli çok büyük.");
            return;
        }
        if (image != null && image.length > 0 && !com.mikasrevs.newphone.network.PhoneImageLimits.accepts(image)) {
            sendState(sender, "Fotoğraf verisi okunamadı.");
            return;
        }
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        String name = sender.getGameProfile().getName();
        if (("BLOCKGRAM".equals(platform)||"BLOCKPOST".equals(platform))&&!social.hasPostCapacity(sender.getUUID(),platform)) {
            sendState(sender,"Profilin dolu (50 gönderi). Yeni paylaşım için bir gönderiyi sil veya Ayarlar > Gönderilerim bölümünden arşivle.");
            return;
        }
        String ref = "";
        boolean story = false;
        if ("BLOCKGRAM".equals(platform)) {
            if (image == null || image.length == 0) { sendState(sender, "Lumi gönderisi için fotoğraf gerekli."); return; }
            ref = social.addGramPost(sender.getUUID(), name, message.body(), image, worldTime);
            sendState(sender, "Lumi gönderisi paylaşıldı.");
        } else if ("BLOCKGRAM_STORY".equals(platform)) {
            if (image == null || image.length == 0) { sendState(sender, "Story için fotoğraf gerekli."); return; }
            ref = social.addGramStory(sender.getUUID(), name, image, worldTime);
            story = true;
            sendState(sender, "Story paylaşıldı.");
        } else if ("BLOCKPOST".equals(platform)) {
            String body = message.body() == null ? "" : message.body().trim();
            if (body.length() > 300) body = body.substring(0, 300);
            if (body.isBlank() && (image == null || image.length == 0)) { sendState(sender, "Gönderi boş olamaz."); return; }
            ref = social.addBlockPost(sender.getUUID(), name, body, image, worldTime);
            sendState(sender, "X gönderisi paylaşıldı.");
        } else {
            sendState(sender, "Bilinmeyen sosyal medya uygulaması.");
            return;
        }
        if (!storyUpload) LAST_SOCIAL_UPLOAD_TICK.put(socialUploadKey, gameTime);
        broadcastSocialStateMetadata(sender.server);
        if (!ref.isBlank() && image != null && image.length > 0) broadcastSocialAsset(sender.server, social, sender.getUUID(), ref, image, story, platform);
    }

    public static void handleSocialAssetUpload(ServerPlayer sender, PhoneSocialAssetUploadMessage message) {
        if (sender == null || sender.server == null || message == null) return;
        if (rateLimited(sender, "SOCIAL_ASSET", PROFILE_ASSET_MIN_INTERVAL_MS)) return;
        byte[] image = message.imageData();
        if (!com.mikasrevs.newphone.network.PhoneImageLimits.accepts(image)) return;
        String kind = message.kind() == null ? "" : message.kind().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("GRAM_PROFILE", "POST_AVATAR", "POST_COVER", "TINDER_PROFILE").contains(kind)) return;
        if (!PhoneModerationManager.require(sender, kind)) return;
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        String ref = social.setProfileImage(sender.getUUID(), sender.getGameProfile().getName(), kind, image);
        broadcastSocialStateMetadata(sender.server);
        if (!ref.isBlank()) broadcastSocialAsset(sender.server, social, sender.getUUID(), ref, image, false, kind);
    }

    private static void handleSocialInteraction(ServerPlayer sender, String platform, String type, String targetId, String secondaryId, String body) {
        long interval = "COMMENT".equals(type) ? 650L : SOCIAL_INTERACTION_MIN_INTERVAL_MS;
        if (rateLimited(sender, "SOCIAL_INTERACTION", interval)) return;
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        UUID owner = parseUuid(social.interactionOwnerUuid(platform, type, targetId));
        if (owner != null && !owner.equals(sender.getUUID()) && isBlockedEither(sender.server, sender.getUUID(), owner)) {
            sendState(sender, "Bu oyuncuyla sosyal etkileşim engellendi.");
            sendSocialStateMetadata(sender);
            return;
        }
        String name = sender.getGameProfile().getName();
        boolean changed = false;
        if ("BLOCKGRAM".equals(platform)) {
            changed = switch (type) {
                case "LIKE" -> social.toggleGramLike(sender.getUUID(), name, targetId);
                case "COMMENT" -> social.addGramComment(sender.getUUID(), name, targetId, body);
                case "COMMENT_LIKE" -> social.toggleGramCommentLike(sender.getUUID(), name, targetId);
                case "SHARE" -> social.recordGramShare(sender.getUUID(), name, targetId);
                default -> false;
            };
        } else if ("BLOCKPOST".equals(platform)) {
            changed = switch (type) {
                case "LIKE" -> social.toggleBlockPostLike(sender.getUUID(), name, targetId);
                case "REPOST" -> social.toggleBlockPostRepost(sender.getUUID(), name, targetId);
                case "COMMENT" -> social.addBlockPostComment(sender.getUUID(), name, targetId, secondaryId, body);
                case "COMMENT_LIKE" -> social.toggleBlockPostCommentLike(sender.getUUID(), name, targetId);
                default -> false;
            };
        }
        if (changed) broadcastSocialStateMetadata(sender.server); else sendSocialStateMetadata(sender);
    }

    private static void handleSocialReply(ServerPlayer sender, String postId, String encoded) {
        String value = encoded == null ? "" : encoded;
        int split = value.indexOf('\n');
        String parent = split < 0 ? "" : value.substring(0, split);
        String body = split < 0 ? value : value.substring(split + 1);
        handleSocialInteraction(sender, "BLOCKPOST", "COMMENT", postId, parent, body);
    }

    private static void handleSocialGramDelete(ServerPlayer sender, String postId) {
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        if (social.deleteGramPost(sender.getUUID(), postId)) { sendState(sender, "Lumi gönderisi silindi."); broadcastSocialStateMetadata(sender.server); }
        else { sendState(sender, "Yalnızca kendi Lumi gönderini silebilirsin."); sendSocialStateMetadata(sender); }
    }

    private static void handleSocialPostDelete(ServerPlayer sender, String postId) {
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        if (social.deleteBlockPost(sender.getUUID(), postId)) { sendState(sender, "X gönderisi silindi."); broadcastSocialStateMetadata(sender.server); }
        else { sendState(sender, "Yalnızca kendi X gönderini silebilirsin."); sendSocialStateMetadata(sender); }
    }

    private static void handleSocialStoryView(ServerPlayer sender, String storyId) {
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        UUID owner = parseUuid(social.gramStoryOwnerUuid(storyId));
        if (owner != null && !owner.equals(sender.getUUID()) && isBlockedEither(sender.server, sender.getUUID(), owner)) { sendSocialStateMetadata(sender); return; }
        if (social.markGramStoryViewed(sender.getUUID(), storyId)) sendSocialStateMetadata(sender);
    }

    private static void handleSocialFollowRequest(ServerPlayer sender, String platform, String targetRaw) {
        if (rateLimited(sender, "FOLLOW_REQUEST", FOLLOW_REQUEST_MIN_INTERVAL_MS)) return;
        UUID target = parseUuid(targetRaw);
        if (target == null || target.equals(sender.getUUID())) { sendSocialStateMetadata(sender); return; }
        if (isBlockedEither(sender.server, sender.getUUID(), target)) { sendState(sender, "Bu oyuncuyla sosyal etkileşim engellendi."); return; }
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        ServerPlayer online = sender.server.getPlayerList().getPlayer(target);
        if (online != null) social.rememberPlayer(target, online.getGameProfile().getName(), socialRole(online));
        if (!social.hasProfile(target)) { sendState(sender, "Bu oyuncunun sosyal profili bulunamadı."); return; }
        if (social.sendFollowRequest(platform, sender.getUUID(), sender.getGameProfile().getName(), target, social.profileName(target))) broadcastSocialStateMetadata(sender.server);
        else sendSocialStateMetadata(sender);
    }

    private static void handleSocialFollowDecision(ServerPlayer sender, String platform, String requestId, boolean accept) {
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        if (social.updateFollowRequest(platform, sender.getUUID(), sender.getGameProfile().getName(), requestId, accept)) broadcastSocialStateMetadata(sender.server);
        else sendSocialStateMetadata(sender);
    }

    private static void handleSocialFollowAcceptBack(ServerPlayer sender, String platform, String requestId) {
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        if (social.updateFollowRequestAndFollowBack(platform, sender.getUUID(), sender.getGameProfile().getName(), requestId)) broadcastSocialStateMetadata(sender.server);
        else sendSocialStateMetadata(sender);
    }

    private static void handleSocialFollowRemove(ServerPlayer sender, String platform, String targetRaw) {
        UUID target = parseUuid(targetRaw);
        if (target == null || target.equals(sender.getUUID())) { sendSocialStateMetadata(sender); return; }
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        if (social.removeFollow(platform, sender.getUUID(), target)) broadcastSocialStateMetadata(sender.server); else sendSocialStateMetadata(sender);
    }

    private static void handleSocialNotificationsRead(ServerPlayer sender, String platform) {
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        social.markNotificationsRead(platform, sender.getUUID());
        sendSocialStateMetadata(sender);
    }

    private static void handleSocialNotificationsClear(ServerPlayer sender, String platform) {
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        social.clearNotifications(platform, sender.getUUID());
        sendSocialStateMetadata(sender);
    }

    private static void updateTinderBio(ServerPlayer sender, String bio) {
        PhoneSocialSavedData.get(sender.server).setTinderBio(sender.getUUID(), sender.getGameProfile().getName(), bio);
        broadcastSocialStateMetadata(sender.server);
    }

    private static void updateTinderEnabled(ServerPlayer sender, String raw) {
        boolean enabled = "1".equals(raw) || "true".equalsIgnoreCase(raw);
        PhoneSocialSavedData.get(sender.server).setTinderEnabled(sender.getUUID(), sender.getGameProfile().getName(), enabled);
        broadcastSocialStateMetadata(sender.server);
    }

    private static void updateTinderSettings(ServerPlayer sender, String raw) {
        String[] parts = raw == null ? new String[0] : raw.split("\\|", -1);
        if (parts.length != 5) { sendSocialStateMetadata(sender); return; }
        try {
            int age = Integer.parseInt(parts[0]);
            int minAge = Integer.parseInt(parts[3]);
            int maxAge = Integer.parseInt(parts[4]);
            PhoneSocialSavedData.get(sender.server).setTinderSettings(
                    sender.getUUID(), sender.getGameProfile().getName(), age, parts[1], parts[2], minAge, maxAge
            );
            broadcastSocialStateMetadata(sender.server);
        } catch (NumberFormatException ignored) {
            sendSocialStateMetadata(sender);
        }
    }

    private static void clearTinderPhoto(ServerPlayer sender) {
        PhoneSocialSavedData.get(sender.server).clearTinderPhoto(sender.getUUID(), sender.getGameProfile().getName());
        broadcastSocialStateMetadata(sender.server);
    }

    private static void handleTinderSwipe(ServerPlayer sender, String targetRaw, String decisionRaw) {
        if (rateLimited(sender, "TINDER_SWIPE", TINDER_SWIPE_MIN_INTERVAL_MS)) return;
        UUID target = parseUuid(targetRaw);
        if (target == null || target.equals(sender.getUUID())) { sendSocialStateMetadata(sender); return; }
        if (isBlockedEither(sender.server, sender.getUUID(), target)) {
            sendState(sender, "Bu oyuncu Minder'da kullanılamıyor.");
            sendSocialStateMetadata(sender);
            return;
        }
        PhoneSocialSavedData social = PhoneSocialSavedData.get(sender.server);
        ServerPlayer online = sender.server.getPlayerList().getPlayer(target);
        if (online != null) social.rememberPlayer(target, online.getGameProfile().getName(), socialRole(online));
        social.swipeTinder(sender.getUUID(), sender.getGameProfile().getName(), target, "LIKE".equalsIgnoreCase(decisionRaw));
        broadcastSocialStateMetadata(sender.server);
    }

    private static void resetTinderPasses(ServerPlayer sender) {
        PhoneSocialSavedData.get(sender.server).resetTinderPasses(sender.getUUID());
        sendSocialStateMetadata(sender);
    }

    private static void updateSocialGramBio(ServerPlayer sender, String bio) {
        PhoneSocialSavedData.get(sender.server).setBlockGramBio(sender.getUUID(), sender.getGameProfile().getName(), bio);
        broadcastSocialStateMetadata(sender.server);
    }

    private static void updateSocialPostBio(ServerPlayer sender, String bio) {
        PhoneSocialSavedData.get(sender.server).setBlockPostBio(sender.getUUID(), sender.getGameProfile().getName(), bio);
        broadcastSocialStateMetadata(sender.server);
    }

    private static void clearSocialGramPhoto(ServerPlayer sender) {
        PhoneSocialSavedData.get(sender.server).clearBlockGramPhoto(sender.getUUID(), sender.getGameProfile().getName());
        broadcastSocialStateMetadata(sender.server);
    }

    private static void sendSocialState(ServerPlayer player) {
        if (player == null || player.server == null) return;
        PhoneSocialSavedData social = PhoneSocialSavedData.get(player.server);
        rememberSocialPlayers(player.server, social);
        PhoneSocialStateData state = social.toClientState(player.getUUID(), player.server.overworld().getDayTime());
        filterBlockedSocialState(player.server, player.getUUID(), state);
        PhoneNetwork.sendSocialState(player, GSON.toJson(state));
        // Visible images are fetched on demand instead of replaying the entire archive on login.
    }

    private static void sendSocialStateMetadata(ServerPlayer player) {
        if (player == null || player.server == null) return;
        PhoneSocialSavedData social = PhoneSocialSavedData.get(player.server);
        rememberSocialPlayers(player.server, social);
        PhoneSocialStateData state = social.toClientState(player.getUUID(), player.server.overworld().getDayTime());
        filterBlockedSocialState(player.server, player.getUUID(), state);
        PhoneNetwork.sendSocialState(player, GSON.toJson(state));
    }

    private static void sendSocialProfileAssets(ServerPlayer player, String targetRaw) {
        UUID target = parseUuid(targetRaw);
        if (target == null || isBlockedEither(player.server, player.getUUID(), target)) return;
        PhoneSocialSavedData social = PhoneSocialSavedData.get(player.server);
        PhoneSocialStateData visible = social.toClientState(player.getUUID(), player.server.overworld().getDayTime());
        filterBlockedSocialState(player.server, player.getUUID(), visible);
        java.util.Set<String> allowedRefs = new java.util.HashSet<>();
        for (var profile : visible.profiles) if (profile.playerUuid.equals(target.toString()))
            allowedRefs.addAll(java.util.List.of(profile.blockGramPhotoRef, profile.blockPostAvatarRef, profile.blockPostCoverRef, profile.tinderPhotoRef));
        for (Map.Entry<String, byte[]> image : social.imagesForProfile(target).entrySet()) {
            if (!allowedRefs.contains(image.getKey())) continue;
            byte[] bytes = image.getValue();
            if (bytes != null && bytes.length > 0 && bytes.length <= PhonePhotoUploadMessage.MAX_IMAGE_BYTES) PhoneNetwork.sendPhotoData(player, image.getKey(), bytes);
        }
    }

    private static void broadcastSocialStateMetadata(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) sendSocialStateMetadata(online);
    }

    private static void broadcastSocialAsset(MinecraftServer server, PhoneSocialSavedData social, UUID owner, String ref, byte[] image, boolean storyFollowersOnly, String platform) {
        if (server == null || social == null || owner == null || ref == null || ref.isBlank() || image == null || image.length == 0) return;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!PhoneModerationManager.allowed(online, platform)) continue;
            UUID viewer = online.getUUID();
            if (!viewer.equals(owner) && isBlockedEither(server, viewer, owner)) continue;
            if (storyFollowersOnly && !social.canViewGramStory(viewer, owner)) continue;
            PhoneNetwork.sendPhotoData(online, ref, image);
        }
    }

    private static void rememberSocialPlayers(MinecraftServer server, PhoneSocialSavedData social) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) social.rememberPlayer(online.getUUID(), online.getGameProfile().getName(), socialRole(online));
    }

    private static String socialRole(ServerPlayer player) {
        if (player == null || player.server == null) return "PL";
        try { return player.server.getPlayerList().isOp(player.getGameProfile()) ? "AD" : "PL"; }
        catch (RuntimeException ignored) { return "PL"; }
    }

    private static boolean isBlockedEither(MinecraftServer server, UUID first, UUID second) {
        if (server == null || first == null || second == null) return false;
        PhoneCommunicationSavedData.Snapshot snapshot = PhoneCommunicationSavedData.get(server).copySnapshot();
        return contains(snapshot.blocked, first.toString(), second.toString()) || contains(snapshot.blocked, second.toString(), first.toString());
    }

    public static void refreshModeration(MinecraftServer server) {
        broadcastSocialStateMetadata(server);
        for (var player : server.getPlayerList().getPlayers()) sendFullState(player, "");
    }

    private static void filterBlockedSocialState(MinecraftServer server, UUID viewerId, PhoneSocialStateData state) {
        if (server == null || viewerId == null || state == null) return;
        PhoneModerationManager.filter(server, viewerId, state);
        PhoneCommunicationSavedData.Snapshot snapshot = PhoneCommunicationSavedData.get(server).copySnapshot();
        String self = viewerId.toString();
        Set<String> hidden = new HashSet<>(snapshot.blocked.getOrDefault(self, List.of()));
        for (Map.Entry<String, List<String>> entry : snapshot.blocked.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(self)) hidden.add(entry.getKey());
        }
        if (hidden.isEmpty()) return;
        Set<String> gramPostIds = new HashSet<>();
        state.blockGramPosts.removeIf(post -> { if (hidden.contains(post.authorUuid)) { gramPostIds.add(post.id); return true; } post.likedBy.removeIf(hidden::contains); return false; });
        state.blockGramStories.removeIf(story -> hidden.contains(story.authorUuid));
        state.blockGramComments.removeIf(comment -> hidden.contains(comment.authorUuid) || gramPostIds.contains(comment.postId));
        state.blockGramComments.forEach(comment -> comment.likedBy.removeIf(hidden::contains));
        Set<String> xPostIds = new HashSet<>();
        state.blockPostPosts.removeIf(post -> { if (hidden.contains(post.authorUuid)) { xPostIds.add(post.id); return true; } post.likedBy.removeIf(hidden::contains); post.repostedBy.removeIf(hidden::contains); return false; });
        state.blockPostComments.removeIf(comment -> hidden.contains(comment.authorUuid) || xPostIds.contains(comment.postId));
        state.blockPostComments.forEach(comment -> comment.likedBy.removeIf(hidden::contains));
        state.follows.removeIf(follow -> hidden.contains(follow.fromUuid) || hidden.contains(follow.toUuid));
        state.notifications.removeIf(notification -> hidden.contains(notification.actorUuid));
        state.profiles.removeIf(profile -> hidden.contains(profile.playerUuid));
        state.tinderLikedUuids.removeIf(hidden::contains);
        state.tinderPassedUuids.removeIf(hidden::contains);
        state.tinderMatchUuids.removeIf(hidden::contains);
    }

    private static boolean canUseLumiDm(PhoneSocialSavedData socialData, UUID first, UUID second) {
        return socialData != null && first != null && second != null
                && socialData.hasProfile(second)
                && socialData.isAcceptedFollow("BLOCKGRAM", first, second)
                && socialData.isAcceptedFollow("BLOCKGRAM", second, first);
    }

    private static String messageChannel(String type) {
        if(type!=null&&type.startsWith("GROUP_"))return "GROUP";
        if (type != null && type.startsWith("GRAM_")) return "GRAM";
        if (type != null && type.startsWith("TINDER_")) return "TINDER";
        return "MESSAGE";
    }

    private static String baseMessageType(String type) {
        if (type == null) return "TEXT";
        if (type.startsWith("GRAM_")) return type.substring("GRAM_".length());
        if (type.startsWith("TINDER_")) return type.substring("TINDER_".length());
        return type;
    }

    private static boolean rateLimited(ServerPlayer sender, String bucket, long intervalMs) {
        if (sender == null || sender.getUUID() == null || intervalMs <= 0L) return false;
        long now = System.currentTimeMillis();
        String key = sender.getUUID() + "|" + (bucket == null ? "GENERIC" : bucket);
        long last = LAST_ACTION_AT.getOrDefault(key, 0L);
        if (last > 0L && now >= last && now - last < intervalMs) return true;
        LAST_ACTION_AT.put(key, now);
        return false;
    }

    private static void clearTransientPlayerState(UUID playerId) {
        if (playerId == null) return;
        String prefix = playerId + "|";
        LAST_ACTION_AT.keySet().removeIf(key -> key.startsWith(prefix));
        LAST_SOCIAL_UPLOAD_TICK.keySet().removeIf(key -> key.startsWith(prefix));
        TYPING.keySet().removeIf(key -> key.startsWith(prefix));
        LAST_VIDEO_FRAME_AT.remove(playerId);
        LAST_VIDEO_SEQUENCE.remove(playerId);
        SENT_MESSAGE_REVISIONS.remove(playerId);
    }

    private static String previewFor(PhoneCommunicationSavedData.StoredMessage message) {
        if (message == null) return "";
        String base = baseMessageType(message.type);
        String text = "PHOTO".equals(base) ? "Fotoğraf" : "LOCATION".equals(base) ? "Konum" : (message.body == null ? "" : message.body);
        text = text.replace('\n', ' ').trim();
        return text.length() <= 48 ? text : text.substring(0, 47) + "…";
    }

    private static void sendFullState(ServerPlayer player, String notice) {
        if (player != null) SENT_MESSAGE_REVISIONS.remove(player.getUUID());
        sendState(player, notice);
    }

    private static void sendRequestedPhoto(ServerPlayer player, String photoId) {
        if (player == null || player.server == null) return;
        // The packet admission budget bounds media requests without retaining arbitrary photo IDs.
        if (photoId == null || photoId.isBlank() || photoId.length() > 64) return;
        if(photoId.startsWith("market-")){com.mikasrevs.newphone.marketplace.MarketplaceManager.photo(player,photoId);return;}
        if(photoId.startsWith("group-")||photoId.startsWith("avatar-")){PhoneGroupService.photo(player,photoId);return;}
        if (photoId.startsWith("social-")) {
            PhoneSocialSavedData social = PhoneSocialSavedData.get(player.server);
            PhoneSocialStateData visible = social.toClientState(player.getUUID(), player.server.overworld().getDayTime());
            filterBlockedSocialState(player.server, player.getUUID(), visible);
            boolean allowed = visible.blockGramPosts.stream().anyMatch(post -> photoId.equals(post.imageRef))
                    || visible.archivedPosts.stream().anyMatch(post -> photoId.equals(post.imageRef))
                    || visible.blockPostPosts.stream().anyMatch(post -> photoId.equals(post.imageRef))
                    || visible.blockGramStories.stream().anyMatch(story -> photoId.equals(story.imageRef))
                    || visible.profiles.stream().anyMatch(profile -> photoId.equals(profile.blockGramPhotoRef)
                    || photoId.equals(profile.blockPostAvatarRef) || photoId.equals(profile.blockPostCoverRef)
                    || photoId.equals(profile.tinderPhotoRef));
            if (allowed) PhoneNetwork.sendPhotoData(player, photoId, social.imageBytes(photoId));
            return;
        }
        PhoneCommunicationSavedData.Snapshot snapshot = PhoneCommunicationSavedData.get(player.server).copySnapshot();
        String self = player.getUUID().toString();
        PhonePhotoSavedData photos = PhonePhotoSavedData.get(player.server);
        for (PhoneCommunicationSavedData.StoredMessage message : snapshot.messages) {
            if (!(self.equals(message.fromUuid) || self.equals(message.toUuid)) || !"PHOTO".equals(baseMessageType(message.type))) continue;
            if (!PhoneModerationManager.allowed(player, message.type)) continue;
            if (!photoId.equals(message.body)) continue;
            byte[] bytes = photos.getPhoto(message.body);
            if (bytes != null && bytes.length > 0 && bytes.length <= PhonePhotoUploadMessage.MAX_IMAGE_BYTES) {
                PhoneNetwork.sendPhotoData(player, message.body, bytes);
            }
            return;
        }
    }

    private static void sendBankState(ServerPlayer player, String resultCode, String peerName,
                                      String amountText, String responseRequestId) {
        if (player == null || player.server == null) return;
        PhoneCurrencyIntegration.Snapshot snapshot = PhoneCurrencyManager.snapshot(player);
        PhoneBankStateData state = new PhoneBankStateData();
        state.available = snapshot.available();
        state.status = bankText(snapshot.status(), 40);
        state.provider = bankText(snapshot.provider(), 64);
        state.accountName = bankText(snapshot.accountName(), 96);
        state.minimumTransfer = snapshot.minimumTransfer();
        state.onlineRecipientRequired = snapshot.onlineRecipientRequired();
        state.updatedAt = System.currentTimeMillis();
        state.resultCode = bankText(resultCode, 32);
        state.resultPeerName = bankText(peerName, 48);
        state.resultAmount = bankText(amountText, 96);
        state.responseRequestId = bankText(responseRequestId, 64);

        int balanceCount = 0;
        for (PhoneCurrencyIntegration.Balance balance : snapshot.balances()) {
            if (balance == null || balance.id().isBlank() || balanceCount++ >= 12) break;
            PhoneBankStateData.BalanceEntry entry = new PhoneBankStateData.BalanceEntry();
            entry.id = bankText(balance.id(), 128);
            entry.name = bankText(balance.name(), 80);
            entry.formatted = bankText(balance.formatted(), 160);
            entry.coreValue = Math.max(0L, balance.coreValue());
            int unitCount = 0;
            for (PhoneCurrencyIntegration.Unit unit : balance.units()) {
                if (unit == null || unit.coreValue() <= 0L || unitCount++ >= 12) break;
                entry.units.add(new PhoneBankStateData.UnitEntry(
                        bankText(unit.name(), 64), unit.coreValue()
                ));
            }
            if (entry.units.isEmpty()) entry.units.add(new PhoneBankStateData.UnitEntry("Unit", 1L));
            state.balances.add(entry);
        }
        PhoneNetwork.sendBankState(player, serializeBankState(state));
    }

    private static String serializeBankState(PhoneBankStateData state) {
        String json = GSON.toJson(state);
        while (json.length() > 30_000) {
            boolean reduced = false;
            for (int i = state.balances.size() - 1; i >= 0; i--) {
                List<PhoneBankStateData.UnitEntry> units = state.balances.get(i).units;
                if (units != null && units.size() > 1) {
                    units.remove(units.size() - 1);
                    reduced = true;
                    break;
                }
            }
            if (!reduced && state.balances.size() > 1) {
                state.balances.remove(state.balances.size() - 1);
                reduced = true;
            }
            if (!reduced) {
                state.balances.clear();
                state.available = false;
                state.status = "ERROR";
            }
            json = GSON.toJson(state);
            if (!reduced) break;
        }
        return json;
    }

    private static String bankText(String value, int maxLength) {
        String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    public static void sendState(ServerPlayer player, String notice) {
        if (player == null || player.server == null) return;
        MinecraftServer server = player.server;
        validateCallForPlayer(player);
        PhoneNumberSavedData numbers = PhoneNumberSavedData.get(server);
        PhoneCommunicationSavedData data = PhoneCommunicationSavedData.get(server);
        for (ServerPlayer online : server.getPlayerList().getPlayers())
            data.rememberName(online.getUUID().toString(),online.getGameProfile().getName());
        PhoneCommunicationSavedData.Snapshot snapshot = data.copyMetadata();

        PhoneStateData state = new PhoneStateData();
        UUID selfId = player.getUUID();
        String self = selfId.toString();
        state.selfUuid = self;
        state.selfName = player.getGameProfile().getName();
        state.selfPhoneNumber = numbers.getOrAssign(selfId);
        state.notice = notice == null ? "" : notice;

        Set<String> contacts = new HashSet<>(snapshot.contacts.getOrDefault(self, List.of()));
        for (String id : contacts) addPlayerEntry(server, numbers, snapshot, state.contacts, id);
        for (String id : snapshot.incomingRequests.getOrDefault(self, List.of())) {
            addPlayerEntry(server, numbers, snapshot, state.incomingRequests, id);
        }
        for (java.util.Map.Entry<String, List<String>> entry : snapshot.incomingRequests.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(self)) {
                addPlayerEntry(server, numbers, snapshot, state.outgoingRequests, entry.getKey());
            }
        }
        for (String id : snapshot.blocked.getOrDefault(self, List.of())) {
            addPlayerEntry(server, numbers, snapshot, state.blockedPlayers, id);
        }

        state.messagesUnchanged = java.util.Objects.equals(SENT_MESSAGE_REVISIONS.get(selfId), snapshot.messageRevision);
        data.recentFor(self,state);
        if(state.messagesUnchanged)state.messages.clear();
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, TypingState>> typingIterator = TYPING.entrySet().iterator();
        while (typingIterator.hasNext()) {
            TypingState typing = typingIterator.next().getValue();
            if (typing.expiresAt < now) { typingIterator.remove(); continue; }
            if (selfId.equals(typing.targetId)) state.typing.add(new PhoneStateData.TypingEntry(typing.senderId.toString(), typing.senderName, typing.channel));
        }

        for (PhoneCommunicationSavedData.StoredCallHistory stored : snapshot.callHistory.getOrDefault(self, List.of())) {
            PhoneStateData.CallEntry entry = new PhoneStateData.CallEntry();
            entry.id = stored.id; entry.peerUuid = stored.peerUuid; entry.peerName = stored.peerName;
            entry.peerPhoneNumber = stored.peerPhoneNumber; entry.direction = stored.direction; entry.result = stored.result;
            entry.timestamp = stored.timestamp; entry.durationMs = stored.durationMs;
            state.callHistory.add(entry);
        }
        state.voiceCallingAvailable = PhoneVoiceManager.isAvailable();
        state.voiceProvider = PhoneVoiceManager.providerDisplayName();
        ServerCall call = CALLS.get(selfId);
        if (call != null) {
            UUID peerId = call.other(selfId);
            ServerPlayer peer = server.getPlayerList().getPlayer(peerId);
            state.callId = call.id.toString();
            state.callPeerUuid = peerId == null ? "" : peerId.toString();
            state.callPeerName = peer != null ? peer.getGameProfile().getName()
                    : snapshot.knownNames.getOrDefault(state.callPeerUuid, "Player");
            state.callVideo = call.video;
            state.callPeerCameraEnabled = call.cameraEnabled(peerId);
            state.callPeerCameraMode = call.cameraMode(peerId);
            state.callStartedAt = call.phase == CallPhase.ACTIVE ? call.startedAt : call.ringStartedAt;
            if (call.phase == CallPhase.ACTIVE) state.callStatus = "ACTIVE";
            else if (selfId.equals(call.callerId)) state.callStatus = "OUTGOING";
            else state.callStatus = "INCOMING";
        }

        PhoneModerationManager.filterMessages(player, state);
        PhoneNetwork.sendState(player, GSON.toJson(state));
        SENT_MESSAGE_REVISIONS.put(selfId, snapshot.messageRevision);
    }

    private static void sendStateIfOnline(MinecraftServer server, UUID playerId, String notice) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) sendState(player, notice);
    }

    private static void rememberOnlinePlayers(MinecraftServer server, PhoneCommunicationSavedData.Snapshot snapshot) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            snapshot.knownNames.put(player.getUUID().toString(), player.getGameProfile().getName());
            PhoneNumberSavedData.get(server).getOrAssign(player.getUUID());
        }
    }

    private static void addPlayerEntry(MinecraftServer server, PhoneNumberSavedData numbers,
                                       PhoneCommunicationSavedData.Snapshot snapshot,
                                       List<PhoneStateData.PlayerEntry> output, String rawUuid) {
        UUID id = parseUuid(rawUuid);
        if (id == null) return;
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        String name = online != null
                ? online.getGameProfile().getName()
                : snapshot.knownNames.getOrDefault(rawUuid, "Player");
        String number = numbers.getNumber(id);
        if (number == null && online != null) number = numbers.getOrAssign(id);
        output.add(new PhoneStateData.PlayerEntry(rawUuid, name, number == null ? "" : number, online != null));
    }

    private static UUID resolvePlayer(MinecraftServer server, PhoneNumberSavedData numbers, PhoneCommunicationSavedData.Snapshot snapshot, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        UUID byNumber = numbers.findOwner(value);
        if (byNumber != null) return byNumber;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getGameProfile().getName().equalsIgnoreCase(value)) return online.getUUID();
        }
        for (Map.Entry<String, String> entry : snapshot.knownNames.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(value)) {
                UUID id = parseUuid(entry.getKey());
                if (id != null) return id;
            }
        }
        return null;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean contains(java.util.Map<String, List<String>> map, String owner, String value) {
        List<String> values = map.get(owner);
        return values != null && values.contains(value);
    }

    private static void addUnique(java.util.Map<String, List<String>> map, String owner, String value) {
        List<String> values = map.computeIfAbsent(owner, ignored -> new ArrayList<>());
        if (!values.contains(value)) values.add(value);
    }

    private static void removeValue(java.util.Map<String, List<String>> map, String owner, String value) {
        List<String> values = map.get(owner);
        if (values != null) values.remove(value);
    }
    private enum CallPhase { RINGING, ACTIVE }

    private static final class ServerCall {
        private final UUID id;
        private final UUID callerId;
        private final UUID calleeId;
        private final boolean video;
        private CallPhase phase = CallPhase.RINGING;
        private final long ringStartedAt = System.currentTimeMillis();
        private long startedAt;
        private String voiceSessionId = "";
        private boolean callerCameraEnabled = true;
        private boolean calleeCameraEnabled = true;
        private String callerCameraMode = "FRONT";
        private String calleeCameraMode = "FRONT";

        private ServerCall(UUID id, UUID callerId, UUID calleeId, boolean video) {
            this.id = id;
            this.callerId = callerId;
            this.calleeId = calleeId;
            this.video = video;
        }

        private UUID other(UUID playerId) {
            if (callerId.equals(playerId)) return calleeId;
            if (calleeId.equals(playerId)) return callerId;
            return null;
        }

        private boolean setCameraState(UUID playerId, boolean enabled, String mode) {
            String safeMode = "FRONT".equalsIgnoreCase(mode) ? "FRONT" : "BACK";
            if (callerId.equals(playerId)) {
                if (callerCameraEnabled == enabled && callerCameraMode.equals(safeMode)) return false;
                callerCameraEnabled = enabled;
                callerCameraMode = safeMode;
                return true;
            }
            if (calleeId.equals(playerId)) {
                if (calleeCameraEnabled == enabled && calleeCameraMode.equals(safeMode)) return false;
                calleeCameraEnabled = enabled;
                calleeCameraMode = safeMode;
                return true;
            }
            return false;
        }

        private boolean cameraEnabled(UUID playerId) {
            if (callerId.equals(playerId)) return callerCameraEnabled;
            return calleeId.equals(playerId) && calleeCameraEnabled;
        }

        private String cameraMode(UUID playerId) {
            if (callerId.equals(playerId)) return callerCameraMode;
            return calleeId.equals(playerId) ? calleeCameraMode : "BACK";
        }
    }

    private record TypingState(UUID senderId, String senderName, UUID targetId, String channel, long expiresAt) { }

    private record CachedBankTransfer(String resultCode, String peerName, String amountText, long completedAt) { }

}

package com.mikasrevs.newphone;

import com.google.gson.Gson;
import com.mikasrevs.newphone.client.gui.PhoneChatDraft;
import com.mikasrevs.newphone.network.PhoneMessageLimits;
import com.mikasrevs.newphone.network.PhoneSharedPostData;
import com.mikasrevs.newphone.network.message.PhoneActionMessage;
import com.mikasrevs.newphone.network.message.PhoneSocialStateMessage;
import com.mikasrevs.newphone.network.message.PhoneStateMessage;
import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import com.mikasrevs.newphone.server.data.PhonePhotoSavedData;
import com.mikasrevs.newphone.server.data.PhoneSocialAssetSavedData;
import com.mikasrevs.newphone.server.data.PhoneSocialSavedData;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** Runs without a world or a graphical client; never touches player save files. */
public final class PhoneRegressionChecks {
    public static void main(String[] args) throws Exception {
        checkMessagePersistence();
        checkPhotoRetention();
        checkSocialRetention();
        checkChunking();
        checkDraftEditing();
        MarketplaceChecks.run();
        PhoneCurrencyChecks.run();
        TradeChecks.run();
        PhoneFeatureChecks.run();
        PhoneGroupChecks.run();
        PhoneVoiceReliabilityChecks.run();
        PhoneReleaseChecks.main(args);
        PhoneModelChecks.run();
        PhoneCaseChecks.run();
        PhoneModerationChecks.run();
        PhoneAdminPanelChecks.run();
        PhoneYouTubeChecks.run();
        PhoneTimeChecks.run();
        DuoChecks.run();
        PhoneCallMotionChecks.run();
        com.mikasrevs.newphone.client.settings.PhoneTranslationChecks.run();
        com.mikasrevs.newphone.client.gui.PhoneMusicChecks.main(args);
        PhoneAuditProbes.run();
        System.out.println("Phone regression checks passed (persistence, media, network, composer).");
    }

    private static void checkMessagePersistence() {
        var data = new PhoneCommunicationSavedData();
        var snapshot = data.copySnapshot();
        for (int i = 0; i < 2500; i++) {
            var message = new PhoneCommunicationSavedData.StoredMessage();
            message.id = "message-" + i;
            message.body = "Merhaba " + i;
            snapshot.messages.add(message);
        }
        data.replace(snapshot);
        var restored = PhoneCommunicationSavedData.load(data.save(new CompoundTag())).copySnapshot();
        require(restored.messages.size() == 2500, "message history was truncated");
        require(restored.messages.get(0).body.equals("Merhaba 0"), "oldest message lost");
        var legacy = new CompoundTag();
        legacy.putString("Json", new Gson().toJson(snapshot));
        require(PhoneCommunicationSavedData.load(legacy).copySnapshot().messages.size() == 2500,
                "legacy message migration lost history");
    }

    private static void checkPhotoRetention() {
        var data = new PhonePhotoSavedData();
        for (int i = 0; i < 600; i++) data.put("photo-" + i, new byte[]{(byte)i, 42});
        require(Arrays.equals(data.getPhoto("photo-0"), new byte[]{0, 42}), "old photo evicted");
        data.put("photo-0", new byte[]{99});
        require(Arrays.equals(data.getPhoto("photo-0"), new byte[]{0, 42}), "photo reference overwritten");
        var restored = PhonePhotoSavedData.load(data.save(new CompoundTag()));
        require(restored.contains("photo-0") && restored.contains("photo-599"), "photos lost on reload");
    }

    private static void checkSocialRetention() throws Exception {
        var social = new PhoneSocialSavedData();
        var assets = new PhoneSocialAssetSavedData();
        var field = PhoneSocialSavedData.class.getDeclaredField("assets");
        field.setAccessible(true);
        field.set(social, assets);
        List<UUID> owners = new ArrayList<>();
        String firstImage = "", otherImage = "";
        for (int player = 0; player < 10; player++) {
            UUID owner = UUID.randomUUID(); owners.add(owner);
            for (int post = 0; post < 50; post++) {
                String ref = social.addGramPost(owner, "Player" + player, "a".repeat(300), new byte[]{1, 2, 3}, 100L);
                if (player == 0 && post == 0) firstImage = ref;
                if (player == 1 && post == 0) otherImage = ref;
            }
        }
        require(assets.getImage(firstImage) != null, "500 images evicted an existing post's photo");
        var state = social.toClientState(owners.get(0), 100L);
        require(state.blockGramPosts.size() == 500, "per-player post allowance was shared");
        var shared = social.sharedGramPost(state.blockGramPosts.get(0).id);
        require(shared != null && shared.caption().length() == 300, "shared caption truncated");
        require(PhoneSharedPostData.decode(shared.encode()).equals(shared), "shared post round trip");
        social.addGramPost(owners.get(0), "Player0", "new", new byte[]{4}, 100L);
        state = social.toClientState(owners.get(0), 100L);
        require(state.blockGramPosts.size() == 500, "51st post did not respect owner's allowance");
        for (UUID owner : owners) require(state.blockGramPosts.stream().filter(post -> post.authorUuid.equals(owner.toString())).count() == 50,
                "another player's post was deleted");
        require(assets.getImage(otherImage) != null, "another player's image was deleted");
        var restored = PhoneSocialAssetSavedData.load(assets.save(new CompoundTag()));
        require(restored.getImage(otherImage) != null, "social images lost on reload");
    }

    private static void checkChunking() {
        String json = "a".repeat(29_999) + "😀" + "ğ".repeat(4_000_000);
        StringBuilder result = new StringBuilder();
        var chunks = PhoneStateMessage.split(json);
        require(chunks.size() > 128, "fixture must exceed old state limit");
        for (var chunk : chunks) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                PhoneStateMessage.encode(chunk, buffer);
                var decoded = PhoneStateMessage.decode(buffer);
                require(decoded.chunkIndex() == chunk.chunkIndex(), "chunk index changed");
                result.append(decoded.jsonChunk());
            } finally { buffer.release(); }
        }
        require(result.toString().equals(json), "unicode state corrupted by chunking");
        result.setLength(0);
        for (var chunk : PhoneSocialStateMessage.split(json)) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                PhoneSocialStateMessage.encode(chunk, buffer);
                result.append(PhoneStateMessage.decode(buffer).jsonChunk());
            } finally { buffer.release(); }
        }
        require(result.toString().equals(json), "large social state was truncated");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            String body = UUID.randomUUID() + "\n" + "ğ".repeat(PhoneMessageLimits.TEXT_LENGTH);
            PhoneActionMessage.encode(new PhoneActionMessage("SEND_REPLY", UUID.randomUUID().toString(), body), buffer);
            require(PhoneActionMessage.decode(buffer).body().equals(body), "long message/reply packet failed");
        } finally { buffer.release(); }
    }

    private static void checkDraftEditing() {
        var text = new StringBuilder();
        var draft = new PhoneChatDraft(text);
        draft.insert("Merhaba dünya! ".repeat(30));
        var lines = draft.lines(28, String::length);
        require(lines.size() > 5, "long draft must wrap vertically");
        StringBuilder joined = new StringBuilder();
        for (var line : lines) joined.append(text.substring(line.start(), line.end()));
        require(joined.toString().equals(text.toString()), "wrapping hid or deleted draft text");
        draft.moveTo(0, false); draft.insert("Başlangıç ");
        require(text.toString().startsWith("Başlangıç Merhaba"), "editing at start failed");
        draft.selectAll(); draft.insert("A😀B\nC");
        draft.moveTo(3, false); draft.delete(true);
        require(text.toString().equals("AB\nC"), "backspace split a surrogate pair");
        lines = draft.lines(20, String::length);
        require(lines.size() == 2, "explicit newline ignored");
        draft.selectAll(); draft.insert("a".repeat(PhoneMessageLimits.TEXT_LENGTH - 1) + "😀");
        require(text.length() == PhoneMessageLimits.TEXT_LENGTH - 1, "paste split a surrogate at packet boundary");
        draft.selectAll(); draft.insert("replacement");
        require(text.toString().equals("replacement"), "selected text replacement failed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

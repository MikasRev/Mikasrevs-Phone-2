package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.sound.PhoneSoundManager;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import com.mikasrevs.newphone.network.PhoneStateData;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Dedicated Tinder DM screen. Tinder messages are intentionally separate from the Messages app. */
public final class ZPhoneTinderChatScreen extends AbstractZPhoneScreen {
    private static final int MESSAGE_VIEW_TOP = 132;
    private int composerTop() { return composer.layout(this, 21, 551, 222, readableTextSize(8)); }
    private int messageViewBottom() { return composerTop() - 20; }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final String targetUuid;
    private final String targetName;
    private final StringBuilder draft = new StringBuilder();
    private final PhoneChatComposer composer = new PhoneChatComposer(draft);
    private boolean inputFocused;
    private boolean menuOpen;
    private PhoneStateData.MessageEntry contextMessage;
    private int messageScrollPixels;
    private long lastSyncAt;
    private long lastTypedAt;
    private boolean typingSent;
    private String outgoingAnimationBody = "";
    private String outgoingAnimationPreviousId = "";
    private String outgoingAnimationId = "";
    private long outgoingAnimationStartedAt;

    public ZPhoneTinderChatScreen(String targetUuid, String targetName) {
        super(Component.literal(targetName == null ? "Minder" : targetName));
        this.targetUuid = targetUuid == null ? "" : targetUuid;
        this.targetName = targetName == null || targetName.isBlank() ? PhoneSettingsStore.text("Oyuncu", "Player") : targetName;
        PhoneClientData.requestSync();
        PhoneSocialClientData.requestSync();
        PhoneClientData.markTinderConversationRead(this.targetUuid);
    }

    public boolean matchesTarget(String uuid) {
        return uuid != null && uuid.equals(targetUuid);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        PhoneClientData.markTinderConversationRead(targetUuid);
        drawWallpaper(g);
        drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xF407090D);
        drawStatusBar(g);
        drawHeader(g);
        drawMessages(g);
        drawTypingIndicator(g);
        drawComposer(g);
        if (menuOpen) drawMenu(g);
        if (contextMessage != null) drawContextMenu(g);
        if(!menuOpen&&contextMessage==null)PhoneChatExtras.draw(this,g,targetUuid);
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void drawHeader(GuiGraphics g) {
        drawBackButton(g);
        PhoneSocialStateData.ProfileEntry profile = PhoneSocialClientData.profile(targetUuid);
        String photo = profile == null ? "" : PhoneSocialClientData.socialPath(profile.tinderPhotoRef);
        drawRoundedRect(g, x(122), y(43), x(178), y(99), size(22), 0xFFFF4E67);
        drawRoundedRect(g, x(126), y(47), x(174), y(95), size(19), 0xFF11161D);
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, targetUuid, photo, x(128), y(49), size(44));
        PhoneModernText.drawCenteredColoredFitted(g, targetName, x(150), y(98), readableTextSize(10), size(190), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("MINDER EŞLEŞMESİ", "MINDER MATCH"), x(150), y(114), readableTextSize(6), size(190), 0xFFFF7183);
        fillCircle(g, x(268), y(72), size(14), 0xFF171D24);
        PhoneModernText.drawCenteredColored(g, "•••", x(268), y(64), readableTextSize(8), 0xFFE9EDF1);
        g.fill(x(18), y(126), x(282), y(127), 0x24FFFFFF);
    }

    private void drawMessages(GuiGraphics g) {
        List<PhoneStateData.MessageEntry> messages = PhoneClientData.tinderConversation(targetUuid);
        resolveOutgoingAnimation(messages);
        if (messages.isEmpty()) {
            drawMatchIntro(g);
            return;
        }

        messageScrollPixels = Math.max(0, Math.min(messageScrollPixels, maxMessageScroll(messages)));
        int top = messageContentTop(messages);
        g.enableScissor(x(16), y(MESSAGE_VIEW_TOP), x(284), y(messageViewBottom()));
        for (PhoneStateData.MessageEntry message : messages) {
            boolean mine = PhoneClientData.isSelfMessageUuid(message.fromUuid);
            int h = messageHeight(message);
            if (top + h >= MESSAGE_VIEW_TOP && top <= messageViewBottom()) {
                drawMessageWithSendAnimation(g, message, mine, top, h);
            }
            top += h + 8;
        }
        g.flush();
        g.disableScissor();
    }

    private void drawMatchIntro(GuiGraphics g) {
        PhoneSocialStateData.ProfileEntry profile = PhoneSocialClientData.profile(targetUuid);
        String photo = profile == null ? "" : PhoneSocialClientData.socialPath(profile.tinderPhotoRef);
        fillCircle(g, x(150), y(221), size(50), 0xFF1E252D);
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, targetUuid, photo, x(105), y(176), size(90));
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("EŞLEŞTİNİZ", "IT'S A MATCH"), x(150), y(286), readableTextSize(12), 0xFFFF5E73);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Sen ve ", "You and ") + targetName + PhoneSettingsStore.text(" birbirinizi beğendiniz.", " liked each other."), x(150), y(313), readableTextSize(7), size(244), 0xFFD4D9DE);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("İlk mesajı gönder ve sohbeti başlat.", "Send the first message and start chatting."), x(150), y(338), readableTextSize(7), size(244), 0xFF8E98A3);
    }

    private int messageHeight(PhoneStateData.MessageEntry message) {
        if(message!=null&&message.type.endsWith("VOICE"))return 70;
        String body = message == null || message.body == null ? "" : message.body;
        List<String> lines = ZPhoneSocialRenderUtil.wrap(body, 29, size(181), readableTextSize(8));
        int count = Math.max(1, lines.size());
        return 42 + count * 13;
    }

    private void drawMessage(GuiGraphics g, PhoneStateData.MessageEntry message, boolean mine, int top, int height) {
        if(message.type.endsWith("VOICE")){int left=mine?76:26;PhoneChatExtras.voiceBubble(this,g,message,left,top,left+198);if(mine)PhoneModernText.drawColoredFitted(g,com.mikasrevs.newphone.client.data.PhoneMessageClient.status(message),x(left+4),y(top+49),size(5),size(190),0xFFAEBBCD);return;}
        String body = message.body == null ? "" : message.body;
        List<String> lines = ZPhoneSocialRenderUtil.wrap(body, 29, size(181), readableTextSize(8));
        if (lines.isEmpty()) lines = List.of("");
        int count = Math.max(1, lines.size());
        int width = textBubbleWidth(lines);
        int left = mine ? 274 - width : 26;
        int right = left + width;
        int bg = mine ? 0xFFFF4E67 : 0xFF202730;
        drawRoundedRect(g, x(left), y(top), x(right), y(top + height - 20), size(14), bg);
        for (int i = 0; i < count; i++) {
            PhoneModernText.drawColored(g, lines.get(i), x(left + 12), y(top + 9 + i * 13), readableTextSize(8), 0xFFFFFFFF);
        }
        String time = formatTime(message.timestamp);
        if (mine) {
            String receipt = time + "  " + com.mikasrevs.newphone.client.data.PhoneMessageClient.status(message);
            PhoneModernText.drawColored(g, receipt, x(left + 5), y(top + height - 18), readableTextSize(5), message.read ? 0xFFFFA7B2 : 0xFF929CA7);
        } else {
            PhoneModernText.drawColored(g, time, x(left + 5), y(top + height - 18), readableTextSize(5), 0xFF77818D);
        }
    }

    private int totalMessageHeight(List<PhoneStateData.MessageEntry> messages) {
        int total = 0;
        for (PhoneStateData.MessageEntry message : messages) total += messageHeight(message) + 8;
        return Math.max(0, total - 8);
    }

    private int maxMessageScroll(List<PhoneStateData.MessageEntry> messages) {
        return Math.max(0, totalMessageHeight(messages) - (messageViewBottom() - MESSAGE_VIEW_TOP));
    }

    private int messageContentTop(List<PhoneStateData.MessageEntry> messages) {
        int total = totalMessageHeight(messages);
        int naturalTop = messageViewBottom() - total;
        if (total <= messageViewBottom() - MESSAGE_VIEW_TOP) return naturalTop;
        return naturalTop + messageScrollPixels;
    }

    private void drawTypingIndicator(GuiGraphics g) {
        if (!PhoneClientData.isTinderTyping(targetUuid)) return;
        PhoneModernText.drawColoredFitted(g, targetName + PhoneSettingsStore.text(" yazıyor…", " is typing…"), x(30), y(messageViewBottom()+4), readableTextSize(6), size(238), 0xFFFF8A99);
    }

    private boolean canChat() {
        return PhoneSocialClientData.isTinderMatch(targetUuid) && !PhoneClientData.isBlocked(targetUuid);
    }

    private void drawComposer(GuiGraphics g) {
        int top = composerTop();
        if (!canChat()) {
            drawRoundedRect(g,x(27),y(top),x(273),y(551),size(15),0xFF1E252D);
            PhoneModernText.drawCenteredColoredFitted(g,PhoneSettingsStore.text("Bu eşleşme artık aktif değil", "This match is no longer active"),x(150),y(top+12),readableTextSize(7),size(220),0xFF8E98A3);
            return;
        }
        composer.render(g,inputFocused,PhoneSettingsStore.text("Bir mesaj yaz…", "Write a message…"),inputFocused?0xFF242C35:0xFF181E25);
        fillCircle(g,x(265),y(530),size(18),draft.toString().trim().isEmpty()?0xFF343C45:0xFFFF4E67);
        PhoneModernText.drawCenteredColored(g,"↑",x(265),y(518),readableTextSize(16),0xFFFFFFFF);
    }

    

    private void drawMenu(GuiGraphics g) {
        beginOverlayLayer(g);
        drawRoundedRect(g, x(143), y(93), x(280), y(174), size(13), 0xFF222831);
        finishOverlayBackground(g);
        PhoneModernText.drawColoredFitted(g, PhoneClientData.isBlocked(targetUuid) ? PhoneSettingsStore.text("Engeli kaldır", "Unblock") : PhoneSettingsStore.text("Engelle", "Block"), x(160), y(112), readableTextSize(8), size(104),
                PhoneClientData.isBlocked(targetUuid) ? 0xFF79D69B : 0xFFFF7180);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Eşleşmelere dön", "Back to matches"), x(160), y(147), readableTextSize(7), size(104), 0xFFE4E8EC);
    }

    private void drawContextMenu(GuiGraphics g) {
        boolean mine = PhoneClientData.isSelfMessageUuid(contextMessage.fromUuid);
        if (!mine) return;
        beginOverlayLayer(g);
        drawRoundedRect(g, x(164), y(405), x(280), y(454), size(12), 0xFF232931);
        finishOverlayBackground(g);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Mesajı sil", "Delete message"), x(181), y(423), readableTextSize(8), size(84), 0xFFFF727F);
    }

    private void sendMessage() {
        if (!canChat()) return;
        String body = draft.toString().trim();
        if (body.isEmpty()) return;
        armOutgoingAnimation(body, PhoneClientData.tinderConversation(targetUuid));
        PhoneClientData.sendTinderText(targetUuid, body);
        PhoneSoundManager.playMessageSend();
        draft.setLength(0);
        inputFocused = true;
        stopTyping();
        messageScrollPixels = 0;
    }

    private void armOutgoingAnimation(String body, List<PhoneStateData.MessageEntry> current) {
        outgoingAnimationBody = body == null ? "" : body;
        outgoingAnimationPreviousId = current == null || current.isEmpty() ? "" : safeMessageId(current.get(current.size() - 1));
        outgoingAnimationId = "";
        outgoingAnimationStartedAt = 0L;
    }

    private void resolveOutgoingAnimation(List<PhoneStateData.MessageEntry> messages) {
        if (!outgoingAnimationId.isBlank() || outgoingAnimationBody.isBlank() || messages == null || messages.isEmpty()) return;
        for (int i = messages.size() - 1; i >= 0; i--) {
            PhoneStateData.MessageEntry message = messages.get(i);
            String id = safeMessageId(message);
            if (!outgoingAnimationPreviousId.isBlank() && outgoingAnimationPreviousId.equals(id)) break;
            if (PhoneClientData.isSelfMessageUuid(message.fromUuid) && outgoingAnimationBody.equals(message.body)) {
                outgoingAnimationId = id;
                outgoingAnimationStartedAt = System.currentTimeMillis();
                outgoingAnimationBody = "";
                outgoingAnimationPreviousId = "";
                return;
            }
        }
    }

    private void drawMessageWithSendAnimation(GuiGraphics g, PhoneStateData.MessageEntry message, boolean mine, int top, int height) {
        String id = safeMessageId(message);
        if (!mine || outgoingAnimationId.isBlank() || !outgoingAnimationId.equals(id)) {
            drawMessage(g, message, mine, top, height);
            return;
        }
        float progress = Math.min(1.0F, (System.currentTimeMillis() - outgoingAnimationStartedAt) / 230.0F);
        float eased = 1.0F - (float)Math.pow(1.0F - progress, 3.0D);
        if (progress >= 1.0F) {
            outgoingAnimationId = "";
            drawMessage(g, message, mine, top, height);
            return;
        }
        float bubbleScale = 0.84F + 0.16F * eased;
        int pivotX = x(274);
        int pivotY = y(top + Math.max(1, height - 9));
        g.pose().pushPose();
        g.pose().translate(pivotX, pivotY + size((1.0F - eased) * 8.0F), 0.0F);
        g.pose().scale(bubbleScale, bubbleScale, 1.0F);
        g.pose().translate(-pivotX, -pivotY, 0.0F);
        drawMessage(g, message, mine, top, height);
        g.pose().popPose();
    }

    private String safeMessageId(PhoneStateData.MessageEntry message) {
        return message == null || message.id == null ? "" : message.id;
    }

    private void stopTyping() {
        PhoneClientData.updateTinderTyping(targetUuid, false);
        typingSent = false;
    }

    private PhoneStateData.MessageEntry messageAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, 16, MESSAGE_VIEW_TOP, 284, messageViewBottom())) return null;
        List<PhoneStateData.MessageEntry> messages = PhoneClientData.tinderConversation(targetUuid);
        int top = messageContentTop(messages);
        for (PhoneStateData.MessageEntry message : messages) {
            int h = messageHeight(message);
            boolean mine = PhoneClientData.isSelfMessageUuid(message.fromUuid);
            String body = message.body == null ? "" : message.body;
            List<String> lines = ZPhoneSocialRenderUtil.wrap(body, 29, size(181), readableTextSize(8));
            if (lines.isEmpty()) lines = List.of("");
            int width = textBubbleWidth(lines);
            int left = mine ? 274 - width : 26;
            if (inside(mouseX, mouseY, left, top, left + width, top + h - 20)) return message;
            top += h + 8;
        }
        return null;
    }


    private int textBubbleWidth(List<String> lines) {
        int widest = lines.stream().mapToInt(line -> PhoneModernText.width(line, readableTextSize(8))).max().orElse(1);
        return Math.min(205, Math.max(76, (int) Math.ceil(widest / phone().scale()) + 24));
    }

    private int readableTextSize(float designSize) {
        float factor = designSize <= 6.0F ? 1.18F : designSize <= 8.0F ? 1.10F : designSize <= 10.0F ? 1.05F : 1.0F;
        return Math.max(1, Math.round(size(designSize) * factor));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(!menuOpen&&contextMessage==null&&PhoneChatExtras.click(this,mouseX,mouseY,button,targetUuid,"TINDER"))return true;
        if(button==0&&!menuOpen&&contextMessage==null&&PhoneChatExtras.open(messageAt(mouseX,mouseY)))return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (menuOpen) return true;
            PhoneStateData.MessageEntry hit = messageAt(mouseX, mouseY);
            if (hit != null && PhoneClientData.isSelfMessageUuid(hit.fromUuid)) {
                contextMessage = hit;
                menuOpen = false;
                inputFocused = false;
                stopTyping();
                return true;
            }
            contextMessage = null;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        if (contextMessage != null) {
            if (inside(mouseX, mouseY, 164, 405, 280, 454)) {
                PhoneClientData.deleteTinderMessage(contextMessage.id);
            }
            contextMessage = null;
            return true;
        }

        if (menuOpen) {
            if (inside(mouseX, mouseY, 143, 93, 280, 135)) {
                if (PhoneClientData.isBlocked(targetUuid)) PhoneSocialClientData.unblockSocialUser(targetUuid);
                else PhoneSocialClientData.blockSocialUser(targetUuid);
                menuOpen = false;
                inputFocused = false;
                stopTyping();
                return true;
            }
            if (inside(mouseX, mouseY, 143, 136, 280, 174)) {
                menuOpen = false;
                PhoneNavigationState.replaceCurrent(this.minecraft, new ZPhoneBrowserScreen(true));
                return true;
            }
            menuOpen = false;
            return true;
        }

        if (handleDynamicIslandClick(mouseX, mouseY)) return true;
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (inside(mouseX, mouseY, 18, 48, 50, 82)) {
            stopTyping();
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }
        if (inside(mouseX, mouseY, 248, 50, 286, 92)) {
            menuOpen = true;
            inputFocused = false;
            stopTyping();
            return true;
        }
        if (!canChat()) return true;
        composerTop(); if (composer.contains(mouseX, mouseY)) { composer.click(mouseX,mouseY,hasShiftDown());
            inputFocused = true;
            return true;
        }
        if (inside(mouseX, mouseY, 245, 509, 286, 553)) {
            sendMessage();
            return true;
        }
        inputFocused = false;
        stopTyping();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (menuOpen || contextMessage != null) return true;
        if (composer.scroll(mouseX,mouseY,delta)) return true;
        if (inside(mouseX, mouseY, 16, MESSAGE_VIEW_TOP, 284, messageViewBottom()) && delta != 0.0D) {
            List<PhoneStateData.MessageEntry> messages = PhoneClientData.tinderConversation(targetUuid);
            int max = maxMessageScroll(messages);
            int step = 52;
            if (delta > 0.0D) messageScrollPixels = Math.min(max, messageScrollPixels + step);
            else messageScrollPixels = Math.max(0, messageScrollPixels - step);
            if(delta>0&&messageScrollPixels>=max)com.mikasrevs.newphone.client.data.PhoneMessageClient.older(targetUuid,"TINDER");
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (menuOpen || contextMessage != null) return true;
        if (inputFocused && canChat() && SharedConstants.isAllowedChatCharacter(codePoint)) {
            composer.type(codePoint);
            lastTypedAt = System.currentTimeMillis();
            PhoneClientData.updateTinderTyping(targetUuid, true);
            typingSent = true;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (contextMessage != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) contextMessage = null;
            return true;
        }
        if (menuOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) menuOpen = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            stopTyping();
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }
        if (inputFocused && canChat() && composer.key(keyCode,modifiers)) { lastTypedAt=System.currentTimeMillis(); typingSent=!draft.isEmpty(); PhoneClientData.updateTinderTyping(targetUuid,typingSent); return true; }
        if (inputFocused && canChat() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            sendMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (now - lastSyncAt >= 1000L) {
            lastSyncAt = now;
            PhoneClientData.requestSync();
        }
        PhoneClientData.markTinderConversationRead(targetUuid);
        if (typingSent && now - lastTypedAt > 1500L) stopTyping();
    }

    @Override
    public void onClose() {
        stopTyping();
        super.onClose();
    }

    @Override
    public void removed() {
        stopTyping();
        super.removed();
    }

    private static String formatTime(long timestamp) {
        if (timestamp <= 0L) return "";
        try {
            return TIME.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}

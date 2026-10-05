package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.network.PhoneStateData;
import com.mikasrevs.newphone.network.PhoneSharedPostData;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** BlockGram's private inbox is intentionally separate from the Messages application. */
public final class ZPhoneBlockGramThreadScreen extends AbstractZPhoneScreen {
    private final String targetUuid;
    private final String targetName;
    private final StringBuilder draft = new StringBuilder();
    private final PhoneChatComposer composer = new PhoneChatComposer(draft);
    private boolean inputFocused;
    private boolean galleryOpen;
    private Path selectedPhoto;
    private static final int MESSAGE_VIEW_TOP = 124;
    private int composerTop() { return composer.layout(this, 57, 547, 187, readableTextSize(8)); }
    private int messageViewBottom() { return composerTop() - (replyTarget == null ? 18 : 43); }
    private int messageScrollPixels;
    private PhoneStateData.MessageEntry replyTarget;
    private PhoneStateData.MessageEntry contextMessage;
    private long lastSyncAt;
    private String outgoingAnimationBody = "";
    private String outgoingAnimationPreviousId = "";
    private String outgoingAnimationId = "";
    private long outgoingAnimationStartedAt;

    public ZPhoneBlockGramThreadScreen(String targetUuid, String targetName) {
        super(Component.literal(targetName == null ? PhoneSettingsStore.text("Lumi DM", "Lumi DM") : targetName));
        this.targetUuid = targetUuid == null ? "" : targetUuid;
        this.targetName = targetName == null || targetName.isBlank() ? PhoneSettingsStore.text("Oyuncu", "Player") : targetName;
        PhoneClientData.markGramConversationOpened(this.targetUuid);
        PhoneClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        PhoneClientData.markGramConversationOpened(targetUuid);
        PhoneClientData.markConversationRead(targetUuid, true);
        drawFrame(g);
        drawWallpaper(g);
        drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xFF050608);
        drawStatusBar(g);
        
        if (galleryOpen) {
            drawGallery(g);
            if(!galleryOpen&&contextMessage==null)PhoneChatExtras.draw(this,g,targetUuid);
        drawHomeIndicator(g);
            drawFrame(g);
            return;
        }
        drawBackButton(g);
        drawHeader(g);
        drawMessages(g);
        drawTypingIndicator(g);
        drawComposer(g);
        if (contextMessage != null) drawContextMenu(g);
        if(!galleryOpen&&contextMessage==null)PhoneChatExtras.draw(this,g,targetUuid);
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void drawHeader(GuiGraphics g) {
        String avatar = PhoneSocialClientData.blockGramPhoto(targetUuid);
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, targetUuid, avatar, x(132), y(51), size(36));
        PhoneModernText.drawCenteredColoredFitted(g, targetName, x(150), y(91), readableTextSize(10), size(190), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, "Lumi", x(150), y(106), readableTextSize(6), 0xFF8E9096);
        g.fill(x(18), y(119), x(282), y(120), 0x2AFFFFFF);
    }

    private void drawMessages(GuiGraphics g) {
        List<PhoneStateData.MessageEntry> messages = PhoneClientData.gramConversation(targetUuid);
        resolveOutgoingAnimation(messages);
        if (messages.isEmpty()) {
            PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Özel bir Lumi sohbeti başlat", "Start a private Lumi chat"), x(150), y(267), readableTextSize(9), size(242), 0xFF7F8996);
            return;
        }

        messageScrollPixels = Math.max(0, Math.min(messageScrollPixels, maxMessageScroll(messages)));
        int top = messageContentTop(messages);
        g.enableScissor(x(16), y(MESSAGE_VIEW_TOP), x(284), y(messageViewBottom()));
        for (PhoneStateData.MessageEntry message : messages) {
            boolean mine = PhoneClientData.isSelfMessageUuid(message.fromUuid);
            int visualHeight = messageVisualHeight(message, mine);
            if (top + visualHeight >= MESSAGE_VIEW_TOP && top <= messageViewBottom()) {
                drawMessageWithSendAnimation(g, message, mine, top, visualHeight);
            }
            top += visualHeight + 7;
        }
        g.disableScissor();
    }

    private int totalMessageHeight(List<PhoneStateData.MessageEntry> messages) {
        int total = 0;
        for (PhoneStateData.MessageEntry message : messages) {
            boolean mine = PhoneClientData.isSelfMessageUuid(message.fromUuid);
            total += messageVisualHeight(message, mine) + 7;
        }
        return Math.max(0, total - 7);
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

    private int drawMessage(GuiGraphics g, PhoneStateData.MessageEntry message, boolean mine, int top) {
        String type = PhoneClientData.baseMessageType(message.type);
        if ("VOICE".equals(type)) {int left=mine?75:27;PhoneChatExtras.voiceBubble(this,g,message,left,top,left+198);drawReceipt(g,message,mine,left,top+48);return 48+(mine?20:0);}
        if ("PHOTO".equals(type)) {
            int w = 148, h = 94+PhoneChatExtras.captionHeight(message), left = mine ? 273 - w : 27;
            drawRoundedRect(g, x(left), y(top), x(left + w), y(top + h), size(10), mine ? 0xFF168AFF : 0xFF242529);
            PhonePhotoStore.PhotoTexture texture = PhonePhotoStore.textureFor(minecraft, message.body == null ? "" : message.body);
            if (texture != null) {
                g.blit(texture.location(), x(left + 5), y(top + 5), size(w - 10), size(84), 0, 0,
                        texture.width(), texture.height(), texture.width(), texture.height());
            } else PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Fotoğraf", "Photo"), x(left + w / 2), y(top + 40), size(9), 0xFFFFFFFF);
            PhoneChatExtras.caption(this,g,message,left+7,top+94,w-14);
            drawReceipt(g, message, mine, left, top + h);
            return h + (mine ? 20 : 0);
        }
        if ("POST".equals(type)) {
            int w = 202, h = 116, left = mine ? 273 - w : 27;
            PhoneSharedPostData shared = PhoneSharedPostData.decode(message.body);
            drawRoundedRect(g, x(left), y(top), x(left + w), y(top + h), size(10), mine ? 0xFF172A3E : 0xFF1A1B1F);
            g.fill(x(left), y(top), x(left + 4), y(top + h), 0xFF168AFF);
            PhoneModernText.drawColored(g, PhoneSettingsStore.text("Lumi Gönderisi", "Lumi Post"), x(left + 14), y(top + 10), size(8), 0xFF6DB6FF);
            if (shared == null) {
                PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Paylaşılan gönderi açılamadı", "Shared post could not be opened"), x(left + 14), y(top + 38), size(7), size(196), 0xFFD9DEE5);
            } else {
                PhoneModernText.drawColored(g, trim(shared.authorName(), 20), x(left + 14), y(top + 29), size(8), 0xFFFFFFFF);
                drawSharedPostImage(g, shared.imageRef(), left + 14, top + 49, 54, 54);
                String caption = shared.caption() == null || shared.caption().isBlank() ? PhoneSettingsStore.text("Fotoğraf gönderisi", "Photo post") : shared.caption();
                ZPhoneSocialRenderUtil.drawWrapped(g, caption, x(left + 77), y(top + 52), size(7), 0xFFD9DEE5, 18, 3, size(10));
                PhoneModernText.drawColored(g, PhoneSettingsStore.text("Gönderiyi aç ›", "Open post ›"), x(left + 77), y(top + 94), size(6), 0xFF6DB6FF);
            }
            drawReceipt(g, message, mine, left, top + h);
            return h + (mine ? 20 : 0);
        }
        String body = message.body == null ? "" : message.body;
        List<String> lines = ZPhoneSocialRenderUtil.wrap(body, 27, size(172), readableTextSize(8));
        int count = Math.max(1, lines.size());
        boolean reply = message.replyPreview != null && !message.replyPreview.isBlank();
        int replyExtra = reply ? 25 : 0;
        int h = 22 + count * 13 + replyExtra;
        int w = textBubbleWidth(message, lines);
        int left = mine ? 273 - w : 27;
        drawRoundedRect(g, x(left), y(top), x(left + w), y(top + h), size(11), mine ? 0xFF168AFF : 0xFF242529);
        int textTop = top + 9;
        if (reply) {
            drawRoundedRect(g, x(left + 8), y(top + 7), x(left + w - 8), y(top + 28), size(5), mine ? 0x55203854 : 0x553D3E43);
            PhoneModernText.drawColoredFitted(g, trim(message.replyPreview, 28), x(left + 14), y(top + 12), readableTextSize(6), size(w - 28), 0xFFD9DEE5);
            textTop += 25;
        }
        if (lines.isEmpty()) lines = List.of("");
        for (int i = 0; i < count; i++) PhoneModernText.drawColoredFitted(g, lines.get(i), x(left + 12), y(textTop + i * 13), readableTextSize(8), size(w - 24), 0xFFFFFFFF);
        drawReceipt(g, message, mine, left, top + h);
        return h + (mine ? 20 : 0);
    }

    private void drawSharedPostImage(GuiGraphics g, String imageRef, int left, int top, int width, int height) {
        String ref = imageRef == null ? "" : imageRef;
        boolean drawn = false;
        if (ref.startsWith("asset:")) {
            String file = ref.substring("asset:".length());
            ResourceLocation texture = new ResourceLocation(NewPhone.MOD_ID, "textures/gui/phone/" + file);
            g.blit(texture, x(left), y(top), size(width), size(height), 0, 0,
                    256, 256, 256, 256);
            drawn = true;
        } else if (ref.startsWith("social:") || ref.startsWith("/")) {
            drawn = ZPhoneSocialRenderUtil.drawImage(g, minecraft, ref, x(left), y(top), size(width), size(height));
        }
        if (!drawn && !ref.isBlank()) {
            PhonePhotoStore.PhotoTexture texture = PhonePhotoStore.textureFor(minecraft, ref);
            if (texture != null) {
                g.blit(texture.location(), x(left), y(top), size(width), size(height), 0, 0,
                        texture.width(), texture.height(), texture.width(), texture.height());
                drawn = true;
            }
        }
        if (!drawn) {
            drawRoundedRect(g, x(left), y(top), x(left + width), y(top + height), size(8), 0xFF303844);
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Fotoğraf", "Photo"), x(left + width / 2), y(top + height / 2 - 4), size(7), 0xFF9CA6B3);
        }
    }

    private void drawReceipt(GuiGraphics g, PhoneStateData.MessageEntry message, boolean mine, int left, int bottom) {
        if(mine)PhoneModernText.drawColoredFitted(g,com.mikasrevs.newphone.client.data.PhoneMessageClient.status(message),x(left+4),y(bottom+1),readableTextSize(5),size(190),message.read?0xFF77BBFF:0xFF8F98A5);
    }

    private void drawTypingIndicator(GuiGraphics g) {
        if (!PhoneClientData.isTyping(targetUuid, true)) return;
        int top = messageViewBottom() + 3;
        PhoneModernText.drawColored(g, targetName + PhoneSettingsStore.text(" yazıyor…", " is typing…"), x(62), y(top), readableTextSize(6), 0xFF96999F);
    }

    private boolean canSendMessages() {
        return PhoneSocialClientData.canMessageBlockGram(targetUuid);
    }

    private void drawComposer(GuiGraphics g) {
        int top = composerTop();
        if (!canSendMessages()) {
            drawRoundedRect(g,x(27),y(top),x(273),y(550),size(15),0xFF20262F);
            PhoneModernText.drawCenteredColoredFitted(g,PhoneSettingsStore.text("Mesaj göndermek için önce takip et", "Follow first to send a message"),x(150),y(top+12),size(7),size(236),0xFF9CA6B3);
            return;
        }
        if (replyTarget != null) {
            drawRoundedRect(g,x(57),y(top-26),x(244),y(top-4),size(8),0xFF242A33);
            PhoneModernText.drawColored(g,PhoneSettingsStore.text("Yanıt: ", "Reply: ")+trim(replyTargetPreview(),22),x(66),y(top-20),readableTextSize(6),0xFFD9DEE5);
            PhoneModernText.drawColored(g,"×",x(230),y(top-23),size(10),0xFFFFFFFF);
        }
        fillCircle(g,x(34),y(529),size(16),0xFF252C35);
        PhoneModernText.drawCenteredColored(g,"+",x(34),y(519),size(17),0xFFFFFFFF);
        composer.render(g,inputFocused,PhoneSettingsStore.text("Mesaj…", "Message…"),0xFF1A2028);
        fillCircle(g,x(264),y(529),size(16),draft.toString().trim().isEmpty()?0xFF34353A:0xFF168AFF);
        PhoneModernText.drawCenteredColored(g,"↑",x(264),y(519),size(14),0xFFFFFFFF);
    }

    private void drawContextMenu(GuiGraphics g) {
        boolean mine = PhoneClientData.isSelfMessageUuid(contextMessage.fromUuid);
        int bottom = mine ? 492 : 462;
        beginOverlayLayer(g);
        drawRoundedRect(g, x(165), y(398), x(280), y(bottom), size(12), 0xFF222730);
        finishOverlayBackground(g);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Yanıtla", "Reply"), x(180), y(414), size(8), 0xFFFFFFFF);
        if (mine) PhoneModernText.drawColored(g, PhoneSettingsStore.text("Sil", "Delete"), x(180), y(454), size(8), 0xFFFF6B75);
    }

    private void drawGallery(GuiGraphics g) {
        drawOpaqueModalBackdrop(g, x(3), y(40), x(297), y(606), 0xFF07090E);
        drawRoundedRect(g, x(18), y(226), x(282), y(568), size(20), 0xFF11171F);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Fotoğraf Gönder", "Send Photo"), x(32), y(246), size(14), size(226), 0xFFFFFFFF);
        List<Path> photos = PhoneGalleryStore.photos(minecraft);
        for (int i = 0; i < Math.min(6, photos.size()); i++) {
            int col = i % 3, row = i / 3;
            int left = 33 + col * 78, top = 280 + row * 72;
            Path photo = photos.get(i);
            drawRoundedRect(g, x(left - 2), y(top - 2), x(left + 62), y(top + 62), size(7), photo.equals(selectedPhoto) ? 0xFF168AFF : 0xFF27282D);
            var texture = PhoneGalleryStore.textureFor(minecraft, photo);
            if (texture != null) g.blit(texture.location(), x(left), y(top), size(58), size(58), 0, 0,
                    texture.width(), texture.height(), texture.width(), texture.height());
        }
        drawRoundedRect(g, x(32), y(438), x(268), y(476), size(8), selectedPhoto == null ? 0xFF35363B : 0xFF168AFF);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Seçili fotoğrafı gönder", "Send selected photo"), x(150), y(449), size(8), size(212), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("İptal", "Cancel"), x(150), y(500), size(8), 0xFF959FAA);
    }

    private void sendText() {
        if (!canSendMessages()) return;
        String body = draft.toString().trim();
        if (body.isEmpty()) return;
        armOutgoingAnimation(body, PhoneClientData.gramConversation(targetUuid));
        if (replyTarget != null) PhoneClientData.sendReply(targetUuid, body, replyTarget.id, true);
        else PhoneClientData.sendGramText(targetUuid, body);
        draft.setLength(0);
        replyTarget = null;
        inputFocused = true;
        contextMessage = null;
        messageScrollPixels = 0;
        PhoneClientData.updateTyping(targetUuid, true, false);
        PhoneClientData.markGramConversationOpened(targetUuid);
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
            drawMessage(g, message, mine, top);
            return;
        }
        float progress = Math.min(1.0F, (System.currentTimeMillis() - outgoingAnimationStartedAt) / 230.0F);
        float eased = 1.0F - (float)Math.pow(1.0F - progress, 3.0D);
        if (progress >= 1.0F) {
            outgoingAnimationId = "";
            drawMessage(g, message, mine, top);
            return;
        }
        float bubbleScale = 0.84F + 0.16F * eased;
        int pivotX = x(273);
        int pivotY = y(top + Math.max(1, height - 20));
        g.pose().pushPose();
        g.pose().translate(pivotX, pivotY + size((1.0F - eased) * 8.0F), 0.0F);
        g.pose().scale(bubbleScale, bubbleScale, 1.0F);
        g.pose().translate(-pivotX, -pivotY, 0.0F);
        drawMessage(g, message, mine, top);
        g.pose().popPose();
    }

    private String safeMessageId(PhoneStateData.MessageEntry message) {
        return message == null || message.id == null ? "" : message.id;
    }

    private int textBubbleWidth(PhoneStateData.MessageEntry message, List<String> lines) {
        int widest = lines.stream().mapToInt(line -> PhoneModernText.width(line, readableTextSize(8))).max().orElse(1);
        if (message.replyPreview != null && !message.replyPreview.isBlank())
            widest = Math.max(widest, Math.min(size(168), PhoneModernText.width(message.replyPreview, readableTextSize(6))) + size(4));
        return Math.min(196, Math.max(82, (int) Math.ceil(widest / phone().scale()) + 24));
    }

    private int readableTextSize(float designSize) {
        float factor = designSize <= 6.0F ? 1.18F : designSize <= 8.0F ? 1.10F : designSize <= 10.0F ? 1.05F : 1.0F;
        return Math.max(1, Math.round(size(designSize) * factor));
    }

    private String replyTargetPreview() {
        if (replyTarget == null) return "";
        String type = PhoneClientData.baseMessageType(replyTarget.type);
        if ("PHOTO".equals(type)) return PhoneSettingsStore.text("Fotoğraf", "Photo");
        if ("POST".equals(type)) return PhoneSettingsStore.text("Lumi Gönderisi", "Lumi Post");
        return replyTarget.body == null ? "" : replyTarget.body;
    }

    private PhoneStateData.MessageEntry messageAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, 16, MESSAGE_VIEW_TOP, 284, messageViewBottom())) return null;
        List<PhoneStateData.MessageEntry> messages = PhoneClientData.gramConversation(targetUuid);
        messageScrollPixels = Math.max(0, Math.min(messageScrollPixels, maxMessageScroll(messages)));
        int top = messageContentTop(messages);
        for (PhoneStateData.MessageEntry message : messages) {
            boolean mine = PhoneClientData.isSelfMessageUuid(message.fromUuid);
            int h = messageVisualHeight(message, mine);
            String type = PhoneClientData.baseMessageType(message.type);
            int left;
            int width;
            if ("PHOTO".equals(type)) { width = 148; left = mine ? 273 - width : 27; }
            else if ("POST".equals(type)) { width = 202; left = mine ? 273 - width : 27; }
            else {
                String body = message.body == null ? "" : message.body;
                width = textBubbleWidth(message, ZPhoneSocialRenderUtil.wrap(body, 27, size(172), readableTextSize(8)));
                left = mine ? 273 - width : 27;
            }
            if (top + h >= MESSAGE_VIEW_TOP && top <= messageViewBottom()
                    && inside(mouseX, mouseY, left, top, left + width, top + h)) return message;
            top += h + 7;
        }
        return null;
    }

    private int messageVisualHeight(PhoneStateData.MessageEntry message, boolean mine) {
        String type = PhoneClientData.baseMessageType(message.type);
        int h;
        if ("PHOTO".equals(type)) h = 94+PhoneChatExtras.captionHeight(message);
        else if ("VOICE".equals(type)) h=48;
        else if ("POST".equals(type)) h = 116;
        else {
            List<String> lines = ZPhoneSocialRenderUtil.wrap(message.body == null ? "" : message.body, 27, size(172), readableTextSize(8));
            int count = Math.max(1, lines.size());
            h = 22 + count * 13 + ((message.replyPreview != null && !message.replyPreview.isBlank()) ? 25 : 0);
        }
        return h + (mine ? 20 : 0);
    }

    private boolean tryOpenMessageAttachment(double mouseX, double mouseY) {
        PhoneStateData.MessageEntry message = messageAt(mouseX, mouseY);
        if (message == null) return false;
        if(PhoneChatExtras.open(message))return true;
        String type = PhoneClientData.baseMessageType(message.type);
        if ("PHOTO".equals(type)) {
            minecraft.setScreen(new ZPhonePhotoViewerScreen(message.body, this));
            return true;
        }
        if ("POST".equals(type)) {
            PhoneSharedPostData shared = PhoneSharedPostData.decode(message.body);
            if (shared != null && PhoneBlockGramStore.findPost(shared.postId()) != null) {
                minecraft.setScreen(new ZPhoneBlockGramPostScreen(shared.postId(), false, this));
            }
            return true;
        }
        return false;
    }

    private String trim(String value, int max) {
        String safe = value == null ? "" : value.replace('\n', ' ').trim();
        return safe.length() <= max ? safe : safe.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(!galleryOpen&&contextMessage==null&&PhoneChatExtras.click(this,mouseX,mouseY,button,targetUuid,"GRAM"))return true;
        
        if (galleryOpen) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            List<Path> photos = PhoneGalleryStore.photos(minecraft);
            for (int i = 0; i < Math.min(6, photos.size()); i++) {
                int col = i % 3, row = i / 3;
                int left = 33 + col * 78, top = 280 + row * 72;
                if (inside(mouseX, mouseY, left - 2, top - 2, left + 62, top + 62)) { selectedPhoto = photos.get(i); return true; }
            }
            if (inside(mouseX, mouseY, 32, 438, 268, 476) && selectedPhoto != null) {
                if (PhoneClientData.sendGramPhoto(targetUuid, selectedPhoto)) { galleryOpen = false; selectedPhoto = null; messageScrollPixels = 0; }
                return true;
            }
            if (inside(mouseX, mouseY, 90, 483, 210, 530)) { galleryOpen = false; selectedPhoto = null; return true; }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            PhoneStateData.MessageEntry hit = messageAt(mouseX, mouseY);
            if (hit != null) { contextMessage = hit; setInputFocused(false); return true; }
            contextMessage = null;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (contextMessage != null) {
            if (inside(mouseX, mouseY, 165, 398, 280, 442)) {
                replyTarget = contextMessage;
                contextMessage = null;
                inputFocused = true;
                return true;
            }
            if (PhoneClientData.isSelfMessageUuid(contextMessage.fromUuid) && inside(mouseX, mouseY, 165, 442, 280, 492)) {
                PhoneClientData.deleteMessage(contextMessage.id, true);
                if (replyTarget != null && replyTarget.id.equals(contextMessage.id)) replyTarget = null;
                contextMessage = null;
                return true;
            }
            contextMessage = null;
            return true;
        }
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (!canSendMessages() && inside(mouseX, mouseY, 18, composerTop()-28, 282, 558)) {
            inputFocused = false;
            return true;
        }
        if (handleDynamicIslandClick(mouseX, mouseY)) return true;
        if (inside(mouseX, mouseY, 18, 48, 48, 78)) { PhoneClientData.updateTyping(targetUuid, true, false); PhoneNavigationState.backOrHome(this.minecraft); return true; }
        // Composer always wins over message cards. A tall shared-post card can never
        // steal clicks from the text field or send/photo buttons behind it.
        if (replyTarget != null && inside(mouseX, mouseY, 218, composerTop()-26, 245, composerTop()-4)) { replyTarget = null; return true; }
        if (inside(mouseX, mouseY, 18, 511, 51, 548)) { PhoneNavigationState.open(minecraft,this,new PhonePhotoPickerScreen(targetUuid,true)); setInputFocused(false); return true; }
        composerTop(); if (composer.contains(mouseX, mouseY)) { setInputFocused(true); composer.click(mouseX,mouseY,hasShiftDown()); return true; }
        if (inside(mouseX, mouseY, 246, 511, 282, 548)) { sendText(); return true; }
        if (inside(mouseX, mouseY, 18, 486, 282, 558)) return true;
        if (tryOpenMessageAttachment(mouseX, mouseY)) return true;
        setInputFocused(false);
        return true;
    }

    private void setInputFocused(boolean focused) {
        inputFocused = focused;
        if (!focused) PhoneClientData.updateTyping(targetUuid, true, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (galleryOpen || contextMessage != null) return true;
        if (composer.scroll(mouseX,mouseY,delta)) return true;
        if (inside(mouseX, mouseY, 16, MESSAGE_VIEW_TOP, 284, messageViewBottom()) && delta != 0.0D) {
            List<PhoneStateData.MessageEntry> messages = PhoneClientData.gramConversation(targetUuid);
            int max = maxMessageScroll(messages);
            int step = 54;
            if (delta > 0.0D) messageScrollPixels = Math.min(max, messageScrollPixels + step);
            else messageScrollPixels = Math.max(0, messageScrollPixels - step);
            if(delta>0&&messageScrollPixels>=max)com.mikasrevs.newphone.client.data.PhoneMessageClient.older(targetUuid,"GRAM");
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (galleryOpen || contextMessage != null) return true;
        if (inputFocused && canSendMessages() && SharedConstants.isAllowedChatCharacter(codePoint)) {
            composer.type(codePoint);
            PhoneClientData.updateTyping(targetUuid, true, !draft.isEmpty());
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (now - lastSyncAt >= 1000L) { lastSyncAt = now; PhoneClientData.requestSync(); }
        PhoneClientData.markConversationRead(targetUuid, true);
        if (inputFocused && !draft.isEmpty()) PhoneClientData.updateTyping(targetUuid, true, true);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((galleryOpen || contextMessage != null) && keyCode != GLFW.GLFW_KEY_ESCAPE) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (contextMessage != null) { contextMessage = null; return true; }
            
        if (galleryOpen) { galleryOpen = false; selectedPhoto = null; }
            else { PhoneClientData.updateTyping(targetUuid, true, false); PhoneNavigationState.backOrHome(this.minecraft); }
            return true;
        }
        if (inputFocused && canSendMessages() && composer.key(keyCode,modifiers)) { PhoneClientData.updateTyping(targetUuid,true,!draft.isEmpty()); return true; }
        if (inputFocused && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) { sendText(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        PhoneClientData.updateTyping(targetUuid, true, false);
        super.onClose();
    }
}

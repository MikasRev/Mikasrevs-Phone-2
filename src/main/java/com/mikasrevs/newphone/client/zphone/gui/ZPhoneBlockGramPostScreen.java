package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;

import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore;
import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore.CommentEntry;
import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore.PostEntry;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhoneRoleBadgeRenderer;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.network.PhoneStateData;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Native detail/comment/share view for a BlockGram post. */
public final class ZPhoneBlockGramPostScreen extends AbstractZPhoneScreen {
    private final String postId;
    private final StringBuilder commentDraft = new StringBuilder();
    private final Set<String> sentTo = new LinkedHashSet<>();
    private final Screen returnTarget;
    private boolean commentFocused;
    private boolean sharePicker;
    private boolean deleteConfirmation;
    private int commentScroll;
    private int shareScroll;

    public ZPhoneBlockGramPostScreen(String postId) {
        this(postId, false, null);
    }

    public ZPhoneBlockGramPostScreen(String postId, boolean openShare) {
        this(postId, openShare, null);
    }

    public ZPhoneBlockGramPostScreen(String postId, boolean openShare, Screen returnTarget) {
        super(Component.literal(PhoneSettingsStore.text("Lumi Gönderisi", "Lumi Post")));
        this.postId = postId == null ? "" : postId;
        this.sharePicker = openShare;
        this.returnTarget = returnTarget;
        PhoneSocialClientData.requestSync();
        PhoneClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawFrame(g);
        drawWallpaper(g);
        drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xFF050608);
        drawStatusBar(g);
        PostEntry post = PhoneBlockGramStore.findPost(postId);
        if (deleteConfirmation && post != null) {
            drawDeleteConfirmation(g);
            drawHomeIndicator(g);
            drawFrame(g);
            return;
        }
        if (sharePicker && post != null) {
            drawSharePicker(g, post);
            drawHomeIndicator(g);
            drawFrame(g);
            return;
        }
        drawBackButton(g);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Gönderi", "Post"), x(150), y(58), size(14), size(190), 0xFFFFFFFF);
        g.fill(x(17), y(86), x(283), y(87), 0x28FFFFFF);
        if (post != null && post.authorUuid().equals(PhoneClientData.selfUuid())) {
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Sil", "Delete"), x(263), y(61), size(7), 0xFFFF5B6E);
        }
        if (post == null) {
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Gönderi kullanılamıyor", "Post unavailable"), x(150), y(270), size(12), 0xFFFFFFFF);
        } else {
            drawPost(g, post);
            drawComments(g, post);
            drawComposer(g);
        }
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void drawPost(GuiGraphics g, PostEntry post) {
        String avatar = post.authorUuid().equals(PhoneClientData.selfUuid()) ? PhoneBlockGramStore.profilePhotoPath() : PhoneSocialClientData.blockGramPhoto(post.authorUuid());
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, post.authorUuid(), avatar, x(24), y(92), size(30));
        PhoneRoleBadgeRenderer.drawNameAndBadge(g, post.authorName(), PhoneSocialClientData.roleBadge(post.authorUuid()),
                x(62), y(94), size(10), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, "@" + post.authorName().toLowerCase(java.util.Locale.ROOT).replace(" ", ""), x(62), y(114), size(7), 0xFF85888F);
        g.fill(x(10), y(133), x(290), y(343), 0xFF111216);
        ZPhoneSocialRenderUtil.drawImage(g, minecraft, post.imagePath(), x(10), y(133), size(280), size(210));

        PhoneModernText.drawColored(g, post.liked() ? "♥" : "♡", x(22), y(351), size(17), post.liked() ? 0xFFFF304F : 0xFFF0F2F5);
        PhoneModernText.drawColored(g, Integer.toString(post.likes()), x(46), y(356), size(8), 0xFFCBD1D8);
        PhoneModernText.drawColored(g, "◇", x(88), y(351), size(17), 0xFFF0F2F5);
        PhoneModernText.drawColored(g, Integer.toString(PhoneBlockGramStore.commentCount(post.id())), x(111), y(356), size(8), 0xFFCBD1D8);
        PhoneModernText.drawColored(g, "↗", x(151), y(352), size(15), 0xFFF0F2F5);
        PhoneModernText.drawColored(g, post.saved() ? "◆" : "◇", x(254), y(352), size(14), 0xFFF0F2F5);
        ZPhoneSocialRenderUtil.drawWrapped(g, post.caption(), x(22), y(379), size(8), 0xFFE7EBEF, 48, 2, size(11));
        g.fill(x(10), y(406), x(290), y(407), 0x33FFFFFF);
    }

    private void drawComments(GuiGraphics g, PostEntry post) {
        List<CommentEntry> comments = PhoneBlockGramStore.comments(post.id());
        int top = 417 - commentScroll;
        g.enableScissor(x(18), y(411), x(282), y(501));
        if (comments.isEmpty()) {
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Henüz yorum yok", "No comments yet"), x(150), y(446), size(9), 0xFF828C99);
        }
        for (CommentEntry comment : comments) {
            if (top + 43 >= 411 && top <= 501) {
                String avatar = PhoneSocialClientData.blockGramPhoto(comment.authorUuid());
                ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, comment.authorUuid(), avatar, x(25), y(top + 4), size(24));
                PhoneRoleBadgeRenderer.drawNameAndBadge(g, comment.authorName(), PhoneSocialClientData.roleBadge(comment.authorUuid()),
                        x(57), y(top + 3), size(8), 0xFFFFFFFF);
                ZPhoneSocialRenderUtil.drawWrapped(g, comment.body(), x(57), y(top + 18), size(7), 0xFFB8C0CA, 37, 2, size(9));
                PhoneModernText.drawColored(g, comment.liked() ? "♥" : "♡", x(251), y(top + 10), size(11), comment.liked() ? 0xFFFF304F : 0xFF8E98A5);
                if (comment.likes() > 0) PhoneModernText.drawColored(g, Integer.toString(comment.likes()), x(251), y(top + 29), size(6), 0xFF7E8895);
            }
            top += 48;
        }
        g.disableScissor();
    }

    private void drawComposer(GuiGraphics g) {
        drawRoundedRect(g, x(20), y(508), x(244), y(545), size(12), 0xFF1A1B1F);
        String shown = commentDraft.isEmpty() ? PhoneSettingsStore.text("Yorum ekle…", "Add a comment…") : commentDraft.toString();
        if (shown.length() > 35) shown = shown.substring(shown.length() - 35);
        PhoneModernText.drawColored(g, shown, x(32), y(520), size(8), commentDraft.isEmpty() ? 0xFF76818E : 0xFFFFFFFF);
        fillCircle(g, x(263), y(526), size(17), commentDraft.toString().trim().isEmpty() ? 0xFF303136 : 0xFF168AFF);
        PhoneModernText.drawCenteredColored(g, "↑", x(263), y(516), size(14), 0xFFFFFFFF);
    }

    private void drawSharePicker(GuiGraphics g, PostEntry post) {
        drawOpaqueModalBackdrop(g, x(3), y(40), x(297), y(606), 0xFF07090E);
        drawRoundedRect(g, x(18), y(222), x(282), y(570), size(20), 0xFF11171F);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Lumi DM ile paylaş", "Share in Lumi DM"), x(32), y(242), size(14), 0xFFFFFFFF);
        List<com.mikasrevs.newphone.network.PhoneSocialStateData.ProfileEntry> contacts = PhoneSocialClientData.profiles().stream()
                .filter(profile -> profile.playerUuid != null && !profile.playerUuid.isBlank() && !profile.playerUuid.equals(PhoneClientData.selfUuid()))
                .filter(profile -> PhoneSocialClientData.canMessageBlockGram(profile.playerUuid))
                .toList();
        int top = 276;
        int startIndex = Math.min(shareScroll, Math.max(0, contacts.size() - 1));
        for (int i = startIndex; i < Math.min(startIndex + 4, contacts.size()); i++) {
            var contact = contacts.get(i);
            g.fill(x(38), y(top + 47), x(271), y(top + 48), 0x28FFFFFF);
            ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, contact.playerUuid, PhoneSocialClientData.blockGramPhoto(contact.playerUuid), x(38), y(top + 8), size(31));
            PhoneModernText.drawColored(g, contact.playerName, x(78), y(top + 10), size(9), 0xFFFFFFFF);
            boolean sent = sentTo.contains(contact.playerUuid);
            drawRoundedRect(g, x(202), y(top + 11), x(260), y(top + 37), size(7), sent ? 0xFF245B3B : 0xFF168AFF);
            PhoneModernText.drawCenteredColoredFitted(g, sent ? PhoneSettingsStore.text("Gönderildi", "Sent") : PhoneSettingsStore.text("Gönder", "Send"), x(231), y(top + 18), size(7), size(64), 0xFFFFFFFF);
            top += 55;
        }
        if (contacts.isEmpty()) PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Gönderi paylaşmak için önce birini takip et", "Follow someone first to share posts"), x(150), y(356), size(9), size(236), 0xFF858F9B);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Kapat", "Close"), x(150), y(540), size(8), 0xFF99A3AF);
    }

    private void drawDeleteConfirmation(GuiGraphics g) {
        drawOpaqueModalBackdrop(g, x(3), y(40), x(297), y(606), 0xFF07090E);
        drawRoundedRect(g, x(30), y(226), x(270), y(403), size(20), 0xFF121820);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Gönderiyi sil?", "Delete post?"), x(150), y(258), size(14), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Profilinden de kalıcı olarak kaldırılır.", "It will also be permanently removed from your profile."), x(150), y(291), size(7), size(190), 0xFF9EA8B3);
        drawRoundedRect(g, x(48), y(333), x(143), y(371), size(12), 0xFF28313B);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Vazgeç", "Cancel"), x(95), y(345), size(8), 0xFFFFFFFF);
        drawRoundedRect(g, x(157), y(333), x(252), y(371), size(12), 0xFF8E2D3A);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Sil", "Delete"), x(204), y(345), size(8), 0xFFFFFFFF);
    }

    private void deleteOwnPost() {
        PostEntry post = PhoneBlockGramStore.findPost(postId);
        if (post == null || !post.authorUuid().equals(PhoneClientData.selfUuid())) return;
        PhoneBlockGramStore.removePostLocally(postId);
        PhoneSocialClientData.deleteBlockGram(postId);
        deleteConfirmation = false;
        returnFromPost();
    }

    private void sendComment() {
        String body = commentDraft.toString().trim();
        if (body.isEmpty()) return;
        PhoneSocialClientData.addGramComment(postId, body);
        commentDraft.setLength(0);
        commentFocused = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PostEntry post = PhoneBlockGramStore.findPost(postId);
        if (deleteConfirmation) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (inside(mouseX, mouseY, 48, 333, 143, 371)) { deleteConfirmation = false; return true; }
                if (inside(mouseX, mouseY, 157, 333, 252, 371)) { deleteOwnPost(); return true; }
            }
            return true;
        }
        if (sharePicker) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            if (post == null) { sharePicker = false; return true; }
            List<com.mikasrevs.newphone.network.PhoneSocialStateData.ProfileEntry> contacts = PhoneSocialClientData.profiles().stream()
                    .filter(profile -> profile.playerUuid != null && !profile.playerUuid.isBlank() && !profile.playerUuid.equals(PhoneClientData.selfUuid()))
                .filter(profile -> PhoneSocialClientData.canMessageBlockGram(profile.playerUuid))
                    .toList();
            int top = 276;
            int startIndex = Math.min(shareScroll, Math.max(0, contacts.size() - 1));
            for (int i = startIndex; i < Math.min(startIndex + 4, contacts.size()); i++) {
                var contact = contacts.get(i);
                if (inside(mouseX, mouseY, 196, top + 6, 266, top + 42)) {
                    if (!sentTo.contains(contact.playerUuid) && PhoneClientData.sendGramSharedPost(contact.playerUuid, post)) {
                        sentTo.add(contact.playerUuid);
                        PhoneSocialClientData.recordGramShare(post.id());
                    }
                    return true;
                }
                top += 55;
            }
            if (inside(mouseX, mouseY, 90, 522, 210, 566)) { sharePicker = false; return true; }
            return true;
        }
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (handleDynamicIslandClick(mouseX, mouseY)) return true;
        if (inside(mouseX, mouseY, 18, 48, 48, 78)) { returnFromPost(); return true; }
        if (post != null) {
            if (post.authorUuid().equals(PhoneClientData.selfUuid()) && inside(mouseX, mouseY, 240, 48, 286, 84)) {
                deleteConfirmation = true;
                commentFocused = false;
                return true;
            }
            if (inside(mouseX, mouseY, 18, 345, 75, 375)) {
                PhoneBlockGramStore.toggleLike(post.id(), PhoneClientData.selfUuid(), PhoneClientData.selfName());
                return true;
            }
            if (inside(mouseX, mouseY, 78, 345, 137, 375)) { commentFocused = true; return true; }
            if (inside(mouseX, mouseY, 140, 345, 196, 375)) { sharePicker = true; commentFocused = false; return true; }
            if (inside(mouseX, mouseY, 236, 345, 282, 375)) { PhoneBlockGramStore.toggleSaved(post.id()); return true; }
            int top = 417 - commentScroll;
            for (CommentEntry comment : PhoneBlockGramStore.comments(post.id())) {
                if (inside(mouseX, mouseY, 238, top, 279, top + 40)) {
                    PhoneSocialClientData.toggleGramCommentLike(comment.id());
                    return true;
                }
                top += 48;
            }
        }
        if (inside(mouseX, mouseY, 20, 508, 244, 545)) { commentFocused = true; return true; }
        if (inside(mouseX, mouseY, 245, 507, 282, 546)) { sendComment(); return true; }
        if (isHomeIndicatorTap(mouseX, mouseY)) { ZPhoneClient.openHomeScreen(); return true; }
        commentFocused = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (deleteConfirmation) return true;
        if (sharePicker) {
            if (delta != 0.0D) {
                int count = (int) PhoneSocialClientData.profiles().stream().filter(profile -> profile.playerUuid != null && !profile.playerUuid.isBlank() && !profile.playerUuid.equals(PhoneClientData.selfUuid()))
                    .filter(profile -> PhoneSocialClientData.canMessageBlockGram(profile.playerUuid)).count();
                int max = Math.max(0, count - 4);
                if (delta < 0) shareScroll = Math.min(max, shareScroll + 1); else shareScroll = Math.max(0, shareScroll - 1);
            }
            return true;
        }
        if (!sharePicker && inside(mouseX, mouseY, 18, 409, 282, 501) && delta != 0.0D) {
            int max = Math.max(0, PhoneBlockGramStore.comments(postId).size() * 48 - 86);
            if (delta < 0) commentScroll = Math.min(max, commentScroll + 40);
            else commentScroll = Math.max(0, commentScroll - 40);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (deleteConfirmation || sharePicker) return true;
        if (commentFocused && SharedConstants.isAllowedChatCharacter(codePoint) && commentDraft.length() < PhoneBlockGramStore.MAX_COMMENT_LENGTH) {
            String s = Character.toString(codePoint);
            int remain = PhoneBlockGramStore.MAX_COMMENT_LENGTH - commentDraft.length();
            commentDraft.append(s, 0, Math.min(remain, s.length()));
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void returnFromPost() {
        if (returnTarget != null) PhoneNavigationState.returnTo(this.minecraft, returnTarget);
        else PhoneNavigationState.backOrHome(this.minecraft);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (deleteConfirmation || sharePicker) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (deleteConfirmation) deleteConfirmation = false;
                else sharePicker = false;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnFromPost();
            return true;
        }
        if (commentFocused && keyCode == GLFW.GLFW_KEY_ENTER) { sendComment(); return true; }
        if (commentFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!commentDraft.isEmpty()) commentDraft.deleteCharAt(commentDraft.length() - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

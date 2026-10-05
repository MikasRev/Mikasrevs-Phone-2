package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;

import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore;
import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore.CommentEntry;
import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore.PostEntry;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Native post thread for BlockPost, including replies and multiplayer reactions. */
public final class ZPhoneBlockPostDetailScreen extends AbstractZPhoneScreen {
    private final String postId;
    private final StringBuilder draft = new StringBuilder();
    private boolean inputFocused;
    private String replyToCommentId = "";
    private String replyToName = "";
    private int scroll;

    public ZPhoneBlockPostDetailScreen(String postId) {
        super(Component.literal(PhoneSettingsStore.text("Gönderi", "Post")));
        this.postId = postId == null ? "" : postId;
        PhoneSocialClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawFrame(g);
        drawWallpaper(g);
        drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xF305090D);
        drawStatusBar(g);
        drawBackButton(g);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Gönderi", "Post"), x(150), y(58), size(14), size(190), 0xFFFFFFFF);
        PostEntry post = PhoneBlockPostStore.find(postId);
        if (post == null) PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Gönderi kullanılamıyor", "Post unavailable"), x(150), y(270), size(11), size(236), 0xFFFFFFFF);
        else {
            int commentsTop = drawPost(g, post);
            drawComments(g, post, commentsTop);
            drawComposer(g);
        }
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private int drawPost(GuiGraphics g, PostEntry post) {
        String avatar = post.authorUuid().equals(PhoneClientData.selfUuid()) ? PhoneBlockPostStore.profileAvatarPath() : PhoneSocialClientData.blockPostAvatar(post.authorUuid());
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, post.authorUuid(), avatar, x(24), y(92), size(34));
        PhoneModernText.drawColored(g, post.authorName(), x(66), y(94), size(10), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, post.handle(), x(66), y(115), size(7), 0xFF7F8996);
        ZPhoneSocialRenderUtil.drawWrapped(g, post.body(), x(25), y(142), size(9), 0xFFF0F2F5, 45, 5, size(12));
        int imageTop = 202;
        int actionTop;
        if (post.imagePath() != null && !post.imagePath().isBlank()) {
            drawRoundedRect(g, x(22), y(imageTop), x(278), y(318), size(13), 0xFF161E27);
            ZPhoneSocialRenderUtil.drawImage(g, minecraft, post.imagePath(), x(22), y(imageTop), size(256), size(116));
            actionTop = 331;
        } else actionTop = 223;
        g.fill(x(20), y(actionTop - 8), x(280), y(actionTop - 7), 0x33FFFFFF);
        PhoneModernText.drawColored(g, "◇ " + PhoneBlockPostStore.commentCount(post.id()), x(28), y(actionTop), size(8), 0xFF8A96A4);
        PhoneModernText.drawColored(g, "↻ " + post.reposts(), x(91), y(actionTop), size(8), post.reposted() ? 0xFF31D48D : 0xFF8A96A4);
        PhoneModernText.drawColored(g, (post.liked() ? "♥ " : "♡ ") + post.likes(), x(153), y(actionTop), size(8), post.liked() ? 0xFFFF4D72 : 0xFF8A96A4);
        PhoneModernText.drawColored(g, post.bookmarked() ? "◆" : "◇", x(252), y(actionTop - 1), size(10), post.bookmarked() ? 0xFFFFD45A : 0xFF8A96A4);
        return actionTop + 28;
    }

    private void drawComments(GuiGraphics g, PostEntry post, int commentsTop) {
        List<CommentEntry> comments = PhoneBlockPostStore.comments(post.id());
        int top = commentsTop - scroll;
        g.enableScissor(x(18), y(commentsTop - 2), x(282), y(498));
        for (CommentEntry comment : comments) {
            int indent = comment.parentCommentId() == null || comment.parentCommentId().isBlank() ? 0 : 22;
            if (top + 54 >= commentsTop - 2 && top <= 498) {
                drawRoundedRect(g, x(20 + indent), y(top), x(280), y(top + 50), size(12), (comment.parentCommentId() == null || comment.parentCommentId().isBlank()) ? 0xFF10171F : 0xFF0D141B);
                String avatar = PhoneSocialClientData.blockPostAvatar(comment.authorUuid());
                ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, comment.authorUuid(), avatar, x(28 + indent), y(top + 8), size(26));
                PhoneModernText.drawColored(g, comment.authorName(), x(62 + indent), y(top + 7), size(8), 0xFFFFFFFF);
                ZPhoneSocialRenderUtil.drawWrapped(g, comment.body(), x(62 + indent), y(top + 22), size(7), 0xFFB7C0CA, indent == 0 ? 34 : 30, 2, size(9));
                PhoneModernText.drawColored(g, comment.liked() ? "♥" : "♡", x(251), y(top + 8), size(10), comment.liked() ? 0xFFFF4D72 : 0xFF84909D);
                if (comment.parentCommentId() == null || comment.parentCommentId().isBlank()) PhoneModernText.drawColored(g, PhoneSettingsStore.text("Yanıtla", "Reply"), x(211), y(top + 32), size(6), 0xFF72B6FF);
            }
            top += 56;
        }
        if (comments.isEmpty()) PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("İlk yanıtı sen ver", "Be the first to reply"), x(150), y(Math.min(450, commentsTop + 45)), size(8), size(238), 0xFF7F8A97);
        g.disableScissor();
    }

    private void drawComposer(GuiGraphics g) {
        if (!replyToCommentId.isBlank()) {
            drawRoundedRect(g, x(25), y(493), x(274), y(514), size(9), 0xFF142334);
            PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Yanıtlanan: ", "Replying to ") + replyToName, x(36), y(499), size(6), size(204), 0xFF7FC0FF);
            PhoneModernText.drawColored(g, "×", x(257), y(496), size(10), 0xFFBAC2CB);
        }
        int top = replyToCommentId.isBlank() ? 510 : 518;
        drawRoundedRect(g, x(20), y(top), x(244), y(top + 36), size(14), 0xFF18202A);
        String shown = draft.isEmpty() ? (replyToCommentId.isBlank() ? PhoneSettingsStore.text("Yanıtını gönder", "Post your reply") : PhoneSettingsStore.text("Bir yanıt yaz", "Write a reply")) : draft.toString();
        if (shown.length() > 34) shown = shown.substring(shown.length() - 34);
        PhoneModernText.drawColored(g, shown, x(32), y(top + 11), size(8), draft.isEmpty() ? 0xFF75818E : 0xFFFFFFFF);
        fillCircle(g, x(263), y(top + 18), size(17), draft.toString().trim().isEmpty() ? 0xFF343C46 : 0xFF0A84FF);
        PhoneModernText.drawCenteredColored(g, "↑", x(263), y(top + 8), size(14), 0xFFFFFFFF);
    }

    private void sendReply() {
        String body = draft.toString().trim();
        if (body.isEmpty()) return;
        if (replyToCommentId.isBlank()) PhoneBlockPostStore.addComment(postId, PhoneClientData.selfUuid(), PhoneClientData.selfName(), body);
        else PhoneBlockPostStore.addReply(postId, replyToCommentId, PhoneClientData.selfUuid(), PhoneClientData.selfName(), body);
        draft.setLength(0);
        inputFocused = false;
        replyToCommentId = "";
        replyToName = "";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (handleDynamicIslandClick(mouseX, mouseY)) return true;
        if (inside(mouseX, mouseY, 18, 48, 48, 78)) { PhoneNavigationState.backOrHome(this.minecraft); return true; }
        PostEntry post = PhoneBlockPostStore.find(postId);
        if (post == null) return true;
        int actionTop = post.imagePath() != null && !post.imagePath().isBlank() ? 331 : 223;
        if (inside(mouseX, mouseY, 80, actionTop - 6, 142, actionTop + 25)) { PhoneBlockPostStore.toggleRepost(post.id()); return true; }
        if (inside(mouseX, mouseY, 145, actionTop - 6, 221, actionTop + 25)) { PhoneBlockPostStore.toggleLike(post.id()); return true; }
        if (inside(mouseX, mouseY, 232, actionTop - 6, 280, actionTop + 25)) { PhoneBlockPostStore.toggleBookmark(post.id()); return true; }
        int commentsTop = actionTop + 28;
        int top = commentsTop - scroll;
        for (CommentEntry comment : PhoneBlockPostStore.comments(post.id())) {
            if (inside(mouseX, mouseY, 235, top, 280, top + 27)) { PhoneBlockPostStore.toggleCommentLike(comment.id()); return true; }
            if ((comment.parentCommentId() == null || comment.parentCommentId().isBlank()) && inside(mouseX, mouseY, 196, top + 25, 245, top + 51)) {
                replyToCommentId = comment.id(); replyToName = comment.authorName(); inputFocused = true; return true;
            }
            top += 56;
        }
        if (!replyToCommentId.isBlank() && inside(mouseX, mouseY, 245, 490, 277, 516)) { replyToCommentId = ""; replyToName = ""; return true; }
        int composerTop = replyToCommentId.isBlank() ? 510 : 518;
        if (inside(mouseX, mouseY, 20, composerTop, 244, composerTop + 36)) { inputFocused = true; return true; }
        if (inside(mouseX, mouseY, 245, composerTop, 282, composerTop + 37)) { sendReply(); return true; }
        if (isHomeIndicatorTap(mouseX, mouseY)) { ZPhoneClient.openHomeScreen(); return true; }
        inputFocused = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        PostEntry post = PhoneBlockPostStore.find(postId);
        if (post != null && delta != 0.0D) {
            int actionTop = post.imagePath() != null && !post.imagePath().isBlank() ? 331 : 223;
            int available = 498 - (actionTop + 26);
            int max = Math.max(0, PhoneBlockPostStore.comments(postId).size() * 56 - available);
            if (delta < 0) scroll = Math.min(max, scroll + 45); else scroll = Math.max(0, scroll - 45);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (inputFocused && SharedConstants.isAllowedChatCharacter(codePoint) && draft.length() < PhoneBlockPostStore.MAX_COMMENT_LENGTH) {
            String s = Character.toString(codePoint); draft.append(s, 0, Math.min(s.length(), PhoneBlockPostStore.MAX_COMMENT_LENGTH - draft.length())); return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { PhoneNavigationState.backOrHome(this.minecraft); return true; }
        if (inputFocused && keyCode == GLFW.GLFW_KEY_ENTER) { sendReply(); return true; }
        if (inputFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (!draft.isEmpty()) draft.deleteCharAt(draft.length() - 1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneLegacyAppScreen;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;

import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore;
import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore.CommentEntry;
import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore.PostEntry;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class PhoneBlockPostDetailScreen extends AbstractZPhoneLegacyAppScreen {
    private static DateTimeFormatter detailTime() {
        return DateTimeFormatter.ofPattern("HH:mm · dd MMM yyyy", PhoneSettingsStore.locale());
    }
    private final String postId;
    private final StringBuilder commentDraft = new StringBuilder();
    private final List<CommentHitBox> commentHitBoxes = new ArrayList<>();
    private boolean commentFocused;
    private int commentScrollOffset;
    private int commentsViewportTop;
    private int commentsViewportBottom;
    private int commentsMaxOffset;
    private int commentsThumbTop;
    private int commentsThumbBottom;
    private boolean draggingCommentsScrollbar;
    private int commentsScrollbarGrabOffset;
    private int postActionsTop;
    private String replyingToCommentId = "";
    private String replyingToName = "";
    private String statusText = "";
    private long statusUntil;

    public PhoneBlockPostDetailScreen(String postId) {
        super(Component.literal("BlockPost"));
        this.postId = postId;
        this.commentFocused = true;
        PhoneClientData.requestSync();
        PhoneSocialClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawLegacyAppShell(graphics);
        graphics.fill(screenX(104), screenY(136), screenX(796), screenY(1490), 0xFF000000);
        drawHeader(graphics);

        PostEntry post = PhoneBlockPostStore.find(postId);
        if (post == null) {
            PhoneModernText.drawCenteredColored(
                    graphics,
                    PhoneSettingsStore.text("Gönderi bulunamadı", "Post not found"),
                    screenX(450),
                    screenY(520),
                    screenSize(28),
                    0xFF8093A8
            );
            finishLegacyAppShell(graphics);
            return;
        }

        int postBottom = drawPostDetail(graphics, post);
        drawComments(graphics, postBottom + 12, 1234);
        drawCommentComposer(graphics, mouseX, mouseY);
        drawStatus(graphics);
        finishLegacyAppShell(graphics);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.fill(screenX(104), screenY(136), screenX(796), screenY(236), 0xFF000000);
        PhoneModernText.drawColored(graphics, "‹", screenX(132), screenY(166), screenSize(38), 0xFFF2F2F2);
        PhoneModernText.draw(graphics, PhoneSettingsStore.text("Gönderi", "Post"), screenX(198), screenY(174), screenSize(30));
        PhoneModernText.drawColored(graphics, "X", screenX(744), screenY(174), screenSize(24), 0xFFF2F2F2);
        graphics.fill(screenX(104), screenY(234), screenX(796), screenY(236), 0xFF2F3336);
    }

    private int drawPostDetail(GuiGraphics graphics, PostEntry post) {
        int left = 116;
        int top = 248;
        int right = 770;
        int textHeight = 23;
        List<String> allLines = wrapToWidth(post.body(), 520, textHeight);
        // Detail view must reveal the full post. The feed intentionally previews only
        // a limited number of lines and shows "Devamını göster".
        int shownLines = allLines.size();

        drawAuthorAvatar(graphics, post.authorUuid(), post.authorName(), 136, top + 18, 68);
        PhoneRoleBadgeRenderer.drawNameAndBadge(graphics, post.authorName(), PhoneSocialClientData.roleBadge(post.authorUuid()),
                screenX(220), screenY(top + 14), screenSize(25), 0xFFF4F4F4);
        PhoneModernText.drawColored(graphics, post.handle(), screenX(220), screenY(top + 48), screenSize(17), 0xFF71767B);
        PhoneModernText.drawColored(graphics, "•••", screenX(720), screenY(top + 18), screenSize(20), 0xFF71767B);

        int y = top + 96;
        for (int index = 0; index < shownLines; index++) {
            String line = allLines.get(index);
            PhoneModernText.drawColored(
                    graphics,
                    line,
                    screenX(136),
                    screenY(y),
                    screenSize(textHeight),
                    line.contains("#") ? 0xFF1D9BF0 : 0xFFE7E9EA
            );
            y += 31;
        }
        if (hasImage(post)) {
            drawPostImage(graphics, post, 136, y + 8, 614, 184);
            y += 202;
        }

        int comments = PhoneBlockPostStore.commentCount(post.id());
        long views = Math.max(18L, post.likes() * 17L + post.reposts() * 9L + comments * 4L);
        String time = detailTime().format(Instant.ofEpochMilli(post.createdAt()).atZone(ZoneId.systemDefault()));
        PhoneModernText.drawColored(
                graphics,
                time + " · " + views + PhoneSettingsStore.text(" görüntülenme", " views"),
                screenX(136),
                screenY(y + 10),
                screenSize(15),
                0xFF71767B
        );
        graphics.fill(screenX(126), screenY(y + 42), screenX(760), screenY(y + 44), 0xFF2F3336);

        this.postActionsTop = y + 48;
        drawPostAction(graphics, 136, this.postActionsTop, 278, "○  " + comments, 0xFF71767B);
        drawPostAction(graphics, 286, this.postActionsTop, 428, "↻  " + post.reposts(),
                post.reposted() ? 0xFF00BA7C : 0xFF71767B);
        drawPostAction(graphics, 436, this.postActionsTop, 578, "♥  " + post.likes(),
                post.liked() ? 0xFFF91880 : 0xFF71767B);
        drawPostAction(graphics, 586, this.postActionsTop, 750, post.bookmarked() ? "◆" : "◇",
                post.bookmarked() ? 0xFF1D9BF0 : 0xFF71767B);

        int bottom = this.postActionsTop + 50;
        graphics.fill(screenX(left), screenY(bottom), screenX(right), screenY(bottom + 2), 0xFF2F3336);
        return bottom + 2;
    }

    private void drawPostAction(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            String label,
            int color
    ) {
        PhoneModernText.drawCenteredColoredFitted(graphics, label, screenX((left + right) / 2),
                screenY(top + 13), screenSize(18), screenSize(right - left - 16), color);
    }

    private void drawComments(GuiGraphics graphics, int top, int bottom) {
        this.commentHitBoxes.clear();
        this.commentsViewportTop = Math.min(top + 42, bottom);
        this.commentsViewportBottom = bottom;
        PhoneModernText.draw(graphics, PhoneSettingsStore.text("Yanıtlar", "Replies"), screenX(132), screenY(top), screenSize(25));

        List<DisplayComment> displayComments = flattenComments(PhoneBlockPostStore.comments(postId));
        if (displayComments.isEmpty()) {
            this.commentsMaxOffset = 0;
            PhoneModernText.drawCenteredColored(
                    graphics,
                    PhoneSettingsStore.text("İlk yanıtı sen gönder", "Be the first to reply"),
                    screenX(450),
                    screenY(top + 92),
                    screenSize(21),
                0xFF71767B
            );
            return;
        }

        int totalHeight = 0;
        for (DisplayComment display : displayComments) {
            totalHeight += commentHeight(display) + 8;
        }
        int viewportHeight = Math.max(1, this.commentsViewportBottom - this.commentsViewportTop);
        this.commentsMaxOffset = Math.max(0, totalHeight - viewportHeight);
        this.commentScrollOffset = Math.max(0, Math.min(this.commentScrollOffset, this.commentsMaxOffset));

        graphics.enableScissor(
                screenX(106),
                screenY(this.commentsViewportTop),
                screenX(790),
                screenY(this.commentsViewportBottom)
        );
        int y = this.commentsViewportTop - this.commentScrollOffset;
        for (DisplayComment display : displayComments) {
            int height = commentHeight(display);
            if (y + height >= this.commentsViewportTop && y <= this.commentsViewportBottom) {
                drawComment(graphics, display, y, height);
            }
            y += height + 8;
        }
        graphics.disableScissor();
        drawCommentsScrollbar(graphics, totalHeight);
    }

    private List<DisplayComment> flattenComments(List<CommentEntry> comments) {
        List<DisplayComment> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (CommentEntry comment : comments) {
            if (comment.parentCommentId() == null || comment.parentCommentId().isBlank()) {
                addCommentTree(result, comments, comment, 0, visited);
            }
        }
        for (CommentEntry comment : comments) {
            if (!visited.contains(comment.id())) {
                addCommentTree(result, comments, comment, 0, visited);
            }
        }
        return result;
    }

    private void addCommentTree(
            List<DisplayComment> result,
            List<CommentEntry> all,
            CommentEntry comment,
            int depth,
            Set<String> visited
    ) {
        if (!visited.add(comment.id())) {
            return;
        }
        result.add(new DisplayComment(comment, Math.min(2, depth)));
        for (CommentEntry reply : all) {
            if (comment.id().equals(reply.parentCommentId())) {
                addCommentTree(result, all, reply, depth + 1, visited);
            }
        }
    }

    private int commentHeight(DisplayComment display) {
        int maxWidth = display.depth() == 0 ? 492 : 438;
        int lines = Math.max(1, wrapToWidth(display.comment().body(), maxWidth, 19).size());
        return Math.max(142, 104 + lines * 26);
    }

    private void drawComment(GuiGraphics graphics, DisplayComment display, int top, int height) {
        CommentEntry comment = display.comment();
        int depth = display.depth();
        int left = depth == 0 ? 120 : 166;
        int avatarLeft = depth == 0 ? 136 : 180;
        int avatarSize = depth == 0 ? 56 : 46;
        int textLeft = depth == 0 ? 208 : 240;
        int right = 770;

        graphics.fill(screenX(left), screenY(top), screenX(right), screenY(top + height), 0xFF000000);
        if (depth > 0) {
            graphics.fill(screenX(150), screenY(top - 8), screenX(154), screenY(top + height - 20), 0xFF2F3336);
        }
        drawAuthorAvatar(graphics, comment.authorUuid(), comment.authorName(), avatarLeft, top + 16, avatarSize);
        PhoneRoleBadgeRenderer.drawNameAndBadge(graphics, comment.authorName(), PhoneSocialClientData.roleBadge(comment.authorUuid()),
                screenX(textLeft), screenY(top + 13), screenSize(depth == 0 ? 21 : 19), 0xFFF4F4F4);
        PhoneModernText.drawColored(
                graphics,
                comment.handle() + " · " + relativeTime(comment.createdAt()),
                screenX(textLeft),
                screenY(top + 42),
                screenSize(15),
                0xFF71767B
        );

        int textY = top + 72;
        int maxWidth = depth == 0 ? 492 : 438;
        for (String line : wrapToWidth(comment.body(), maxWidth, 19)) {
            PhoneModernText.draw(graphics, line, screenX(textLeft), screenY(textY), screenSize(19));
            textY += 26;
        }

        int actionY = top + height - 36;
        int replyCount = PhoneBlockPostStore.replyCount(comment.id());
        int replyLeft = textLeft;
        int replyRight = textLeft + 150;
        int likeLeft = textLeft + 190;
        int likeRight = textLeft + 330;
        PhoneModernText.drawColored(
                graphics,
                PhoneSettingsStore.text("○ Yanıtla", "○ Reply") + (replyCount > 0 ? " " + replyCount : ""),
                screenX(replyLeft),
                screenY(actionY),
                screenSize(15),
                0xFF71767B
        );
        PhoneModernText.drawColored(
                graphics,
                "♥ " + comment.likes(),
                screenX(likeLeft),
                screenY(actionY),
                screenSize(15),
                comment.liked() ? 0xFFF91880 : 0xFF71767B
        );
        graphics.fill(screenX(left), screenY(top + height - 2), screenX(right), screenY(top + height), 0xFF2F3336);

        int visibleTop = Math.max(top, this.commentsViewportTop);
        int visibleBottom = Math.min(top + height, this.commentsViewportBottom);
        if (visibleBottom > visibleTop) {
            commentHitBoxes.add(new CommentHitBox(
                    comment.id(),
                    comment.authorName(),
                    comment.authorUuid(),
                    screenX(avatarLeft),
                    screenY(top + 8),
                    screenX(right - 18),
                    screenY(top + 66),
                    screenX(replyLeft),
                    screenY(actionY - 8),
                    screenX(replyRight),
                    screenY(actionY + 28),
                    screenX(likeLeft),
                    screenY(actionY - 8),
                    screenX(likeRight),
                    screenY(actionY + 28),
                    screenY(this.commentsViewportTop),
                    screenY(this.commentsViewportBottom)
            ));
        }
    }

    private void drawCommentsScrollbar(GuiGraphics graphics, int totalHeight) {
        int top = this.commentsViewportTop;
        int bottom = this.commentsViewportBottom;
        int viewportHeight = Math.max(1, bottom - top);
        int trackLeft = 776;
        int trackRight = 786;
        graphics.fill(screenX(trackLeft), screenY(top), screenX(trackRight), screenY(bottom), 0xA6151515);
        if (totalHeight <= viewportHeight || this.commentsMaxOffset <= 0) {
            this.commentsThumbTop = top;
            this.commentsThumbBottom = bottom;
            return;
        }
        int thumbHeight = Math.max(54, Math.round(viewportHeight * (viewportHeight / (float) totalHeight)));
        int travel = Math.max(1, viewportHeight - thumbHeight);
        int thumbTop = top + Math.round(travel * (this.commentScrollOffset / (float) this.commentsMaxOffset));
        this.commentsThumbTop = thumbTop;
        this.commentsThumbBottom = thumbTop + thumbHeight;
        graphics.fill(screenX(trackLeft + 2), screenY(thumbTop), screenX(trackRight - 2), screenY(thumbTop + thumbHeight), 0xFF71767B);
    }

    private void setCommentsScrollFromMouse(double mouseY, boolean keepGrabOffset) {
        if (this.commentsMaxOffset <= 0) {
            this.commentScrollOffset = 0;
            return;
        }
        int viewportHeight = this.commentsViewportBottom - this.commentsViewportTop;
        int thumbHeight = Math.max(1, this.commentsThumbBottom - this.commentsThumbTop);
        int travel = Math.max(1, viewportHeight - thumbHeight);
        double textureMouseY = (mouseY - screenY(this.commentsViewportTop))
                / Math.max(0.0001F, getPhoneDrawData().scale())
                + this.commentsViewportTop;
        double desiredTop = keepGrabOffset
                ? textureMouseY - this.commentsScrollbarGrabOffset
                : textureMouseY - thumbHeight / 2.0;
        double progress = Math.max(0.0, Math.min(1.0, (desiredTop - this.commentsViewportTop) / travel));
        this.commentScrollOffset = (int) Math.round(progress * this.commentsMaxOffset);
    }

    private void drawCommentComposer(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = 116;
        int top = 1242;
        int right = 784;
        int bottom = 1384;
        graphics.fill(screenX(left), screenY(top), screenX(right), screenY(bottom), 0xFF000000);
        graphics.fill(screenX(left), screenY(top), screenX(right), screenY(top + 2), 0xFF2F3336);

        if (!replyingToCommentId.isBlank()) {
            drawRoundedRect(graphics, screenX(132), screenY(top + 10), screenX(760), screenY(top + 40),
                    screenSize(12), 0xFF0F1419);
            PhoneModernText.drawColoredFitted(
                    graphics,
                    PhoneSettingsStore.text("", "Replying to ") + replyingToName
                            + PhoneSettingsStore.text(" kullanıcısına yanıt veriyorsun", ""),
                    screenX(148),
                    screenY(top + 17),
                    screenSize(14),
                    screenSize(520),
                    0xFF1D9BF0
            );
            PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("İptal", "Cancel"), screenX(708), screenY(top + 17), screenSize(14), 0xFFFF8C8C);
        }

        int inputTop = replyingToCommentId.isBlank() ? top + 18 : top + 46;
        drawAuthorAvatar(graphics, selfUuid(), selfName(), 134, inputTop + 8, 54);
        drawRoundedRect(graphics, screenX(204), screenY(inputTop), screenX(612), screenY(inputTop + 68),
                screenSize(22), commentFocused ? 0xFF16181C : 0xFF0F1113);

        if (commentDraft.isEmpty()) {
            PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Yanıtını gönder", "Send your reply"),
                    screenX(226), screenY(inputTop + 22), screenSize(18), 0xFF71767B);
        } else {
            List<String> lines = wrapToWidth(commentDraft.toString(), 360, 18);
            int y = inputTop + 13;
            int start = Math.max(0, lines.size() - 2);
            for (int index = start; index < lines.size(); index++) {
                PhoneModernText.draw(graphics, lines.get(index), screenX(226), screenY(y), screenSize(18));
                y += 24;
            }
        }

        int remaining = PhoneBlockPostStore.MAX_COMMENT_LENGTH - commentDraft.length();
        PhoneModernText.drawColored(
                graphics,
                remaining + "/280",
                screenX(520),
                screenY(inputTop + 74),
                screenSize(13),
                remaining <= 24 ? 0xFFFF6B6B : 0xFF71767B
        );

        boolean sendEnabled = !commentDraft.toString().isBlank();
        boolean hover = inside(mouseX, mouseY, 628, inputTop + 4, 764, inputTop + 66);
        drawRoundedRect(graphics, screenX(628), screenY(inputTop + 4), screenX(764), screenY(inputTop + 66),
                screenSize(22), sendEnabled ? (hover ? 0xFF35A7F5 : 0xFF1D9BF0) : 0xFF202327);
        PhoneModernText.drawCenteredColoredFitted(graphics, PhoneSettingsStore.text("Yanıtla", "Reply"),
                screenX(696), screenY(inputTop + 23), screenSize(20), screenSize(112),
                sendEnabled ? 0xFFFFFFFF : 0xFF71767B);
    }

    private void drawPostImage(GuiGraphics graphics, PostEntry post, int left, int top, int width, int height) {
        String pathValue = post.imagePath();
        if (pathValue == null || pathValue.isBlank()) return;
        if (pathValue.startsWith("social:")) {
            PhonePhotoStore.PhotoTexture texture = PhonePhotoStore.textureFor(this.minecraft, pathValue.substring("social:".length()));
            if (texture != null) {
                graphics.blit(texture.location(), screenX(left), screenY(top), screenSize(width), screenSize(height), 0, 0,
                        texture.width(), texture.height(), texture.width(), texture.height());
                return;
            }
        }
        try {
            Path path = Path.of(pathValue);
            if (!Files.exists(path)) {
                graphics.fill(screenX(left), screenY(top), screenX(left + width), screenY(top + height), 0xFF16181C);
                return;
            }
            PhoneGalleryStore.GalleryTexture texture = PhoneGalleryStore.textureFor(this.minecraft, path);
            if (texture == null) return;
            graphics.blit(texture.location(), screenX(left), screenY(top), screenSize(width), screenSize(height), 0, 0,
                    texture.width(), texture.height(), texture.width(), texture.height());
        } catch (RuntimeException ignored) {
        }
    }

    private boolean drawSocialOrLocalAvatar(GuiGraphics graphics, String pathValue, int left, int top, int size) {
        if (pathValue == null || pathValue.isBlank()) return false;
        if (pathValue.startsWith("social:")) {
            PhonePhotoStore.PhotoTexture texture = PhonePhotoStore.textureFor(this.minecraft, pathValue.substring("social:".length()));
            if (texture == null) return false;
            graphics.blit(texture.location(), screenX(left), screenY(top), screenSize(size), screenSize(size), 0, 0,
                    texture.width(), texture.height(), texture.width(), texture.height());
            return true;
        }
        try {
            Path path = Path.of(pathValue);
            if (!Files.exists(path)) return false;
            PhoneGalleryStore.GalleryTexture texture = PhoneGalleryStore.textureFor(this.minecraft, path);
            if (texture == null) return false;
            graphics.blit(texture.location(), screenX(left), screenY(top), screenSize(size), screenSize(size), 0, 0,
                    texture.width(), texture.height(), texture.width(), texture.height());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void drawAuthorAvatar(GuiGraphics graphics, String uuid, String name, int left, int top, int size) {
        String socialAvatar = uuid != null && uuid.equals(selfUuid())
                ? PhoneBlockPostStore.profileAvatarPath()
                : PhoneSocialClientData.blockPostAvatar(uuid);
        if (drawSocialOrLocalAvatar(graphics, socialAvatar, left, top, size)) return;
        if (uuid != null && !uuid.isBlank() && (uuid.equals(selfUuid()) || uuid.contains("-"))) {
            PhonePlayerHeadRenderer.draw(graphics, uuid, screenX(left), screenY(top), screenSize(size));
            return;
        }
        int color = avatarColor(name);
        graphics.fill(screenX(left), screenY(top), screenX(left + size), screenY(top + size), color);
        String initial = name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        PhoneModernText.drawCentered(graphics, initial, screenX(left + size / 2), screenY(top + 12), screenSize(size / 2));
    }

    private void publishComment() {
        String body = commentDraft.toString().strip();
        if (body.isBlank()) {
            showStatus(PhoneSettingsStore.text("Yorum alanı boş", "Comment cannot be empty"));
            return;
        }
        if (replyingToCommentId.isBlank()) {
            PhoneBlockPostStore.addComment(postId, selfUuid(), selfName(), body);
        } else {
            PhoneBlockPostStore.addReply(postId, replyingToCommentId, selfUuid(), selfName(), body);
        }
        commentDraft.setLength(0);
        commentFocused = false;
        replyingToCommentId = "";
        replyingToName = "";
        this.commentScrollOffset = this.commentsMaxOffset;
        showStatus(PhoneSettingsStore.text("Yanıt gönderildi", "Reply sent"));
    }

    private void startReply(CommentHitBox hitBox) {
        this.replyingToCommentId = hitBox.commentId();
        this.replyingToName = hitBox.authorName();
        this.commentFocused = true;
    }

    private void cancelReply() {
        this.replyingToCommentId = "";
        this.replyingToName = "";
    }

    private void showStatus(String text) {
        statusText = text == null ? "" : text;
        statusUntil = System.currentTimeMillis() + 2200L;
    }

    private void drawStatus(GuiGraphics graphics) {
        if (statusText.isBlank() || System.currentTimeMillis() > statusUntil) {
            return;
        }
        drawRoundedRect(graphics, screenX(250), screenY(1192), screenX(650), screenY(1236),
                screenSize(16), 0xEE1D9BF0);
        PhoneModernText.drawCenteredFitted(graphics, statusText, screenX(450), screenY(1204),
                screenSize(16), screenSize(370));
    }

    private boolean hasImage(PostEntry post) {
        return post.imagePath() != null && !post.imagePath().isBlank();
    }

    private String selfUuid() {
        String value = PhoneClientData.selfUuid();
        if (!value.isBlank()) {
            return value;
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            return this.minecraft.player.getUUID().toString();
        }
        return "local-player";
    }

    private String selfName() {
        String value = PhoneClientData.selfName();
        if (!value.isBlank()) {
            return value;
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            return this.minecraft.player.getName().getString();
        }
        return PhoneSettingsStore.text("Oyuncu", "Player");
    }

    private String relativeTime(long timestamp) {
        long seconds = Math.max(0L, Duration.ofMillis(System.currentTimeMillis() - timestamp).toSeconds());
        if (seconds < 60) {
            return PhoneSettingsStore.text("şimdi", "now");
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + PhoneSettingsStore.text("dk", "m");
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + PhoneSettingsStore.text("sa", "h");
        }
        return (hours / 24) + PhoneSettingsStore.text("g", "d");
    }

    private List<String> wrapToWidth(String text, int maxTextureWidth, int textHeight) {
        return PhoneModernText.wrap(text, screenSize(textHeight), screenSize(maxTextureWidth));
    }

    private int avatarColor(String name) {
        int hash = name == null ? 0 : name.hashCode();
        int red = 45 + Math.floorMod(hash, 90);
        int green = 70 + Math.floorMod(hash / 7, 100);
        int blue = 100 + Math.floorMod(hash / 17, 120);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private boolean inside(double mouseX, double mouseY, int left, int top, int right, int bottom) {
        return mouseX >= screenX(left) && mouseX <= screenX(right)
                && mouseY >= screenY(top) && mouseY <= screenY(bottom);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (commentFocused
                && SharedConstants.isAllowedChatCharacter(codePoint)
                && commentDraft.length() < PhoneBlockPostStore.MAX_COMMENT_LENGTH) {
            String typed = Character.toString(codePoint);
            int remaining = PhoneBlockPostStore.MAX_COMMENT_LENGTH - commentDraft.length();
            commentDraft.append(typed, 0, Math.min(typed.length(), remaining));
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
                && handleBridgeIslandClick(mouseX, mouseY)) return true;
        if (handleHomeIndicatorClick(mouseX, mouseY, button)) {
            return true;
        }
        if (inside(mouseX, mouseY, 122, 154, 190, 236)) {
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }

        if (inside(mouseX, mouseY, 770, this.commentsViewportTop, 792, this.commentsViewportBottom)
                && this.commentsMaxOffset > 0) {
            double textureY = (mouseY - screenY(this.commentsViewportTop))
                    / Math.max(0.0001F, getPhoneDrawData().scale())
                    + this.commentsViewportTop;
            if (textureY >= this.commentsThumbTop && textureY <= this.commentsThumbBottom) {
                this.draggingCommentsScrollbar = true;
                this.commentsScrollbarGrabOffset = (int) Math.round(textureY - this.commentsThumbTop);
            } else {
                this.commentsScrollbarGrabOffset = Math.max(1, (this.commentsThumbBottom - this.commentsThumbTop) / 2);
                setCommentsScrollFromMouse(mouseY, false);
                this.draggingCommentsScrollbar = true;
            }
            return true;
        }

        PostEntry post = PhoneBlockPostStore.find(postId);
        if (post != null) {
            if (inside(mouseX, mouseY, 128, 252, 690, 330)
                    && post.authorUuid() != null && !post.authorUuid().isBlank()) {
                this.minecraft.setScreen(new PhoneBlockPostUserProfileScreen(post.authorUuid()));
                return true;
            }
            if (inside(mouseX, mouseY, 136, this.postActionsTop, 278, this.postActionsTop + 46)) {
                commentFocused = true;
                return true;
            }
            if (inside(mouseX, mouseY, 286, this.postActionsTop, 428, this.postActionsTop + 46)) {
                PhoneBlockPostStore.toggleRepost(postId);
                return true;
            }
            if (inside(mouseX, mouseY, 436, this.postActionsTop, 578, this.postActionsTop + 46)) {
                PhoneBlockPostStore.toggleLike(postId);
                return true;
            }
            if (inside(mouseX, mouseY, 586, this.postActionsTop, 750, this.postActionsTop + 46)) {
                PhoneBlockPostStore.toggleBookmark(postId);
                return true;
            }
        }

        for (CommentHitBox hitBox : commentHitBoxes) {
            if (hitBox.insideAuthor(mouseX, mouseY)
                    && hitBox.authorUuid() != null && !hitBox.authorUuid().isBlank()) {
                this.minecraft.setScreen(new PhoneBlockPostUserProfileScreen(hitBox.authorUuid()));
                return true;
            }
            if (hitBox.insideReply(mouseX, mouseY)) {
                startReply(hitBox);
                return true;
            }
            if (hitBox.insideLike(mouseX, mouseY)) {
                PhoneBlockPostStore.toggleCommentLike(hitBox.commentId());
                return true;
            }
        }

        if (!replyingToCommentId.isBlank() && inside(mouseX, mouseY, 690, 1252, 764, 1290)) {
            cancelReply();
            return true;
        }
        if (inside(mouseX, mouseY, 204, 1248, 612, 1360)) {
            commentFocused = true;
            return true;
        }
        if (inside(mouseX, mouseY, 628, 1248, 764, 1362)) {
            publishComment();
            return true;
        }
        commentFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (y >= screenY(this.commentsViewportTop)
                && y <= screenY(this.commentsViewportBottom)
                && delta != 0.0D) {
            int step = 58;
            if (delta < 0.0D) {
                this.commentScrollOffset = Math.min(this.commentsMaxOffset, this.commentScrollOffset + step);
            } else {
                this.commentScrollOffset = Math.max(0, this.commentScrollOffset - step);
            }
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingCommentsScrollbar) {
            setCommentsScrollFromMouse(mouseY, true);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingCommentsScrollbar) {
            this.draggingCommentsScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (commentFocused) {
                commentFocused = false;
                return true;
            }
            if (!replyingToCommentId.isBlank()) {
                cancelReply();
                return true;
            }
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }
        if (commentFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!commentDraft.isEmpty()) {
                    commentDraft.deleteCharAt(commentDraft.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                publishComment();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.commentScrollOffset = Math.min(this.commentsMaxOffset, this.commentScrollOffset + 220);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            this.commentScrollOffset = Math.max(0, this.commentScrollOffset - 220);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private record DisplayComment(CommentEntry comment, int depth) {
    }

    private record CommentHitBox(
            String commentId,
            String authorName,
            String authorUuid,
            int authorLeft,
            int authorTop,
            int authorRight,
            int authorBottom,
            int replyLeft,
            int replyTop,
            int replyRight,
            int replyBottom,
            int likeLeft,
            int likeTop,
            int likeRight,
            int likeBottom,
            int viewportTop,
            int viewportBottom
    ) {
        boolean insideAuthor(double x, double y) {
            return y >= viewportTop && y <= viewportBottom
                    && x >= authorLeft && x <= authorRight && y >= authorTop && y <= authorBottom;
        }

        boolean insideReply(double x, double y) {
            return y >= viewportTop && y <= viewportBottom
                    && x >= replyLeft && x <= replyRight && y >= replyTop && y <= replyBottom;
        }

        boolean insideLike(double x, double y) {
            return y >= viewportTop && y <= viewportBottom
                    && x >= likeLeft && x <= likeRight && y >= likeTop && y <= likeBottom;
        }
    }
}

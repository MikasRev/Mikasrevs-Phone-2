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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Full X-style player profile. */
public final class ZPhoneXProfileScreen extends AbstractZPhoneScreen {
    private enum ProfileTab { POSTS, REPLIES, MEDIA, LIKES }

    private final String playerUuid;
    private final String playerName;
    private ProfileTab tab = ProfileTab.POSTS;
    private int scroll;
    private final List<DeleteHitBox> deleteHitBoxes = new ArrayList<>();
    private String pendingDeletePostId = "";

    public ZPhoneXProfileScreen(String playerUuid) {
        this(playerUuid, resolvePlayerName(playerUuid));
    }

    public ZPhoneXProfileScreen(String playerUuid, String playerName) {
        super(Component.literal(PhoneSettingsStore.text("Profil", "Profile")));
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.playerName = playerName == null || playerName.isBlank() ? PhoneSettingsStore.text("Oyuncu", "Player") : playerName;
        PhoneSocialClientData.requestSync();
    }

    private static String resolvePlayerName(String playerUuid) {
        var profile = PhoneSocialClientData.profile(playerUuid == null ? "" : playerUuid);
        return profile == null ? PhoneSettingsStore.text("Oyuncu", "Player") : profile.playerName;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawFrame(g);
        drawWallpaper(g);
        drawRoundedRect(g, x(8), y(40), x(292), y(602), size(22), 0xF3070B10);
        drawStatusBar(g);
        if (!pendingDeletePostId.isBlank()) {
            drawDeleteConfirmation(g);
            drawHomeIndicator(g);
            drawFrame(g);
            return;
        }
        drawBackButton(g);
        PhoneModernText.drawCenteredColoredFitted(g, playerName, x(150), y(55), size(12), size(188), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, postCount() + PhoneSettingsStore.text(" gönderi", " posts"), x(150), y(74), size(6), 0xFF7D8996);
        drawProfile(g);
        drawPosts(g);
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void drawProfile(GuiGraphics g) {
        boolean self = playerUuid.equals(PhoneClientData.selfUuid());
        String cover = self ? PhoneBlockPostStore.profileCoverPath() : PhoneSocialClientData.blockPostCover(playerUuid);
        String avatar = self ? PhoneBlockPostStore.profileAvatarPath() : PhoneSocialClientData.blockPostAvatar(playerUuid);

        drawRoundedRect(g, x(17), y(91), x(283), y(181), size(13), 0xFF14202B);
        ZPhoneSocialRenderUtil.drawImage(g, minecraft, cover, x(17), y(91), size(266), size(90));
        fillCircle(g, x(67), y(181), size(36), 0xFF070B10);
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, playerUuid, avatar, x(37), y(151), size(60));

        if (!self) {
            boolean blocked = PhoneSocialClientData.isSocialBlocked(playerUuid);
            String state = PhoneSocialClientData.followState("BLOCKPOST", PhoneClientData.selfUuid(), playerUuid);
            int bg = blocked ? 0xFF151A20 : "ACCEPTED".equals(state) ? 0xFF151A20 : "PENDING".equals(state) ? 0xFF25364A : 0xFFF4F6F8;
            int fg = blocked ? 0xFF7E8996 : "NONE".equals(state) ? 0xFF070A0E : 0xFFFFFFFF;
            drawRoundedRect(g, x(174), y(191), x(228), y(222), size(14), bg);
            PhoneModernText.drawCenteredColoredFitted(g, blocked ? PhoneSettingsStore.text("Engellendi", "Blocked") : "ACCEPTED".equals(state) ? PhoneSettingsStore.text("Takip ediliyor", "Following") : "PENDING".equals(state) ? PhoneSettingsStore.text("İstek gönderildi", "Requested") : PhoneSettingsStore.text("Takip et", "Follow"), x(201), y(200), size(5), size(46), fg);
            drawRoundedRect(g, x(233), y(191), x(281), y(222), size(14), blocked ? 0xFF223044 : 0xFF3A171D);
            PhoneModernText.drawCenteredColoredFitted(g, blocked ? PhoneSettingsStore.text("Engeli kaldır", "Unblock") : PhoneSettingsStore.text("Engelle", "Block"), x(257), y(200), size(5), size(40), blocked ? 0xFFB9D4FF : 0xFFFF7785);
        } else {
            drawRoundedRect(g, x(194), y(191), x(276), y(222), size(14), 0xFF161C22);
            PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Profilin", "Your profile"), x(235), y(200), size(7), size(72), 0xFFFFFFFF);
        }

        PhoneModernText.drawColoredFitted(g, playerName, x(25), y(226), size(15), size(250), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, "@" + safeHandle(playerName), x(25), y(249), size(8), 0xFF788591);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Minecraft oyuncusu · X profili", "Minecraft player · X profile"), x(25), y(271), size(8), size(250), 0xFFD9DFE6);

        int following = PhoneSocialClientData.followingCount("BLOCKPOST", playerUuid);
        int followers = PhoneSocialClientData.followerCount("BLOCKPOST", playerUuid);
        PhoneModernText.drawColored(g, Integer.toString(following), x(25), y(294), size(8), 0xFFFFFFFF);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Takip edilen", "Following"), x(48), y(294), size(7), size(58), 0xFF83909D);
        PhoneModernText.drawColored(g, Integer.toString(followers), x(111), y(294), size(8), 0xFFFFFFFF);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Takipçiler", "Followers"), x(135), y(294), size(7), size(84), 0xFF83909D);

        drawTab(g, ProfileTab.POSTS, 42, PhoneSettingsStore.text("Gönderiler", "Posts"));
        drawTab(g, ProfileTab.REPLIES, 108, PhoneSettingsStore.text("Yanıtlar", "Replies"));
        drawTab(g, ProfileTab.MEDIA, 180, PhoneSettingsStore.text("Medya", "Media"));
        drawTab(g, ProfileTab.LIKES, 252, PhoneSettingsStore.text("Beğeniler", "Likes"));
        g.fill(x(18), y(337), x(282), y(338), 0x32FFFFFF);
    }

    private void drawTab(GuiGraphics g, ProfileTab item, int centerX, String text) {
        boolean active = tab == item;
        PhoneModernText.drawCenteredColoredFitted(g, text, x(centerX), y(316), size(7), size(56), active ? 0xFFFFFFFF : 0xFF7E8996);
        if (active) drawRoundedRect(g, x(centerX - 22), y(333), x(centerX + 22), y(336), size(1), 0xFF1D9BF0);
    }

    private void drawPosts(GuiGraphics g) {
        deleteHitBoxes.clear();
        int top = 347 - scroll;
        g.enableScissor(x(15), y(340), x(285), y(563));
        int shown = 0;
        if (tab == ProfileTab.REPLIES) {
            for (PostEntry parent : PhoneBlockPostStore.all()) {
                for (CommentEntry comment : PhoneBlockPostStore.comments(parent.id())) {
                    if (!playerUuid.equals(comment.authorUuid())) continue;
                    if (top + 70 >= 340 && top <= 563) {
                        g.fill(x(18), y(top + 67), x(282), y(top + 68), 0x24FFFFFF);
                        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Yanıtlanan @", "Replying to @") + safeHandle(parent.authorName()), x(26), y(top + 7), size(6), 0xFF1D9BF0);
                        PhoneModernText.drawColored(g, comment.authorName(), x(26), y(top + 22), size(8), 0xFFFFFFFF);
                        ZPhoneSocialRenderUtil.drawWrapped(g, comment.body(), x(26), y(top + 39), size(7), 0xFFDCE2E8, 44, 2, size(9));
                    }
                    top += 70;
                    shown++;
                }
            }
        } else {
            for (PostEntry post : visiblePosts()) {
                int h = post.imagePath() == null || post.imagePath().isBlank() ? 110 : 175;
                if (top + h >= 340 && top <= 563) drawCompactPost(g, post, top, h);
                top += h;
                shown++;
            }
        }
        if (shown == 0) {
            String text = tab == ProfileTab.REPLIES ? PhoneSettingsStore.text("Henüz yanıt yok", "No replies yet") : tab == ProfileTab.MEDIA ? PhoneSettingsStore.text("Henüz medya yok", "No media yet") : tab == ProfileTab.LIKES ? PhoneSettingsStore.text("Henüz beğenilen gönderi yok", "No liked posts yet") : PhoneSettingsStore.text("Henüz gönderi yok", "No posts yet");
            PhoneModernText.drawCenteredColoredFitted(g, text, x(150), y(410), size(9), size(244), 0xFF7E8996);
        }
        g.disableScissor();
    }

    private void drawCompactPost(GuiGraphics g, PostEntry post, int top, int h) {
        String avatar = post.authorUuid().equals(PhoneClientData.selfUuid()) ? PhoneBlockPostStore.profileAvatarPath() : PhoneSocialClientData.blockPostAvatar(post.authorUuid());
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, post.authorUuid(), avatar, x(24), y(top + 10), size(31));
        PhoneModernText.drawColored(g, post.authorName(), x(63), y(top + 9), size(9), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, post.handle(), x(63), y(top + 26), size(7), 0xFF7D8996);
        if (playerUuid.equals(PhoneClientData.selfUuid()) && playerUuid.equals(post.authorUuid())) {
            drawRoundedRect(g, x(247), y(top + 7), x(276), y(top + 29), size(8), 0xFF3A171D);
            PhoneModernText.drawCenteredColored(g, "×", x(261), y(top + 8), size(10), 0xFFFF6B78);
            deleteHitBoxes.add(new DeleteHitBox(post.id(), top + 4, top + 32));
        }
        ZPhoneSocialRenderUtil.drawWrapped(g, post.body(), x(63), y(top + 43), size(7), 0xFFE3E8ED, 33, 2, size(9));
        if (post.imagePath() != null && !post.imagePath().isBlank()) {
            drawRoundedRect(g, x(63), y(top + 72), x(270), y(top + 142), size(10), 0xFF151B22);
            ZPhoneSocialRenderUtil.drawImage(g, minecraft, post.imagePath(), x(63), y(top + 72), size(207), size(70));
        }
        int actions = top + h - 25;
        PhoneModernText.drawColored(g, "○ " + PhoneBlockPostStore.commentCount(post.id()), x(63), y(actions), size(6), 0xFF7E8996);
        PhoneModernText.drawColored(g, "↻ " + post.reposts(), x(116), y(actions), size(6), post.reposted() ? 0xFF00BA7C : 0xFF7E8996);
        PhoneModernText.drawColored(g, "♡ " + post.likes(), x(170), y(actions), size(6), post.liked() ? 0xFFF91880 : 0xFF7E8996);
        g.fill(x(18), y(top + h - 1), x(282), y(top + h), 0x25FFFFFF);
    }

    private void drawDeleteConfirmation(GuiGraphics g) {
        drawOpaqueModalBackdrop(g, x(8), y(40), x(292), y(602), 0xFF070B10);
        drawRoundedRect(g, x(42), y(216), x(258), y(382), size(18), 0xFF151B22);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Gönderiyi sil?", "Delete post?"), x(150), y(244), size(14), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Bu işlem geri alınamaz.", "This action cannot be undone."), x(150), y(274), size(7), size(190), 0xFF909BA7);
        drawRoundedRect(g, x(58), y(318), x(142), y(354), size(11), 0xFF252D36);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Vazgeç", "Cancel"), x(100), y(329), size(8), 0xFFFFFFFF);
        drawRoundedRect(g, x(158), y(318), x(242), y(354), size(11), 0xFF8C2933);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Sil", "Delete"), x(200), y(329), size(8), 0xFFFFFFFF);
    }

    private List<PostEntry> visiblePosts() {
        return PhoneBlockPostStore.all().stream().filter(post -> switch (tab) {
            case POSTS -> playerUuid.equals(post.authorUuid());
            case MEDIA -> playerUuid.equals(post.authorUuid()) && post.imagePath() != null && !post.imagePath().isBlank();
            case LIKES -> playerUuid.equals(PhoneClientData.selfUuid()) && post.liked();
            case REPLIES -> false;
        }).toList();
    }

    private int postCount() {
        return (int) PhoneBlockPostStore.all().stream().filter(p -> playerUuid.equals(p.authorUuid())).count();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!pendingDeletePostId.isBlank()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (inside(mouseX, mouseY, 58, 318, 142, 354)) {
                    pendingDeletePostId = "";
                    return true;
                }
                if (inside(mouseX, mouseY, 158, 318, 242, 354)) {
                    String postId = pendingDeletePostId;
                    pendingDeletePostId = "";
                    PhoneBlockPostStore.delete(postId, PhoneClientData.selfUuid());
                    return true;
                }
            }
            return true;
        }
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (handleDynamicIslandClick(mouseX, mouseY)) return true;
        if (inside(mouseX, mouseY, 18, 48, 48, 78)) { PhoneNavigationState.backOrHome(this.minecraft); return true; }

        boolean self = playerUuid.equals(PhoneClientData.selfUuid());
        if (!self && inside(mouseX, mouseY, 232, 185, 283, 228)) {
            if (PhoneSocialClientData.isSocialBlocked(playerUuid)) PhoneSocialClientData.unblockSocialUser(playerUuid);
            else PhoneSocialClientData.blockSocialUser(playerUuid);
            return true;
        }
        if (!self && inside(mouseX, mouseY, 171, 185, 231, 228)) {
            if (PhoneSocialClientData.isSocialBlocked(playerUuid)) return true;
            String state = PhoneSocialClientData.followState("BLOCKPOST", PhoneClientData.selfUuid(), playerUuid);
            if ("ACCEPTED".equals(state)) PhoneSocialClientData.removeFollow("BLOCKPOST", playerUuid);
            else if (!"PENDING".equals(state)) PhoneSocialClientData.sendFollowRequest("BLOCKPOST", playerUuid);
            return true;
        }
        if (inside(mouseX, mouseY, 20, 284, 102, 310)) { minecraft.setScreen(new ZPhoneXPeopleScreen(playerUuid, playerName, ZPhoneXPeopleScreen.Mode.FOLLOWING)); return true; }
        if (inside(mouseX, mouseY, 104, 284, 198, 310)) { minecraft.setScreen(new ZPhoneXPeopleScreen(playerUuid, playerName, ZPhoneXPeopleScreen.Mode.FOLLOWERS)); return true; }

        if (inside(mouseX, mouseY, 15, 308, 75, 340)) { tab = ProfileTab.POSTS; scroll = 0; return true; }
        if (inside(mouseX, mouseY, 76, 308, 144, 340)) { tab = ProfileTab.REPLIES; scroll = 0; return true; }
        if (inside(mouseX, mouseY, 145, 308, 216, 340)) { tab = ProfileTab.MEDIA; scroll = 0; return true; }
        if (inside(mouseX, mouseY, 217, 308, 285, 340)) { tab = ProfileTab.LIKES; scroll = 0; return true; }

        for (DeleteHitBox hitBox : deleteHitBoxes) {
            if (inside(mouseX, mouseY, 242, hitBox.top(), 282, hitBox.bottom())) {
                pendingDeletePostId = hitBox.postId();
                return true;
            }
        }

        if (tab != ProfileTab.REPLIES) {
            int top = 347 - scroll;
            for (PostEntry post : visiblePosts()) {
                int h = post.imagePath() == null || post.imagePath().isBlank() ? 110 : 175;
                if (inside(mouseX, mouseY, 18, top, 282, top + h)) { minecraft.setScreen(new ZPhoneBlockPostDetailScreen(post.id())); return true; }
                top += h;
            }
        }
        if (isHomeIndicatorTap(mouseX, mouseY)) { ZPhoneClient.openHomeScreen(); return true; }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!pendingDeletePostId.isBlank()) return true;
        if (delta == 0.0D) return true;
        int total;
        if (tab == ProfileTab.REPLIES) {
            int count = 0;
            for (PostEntry post : PhoneBlockPostStore.all()) for (CommentEntry c : PhoneBlockPostStore.comments(post.id())) if (playerUuid.equals(c.authorUuid())) count++;
            total = count * 70;
        } else {
            total = visiblePosts().stream().mapToInt(p -> p.imagePath() == null || p.imagePath().isBlank() ? 110 : 175).sum();
        }
        int max = Math.max(0, total - 215);
        if (delta < 0) scroll = Math.min(max, scroll + 55); else scroll = Math.max(0, scroll - 55);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!pendingDeletePostId.isBlank()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) pendingDeletePostId = "";
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private record DeleteHitBox(String postId, int top, int bottom) { }

    private String safeHandle(String name) {
        return name == null ? "player" : name.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
    }
}

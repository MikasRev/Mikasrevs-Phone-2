package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneLegacyAppScreen;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneXPeopleScreen;

import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore;
import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore.PostEntry;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Other-user BlockPost profile, using the same layout as the local profile page. */
public final class PhoneBlockPostUserProfileScreen extends AbstractZPhoneLegacyAppScreen {
    private static DateTimeFormatter joinedFormat() { return DateTimeFormatter.ofPattern("MMMM yyyy", PhoneSettingsStore.locale()); }

    private enum ProfileTab { POSTS, REPLIES, MEDIA, LIKES }

    private final String targetUuid;
    private ProfileTab profileTab = ProfileTab.POSTS;
    private int scroll;
    private int maxScroll;
    private String pendingDeletePostId = "";

    public PhoneBlockPostUserProfileScreen(String targetUuid) {
        super(Component.literal(PhoneSettingsStore.text("X Profili", "X Profile")));
        this.targetUuid = targetUuid == null ? "" : targetUuid;
        PhoneSocialClientData.requestSync();
        PhoneSocialClientData.requestProfileAssets(this.targetUuid);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawLegacyAppShell(g);
        g.fill(screenX(104), screenY(136), screenX(796), screenY(1490), 0xFF000000);
        if (!pendingDeletePostId.isBlank()) {
            drawDeleteConfirmation(g);
            finishLegacyAppShell(g);
            return;
        }
        drawTopBar(g);
        drawProfile(g, mouseX, mouseY);
        drawFeed(g);
        finishLegacyAppShell(g);
    }

    private void drawTopBar(GuiGraphics g) {
        PhoneModernText.drawCenteredColored(g, "‹", screenX(142), screenY(166), screenSize(39), 0xFFFFFFFF);
        PhoneModernText.draw(g, PhoneSettingsStore.text("Profil", "Profile"), screenX(194), screenY(174), screenSize(30));
        PhoneModernText.drawColored(g, "X", screenX(744), screenY(176), screenSize(23), 0xFFF2F2F2);
        g.fill(screenX(104), screenY(230), screenX(796), screenY(232), 0xFF2F3336);
    }

    private void drawProfile(GuiGraphics g, int mouseX, int mouseY) {
        PhoneSocialStateData.ProfileEntry profile = PhoneSocialClientData.profile(targetUuid);
        String name = profile == null || profile.playerName == null || profile.playerName.isBlank() ? PhoneSettingsStore.text("Oyuncu", "Player") : profile.playerName;
        String badge = PhoneSocialClientData.roleBadge(targetUuid);
        String bio = PhoneSocialClientData.blockPostBio(targetUuid);

        int bannerTop = 232;
        int bannerBottom = 462;
        drawCover(g, 104, bannerTop, 692, bannerBottom - bannerTop);
        g.fill(screenX(104), screenY(bannerBottom - 3), screenX(796), screenY(bannerBottom), 0xFF2F3336);
        g.fill(screenX(124), screenY(380), screenX(272), screenY(528), 0xFF000000);
        drawAvatar(g, 132, 388, 132);

        boolean self = targetUuid.equals(PhoneClientData.selfUuid());
        boolean buttonHover = inside(mouseX, mouseY, 548, 478, 764, 532);
        String followState = self ? "" : PhoneSocialClientData.followState(
                "BLOCKPOST", PhoneClientData.selfUuid(), targetUuid);
        int buttonColor;
        String buttonLabel;
        if (self) {
            buttonColor = buttonHover ? 0xFF202327 : 0xFF000000;
            buttonLabel = PhoneSettingsStore.text("Profili düzenle", "Edit profile");
        } else {
            buttonColor = "ACCEPTED".equals(followState) || "PENDING".equals(followState) ? 0xFF202327 : 0xFFF2F2F2;
            if (buttonHover) buttonColor = "ACCEPTED".equals(followState) || "PENDING".equals(followState)
                    ? 0xFF2F3336 : 0xFFD7D9DA;
            buttonLabel = "ACCEPTED".equals(followState) ? PhoneSettingsStore.text("Takip ediliyor", "Following")
                    : "PENDING".equals(followState) ? PhoneSettingsStore.text("İstek gönderildi", "Requested")
                    : PhoneSettingsStore.text("Takip et", "Follow");
        }
        drawRoundedRect(g, screenX(548), screenY(478), screenX(764), screenY(532),
                screenSize(20), buttonColor);
        PhoneModernText.drawCenteredColoredFitted(g, buttonLabel, screenX(656), screenY(494),
                screenSize(17), screenSize(188), !self && !"ACCEPTED".equals(followState)
                        && !"PENDING".equals(followState) ? 0xFF0F1419 : 0xFFF2F2F2);

        PhoneRoleBadgeRenderer.drawNameAndBadge(g, name, badge, screenX(132), screenY(540), screenSize(31), 0xFFF2F2F2);
        PhoneModernText.drawColored(g, "@" + handle(name), screenX(132), screenY(580), screenSize(18), 0xFF71767B);
        String shownBio = bio == null || bio.isBlank()
                ? PhoneSettingsStore.text("Minecraft oyuncusu • X kullanıcısı", "Minecraft player • X user")
                : PhoneSettingsStore.localizeNetworkText(bio);
        List<String> bioLines = wrap(shownBio, 634, 18);
        for (int index = 0; index < Math.min(2, bioLines.size()); index++) {
            PhoneModernText.drawColored(g, bioLines.get(index), screenX(132), screenY(616 + index * 24),
                    screenSize(18), 0xFFE7E9EA);
        }
        String joined = joinedFormat().format(Instant.ofEpochMilli(1_720_000_000_000L).atZone(ZoneId.systemDefault()));
        PhoneModernText.drawColoredFitted(g,
                PhoneSettingsStore.text("▣  ", "▣  Joined ") + joined
                        + PhoneSettingsStore.text(" tarihinde katıldı", ""),
                screenX(132), screenY(674), screenSize(15), screenSize(620), 0xFF71767B);

        int following = PhoneSocialClientData.followingCount("BLOCKPOST", targetUuid);
        int followers = PhoneSocialClientData.followerCount("BLOCKPOST", targetUuid);
        PhoneModernText.draw(g, Integer.toString(following), screenX(132), screenY(712), screenSize(19));
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Takip edilen", "Following"),
                screenX(168), screenY(715), screenSize(15), screenSize(134), 0xFF71767B);
        PhoneModernText.draw(g, Integer.toString(followers), screenX(326), screenY(712), screenSize(19));
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Takipçi", "Followers"),
                screenX(364), screenY(715), screenSize(15), screenSize(130), 0xFF71767B);

        drawProfileTab(g, mouseX, mouseY, ProfileTab.POSTS, 190,
                PhoneSettingsStore.text("Gönderiler", "Posts"));
        drawProfileTab(g, mouseX, mouseY, ProfileTab.REPLIES, 360,
                PhoneSettingsStore.text("Yanıtlar", "Replies"));
        drawProfileTab(g, mouseX, mouseY, ProfileTab.MEDIA, 530,
                PhoneSettingsStore.text("Medya", "Media"));
        drawProfileTab(g, mouseX, mouseY, ProfileTab.LIKES, 700,
                PhoneSettingsStore.text("Beğeniler", "Likes"));
    }

    private void drawProfileTab(GuiGraphics g, int mouseX, int mouseY, ProfileTab tab, int centerX, String label) {
        boolean active = profileTab == tab;
        boolean hover = inside(mouseX, mouseY, centerX - 82, 770, centerX + 82, 838);
        int color = active ? 0xFFF2F2F2 : hover ? 0xFFD6D9DB : 0xFF71767B;
        PhoneModernText.drawCenteredColoredFitted(g, label, screenX(centerX), screenY(790),
                screenSize(15), screenSize(144), color);
        if (active) drawRoundedRect(g, screenX(centerX - 45), screenY(832), screenX(centerX + 45),
                screenY(838), screenSize(3), 0xFF1D9BF0);
        g.fill(screenX(104), screenY(838), screenX(796), screenY(840), 0xFF2F3336);
    }

    private List<PostEntry> filteredPosts() {
        List<PostEntry> authored = PhoneBlockPostStore.all().stream().filter(p -> targetUuid.equals(p.authorUuid())).toList();
        if (profileTab == ProfileTab.MEDIA) return authored.stream().filter(this::hasImage).toList();
        if (profileTab == ProfileTab.REPLIES) {
            return PhoneBlockPostStore.all().stream()
                    .filter(post -> PhoneBlockPostStore.comments(post.id()).stream()
                            .anyMatch(comment -> targetUuid.equals(comment.authorUuid())))
                    .toList();
        }
        if (profileTab == ProfileTab.LIKES) {
            if (!targetUuid.equals(PhoneClientData.selfUuid())) return List.of();
            return PhoneBlockPostStore.all().stream().filter(PostEntry::liked).toList();
        }
        return authored;
    }

    private void drawFeed(GuiGraphics g) {
        List<PostEntry> posts = filteredPosts();
        int viewportTop = 842;
        int viewportBottom = 1382;
        int total = 0;
        for (PostEntry post : posts) total += postHeight(post) + 6;
        maxScroll = Math.max(0, total - (viewportBottom - viewportTop));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int top = viewportTop - scroll;
        g.enableScissor(screenX(106), screenY(viewportTop), screenX(790), screenY(viewportBottom));
        for (PostEntry post : posts) {
            int height = postHeight(post);
            if (top + height >= viewportTop && top <= viewportBottom) drawPost(g, post, top, height);
            top += height + 6;
        }
        g.disableScissor();
        if (posts.isEmpty()) {
            String text = profileTab == ProfileTab.POSTS ? PhoneSettingsStore.text("Henüz gönderi yok", "No posts yet") : PhoneSettingsStore.text("Bu sekmede henüz içerik yok", "No content in this tab yet");
            PhoneModernText.drawCenteredColored(g, text, screenX(450), screenY(960), screenSize(22), 0xFF71767B);
        }
    }

    private int postHeight(PostEntry post) {
        int lines = Math.max(1, Math.min(4, wrap(post.body(), 560, 19).size()));
        return hasImage(post) ? 334 + lines * 25 : 174 + lines * 25;
    }

    private void drawPost(GuiGraphics g, PostEntry post, int top, int height) {
        int left = 116;
        int right = 770;
        g.fill(screenX(left), screenY(top), screenX(right), screenY(top + height), 0xFF000000);
        drawAvatar(g, 136, top + 18, 62);
        PhoneRoleBadgeRenderer.drawNameAndBadge(g, post.authorName(), PhoneSocialClientData.roleBadge(post.authorUuid()),
                screenX(214), screenY(top + 14), screenSize(22), 0xFFF4F4F4);
        PhoneModernText.drawColored(g, post.handle(), screenX(214), screenY(top + 45), screenSize(15), 0xFF71767B);
        if (targetUuid.equals(PhoneClientData.selfUuid())) {
            drawRoundedRect(g, screenX(688), screenY(top + 12), screenX(754), screenY(top + 48),
                    screenSize(14), 0xFF2A1117);
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Sil", "Delete"),
                    screenX(721), screenY(top + 20), screenSize(15), 0xFFF4212E);
        }
        List<String> lines = wrap(post.body(), 560, 19);
        int textY = top + 79;
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            PhoneModernText.drawColored(g, lines.get(i), screenX(214), screenY(textY), screenSize(19), 0xFFE7E9EA);
            textY += 25;
        }
        if (hasImage(post)) {
            drawImage(g, post.imagePath(), 214, textY + 6, 520, 154);
            textY += 168;
        }
        PhoneModernText.drawColored(g, "○  " + PhoneBlockPostStore.commentCount(post.id()), screenX(214), screenY(top + height - 43), screenSize(20), 0xFF71767B);
        PhoneModernText.drawColored(g, "↻  " + post.reposts(), screenX(350), screenY(top + height - 43), screenSize(20), post.reposted() ? 0xFF00BA7C : 0xFF71767B);
        PhoneModernText.drawColored(g, "♥  " + post.likes(), screenX(486), screenY(top + height - 43), screenSize(20), post.liked() ? 0xFFF91880 : 0xFF71767B);
        g.fill(screenX(left), screenY(top + height - 2), screenX(right), screenY(top + height), 0xFF2F3336);
    }

    private void drawAvatar(GuiGraphics g, int left, int top, int size) {
        String path = PhoneSocialClientData.blockPostAvatar(targetUuid);
        if (!drawImage(g, path, left, top, size, size)) PhonePlayerHeadRenderer.draw(g, targetUuid, screenX(left), screenY(top), screenSize(size));
    }

    private void drawCover(GuiGraphics g, int left, int top, int width, int height) {
        String path = PhoneSocialClientData.blockPostCover(targetUuid);
        if (!drawImage(g, path, left, top, width, height)) {
            g.fill(screenX(left), screenY(top), screenX(left + width), screenY(top + height), 0xFF242424);
            g.fill(screenX(left), screenY(top), screenX(left + width), screenY(top + 46), 0xFF333333);
            g.fill(screenX(left), screenY(top + height - 36), screenX(left + width), screenY(top + height), 0xFF1A1A1A);
        }
    }

    private boolean drawImage(GuiGraphics g, String path, int left, int top, int width, int height) {
        if (path == null || path.isBlank()) return false;
        String ref = path.startsWith("social:") ? path.substring("social:".length()) : path;
        PhonePhotoStore.PhotoTexture texture = PhonePhotoStore.textureFor(minecraft, ref);
        if (texture == null) return false;
        g.blit(texture.location(), screenX(left), screenY(top), screenSize(width), screenSize(height), 0, 0,
                texture.width(), texture.height(), texture.width(), texture.height());
        return true;
    }

    private List<String> wrap(String value, int maxWidth, int height) {
        return PhoneModernText.wrap(value, screenSize(height), screenSize(maxWidth));
    }

    private void drawDeleteConfirmation(GuiGraphics g) {
        drawOpaqueModalBackdrop(g, screenX(104), screenY(136), screenX(796), screenY(1490), 0xFF000000);
        drawRoundedRect(g, screenX(230), screenY(610), screenX(670), screenY(856),
                screenSize(24), 0xFF16181C);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Gönderiyi sil?", "Delete post?"), screenX(450), screenY(652), screenSize(30), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Bu işlem geri alınamaz.", "This action cannot be undone."), screenX(450), screenY(708), screenSize(17), 0xFF71767B);
        drawRoundedRect(g, screenX(274), screenY(770), screenX(430), screenY(826),
                screenSize(18), 0xFF2F3336);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Vazgeç", "Cancel"), screenX(352), screenY(786), screenSize(18), 0xFFE7E9EA);
        drawRoundedRect(g, screenX(470), screenY(770), screenX(626), screenY(826),
                screenSize(18), 0xFFF4212E);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Sil", "Delete"), screenX(548), screenY(786), screenSize(18), 0xFFFFFFFF);
    }

    private boolean hasImage(PostEntry post) { return post.imagePath() != null && !post.imagePath().isBlank(); }
    private String handle(String name) { return name == null ? "player" : name.toLowerCase(Locale.ROOT).replace(" ", ""); }
    private boolean inside(double mx, double my, int l, int t, int r, int b) { return mx >= screenX(l) && mx <= screenX(r) && my >= screenY(t) && my <= screenY(b); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!pendingDeletePostId.isBlank()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (inside(mouseX, mouseY, 274, 770, 430, 826)) { pendingDeletePostId = ""; return true; }
                if (inside(mouseX, mouseY, 470, 770, 626, 826)) {
                    String postId = pendingDeletePostId;
                    pendingDeletePostId = "";
                    PhoneBlockPostStore.delete(postId, PhoneClientData.selfUuid());
                    return true;
                }
            }
            return true;
        }
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
                && handleBridgeIslandClick(mouseX, mouseY)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (inside(mouseX, mouseY, 108, 148, 184, 232)) { PhoneNavigationState.backOrHome(this.minecraft); return true; }
        boolean self = targetUuid.equals(PhoneClientData.selfUuid());
        PhoneSocialStateData.ProfileEntry targetProfile = PhoneSocialClientData.profile(targetUuid);
        String targetName = targetProfile == null || targetProfile.playerName == null || targetProfile.playerName.isBlank() ? PhoneSettingsStore.text("Oyuncu", "Player") : targetProfile.playerName;
        if (inside(mouseX, mouseY, 124, 696, 304, 752)) {
            minecraft.setScreen(new ZPhoneXPeopleScreen(targetUuid, targetName, ZPhoneXPeopleScreen.Mode.FOLLOWING));
            return true;
        }
        if (inside(mouseX, mouseY, 312, 696, 492, 752)) {
            minecraft.setScreen(new ZPhoneXPeopleScreen(targetUuid, targetName, ZPhoneXPeopleScreen.Mode.FOLLOWERS));
            return true;
        }
        if (inside(mouseX, mouseY, 548, 478, 764, 532)) {
            if (self) minecraft.setScreen(new PhoneBlockPostProfileEditScreen());
            else {
                String state = PhoneSocialClientData.followState("BLOCKPOST", PhoneClientData.selfUuid(), targetUuid);
                if ("ACCEPTED".equals(state) || "PENDING".equals(state)) PhoneSocialClientData.removeFollow("BLOCKPOST", targetUuid);
                else PhoneSocialClientData.sendFollowRequest("BLOCKPOST", targetUuid);
            }
            return true;
        }
        if (inside(mouseX, mouseY, 108, 770, 272, 838)) { profileTab = ProfileTab.POSTS; scroll = 0; return true; }
        if (inside(mouseX, mouseY, 278, 770, 442, 838)) { profileTab = ProfileTab.REPLIES; scroll = 0; return true; }
        if (inside(mouseX, mouseY, 448, 770, 612, 838)) { profileTab = ProfileTab.MEDIA; scroll = 0; return true; }
        if (inside(mouseX, mouseY, 618, 770, 782, 838)) { profileTab = ProfileTab.LIKES; scroll = 0; return true; }

        int top = 842 - scroll;
        for (PostEntry post : filteredPosts()) {
            int height = postHeight(post);
            if (self && top + height >= 842 && top <= 1382
                    && inside(mouseX, mouseY, 684, Math.max(842, top + 8), 758, Math.min(1382, top + 52))) {
                pendingDeletePostId = post.id();
                return true;
            }
            if (inside(mouseX, mouseY, 116, top, 770, top + height)) { minecraft.setScreen(new PhoneBlockPostDetailScreen(post.id())); return true; }
            top += height + 6;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!pendingDeletePostId.isBlank()) return true;
        if (delta < 0) scroll = Math.min(maxScroll, scroll + 64);
        else if (delta > 0) scroll = Math.max(0, scroll - 64);
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
}

package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;

import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore;
import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore.PostEntry;
import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore.StoryEntry;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhoneRoleBadgeRenderer;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import com.mikasrevs.newphone.network.PhoneStateData;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Native Z-Phone BlockGram frontend. Social state remains server-authoritative. */
public final class ZPhoneBlockGramScreen extends AbstractZPhoneScreen {
    private enum Tab { HOME, EXPLORE, DMS, ALERTS, PROFILE }
    private enum Editor { NONE, POST, STORY, PROFILE }
    private enum ExploreSection { PEOPLE, INCOMING, OUTGOING }
    private enum HomeFeed { FOR_YOU, FOLLOWING }

    private static final int HOME_LIST_TOP = 161;
    private static final int HOME_POST_HEIGHT = 400;
    private static final int HOME_IMAGE_TOP = 43;
    private static final int HOME_IMAGE_BOTTOM = 307;
    private static final int HOME_ACTION_TOP = 313;
    private static final int HOME_ACTION_BOTTOM = 344;

    private static Editor pendingCameraEditor = Editor.NONE;
    private static String pendingCameraDraft = "";

    private Tab tab = Tab.HOME;
    private Editor editor = Editor.NONE;
    private int scroll;
    private long lastSyncAt;
    private final StringBuilder draft = new StringBuilder();
    private final StringBuilder bioDraft = new StringBuilder();
    private boolean textFocused;
    private final StringBuilder searchQuery = new StringBuilder();
    private boolean searchFocused;
    private ExploreSection exploreSection = ExploreSection.PEOPLE;
    private HomeFeed homeFeed = HomeFeed.FOR_YOU;
    private boolean profileSavedTab;
    private Path selectedPhoto;
    private StoryEntry storyViewer;
    private int storyOffset;
    private List<StoryEntry> storySequence = List.of();
    private int storyIndex;
    private long storyStartedAt;
    private String fullscreenImagePath = "";
    private String heartPostId = "";
    private long heartAnimationStartedAt;
    private String pendingImagePostId = "";
    private String pendingImagePath = "";
    private long pendingImageClickAt;
    private boolean scrollBarDragging;
    private double scrollBarGrabOffset;
    private int scrollThumbTop = 151;
    private int scrollThumbBottom = 540;
    private String lastServerNotice = "";
    private String socialToast = "";
    private long socialToastUntil;

    /** Clears temporary editor/camera state after disconnects or forced phone closure. */
    public static void resetTransientState() {
        pendingCameraEditor = Editor.NONE;
        pendingCameraDraft = "";
    }

    public ZPhoneBlockGramScreen() {
        super(Component.literal("Lumi"));
        this.lastServerNotice = PhoneClientData.notice();
        PhoneSocialClientData.requestSync();
        PhoneClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        resolvePendingImageClick();
        syncOccasionally();
        captureSocialNotice();
        drawFrame(graphics);
        drawWallpaper(graphics);
        drawRoundedRect(graphics, x(3), y(40), x(297), y(606), size(22), 0xFF050608);
        drawStatusBar(graphics);
        if (!fullscreenImagePath.isBlank()) {
            drawFullscreenImage(graphics);
            drawHomeIndicator(graphics);
            drawFrame(graphics);
            return;
        }
        if (editor != Editor.NONE) {
            drawEditor(graphics, mouseX, mouseY);
            drawHomeIndicator(graphics);
            drawFrame(graphics);
            return;
        }
        if (storyViewer != null) {
            advanceStoryIfNeeded();
            if (storyViewer != null) {
                drawStoryViewer(graphics);
                drawHomeIndicator(graphics);
                drawFrame(graphics);
                return;
            }
        }
        drawHeader(graphics);
        if (tab == Tab.HOME) drawHome(graphics, mouseX, mouseY);
        else if (tab == Tab.EXPLORE) drawExplore(graphics);
        else if (tab == Tab.DMS) drawDms(graphics, mouseX, mouseY);
        else if (tab == Tab.ALERTS) drawAlerts(graphics, mouseX, mouseY);
        else drawProfile(graphics, mouseX, mouseY);
        drawScrollBar(graphics);
        drawBottomNav(graphics);
        drawSocialToast(graphics);
        drawHomeIndicator(graphics);
        drawFrame(graphics);
    }

    private void syncOccasionally() {
        long now = System.currentTimeMillis();
        if (now - lastSyncAt > 4000L) {
            lastSyncAt = now;
            PhoneSocialClientData.requestSync();
            PhoneClientData.requestSync();
        }
    }

    private void captureSocialNotice() {
        String current = PhoneClientData.notice();
        if (current == null) current = "";
        if (current.equals(lastServerNotice)) return;
        lastServerNotice = current;
        if (current.isBlank()) return;
        socialToast = PhoneSettingsStore.localizeNetworkText(current);
        socialToastUntil = System.currentTimeMillis() + 3200L;
    }

    private void drawSocialToast(GuiGraphics g) {
        if (socialToast.isBlank() || System.currentTimeMillis() >= socialToastUntil) return;
        String shown = socialToast.length() > 54 ? socialToast.substring(0, 53) + "…" : socialToast;
        drawRoundedRect(g, x(22), y(505), x(278), y(536), size(8), 0xF51A1B1F);
        g.fill(x(30), y(505), x(270), y(506), 0xFF34363B);
        PhoneModernText.drawCenteredColoredFitted(g, shown, x(150), y(515), size(7), size(236), 0xFFFFFFFF);
    }

    private void drawHeader(GuiGraphics g) {
        int unread = PhoneSocialClientData.unreadNotificationCount("BLOCKGRAM", PhoneClientData.selfUuid());
        PhoneModernText.drawCenteredColored(g, "+", x(34), y(53), size(22), 0xFFFFFFFF);
        if (tab == Tab.HOME) drawHomeFeedTabs(g);
        else PhoneModernText.drawCenteredColoredFitted(g, tabName(), x(150), y(59), size(13), size(174), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, "♡", x(266), y(54), size(19), 0xFFFFFFFF);
        if (unread > 0) {
            fillCircle(g, x(276), y(54), size(5), 0xFFFF304F);
        }
        g.fill(x(17), y(86), x(283), y(87), 0x24FFFFFF);
        if (tab == Tab.EXPLORE) {
            drawRoundedRect(g, x(20), y(94), x(280), y(125), size(9), 0xFF17181C);
            PhoneModernText.drawColoredFitted(g, searchQuery.isEmpty() ? PhoneSettingsStore.text("Nick yaz ve oyuncu ara", "Search players by nickname") : searchQuery.toString(), x(38), y(103), size(8), size(204), searchQuery.isEmpty() ? 0xFF727C89 : 0xFFFFFFFF);
            PhoneModernText.drawColored(g, "⌕", x(255), y(99), size(12), 0xFF9AA4B1);
            if (searchFocused && isCaretVisible()) {
                int caretX = x(38 + Math.min(26, searchQuery.length()) * 5);
                g.fill(caretX, y(101), caretX + Math.max(1, size(1)), y(114), 0xFFFFFFFF);
            }
        }
    }

    private void drawHomeFeedTabs(GuiGraphics g) {
        boolean forYou = homeFeed == HomeFeed.FOR_YOU;
        PhoneModernText.drawCenteredColored(g, "Lumi", x(150), y(48), size(5), 0xFF777D87);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Sana Özel", "For You"),
                x(105), y(62), size(8), size(78), forYou ? 0xFFFFFFFF : 0xFF777D87);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Takip", "Following"),
                x(195), y(62), size(8), size(82), forYou ? 0xFF777D87 : 0xFFFFFFFF);
        int left = forYou ? 69 : 158;
        int right = forYou ? 141 : 232;
        drawRoundedRect(g, x(left), y(82), x(right), y(84), size(1), 0xFFFFFFFF);
    }

    private String socialName(String uuid, String name) {
        return name + "  " + PhoneSocialClientData.roleBadge(uuid);
    }

    private String tabName() {
        return switch (tab) {
            case HOME -> PhoneSettingsStore.text("Ana Sayfa", "Home");
            case EXPLORE -> PhoneSettingsStore.text("Ara", "Search");
            case DMS -> PhoneSettingsStore.text("Mesajlar", "Messages");
            case ALERTS -> PhoneSettingsStore.text("Hareketler", "Activity");
            case PROFILE -> PhoneSettingsStore.text("Profil", "Profile");
        };
    }

    private void drawHome(GuiGraphics g, int mouseX, int mouseY) {
        drawStories(g);
        List<PostEntry> posts = homeFeedPosts();
        int top = HOME_LIST_TOP - scroll;
        g.enableScissor(x(12), y(151), x(288), y(540));
        if (posts.isEmpty()) {
            String title = homeFeed == HomeFeed.FOLLOWING
                    ? PhoneSettingsStore.text("Takip akışın henüz boş", "Your following feed is empty")
                    : PhoneSettingsStore.text("Akışın henüz sessiz", "Your feed is quiet");
            String hint = homeFeed == HomeFeed.FOLLOWING
                    ? PhoneSettingsStore.text("Takip ettiğin kişilerin gönderileri burada görünür", "Posts from people you follow appear here")
                    : PhoneSettingsStore.text("Oyuncuları takip et veya ilk fotoğrafını paylaş", "Follow players or share your first photo");
            PhoneModernText.drawCenteredColoredFitted(g, title, x(150), y(286), size(12), size(250), 0xFFFFFFFF);
            PhoneModernText.drawCenteredColoredFitted(g, hint, x(150), y(309), size(7), size(250), 0xFF7F8996);
        }
        for (PostEntry post : posts) {
            drawPostCard(g, post, top, mouseX, mouseY);
            top += HOME_POST_HEIGHT;
        }
        g.disableScissor();
    }

    private void drawStories(GuiGraphics g) {
        String selfPhoto = PhoneBlockGramStore.profilePhotoPath();
        List<StoryEntry> ownStories = PhoneBlockGramStore.storiesByAuthor(PhoneClientData.selfUuid());
        boolean hasOwnStory = !ownStories.isEmpty();
        fillCircle(g, x(42), y(112), size(hasOwnStory ? 22 : 20), hasOwnStory ? 0xFFFF304F : 0xFF3A3B40);
        if (hasOwnStory) fillCircle(g, x(42), y(112), size(20), 0xFF050608);
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, PhoneClientData.selfUuid(), selfPhoto, x(24), y(94), size(36));
        fillCircle(g, x(57), y(128), size(7), 0xFF168AFF);
        PhoneModernText.drawCenteredColored(g, "+", x(57), y(123), size(9), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Hikayen", "Your story"), x(42), y(137), size(6), 0xFFCFD4DB);

        List<StoryEntry> stories = PhoneBlockGramStore.stories().stream()
                .filter(story -> !story.authorUuid().equals(PhoneClientData.selfUuid())).toList();
        int visible = 0;
        for (int i = storyOffset; i < stories.size() && visible < 4; i++) {
            StoryEntry story = stories.get(i);
            int center = 93 + visible * 51;
            boolean unseen = PhoneBlockGramStore.hasUnseenStories(story.authorUuid());
            if (unseen) {
                fillCircle(g, x(center), y(112), size(22), 0xFFFFB000);
                fillCircle(g, x(center), y(112), size(20), 0xFFFF304F);
            } else {
                fillCircle(g, x(center), y(112), size(21), 0xFF4C4D52);
            }
            fillCircle(g, x(center), y(112), size(18), 0xFF050608);
            ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, story.authorUuid(), PhoneSocialClientData.blockGramPhoto(story.authorUuid()), x(center - 16), y(96), size(32));
            String name = story.authorName();
            if (name.length() > 8) name = name.substring(0, 7) + "…";
            PhoneModernText.drawCenteredColored(g, name, x(center), y(137), size(6), 0xFFCFD4DB);
            visible++;
        }
        if (storyOffset > 0) PhoneModernText.drawCenteredColored(g, "‹", x(70), y(105), size(12), 0xFFB5BDC7);
        if (storyOffset + 4 < stories.size()) PhoneModernText.drawCenteredColored(g, "›", x(283), y(105), size(12), 0xFFB5BDC7);
        g.fill(x(10), y(150), x(290), y(151), 0x28FFFFFF);
    }

    private void drawPostCard(GuiGraphics g, PostEntry post, int top, int mouseX, int mouseY) {
        if (top > 540 || top + HOME_POST_HEIGHT < 151) return;
        String avatar = post.authorUuid().equals(PhoneClientData.selfUuid()) ? PhoneBlockGramStore.profilePhotoPath() : PhoneSocialClientData.blockGramPhoto(post.authorUuid());
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, post.authorUuid(), avatar, x(21), y(top + 8), size(29));
        PhoneRoleBadgeRenderer.drawNameAndBadge(g, post.authorName(), PhoneSocialClientData.roleBadge(post.authorUuid()),
                x(58), y(top + 8), size(9), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, "@" + safeHandle(post.authorName()), x(58), y(top + 24), size(6), 0xFF858F9C);
        PhoneModernText.drawColored(g, "•••", x(255), y(top + 12), size(8), 0xFFD5D7DB);

        g.fill(x(18), y(top + HOME_IMAGE_TOP), x(282), y(top + HOME_IMAGE_BOTTOM), 0xFF111216);
        if (!ZPhoneSocialRenderUtil.drawImage(g, minecraft, post.imagePath(), x(18), y(top + HOME_IMAGE_TOP), size(264), size(264))) {
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Fotoğraf", "Photo"), x(150), y(top + 166), size(13), 0xFF74777E);
        }
        drawHeartAnimation(g, post.id(), top);

        PhoneModernText.drawColored(g, post.liked() ? "♥" : "♡", x(22), y(top + 316), size(18), post.liked() ? 0xFFFF304F : 0xFFFFFFFF);
        PhoneModernText.drawColored(g, "◇", x(61), y(top + 316), size(18), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, "↗", x(101), y(top + 317), size(15), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, post.saved() ? "◆" : "◇", x(257), y(top + 317), size(15), 0xFFFFFFFF);

        PhoneModernText.drawColored(g, post.likes() + PhoneSettingsStore.text(" beğeni", " likes"), x(22), y(top + 343), size(7), 0xFFFFFFFF);
        PhoneRoleBadgeRenderer.drawNameAndBadge(g, post.authorName(), PhoneSocialClientData.roleBadge(post.authorUuid()),
                x(22), y(top + 357), size(7), 0xFFFFFFFF);
        String cap = post.caption() == null ? "" : post.caption();
        if (cap.length() > 37) cap = cap.substring(0, 36) + "…";
        PhoneModernText.drawColored(g, cap, x(78), y(top + 357), size(7), 0xFFD8DDE4);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Tüm ", "View all ") + PhoneBlockGramStore.commentCount(post.id()) + PhoneSettingsStore.text(" yorumu gör", " comments"), x(22), y(top + 372), size(6), 0xFF86888E);
        PhoneModernText.drawColored(g, relativeTime(post.createdAt()), x(22), y(top + 385), size(5), 0xFF666970);
        g.fill(x(10), y(top + 397), x(290), y(top + 398), 0x28FFFFFF);
    }

    private void drawExplore(GuiGraphics g) {
        String self = PhoneClientData.selfUuid();
        List<PhoneSocialStateData.FollowEntry> incoming = PhoneSocialClientData.incomingFollowRequests("BLOCKGRAM", self);
        List<PhoneSocialStateData.FollowEntry> outgoing = PhoneSocialClientData.outgoingFollowRequests("BLOCKGRAM", self);

        int incomingColor = exploreSection == ExploreSection.INCOMING ? 0xFF2A2B30 : 0xFF121317;
        int outgoingColor = exploreSection == ExploreSection.OUTGOING ? 0xFF2A2B30 : 0xFF121317;
        drawRoundedRect(g, x(18), y(130), x(140), y(158), size(7), incomingColor);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Gelen ", "Incoming ") + incoming.size(), x(79), y(138), size(7), size(106), 0xFFFFFFFF);
        drawRoundedRect(g, x(146), y(130), x(282), y(158), size(7), outgoingColor);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Gönderilen ", "Sent ") + outgoing.size(), x(214), y(138), size(7), size(120), 0xFFFFFFFF);
        if (exploreSection == ExploreSection.INCOMING) g.fill(x(43), y(156), x(115), y(158), 0xFFFF304F);
        if (exploreSection == ExploreSection.OUTGOING) g.fill(x(176), y(156), x(252), y(158), 0xFFFF304F);

        int top = 170 - scroll;
        g.enableScissor(x(14), y(164), x(286), y(540));
        if (exploreSection == ExploreSection.INCOMING) {
            if (incoming.isEmpty()) {
                PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Gelen takip isteği yok", "No incoming follow requests"), x(150), y(286), size(9), size(238), 0xFF7F8996);
            }
            for (PhoneSocialStateData.FollowEntry request : incoming) {
                if (top + 80 >= 164 && top <= 540) {
                    g.fill(x(18), y(top + 77), x(282), y(top + 78), 0x28FFFFFF);
                    ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, request.fromUuid, PhoneSocialClientData.blockGramPhoto(request.fromUuid), x(27), y(top + 10), size(34));
                    PhoneRoleBadgeRenderer.drawNameAndBadge(g, request.fromName, PhoneSocialClientData.roleBadge(request.fromUuid),
                            x(70), y(top + 10), size(9), 0xFFFFFFFF);
                    PhoneModernText.drawColored(g, PhoneSettingsStore.text("seni takip etmek istiyor", "wants to follow you"), x(70), y(top + 28), size(6), 0xFF8B95A1);
                    drawRoundedRect(g, x(58), y(top + 48), x(116), y(top + 71), size(6), 0xFF2C9B58);
                    PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Kabul et", "Accept"), x(87), y(top + 54), size(6), size(48), 0xFFFFFFFF);
                    drawRoundedRect(g, x(121), y(top + 48), x(204), y(top + 71), size(6), 0xFF168AFF);
                    PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Geri takip et", "Follow back"), x(162), y(top + 54), size(6), size(73), 0xFFFFFFFF);
                    drawRoundedRect(g, x(209), y(top + 48), x(270), y(top + 71), size(6), 0xFF2A2B30);
                    PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Reddet", "Decline"), x(239), y(top + 54), size(6), size(51), 0xFFCBD0D7);
                }
                top += 86;
            }
        } else if (exploreSection == ExploreSection.OUTGOING) {
            if (outgoing.isEmpty()) {
                PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Gönderilen takip isteği yok", "No sent follow requests"), x(150), y(286), size(9), size(238), 0xFF7F8996);
            }
            for (PhoneSocialStateData.FollowEntry request : outgoing) {
                if (top + 66 >= 164 && top <= 540) {
                    g.fill(x(18), y(top + 63), x(282), y(top + 64), 0x28FFFFFF);
                    ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, request.toUuid, PhoneSocialClientData.blockGramPhoto(request.toUuid), x(27), y(top + 12), size(38));
                    PhoneRoleBadgeRenderer.drawNameAndBadge(g, request.toName, PhoneSocialClientData.roleBadge(request.toUuid),
                            x(75), y(top + 12), size(9), 0xFFFFFFFF);
                    PhoneModernText.drawColored(g, PhoneSettingsStore.text("Takip isteği bekliyor", "Follow request pending"), x(75), y(top + 31), size(6), 0xFF8B95A1);
                    drawRoundedRect(g, x(207), y(top + 18), x(271), y(top + 46), size(7), 0xFF2A2B30);
                    PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Bekliyor", "Pending"), x(239), y(top + 26), size(6), size(54), 0xFFFFFFFF);
                    PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("İptal etmek için dokun", "Tap to cancel"), x(150), y(top + 53), size(5), 0xFF697482);
                }
                top += 72;
            }
        } else {
            String query = searchQuery.toString();
            List<PhoneSocialStateData.ProfileEntry> profiles = PhoneSocialClientData.profilesMatching(query);
            int shown = 0;
            for (PhoneSocialStateData.ProfileEntry profile : profiles) {
                if (profile.playerUuid == null || profile.playerUuid.isBlank() || profile.playerUuid.equals(self)) continue;
                if (top + 56 >= 164 && top <= 540) {
                    ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, profile.playerUuid, PhoneSocialClientData.blockGramPhoto(profile.playerUuid), x(22), y(top + 6), size(39));
                    PhoneRoleBadgeRenderer.drawNameAndBadge(g, profile.playerName, PhoneSocialClientData.roleBadge(profile.playerUuid),
                            x(70), y(top + 8), size(9), 0xFFFFFFFF);
                    PhoneModernText.drawColored(g, "@" + safeHandle(profile.playerName), x(70), y(top + 25), size(6), 0xFF7F8996);
                    String state = PhoneSocialClientData.followState("BLOCKGRAM", self, profile.playerUuid);
                    int color = "ACCEPTED".equals(state) ? 0xFF2A2B30 : "PENDING".equals(state) ? 0xFF2A2B30 : 0xFF168AFF;
                    drawRoundedRect(g, x(205), y(top + 12), x(274), y(top + 40), size(7), color);
                    PhoneModernText.drawCenteredColoredFitted(g, "ACCEPTED".equals(state) ? PhoneSettingsStore.text("Takip ediliyor", "Following") : "PENDING".equals(state) ? PhoneSettingsStore.text("Bekliyor", "Pending") : PhoneSettingsStore.text("Takip et", "Follow"), x(239), y(top + 20), size(6), size(59), 0xFFFFFFFF);
                    g.fill(x(22), y(top + 55), x(278), y(top + 56), 0x20FFFFFF);
                }
                top += 58;
                shown++;
            }
            if (shown == 0) PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Oyuncu bulunamadı", "Player not found"), x(150), y(286), size(9), size(238), 0xFF7F8996);
        }
        g.disableScissor();
    }

    private void drawDms(GuiGraphics g, int mouseX, int mouseY) {
        List<PhoneStateData.PlayerEntry> contacts = PhoneClientData.sortedGramContacts();
        int top = 108 - scroll;
        g.enableScissor(x(15), y(100), x(285), y(536));
        if (contacts.isEmpty()) {
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Konuşma yok", "No conversations"), x(150), y(268), size(12), 0xFFFFFFFF);
            PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Sohbet başlatmak için birbirinizi takip edin", "Follow each other to start a chat"), x(150), y(293), size(8), size(244), 0xFF7D8794);
        }
        for (PhoneStateData.PlayerEntry contact : contacts) {
            if (top + 62 >= 100 && top <= 536) {
                if (inside(mouseX, mouseY, 18, top, 282, top + 56)) g.fill(x(18), y(top), x(282), y(top + 56), 0xFF121317);
                g.fill(x(28), y(top + 55), x(282), y(top + 56), 0x24FFFFFF);
                ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, contact.uuid, PhoneSocialClientData.blockGramPhoto(contact.uuid), x(28), y(top + 10), size(36));
                PhoneModernText.drawColoredFitted(g, contact.name, x(75), y(top + 11), size(10), size(158), 0xFFFFFFFF);
                var last = PhoneClientData.lastGramMessage(contact.uuid);
                PhoneModernText.drawColoredFitted(g, PhoneClientData.messagePreview(last), x(75), y(top + 31), size(8), size(158), 0xFF7E8996);
                int unread = PhoneClientData.unreadGramCount(contact.uuid);
                if (unread > 0) {
                    fillCircle(g, x(260), y(top + 28), size(9), 0xFFFF304F);
                    PhoneModernText.drawCenteredColored(g, unread > 9 ? "9+" : Integer.toString(unread), x(260), y(top + 23), size(7), 0xFFFFFFFF);
                }
            }
            top += 64;
        }
        g.disableScissor();
    }

    private void drawAlerts(GuiGraphics g, int mouseX, int mouseY) {
        String self = PhoneClientData.selfUuid();
        List<PhoneSocialStateData.FollowEntry> requests = PhoneSocialClientData.incomingFollowRequests("BLOCKGRAM", self);
        List<PhoneSocialStateData.NotificationEntry> notifications = PhoneSocialClientData.notifications("BLOCKGRAM", self);
        int top = 106 - scroll;
        g.enableScissor(x(15), y(99), x(285), y(536));
        for (PhoneSocialStateData.FollowEntry request : requests) {
            if (top + 82 >= 99 && top <= 536) {
                g.fill(x(18), y(top + 75), x(282), y(top + 76), 0x28FFFFFF);
                ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, request.fromUuid, PhoneSocialClientData.blockGramPhoto(request.fromUuid), x(27), y(top + 11), size(29));
                PhoneModernText.drawColored(g, request.fromName, x(65), y(top + 10), size(9), 0xFFFFFFFF);
                PhoneModernText.drawColored(g, PhoneSettingsStore.text("seni takip etmek istiyor", "wants to follow you"), x(65), y(top + 27), size(7), 0xFF8B95A1);
                drawRoundedRect(g, x(65), y(top + 44), x(122), y(top + 66), size(9), 0xFF2C9B58);
                PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Kabul et", "Accept"), x(93), y(top + 50), size(6), size(47), 0xFFFFFFFF);
                drawRoundedRect(g, x(127), y(top + 44), x(205), y(top + 66), size(6), 0xFF168AFF);
                PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Geri takip et", "Follow back"), x(166), y(top + 50), size(6), size(68), 0xFFFFFFFF);
                drawRoundedRect(g, x(210), y(top + 44), x(268), y(top + 66), size(9), 0xFF343B45);
                PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Reddet", "Decline"), x(239), y(top + 50), size(6), size(48), 0xFFCBD0D7);
            }
            top += 84;
        }
        for (PhoneSocialStateData.NotificationEntry n : notifications) {
            if (top + 58 >= 99 && top <= 536) {
                if (inside(mouseX, mouseY, 18, top, 282, top + 52)) g.fill(x(18), y(top), x(282), y(top + 52), 0xFF121317);
                if (!n.read) g.fill(x(18), y(top + 5), x(20), y(top + 47), 0xFFFF304F);
                g.fill(x(28), y(top + 51), x(282), y(top + 52), 0x20FFFFFF);
                fillCircle(g, x(37), y(top + 26), size(12), n.read ? 0xFF2A2B30 : 0xFFFF304F);
                PhoneModernText.drawCenteredColored(g, alertIcon(n.type), x(37), y(top + 20), size(9), 0xFFFFFFFF);
                PhoneModernText.drawColored(g, n.actorName == null || n.actorName.isBlank() ? "Lumi" : n.actorName, x(57), y(top + 9), size(8), 0xFFFFFFFF);
                ZPhoneSocialRenderUtil.drawWrapped(g, PhoneSettingsStore.localizeNetworkText(n.text), x(57), y(top + 25), size(7), 0xFF8C96A2, 38, 2, size(9));
            }
            top += 59;
        }
        if (requests.isEmpty() && notifications.isEmpty()) {
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Hepsini gördün", "You're all caught up"), x(150), y(270), size(12), 0xFFFFFFFF);
        }
        g.disableScissor();
    }

    private String alertIcon(String type) {
        if (type == null) return "•";
        return switch (type.toUpperCase()) {
            case "LIKE" -> "♥";
            case "COMMENT" -> "◇";
            case "SHARE" -> "↗";
            case "FOLLOW" -> "+";
            default -> "•";
        };
    }

    private void drawProfile(GuiGraphics g, int mouseX, int mouseY) {
        String self = PhoneClientData.selfUuid();
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, self, PhoneBlockGramStore.profilePhotoPath(), x(22), y(91), size(58));
        PhoneModernText.drawColored(g, PhoneClientData.selfName(), x(91), y(94), size(12), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, "@" + safeHandle(PhoneClientData.selfName()), x(91), y(114), size(7), 0xFF858F9C);
        int posts = PhoneBlockGramStore.byAuthor(self).size();
        int followers = PhoneSocialClientData.followerCount("BLOCKGRAM", self);
        int following = PhoneSocialClientData.followingCount("BLOCKGRAM", self);
        drawStat(g, 103, 143, posts, PhoneSettingsStore.text("gönderi", "posts"));
        drawStat(g, 168, 143, followers, PhoneSettingsStore.text("takipçi", "followers"));
        drawStat(g, 239, 143, following, PhoneSettingsStore.text("takip", "following"));
        ZPhoneSocialRenderUtil.drawWrapped(g, PhoneSettingsStore.localizeNetworkText(PhoneBlockGramStore.profileBio()), x(23), y(181), size(7), 0xFFE4E8EE, 48, 3, size(10));
        drawRoundedRect(g, x(21), y(220), x(278), y(250), size(7), 0xFF242529);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Profili düzenle", "Edit profile"), x(149), y(229), size(7), size(238), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Gönderiler", "Posts"), x(82), y(268), size(7), size(96), profileSavedTab ? 0xFF777F8A : 0xFFFFFFFF);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Kaydedilenler", "Saved"), x(218), y(268), size(7), size(96), profileSavedTab ? 0xFFFFFFFF : 0xFF777F8A);
        g.fill(x(16), y(284), x(284), y(285), 0x28FFFFFF);
        g.fill(x(profileSavedTab ? 171 : 32), y(284), x(profileSavedTab ? 267 : 132), y(286), 0xFFFFFFFF);

        List<PostEntry> postList = profileSavedTab ? PhoneBlockGramStore.savedPosts() : PhoneBlockGramStore.byAuthor(self);
        int cell = 88, gap = 2, startX = 16, startY = 296 - scroll;
        g.enableScissor(x(16), y(291), x(284), y(540));
        for (int i = 0; i < postList.size(); i++) {
            int col = i % 3, row = i / 3;
            int left = startX + col * (cell + gap), top = startY + row * (cell + gap);
            g.fill(x(left), y(top), x(left + cell), y(top + cell), 0xFF111216);
            ZPhoneSocialRenderUtil.drawImage(g, minecraft, postList.get(i).imagePath(), x(left), y(top), size(cell), size(cell));
        }
        g.disableScissor();
        if (postList.isEmpty()) PhoneModernText.drawCenteredColoredFitted(g, profileSavedTab ? PhoneSettingsStore.text("Kaydedilmiş gönderi yok", "No saved posts") : PhoneSettingsStore.text("Henüz gönderi yok", "No posts yet"), x(150), y(362), size(9), size(238), 0xFF7D8794);
    }

    private void drawStat(GuiGraphics g, int centerX, int top, int value, String label) {
        PhoneModernText.drawCenteredColored(g, Integer.toString(value), x(centerX), y(top), size(9), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColoredFitted(g, label, x(centerX), y(top + 15), size(6), size(60), 0xFF858F9C);
    }

    private void drawBottomNav(GuiGraphics g) {
        g.fill(x(8), y(541), x(292), y(582), 0xFF050608);
        g.fill(x(10), y(541), x(290), y(542), 0x35FFFFFF);
        drawNavItem(g, Tab.HOME, 36, "⌂");
        drawNavItem(g, Tab.EXPLORE, 93, "⌕");
        drawDmNavItem(g);
        drawNavItem(g, Tab.ALERTS, 207, "♡");
        drawProfileNavItem(g);
    }

    private void drawDmNavItem(GuiGraphics g) {
        boolean active = tab == Tab.DMS;
        int color = active ? 0xFFFFFFFF : 0xFF8E9096;
        PhoneModernText.drawCenteredColored(g, "↗", x(150), y(551), size(15), color);
        int unread = totalDmUnread();
        if (unread > 0) {
            fillCircle(g, x(162), y(549), size(8), 0xFFFF304F);
            PhoneModernText.drawCenteredColored(g, unread > 9 ? "9+" : Integer.toString(unread), x(162), y(544), size(6), 0xFFFFFFFF);
        }
        if (active) fillCircle(g, x(150), y(577), size(2), 0xFFFFFFFF);
    }

    private void drawProfileNavItem(GuiGraphics g) {
        boolean active = tab == Tab.PROFILE;
        if (active) fillCircle(g, x(264), y(559), size(14), 0xFFFFFFFF);
        else fillCircle(g, x(264), y(559), size(13), 0xFF3A3B40);
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, PhoneClientData.selfUuid(), PhoneBlockGramStore.profilePhotoPath(),
                x(active ? 252 : 253), y(active ? 547 : 548), size(active ? 24 : 22));
        if (active) fillCircle(g, x(264), y(577), size(2), 0xFFFFFFFF);
    }

    private int totalDmUnread() {
        int total = 0;
        for (PhoneStateData.PlayerEntry contact : PhoneClientData.sortedGramContacts()) {
            total += PhoneClientData.unreadGramCount(contact.uuid);
            if (total >= 99) return 99;
        }
        return total;
    }

    private void drawNavItem(GuiGraphics g, Tab item, int centerX, String icon) {
        boolean active = tab == item;
        PhoneModernText.drawCenteredColored(g, icon, x(centerX), y(551), size(14), active ? 0xFFFFFFFF : 0xFF8E9096);
        if (active) fillCircle(g, x(centerX), y(577), size(2), 0xFFFFFFFF);
    }

    private void drawStoryViewer(GuiGraphics g) {
        drawOpaqueModalBackdrop(g, x(3), y(40), x(297), y(606), 0xFF07090E);
        drawRoundedRect(g, x(18), y(56), x(282), y(559), size(18), 0xFF0C1016);

        int count = Math.max(1, storySequence.size());
        int gap = 3;
        int totalWidth = 236;
        int segmentWidth = Math.max(8, (totalWidth - gap * (count - 1)) / count);
        for (int i = 0; i < count; i++) {
            int left = 32 + i * (segmentWidth + gap);
            drawRoundedRect(g, x(left), y(64), x(left + segmentWidth), y(67), size(1), 0x553E4652);
            float progress = i < storyIndex ? 1.0F : i == storyIndex ? Math.min(1.0F, (System.currentTimeMillis() - storyStartedAt) / 5000.0F) : 0.0F;
            if (progress > 0.0F) drawRoundedRect(g, x(left), y(64), x(left + Math.max(1, Math.round(segmentWidth * progress))), y(67), size(1), 0xFFFFFFFF);
        }

        String avatar = PhoneSocialClientData.blockGramPhoto(storyViewer.authorUuid());
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, storyViewer.authorUuid(), avatar, x(29), y(75), size(27));
        PhoneModernText.drawColored(g, storyViewer.authorName(), x(65), y(79), size(9), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("sola / sağa dokun", "tap left / right"), x(65), y(96), size(6), 0xFF8A95A2);
        PhoneModernText.drawColored(g, "×", x(254), y(77), size(14), 0xFFC7CDD5);
        drawRoundedRect(g, x(29), y(116), x(271), y(530), size(15), 0xFF161C24);
        ZPhoneSocialRenderUtil.drawImage(g, minecraft, storyViewer.imagePath(), x(29), y(116), size(242), size(414));
        if (PhoneClientData.selfUuid().equals(storyViewer.authorUuid())) {
            List<String> viewers = storyViewer.seenByNames();
            String text;
            if (viewers.isEmpty()) text = PhoneSettingsStore.text("Henüz görüntüleyen yok", "No views yet");
            else {
                int shown = Math.min(3, viewers.size());
                text = PhoneSettingsStore.text("Görenler: ", "Seen by ") + String.join(", ", viewers.subList(0, shown));
                if (viewers.size() > shown) text += " +" + (viewers.size() - shown);
            }
            drawRoundedRect(g, x(38), y(487), x(262), y(521), size(10), 0xB5000000);
            PhoneModernText.drawCenteredColored(g, text, x(150), y(498), size(6), 0xFFFFFFFF);
        }
    }

    private void resolvePendingImageClick() {
        if (pendingImageClickAt <= 0L || pendingImagePath.isBlank()) return;
        if (System.currentTimeMillis() - pendingImageClickAt < 300L) return;
        fullscreenImagePath = pendingImagePath;
        pendingImagePostId = "";
        pendingImagePath = "";
        pendingImageClickAt = 0L;
    }

    private void drawHeartAnimation(GuiGraphics g, String postId, int top) {
        if (!postId.equals(heartPostId)) return;
        long elapsed = System.currentTimeMillis() - heartAnimationStartedAt;
        if (elapsed >= 720L) { heartPostId = ""; return; }
        double p = elapsed / 720.0D;
        int textSize = size((float) (38.0D + Math.sin(Math.PI * p) * 15.0D));
        PhoneModernText.drawCenteredColored(g, "♥", x(150), y(top + 145), textSize, 0xF5FFFFFF);
    }

    private void drawFullscreenImage(GuiGraphics g) {
        drawOpaqueModalBackdrop(g, x(3), y(40), x(297), y(606), 0xFF000000);
        drawRoundedRect(g, x(18), y(72), x(282), y(548), size(18), 0xFF080B10);
        ZPhoneSocialRenderUtil.drawImage(g, minecraft, fullscreenImagePath, x(24), y(100), size(252), size(410));
        PhoneModernText.drawColored(g, "×", x(258), y(62), size(18), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Kapatmak için dokun", "Tap to close"), x(150), y(526), size(7), 0xFFB6C0CC);
    }

    private void openStory(StoryEntry story) {
        if (story == null) return;
        List<StoryEntry> authorStories = PhoneBlockGramStore.storiesByAuthor(story.authorUuid());
        storySequence = authorStories.isEmpty() ? List.of(story) : authorStories;
        storyIndex = Math.max(0, storySequence.indexOf(story));
        storyViewer = storySequence.get(storyIndex);
        storyStartedAt = System.currentTimeMillis();
    }

    private void moveStory(int direction) {
        if (storyViewer == null || storySequence.isEmpty()) return;
        PhoneBlockGramStore.markStoryViewed(storyViewer.id());
        int next = storyIndex + direction;
        if (next < 0) next = 0;
        if (next >= storySequence.size()) {
            closeStoryViewer();
            return;
        }
        storyIndex = next;
        storyViewer = storySequence.get(storyIndex);
        storyStartedAt = System.currentTimeMillis();
    }

    private void advanceStoryIfNeeded() {
        if (storyViewer != null && System.currentTimeMillis() - storyStartedAt >= 5000L) moveStory(1);
    }

    private void closeStoryViewer() {
        if (storyViewer != null) PhoneBlockGramStore.markStoryViewed(storyViewer.id());
        storyViewer = null;
        storySequence = List.of();
        storyIndex = 0;
        storyStartedAt = 0L;
    }

    private void drawEditor(GuiGraphics g, int mouseX, int mouseY) {
        drawOpaqueModalBackdrop(g, x(3), y(40), x(297), y(606), 0xFF050608);
        drawRoundedRect(g, x(18), y(editor == Editor.PROFILE ? 160 : 184), x(282), y(566), size(10), 0xFF111216);
        if (editor == Editor.PROFILE) drawProfileEditor(g);
        else drawPostEditor(g);
    }

    private void drawPostEditor(GuiGraphics g) {
        int top = 199;
        PhoneModernText.drawColoredFitted(g, editor == Editor.STORY ? PhoneSettingsStore.text("Yeni Hikaye", "New Story") : PhoneSettingsStore.text("Yeni Lumi Gönderisi", "New Lumi Post"), x(33), y(top), size(15), size(234), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Galeriden seç", "Choose from gallery"), x(34), y(top + 32), size(8), 0xFF8C97A4);
        drawRoundedRect(g, x(176), y(top + 22), x(269), y(top + 56), size(7), 0xFF242529);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("◉  Kamera", "◉  Camera"), x(222), y(top + 32), size(8), size(88), 0xFFFFFFFF);
        drawPhotoPicker(g, top + 62);
        if (editor == Editor.POST) {
            drawRoundedRect(g, x(31), y(top + 205), x(269), y(top + 251), size(8), 0xFF1A1B1F);
            String shown = draft.isEmpty() ? PhoneSettingsStore.text("Bir açıklama yaz…", "Write a caption…") : draft.toString();
            PhoneModernText.drawColored(g, shown.length() > 44 ? shown.substring(shown.length() - 44) : shown, x(43), y(top + 220), size(8), draft.isEmpty() ? 0xFF74808E : 0xFFFFFFFF);
        }
        int buttonTop = editor == Editor.POST ? top + 270 : top + 218;
        drawRoundedRect(g, x(31), y(buttonTop), x(269), y(buttonTop + 38), size(8), selectedPhoto == null ? 0xFF34353A : 0xFF168AFF);
        PhoneModernText.drawCenteredColoredFitted(g, editor == Editor.STORY ? PhoneSettingsStore.text("Hikayeyi Paylaş", "Share Story") : PhoneSettingsStore.text("Gönderiyi Paylaş", "Share Post"), x(150), y(buttonTop + 12), size(9), size(218), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("İptal", "Cancel"), x(150), y(buttonTop + 54), size(8), 0xFF8E98A5);
    }

    private void drawProfileEditor(GuiGraphics g) {
        int top = 176;
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Profili Düzenle", "Edit Profile"), x(33), y(top), size(15), size(234), 0xFFFFFFFF);
        String photoPath = selectedPhoto == null ? PhoneBlockGramStore.profilePhotoPath() : selectedPhoto.toAbsolutePath().normalize().toString();
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, PhoneClientData.selfUuid(), photoPath, x(33), y(top + 31), size(54));
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Aşağıdan yeni bir fotoğraf seç", "Choose a new photo below"), x(100), y(top + 45), size(8), size(166), 0xFF8C97A4);
        drawPhotoPicker(g, top + 92);
        drawRoundedRect(g, x(31), y(top + 245), x(269), y(top + 303), size(8), 0xFF1A1B1F);
        String shown = bioDraft.isEmpty() ? PhoneSettingsStore.text("Biyografi", "Bio") : bioDraft.toString();
        ZPhoneSocialRenderUtil.drawWrapped(g, shown, x(43), y(top + 258), size(8), bioDraft.isEmpty() ? 0xFF74808E : 0xFFFFFFFF, 43, 2, size(11));
        drawRoundedRect(g, x(31), y(top + 319), x(269), y(top + 356), size(8), 0xFF168AFF);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Kaydet", "Save"), x(150), y(top + 330), size(9), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("İptal", "Cancel"), x(150), y(top + 371), size(8), 0xFF8E98A5);
    }

    private void drawPhotoPicker(GuiGraphics g, int top) {
        List<Path> photos = PhoneGalleryStore.photos(minecraft);
        for (int i = 0; i < Math.min(6, photos.size()); i++) {
            int col = i % 3, row = i / 3;
            int left = 33 + col * 78, py = top + row * 72;
            Path photo = photos.get(i);
            boolean selected = photo.equals(selectedPhoto);
            drawRoundedRect(g, x(left - 2), y(py - 2), x(left + 62), y(py + 62), size(7), selected ? 0xFF168AFF : 0xFF27282D);
            var texture = PhoneGalleryStore.textureFor(minecraft, photo);
            if (texture != null) {
                g.blit(texture.location(), x(left), y(py), size(58), size(58), 0, 0,
                        texture.width(), texture.height(), texture.width(), texture.height());
            }
        }
    }

    private void graphicsLine(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        g.fill(x(left), y(top), x(right), y(bottom), color);
    }

    private String safeHandle(String name) {
        return name == null ? "player" : name.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
    }

    private String relativeTime(long createdAt) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - createdAt) / 1000L);
        if (seconds < 60) return PhoneSettingsStore.text("şimdi", "now");
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + PhoneSettingsStore.text("dk", "m");
        long hours = minutes / 60;
        if (hours < 24) return hours + PhoneSettingsStore.text("sa", "h");
        return (hours / 24) + PhoneSettingsStore.text("g", "d");
    }

    private int maxProfileScroll() {
        String self = PhoneClientData.selfUuid();
        List<PostEntry> list = profileSavedTab ? PhoneBlockGramStore.savedPosts() : PhoneBlockGramStore.byAuthor(self);
        int rows = (list.size() + 2) / 3;
        return Math.max(0, rows * 90 - 244);
    }

    private List<PostEntry> homeFeedPosts() {
        List<PostEntry> posts = PhoneBlockGramStore.feedPosts();
        if (homeFeed == HomeFeed.FOR_YOU) return posts;
        String self = PhoneClientData.selfUuid();
        return posts.stream()
                .filter(post -> "ACCEPTED".equals(PhoneBlockGramStore.followState(self, post.authorUuid())))
                .toList();
    }

    private int maxHomeScroll() { return Math.max(0, homeFeedPosts().size() * HOME_POST_HEIGHT - 379); }
    private int maxExploreScroll() {
        String self = PhoneClientData.selfUuid();
        if (exploreSection == ExploreSection.INCOMING) {
            return Math.max(0, PhoneSocialClientData.incomingFollowRequests("BLOCKGRAM", self).size() * 86 - 370);
        }
        if (exploreSection == ExploreSection.OUTGOING) {
            return Math.max(0, PhoneSocialClientData.outgoingFollowRequests("BLOCKGRAM", self).size() * 72 - 370);
        }
        int profileCount = 0;
        for (PhoneSocialStateData.ProfileEntry profile : PhoneSocialClientData.profilesMatching(searchQuery.toString())) {
            if (profile.playerUuid == null || profile.playerUuid.isBlank() || profile.playerUuid.equals(self)) continue;
            profileCount++;
        }
        return Math.max(0, profileCount * 58 - 370);
    }
    private int maxDmScroll() { return Math.max(0, PhoneClientData.sortedGramContacts().size() * 64 - 426); }
    private int maxAlertScroll() {
        String self = PhoneClientData.selfUuid();
        int h = PhoneSocialClientData.incomingFollowRequests("BLOCKGRAM", self).size() * 84
                + PhoneSocialClientData.notifications("BLOCKGRAM", self).size() * 59;
        return Math.max(0, h - 428);
    }

    private int currentMaxScroll() {
        return switch (tab) {
            case HOME -> maxHomeScroll();
            case EXPLORE -> maxExploreScroll();
            case DMS -> maxDmScroll();
            case ALERTS -> maxAlertScroll();
            case PROFILE -> maxProfileScroll();
        };
    }

    private int scrollViewportTop() {
        return switch (tab) {
            case HOME -> 151;
            case EXPLORE -> 164;
            case DMS -> 100;
            case ALERTS -> 99;
            case PROFILE -> 291;
        };
    }

    private int scrollViewportBottom() {
        return tab == Tab.DMS || tab == Tab.ALERTS ? 536 : 540;
    }

    private void drawScrollBar(GuiGraphics g) {
        if (editor != Editor.NONE || storyViewer != null || !fullscreenImagePath.isBlank()) return;
        int max = currentMaxScroll();
        int top = scrollViewportTop();
        int bottom = scrollViewportBottom();
        if (max <= 0) {
            scrollThumbTop = top;
            scrollThumbBottom = bottom;
            return;
        }
        int viewport = Math.max(1, bottom - top);
        int contentHeight = viewport + max;
        int thumbHeight = Math.max(36, Math.round(viewport * (viewport / (float) contentHeight)));
        int travel = Math.max(1, viewport - thumbHeight);
        scroll = Math.max(0, Math.min(max, scroll));
        int thumbTop = top + Math.round(travel * (scroll / (float) max));
        scrollThumbTop = thumbTop;
        scrollThumbBottom = thumbTop + thumbHeight;
        drawRoundedRect(g, x(282), y(top), x(287), y(bottom), size(2), 0x302C3440);
        drawRoundedRect(g, x(281), y(thumbTop), x(288), y(thumbTop + thumbHeight), size(3), 0xD0A2ABB7);
    }

    private void setScrollFromMouse(double mouseY, boolean keepGrabOffset) {
        int max = currentMaxScroll();
        if (max <= 0) {
            scroll = 0;
            return;
        }
        int top = scrollViewportTop();
        int bottom = scrollViewportBottom();
        int thumbHeight = Math.max(1, scrollThumbBottom - scrollThumbTop);
        int travel = Math.max(1, bottom - top - thumbHeight);
        double designMouseY = (mouseY - phone().y()) / Math.max(0.001F, phone().scale());
        double desiredTop = keepGrabOffset ? designMouseY - scrollBarGrabOffset : designMouseY - thumbHeight / 2.0D;
        double progress = Math.max(0.0D, Math.min(1.0D, (desiredTop - top) / travel));
        scroll = Math.max(0, Math.min(max, (int) Math.round(progress * max)));
    }

    public static boolean finishPendingCameraCapture(Minecraft minecraft) {
        if (minecraft == null || pendingCameraEditor == Editor.NONE) return false;
        Editor target = pendingCameraEditor;
        String savedDraft = pendingCameraDraft;
        pendingCameraEditor = Editor.NONE;
        pendingCameraDraft = "";
        ZPhoneBlockGramScreen screen = new ZPhoneBlockGramScreen();
        screen.openEditor(target);
        if (target == Editor.POST && !savedDraft.isBlank()) screen.draft.append(savedDraft);
        Path latest = PhoneGalleryStore.latestPhoto(minecraft);
        if (latest != null) screen.selectedPhoto = latest;
        PhoneNavigationState.replaceCurrent(minecraft, screen);
        return true;
    }

    public static boolean cancelPendingCameraCapture(Minecraft minecraft) {
        if (minecraft == null || pendingCameraEditor == Editor.NONE) return false;
        Editor target = pendingCameraEditor;
        String savedDraft = pendingCameraDraft;
        pendingCameraEditor = Editor.NONE;
        pendingCameraDraft = "";
        ZPhoneBlockGramScreen screen = new ZPhoneBlockGramScreen();
        screen.openEditor(target);
        if (target == Editor.POST && !savedDraft.isBlank()) screen.draft.append(savedDraft);
        PhoneNavigationState.replaceCurrent(minecraft, screen);
        return true;
    }

    private void openEditor(Editor next) {
        editor = next;
        textFocused = false;
        selectedPhoto = null;
        draft.setLength(0);
        bioDraft.setLength(0);
        if (next == Editor.PROFILE) bioDraft.append(PhoneBlockGramStore.profileBio());
    }

    private void closeEditor() {
        editor = Editor.NONE;
        textFocused = false;
        selectedPhoto = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (!fullscreenImagePath.isBlank()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) fullscreenImagePath = "";
            return true;
        }
        if (storyViewer != null) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            if (inside(mouseX, mouseY, 236, 68, 276, 108)) { closeStoryViewer(); return true; }
            if (mouseX < x(150)) moveStory(-1);
            else moveStory(1);
            return true;
        }
        if (editor != Editor.NONE) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            return handleEditorClick(mouseX, mouseY);
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (handleDynamicIslandClick(mouseX, mouseY)) return true;
        if (currentMaxScroll() > 0 && inside(mouseX, mouseY, 278, scrollViewportTop() - 5, 292, scrollViewportBottom() + 5)) {
            scrollBarDragging = true;
            double designMouseY = (mouseY - phone().y()) / Math.max(0.001F, phone().scale());
            if (designMouseY >= scrollThumbTop && designMouseY <= scrollThumbBottom) {
                scrollBarGrabOffset = designMouseY - scrollThumbTop;
                setScrollFromMouse(mouseY, true);
            } else {
                scrollBarGrabOffset = (scrollThumbBottom - scrollThumbTop) / 2.0D;
                setScrollFromMouse(mouseY, false);
            }
            return true;
        }

        // Bottom nav has priority over every post/image hitbox.
        if (inside(mouseX, mouseY, 15, 538, 65, 582)) return switchTab(Tab.HOME);
        if (inside(mouseX, mouseY, 66, 538, 122, 582)) return switchTab(Tab.EXPLORE);
        if (inside(mouseX, mouseY, 124, 538, 178, 582)) return switchTab(Tab.DMS);
        if (inside(mouseX, mouseY, 180, 538, 235, 582)) return switchTab(Tab.ALERTS);
        if (inside(mouseX, mouseY, 236, 538, 285, 582)) return switchTab(Tab.PROFILE);

        if (inside(mouseX, mouseY, 245, 48, 288, 87)) { switchTab(Tab.ALERTS); return true; }
        if (inside(mouseX, mouseY, 12, 48, 56, 87)) { openEditor(Editor.POST); return true; }
        if (tab == Tab.HOME) {
            if (inside(mouseX, mouseY, 57, 48, 150, 87)) return switchHomeFeed(HomeFeed.FOR_YOU);
            if (inside(mouseX, mouseY, 150, 48, 244, 87)) return switchHomeFeed(HomeFeed.FOLLOWING);
            if (inside(mouseX, mouseY, 51, 119, 68, 143)) {
                openEditor(Editor.STORY);
                return true;
            }
            if (inside(mouseX, mouseY, 20, 91, 66, 148)) {
                List<StoryEntry> own = PhoneBlockGramStore.storiesByAuthor(PhoneClientData.selfUuid());
                if (own.isEmpty()) openEditor(Editor.STORY);
                else openStory(own.get(own.size() - 1));
                return true;
            }
            List<StoryEntry> stories = PhoneBlockGramStore.stories().stream()
                    .filter(story -> !story.authorUuid().equals(PhoneClientData.selfUuid())).toList();
            int visible = 0;
            for (int i = storyOffset; i < stories.size() && visible < 4; i++) {
                StoryEntry story = stories.get(i);
                int center = 93 + visible * 51;
                if (inside(mouseX, mouseY, center - 22, 91, center + 22, 148)) {
                    openStory(story);
                    return true;
                }
                visible++;
            }
            int top = HOME_LIST_TOP - scroll;
            for (PostEntry post : homeFeedPosts()) {
                if (inside(mouseX, mouseY, 18, top + 4, 184, top + 40)) {
                    if (!post.authorUuid().equals(PhoneClientData.selfUuid())) minecraft.setScreen(new ZPhoneLumiProfileScreen(post.authorUuid()));
                    return true;
                }
                if (inside(mouseX, mouseY, 18, top + HOME_ACTION_TOP, 55, top + HOME_ACTION_BOTTOM)) {
                    PhoneBlockGramStore.toggleLike(post.id(), PhoneClientData.selfUuid(), PhoneClientData.selfName());
                    return true;
                }
                if (inside(mouseX, mouseY, 18, top + HOME_IMAGE_TOP, 282, top + HOME_IMAGE_BOTTOM)) {
                    long now = System.currentTimeMillis();
                    boolean secondTap = post.id().equals(pendingImagePostId) && now - pendingImageClickAt <= 360L;
                    if (secondTap) {
                        if (!post.liked()) PhoneBlockGramStore.toggleLike(post.id(), PhoneClientData.selfUuid(), PhoneClientData.selfName());
                        heartPostId = post.id();
                        heartAnimationStartedAt = now;
                        pendingImagePostId = ""; pendingImagePath = ""; pendingImageClickAt = 0L;
                    } else if (post.imagePath() != null && !post.imagePath().isBlank()) {
                        pendingImagePostId = post.id();
                        pendingImagePath = post.imagePath();
                        pendingImageClickAt = now;
                    }
                    return true;
                }
                if (inside(mouseX, mouseY, 55, top + HOME_ACTION_TOP, 94, top + HOME_ACTION_BOTTOM)) {
                    minecraft.setScreen(new ZPhoneBlockGramPostScreen(post.id()));
                    return true;
                }
                if (inside(mouseX, mouseY, 94, top + HOME_ACTION_TOP, 137, top + HOME_ACTION_BOTTOM)) {
                    minecraft.setScreen(new ZPhoneBlockGramPostScreen(post.id(), true));
                    return true;
                }
                if (inside(mouseX, mouseY, 244, top + HOME_ACTION_TOP, 282, top + HOME_ACTION_BOTTOM)) {
                    PhoneBlockGramStore.toggleSaved(post.id());
                    return true;
                }
                top += HOME_POST_HEIGHT;
            }
        } else if (tab == Tab.EXPLORE) {
            if (inside(mouseX, mouseY, 20, 94, 280, 125)) {
                exploreSection = ExploreSection.PEOPLE;
                searchFocused = true;
                scroll = 0;
                return true;
            }
            if (inside(mouseX, mouseY, 18, 128, 140, 158)) {
                exploreSection = ExploreSection.INCOMING;
                searchFocused = false;
                scroll = 0;
                return true;
            }
            if (inside(mouseX, mouseY, 146, 128, 282, 158)) {
                exploreSection = ExploreSection.OUTGOING;
                searchFocused = false;
                scroll = 0;
                return true;
            }

            String self = PhoneClientData.selfUuid();
            int top = 170 - scroll;
            if (exploreSection == ExploreSection.INCOMING) {
                for (PhoneSocialStateData.FollowEntry request : PhoneSocialClientData.incomingFollowRequests("BLOCKGRAM", self)) {
                    if (inside(mouseX, mouseY, 18, top, 176, top + 45)) { minecraft.setScreen(new ZPhoneLumiProfileScreen(request.fromUuid)); return true; }
                    if (inside(mouseX, mouseY, 58, top + 48, 116, top + 71)) { PhoneSocialClientData.acceptFollowRequest("BLOCKGRAM", request.id); return true; }
                    if (inside(mouseX, mouseY, 121, top + 48, 204, top + 71)) { PhoneSocialClientData.acceptAndFollowBack("BLOCKGRAM", request.id); return true; }
                    if (inside(mouseX, mouseY, 209, top + 48, 270, top + 71)) { PhoneSocialClientData.declineFollowRequest("BLOCKGRAM", request.id); return true; }
                    top += 86;
                }
            } else if (exploreSection == ExploreSection.OUTGOING) {
                for (PhoneSocialStateData.FollowEntry request : PhoneSocialClientData.outgoingFollowRequests("BLOCKGRAM", self)) {
                    if (inside(mouseX, mouseY, 18, top, 198, top + 64)) { minecraft.setScreen(new ZPhoneLumiProfileScreen(request.toUuid)); return true; }
                    if (inside(mouseX, mouseY, 202, top + 10, 278, top + 54)) { PhoneSocialClientData.removeFollow("BLOCKGRAM", request.toUuid); return true; }
                    top += 72;
                }
            } else {
                for (PhoneSocialStateData.ProfileEntry profile : PhoneSocialClientData.profilesMatching(searchQuery.toString())) {
                    if (profile.playerUuid == null || profile.playerUuid.isBlank() || profile.playerUuid.equals(self)) continue;
                    if (inside(mouseX, mouseY, 18, top, 195, top + 54)) { minecraft.setScreen(new ZPhoneLumiProfileScreen(profile.playerUuid)); return true; }
                    if (inside(mouseX, mouseY, 198, top + 5, 278, top + 48)) {
                        String state = PhoneSocialClientData.followState("BLOCKGRAM", self, profile.playerUuid);
                        if ("NONE".equals(state) || "DECLINED".equals(state) || "REMOVED".equals(state)) PhoneSocialClientData.sendFollowRequest("BLOCKGRAM", profile.playerUuid);
                        else if ("ACCEPTED".equals(state) || "PENDING".equals(state)) PhoneSocialClientData.removeFollow("BLOCKGRAM", profile.playerUuid);
                        return true;
                    }
                    top += 58;
                }
            }
        } else if (tab == Tab.DMS) {

            int top = 108 - scroll;
            for (PhoneStateData.PlayerEntry contact : PhoneClientData.sortedGramContacts()) {
                if (inside(mouseX, mouseY, 18, top, 282, top + 56)) {
                    minecraft.setScreen(new ZPhoneBlockGramThreadScreen(contact.uuid, contact.name));
                    return true;
                }
                top += 64;
            }
        } else if (tab == Tab.ALERTS) {
            String self = PhoneClientData.selfUuid();
            int top = 106 - scroll;
            for (PhoneSocialStateData.FollowEntry request : PhoneSocialClientData.incomingFollowRequests("BLOCKGRAM", self)) {
                if (inside(mouseX, mouseY, 65, top + 44, 122, top + 66)) {
                    PhoneSocialClientData.acceptFollowRequest("BLOCKGRAM", request.id);
                    return true;
                }
                if (inside(mouseX, mouseY, 127, top + 44, 205, top + 66)) {
                    PhoneSocialClientData.acceptAndFollowBack("BLOCKGRAM", request.id);
                    return true;
                }
                if (inside(mouseX, mouseY, 210, top + 44, 268, top + 66)) {
                    PhoneSocialClientData.declineFollowRequest("BLOCKGRAM", request.id);
                    return true;
                }
                top += 84;
            }
            for (PhoneSocialStateData.NotificationEntry notification : PhoneSocialClientData.notifications("BLOCKGRAM", self)) {
                if (inside(mouseX, mouseY, 18, top, 282, top + 52)) {
                    PhoneSocialClientData.markNotificationsRead("BLOCKGRAM");
                    if (notification.postId != null && !notification.postId.isBlank() && PhoneBlockGramStore.findPost(notification.postId) != null) {
                        minecraft.setScreen(new ZPhoneBlockGramPostScreen(notification.postId));
                    } else if (notification.actorUuid != null && !notification.actorUuid.isBlank() && !notification.actorUuid.equals(self)) {
                        minecraft.setScreen(new ZPhoneLumiProfileScreen(notification.actorUuid));
                    }
                    return true;
                }
                top += 59;
            }
            PhoneSocialClientData.markNotificationsRead("BLOCKGRAM");
        } else if (tab == Tab.PROFILE) {
            String self = PhoneClientData.selfUuid();
            if (inside(mouseX, mouseY, 21, 216, 278, 254)) { openEditor(Editor.PROFILE); return true; }
            if (inside(mouseX, mouseY, 136, 135, 199, 174)) { minecraft.setScreen(new ZPhoneLumiPeopleScreen(self, true)); return true; }
            if (inside(mouseX, mouseY, 204, 135, 278, 174)) { minecraft.setScreen(new ZPhoneLumiPeopleScreen(self, false)); return true; }
            if (inside(mouseX, mouseY, 25, 255, 139, 289)) { profileSavedTab = false; return true; }
            if (inside(mouseX, mouseY, 160, 255, 278, 289)) { profileSavedTab = true; return true; }
            List<PostEntry> list = profileSavedTab ? PhoneBlockGramStore.savedPosts() : PhoneBlockGramStore.byAuthor(self);
            int cell = 88, gap = 2, startX = 16, startY = 296 - scroll;
            for (int i = 0; i < list.size(); i++) {
                int col = i % 3, row = i / 3;
                int left = startX + col * (cell + gap), top = startY + row * (cell + gap);
                if (inside(mouseX, mouseY, left, top, left + cell, top + cell)) { minecraft.setScreen(new ZPhoneBlockGramPostScreen(list.get(i).id())); return true; }
            }
        }


        if (isHomeIndicatorTap(mouseX, mouseY)) { ZPhoneClient.openHomeScreen();
            return true;
        }
        return true;
    }

    private boolean switchTab(Tab next) {
        tab = next;
        scroll = 0;
        searchFocused = next == Tab.EXPLORE && searchFocused;
        if (next == Tab.ALERTS) PhoneSocialClientData.markNotificationsRead("BLOCKGRAM");
        return true;
    }

    private boolean switchHomeFeed(HomeFeed next) {
        if (next != null && homeFeed != next) {
            homeFeed = next;
            scroll = 0;
            pendingImagePostId = "";
            pendingImagePath = "";
            pendingImageClickAt = 0L;
        }
        return true;
    }

    private boolean handleEditorClick(double mouseX, double mouseY) {
        int top = editor == Editor.PROFILE ? 176 : 199;
        int photoTop = editor == Editor.PROFILE ? top + 92 : top + 62;
        if ((editor == Editor.POST || editor == Editor.STORY) && inside(mouseX, mouseY, 176, top + 22, 269, top + 56)) {
            pendingCameraEditor = editor;
            pendingCameraDraft = draft.toString();
            textFocused = false;
            this.minecraft.setScreen(new ZPhoneCameraScreen());
            return true;
        }
        List<Path> photos = PhoneGalleryStore.photos(minecraft);
        for (int i = 0; i < Math.min(6, photos.size()); i++) {
            int col = i % 3, row = i / 3;
            int left = 33 + col * 78, py = photoTop + row * 72;
            if (inside(mouseX, mouseY, left - 2, py - 2, left + 62, py + 62)) {
                selectedPhoto = photos.get(i);
                return true;
            }
        }
        if (editor == Editor.POST && inside(mouseX, mouseY, 31, top + 205, 269, top + 251)) {
            textFocused = true;
            return true;
        }
        if (editor == Editor.PROFILE && inside(mouseX, mouseY, 31, top + 245, 269, top + 303)) {
            textFocused = true;
            return true;
        }
        int buttonTop = editor == Editor.POST ? top + 270 : (editor == Editor.STORY ? top + 218 : top + 319);
        if (inside(mouseX, mouseY, 31, buttonTop, 269, buttonTop + 38)) {
            if (editor == Editor.POST && selectedPhoto != null) { if (!PhoneSocialClientData.publishBlockGram(draft.toString().trim(), selectedPhoto)) return true; }
            else if (editor == Editor.STORY && selectedPhoto != null) PhoneSocialClientData.publishBlockGramStory(selectedPhoto);
            else if (editor == Editor.PROFILE) {
                String path = selectedPhoto == null ? PhoneBlockGramStore.profilePhotoPath() : selectedPhoto.toAbsolutePath().normalize().toString();
                PhoneSocialClientData.updateBlockGramProfile(bioDraft.toString().trim(), path);
            }
            closeEditor();
            return true;
        }
        if (inside(mouseX, mouseY, 80, buttonTop + 43, 220, buttonTop + 78)) {
            closeEditor();
            return true;
        }
        textFocused = false;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (editor != Editor.NONE || storyViewer != null || !fullscreenImagePath.isBlank()) {
            scrollBarDragging = false;
            return true;
        }
        if (scrollBarDragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            setScrollFromMouse(mouseY, true);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (editor != Editor.NONE || storyViewer != null || !fullscreenImagePath.isBlank()) {
            scrollBarDragging = false;
            return true;
        }
        if (scrollBarDragging) {
            scrollBarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (editor != Editor.NONE || storyViewer != null || !fullscreenImagePath.isBlank() || delta == 0.0D) return true;
        if (tab == Tab.HOME && inside(mouseX, mouseY, 18, 90, 286, 151)) {
            int storyCount = (int) PhoneBlockGramStore.stories().stream().filter(story -> !story.authorUuid().equals(PhoneClientData.selfUuid())).count();
            int maxStoryOffset = Math.max(0, storyCount - 4);
            if (delta < 0) storyOffset = Math.min(maxStoryOffset, storyOffset + 1); else storyOffset = Math.max(0, storyOffset - 1);
            return true;
        }
        int max = currentMaxScroll();
        if (delta < 0) scroll = Math.min(max, scroll + 64);
        else scroll = Math.max(0, scroll - 64);
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!fullscreenImagePath.isBlank() || storyViewer != null) return true;
        if (editor != Editor.NONE) {
            if (textFocused && SharedConstants.isAllowedChatCharacter(codePoint)) {
                String typed = Character.toString(codePoint);
                StringBuilder target = editor == Editor.PROFILE ? bioDraft : draft;
                int limit = editor == Editor.PROFILE ? PhoneBlockGramStore.MAX_BIO_LENGTH : PhoneBlockGramStore.MAX_CAPTION_LENGTH;
                if (target.length() < limit) target.append(typed, 0, Math.min(typed.length(), limit - target.length()));
            }
            return true;
        }
        if (searchFocused && tab == Tab.EXPLORE && SharedConstants.isAllowedChatCharacter(codePoint) && searchQuery.length() < 40) {
            String typed = Character.toString(codePoint);
            searchQuery.append(typed, 0, Math.min(typed.length(), 40 - searchQuery.length()));
            scroll = 0;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!fullscreenImagePath.isBlank()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) fullscreenImagePath = "";
            return true;
        }
        if (storyViewer != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) closeStoryViewer();
            return true;
        }
        if (editor != Editor.NONE) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeEditor(); return true; }
            if (textFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                StringBuilder target = editor == Editor.PROFILE ? bioDraft : draft;
                if (!target.isEmpty()) target.deleteCharAt(target.length() - 1);
            }
            return true;
        }
        if (searchFocused && tab == Tab.EXPLORE) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) searchQuery.deleteCharAt(searchQuery.length() - 1);
                scroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

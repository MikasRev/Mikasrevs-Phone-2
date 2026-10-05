package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore;
import com.mikasrevs.newphone.client.blockpost.PhoneBlockPostStore.PostEntry;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneLegacyAppScreen;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneCameraScreen;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneXPeopleScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class PhoneBlockPostScreen extends AbstractZPhoneLegacyAppScreen {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(
            NewPhone.MOD_ID,
            "textures/gui/phone/blockpost_logo.png"
    );
    private static DateTimeFormatter joinedFormat() { return DateTimeFormatter.ofPattern("MMMM yyyy", PhoneSettingsStore.locale()); }
    private static final int MIN_TREND_AUTHORS = 1;
    private static final int MAX_TRENDS = 4;
    private static final Pattern HASHTAG_PATTERN = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{N}_]{1,32})"
    );

    private final StringBuilder draft = new StringBuilder();
    private final StringBuilder exploreSearch = new StringBuilder();
    private boolean exploreSearchFocused;
    private final List<PostHitBox> postHitBoxes = new ArrayList<>();
    private boolean composerFocused;
    private String attachedPhoto = "";
    private boolean photoPickerOpen;
    private Path selectedComposerPhoto;
    private static boolean pendingComposerCameraCapture;
    private static String pendingComposerDraft = "";
    private int scrollOffset;
    private Tab activeTab = Tab.HOME;
    private HomeFeed homeFeed = HomeFeed.FOR_YOU;
    private ProfileTab profileTab = ProfileTab.POSTS;
    private String activeTag = "";
    private String statusText = "";
    private long statusUntil;
    private String pendingDeletePostId = "";
    private int feedTop;
    private int feedBottom;
    private int feedMaxOffset;
    private int feedThumbTop;
    private int feedThumbBottom;
    private boolean draggingFeedScrollbar;
    private int feedScrollbarGrabOffset;

    /** Clears camera/composer state that must not survive disconnects or phone removal. */
    public static void resetTransientState() {
        pendingComposerCameraCapture = false;
        pendingComposerDraft = "";
    }

    public PhoneBlockPostScreen() {
        this(false);
    }

    public PhoneBlockPostScreen(boolean openProfile) {
        super(Component.translatable("screen.newphone.app.blockpost"));
        this.activeTab = openProfile ? Tab.PROFILE : Tab.HOME;
        PhoneClientData.requestSync();
        PhoneSocialClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawLegacyAppShell(graphics);
        drawBlockPost(graphics, mouseX, mouseY);
    }

    private void drawBlockPost(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(screenX(104), screenY(136), screenX(796), screenY(1490), 0xFF000000);
        if (!pendingDeletePostId.isBlank()) {
            drawDeleteConfirmation(graphics);
            finishLegacyAppShell(graphics);
            return;
        }
        if (photoPickerOpen) {
            drawComposerPhotoPicker(graphics);
            finishLegacyAppShell(graphics);
            return;
        }
        drawHeader(graphics);

        if (activeTab == Tab.HOME) {
            drawHomeTabs(graphics);
            drawComposer(graphics, mouseX, mouseY);
            drawFeed(graphics, 472, 1382, false);
        } else if (activeTab == Tab.EXPLORE) {
            drawExplore(graphics, mouseX, mouseY);
            drawFeed(graphics, 806, 1382, false);
        } else if (activeTab == Tab.NOTIFICATIONS) {
            drawNotifications(graphics);
        } else {
            drawProfile(graphics, mouseX, mouseY);
            drawFeed(graphics, 842, 1382, true);
        }

        drawBottomNavigation(graphics, mouseX, mouseY);
        drawStatus(graphics);
        finishLegacyAppShell(graphics);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.fill(screenX(104), screenY(136), screenX(796), screenY(232), 0xFF000000);
        if (activeTab == Tab.HOME) {
            drawAuthorAvatar(graphics, selfUuid(), selfName(), 128, 158, 52);
            int logoSize = screenSize(52);
            graphics.blit(LOGO, screenX(424), screenY(154), logoSize, logoSize,
                    0, 0, 256, 256, 256, 256);
            drawRoundedRect(graphics, screenX(716), screenY(158), screenX(772), screenY(214),
                    screenSize(20), 0xFF1D9BF0);
            PhoneModernText.drawCenteredColored(graphics, "+", screenX(744), screenY(165),
                    screenSize(32), 0xFFFFFFFF);
        } else {
            String title = switch (activeTab) {
                case EXPLORE -> PhoneSettingsStore.text("Keşfet", "Explore");
                case NOTIFICATIONS -> PhoneSettingsStore.text("Bildirimler", "Notifications");
                case PROFILE -> PhoneSettingsStore.text("Profil", "Profile");
                default -> "X";
            };
            PhoneModernText.draw(graphics, title, screenX(132), screenY(168), screenSize(34));
            int logoSize = screenSize(42);
            graphics.blit(LOGO, screenX(726), screenY(160), logoSize, logoSize,
                    0, 0, 256, 256, 256, 256);
        }
        graphics.fill(screenX(104), screenY(230), screenX(796), screenY(232), 0xFF2F3336);
    }

    private void drawHomeTabs(GuiGraphics graphics) {
        graphics.fill(screenX(104), screenY(232), screenX(796), screenY(298), 0xFF000000);
        drawHomeFeedTab(graphics, HomeFeed.FOR_YOU, 277,
                PhoneSettingsStore.text("Sana özel", "For you"));
        drawHomeFeedTab(graphics, HomeFeed.FOLLOWING, 623,
                PhoneSettingsStore.text("Takip", "Following"));
        graphics.fill(screenX(104), screenY(296), screenX(796), screenY(298), 0xFF2F3336);
    }

    private void drawHomeFeedTab(GuiGraphics graphics, HomeFeed feed, int centerX, String label) {
        boolean active = homeFeed == feed;
        PhoneModernText.drawCenteredColoredFitted(graphics, label, screenX(centerX), screenY(250),
                screenSize(19), screenSize(270), active ? 0xFFF2F2F2 : 0xFF71767B);
        if (active) {
            drawRoundedRect(graphics, screenX(centerX - 44), screenY(290), screenX(centerX + 44),
                    screenY(296), screenSize(3), 0xFF1D9BF0);
        }
    }

    private void drawExplore(GuiGraphics graphics, int mouseX, int mouseY) {
        drawRoundedRect(graphics, screenX(126), screenY(252), screenX(774), screenY(314),
                screenSize(22), exploreSearchFocused ? 0xFF202327 : 0xFF16181C);
        PhoneModernText.drawColored(graphics, "⌕", screenX(150), screenY(267), screenSize(26), 0xFF71767B);
        String query = exploreSearch.toString();
        PhoneModernText.drawColoredFitted(graphics,
                query.isBlank() ? PhoneSettingsStore.text("X'te ara", "Search X") : query,
                screenX(198), screenY(271), screenSize(18), screenSize(500),
                query.isBlank() ? 0xFF71767B : 0xFFF2F2F2);
        if (!query.isBlank()) {
            PhoneModernText.drawCenteredColored(graphics, "×", screenX(744), screenY(266),
                    screenSize(24), 0xFFB7BABD);
        }

        PhoneModernText.draw(graphics, PhoneSettingsStore.text("Gündemdekiler", "Trends for you"),
                screenX(132), screenY(338), screenSize(25));
        int top = 378;
        int left = 126;
        List<TrendEntry> trends = currentTrends();
        if (trends.isEmpty()) {
            graphics.fill(screenX(126), screenY(top), screenX(774), screenY(top + 94), 0xFF080808);
            PhoneModernText.drawColored(graphics,
                    PhoneSettingsStore.text("Gündem, paylaşılan etiketlerle oluşur.", "Trends appear as people post hashtags."),
                    screenX(148), screenY(top + 31), screenSize(17), 0xFF71767B);
        } else {
            int index = 0;
            for (TrendEntry trend : trends) {
                int column = index % 2;
                int row = index / 2;
                int cardLeft = 126 + column * 330;
                int cardTop = top + row * 78;
                boolean selected = trend.tag().equalsIgnoreCase(activeTag);
                boolean hover = inside(mouseX, mouseY, cardLeft, cardTop, cardLeft + 318, cardTop + 68);
                int color = selected ? 0xFF10293D : hover ? 0xFF111315 : 0xFF080808;
                drawRoundedRect(graphics, screenX(cardLeft), screenY(cardTop), screenX(cardLeft + 318),
                        screenY(cardTop + 68), screenSize(8), color);
                PhoneModernText.drawColoredFitted(graphics, trimTrendLabel(trend.tag()),
                        screenX(cardLeft + 18), screenY(cardTop + 11), screenSize(18), screenSize(278), 0xFFF2F2F2);
                PhoneModernText.drawColored(graphics,
                        trend.authorCount() + PhoneSettingsStore.text(" kişi konuşuyor", " people talking"),
                        screenX(cardLeft + 18), screenY(cardTop + 40), screenSize(13), 0xFF71767B);
                index++;
            }
        }

        PhoneModernText.draw(graphics, query.isBlank()
                        ? PhoneSettingsStore.text("Kimi takip etmeli", "Who to follow")
                        : PhoneSettingsStore.text("Arama sonuçları", "Search results"),
                screenX(132), screenY(552), screenSize(25));
        List<com.mikasrevs.newphone.network.PhoneSocialStateData.ProfileEntry> profiles = exploreProfiles();
        int profileTop = 590;
        if (profiles.isEmpty()) {
            PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Oyuncu bulunamadı", "No players found"),
                    screenX(148), screenY(profileTop + 24), screenSize(17), 0xFF71767B);
        }
        for (int index = 0; index < profiles.size(); index++) {
            var profile = profiles.get(index);
            int rowTop = profileTop + index * 76;
            drawAuthorAvatar(graphics, profile.playerUuid, profile.playerName, 140, rowTop + 8, 52);
            PhoneRoleBadgeRenderer.drawNameAndBadge(graphics, profile.playerName,
                    PhoneSocialClientData.roleBadge(profile.playerUuid), screenX(208), screenY(rowTop + 6),
                    screenSize(19), 0xFFF2F2F2);
            PhoneModernText.drawColoredFitted(graphics, handleFor(profile.playerName), screenX(208),
                    screenY(rowTop + 34), screenSize(14), screenSize(270), 0xFF71767B);
            String state = PhoneSocialClientData.followState("BLOCKPOST", selfUuid(), profile.playerUuid);
            boolean canFollow = !"ACCEPTED".equals(state) && !"PENDING".equals(state);
            drawRoundedRect(graphics, screenX(606), screenY(rowTop + 10), screenX(756), screenY(rowTop + 54),
                    screenSize(18), canFollow ? 0xFFF2F2F2 : 0xFF000000);
            if (!canFollow) {
                drawRoundedRect(graphics, screenX(606), screenY(rowTop + 10), screenX(756), screenY(rowTop + 54),
                        screenSize(18), 0xFF2F3336);
            }
            String label = "ACCEPTED".equals(state) ? PhoneSettingsStore.text("Takiptesin", "Following")
                    : "PENDING".equals(state) ? PhoneSettingsStore.text("İstek gitti", "Requested")
                    : PhoneSettingsStore.text("Takip et", "Follow");
            PhoneModernText.drawCenteredColoredFitted(graphics, label, screenX(681), screenY(rowTop + 22),
                    screenSize(15), screenSize(126), canFollow ? 0xFF0F1419 : 0xFFF2F2F2);
            graphics.fill(screenX(126), screenY(rowTop + 72), screenX(774), screenY(rowTop + 74), 0xFF2F3336);
        }
        PhoneModernText.draw(graphics, activeTag.isBlank()
                        ? PhoneSettingsStore.text("Popüler gönderiler", "Popular posts") : activeTag,
                screenX(132), screenY(770), screenSize(22));
    }

    private List<TrendEntry> currentTrends() {
        Map<String, TrendCounter> counters = new LinkedHashMap<>();
        for (PostEntry post : PhoneBlockPostStore.feedPosts()) {
            String body = post.body() == null ? "" : post.body();
            Matcher matcher = HASHTAG_PATTERN.matcher(body);
            Set<String> seenInPost = new HashSet<>();
            while (matcher.find()) {
                String tag = "#" + matcher.group(1);
                String key = tag.toLowerCase(Locale.ROOT);
                // Repeating the same hashtag in one post never increases its score.
                if (!seenInPost.add(key)) continue;
                TrendCounter counter = counters.computeIfAbsent(key, ignored -> new TrendCounter(tag));
                counter.authors.add(trendAuthorKey(post));
                counter.lastUsedAt = Math.max(counter.lastUsedAt, post.createdAt());
            }
        }

        return counters.values().stream()
                .filter(counter -> counter.authors.size() >= MIN_TREND_AUTHORS)
                .sorted(Comparator
                        .comparingInt((TrendCounter counter) -> counter.authors.size()).reversed()
                        .thenComparing(Comparator.comparingLong((TrendCounter counter) -> counter.lastUsedAt).reversed()))
                .limit(MAX_TRENDS)
                .map(counter -> new TrendEntry(counter.tag, counter.authors.size(), counter.lastUsedAt))
                .toList();
    }

    private String trendAuthorKey(PostEntry post) {
        if (post.authorUuid() != null && !post.authorUuid().isBlank()) return post.authorUuid();
        if (post.handle() != null && !post.handle().isBlank()) return post.handle().toLowerCase(Locale.ROOT);
        return post.authorName() == null ? "" : post.authorName().toLowerCase(Locale.ROOT);
    }

    private boolean containsHashtag(String body, String wantedTag) {
        if (wantedTag == null || wantedTag.isBlank()) return true;
        String wanted = wantedTag.toLowerCase(Locale.ROOT);
        Matcher matcher = HASHTAG_PATTERN.matcher(body == null ? "" : body);
        while (matcher.find()) {
            if (("#" + matcher.group(1)).toLowerCase(Locale.ROOT).equals(wanted)) return true;
        }
        return false;
    }

    private String trimTrendLabel(String tag) {
        if (tag == null) return "#";
        return tag.length() <= 17 ? tag : tag.substring(0, 16) + "…";
    }

    private List<com.mikasrevs.newphone.network.PhoneSocialStateData.ProfileEntry> exploreProfiles() {
        String self = selfUuid();
        return PhoneSocialClientData.profilesMatching(exploreSearch.toString()).stream()
                .filter(profile -> profile.playerUuid != null && !profile.playerUuid.isBlank())
                .filter(profile -> !profile.playerUuid.equals(self))
                .filter(profile -> !PhoneClientData.isBlocked(profile.playerUuid))
                .limit(2)
                .toList();
    }

    private void drawComposer(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = 122;
        int top = 308;
        int right = 778;
        int bottom = 462;
        graphics.fill(screenX(left), screenY(top), screenX(right), screenY(bottom), 0xFF000000);
        graphics.fill(screenX(left), screenY(bottom - 2), screenX(right), screenY(bottom), 0xFF2F3336);

        drawAuthorAvatar(graphics, selfUuid(), selfName(), 140, 326, 54);
        drawRoundedRect(graphics, screenX(210), screenY(320), screenX(756), screenY(388),
                screenSize(10), composerFocused ? 0xFF0D1115 : 0xFF07090B);
        drawDraft(graphics);

        int remaining = PhoneBlockPostStore.MAX_POST_LENGTH - draft.length();
        int counterColor = remaining <= 32 ? 0xFFFF6B6B : 0xFF7F93A9;
        PhoneModernText.drawColored(graphics, remaining + "/" + PhoneBlockPostStore.MAX_POST_LENGTH,
                screenX(486), screenY(410), screenSize(13), counterColor);

        boolean imageHover = inside(mouseX, mouseY, 210, 398, 402, 448);
        drawRoundedRect(graphics, screenX(210), screenY(398), screenX(402), screenY(448),
                screenSize(18), imageHover ? 0xFF102A3D : 0xFF07151F);
        PhoneModernText.drawCenteredColoredFitted(graphics,
                attachedPhoto.isBlank() ? PhoneSettingsStore.text("▣  Fotoğraf", "▣  Photo")
                        : PhoneSettingsStore.text("▣  Fotoğraf eklendi", "▣  Photo added"),
                screenX(306), screenY(413), screenSize(16), screenSize(168),
                attachedPhoto.isBlank() ? 0xFF1D9BF0 : 0xFF00BA7C);

        boolean sendEnabled = !draft.toString().isBlank() || !attachedPhoto.isBlank();
        boolean sendHover = inside(mouseX, mouseY, 608, 398, 756, 448);
        int sendColor = sendEnabled ? (sendHover ? 0xFF35A7F5 : 0xFF1D9BF0) : 0xFF202327;
        drawRoundedRect(graphics, screenX(608), screenY(398), screenX(756), screenY(448),
                screenSize(20), sendColor);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Gönder", "Post"),
                screenX(682), screenY(412), screenSize(18), sendEnabled ? 0xFFFFFFFF : 0xFF71767B);
    }

    private void drawDraft(GuiGraphics graphics) {
        int textX = screenX(228);
        int textY = screenY(336);
        int textHeight = 18;
        if (draft.isEmpty()) {
            PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Neler oluyor?", "What is happening?"), textX, textY, screenSize(textHeight), 0xFF70839A);
        } else {
            List<String> lines = wrapToWidth(draft.toString(), 482, textHeight);
            int lineY = textY;
            int start = Math.max(0, lines.size() - 2);
            for (int i = start; i < lines.size(); i++) {
                PhoneModernText.draw(graphics, lines.get(i), textX, lineY, screenSize(textHeight));
                lineY += screenSize(22);
            }
        }
        if (composerFocused && isCaretVisible()) {
            List<String> lines = wrapToWidth(draft.toString(), 482, textHeight);
            String last = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
            int visibleLineCount = Math.min(2, Math.max(1, lines.size()));
            int caretX = textX + PhoneModernText.width(last, screenSize(textHeight)) + screenSize(3);
            int caretY = textY + (visibleLineCount - 1) * screenSize(22);
            drawCaret(graphics, caretX, caretY, screenSize(20), 0xFFFFFFFF);
        }
    }

    private void drawNotifications(GuiGraphics graphics) {
        String self = selfUuid();
        var requests = PhoneSocialClientData.incomingFollowRequests("BLOCKPOST", self);
        var notifications = PhoneSocialClientData.notifications("BLOCKPOST", self);
        int unread = PhoneSocialClientData.unreadNotificationCount("BLOCKPOST", self);
        graphics.fill(screenX(104), screenY(232), screenX(796), screenY(296), 0xFF000000);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Tümü", "All"),
                screenX(142), screenY(250), screenSize(19), 0xFFF2F2F2);
        if (unread > 0) {
            drawRoundedRect(graphics, screenX(684), screenY(246), screenX(766), screenY(282),
                    screenSize(15), 0xFF102A3D);
            PhoneModernText.drawCenteredColoredFitted(graphics, unread + PhoneSettingsStore.text(" yeni", " new"),
                    screenX(725), screenY(256), screenSize(14), screenSize(68), 0xFF1D9BF0);
        }
        graphics.fill(screenX(104), screenY(294), screenX(796), screenY(296), 0xFF2F3336);

        this.feedTop = 296;
        this.feedBottom = 1382;
        int contentHeight = requests.size() * 110 + notifications.size() * 98 + 10;
        this.feedMaxOffset = Math.max(0, contentHeight - (this.feedBottom - this.feedTop));
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.feedMaxOffset));
        graphics.enableScissor(screenX(104), screenY(this.feedTop), screenX(796), screenY(this.feedBottom));

        int top = 306 - this.scrollOffset;
        for (var request : requests) {
            if (top > 1250) break;
            drawAuthorAvatar(graphics, request.fromUuid, request.fromName, 134, top + 16, 54);
            PhoneModernText.drawColoredFitted(graphics,
                    request.fromName + PhoneSettingsStore.text(" seni takip etmek istiyor", " wants to follow you"),
                    screenX(204), screenY(top + 12), screenSize(18), screenSize(540), 0xFFF2F2F2);
            drawRoundedRect(graphics, screenX(474), screenY(top + 54), screenX(606), screenY(top + 96),
                    screenSize(16), 0xFFF2F2F2);
            PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Kabul et", "Accept"),
                    screenX(540), screenY(top + 65), screenSize(15), 0xFF0F1419);
            drawRoundedRect(graphics, screenX(620), screenY(top + 54), screenX(752), screenY(top + 96),
                    screenSize(16), 0xFF202327);
            PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Reddet", "Decline"),
                    screenX(686), screenY(top + 65), screenSize(15), 0xFFF2F2F2);
            graphics.fill(screenX(104), screenY(top + 108), screenX(796), screenY(top + 110), 0xFF2F3336);
            top += 110;
        }
        for (var notification : notifications) {
            if (top > 1280) break;
            drawNotificationCard(graphics, top, notification.actorUuid, notification.actorName,
                    PhoneSettingsStore.localizeNetworkText(notification.text), notification.type, notification.read);
            top += 98;
        }
        if (requests.isEmpty() && notifications.isEmpty()) {
            fillCircle(graphics, screenX(450), screenY(520), screenSize(54), 0xFF0F1419);
            PhoneModernText.drawCenteredColored(graphics, "◌", screenX(450), screenY(488),
                    screenSize(48), 0xFF71767B);
            PhoneModernText.drawCenteredColored(graphics,
                    PhoneSettingsStore.text("Henüz bildirim yok", "No notifications yet"),
                    screenX(450), screenY(598), screenSize(25), 0xFFF2F2F2);
            PhoneModernText.drawCenteredColoredFitted(graphics,
                    PhoneSettingsStore.text("X etkileşimlerin burada görünecek.", "Your X activity will appear here."),
                    screenX(450), screenY(640), screenSize(17), screenSize(520), 0xFF71767B);
        }
        graphics.disableScissor();
        drawFeedScrollbar(graphics, this.feedTop, this.feedBottom, contentHeight);
    }

    private void drawNotificationCard(
            GuiGraphics graphics,
            int top,
            String actorUuid,
            String actorName,
            String body,
            String type,
            boolean read
    ) {
        int accent = switch (type == null ? "" : type) {
            case "LIKE" -> 0xFFF91880;
            case "REPOST" -> 0xFF00BA7C;
            case "COMMENT", "REPLY" -> 0xFF1D9BF0;
            default -> 0xFF7856FF;
        };
        graphics.fill(screenX(104), screenY(top), screenX(796), screenY(top + 96),
                read ? 0xFF000000 : 0xFF07131C);
        fillCircle(graphics, screenX(148), screenY(top + 32), screenSize(16), accent);
        drawAuthorAvatar(graphics, actorUuid, actorName, 180, top + 14, 48);
        PhoneModernText.drawColoredFitted(graphics, actorName == null ? "X" : actorName,
                screenX(244), screenY(top + 12), screenSize(18), screenSize(500), 0xFFF2F2F2);
        PhoneModernText.drawColoredFitted(graphics, body, screenX(244), screenY(top + 43),
                screenSize(15), screenSize(500), 0xFFB0B3B8);
        graphics.fill(screenX(104), screenY(top + 94), screenX(796), screenY(top + 96), 0xFF2F3336);
    }

    private void drawProfile(GuiGraphics graphics, int mouseX, int mouseY) {
        int bannerTop = 232;
        int bannerBottom = 462;
        drawProfileCover(graphics, 104, bannerTop, 692, bannerBottom - bannerTop);
        graphics.fill(screenX(104), screenY(bannerBottom - 3), screenX(796), screenY(bannerBottom), 0xFF2F3336);

        drawProfileAvatarSquare(graphics, 132, 388, 132);

        boolean editHover = inside(mouseX, mouseY, 548, 478, 764, 532);
        drawRoundedRect(graphics, screenX(548), screenY(478), screenX(764), screenY(532),
                screenSize(20), editHover ? 0xFF202327 : 0xFF000000);
        drawRoundedRect(graphics, screenX(550), screenY(480), screenX(762), screenY(530),
                screenSize(18), 0xFF000000);
        PhoneModernText.drawCenteredColoredFitted(graphics, PhoneSettingsStore.text("Profili düzenle", "Edit profile"),
                screenX(656), screenY(494), screenSize(17), screenSize(184), 0xFFF2F2F2);

        PhoneRoleBadgeRenderer.drawNameAndBadge(graphics, selfName(), PhoneSocialClientData.roleBadge(selfUuid()),
                screenX(132), screenY(540), screenSize(31), 0xFFF2F2F2);
        PhoneModernText.drawColored(graphics, handleFor(selfName()), screenX(132), screenY(580),
                screenSize(18), 0xFF71767B);
        List<String> bioLines = wrapToWidth(
                PhoneSettingsStore.localizeNetworkText(PhoneBlockPostStore.profileBio()), 610, 18);
        for (int index = 0; index < Math.min(2, bioLines.size()); index++) {
            PhoneModernText.drawColored(graphics, bioLines.get(index), screenX(132), screenY(616 + index * 24),
                    screenSize(18), 0xFFE7E9EA);
        }
        String joined = joinedFormat().format(Instant.ofEpochMilli(1_720_000_000_000L).atZone(ZoneId.systemDefault()));
        PhoneModernText.drawColoredFitted(graphics,
                PhoneSettingsStore.text("▣  ", "▣  Joined ") + joined
                        + PhoneSettingsStore.text(" tarihinde katıldı", ""),
                screenX(132), screenY(674), screenSize(15), screenSize(620), 0xFF71767B);

        int following = PhoneSocialClientData.followingCount("BLOCKPOST", selfUuid());
        int followers = PhoneSocialClientData.followerCount("BLOCKPOST", selfUuid());
        PhoneModernText.draw(graphics, Integer.toString(following), screenX(132), screenY(712), screenSize(19));
        PhoneModernText.drawColoredFitted(graphics, PhoneSettingsStore.text("Takip edilen", "Following"),
                screenX(168), screenY(715), screenSize(15), screenSize(134), 0xFF71767B);
        PhoneModernText.draw(graphics, Integer.toString(followers), screenX(326), screenY(712), screenSize(19));
        PhoneModernText.drawColoredFitted(graphics, PhoneSettingsStore.text("Takipçi", "Followers"),
                screenX(364), screenY(715), screenSize(15), screenSize(130), 0xFF71767B);

        drawProfileTab(graphics, mouseX, mouseY, ProfileTab.POSTS, 190,
                PhoneSettingsStore.text("Gönderiler", "Posts"));
        drawProfileTab(graphics, mouseX, mouseY, ProfileTab.REPLIES, 360,
                PhoneSettingsStore.text("Yanıtlar", "Replies"));
        drawProfileTab(graphics, mouseX, mouseY, ProfileTab.MEDIA, 530,
                PhoneSettingsStore.text("Medya", "Media"));
        drawProfileTab(graphics, mouseX, mouseY, ProfileTab.LIKES, 700,
                PhoneSettingsStore.text("Beğeniler", "Likes"));
    }

    private void drawProfileTab(GuiGraphics graphics, int mouseX, int mouseY, ProfileTab tab, int centerX, String label) {
        boolean active = profileTab == tab;
        boolean hover = inside(mouseX, mouseY, centerX - 82, 770, centerX + 82, 838);
        int color = active ? 0xFFF2F2F2 : hover ? 0xFFD6D9DB : 0xFF71767B;
        PhoneModernText.drawCenteredColoredFitted(graphics, label, screenX(centerX), screenY(790),
                screenSize(15), screenSize(144), color);
        if (active) {
            drawRoundedRect(graphics, screenX(centerX - 45), screenY(832), screenX(centerX + 45),
                    screenY(838), screenSize(3), 0xFF1D9BF0);
        }
        graphics.fill(screenX(104), screenY(838), screenX(796), screenY(840), 0xFF2F3336);
    }

    private void drawFeed(GuiGraphics graphics, int top, int bottom, boolean profileMode) {
        postHitBoxes.clear();
        List<PostEntry> posts = filteredPosts(profileMode);
        this.feedTop = top;
        this.feedBottom = bottom;

        if (posts.isEmpty()) {
            this.feedMaxOffset = 0;
            String emptyText;
            if (activeTab == Tab.HOME && homeFeed == HomeFeed.FOLLOWING) {
                emptyText = PhoneSettingsStore.text("Takip akışında henüz gönderi yok", "No posts in your following feed yet");
            } else if (activeTab == Tab.PROFILE && profileTab != ProfileTab.POSTS) {
                emptyText = PhoneSettingsStore.text("Bu sekmede henüz içerik yok", "No content in this tab yet");
            } else {
                emptyText = PhoneSettingsStore.text("Henüz gönderi yok", "No posts yet");
            }
            PhoneModernText.drawCenteredColored(graphics, emptyText, screenX(450), screenY(top + 90), screenSize(24), 0xFF71767B);
            return;
        }

        int viewportHeight = bottom - top;
        int totalHeight = totalFeedHeight(posts);
        this.feedMaxOffset = Math.max(0, totalHeight - viewportHeight);
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.feedMaxOffset));

        graphics.enableScissor(screenX(106), screenY(top), screenX(790), screenY(bottom));
        int y = top - this.scrollOffset;
        for (PostEntry post : posts) {
            int height = calculatePostHeight(post);
            if (y + height >= top && y <= bottom) {
                drawPost(graphics, post, y, height, top, bottom);
            }
            y += height + 6;
        }
        graphics.disableScissor();
        drawFeedScrollbar(graphics, top, bottom, totalHeight);
    }

    private int totalFeedHeight(List<PostEntry> posts) {
        int total = 0;
        for (PostEntry post : posts) {
            total += calculatePostHeight(post) + 6;
        }
        return Math.max(0, total - 6);
    }

    private void drawFeedScrollbar(GuiGraphics graphics, int top, int bottom, int totalHeight) {
        int trackLeft = 776;
        int trackRight = 786;
        graphics.fill(screenX(trackLeft), screenY(top), screenX(trackRight), screenY(bottom), 0xA6151515);
        int viewportHeight = bottom - top;
        if (totalHeight <= viewportHeight || this.feedMaxOffset <= 0) {
            this.feedThumbTop = top;
            this.feedThumbBottom = bottom;
            return;
        }
        int thumbHeight = Math.max(70, Math.round(viewportHeight * (viewportHeight / (float) totalHeight)));
        int travel = Math.max(1, viewportHeight - thumbHeight);
        int thumbTop = top + Math.round(travel * (this.scrollOffset / (float) this.feedMaxOffset));
        this.feedThumbTop = thumbTop;
        this.feedThumbBottom = thumbTop + thumbHeight;
        graphics.fill(screenX(trackLeft + 2), screenY(thumbTop), screenX(trackRight - 2), screenY(thumbTop + thumbHeight), 0xFF71767B);
    }

    private void setFeedScrollFromMouse(double mouseY, boolean keepGrabOffset) {
        if (this.feedMaxOffset <= 0 || this.feedBottom <= this.feedTop) {
            this.scrollOffset = 0;
            return;
        }
        int viewportHeight = this.feedBottom - this.feedTop;
        int thumbHeight = Math.max(1, this.feedThumbBottom - this.feedThumbTop);
        int travel = Math.max(1, viewportHeight - thumbHeight);
        double textureMouseY = (mouseY - screenY(this.feedTop)) / Math.max(0.0001F, getPhoneDrawData().scale()) + this.feedTop;
        double desiredTop = keepGrabOffset ? textureMouseY - this.feedScrollbarGrabOffset : textureMouseY - thumbHeight / 2.0;
        double progress = Math.max(0.0, Math.min(1.0, (desiredTop - this.feedTop) / travel));
        this.scrollOffset = (int) Math.round(progress * this.feedMaxOffset);
    }

    private List<PostEntry> filteredPosts(boolean profileMode) {
        String self = selfUuid();
        List<PostEntry> source = profileMode ? PhoneBlockPostStore.all() : PhoneBlockPostStore.feedPosts();
        return source.stream()
                .filter(post -> post.authorUuid() == null || post.authorUuid().isBlank()
                        || self.equals(post.authorUuid()) || !PhoneClientData.isBlocked(post.authorUuid()))
                .filter(post -> !profileMode || visibleInProfile(post, self))
                .filter(post -> activeTab != Tab.HOME || homeFeed != HomeFeed.FOLLOWING
                        || self.equals(post.authorUuid())
                        || PhoneSocialClientData.isAcceptedFollow("BLOCKPOST", self, post.authorUuid()))
                .filter(post -> activeTag.isBlank() || containsHashtag(post.body(), activeTag))
                .toList();
    }

    private boolean visibleInProfile(PostEntry post, String self) {
        return switch (profileTab) {
            case POSTS -> self.equals(post.authorUuid());
            case REPLIES -> PhoneBlockPostStore.comments(post.id()).stream()
                    .anyMatch(comment -> self.equals(comment.authorUuid()));
            case MEDIA -> self.equals(post.authorUuid()) && hasImage(post);
            case LIKES -> post.liked();
        };
    }

    private int calculatePostHeight(PostEntry post) {
        int textHeight = 21;
        int lines = Math.min(7, wrapToWidth(post.body(), 500, textHeight).size());
        int height = 104 + lines * 29 + 58;
        if (hasImage(post)) {
            height += 238;
        }
        return Math.max(222, height);
    }

    private void drawPost(
            GuiGraphics graphics,
            PostEntry post,
            int top,
            int height,
            int viewportTop,
            int viewportBottom
    ) {
        int left = 116;
        int right = 770;
        int bottom = top + height;
        graphics.fill(screenX(left), screenY(top), screenX(right), screenY(bottom), 0xFF000000);
        graphics.fill(screenX(left), screenY(bottom - 2), screenX(right), screenY(bottom), 0xFF2F3336);

        drawAuthorAvatar(graphics, post.authorUuid(), post.authorName(), 136, top + 18, 62);
        PhoneRoleBadgeRenderer.drawNameAndBadge(graphics, post.authorName(), PhoneSocialClientData.roleBadge(post.authorUuid()),
                screenX(214), screenY(top + 14), screenSize(23), 0xFFF4F4F4);
        PhoneModernText.drawColored(graphics, post.handle() + " · " + relativeTime(post.createdAt()),
                screenX(214), screenY(top + 46), screenSize(16), 0xFF71767B);
        boolean canDelete = activeTab == Tab.PROFILE && selfUuid().equals(post.authorUuid());
        if (canDelete) {
            drawRoundedRect(graphics, screenX(688), screenY(top + 12), screenX(754), screenY(top + 48),
                    screenSize(14), 0xFF2A1117);
            PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Sil", "Delete"),
                    screenX(721), screenY(top + 20), screenSize(15), 0xFFF4212E);
        } else {
            PhoneModernText.drawColored(graphics, "•••", screenX(724), screenY(top + 17), screenSize(18), 0xFF71767B);
        }

        List<String> lines = wrapToWidth(post.body(), 520, 21);
        int textY = top + 78;
        int shown = Math.min(7, lines.size());
        for (int index = 0; index < shown; index++) {
            String line = lines.get(index);
            PhoneModernText.drawColored(
                    graphics,
                    line,
                    screenX(214),
                    screenY(textY),
                    screenSize(21),
                    line.contains("#") ? 0xFF1D9BF0 : 0xFFE7E9EA
            );
            textY += 29;
        }
        if (lines.size() > shown) {
            PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Devamını göster", "Show more"), screenX(214), screenY(textY), screenSize(17), 0xFF1D9BF0);
            textY += 24;
        }

        int actionsTop;
        if (hasImage(post)) {
            int imageTop = textY + 7;
            drawPostImage(graphics, post, 214, imageTop, 536, 220);
            actionsTop = imageTop + 228;
        } else {
            actionsTop = textY + 6;
        }

        int commentCount = PhoneBlockPostStore.commentCount(post.id());
        drawActionButton(graphics, 202, actionsTop, 326, "○  " + commentCount, 0xFF71767B);
        drawActionButton(graphics, 332, actionsTop, 456, "↻  " + post.reposts(),
                post.reposted() ? 0xFF00BA7C : 0xFF71767B);
        drawActionButton(graphics, 462, actionsTop, 586, "♥  " + post.likes(),
                post.liked() ? 0xFFF91880 : 0xFF71767B);
        drawActionButton(graphics, 592, actionsTop, 748, post.bookmarked() ? "◆" : "◇",
                post.bookmarked() ? 0xFF1D9BF0 : 0xFF71767B);

        int clippedTop = Math.max(top, viewportTop);
        int clippedBottom = Math.min(bottom, viewportBottom);
        if (clippedBottom > clippedTop) {
            int authorNameLeft = screenX(214);
            int authorNameRight = Math.min(screenX(680), authorNameLeft
                    + PhoneModernText.width(post.authorName(), screenSize(23)) + screenSize(8));
            postHitBoxes.add(new PostHitBox(
                    post.id(),
                    screenX(left), screenY(clippedTop), screenX(right), screenY(clippedBottom),
                    screenX(136), screenY(top + 18), screenX(198), screenY(top + 80),
                    authorNameLeft, screenY(top + 10), authorNameRight, screenY(top + 40),
                    canDelete ? screenX(684) : 0, canDelete ? screenY(top + 8) : 0,
                    canDelete ? screenX(758) : 0, canDelete ? screenY(top + 52) : 0,
                    screenX(196), screenY(actionsTop - 8), screenX(330), screenY(actionsTop + 56),
                    screenX(326), screenY(actionsTop - 8), screenX(460), screenY(actionsTop + 56),
                    screenX(456), screenY(actionsTop - 8), screenX(590), screenY(actionsTop + 56),
                    screenX(586), screenY(actionsTop - 8), screenX(754), screenY(actionsTop + 56),
                    screenY(viewportTop), screenY(viewportBottom)
            ));
        }
    }

    private void drawActionButton(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            String label,
            int color
    ) {
        PhoneModernText.drawCenteredColoredFitted(graphics, label, screenX((left + right) / 2),
                screenY(top + 9), screenSize(22), screenSize(right - left - 14), color);
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
                PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Fotoğraf bulunamadı", "Photo not found"), screenX(left + width / 2), screenY(top + height / 2 - 10), screenSize(16), 0xFF8196AD);
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
        PhoneModernText.drawCentered(graphics, initial, screenX(left + size / 2), screenY(top + 14), screenSize(size / 2));
    }
    private void drawProfileCover(GuiGraphics graphics, int left, int top, int width, int height) {
        if (!drawGalleryTexture(graphics, PhoneBlockPostStore.profileCoverPath(), left, top, width, height)) {
            graphics.fill(screenX(left), screenY(top), screenX(left + width), screenY(top + height), 0xFF242424);
            graphics.fill(screenX(left), screenY(top), screenX(left + width), screenY(top + 46), 0xFF333333);
            graphics.fill(screenX(left), screenY(top + height - 36), screenX(left + width),
                    screenY(top + height), 0xFF1A1A1A);
        }
    }

    private void drawProfileAvatarSquare(GuiGraphics graphics, int left, int top, int size) {
        graphics.fill(screenX(left - 8), screenY(top - 8), screenX(left + size + 8), screenY(top + size + 8), 0xFF000000);
        if (!drawGalleryTexture(graphics, PhoneBlockPostStore.profileAvatarPath(), left, top, size, size)) {
            drawAuthorAvatar(graphics, selfUuid(), selfName(), left + 6, top + 6, size - 12);
        }
    }

    private boolean drawGalleryTexture(GuiGraphics graphics, String pathValue, int left, int top, int width, int height) {
        if (pathValue == null || pathValue.isBlank()) return false;
        if (pathValue.startsWith("social:")) {
            PhonePhotoStore.PhotoTexture texture = PhonePhotoStore.textureFor(this.minecraft, pathValue.substring("social:".length()));
            if (texture == null) return false;
            graphics.blit(texture.location(), screenX(left), screenY(top), screenSize(width), screenSize(height), 0, 0,
                    texture.width(), texture.height(), texture.width(), texture.height());
            return true;
        }
        try {
            Path path = Path.of(pathValue);
            if (!Files.exists(path)) return false;
            PhoneGalleryStore.GalleryTexture texture = PhoneGalleryStore.textureFor(this.minecraft, path);
            if (texture == null) return false;
            graphics.blit(texture.location(), screenX(left), screenY(top), screenSize(width), screenSize(height), 0, 0,
                    texture.width(), texture.height(), texture.width(), texture.height());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void drawBottomNavigation(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(screenX(104), screenY(1384), screenX(796), screenY(1490), 0xFF000000);
        graphics.fill(screenX(104), screenY(1384), screenX(796), screenY(1386), 0xFF2F3336);
        drawTab(graphics, mouseX, mouseY, Tab.HOME, 184, "⌂", PhoneSettingsStore.text("Ana Akış", "Home"));
        drawTab(graphics, mouseX, mouseY, Tab.EXPLORE, 360, "⌕", PhoneSettingsStore.text("Keşfet", "Explore"));
        drawTab(graphics, mouseX, mouseY, Tab.NOTIFICATIONS, 540, "◌",
                PhoneSettingsStore.text("Bildirimler", "Notifications"));
        drawTab(graphics, mouseX, mouseY, Tab.PROFILE, 716, "●", PhoneSettingsStore.text("Profil", "Profile"));
        int unread = PhoneSocialClientData.unreadNotificationCount("BLOCKPOST", selfUuid());
        if (unread > 0) {
            fillCircle(graphics, screenX(565), screenY(1408), screenSize(12), 0xFF1D9BF0);
            PhoneModernText.drawCenteredColored(graphics, unread > 9 ? "9+" : Integer.toString(unread),
                    screenX(565), screenY(1402), screenSize(10), 0xFFFFFFFF);
        }
    }

    private void drawTab(GuiGraphics graphics, int mouseX, int mouseY, Tab tab, int centerX, String icon, String label) {
        boolean active = activeTab == tab;
        boolean hover = inside(mouseX, mouseY, centerX - 76, 1394, centerX + 76, 1478);
        if (hover && !active) {
            drawRoundedRect(graphics, screenX(centerX - 58), screenY(1396), screenX(centerX + 58),
                    screenY(1474), screenSize(18), 0xFF0A0A0A);
        }
        int color = active ? 0xFFF2F2F2 : 0xFF71767B;
        PhoneModernText.drawCenteredColored(graphics, icon, screenX(centerX), screenY(1408), screenSize(31), color);
        PhoneModernText.drawCenteredColoredFitted(graphics, label, screenX(centerX), screenY(1450),
                screenSize(15), screenSize(128), color);
        if (active) {
            drawRoundedRect(graphics, screenX(centerX - 24), screenY(1473), screenX(centerX + 24),
                    screenY(1478), screenSize(2), 0xFF1D9BF0);
        }
    }

    private void drawStatus(GuiGraphics graphics) {
        if (statusText.isBlank() || System.currentTimeMillis() > statusUntil) {
            return;
        }
        drawRoundedRect(graphics, screenX(236), screenY(1328), screenX(664), screenY(1376),
                screenSize(16), 0xEE1D9BF0);
        PhoneModernText.drawCenteredFitted(graphics, statusText, screenX(450), screenY(1341),
                screenSize(17), screenSize(400));
    }

    private void publishPost() {
        String body = draft.toString().strip();
        if (body.isBlank() && attachedPhoto.isBlank()) {
            showStatus(PhoneSettingsStore.text("Önce bir şey yaz veya fotoğraf ekle",
                    "Write something or add a photo first"));
            return;
        }
        if (!PhoneSocialClientData.publishBlockPost(body, attachedPhoto)) return;
        draft.setLength(0);
        attachedPhoto = "";
        composerFocused = false;
        scrollOffset = 0;
        activeTag = "";
        showStatus(PhoneSettingsStore.text("Gönderi paylaşıldı", "Post published"));
    }

    private void openComposerPhotoPicker() {
        photoPickerOpen = true;
        selectedComposerPhoto = null;
        composerFocused = false;
    }

    private void drawComposerPhotoPicker(GuiGraphics graphics) {
        drawOpaqueModalBackdrop(graphics, screenX(104), screenY(136), screenX(796), screenY(1490), 0xFF000000);
        drawRoundedRect(graphics, screenX(142), screenY(404), screenX(758), screenY(1148),
                screenSize(24), 0xFF101214);
        PhoneModernText.draw(graphics, PhoneSettingsStore.text("Gönderiye fotoğraf ekle", "Add a photo to the post"), screenX(174), screenY(438), screenSize(28));
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Galeriden seç veya şimdi fotoğraf çek", "Choose from gallery or take a photo now"), screenX(174), screenY(482), screenSize(16), 0xFF71767B);

        List<Path> photos = PhoneGalleryStore.photos(this.minecraft);
        int[] lefts = {174, 370, 566};
        for (int i = 0; i < Math.min(6, photos.size()); i++) {
            int col = i % 3;
            int row = i / 3;
            int left = lefts[col];
            int top = 536 + row * 184;
            Path photo = photos.get(i);
            boolean selected = photo.equals(selectedComposerPhoto);
            drawRoundedRect(graphics, screenX(left - 7), screenY(top - 7), screenX(left + 157),
                    screenY(top + 157), screenSize(10), selected ? 0xFF1D9BF0 : 0xFF2F3336);
            var texture = PhoneGalleryStore.textureFor(this.minecraft, photo);
            if (texture != null) {
                graphics.blit(texture.location(), screenX(left), screenY(top), screenSize(150), screenSize(150), 0, 0,
                        texture.width(), texture.height(), texture.width(), texture.height());
            }
        }
        if (photos.isEmpty()) {
            PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Galeride fotoğraf yok", "No photos in gallery"), screenX(450), screenY(660), screenSize(20), 0xFF7E91A6);
        }

        drawRoundedRect(graphics, screenX(170), screenY(930), screenX(430), screenY(1002),
                screenSize(22), 0xFF202327);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("◉  Kamerayı aç", "◉  Open camera"), screenX(300), screenY(951), screenSize(20), 0xFFFFFFFF);
        drawRoundedRect(graphics, screenX(470), screenY(930), screenX(730), screenY(1002),
                screenSize(22), selectedComposerPhoto == null ? 0xFF202327 : 0xFF1D9BF0);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("▣  Seçileni ekle", "▣  Add selected"), screenX(600), screenY(951), screenSize(20), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("İptal", "Cancel"), screenX(450), screenY(1052), screenSize(18), 0xFF9AABBD);
    }

    private boolean handleComposerPhotoPickerClick(double mouseX, double mouseY) {
        List<Path> photos = PhoneGalleryStore.photos(this.minecraft);
        int[] lefts = {174, 370, 566};
        for (int i = 0; i < Math.min(6, photos.size()); i++) {
            int col = i % 3;
            int row = i / 3;
            int left = lefts[col];
            int top = 536 + row * 184;
            if (inside(mouseX, mouseY, left - 8, top - 8, left + 158, top + 158)) {
                selectedComposerPhoto = photos.get(i);
                return true;
            }
        }
        if (inside(mouseX, mouseY, 170, 930, 430, 1002)) {
            pendingComposerCameraCapture = true;
            pendingComposerDraft = draft.toString();
            photoPickerOpen = false;
            selectedComposerPhoto = null;
            this.minecraft.setScreen(new ZPhoneCameraScreen());
            return true;
        }
        if (inside(mouseX, mouseY, 470, 930, 730, 1002) && selectedComposerPhoto != null) {
            attachedPhoto = selectedComposerPhoto.toAbsolutePath().normalize().toString();
            photoPickerOpen = false;
            selectedComposerPhoto = null;
            showStatus(PhoneSettingsStore.text("Fotoğraf gönderiye eklendi", "Photo added to post"));
            return true;
        }
        if (inside(mouseX, mouseY, 330, 1025, 570, 1095)) {
            photoPickerOpen = false;
            selectedComposerPhoto = null;
            return true;
        }
        return true;
    }

    public static boolean finishPendingCameraCapture(Minecraft minecraft) {
        if (minecraft == null || !pendingComposerCameraCapture) return false;
        pendingComposerCameraCapture = false;
        String savedDraft = pendingComposerDraft;
        pendingComposerDraft = "";
        PhoneBlockPostScreen screen = new PhoneBlockPostScreen();
        if (!savedDraft.isBlank()) screen.draft.append(savedDraft);
        Path latest = PhoneGalleryStore.latestPhoto(minecraft);
        if (latest != null) {
            screen.attachedPhoto = latest.toAbsolutePath().normalize().toString();
            screen.showStatus(PhoneSettingsStore.text("Yeni fotoğraf gönderiye eklendi",
                    "New photo added to post"));
        } else {
            screen.showStatus(PhoneSettingsStore.text("Fotoğraf kaydedilemedi", "Photo could not be saved"));
        }
        PhoneNavigationState.replaceCurrent(minecraft, screen);
        return true;
    }

    public static boolean cancelPendingCameraCapture(Minecraft minecraft) {
        if (minecraft == null || !pendingComposerCameraCapture) return false;
        pendingComposerCameraCapture = false;
        String savedDraft = pendingComposerDraft;
        pendingComposerDraft = "";
        PhoneBlockPostScreen screen = new PhoneBlockPostScreen();
        if (!savedDraft.isBlank()) screen.draft.append(savedDraft);
        PhoneNavigationState.replaceCurrent(minecraft, screen);
        return true;
    }

    private void drawDeleteConfirmation(GuiGraphics graphics) {
        drawOpaqueModalBackdrop(graphics, screenX(104), screenY(136), screenX(796), screenY(1490), 0xFF000000);
        drawRoundedRect(graphics, screenX(230), screenY(610), screenX(670), screenY(856),
                screenSize(24), 0xFF16181C);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Gönderiyi sil?", "Delete post?"), screenX(450), screenY(652), screenSize(30), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Bu işlem geri alınamaz.", "This action cannot be undone."), screenX(450), screenY(708), screenSize(17), 0xFF71767B);
        drawRoundedRect(graphics, screenX(274), screenY(770), screenX(430), screenY(826),
                screenSize(18), 0xFF2F3336);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Vazgeç", "Cancel"), screenX(352), screenY(786), screenSize(18), 0xFFE7E9EA);
        drawRoundedRect(graphics, screenX(470), screenY(770), screenX(626), screenY(826),
                screenSize(18), 0xFFF4212E);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Sil", "Delete"), screenX(548), screenY(786), screenSize(18), 0xFFFFFFFF);
    }

    private void showStatus(String text) {
        statusText = text == null ? "" : text;
        statusUntil = System.currentTimeMillis() + 2200L;
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

    private String handleFor(String name) {
        String normalized = name == null ? "player" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        return "@" + (normalized.isBlank() ? "player" : normalized);
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
        if (photoPickerOpen || !pendingDeletePostId.isBlank()) return true;
        if (exploreSearchFocused && SharedConstants.isAllowedChatCharacter(codePoint) && exploreSearch.length() < 32) {
            String typed = Character.toString(codePoint);
            int remaining = 32 - exploreSearch.length();
            exploreSearch.append(typed, 0, Math.min(typed.length(), remaining));
            return true;
        }
        if (composerFocused && SharedConstants.isAllowedChatCharacter(codePoint) && draft.length() < PhoneBlockPostStore.MAX_POST_LENGTH) {
            String typed = Character.toString(codePoint);
            int remaining = PhoneBlockPostStore.MAX_POST_LENGTH - draft.length();
            draft.append(typed, 0, Math.min(typed.length(), remaining));
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!pendingDeletePostId.isBlank()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (inside(mouseX, mouseY, 274, 770, 430, 826)) {
                    pendingDeletePostId = "";
                    return true;
                }
                if (inside(mouseX, mouseY, 470, 770, 626, 826)) {
                    String postId = pendingDeletePostId;
                    pendingDeletePostId = "";
                    PhoneBlockPostStore.delete(postId, selfUuid());
                    return true;
                }
            }
            return true;
        }
        if (photoPickerOpen) return handleComposerPhotoPickerClick(mouseX, mouseY);
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
                && handleBridgeIslandClick(mouseX, mouseY)) return true;
        if (isInsideHomeIndicator(mouseX, mouseY)) {
            ZPhoneClient.openHomeScreen();
            return true;
        }
        if (handleHomeIndicatorClick(mouseX, mouseY, button)) {
            return true;
        }

        // Bottom navigation gets priority over feed cards so a covered post can never open.
        if (inside(mouseX, mouseY, 112, 1396, 256, 1478)) {
            switchTab(Tab.HOME);
            return true;
        }
        if (inside(mouseX, mouseY, 288, 1396, 432, 1478)) {
            switchTab(Tab.EXPLORE);
            return true;
        }
        if (inside(mouseX, mouseY, 468, 1396, 612, 1478)) {
            switchTab(Tab.NOTIFICATIONS);
            return true;
        }
        if (inside(mouseX, mouseY, 644, 1396, 788, 1478)) {
            switchTab(Tab.PROFILE);
            return true;
        }

        if (inside(mouseX, mouseY, 770, this.feedTop, 792, this.feedBottom) && this.feedMaxOffset > 0) {
            double textureY = (mouseY - screenY(this.feedTop)) / Math.max(0.0001F, getPhoneDrawData().scale()) + this.feedTop;
            if (textureY >= this.feedThumbTop && textureY <= this.feedThumbBottom) {
                this.draggingFeedScrollbar = true;
                this.feedScrollbarGrabOffset = (int) Math.round(textureY - this.feedThumbTop);
            } else {
                this.feedScrollbarGrabOffset = Math.max(1, (this.feedThumbBottom - this.feedThumbTop) / 2);
                setFeedScrollFromMouse(mouseY, false);
                this.draggingFeedScrollbar = true;
            }
            return true;
        }

        if (activeTab == Tab.EXPLORE) {
            if (inside(mouseX, mouseY, 126, 252, 774, 314)) {
                if (!exploreSearch.isEmpty() && inside(mouseX, mouseY, 712, 252, 774, 314)) {
                    exploreSearch.setLength(0);
                } else {
                    exploreSearchFocused = true;
                }
                composerFocused = false;
                return true;
            }

            int trendIndex = 0;
            for (TrendEntry trend : currentTrends()) {
                int column = trendIndex % 2;
                int row = trendIndex / 2;
                int left = 126 + column * 330;
                int top = 378 + row * 78;
                if (inside(mouseX, mouseY, left, top, left + 318, top + 68)) {
                    activeTag = activeTag.equalsIgnoreCase(trend.tag()) ? "" : trend.tag();
                    scrollOffset = 0;
                    exploreSearchFocused = false;
                    return true;
                }
                trendIndex++;
            }

            List<com.mikasrevs.newphone.network.PhoneSocialStateData.ProfileEntry> profiles = exploreProfiles();
            for (int index = 0; index < profiles.size(); index++) {
                var profile = profiles.get(index);
                int rowTop = 590 + index * 76;
                if (inside(mouseX, mouseY, 126, rowTop, 590, rowTop + 72)) {
                    minecraft.setScreen(new PhoneBlockPostUserProfileScreen(profile.playerUuid));
                    return true;
                }
                if (inside(mouseX, mouseY, 606, rowTop + 8, 756, rowTop + 58)) {
                    String state = PhoneSocialClientData.followState("BLOCKPOST", selfUuid(), profile.playerUuid);
                    if ("ACCEPTED".equals(state) || "PENDING".equals(state)) PhoneSocialClientData.removeFollow("BLOCKPOST", profile.playerUuid);
                    else PhoneSocialClientData.sendFollowRequest("BLOCKPOST", profile.playerUuid);
                    return true;
                }
            }
            exploreSearchFocused = false;
        }

        if (activeTab == Tab.HOME) {
            if (inside(mouseX, mouseY, 104, 232, 450, 298)) {
                homeFeed = HomeFeed.FOR_YOU;
                scrollOffset = 0;
                return true;
            }
            if (inside(mouseX, mouseY, 450, 232, 796, 298)) {
                homeFeed = HomeFeed.FOLLOWING;
                scrollOffset = 0;
                return true;
            }
            if (inside(mouseX, mouseY, 716, 158, 772, 214)) {
                composerFocused = true;
                return true;
            }
            if (inside(mouseX, mouseY, 210, 320, 756, 388)) {
                composerFocused = true;
                return true;
            }
            if (inside(mouseX, mouseY, 210, 398, 402, 448)) {
                openComposerPhotoPicker();
                return true;
            }
            if (inside(mouseX, mouseY, 608, 398, 756, 448)) {
                publishPost();
                return true;
            }
        }

        if (activeTab == Tab.NOTIFICATIONS) {
            int requestTop = 306 - this.scrollOffset;
            for (var request : PhoneSocialClientData.incomingFollowRequests("BLOCKPOST", selfUuid())) {
                if (inside(mouseX, mouseY, 474, requestTop + 52, 606, requestTop + 98)) {
                    PhoneSocialClientData.acceptFollowRequest("BLOCKPOST", request.id);
                    return true;
                }
                if (inside(mouseX, mouseY, 620, requestTop + 52, 752, requestTop + 98)) {
                    PhoneSocialClientData.declineFollowRequest("BLOCKPOST", request.id);
                    return true;
                }
                requestTop += 110;
            }
            int notificationTop = requestTop;
            for (var notification : PhoneSocialClientData.notifications("BLOCKPOST", selfUuid())) {
                if (notificationTop > 1280) break;
                if (inside(mouseX, mouseY, 104, notificationTop, 796, notificationTop + 96)
                        && notification.postId != null && !notification.postId.isBlank()
                        && PhoneBlockPostStore.find(notification.postId) != null) {
                    this.minecraft.setScreen(new PhoneBlockPostDetailScreen(notification.postId));
                    return true;
                }
                notificationTop += 98;
            }
            PhoneSocialClientData.markNotificationsRead("BLOCKPOST");
        }

        if (activeTab == Tab.PROFILE) {
            if (inside(mouseX, mouseY, 124, 696, 304, 752)) {
                this.minecraft.setScreen(new ZPhoneXPeopleScreen(selfUuid(), selfName(), ZPhoneXPeopleScreen.Mode.FOLLOWING));
                return true;
            }
            if (inside(mouseX, mouseY, 312, 696, 492, 752)) {
                this.minecraft.setScreen(new ZPhoneXPeopleScreen(selfUuid(), selfName(), ZPhoneXPeopleScreen.Mode.FOLLOWERS));
                return true;
            }
            if (inside(mouseX, mouseY, 548, 478, 764, 532)) {
                this.minecraft.setScreen(new PhoneBlockPostProfileEditScreen());
                return true;
            }
            if (inside(mouseX, mouseY, 108, 770, 272, 838)) {
                switchProfileTab(ProfileTab.POSTS);
                return true;
            }
            if (inside(mouseX, mouseY, 278, 770, 442, 838)) {
                switchProfileTab(ProfileTab.REPLIES);
                return true;
            }
            if (inside(mouseX, mouseY, 448, 770, 612, 838)) {
                switchProfileTab(ProfileTab.MEDIA);
                return true;
            }
            if (inside(mouseX, mouseY, 618, 770, 782, 838)) {
                switchProfileTab(ProfileTab.LIKES);
                return true;
            }
        }

        for (PostHitBox hitBox : postHitBoxes) {
            if (hitBox.insideDelete(mouseX, mouseY)) {
                pendingDeletePostId = hitBox.postId();
                composerFocused = false;
                exploreSearchFocused = false;
                return true;
            }
            if (hitBox.insideAuthor(mouseX, mouseY)) {
                PostEntry post = PhoneBlockPostStore.find(hitBox.postId());
                if (post != null && post.authorUuid() != null && !post.authorUuid().isBlank()) {
                    this.minecraft.setScreen(new PhoneBlockPostUserProfileScreen(post.authorUuid()));
                    return true;
                }
            }
            if (hitBox.insideComment(mouseX, mouseY)) {
                this.minecraft.setScreen(new PhoneBlockPostDetailScreen(hitBox.postId()));
                return true;
            }
            if (hitBox.insideRepost(mouseX, mouseY)) {
                PhoneBlockPostStore.toggleRepost(hitBox.postId());
                return true;
            }
            if (hitBox.insideLike(mouseX, mouseY)) {
                PhoneBlockPostStore.toggleLike(hitBox.postId());
                return true;
            }
            if (hitBox.insideBookmark(mouseX, mouseY)) {
                PhoneBlockPostStore.toggleBookmark(hitBox.postId());
                return true;
            }
            if (hitBox.insideCard(mouseX, mouseY)) {
                this.minecraft.setScreen(new PhoneBlockPostDetailScreen(hitBox.postId()));
                return true;
            }
        }

        composerFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void switchTab(Tab tab) {
        activeTab = tab;
        scrollOffset = 0;
        composerFocused = false;
        if (tab != Tab.EXPLORE) exploreSearchFocused = false;
        if (tab == Tab.HOME || tab == Tab.PROFILE) {
            activeTag = "";
        }
        if (tab == Tab.NOTIFICATIONS) PhoneSocialClientData.markNotificationsRead("BLOCKPOST");
    }

    private void switchProfileTab(ProfileTab tab) {
        profileTab = tab;
        scrollOffset = 0;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (photoPickerOpen || !pendingDeletePostId.isBlank()) return true;
        if (y >= screenY(this.feedTop) && y <= screenY(this.feedBottom) && delta != 0.0D) {
            int step = 62;
            if (delta < 0.0D) {
                this.scrollOffset = Math.min(this.feedMaxOffset, this.scrollOffset + step);
            } else {
                this.scrollOffset = Math.max(0, this.scrollOffset - step);
            }
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (photoPickerOpen || !pendingDeletePostId.isBlank()) {
            this.draggingFeedScrollbar = false;
            return true;
        }
        if (this.draggingFeedScrollbar) {
            setFeedScrollFromMouse(mouseY, true);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (photoPickerOpen || !pendingDeletePostId.isBlank()) {
            this.draggingFeedScrollbar = false;
            return true;
        }
        if (this.draggingFeedScrollbar) {
            this.draggingFeedScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!pendingDeletePostId.isBlank()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) pendingDeletePostId = "";
            return true;
        }
        if (photoPickerOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                photoPickerOpen = false;
                selectedComposerPhoto = null;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (exploreSearchFocused) { exploreSearchFocused = false; return true; }
            if (composerFocused) {
                composerFocused = false;
                return true;
            }
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }
        if (exploreSearchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!exploreSearch.isEmpty()) exploreSearch.deleteCharAt(exploreSearch.length() - 1);
            return true;
        }
        if (composerFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!draft.isEmpty()) {
                    draft.deleteCharAt(draft.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                publishPost();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollOffset += 240;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scrollOffset = Math.max(0, scrollOffset - 240);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static final class TrendCounter {
        private final String tag;
        private final Set<String> authors = new HashSet<>();
        private long lastUsedAt;

        private TrendCounter(String tag) {
            this.tag = tag;
        }
    }

    private record TrendEntry(String tag, int authorCount, long lastUsedAt) {
    }

    private enum Tab {
        HOME,
        EXPLORE,
        NOTIFICATIONS,
        PROFILE
    }

    private enum HomeFeed {
        FOR_YOU,
        FOLLOWING
    }

    private enum ProfileTab {
        POSTS,
        REPLIES,
        MEDIA,
        LIKES
    }

    private record PostHitBox(
            String postId,
            int cardLeft,
            int cardTop,
            int cardRight,
            int cardBottom,
            int authorHeadLeft,
            int authorHeadTop,
            int authorHeadRight,
            int authorHeadBottom,
            int authorNameLeft,
            int authorNameTop,
            int authorNameRight,
            int authorNameBottom,
            int deleteLeft,
            int deleteTop,
            int deleteRight,
            int deleteBottom,
            int commentLeft,
            int commentTop,
            int commentRight,
            int commentBottom,
            int repostLeft,
            int repostTop,
            int repostRight,
            int repostBottom,
            int likeLeft,
            int likeTop,
            int likeRight,
            int likeBottom,
            int bookmarkLeft,
            int bookmarkTop,
            int bookmarkRight,
            int bookmarkBottom,
            int viewportTop,
            int viewportBottom
    ) {
        boolean insideCard(double x, double y) {
            return y >= viewportTop && y <= viewportBottom
                    && x >= cardLeft && x <= cardRight && y >= cardTop && y <= cardBottom;
        }

        boolean insideAuthor(double x, double y) {
            if (y < viewportTop || y > viewportBottom) return false;
            boolean head = x >= authorHeadLeft && x <= authorHeadRight
                    && y >= authorHeadTop && y <= authorHeadBottom;
            boolean name = x >= authorNameLeft && x <= authorNameRight
                    && y >= authorNameTop && y <= authorNameBottom;
            return head || name;
        }

        boolean insideDelete(double x, double y) {
            return deleteRight > deleteLeft
                    && y >= viewportTop && y <= viewportBottom
                    && x >= deleteLeft && x <= deleteRight && y >= deleteTop && y <= deleteBottom;
        }

        boolean insideComment(double x, double y) {
            return y >= viewportTop && y <= viewportBottom
                    && x >= commentLeft && x <= commentRight && y >= commentTop && y <= commentBottom;
        }

        boolean insideRepost(double x, double y) {
            return y >= viewportTop && y <= viewportBottom
                    && x >= repostLeft && x <= repostRight && y >= repostTop && y <= repostBottom;
        }

        boolean insideLike(double x, double y) {
            return y >= viewportTop && y <= viewportBottom
                    && x >= likeLeft && x <= likeRight && y >= likeTop && y <= likeBottom;
        }

        boolean insideBookmark(double x, double y) {
            return y >= viewportTop && y <= viewportBottom
                    && x >= bookmarkLeft && x <= bookmarkRight && y >= bookmarkTop && y <= bookmarkBottom;
        }

    }
}

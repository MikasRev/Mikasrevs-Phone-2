package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import com.mikasrevs.newphone.network.PhoneStateData;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Tinder-style multiplayer matching app that replaces the old local Browser shell. */
public final class ZPhoneBrowserScreen extends AbstractZPhoneScreen {
    private enum Tab { DISCOVER, MATCHES, PROFILE }
    private enum CardAnimation { NONE, SNAP_BACK, SWIPE_LEFT, SWIPE_RIGHT }

    private static final long CARD_SWIPE_MS = 235L;
    private static final long CARD_SNAP_MS = 165L;
    private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Tab tab = Tab.DISCOVER;
    private long lastSyncAt;
    private int conversationScroll;
    private int freshMatchScroll;
    private final Set<String> locallySkipped = new LinkedHashSet<>();
    private Set<String> knownMatches = new LinkedHashSet<>();

    private boolean draggingCard;
    private double dragStartX;
    private float cardOffsetX;
    private CardAnimation cardAnimation = CardAnimation.NONE;
    private long cardAnimationStartedAt;
    private float cardAnimationFromX;
    private PhoneSocialStateData.ProfileEntry animatedCandidate;
    private long cardAppearStartedAt = System.currentTimeMillis();

    private PhoneSocialStateData.ProfileEntry matchPopup;

    private boolean editingProfile;
    private boolean galleryOpen;
    private boolean bioFocused;
    private final StringBuilder bioDraft = new StringBuilder();
    private String selectedPhotoPath = "";
    private boolean discoveryEnabled = true;
    private int galleryOffset;

    private boolean preferencesOpen;
    private int draftAge = 18;
    private String draftGender = "UNSPECIFIED";
    private String draftShowGender = "EVERYONE";
    private int draftMinAge = 18;
    private int draftMaxAge = 99;

    public ZPhoneBrowserScreen() {
        this(false);
    }

    public ZPhoneBrowserScreen(boolean openMessages) {
        super(Component.literal("Minder"));
        tab = openMessages ? Tab.MATCHES : Tab.DISCOVER;
        PhoneSocialClientData.requestSync();
        PhoneClientData.requestSync();
        knownMatches = new LinkedHashSet<>(PhoneSocialClientData.tinderMatchUuids());
        PhoneSocialClientData.markNotificationsRead("TINDER");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        syncOccasionally();
        updateCardAnimation();
        detectNewMatch();
        drawWallpaper(g);
        drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xF9080A0F);
        drawStatusBar(g);

        if (matchPopup != null) {
            drawOpaqueModalBackdrop(g, x(3), y(40), x(297), y(606), 0xFF080A0F);
            drawMatchPopup(g);
            drawHomeIndicator(g);
            drawFrame(g);
            return;
        }
        if (preferencesOpen) {
            drawOpaqueModalBackdrop(g, x(3), y(40), x(297), y(606), 0xFF080A0F);
            drawPreferences(g, mouseX, mouseY);
            drawHomeIndicator(g);
            drawFrame(g);
            return;
        }
        if (galleryOpen) {
            drawOpaqueModalBackdrop(g, x(3), y(40), x(297), y(606), 0xFF080A0F);
            drawGallery(g, mouseX, mouseY);
            drawHomeIndicator(g);
            drawFrame(g);
            return;
        }
        if (editingProfile) {
            drawOpaqueModalBackdrop(g, x(3), y(40), x(297), y(606), 0xFF080A0F);
            drawProfileEditor(g, mouseX, mouseY);
            drawHomeIndicator(g);
            drawFrame(g);
            return;
        }

        drawHeader(g);

        if (tab == Tab.DISCOVER) drawDiscover(g, mouseX, mouseY);
        else if (tab == Tab.MATCHES) drawMatches(g, mouseX, mouseY);
        else drawProfile(g, mouseX, mouseY);

        drawBottomNav(g);
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void syncOccasionally() {
        long now = System.currentTimeMillis();
        if (now - lastSyncAt < 4000L) return;
        lastSyncAt = now;
        PhoneSocialClientData.requestSync();
        PhoneClientData.requestSync();
    }

    private void detectNewMatch() {
        Set<String> current = new LinkedHashSet<>(PhoneSocialClientData.tinderMatchUuids());
        for (String uuid : current) {
            if (!knownMatches.contains(uuid)) {
                PhoneSocialStateData.ProfileEntry profile = PhoneSocialClientData.profile(uuid);
                if (profile != null) matchPopup = profile;
                break;
            }
        }
        knownMatches = current;
    }

    private void drawHeader(GuiGraphics g) {
        drawFlame(g, 24, 65, 8, 0xFFFF4D67);
        PhoneModernText.drawColored(g, "Minder", x(39), y(55), readableTextSize(21), 0xFFFFFFFF);
        int matches = PhoneSocialClientData.tinderMatches().size();
        if (matches > 0) {
            fillCircle(g, x(274), y(67), size(12), 0xFFFF4D67);
            PhoneModernText.drawCenteredColored(g, matches > 9 ? "9+" : Integer.toString(matches), x(274), y(61), readableTextSize(6), 0xFFFFFFFF);
        }
    }

    private void drawBottomNav(GuiGraphics g) {
        g.fill(x(16), y(548), x(284), y(549), 0x24FFFFFF);
        drawNavItem(g, Tab.DISCOVER, 58, "⌁", PhoneSettingsStore.text("Keşfet", "Explore"));
        drawNavItem(g, Tab.MATCHES, 150, "●", PhoneSettingsStore.text("Mesajlar", "Messages"));
        drawNavItem(g, Tab.PROFILE, 242, "●", PhoneSettingsStore.text("Profil", "Profile"));
    }

    private void drawNavItem(GuiGraphics g, Tab target, int centerX, String icon, String label) {
        boolean active = tab == target;
        PhoneModernText.drawCenteredColored(g, icon, x(centerX), y(557), readableTextSize(target == Tab.PROFILE ? 12 : 16), active ? 0xFFFF5369 : 0xFF707983);
        PhoneModernText.drawCenteredColoredFitted(g, label, x(centerX), y(580), readableTextSize(6), size(78), active ? 0xFFFF697D : 0xFF737C87);
    }

    private void drawDiscover(GuiGraphics g, int mouseX, int mouseY) {
        PhoneSocialStateData.ProfileEntry candidate = currentCandidate();
        if (candidate == null) {
            drawRoundedRect(g, x(24), y(116), x(276), y(447), size(20), 0xFF12171E);
            drawFlame(g, 150, 220, 28, 0xFFFF5369);
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Şimdilik bu kadar", "That's all for now"), x(150), y(274), readableTextSize(15), 0xFFFFFFFF);
            PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Yeni oyuncular gelince burada görünür.", "New players will appear here when they join."), x(150), y(304), readableTextSize(7), size(238), 0xFF9AA3AD);
            drawRoundedRect(g, x(72), y(342), x(228), y(378), size(15), 0xFF232A33);
            PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Geçilenleri tekrar göster", "Show skipped profiles again"), x(150), y(353), readableTextSize(7), size(140), 0xFFFF7B8D);
            return;
        }

        float offset = cardOffsetX;
        int enterShift = cardEnterShift();
        int left = Math.round(24 + offset);
        int right = left + 252;
        int top = 104 + enterShift;
        int bottom = 456 + enterShift;

        // A subtle second card makes the stack feel alive while the active card moves.
        // Everything card-related is clipped to the phone display, so a swipe can never
        // paint over the bezel or outside the handset.
        g.enableScissor(x(14), y(96), x(286), y(462));
        drawRoundedRect(g, x(29), y(109), x(271), y(458), size(19), 0xFF10151C);
        drawRoundedRect(g, x(left), y(top), x(right), y(bottom), size(20), 0xFF161B22);

        String photo = PhoneSocialClientData.socialPath(candidate.tinderPhotoRef);
        boolean imageDrawn = ZPhoneSocialRenderUtil.drawImage(g, minecraft, photo, x(left + 6), y(top + 6), size(240), size(248));
        if (!imageDrawn) {
            drawRoundedRect(g, x(left + 6), y(top + 6), x(right - 6), y(top + 254), size(17), 0xFF222B34);
            fillCircle(g, x(left + 126), y(top + 126), size(83), 0xFF313B46);
            PhonePlayerHeadRenderer.draw(g, candidate.playerUuid, x(left + 53), y(top + 53), size(146));
        }
        // Dark readable caption band.
        g.fill(x(left + 6), y(top + 226), x(right - 6), y(top + 345), 0xBD080B10);
        PhoneModernText.drawColoredFitted(g, safeName(candidate) + ", " + safeAge(candidate), x(left + 18), y(top + 238), readableTextSize(18), size(216), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, genderLabel(candidate.tinderGender), x(left + 19), y(top + 270), readableTextSize(7), 0xFFB9C0C8);
        ZPhoneSocialRenderUtil.drawWrapped(g, safeBio(candidate), x(left + 19), y(top + 293), readableTextSize(7), 0xFFE1E5E9, 35, 3, readableTextSize(13));

        if (offset < -18) {
            drawStamp(g, left + 162, top + 27, PhoneSettingsStore.text("GEÇ", "SKIP"), 0xFF56B7FF);
        } else if (offset > 18) {
            drawStamp(g, left + 26, top + 27, PhoneSettingsStore.text("BEĞEN", "LIKE"), 0xFF3BD39A);
        }
        g.disableScissor();

        drawActionButton(g, 82, 495, 23, 0xFF20262E, 0xFFFF5B71, "×", mouseX, mouseY);
        drawActionButton(g, 137, 493, 18, 0xFF20262E, 0xFF65B7FF, "↻", mouseX, mouseY);
        drawActionButton(g, 192, 495, 23, 0xFF20262E, 0xFF41D49A, "♡", mouseX, mouseY);
        drawActionButton(g, 248, 495, 18, 0xFF20262E, 0xFFFF8793, "⊘", mouseX, mouseY);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Kaydır • beğen • engelle", "Swipe • like • block"), x(150), y(529), readableTextSize(6), size(246), 0xFF69737E);
    }

    private void drawActionButton(GuiGraphics g, int cx, int cy, int radius, int bg, int fg, String icon, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, cx - radius, cy - radius, cx + radius, cy + radius);
        fillCircle(g, x(cx), y(cy), size(radius), hover ? 0xFF2D343D : bg);
        PhoneModernText.drawCenteredColored(g, icon, x(cx), y(cy - ("×".equals(icon) ? 13 : 11)), readableTextSize("↻".equals(icon) ? 15 : 21), fg);
    }

    private void drawStamp(GuiGraphics g, int left, int top, String text, int color) {
        drawRoundedRect(g, x(left), y(top), x(left + 64), y(top + 29), size(5), 0xCC0C1117);
        PhoneModernText.drawCenteredColoredFitted(g, text, x(left + 32), y(top + 8), readableTextSize(9), size(54), color);
    }

    private void drawMatches(GuiGraphics g, int mouseX, int mouseY) {
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Mesajlar", "Messages"), x(22), y(96), readableTextSize(17), 0xFFFFFFFF);
        List<PhoneSocialStateData.ProfileEntry> matches = sortedMatches();
        if (matches.isEmpty()) {
            fillCircle(g, x(150), y(260), size(42), 0xFF1B222B);
            PhoneModernText.drawCenteredColored(g, "♡", x(150), y(234), readableTextSize(39), 0xFFFF5C72);
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Henüz eşleşme yok", "No matches yet"), x(150), y(322), readableTextSize(12), 0xFFFFFFFF);
            PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Keşfet'te sağa kaydırmaya devam et.", "Keep swiping right in Discover."), x(150), y(348), readableTextSize(7), size(242), 0xFF7F8995);
            return;
        }

        PhoneModernText.drawColored(g, PhoneSettingsStore.text("YENİ EŞLEŞMELER", "NEW MATCHES"), x(22), y(126), readableTextSize(7), 0xFFFF7082);
        List<PhoneSocialStateData.ProfileEntry> fresh = newMatches();
        if (fresh.isEmpty()) {
            PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Yeni eşleşmen olduğunda burada görünür.", "New matches will appear here."), x(24), y(163), readableTextSize(7), size(250), 0xFF747E89);
        } else {
            int maxFreshScroll = Math.max(0, fresh.size() - 4);
            freshMatchScroll = Math.max(0, Math.min(freshMatchScroll, maxFreshScroll));
            int count = Math.min(4, fresh.size() - freshMatchScroll);
            for (int i = 0; i < count; i++) {
                int left = 22 + i * 66;
                PhoneSocialStateData.ProfileEntry profile = fresh.get(freshMatchScroll + i);
                boolean hover = inside(mouseX, mouseY, left - 3, 140, left + 51, 207);
                drawRoundedRect(g, x(left - 2), y(140), x(left + 50), y(192), size(18), hover ? 0xFFFF7183 : 0xFFFF4D67);
                String photo = PhoneSocialClientData.socialPath(profile.tinderPhotoRef);
                ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, profile.playerUuid, photo, x(left + 2), y(144), size(44));
                PhoneModernText.drawCenteredColored(g, trim(safeName(profile), 9), x(left + 24), y(197), readableTextSize(6), 0xFFE7EBEF);
            }
            if (freshMatchScroll > 0) PhoneModernText.drawCenteredColored(g, "‹", x(12), y(158), readableTextSize(14), 0xFFFF8292);
            if (freshMatchScroll < maxFreshScroll) PhoneModernText.drawCenteredColored(g, "›", x(288), y(158), readableTextSize(14), 0xFFFF8292);
        }

        g.fill(x(18), y(216), x(282), y(217), 0x24FFFFFF);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("SOHBETLER", "CHATS"), x(22), y(226), readableTextSize(7), 0xFFFF7082);
        List<PhoneSocialStateData.ProfileEntry> conversations = activeConversations();
        int maxScroll = Math.max(0, conversations.size() - 4);
        conversationScroll = Math.max(0, Math.min(conversationScroll, maxScroll));
        if (conversations.isEmpty()) {
            PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Henüz bir sohbet başlatmadın.", "You haven't started a chat yet."), x(150), y(307), readableTextSize(8), size(242), 0xFF929BA5);
            PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Yukarıdaki bir eşleşmeye dokun.", "Tap a match above."), x(150), y(330), readableTextSize(7), size(242), 0xFF69737E);
            return;
        }

        int count = Math.min(4, conversations.size() - conversationScroll);
        for (int i = 0; i < count; i++) {
            int top = 246 + i * 66;
            PhoneSocialStateData.ProfileEntry profile = conversations.get(conversationScroll + i);
            boolean hover = inside(mouseX, mouseY, 16, top, 284, top + 58);
            drawRoundedRect(g, x(16), y(top), x(284), y(top + 58), size(11), hover ? 0xFF1D242D : 0xFF141A21);
            String photo = PhoneSocialClientData.socialPath(profile.tinderPhotoRef);
            ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, profile.playerUuid, photo, x(26), y(top + 8), size(42));
            PhoneModernText.drawColored(g, trim(safeName(profile), 18), x(80), y(top + 8), readableTextSize(9), 0xFFFFFFFF);
            PhoneStateData.MessageEntry lastMessage = PhoneClientData.lastTinderMessage(profile.playerUuid);
            PhoneModernText.drawColored(g, trim(PhoneClientData.messagePreview(lastMessage), 27), x(80), y(top + 30), readableTextSize(6), 0xFFB7BFC8);
            String time = lastMessage == null ? "" : formatMessageTime(lastMessage.timestamp);
            int timeWidth = PhoneModernText.width(time, readableTextSize(5));
            PhoneModernText.drawColored(g, time, x(270) - timeWidth, y(top + 9), readableTextSize(5), 0xFF747E89);
            int unread = PhoneClientData.unreadTinderCount(profile.playerUuid);
            if (unread > 0) {
                fillCircle(g, x(265), y(top + 39), size(9), 0xFFFF4D67);
                PhoneModernText.drawCenteredColored(g, unread > 9 ? "9+" : Integer.toString(unread), x(265), y(top + 34), readableTextSize(5), 0xFFFFFFFF);
            } else {
                PhoneModernText.drawCenteredColored(g, "›", x(266), y(top + 29), readableTextSize(11), 0xFF66717D);
            }
        }
        if (maxScroll > 0) {
            int thumbHeight = Math.max(30, Math.round(256.0F * (4.0F / conversations.size())));
            int thumbTravel = 256 - thumbHeight;
            int thumbTop = 246 + Math.round(thumbTravel * (conversationScroll / (float) maxScroll));
            drawRoundedRect(g, x(289), y(thumbTop), x(291), y(thumbTop + thumbHeight), size(1), 0x80FF7183);
        }
    }

    private void drawProfile(GuiGraphics g, int mouseX, int mouseY) {
        PhoneSocialStateData.ProfileEntry self = PhoneSocialClientData.profile(PhoneClientData.selfUuid());
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Profilim", "My Profile"), x(22), y(100), readableTextSize(17), 0xFFFFFFFF);
        if (self == null) {
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Profil senkronize ediliyor...", "Syncing profile..."), x(150), y(285), readableTextSize(9), 0xFF89939E);
            return;
        }
        String photo = PhoneSocialClientData.socialPath(self.tinderPhotoRef);
        fillCircle(g, x(150), y(196), size(70), 0xFF222A33);
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, self.playerUuid, photo, x(88), y(134), size(124));
        PhoneModernText.drawCenteredColoredFitted(g, safeName(self) + ", " + safeAge(self), x(150), y(278), readableTextSize(16), size(228), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, genderLabel(self.tinderGender), x(150), y(305), readableTextSize(7), 0xFF9AA4AE);
        ZPhoneSocialRenderUtil.drawWrapped(g, safeBio(self), x(49), y(329), readableTextSize(7), 0xFFB8C0C9, 35, 2, readableTextSize(13));

        drawRoundedRect(g, x(42), y(382), x(258), y(421), size(15), 0xFFFF4D67);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Profili Düzenle", "Edit Profile"), x(150), y(394), readableTextSize(8), 0xFFFFFFFF);

        drawRoundedRect(g, x(42), y(431), x(258), y(470), size(15), 0xFF1A2129);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Tercihler", "Preferences"), x(61), y(443), readableTextSize(8), 0xFFFFFFFF);
        PhoneModernText.drawColoredFitted(g, preferenceSummary(self), x(126), y(444), readableTextSize(6), size(104), 0xFF8E98A4);
        PhoneModernText.drawColored(g, "›", x(239), y(441), readableTextSize(12), 0xFF8E98A4);

        drawRoundedRect(g, x(42), y(480), x(258), y(521), size(14), 0xFF151C24);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Keşifte görün", "Show in Discover"), x(59), y(493), readableTextSize(8), size(132), 0xFFFFFFFF);
        drawToggle(g, 221, 501, self.tinderEnabled);
        PhoneModernText.drawCenteredColored(g, PhoneSocialClientData.tinderMatches().size() + PhoneSettingsStore.text(" eşleşme", " matches"), x(150), y(531), readableTextSize(6), 0xFF8D97A2);
    }

    private void drawProfileEditor(GuiGraphics g, int mouseX, int mouseY) {
        drawRoundedRect(g, x(12), y(82), x(288), y(544), size(22), 0xFF10151C);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Profili Düzenle", "Edit Profile"), x(28), y(98), readableTextSize(16), size(190), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Bitti", "Done"), x(244), y(103), readableTextSize(8), 0xFFFF667A);

        String selfUuid = PhoneClientData.selfUuid();
        drawRoundedRect(g, x(56), y(136), x(244), y(326), size(18), 0xFF1B222B);
        boolean has = ZPhoneSocialRenderUtil.drawImage(g, minecraft, selectedPhotoPath, x(61), y(141), size(178), size(180));
        if (!has) {
            PhonePlayerHeadRenderer.draw(g, selfUuid, x(87), y(167), size(126));
        }
        drawRoundedRect(g, x(70), y(338), x(146), y(369), size(12), 0xFF252D37);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Galeri", "Gallery"), x(108), y(347), readableTextSize(7), size(66), 0xFFFFFFFF);
        drawRoundedRect(g, x(154), y(338), x(230), y(369), size(12), 0xFF252D37);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Skin kullan", "Use skin"), x(192), y(347), readableTextSize(6), size(66), 0xFFFFFFFF);

        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Hakkında", "About"), x(32), y(391), readableTextSize(7), 0xFF8E98A4);
        drawRoundedRect(g, x(28), y(408), x(272), y(473), size(12), bioFocused ? 0xFF242E39 : 0xFF1A2129);
        ZPhoneSocialRenderUtil.drawWrapped(g, bioDraft.toString(), x(39), y(420), readableTextSize(7), 0xFFF2F4F6, 39, 3, readableTextSize(14));
        if (bioDraft.isEmpty()) PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Kendinden biraz bahset...", "Tell people a little about yourself..."), x(39), y(420), readableTextSize(7), size(220), 0xFF6E7985);
        PhoneModernText.drawColored(g, bioDraft.length() + "/180", x(232), y(478), readableTextSize(6), 0xFF6D7782);

        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Keşifte görün", "Show in Discover"), x(32), y(503), readableTextSize(8), size(172), 0xFFFFFFFF);
        drawToggle(g, 236, 510, discoveryEnabled);
    }

    private void drawPreferences(GuiGraphics g, int mouseX, int mouseY) {
        drawRoundedRect(g, x(12), y(82), x(288), y(536), size(22), 0xFF10151C);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Minder Tercihleri", "Minder Preferences"), x(28), y(98), readableTextSize(16), size(190), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Bitti", "Done"), x(244), y(103), readableTextSize(8), 0xFFFF667A);

        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Profil", "Profile"), x(30), y(137), readableTextSize(7), 0xFF7F8995);
        drawRoundedRect(g, x(28), y(153), x(272), y(199), size(12), 0xFF181F27);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Yaşın", "Your age"), x(42), y(169), readableTextSize(8), 0xFFFFFFFF);
        drawMiniButton(g, 176, 161, 204, 191, "−");
        PhoneModernText.drawCenteredColored(g, Integer.toString(draftAge), x(225), y(169), readableTextSize(8), 0xFFFFFFFF);
        drawMiniButton(g, 246, 161, 274, 191, "+");

        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Cinsiyetin", "Your gender"), x(30), y(216), readableTextSize(7), 0xFF7F8995);
        drawChoice(g, 30, 233, 105, 267, PhoneSettingsStore.text("Erkek", "Male"), "MALE".equals(draftGender));
        drawChoice(g, 112, 233, 187, 267, PhoneSettingsStore.text("Kadın", "Female"), "FEMALE".equals(draftGender));
        drawChoice(g, 194, 233, 269, 267, PhoneSettingsStore.text("Diğer", "Other"), "OTHER".equals(draftGender));

        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Keşif tercihleri", "Discovery preferences"), x(30), y(292), readableTextSize(7), 0xFF7F8995);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Bana göster", "Show me"), x(30), y(316), readableTextSize(8), 0xFFFFFFFF);
        drawChoice(g, 30, 337, 105, 371, PhoneSettingsStore.text("Erkek", "Male"), "MALE".equals(draftShowGender));
        drawChoice(g, 112, 337, 187, 371, PhoneSettingsStore.text("Kadın", "Female"), "FEMALE".equals(draftShowGender));
        drawChoice(g, 194, 337, 269, 371, PhoneSettingsStore.text("Herkes", "Everyone"), "EVERYONE".equals(draftShowGender));

        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Yaş aralığı", "Age range"), x(30), y(395), readableTextSize(8), 0xFFFFFFFF);
        drawRoundedRect(g, x(30), y(414), x(132), y(454), size(11), 0xFF181F27);
        drawMiniButton(g, 34, 420, 60, 448, "−");
        PhoneModernText.drawCenteredColored(g, Integer.toString(draftMinAge), x(81), y(427), readableTextSize(8), 0xFFFFFFFF);
        drawMiniButton(g, 103, 420, 129, 448, "+");
        PhoneModernText.drawCenteredColored(g, "—", x(150), y(427), readableTextSize(8), 0xFF808A95);
        drawRoundedRect(g, x(168), y(414), x(270), y(454), size(11), 0xFF181F27);
        drawMiniButton(g, 171, 420, 197, 448, "−");
        PhoneModernText.drawCenteredColored(g, Integer.toString(draftMaxAge), x(219), y(427), readableTextSize(8), 0xFFFFFFFF);
        drawMiniButton(g, 241, 420, 267, 448, "+");

        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Yaş seçimi 18'den başlar.", "Age selection starts at 18."), x(150), y(474), readableTextSize(7), size(248), 0xFF8C96A1);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Filtreler yalnızca cinsiyet ve yaşa göre çalışır.", "Filters only use gender and age."), x(150), y(494), readableTextSize(6), size(248), 0xFF68737E);
    }

    private void drawChoice(GuiGraphics g, int left, int top, int right, int bottom, String label, boolean active) {
        drawRoundedRect(g, x(left), y(top), x(right), y(bottom), size(11), active ? 0xFFFF5369 : 0xFF202832);
        PhoneModernText.drawCenteredColoredFitted(g, label, x((left + right) / 2), y(top + 11), readableTextSize(7), size(right - left - 10), active ? 0xFFFFFFFF : 0xFFB0B8C1);
    }

    private void drawMiniButton(GuiGraphics g, int left, int top, int right, int bottom, String label) {
        drawRoundedRect(g, x(left), y(top), x(right), y(bottom), size(9), 0xFF29323D);
        PhoneModernText.drawCenteredColored(g, label, x((left + right) / 2), y(top + 7), readableTextSize(9), 0xFFFFFFFF);
    }

    private void drawGallery(GuiGraphics g, int mouseX, int mouseY) {
        drawRoundedRect(g, x(18), y(103), x(282), y(523), size(20), 0xFF11171E);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Profil fotoğrafı seç", "Choose profile photo"), x(31), y(119), readableTextSize(14), size(205), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, "×", x(258), y(115), readableTextSize(17), 0xFF9AA4AE);
        List<Path> photos = PhoneGalleryStore.photos(minecraft);
        int maxOffset = Math.max(0, photos.size() - 12);
        galleryOffset = Math.min(galleryOffset, maxOffset);
        int count = Math.min(12, Math.max(0, photos.size() - galleryOffset));
        if (count == 0) {
            PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Galeride fotoğraf yok", "No photos in Gallery"), x(150), y(286), readableTextSize(10), size(238), 0xFF9AA4AE);
            PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Kamera ile bir fotoğraf çekebilirsin.", "You can take a photo with the Camera."), x(150), y(310), readableTextSize(7), size(238), 0xFF69747F);
            return;
        }
        for (int i = 0; i < count; i++) {
            int column = i % 3;
            int row = i / 3;
            int left = 31 + column * 82;
            int top = 158 + row * 79;
            Path photo = photos.get(galleryOffset + i);
            boolean selected = photo.toAbsolutePath().normalize().toString().equals(selectedPhotoPath);
            drawRoundedRect(g, x(left - 2), y(top - 2), x(left + 72), y(top + 68), size(8), selected ? 0xFFFF5369 : 0xFF27303A);
            var texture = PhoneGalleryStore.textureFor(minecraft, photo);
            if (texture != null) {
                g.blit(texture.location(), x(left), y(top), size(68), size(64),
                        0, 0, texture.width(), texture.height(), texture.width(), texture.height());
            }
        }
    }

    private void drawMatchPopup(GuiGraphics g) {
        g.fill(x(0), y(40), x(300), y(594), 0xD9080A0F);
        drawFlame(g, 150, 168, 38, 0xFFFF4D67);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("EŞLEŞTİNİZ!", "IT'S A MATCH!"), x(150), y(231), readableTextSize(22), 0xFFFF6175);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Sen ve ", "You and ") + safeName(matchPopup) + PhoneSettingsStore.text(" birbirinizi beğendiniz.", " liked each other."), x(150), y(273), readableTextSize(7), size(236), 0xFFE5E8EB);
        String photo = PhoneSocialClientData.socialPath(matchPopup.tinderPhotoRef);
        fillCircle(g, x(150), y(350), size(56), 0xFF222A33);
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, matchPopup.playerUuid, photo, x(100), y(300), size(100));
        drawRoundedRect(g, x(48), y(428), x(252), y(469), size(15), 0xFFFF4D67);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Mesaj Gönder", "Send Message"), x(150), y(441), readableTextSize(8), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Kaydırmaya devam et", "Keep swiping"), x(116), y(493), readableTextSize(7), size(146), 0xFFB6BEC7);
        drawRoundedRect(g, x(205), y(479), x(263), y(510), size(10), 0xFF3A171D);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Engelle", "Block"), x(234), y(488), readableTextSize(5), 0xFFFF8793);
    }

    private void drawToggle(GuiGraphics g, int cx, int cy, boolean enabled) {
        drawRoundedRect(g, x(cx - 23), y(cy - 11), x(cx + 23), y(cy + 11), size(11), enabled ? 0xFF37D37F : 0xFF39414A);
        fillCircle(g, x(cx + (enabled ? 12 : -12)), y(cy), size(9), 0xFFFFFFFF);
    }

    private void drawFlame(GuiGraphics g, int cx, int cy, int scale, int color) {
        // Small vector flame/heart hybrid, avoiding a texture dependency.
        int s = Math.max(2, scale / 8);
        fillCircle(g, x(cx), y(cy + scale / 6), size(scale / 2), color);
        fillCircle(g, x(cx - scale / 4), y(cy), size(scale / 3), color);
        fillCircle(g, x(cx + scale / 4), y(cy), size(scale / 3), color);
        g.fill(x(cx - s * 2), y(cy - scale / 2), x(cx + s), y(cy + scale / 3), color);
        fillCircle(g, x(cx + scale / 5), y(cy - scale / 3), size(Math.max(2, scale / 4)), color);
    }

    private PhoneSocialStateData.ProfileEntry currentCandidate() {
        if (animatedCandidate != null) return animatedCandidate;
        List<PhoneSocialStateData.ProfileEntry> candidates = new ArrayList<>(PhoneSocialClientData.tinderCandidates());
        candidates.removeIf(profile -> locallySkipped.contains(profile.playerUuid));
        if (candidates.isEmpty()) return null;
        int seed = PhoneClientData.selfUuid() == null ? 0 : PhoneClientData.selfUuid().hashCode();
        return candidates.get(Math.floorMod(seed, candidates.size()));
    }

    private void swipeCurrent(boolean like) {
        startSwipeAnimation(like);
    }

    private void startSwipeAnimation(boolean like) {
        if (cardAnimation != CardAnimation.NONE) return;
        PhoneSocialStateData.ProfileEntry candidate = currentCandidate();
        if (candidate == null) return;
        animatedCandidate = candidate;
        draggingCard = false;
        cardAnimationFromX = cardOffsetX;
        cardAnimationStartedAt = System.currentTimeMillis();
        cardAnimation = like ? CardAnimation.SWIPE_RIGHT : CardAnimation.SWIPE_LEFT;
    }

    private void startSnapBackAnimation() {
        if (cardAnimation != CardAnimation.NONE) return;
        if (Math.abs(cardOffsetX) < 0.5F) { cardOffsetX = 0.0F; return; }
        animatedCandidate = currentCandidate();
        draggingCard = false;
        cardAnimationFromX = cardOffsetX;
        cardAnimationStartedAt = System.currentTimeMillis();
        cardAnimation = CardAnimation.SNAP_BACK;
    }

    private void updateCardAnimation() {
        if (cardAnimation == CardAnimation.NONE) return;
        long now = System.currentTimeMillis();
        long duration = cardAnimation == CardAnimation.SNAP_BACK ? CARD_SNAP_MS : CARD_SWIPE_MS;
        float progress = Math.max(0.0F, Math.min(1.0F, (now - cardAnimationStartedAt) / (float) duration));
        float eased = easeOutCubic(progress);
        float target = switch (cardAnimation) {
            case SWIPE_LEFT -> -310.0F;
            case SWIPE_RIGHT -> 310.0F;
            default -> 0.0F;
        };
        cardOffsetX = cardAnimationFromX + (target - cardAnimationFromX) * eased;
        if (progress < 1.0F) return;

        CardAnimation finished = cardAnimation;
        PhoneSocialStateData.ProfileEntry finishedCandidate = animatedCandidate;
        cardAnimation = CardAnimation.NONE;
        cardOffsetX = 0.0F;
        animatedCandidate = null;
        if ((finished == CardAnimation.SWIPE_LEFT || finished == CardAnimation.SWIPE_RIGHT) && finishedCandidate != null) {
            locallySkipped.add(finishedCandidate.playerUuid);
            PhoneSocialClientData.tinderSwipe(finishedCandidate.playerUuid, finished == CardAnimation.SWIPE_RIGHT);
            cardAppearStartedAt = now;
        }
    }

    private int cardEnterShift() {
        long elapsed = Math.max(0L, System.currentTimeMillis() - cardAppearStartedAt);
        if (elapsed >= 180L) return 0;
        float progress = easeOutCubic(elapsed / 180.0F);
        return Math.round((1.0F - progress) * 12.0F);
    }

    private static float easeOutCubic(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        float inverse = 1.0F - clamped;
        return 1.0F - inverse * inverse * inverse;
    }

    private void openProfileEditor() {
        PhoneSocialStateData.ProfileEntry self = PhoneSocialClientData.profile(PhoneClientData.selfUuid());
        if (self == null) return;
        editingProfile = true;
        galleryOpen = false;
        bioFocused = false;
        bioDraft.setLength(0);
        bioDraft.append(safeBio(self));
        selectedPhotoPath = PhoneSocialClientData.socialPath(self.tinderPhotoRef);
        discoveryEnabled = self.tinderEnabled;
    }

    private void saveProfileEditor() {
        PhoneSocialClientData.updateTinderProfile(bioDraft.toString(), selectedPhotoPath, discoveryEnabled);
        editingProfile = false;
        galleryOpen = false;
        bioFocused = false;
    }

    private void openPreferences() {
        PhoneSocialStateData.ProfileEntry self = PhoneSocialClientData.profile(PhoneClientData.selfUuid());
        if (self == null) return;
        draftAge = clampAge(self.tinderAge);
        draftGender = normalizeGender(self.tinderGender);
        draftShowGender = normalizeShowGender(self.tinderShowGender);
        draftMinAge = clampAge(self.tinderMinAge);
        draftMaxAge = clampAge(self.tinderMaxAge);
        if (draftMinAge > draftMaxAge) draftMaxAge = draftMinAge;
        preferencesOpen = true;
        editingProfile = false;
        galleryOpen = false;
        bioFocused = false;
    }

    private void savePreferences() {
        draftAge = clampAge(draftAge);
        draftMinAge = clampAge(draftMinAge);
        draftMaxAge = clampAge(draftMaxAge);
        if (draftMinAge > draftMaxAge) draftMaxAge = draftMinAge;
        PhoneSocialClientData.updateTinderSettings(draftAge, draftGender, draftShowGender, draftMinAge, draftMaxAge);
        locallySkipped.clear();
        preferencesOpen = false;
    }

    private List<PhoneSocialStateData.ProfileEntry> sortedMatches() {
        List<PhoneSocialStateData.ProfileEntry> matches = new ArrayList<>(PhoneSocialClientData.tinderMatches());
        matches.sort((a, b) -> {
            long timeA = PhoneClientData.lastTinderMessageTimestamp(a.playerUuid);
            long timeB = PhoneClientData.lastTinderMessageTimestamp(b.playerUuid);
            if (timeA != timeB) return Long.compare(timeB, timeA);
            return safeName(a).compareToIgnoreCase(safeName(b));
        });
        return matches;
    }

    private List<PhoneSocialStateData.ProfileEntry> newMatches() {
        List<PhoneSocialStateData.ProfileEntry> result = new ArrayList<>();
        for (PhoneSocialStateData.ProfileEntry profile : sortedMatches()) {
            if (PhoneClientData.lastTinderMessage(profile.playerUuid) == null) result.add(profile);
        }
        return result;
    }

    private List<PhoneSocialStateData.ProfileEntry> activeConversations() {
        List<PhoneSocialStateData.ProfileEntry> result = new ArrayList<>();
        for (PhoneSocialStateData.ProfileEntry profile : sortedMatches()) {
            if (PhoneClientData.lastTinderMessage(profile.playerUuid) != null) result.add(profile);
        }
        return result;
    }

    private static String formatMessageTime(long timestamp) {
        if (timestamp <= 0L) return "";
        try {
            return CHAT_TIME.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private void openMatchMessage(PhoneSocialStateData.ProfileEntry profile) {
        if (profile == null || minecraft == null) return;
        PhoneNavigationState.open(minecraft, this, new ZPhoneTinderChatScreen(profile.playerUuid, safeName(profile)));
    }


    private int readableTextSize(float designSize) {
        float factor = designSize <= 6.0F ? 1.18F : designSize <= 8.0F ? 1.10F : designSize <= 10.0F ? 1.05F : 1.0F;
        return Math.max(1, Math.round(size(designSize) * factor));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (matchPopup != null) {
            if (inside(mouseX, mouseY, 205, 476, 266, 514)) {
                PhoneSocialClientData.blockSocialUser(matchPopup.playerUuid);
                locallySkipped.add(matchPopup.playerUuid);
                matchPopup = null;
                return true;
            }
            if (inside(mouseX, mouseY, 48, 428, 252, 469)) {
                PhoneSocialStateData.ProfileEntry target = matchPopup;
                matchPopup = null;
                openMatchMessage(target);
                return true;
            }
            if (inside(mouseX, mouseY, 70, 476, 202, 514)) { matchPopup = null; return true; }
            return true;
        }
        if (preferencesOpen) {
            if (inside(mouseX, mouseY, 225, 90, 280, 128)) { savePreferences(); return true; }
            if (inside(mouseX, mouseY, 176, 161, 204, 191)) { draftAge = clampAge(draftAge - 1); return true; }
            if (inside(mouseX, mouseY, 246, 161, 274, 191)) { draftAge = clampAge(draftAge + 1); return true; }
            if (inside(mouseX, mouseY, 30, 233, 105, 267)) { draftGender = "MALE"; return true; }
            if (inside(mouseX, mouseY, 112, 233, 187, 267)) { draftGender = "FEMALE"; return true; }
            if (inside(mouseX, mouseY, 194, 233, 269, 267)) { draftGender = "OTHER"; return true; }
            if (inside(mouseX, mouseY, 30, 337, 105, 371)) { draftShowGender = "MALE"; return true; }
            if (inside(mouseX, mouseY, 112, 337, 187, 371)) { draftShowGender = "FEMALE"; return true; }
            if (inside(mouseX, mouseY, 194, 337, 269, 371)) { draftShowGender = "EVERYONE"; return true; }
            if (inside(mouseX, mouseY, 34, 420, 60, 448)) { draftMinAge = clampAge(draftMinAge - 1); return true; }
            if (inside(mouseX, mouseY, 103, 420, 129, 448)) {
                draftMinAge = clampAge(draftMinAge + 1);
                if (draftMinAge > draftMaxAge) draftMaxAge = draftMinAge;
                return true;
            }
            if (inside(mouseX, mouseY, 171, 420, 197, 448)) {
                draftMaxAge = clampAge(draftMaxAge - 1);
                if (draftMaxAge < draftMinAge) draftMinAge = draftMaxAge;
                return true;
            }
            if (inside(mouseX, mouseY, 241, 420, 267, 448)) { draftMaxAge = clampAge(draftMaxAge + 1); return true; }
            return true;
        }
        if (galleryOpen) {
            if (inside(mouseX, mouseY, 246, 108, 277, 145)) { galleryOpen = false; return true; }
            List<Path> photos = PhoneGalleryStore.photos(minecraft);
            int count = Math.min(12, Math.max(0, photos.size() - galleryOffset));
            for (int i = 0; i < count; i++) {
                int column = i % 3;
                int row = i / 3;
                int left = 31 + column * 82;
                int top = 158 + row * 79;
                if (inside(mouseX, mouseY, left - 2, top - 2, left + 72, top + 68)) {
                    selectedPhotoPath = photos.get(galleryOffset + i).toAbsolutePath().normalize().toString();
                    galleryOpen = false;
                    return true;
                }
            }
            return true;
        }
        if (editingProfile) {
            if (inside(mouseX, mouseY, 225, 90, 280, 128)) { saveProfileEditor(); return true; }
            if (inside(mouseX, mouseY, 70, 338, 146, 369)) { galleryOpen = true; bioFocused = false; return true; }
            if (inside(mouseX, mouseY, 154, 338, 230, 369)) { selectedPhotoPath = ""; return true; }
            if (inside(mouseX, mouseY, 28, 408, 272, 473)) { bioFocused = true; return true; }
            if (inside(mouseX, mouseY, 205, 492, 267, 528)) { discoveryEnabled = !discoveryEnabled; return true; }
            bioFocused = false;
            return true;
        }
        if (handleDynamicIslandClick(mouseX, mouseY)) return true;
        if (inside(mouseX, mouseY, 20, 548, 100, 594)) { tab = Tab.DISCOVER; return true; }
        if (inside(mouseX, mouseY, 110, 548, 191, 594)) { tab = Tab.MATCHES; conversationScroll = 0; freshMatchScroll = 0; PhoneSocialClientData.markNotificationsRead("TINDER"); return true; }
        if (inside(mouseX, mouseY, 200, 548, 282, 594)) { tab = Tab.PROFILE; return true; }

        if (tab == Tab.DISCOVER) {
            if (cardAnimation != CardAnimation.NONE) return true;
            if (inside(mouseX, mouseY, 24, 104, 276, 456) && currentCandidate() != null) {
                draggingCard = true;
                dragStartX = mouseX;
                return true;
            }
            if (inside(mouseX, mouseY, 57, 470, 106, 520)) { swipeCurrent(false); return true; }
            if (inside(mouseX, mouseY, 117, 472, 157, 514)) {
                locallySkipped.clear();
                PhoneSocialClientData.resetTinderPasses();
                return true;
            }
            if (inside(mouseX, mouseY, 168, 470, 216, 520)) { swipeCurrent(true); return true; }
            if (inside(mouseX, mouseY, 228, 476, 269, 516)) {
                PhoneSocialStateData.ProfileEntry target = currentCandidate();
                if (target != null) {
                    PhoneSocialClientData.blockSocialUser(target.playerUuid);
                    locallySkipped.add(target.playerUuid);
                    cardAppearStartedAt = System.currentTimeMillis();
                }
                return true;
            }
            if (currentCandidate() == null && inside(mouseX, mouseY, 72, 342, 228, 378)) {
                locallySkipped.clear();
                PhoneSocialClientData.resetTinderPasses();
                return true;
            }
        } else if (tab == Tab.MATCHES) {
            List<PhoneSocialStateData.ProfileEntry> fresh = newMatches();
            int maxFreshScroll = Math.max(0, fresh.size() - 4);
            freshMatchScroll = Math.max(0, Math.min(freshMatchScroll, maxFreshScroll));
            if (freshMatchScroll > 0 && inside(mouseX, mouseY, 5, 140, 21, 198)) {
                freshMatchScroll--;
                return true;
            }
            if (freshMatchScroll < maxFreshScroll && inside(mouseX, mouseY, 279, 140, 295, 198)) {
                freshMatchScroll++;
                return true;
            }
            int freshCount = Math.min(4, fresh.size() - freshMatchScroll);
            for (int i = 0; i < freshCount; i++) {
                int left = 22 + i * 66;
                if (inside(mouseX, mouseY, left - 3, 140, left + 51, 207)) {
                    openMatchMessage(fresh.get(freshMatchScroll + i));
                    return true;
                }
            }
            List<PhoneSocialStateData.ProfileEntry> conversations = activeConversations();
            conversationScroll = Math.max(0, Math.min(conversationScroll, Math.max(0, conversations.size() - 4)));
            int count = Math.min(4, conversations.size() - conversationScroll);
            for (int i = 0; i < count; i++) {
                int top = 246 + i * 66;
                if (inside(mouseX, mouseY, 16, top, 284, top + 58)) {
                    openMatchMessage(conversations.get(conversationScroll + i));
                    return true;
                }
            }
        } else if (tab == Tab.PROFILE) {
            if (inside(mouseX, mouseY, 42, 382, 258, 421)) { openProfileEditor(); return true; }
            if (inside(mouseX, mouseY, 42, 431, 258, 470)) { openPreferences(); return true; }
            if (inside(mouseX, mouseY, 194, 476, 266, 525)) {
                PhoneSocialStateData.ProfileEntry self = PhoneSocialClientData.profile(PhoneClientData.selfUuid());
                if (self != null) {
                    PhoneSocialClientData.updateTinderProfile(safeBio(self), PhoneSocialClientData.socialPath(self.tinderPhotoRef), !self.tinderEnabled);
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingCard && cardAnimation == CardAnimation.NONE && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !editingProfile && !galleryOpen && matchPopup == null) {
            float designOffset = (float) ((mouseX - dragStartX) / Math.max(0.001F, phone().scale()));
            cardOffsetX = Math.max(-92.0F, Math.min(92.0F, designOffset));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingCard) {
            draggingCard = false;
            if (cardOffsetX <= -43.0F) startSwipeAnimation(false);
            else if (cardOffsetX >= 43.0F) startSwipeAnimation(true);
            else startSnapBackAnimation();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (galleryOpen && delta != 0.0D) {
            int max = Math.max(0, PhoneGalleryStore.photos(minecraft).size() - 12);
            galleryOffset = delta < 0 ? Math.min(max, galleryOffset + 3) : Math.max(0, galleryOffset - 3);
            return true;
        }
        if (editingProfile || preferencesOpen || matchPopup != null) return true;
        if (tab == Tab.MATCHES && inside(mouseX, mouseY, 14, 218, 286, 520) && delta != 0.0D) {
            int max = Math.max(0, activeConversations().size() - 4);
            conversationScroll = delta < 0.0D ? Math.min(max, conversationScroll + 1) : Math.max(0, conversationScroll - 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (matchPopup != null || preferencesOpen || galleryOpen) return true;
        if (editingProfile) {
            if (bioFocused && SharedConstants.isAllowedChatCharacter(codePoint) && bioDraft.length() < 180) {
                bioDraft.append(codePoint);
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (preferencesOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) preferencesOpen = false;
            return true;
        }
        if (galleryOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) galleryOpen = false;
            return true;
        }
        if (editingProfile) {
            if (bioFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!bioDraft.isEmpty()) bioDraft.deleteCharAt(bioDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editingProfile = false;
                galleryOpen = false;
                bioFocused = false;
            }
            return true;
        }
        if (matchPopup != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) matchPopup = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static int clampAge(int age) {
        return Math.max(18, Math.min(99, age <= 0 ? 18 : age));
    }

    private static int safeAge(PhoneSocialStateData.ProfileEntry profile) {
        return profile == null ? 18 : clampAge(profile.tinderAge);
    }

    private static String normalizeGender(String value) {
        String safe = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (safe) {
            case "MALE", "FEMALE", "OTHER" -> safe;
            default -> "UNSPECIFIED";
        };
    }

    private static String normalizeShowGender(String value) {
        String safe = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (safe) {
            case "MALE", "FEMALE" -> safe;
            default -> "EVERYONE";
        };
    }

    private static String genderLabel(String gender) {
        return switch (normalizeGender(gender)) {
            case "MALE" -> PhoneSettingsStore.text("Erkek", "Male");
            case "FEMALE" -> PhoneSettingsStore.text("Kadın", "Female");
            case "OTHER" -> PhoneSettingsStore.text("Diğer", "Other");
            default -> PhoneSettingsStore.text("Cinsiyet belirtilmedi", "Gender not specified");
        };
    }

    private static String showGenderLabel(String showGender) {
        return switch (normalizeShowGender(showGender)) {
            case "MALE" -> PhoneSettingsStore.text("Erkek", "Male");
            case "FEMALE" -> PhoneSettingsStore.text("Kadın", "Female");
            default -> PhoneSettingsStore.text("Herkes", "Everyone");
        };
    }

    private static String preferenceSummary(PhoneSocialStateData.ProfileEntry profile) {
        if (profile == null) return PhoneSettingsStore.text("Herkes • 18-99", "Everyone • 18-99");
        int min = clampAge(profile.tinderMinAge);
        int max = clampAge(profile.tinderMaxAge);
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        return showGenderLabel(profile.tinderShowGender) + " • " + min + "-" + max;
    }

    private static String safeName(PhoneSocialStateData.ProfileEntry profile) {
        return profile == null || profile.playerName == null || profile.playerName.isBlank()
                ? PhoneSettingsStore.text("Oyuncu", "Player")
                : profile.playerName;
    }

    private static String safeBio(PhoneSocialStateData.ProfileEntry profile) {
        if (profile == null || profile.tinderBio == null || profile.tinderBio.isBlank()) {
            return PhoneSettingsStore.text("Minecraft oyuncusu • Yeni insanlarla tanışmaya açık.", "Minecraft player • Open to meeting new people.");
        }
        return profile.tinderBio;
    }

    private static String trim(String value, int max) {
        String safe = value == null ? "" : value.replace('\n', ' ').trim();
        return safe.length() <= max ? safe : safe.substring(0, Math.max(1, max - 1)) + "…";
    }
}

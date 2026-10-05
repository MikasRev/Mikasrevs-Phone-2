package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;

import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore;
import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore.PostEntry;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhoneRoleBadgeRenderer;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import java.util.List;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Full Lumi profile page for any multiplayer social profile. */
public final class ZPhoneLumiProfileScreen extends AbstractZPhoneScreen {
    private final String targetUuid;
    private boolean savedTab;
    private int profileScroll;

    public ZPhoneLumiProfileScreen(String targetUuid) {
        super(Component.literal(PhoneSettingsStore.text("Lumi Profili", "Lumi Profile")));
        this.targetUuid = targetUuid == null ? "" : targetUuid;
        PhoneSocialClientData.requestSync();
        PhoneSocialClientData.requestProfileAssets(this.targetUuid);
        PhoneClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawFrame(g);
        drawWallpaper(g);
        drawRoundedRect(g, x(8), y(40), x(292), y(602), size(22), 0xFF050608);
        drawStatusBar(g);
        drawBackButton(g);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Profil", "Profile"), x(150), y(58), size(13), 0xFFFFFFFF);
        g.fill(x(17), y(86), x(283), y(87), 0x28FFFFFF);
        drawProfile(g);
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void drawProfile(GuiGraphics g) {
        PhoneSocialStateData.ProfileEntry profile = PhoneSocialClientData.profile(targetUuid);
        String name = profile == null || profile.playerName == null || profile.playerName.isBlank() ? PhoneSettingsStore.text("Oyuncu", "Player") : profile.playerName;
        String photo = targetUuid.equals(PhoneClientData.selfUuid()) ? PhoneBlockGramStore.profilePhotoPath() : PhoneSocialClientData.blockGramPhoto(targetUuid);
        String bio = profile == null ? "" : profile.blockGramBio;
        ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, targetUuid, photo, x(20), y(91), size(64));
        PhoneRoleBadgeRenderer.drawNameAndBadge(g, name, PhoneSocialClientData.roleBadge(targetUuid),
                x(94), y(95), size(13), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, "@" + handle(name), x(94), y(116), size(7), 0xFF858F9C);

        int posts = PhoneBlockGramStore.byAuthor(targetUuid).size();
        int followers = PhoneSocialClientData.followerCount("BLOCKGRAM", targetUuid);
        int following = PhoneSocialClientData.followingCount("BLOCKGRAM", targetUuid);
        stat(g, 106, 146, posts, PhoneSettingsStore.text("gönderi", "posts"));
        stat(g, 173, 146, followers, PhoneSettingsStore.text("takipçi", "followers"));
        stat(g, 241, 146, following, PhoneSettingsStore.text("takip", "following"));
        ZPhoneSocialRenderUtil.drawWrapped(g, bio == null || bio.isBlank() ? PhoneSettingsStore.text("Lumi kullanıcısı", "Lumi user") : PhoneSettingsStore.localizeNetworkText(bio), x(23), y(184), size(7), 0xFFE5E9EF, 48, 3, size(10));

        boolean self = targetUuid.equals(PhoneClientData.selfUuid());
        if (!self) {
            boolean blocked = PhoneSocialClientData.isSocialBlocked(targetUuid);
            String state = PhoneSocialClientData.followState("BLOCKGRAM", PhoneClientData.selfUuid(), targetUuid);
            int followColor = blocked ? 0xFF17181C : "ACCEPTED".equals(state) ? 0xFF242529 : "PENDING".equals(state) ? 0xFF242529 : 0xFF168AFF;
            drawRoundedRect(g, x(22), y(224), x(134), y(256), size(7), followColor);
            PhoneModernText.drawCenteredColoredFitted(g, blocked ? PhoneSettingsStore.text("Engellendi", "Blocked") : "ACCEPTED".equals(state) ? PhoneSettingsStore.text("Takip ediliyor", "Following") : "PENDING".equals(state) ? PhoneSettingsStore.text("İstek gönderildi", "Requested") : PhoneSettingsStore.text("Takip et", "Follow"), x(78), y(234), size(6), size(102), blocked ? 0xFF788390 : 0xFFFFFFFF);
            boolean canMessage = !blocked && PhoneSocialClientData.canMessageBlockGram(targetUuid);
            drawRoundedRect(g, x(140), y(224), x(215), y(256), size(7), canMessage ? 0xFF242529 : 0xFF17181C);
            PhoneModernText.drawCenteredColoredFitted(g, canMessage ? PhoneSettingsStore.text("Mesaj", "Message") : "DM", x(177), y(234), size(6), size(65), canMessage ? 0xFFFFFFFF : 0xFF788390);
            drawRoundedRect(g, x(221), y(224), x(278), y(256), size(7), blocked ? 0xFF202A39 : 0xFF35171D);
            PhoneModernText.drawCenteredColoredFitted(g, blocked ? PhoneSettingsStore.text("Engeli kaldır", "Unblock") : PhoneSettingsStore.text("Engelle", "Block"), x(249), y(234), size(5), size(47), blocked ? 0xFFB9D4FF : 0xFFFF8793);
        }

        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Gönderiler", "Posts"), x(82), y(278), size(7), size(92), savedTab ? 0xFF737D89 : 0xFFFFFFFF);
        if (self) PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Kaydedilenler", "Saved"), x(218), y(278), size(7), size(92), savedTab ? 0xFFFFFFFF : 0xFF737D89);
        g.fill(x(16), y(293), x(284), y(294), 0x28FFFFFF);
        g.fill(x(savedTab ? 170 : 31), y(293), x(savedTab ? 268 : 133), y(295), 0xFFFFFFFF);

        List<PostEntry> postsList = savedTab && self ? PhoneBlockGramStore.savedPosts() : PhoneBlockGramStore.byAuthor(targetUuid);
        int cell = 88, gap = 2, startX = 16, startY = 304 - profileScroll;
        g.enableScissor(x(14), y(301), x(286), y(579));
        for (int i = 0; i < postsList.size(); i++) {
            int col = i % 3, row = i / 3;
            int left = startX + col * (cell + gap), top = startY + row * (cell + gap);
            g.fill(x(left), y(top), x(left + cell), y(top + cell), 0xFF111216);
            ZPhoneSocialRenderUtil.drawImage(g, minecraft, postsList.get(i).imagePath(), x(left), y(top), size(cell), size(cell));
        }
        g.disableScissor();
        if (postsList.isEmpty()) PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Henüz gönderi yok", "No posts yet"), x(150), y(380), size(9), 0xFF7E8895);
    }

    private void stat(GuiGraphics g, int cx, int top, int value, String label) {
        PhoneModernText.drawCenteredColored(g, Integer.toString(value), x(cx), y(top), size(9), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColoredFitted(g, label, x(cx), y(top + 15), size(6), size(60), 0xFF858F9C);
    }

    private String handle(String name) {
        return name == null ? "player" : name.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (handleDynamicIslandClick(mouseX, mouseY)) return true;
        if (inside(mouseX, mouseY, 18, 48, 48, 78)) { PhoneNavigationState.backOrHome(this.minecraft); return true; }
        if (inside(mouseX, mouseY, 135, 138, 199, 179)) { minecraft.setScreen(new ZPhoneLumiPeopleScreen(targetUuid, true)); return true; }
        if (inside(mouseX, mouseY, 201, 138, 280, 179)) { minecraft.setScreen(new ZPhoneLumiPeopleScreen(targetUuid, false)); return true; }
        boolean self = targetUuid.equals(PhoneClientData.selfUuid());
        if (!self) {
            if (inside(mouseX, mouseY, 221, 220, 280, 260)) {
                if (PhoneSocialClientData.isSocialBlocked(targetUuid)) PhoneSocialClientData.unblockSocialUser(targetUuid);
                else PhoneSocialClientData.blockSocialUser(targetUuid);
                return true;
            }
            if (inside(mouseX, mouseY, 22, 220, 134, 260)) {
                if (PhoneSocialClientData.isSocialBlocked(targetUuid)) return true;
                String state = PhoneSocialClientData.followState("BLOCKGRAM", PhoneClientData.selfUuid(), targetUuid);
                if ("ACCEPTED".equals(state) || "PENDING".equals(state)) PhoneSocialClientData.removeFollow("BLOCKGRAM", targetUuid);
                else PhoneSocialClientData.sendFollowRequest("BLOCKGRAM", targetUuid);
                return true;
            }
            if (inside(mouseX, mouseY, 138, 220, 218, 260)) {
                if (PhoneSocialClientData.isSocialBlocked(targetUuid) || !PhoneSocialClientData.canMessageBlockGram(targetUuid)) return true;
                PhoneSocialStateData.ProfileEntry p = PhoneSocialClientData.profile(targetUuid);
                minecraft.setScreen(new ZPhoneBlockGramThreadScreen(targetUuid, p == null ? PhoneSettingsStore.text("Oyuncu", "Player") : p.playerName));
                return true;
            }
        }
        if (self && inside(mouseX, mouseY, 159, 264, 280, 298)) { savedTab = true; profileScroll = 0; return true; }
        if (inside(mouseX, mouseY, 20, 264, 144, 298)) { savedTab = false; profileScroll = 0; return true; }
        List<PostEntry> list = savedTab && self ? PhoneBlockGramStore.savedPosts() : PhoneBlockGramStore.byAuthor(targetUuid);
        int cell = 88, gap = 2, startX = 16, startY = 304 - profileScroll;
        for (int i = 0; i < list.size(); i++) {
            int col = i % 3, row = i / 3;
            int left = startX + col * (cell + gap), top = startY + row * (cell + gap);
            if (inside(mouseX, mouseY, left, top, left + cell, top + cell)) { minecraft.setScreen(new ZPhoneBlockGramPostScreen(list.get(i).id())); return true; }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D || !inside(mouseX, mouseY, 14, 300, 286, 580)) return true;
        boolean self = targetUuid.equals(PhoneClientData.selfUuid());
        List<PostEntry> list = savedTab && self ? PhoneBlockGramStore.savedPosts() : PhoneBlockGramStore.byAuthor(targetUuid);
        int rows = (list.size() + 2) / 3;
        int contentHeight = rows * 90;
        int max = Math.max(0, contentHeight - 275);
        if (delta < 0) profileScroll = Math.min(max, profileScroll + 64);
        else profileScroll = Math.max(0, profileScroll - 64);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { PhoneNavigationState.backOrHome(this.minecraft); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

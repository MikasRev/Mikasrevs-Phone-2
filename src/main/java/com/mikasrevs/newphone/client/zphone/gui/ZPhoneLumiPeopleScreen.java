package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Followers / following list shared by every Lumi profile. */
public final class ZPhoneLumiPeopleScreen extends AbstractZPhoneScreen {
    private final String profileUuid;
    private final boolean followersMode;
    private int scroll;

    public ZPhoneLumiPeopleScreen(String profileUuid, boolean followersMode) {
        super(Component.literal(followersMode ? PhoneSettingsStore.text("Takipçiler", "Followers") : PhoneSettingsStore.text("Takip edilenler", "Following")));
        this.profileUuid = profileUuid == null ? "" : profileUuid;
        this.followersMode = followersMode;
        PhoneSocialClientData.requestSync();
    }

    private List<PhoneSocialStateData.ProfileEntry> people() {
        return followersMode ? PhoneSocialClientData.followers("BLOCKGRAM", profileUuid) : PhoneSocialClientData.following("BLOCKGRAM", profileUuid);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawFrame(g); drawWallpaper(g);
        drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xFF050608);
        drawStatusBar(g); drawBackButton(g);
        PhoneModernText.drawCenteredColoredFitted(g, followersMode ? PhoneSettingsStore.text("Takipçiler", "Followers") : PhoneSettingsStore.text("Takip edilenler", "Following"), x(150), y(58), size(13), size(210), 0xFFFFFFFF);
        g.fill(x(17), y(86), x(283), y(87), 0x28FFFFFF);
        List<PhoneSocialStateData.ProfileEntry> list = people();
        int top = 92 - scroll;
        g.enableScissor(x(16), y(88), x(284), y(562));
        for (PhoneSocialStateData.ProfileEntry profile : list) {
            if (top + 58 >= 88 && top <= 562) {
                if (inside(mouseX, mouseY, 18, top, 282, top + 57)) g.fill(x(18), y(top), x(282), y(top + 57), 0xFF121317);
                g.fill(x(33), y(top + 57), x(282), y(top + 58), 0x24FFFFFF);
                ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, profile.playerUuid, PhoneSocialClientData.blockGramPhoto(profile.playerUuid), x(24), y(top + 7), size(38));
                PhoneModernText.drawColoredFitted(g, profile.playerName, x(72), y(top + 9), size(9), size(125), 0xFFFFFFFF);
                PhoneModernText.drawColored(g, "@" + profile.playerName.toLowerCase(java.util.Locale.ROOT).replace(" ", ""), x(72), y(top + 26), size(6), 0xFF7F8996);
                if (!profile.playerUuid.equals(PhoneClientData.selfUuid())) {
                    String state = PhoneSocialClientData.followState("BLOCKGRAM", PhoneClientData.selfUuid(), profile.playerUuid);
                    drawRoundedRect(g, x(205), y(top + 13), x(274), y(top + 41), size(7), "ACCEPTED".equals(state) ? 0xFF242529 : 0xFF168AFF);
                    PhoneModernText.drawCenteredColoredFitted(g, "ACCEPTED".equals(state) ? PhoneSettingsStore.text("Takip ediliyor", "Following") : PhoneSettingsStore.text("Takip et", "Follow"), x(239), y(top + 21), size(6), size(59), 0xFFFFFFFF);
                }
            }
            top += 61;
        }
        if (list.isEmpty()) PhoneModernText.drawCenteredColoredFitted(g, followersMode ? PhoneSettingsStore.text("Henüz takipçi yok", "No followers yet") : PhoneSettingsStore.text("Henüz kimseyi takip etmiyorsun", "Not following anyone yet"), x(150), y(280), size(9), size(238), 0xFF7F8996);
        g.disableScissor(); drawHomeIndicator(g); drawFrame(g);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (handleDynamicIslandClick(mouseX, mouseY)) return true;
        if (inside(mouseX, mouseY, 18, 48, 48, 78)) { PhoneNavigationState.backOrHome(this.minecraft); return true; }
        int top = 92 - scroll;
        for (PhoneSocialStateData.ProfileEntry profile : people()) {
            if (inside(mouseX, mouseY, 18, top, 194, top + 55)) { minecraft.setScreen(new ZPhoneLumiProfileScreen(profile.playerUuid)); return true; }
            if (!profile.playerUuid.equals(PhoneClientData.selfUuid()) && inside(mouseX, mouseY, 198, top + 7, 280, top + 48)) {
                String state = PhoneSocialClientData.followState("BLOCKGRAM", PhoneClientData.selfUuid(), profile.playerUuid);
                if ("ACCEPTED".equals(state)) PhoneSocialClientData.removeFollow("BLOCKGRAM", profile.playerUuid);
                else if (!"PENDING".equals(state)) PhoneSocialClientData.sendFollowRequest("BLOCKGRAM", profile.playerUuid);
                return true;
            }
            top += 61;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, people().size() * 61 - 465);
        if (delta < 0) scroll = Math.min(max, scroll + 55); else if (delta > 0) scroll = Math.max(0, scroll - 55);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { PhoneNavigationState.backOrHome(this.minecraft); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

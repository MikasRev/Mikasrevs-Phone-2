package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Followers / Following list for the X-style app. */
public final class ZPhoneXPeopleScreen extends AbstractZPhoneScreen {
    public enum Mode { FOLLOWERS, FOLLOWING }

    private final String playerUuid;
    private final String playerName;
    private final Mode mode;
    private int scroll;

    public ZPhoneXPeopleScreen(String playerUuid, String playerName, Mode mode) {
        super(Component.literal(mode == Mode.FOLLOWERS ? PhoneSettingsStore.text("Takipçiler", "Followers") : PhoneSettingsStore.text("Takip edilenler", "Following")));
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.playerName = playerName == null || playerName.isBlank() ? PhoneSettingsStore.text("Oyuncu", "Player") : playerName;
        this.mode = mode == null ? Mode.FOLLOWERS : mode;
        PhoneSocialClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawFrame(g);
        drawWallpaper(g);
        drawRoundedRect(g, x(8), y(40), x(292), y(602), size(22), 0xFF000000);
        drawStatusBar(g);
        drawBackButton(g);
        PhoneModernText.drawCenteredColoredFitted(g, mode == Mode.FOLLOWERS ? PhoneSettingsStore.text("Takipçiler", "Followers") : PhoneSettingsStore.text("Takip edilenler", "Following"), x(150), y(56), size(13), size(210), 0xFFFFFFFF);
        PhoneModernText.drawCenteredColored(g, "@" + safeHandle(playerName), x(150), y(76), size(7), 0xFF7F8B99);
        drawList(g);
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void drawList(GuiGraphics g) {
        List<PhoneSocialStateData.ProfileEntry> people = mode == Mode.FOLLOWERS
                ? PhoneSocialClientData.followers("BLOCKPOST", playerUuid)
                : PhoneSocialClientData.following("BLOCKPOST", playerUuid);
        int top = 104 - scroll;
        String self = PhoneClientData.selfUuid();
        g.enableScissor(x(15), y(96), x(285), y(564));
        if (people.isEmpty()) {
            PhoneModernText.drawCenteredColoredFitted(g, mode == Mode.FOLLOWERS ? PhoneSettingsStore.text("Henüz takipçi yok", "No followers yet") : PhoneSettingsStore.text("Henüz kimseyi takip etmiyorsun", "Not following anyone yet"), x(150), y(270), size(9), size(238), 0xFF7E8997);
        }
        for (PhoneSocialStateData.ProfileEntry profile : people) {
            if (top + 68 >= 96 && top <= 564) {
                ZPhoneSocialRenderUtil.drawAvatar(g, minecraft, profile.playerUuid, PhoneSocialClientData.blockPostAvatar(profile.playerUuid), x(24), y(top + 8), size(42));
                PhoneModernText.drawColoredFitted(g, profile.playerName, x(76), y(top + 10), size(10), size(116), 0xFFFFFFFF);
                PhoneModernText.drawColored(g, "@" + safeHandle(profile.playerName), x(76), y(top + 30), size(7), 0xFF7E8997);
                if (!profile.playerUuid.equals(self)) {
                    String state = PhoneSocialClientData.followState("BLOCKPOST", self, profile.playerUuid);
                    int color = "ACCEPTED".equals(state) ? 0xFF232A32 : "PENDING".equals(state) ? 0xFF263C54 : 0xFFF0F3F5;
                    int text = "ACCEPTED".equals(state) ? 0xFFFFFFFF : "PENDING".equals(state) ? 0xFFD7E8FF : 0xFF070A0E;
                    drawRoundedRect(g, x(204), y(top + 17), x(274), y(top + 45), size(13), color);
                    PhoneModernText.drawCenteredColoredFitted(g, "ACCEPTED".equals(state) ? PhoneSettingsStore.text("Takip ediliyor", "Following") : "PENDING".equals(state) ? PhoneSettingsStore.text("Bekliyor", "Pending") : PhoneSettingsStore.text("Takip et", "Follow"), x(239), y(top + 25), size(6), size(66), text);
                }
                g.fill(x(20), y(top + 64), x(280), y(top + 65), 0xFF2F3336);
            }
            top += 68;
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (handleDynamicIslandClick(mouseX, mouseY)) return true;
        if (inside(mouseX, mouseY, 18, 48, 48, 78)) {
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }
        List<PhoneSocialStateData.ProfileEntry> people = mode == Mode.FOLLOWERS
                ? PhoneSocialClientData.followers("BLOCKPOST", playerUuid)
                : PhoneSocialClientData.following("BLOCKPOST", playerUuid);
        int top = 104 - scroll;
        String self = PhoneClientData.selfUuid();
        for (PhoneSocialStateData.ProfileEntry profile : people) {
            if (inside(mouseX, mouseY, 18, top, 194, top + 62)) {
                minecraft.setScreen(new com.mikasrevs.newphone.client.gui.PhoneBlockPostUserProfileScreen(profile.playerUuid));
                return true;
            }
            if (!profile.playerUuid.equals(self) && inside(mouseX, mouseY, 198, top + 10, 280, top + 52)) {
                String state = PhoneSocialClientData.followState("BLOCKPOST", self, profile.playerUuid);
                if ("ACCEPTED".equals(state) || "PENDING".equals(state)) {
                    PhoneSocialClientData.removeFollow("BLOCKPOST", profile.playerUuid);
                } else {
                    PhoneSocialClientData.sendFollowRequest("BLOCKPOST", profile.playerUuid);
                }
                return true;
            }
            top += 68;
        }
        if (isHomeIndicatorTap(mouseX, mouseY)) { ZPhoneClient.openHomeScreen(); return true; }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D) return true;
        int count = mode == Mode.FOLLOWERS ? PhoneSocialClientData.followers("BLOCKPOST", playerUuid).size() : PhoneSocialClientData.following("BLOCKPOST", playerUuid).size();
        int max = Math.max(0, count * 68 - 462);
        if (delta < 0) scroll = Math.min(max, scroll + 55); else scroll = Math.max(0, scroll - 55);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            PhoneNavigationState.backOrHome(this.minecraft);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String safeHandle(String name) {
        return name == null ? "player" : name.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
    }
}

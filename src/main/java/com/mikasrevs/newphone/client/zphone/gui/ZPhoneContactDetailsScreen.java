package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.PhoneStateData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ZPhoneContactDetailsScreen extends AbstractZPhoneScreen {
    private final String uuid;
    private final String fallbackName;

    public ZPhoneContactDetailsScreen(String uuid, String name) {
        super(Component.literal(PhoneSettingsStore.text("Kişi", "Contact"))); this.uuid = uuid == null ? "" : uuid; this.fallbackName = name == null ? PhoneSettingsStore.text("Oyuncu", "Player") : name; PhoneClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); drawWallpaper(g); drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xF5070B10); drawStatusBar(g);
        PhoneModernText.drawColored(g, "‹", x(18), y(57), size(20), 0xFF7DBBFF); PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Kişi", "Contact"), x(150), y(59), size(10), 0xFFFFFFFF);
        PhoneStateData.PlayerEntry entry = current(); String name = entry == null ? fallbackName : entry.name; String number = entry == null ? "" : PhoneClientData.formatPhoneNumber(entry.phoneNumber);
        PhonePlayerHeadRenderer.draw(g, uuid, x(112), y(105), size(76)); PhoneModernText.drawCenteredColored(g, name, x(150), y(192), size(19), 0xFFFFFFFF);
        if (!number.isBlank()) PhoneModernText.drawCenteredColored(g, "#" + number, x(150), y(220), size(8), 0xFF8A95A2);
        drawRoundedRect(g, x(24), y(260), x(102), y(316), size(17), 0xFF162B3C); PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Mesaj", "Message"), x(63), y(279), size(7), 0xFF77B9FF);
        drawRoundedRect(g, x(111), y(260), x(189), y(316), size(17), 0xFF162B3C); PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Görüntülü", "Video"), x(150), y(279), size(7), 0xFF77B9FF);
        drawRoundedRect(g, x(198), y(260), x(276), y(316), size(17), 0xFF173426); PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Ara", "Call"), x(237), y(279), size(7), 0xFF75E59D);
        drawRoundedRect(g, x(35), y(356), x(265), y(400), size(13), 0xFF1A222C); PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Kişilerden kaldır", "Remove from contacts"), x(150), y(370), size(8), 0xFFD4DAE1);
        drawRoundedRect(g, x(35), y(416), x(265), y(460), size(13), 0xFF3A2024); PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Oyuncuyu engelle", "Block player"), x(150), y(430), size(8), 0xFFFF8B8B);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Engellemek kişiyi kaldırır ve arama/mesajları durdurur", "Blocking removes the contact and stops calls/messages"), x(150), y(485), size(6), 0xFF6F7B88); drawHomeIndicator(g); drawFrame(g);
    }

    private PhoneStateData.PlayerEntry current() { return PhoneClientData.contactByUuid(uuid); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (inside(mouseX, mouseY, 12, 48, 50, 86)) { PhoneNavigationState.backOrHome(minecraft); return true; }
        if (inside(mouseX, mouseY, 24, 260, 102, 316)) { PhoneClientData.markConversationOpened(uuid); PhoneNavigationState.open(minecraft, this, new ZPhoneMessageThreadScreen(uuid, fallbackName)); return true; }
        if (inside(mouseX, mouseY, 111, 260, 189, 316)) { PhoneClientData.startVideoCall(uuid); return true; }
        if (inside(mouseX, mouseY, 198, 260, 276, 316)) { PhoneClientData.startCall(uuid); return true; }
        if (inside(mouseX, mouseY, 35, 356, 265, 400)) { PhoneClientData.removeContact(uuid); PhoneNavigationState.backOrHome(minecraft); return true; }
        if (inside(mouseX, mouseY, 35, 416, 265, 460)) { PhoneClientData.blockPlayer(uuid); PhoneNavigationState.backOrHome(minecraft); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { if (keyCode == GLFW.GLFW_KEY_ESCAPE) { PhoneNavigationState.backOrHome(minecraft); return true; } return super.keyPressed(keyCode, scanCode, modifiers); }
}

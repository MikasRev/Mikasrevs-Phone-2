package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.PhoneStateData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Shared contact book used by Messages and Phone. */
public final class ZPhoneContactsScreen extends AbstractZPhoneScreen {
    private enum Tab { CONTACTS, BLOCKED }
    private Tab tab = Tab.CONTACTS;
    private final StringBuilder search = new StringBuilder();
    private boolean focused;
    private int scroll;

    public ZPhoneContactsScreen() { super(Component.literal(PhoneSettingsStore.text("Kişiler", "Contacts"))); PhoneClientData.requestSync(); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); drawWallpaper(g); drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xF5070B10); drawStatusBar(g);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Kişiler", "Contacts"), x(18), y(55), size(23), 0xFFFFFFFF);
        drawRoundedRect(g, x(224), y(55), x(282), y(84), size(13), 0xFF18212B); PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("+ Ekle", "+ Add"), x(253), y(63), size(7), size(50), 0xFF59A9FF);
        drawMyCard(g); drawTabs(g); drawSearch(g); drawRows(g, mouseX, mouseY); drawHomeIndicator(g); drawFrame(g);
    }

    private void drawMyCard(GuiGraphics g) {
        drawRoundedRect(g, x(18), y(96), x(282), y(147), size(14), 0xFF141C25);
        PhonePlayerHeadRenderer.draw(g, PhoneClientData.selfUuid(), x(29), y(106), size(31));
        String self = PhoneClientData.selfName().isBlank() ? PhoneSettingsStore.text("Sen", "You") : PhoneClientData.selfName();
        PhoneModernText.drawColored(g, self, x(70), y(105), size(10), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Numaram  #", "My number  #") + PhoneClientData.formattedSelfPhoneNumber(), x(70), y(126), size(7), 0xFF7E8996);
    }

    private void drawTabs(GuiGraphics g) {
        drawRoundedRect(g, x(18), y(158), x(282), y(190), size(13), 0xFF141B24);
        drawRoundedRect(g, tab == Tab.CONTACTS ? x(21) : x(151), y(161), tab == Tab.CONTACTS ? x(149) : x(279), y(187), size(11), 0xFF253142);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Kişiler", "Contacts"), x(85), y(169), size(8), size(112), tab == Tab.CONTACTS ? 0xFFFFFFFF : 0xFF7E8996);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Engellenen ", "Blocked ") + PhoneClientData.blockedPlayers().size(), x(215), y(169), size(8), size(112), tab == Tab.BLOCKED ? 0xFFFFFFFF : 0xFF7E8996);
    }

    private void drawSearch(GuiGraphics g) {
        drawRoundedRect(g, x(18), y(201), x(282), y(232), size(12), 0xFF171F29);
        PhoneModernText.drawColored(g, "⌕", x(29), y(208), size(13), 0xFF87919D);
        String shown = search.length() == 0 && !focused ? (tab == Tab.CONTACTS ? PhoneSettingsStore.text("Kişilerde ara", "Search contacts") : PhoneSettingsStore.text("Engellenenlerde ara", "Search blocked")) : search.toString();
        PhoneModernText.drawColored(g, shown, x(51), y(210), size(8), search.length() == 0 ? 0xFF79838F : 0xFFFFFFFF);
        if (focused && isCaretVisible()) { int w = search.length() == 0 ? 0 : PhoneModernText.width(search.toString(), size(8)); g.fill(x(51) + w + size(2), y(209), x(51) + w + size(3), y(221), 0xFFFFFFFF); }
    }

    private List<PhoneStateData.PlayerEntry> entries() {
        List<PhoneStateData.PlayerEntry> list = new ArrayList<>(tab == Tab.CONTACTS ? PhoneClientData.contacts() : PhoneClientData.blockedPlayers());
        String q = search.toString().trim().toLowerCase(Locale.ROOT); String digits = PhoneClientData.normalizePhoneNumber(q);
        if (!q.isEmpty()) list.removeIf(entry -> !entry.name.toLowerCase(Locale.ROOT).contains(q) && (digits.isEmpty() || !PhoneClientData.normalizePhoneNumber(entry.phoneNumber).contains(digits)));
        list.sort((a, b) -> a.name.compareToIgnoreCase(b.name)); return list;
    }

    private void drawRows(GuiGraphics g, int mouseX, int mouseY) {
        List<PhoneStateData.PlayerEntry> list = entries(); scroll = Math.min(scroll, Math.max(0, list.size() - 5)); int count = Math.min(5, Math.max(0, list.size() - scroll));
        if (count == 0) { PhoneModernText.drawCenteredColored(g, tab == Tab.CONTACTS ? PhoneSettingsStore.text("Henüz kişi yok", "No contacts yet") : PhoneSettingsStore.text("Engellenmiş oyuncu yok", "No blocked players"), x(150), y(315), size(11), 0xFF7F8A97); return; }
        for (int i = 0; i < count; i++) drawRow(g, list.get(scroll + i), 246 + i * 65, mouseX, mouseY);
    }

    private void drawRow(GuiGraphics g, PhoneStateData.PlayerEntry entry, int top, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, 13, top, 287, top + 59)) drawRoundedRect(g, x(13), y(top), x(287), y(top + 59), size(12), 0xFF151C25);
        PhonePlayerHeadRenderer.draw(g, entry.uuid, x(25), y(top + 14), size(31));
        PhoneModernText.drawColored(g, entry.name, x(66), y(top + 9), size(10), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, "#" + PhoneClientData.formatPhoneNumber(entry.phoneNumber) + (entry.online ? PhoneSettingsStore.text("  · Çevrimiçi", "  · Online") : PhoneSettingsStore.text("  · Çevrimdışı", "  · Offline")), x(66), y(top + 29), size(6), entry.online ? 0xFF7EDC9B : 0xFF7E8996);
        if (tab == Tab.CONTACTS) {
            fillCircle(g, x(231), y(top + 29), size(14), 0xFF192D3D); PhoneModernText.drawCenteredColored(g, "✉", x(231), y(top + 21), size(11), 0xFF6AB3FF);
            fillCircle(g, x(266), y(top + 29), size(14), 0xFF173326); drawHandset(g, x(266), y(top + 29), 0xFF31D158);
        } else { drawRoundedRect(g, x(220), y(top + 15), x(276), y(top + 44), size(10), 0xFF26313D); PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Engeli kaldır", "Unblock"), x(248), y(top + 23), size(5), size(48), 0xFF9AD7FF); }
        g.fill(x(66), y(top + 58), x(282), y(top + 59), 0x242E3540);
    }

    private void drawHandset(GuiGraphics g, int cx, int cy, int color) { int s = Math.max(1, size(1.2F)); g.fill(cx-7*s,cy-2*s,cx-3*s,cy+3*s,color); g.fill(cx-4*s,cy-5*s,cx,cy,color); g.fill(cx,cy-5*s,cx+4*s,cy,color); g.fill(cx+3*s,cy-2*s,cx+7*s,cy+3*s,color); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (inside(mouseX, mouseY, 224, 55, 282, 84)) { PhoneNavigationState.open(minecraft, this, new ZPhoneMessagesScreen(true)); return true; }
        if (inside(mouseX, mouseY, 21, 161, 149, 187)) { tab = Tab.CONTACTS; scroll = 0; search.setLength(0); return true; }
        if (inside(mouseX, mouseY, 151, 161, 279, 187)) { tab = Tab.BLOCKED; scroll = 0; search.setLength(0); return true; }
        if (inside(mouseX, mouseY, 18, 201, 282, 232)) { focused = true; return true; }
        focused = false;
        List<PhoneStateData.PlayerEntry> list = entries(); int count = Math.min(5, Math.max(0, list.size() - scroll));
        for (int i = 0; i < count; i++) {
            PhoneStateData.PlayerEntry entry = list.get(scroll + i); int top = 246 + i * 65;
            if (tab == Tab.BLOCKED && inside(mouseX, mouseY, 216, top + 10, 280, top + 49)) { PhoneClientData.unblockPlayer(entry.uuid); return true; }
            if (tab == Tab.CONTACTS) {
                if (inside(mouseX, mouseY, 213, top + 8, 248, top + 50)) { PhoneClientData.markConversationOpened(entry.uuid); PhoneNavigationState.open(minecraft, this, new ZPhoneMessageThreadScreen(entry.uuid, entry.name)); return true; }
                if (inside(mouseX, mouseY, 248, top + 8, 284, top + 50)) { PhoneClientData.startCall(entry.uuid); return true; }
                if (inside(mouseX, mouseY, 13, top, 212, top + 59)) { PhoneNavigationState.open(minecraft, this, new ZPhoneContactDetailsScreen(entry.uuid, entry.name)); return true; }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inside(mouseX, mouseY, 10, 240, 290, 570) && delta != 0.0D) { int max = Math.max(0, entries().size() - 5); scroll = delta < 0 ? Math.min(max, scroll + 1) : Math.max(0, scroll - 1); return true; }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (focused && character >= 32 && character != 127 && search.length() < 24) { search.append(character); scroll = 0; return true; }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { PhoneNavigationState.backOrHome(minecraft); return true; }
        if (focused && keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (search.length() > 0) search.deleteCharAt(search.length() - 1); scroll = 0; return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

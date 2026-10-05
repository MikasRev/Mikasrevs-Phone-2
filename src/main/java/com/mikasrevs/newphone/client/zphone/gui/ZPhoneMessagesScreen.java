package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.PhoneStateData;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Multiplayer Messages hub for the Forge 1.20.1 MPhone port. */
public final class ZPhoneMessagesScreen extends AbstractZPhoneScreen {
    private enum Tab { CHATS, REQUESTS }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int REQUEST_ACCEPT_LEFT = 178;
    private static final int REQUEST_ACCEPT_RIGHT = 230;
    private static final int REQUEST_DECLINE_LEFT = 236;
    private static final int REQUEST_DECLINE_RIGHT = 276;
    private static final int REQUEST_BUTTON_TOP = 13;
    private static final int REQUEST_BUTTON_BOTTOM = 51;

    private Tab tab = Tab.CHATS;
    private boolean composeMenu;
    private final StringBuilder search = new StringBuilder();
    private final StringBuilder addQuery = new StringBuilder();
    private boolean searchFocused;
    private boolean addFocused;
    private int chatOffset;
    private int incomingOffset;
    private int outgoingOffset;
    private String lastNotice = "";
    private long noticeUntil;

    public ZPhoneMessagesScreen() { this(false); }

    public ZPhoneMessagesScreen(boolean openRequests) {
        super(Component.literal(PhoneSettingsStore.text("Mesajlar", "Messages")));
        if (openRequests) { tab = Tab.REQUESTS; addFocused = true; }
        PhoneClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        updateNotice();
        drawWallpaper(g);
        drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xF5070B10);
        drawStatusBar(g);
        drawHeader(g);
        drawTabs(g);
        if (tab == Tab.CHATS) drawChats(g, mouseX, mouseY); else drawRequests(g);
        drawNotice(g);
        if(composeMenu){
            beginOverlayLayer(g);drawRoundedRect(g,x(131),y(91),x(284),y(187),size(16),0xFF2C2C33);finishOverlayBackground(g);
            PhoneGroupVisuals.text(this,g,PhoneSettingsStore.text("Yeni grup","New group"),146,108,11,124,PhoneGroupVisuals.BLUE);
            g.fill(x(142),y(137),x(274),y(138),0xFF414148);
            PhoneGroupVisuals.text(this,g,PhoneSettingsStore.text("Kişi ekle","Add contact"),146,153,11,124,PhoneGroupVisuals.TEXT);
        }
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void drawHeader(GuiGraphics g) {
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Mesajlar", "Messages"), x(18), y(55), readableTextSize(23), 0xFFFFFFFF);
        String number = PhoneClientData.formattedSelfPhoneNumber();
        if (!number.isBlank()) PhoneModernText.drawColored(g, "#" + number, x(19), y(82), readableTextSize(7), 0xFF7C8794);
        drawRoundedRect(g, x(226), y(55), x(282), y(84), size(13), 0xFF19222D);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("+ Ekle", "+ Add"), x(254), y(63), readableTextSize(7), size(48), 0xFF59A9FF);
    }

    private void drawTabs(GuiGraphics g) {
        drawRoundedRect(g, x(18), y(100), x(282), y(132), size(13), 0xFF141B24);
        drawRoundedRect(g, tab == Tab.CHATS ? x(21) : x(151), y(103), tab == Tab.CHATS ? x(149) : x(279), y(129), size(11), 0xFF253142);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Sohbetler", "Chats"), x(85), y(111), readableTextSize(8), size(112), tab == Tab.CHATS ? 0xFFFFFFFF : 0xFF7E8996);
        int requestCount = PhoneClientData.incomingRequests().size();
        PhoneModernText.drawCenteredColoredFitted(g, requestCount > 0 ? PhoneSettingsStore.text("İstekler  ", "Requests  ") + requestCount : PhoneSettingsStore.text("İstekler", "Requests"), x(215), y(111), readableTextSize(8), size(112), tab == Tab.REQUESTS ? 0xFFFFFFFF : 0xFF7E8996);
    }

    private void drawChats(GuiGraphics g, int mouseX, int mouseY) {
        drawRoundedRect(g, x(18), y(145), x(282), y(176), size(12), 0xFF171F29);
        PhoneModernText.drawColored(g, "⌕", x(29), y(152), readableTextSize(13), 0xFF87919D);
        String shown = search.length() == 0 && !searchFocused ? PhoneSettingsStore.text("Sohbetlerde ara", "Search conversations") : search.toString();
        PhoneModernText.drawColored(g, shown, x(51), y(154), readableTextSize(8), search.length() == 0 ? 0xFF79838F : 0xFFFFFFFF);
        if (searchFocused && isCaretVisible()) {
            int w = search.length() == 0 ? 0 : PhoneModernText.width(search.toString(), readableTextSize(8));
            g.fill(x(51) + w + size(2), y(153), x(51) + w + size(3), y(165), 0xFFFFFFFF);
        }

        List<PhoneStateData.PlayerEntry> contacts = filteredContacts();
        chatOffset = Math.min(chatOffset, Math.max(0, contacts.size() - 5));
        int count = Math.min(5, Math.max(0, contacts.size() - chatOffset));
        if (count == 0) {
            PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Henüz sohbet yok", "No conversations yet"), x(150), y(270), readableTextSize(12), 0xFF8B939F);
            PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Oyuncu eklemek için İstekler bölümünü aç", "Open Requests to add a player"), x(150), y(292), readableTextSize(7), size(250), 0xFF626D79);
            return;
        }
        for (int i = 0; i < count; i++) drawChatRow(g, contacts.get(chatOffset + i), 188 + i * 70, mouseX, mouseY);
    }

    private void drawChatRow(GuiGraphics g, PhoneStateData.PlayerEntry contact, int top, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, 13, top, 287, top + 64)) drawRoundedRect(g, x(13), y(top), x(287), y(top + 64), size(12), 0xFF151C25);
        fillCircle(g, x(45), y(top + 31), size(22), 0xFF2B3541);
        var group=com.mikasrevs.newphone.client.data.PhoneGroupClient.group(contact.uuid);
        if(group!=null)PhoneGroupVisuals.avatar(this,g,group,45,top+31,44);else PhonePlayerHeadRenderer.draw(g, contact.uuid, x(28), y(top + 14), size(34));
        if (contact.online) fillCircle(g, x(61), y(top + 48), size(5), 0xFF31D158);
        PhoneModernText.drawColoredFitted(g, contact.name, x(78), y(top + 9), readableTextSize(11),size(160), 0xFFFFFFFF);
        PhoneStateData.MessageEntry last = PhoneClientData.lastMessage(contact.uuid);
        PhoneModernText.drawColored(g, trim(PhoneClientData.messagePreview(last), 30), x(78), y(top + 31), readableTextSize(7), 0xFF939CAA);
        if (last != null) PhoneModernText.drawColored(g, TIME.format(Instant.ofEpochMilli(last.timestamp).atZone(ZoneId.systemDefault())), x(246), y(top + 10), readableTextSize(6), 0xFF6F7A87);
        int unread = PhoneClientData.unreadCount(contact.uuid);
        if (unread > 0) {
            fillCircle(g, x(266), y(top + 40), size(8), 0xFF0A84FF);
            PhoneModernText.drawCenteredColored(g, unread > 9 ? "9+" : Integer.toString(unread), x(266), y(top + 35), readableTextSize(6), 0xFFFFFFFF);
        }
        g.fill(x(78), y(top + 63), x(282), y(top + 64), 0x242E3540);
    }

    private void drawRequests(GuiGraphics g) {
        drawRoundedRect(g, x(18), y(145), x(282), y(211), size(15), 0xFF151D27);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Minecraft kullanıcı adı veya 4 haneli numarayla ekle", "Add by Minecraft nick or 4-digit number"), x(29), y(155), readableTextSize(7), size(240), 0xFFAAB5C2);
        drawRoundedRect(g, x(28), y(177), x(218), y(201), size(9), addFocused ? 0xFF29394C : 0xFF202A35);
        String shown = addQuery.length() == 0 ? PhoneSettingsStore.text("Nick veya 4 hane", "Nickname or 4 digits") : addQuery.toString();
        PhoneModernText.drawColored(g, shown, x(38), y(183), readableTextSize(7), addQuery.length() == 0 ? 0xFF728092 : 0xFFFFFFFF);
        if (addFocused && isCaretVisible()) {
            int w = addQuery.length() == 0 ? 0 : PhoneModernText.width(addQuery.toString(), readableTextSize(7));
            g.fill(x(38) + w + size(2), y(182), x(38) + w + size(3), y(193), 0xFFFFFFFF);
        }
        drawRoundedRect(g, x(225), y(177), x(272), y(201), size(9), addQuery.toString().trim().isEmpty() ? 0xFF303945 : 0xFF0A84FF);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Gönder", "Send"), x(248), y(183), readableTextSize(6), size(40), 0xFFFFFFFF);

        List<PhoneStateData.PlayerEntry> incoming = PhoneClientData.incomingRequests();
        List<PhoneStateData.PlayerEntry> outgoing = PhoneClientData.outgoingRequests();
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("GELEN  ", "INCOMING  ") + incoming.size(), x(20), y(228), readableTextSize(7), 0xFF7F8B98);
        incomingOffset = Math.min(incomingOffset, Math.max(0, incoming.size() - 2));
        for (int i = 0; i < Math.min(2, Math.max(0, incoming.size() - incomingOffset)); i++) drawIncomingRow(g, incoming.get(incomingOffset + i), 245 + i * 72);
        int sentTop = 395;
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("GÖNDERİLEN  ", "SENT  ") + outgoing.size(), x(20), y(sentTop), readableTextSize(7), 0xFF7F8B98);
        outgoingOffset = Math.min(outgoingOffset, Math.max(0, outgoing.size() - 2));
        for (int i = 0; i < Math.min(2, Math.max(0, outgoing.size() - outgoingOffset)); i++) drawOutgoingRow(g, outgoing.get(outgoingOffset + i), sentTop + 17 + i * 58);
    }

    private void drawIncomingRow(GuiGraphics g, PhoneStateData.PlayerEntry entry, int top) {
        drawRoundedRect(g, x(18), y(top), x(282), y(top + 62), size(12), 0xFF151D27);
        PhonePlayerHeadRenderer.draw(g, entry.uuid, x(28), y(top + 15), size(31));
        PhoneModernText.drawColored(g, entry.name, x(68), y(top + 9), readableTextSize(9), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, "#" + PhoneClientData.formatPhoneNumber(entry.phoneNumber), x(68), y(top + 28), readableTextSize(6), 0xFF7F8A97);
        drawRoundedRect(g, x(REQUEST_ACCEPT_LEFT), y(top + REQUEST_BUTTON_TOP),
                x(REQUEST_ACCEPT_RIGHT), y(top + REQUEST_BUTTON_BOTTOM), size(11), 0xFF216A3E);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("Kabul et", "Accept"),
                x((REQUEST_ACCEPT_LEFT + REQUEST_ACCEPT_RIGHT) / 2), y(top + 28), readableTextSize(7), size(REQUEST_ACCEPT_RIGHT - REQUEST_ACCEPT_LEFT - 8), 0xFFFFFFFF);
        drawRoundedRect(g, x(REQUEST_DECLINE_LEFT), y(top + REQUEST_BUTTON_TOP),
                x(REQUEST_DECLINE_RIGHT), y(top + REQUEST_BUTTON_BOTTOM), size(11), 0xFF333B46);
        PhoneModernText.drawCenteredColored(g, "X",
                x((REQUEST_DECLINE_LEFT + REQUEST_DECLINE_RIGHT) / 2), y(top + 27), readableTextSize(9), 0xFFFFFFFF);
    }

    private void drawOutgoingRow(GuiGraphics g, PhoneStateData.PlayerEntry entry, int top) {
        drawRoundedRect(g, x(18), y(top), x(282), y(top + 50), size(11), 0xFF141B24);
        PhonePlayerHeadRenderer.draw(g, entry.uuid, x(27), y(top + 10), size(30));
        PhoneModernText.drawColored(g, entry.name, x(66), y(top + 8), readableTextSize(8), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Bekliyor", "Pending"), x(66), y(top + 27), readableTextSize(6), 0xFF8D98A5);
        drawRoundedRect(g, x(228), y(top + 12), x(272), y(top + 39), size(9), 0xFF302D32);
        PhoneModernText.drawCenteredColoredFitted(g, PhoneSettingsStore.text("İptal", "Cancel"), x(250), y(top + 20), readableTextSize(5), size(36), 0xFFFF8B8B);
    }

    private List<PhoneStateData.PlayerEntry> filteredContacts() {
        List<PhoneStateData.PlayerEntry> result = new ArrayList<>(PhoneClientData.sortedMessagePeers());
        String q = search.toString().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return result;
        String digits = PhoneClientData.normalizePhoneNumber(q);
        result.removeIf(entry -> !entry.name.toLowerCase(Locale.ROOT).contains(q) && (digits.isEmpty() || !PhoneClientData.normalizePhoneNumber(entry.phoneNumber).contains(digits)));
        return result;
    }

    private void sendRequest() {
        String value = addQuery.toString().trim();
        if (value.isEmpty()) return;
        PhoneClientData.sendFriendRequest(value);
        addQuery.setLength(0);
        addFocused = true;
    }

    private void updateNotice() {
        String notice = PhoneClientData.notice();
        if (notice == null || notice.isBlank() || notice.equals(lastNotice)) return;
        lastNotice = notice; noticeUntil = System.currentTimeMillis() + 2500L;
    }

    private void drawNotice(GuiGraphics g) {
        if (lastNotice.isBlank() || System.currentTimeMillis() > noticeUntil) return;
        drawRoundedRect(g, x(38), y(535), x(262), y(565), size(12), 0xF02A3644);
        PhoneModernText.drawCenteredColored(g, trim(lastNotice, 38), x(150), y(544), readableTextSize(6), 0xFFFFFFFF);
    }

    private String trim(String value, int max) {
        String safe = value == null ? "" : value.replace('\n', ' ').trim();
        return safe.length() <= max ? safe : safe.substring(0, Math.max(1, max - 1)) + "…";
    }


    private int readableTextSize(float designSize) {
        float factor = designSize <= 6.0F ? 1.18F : designSize <= 8.0F ? 1.10F : designSize <= 10.0F ? 1.05F : 1.0F;
        return Math.max(1, Math.round(size(designSize) * factor));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if(composeMenu){
            composeMenu=false;
            if(inside(mouseX,mouseY,131,91,284,137))PhoneNavigationState.open(minecraft,this,new PhoneGroupDetailsScreen());
            else if(inside(mouseX,mouseY,131,138,284,187)){tab=Tab.REQUESTS;addFocused=true;}
            return true;
        }
        if (inside(mouseX, mouseY, 21, 103, 149, 129)) { tab = Tab.CHATS; searchFocused = false; addFocused = false; return true; }
        if (inside(mouseX, mouseY, 151, 103, 279, 129)) { tab = Tab.REQUESTS; searchFocused = false; addFocused = false; return true; }
        if (inside(mouseX, mouseY, 226, 55, 282, 84)) { composeMenu=true;searchFocused=false;addFocused=false;return true; }
        if (tab == Tab.CHATS) {
            if (inside(mouseX, mouseY, 18, 145, 282, 176)) { searchFocused = true; return true; }
            searchFocused = false;
            List<PhoneStateData.PlayerEntry> contacts = filteredContacts();
            int count = Math.min(5, Math.max(0, contacts.size() - chatOffset));
            for (int i = 0; i < count; i++) {
                int top = 188 + i * 70;
                if (inside(mouseX, mouseY, 13, top, 287, top + 64)) {
                    PhoneStateData.PlayerEntry entry = contacts.get(chatOffset + i);
                    PhoneClientData.markConversationOpened(entry.uuid);
                    PhoneNavigationState.open(minecraft, this, com.mikasrevs.newphone.client.data.PhoneGroupClient.group(entry.uuid)!=null?new PhoneGroupThreadScreen(entry.uuid):new ZPhoneMessageThreadScreen(entry.uuid, entry.name));
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inside(mouseX, mouseY, 28, 177, 218, 201)) { addFocused = true; return true; }
        if (inside(mouseX, mouseY, 225, 177, 272, 201)) { sendRequest(); return true; }
        addFocused = false;
        List<PhoneStateData.PlayerEntry> incoming = PhoneClientData.incomingRequests();
        for (int i = 0; i < Math.min(2, Math.max(0, incoming.size() - incomingOffset)); i++) {
            PhoneStateData.PlayerEntry entry = incoming.get(incomingOffset + i); int top = 245 + i * 72;
            if (inside(mouseX, mouseY, REQUEST_ACCEPT_LEFT, top + REQUEST_BUTTON_TOP,
                    REQUEST_ACCEPT_RIGHT, top + REQUEST_BUTTON_BOTTOM)) {
                PhoneClientData.acceptFriendRequest(entry.uuid);
                return true;
            }
            if (inside(mouseX, mouseY, REQUEST_DECLINE_LEFT, top + REQUEST_BUTTON_TOP,
                    REQUEST_DECLINE_RIGHT, top + REQUEST_BUTTON_BOTTOM)) {
                PhoneClientData.declineFriendRequest(entry.uuid);
                return true;
            }
        }
        List<PhoneStateData.PlayerEntry> outgoing = PhoneClientData.outgoingRequests();
        for (int i = 0; i < Math.min(2, Math.max(0, outgoing.size() - outgoingOffset)); i++) {
            PhoneStateData.PlayerEntry entry = outgoing.get(outgoingOffset + i); int top = 412 + i * 58;
            if (inside(mouseX, mouseY, 224, top + 8, 276, top + 43)) { PhoneClientData.cancelFriendRequest(entry.uuid); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D) return super.mouseScrolled(mouseX, mouseY, delta);
        if (tab == Tab.CHATS && inside(mouseX, mouseY, 10, 180, 290, 540)) {
            int max = Math.max(0, filteredContacts().size() - 5); chatOffset = delta < 0 ? Math.min(max, chatOffset + 1) : Math.max(0, chatOffset - 1); return true;
        }
        if (tab == Tab.REQUESTS && inside(mouseX, mouseY, 10, 238, 290, 390)) {
            int max = Math.max(0, PhoneClientData.incomingRequests().size() - 2); incomingOffset = delta < 0 ? Math.min(max, incomingOffset + 1) : Math.max(0, incomingOffset - 1); return true;
        }
        if (tab == Tab.REQUESTS && inside(mouseX, mouseY, 10, 400, 290, 535)) {
            int max = Math.max(0, PhoneClientData.outgoingRequests().size() - 2); outgoingOffset = delta < 0 ? Math.min(max, outgoingOffset + 1) : Math.max(0, outgoingOffset - 1); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (character < 32 || character == 127) return super.charTyped(character, modifiers);
        if (tab == Tab.CHATS && searchFocused && search.length() < 24) { search.append(character); chatOffset = 0; return true; }
        if (tab == Tab.REQUESTS && addFocused && addQuery.length() < 24) { addQuery.append(character); return true; }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { if(composeMenu)composeMenu=false;else PhoneNavigationState.backOrHome(minecraft); return true; }
        if (tab == Tab.CHATS && searchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (search.length() > 0) search.deleteCharAt(search.length() - 1); chatOffset = 0; return true; }
        if (tab == Tab.REQUESTS && addFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (addQuery.length() > 0) addQuery.deleteCharAt(addQuery.length() - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { sendRequest(); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

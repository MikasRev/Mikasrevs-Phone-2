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
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** MPhone dialer with Recents, Contacts and four-digit keypad. */
public final class ZPhonePhoneScreen extends AbstractZPhoneScreen {
    private enum Tab { RECENTS, CONTACTS, KEYPAD }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private Tab tab = Tab.RECENTS;
    private final StringBuilder digits = new StringBuilder();
    private int scroll;
    private String status = "";
    private long statusUntil;

    public ZPhonePhoneScreen() { super(Component.literal(PhoneSettingsStore.text("Telefon", "Phone"))); PhoneClientData.requestSync(); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); drawWallpaper(g); drawRoundedRect(g, x(3), y(40), x(297), y(606), size(22), 0xF5070B10); drawStatusBar(g);
        drawHeader(g);
        if (tab == Tab.RECENTS) drawRecents(g, mouseX, mouseY); else if (tab == Tab.CONTACTS) drawContacts(g, mouseX, mouseY); else drawKeypad(g);
        drawTabs(g); drawToast(g); drawHomeIndicator(g); drawFrame(g);
    }

    private void drawHeader(GuiGraphics g) {
        PhoneModernText.drawColored(g, tab == Tab.RECENTS ? PhoneSettingsStore.text("Son Aramalar", "Recents") : tab == Tab.CONTACTS ? PhoneSettingsStore.text("Kişiler", "Contacts") : PhoneSettingsStore.text("Tuş Takımı", "Keypad"), x(18), y(55), size(23), 0xFFFFFFFF);
        boolean available = PhoneClientData.voiceCallingAvailable(); fillCircle(g, x(270), y(69), size(4), available ? 0xFF31D158 : 0xFFFF9F0A);
        String provider = PhoneClientData.voiceProvider(); PhoneModernText.drawColored(g, provider.isBlank() ? PhoneSettingsStore.text("Ses", "Voice") : provider, x(202), y(63), size(6), 0xFF7F8A96);
    }

    private void drawRecents(GuiGraphics g, int mouseX, int mouseY) {
        List<PhoneStateData.CallEntry> history = PhoneClientData.callHistory(); scroll = Math.min(scroll, Math.max(0, history.size() - 7));
        if (history.isEmpty()) { PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Son arama yok", "No recent calls"), x(150), y(280), size(12), 0xFF7E8996); return; }
        int count = Math.min(7, history.size() - scroll);
        for (int i = 0; i < count; i++) {
            PhoneStateData.CallEntry entry = history.get(scroll + i); int top = 103 + i * 59;
            if (inside(mouseX, mouseY, 12, top, 288, top + 54)) drawRoundedRect(g, x(12), y(top), x(288), y(top + 54), size(11), 0xFF151C25);
            PhonePlayerHeadRenderer.draw(g, entry.peerUuid, x(22), y(top + 11), size(32)); PhoneModernText.drawColored(g, entry.peerName, x(65), y(top + 7), size(10), 0xFFFFFFFF);
            PhoneModernText.drawColored(g, directionSymbol(entry.direction) + " " + resultLabel(entry.result), x(65), y(top + 27), size(6), resultColor(entry.result));
            PhoneModernText.drawColored(g, TIME.format(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault())), x(228), y(top + 8), size(6), 0xFF75808C);
            fillCircle(g, x(266), y(top + 31), size(13), 0xFF173326); drawHandset(g, x(266), y(top + 31), 0xFF31D158); g.fill(x(65), y(top + 53), x(282), y(top + 54), 0x242E3540);
        }
    }

    private void drawContacts(GuiGraphics g, int mouseX, int mouseY) {
        List<PhoneStateData.PlayerEntry> contacts = PhoneClientData.sortedContacts(); scroll = Math.min(scroll, Math.max(0, contacts.size() - 7));
        if (contacts.isEmpty()) { PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Mesajlar uygulamasından kişi ekle", "Add contacts from Messages"), x(150), y(280), size(10), 0xFF7E8996); return; }
        int count = Math.min(7, contacts.size() - scroll);
        for (int i = 0; i < count; i++) {
            PhoneStateData.PlayerEntry entry = contacts.get(scroll + i); int top = 103 + i * 59;
            if (inside(mouseX, mouseY, 12, top, 288, top + 54)) drawRoundedRect(g, x(12), y(top), x(288), y(top + 54), size(11), 0xFF151C25);
            PhonePlayerHeadRenderer.draw(g, entry.uuid, x(22), y(top + 11), size(32)); PhoneModernText.drawColored(g, entry.name, x(65), y(top + 7), size(10), 0xFFFFFFFF);
            PhoneModernText.drawColored(g, "#" + PhoneClientData.formatPhoneNumber(entry.phoneNumber) + (entry.online ? PhoneSettingsStore.text(" · Çevrimiçi", " · Online") : PhoneSettingsStore.text(" · Çevrimdışı", " · Offline")), x(65), y(top + 27), size(6), entry.online ? 0xFF75DA96 : 0xFF7E8996);
            fillCircle(g, x(266), y(top + 31), size(13), 0xFF173326); drawHandset(g, x(266), y(top + 31), 0xFF31D158); g.fill(x(65), y(top + 53), x(282), y(top + 54), 0x242E3540);
        }
    }

    private void drawKeypad(GuiGraphics g) {
        String shown = digits.length() == 0 ? PhoneSettingsStore.text("4 haneli numara", "4-digit number") : digits.toString();
        PhoneModernText.drawCenteredColored(g, shown, x(150), y(106), size(digits.length() == 0 ? 11 : 26), digits.length() == 0 ? 0xFF707B87 : 0xFFFFFFFF);
        PhoneStateData.PlayerEntry match = PhoneClientData.contactByNumber(digits.toString());
        if (digits.length() == 4) PhoneModernText.drawCenteredColored(g, match == null ? PhoneSettingsStore.text("Kişilerde değil", "Not in contacts") : match.name, x(150), y(145), size(8), match == null ? 0xFFFF8B8B : 0xFF65B0FF);
        String[] keys = {"1","2","3","4","5","6","7","8","9","*","0","#"};
        for (int i = 0; i < keys.length; i++) { int col=i%3,row=i/3,cx=79+col*71,cy=206+row*70; fillCircle(g, x(cx), y(cy), size(26), 0xFF252C35); PhoneModernText.drawCenteredColored(g, keys[i], x(cx), y(cy-12), size(18), 0xFFFFFFFF); }
        fillCircle(g, x(150), y(486), size(28), 0xFF31D158); drawHandset(g, x(150), y(486), 0xFFFFFFFF);
        if (digits.length() > 0) { fillCircle(g, x(220), y(486), size(19), 0xFF252C35); PhoneModernText.drawCenteredColored(g, "⌫", x(220), y(477), size(14), 0xFFFFFFFF); }
    }

    private void drawTabs(GuiGraphics g) {
        drawRoundedRect(g, x(26), y(540), x(274), y(578), size(17), 0xFF141B24); int left = tab == Tab.RECENTS ? 30 : tab == Tab.CONTACTS ? 111 : 193;
        drawRoundedRect(g, x(left), y(544), x(left + 77), y(574), size(13), 0xFF263342);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Son Aramalar", "Recents"), x(68), y(554), size(6), tab == Tab.RECENTS ? 0xFFFFFFFF : 0xFF7C8794);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Kişiler", "Contacts"), x(150), y(554), size(6), tab == Tab.CONTACTS ? 0xFFFFFFFF : 0xFF7C8794);
        PhoneModernText.drawCenteredColored(g, PhoneSettingsStore.text("Tuş Takımı", "Keypad"), x(232), y(554), size(6), tab == Tab.KEYPAD ? 0xFFFFFFFF : 0xFF7C8794);
    }

    private void drawToast(GuiGraphics g) { if (status.isBlank() || System.currentTimeMillis() > statusUntil) return; drawRoundedRect(g, x(48), y(88), x(252), y(117), size(12), 0xEE26323F); PhoneModernText.drawCenteredColored(g, status, x(150), y(97), size(7), 0xFFFFFFFF); }
    private void call(String uuid) { if (uuid == null || uuid.isBlank() || !PhoneClientData.isContact(uuid)) { toast(PhoneSettingsStore.text("Önce bu oyuncuyu ekle", "Add this player first")); return; } if (PhoneClientData.isBlocked(uuid)) { toast(PhoneSettingsStore.text("Bu oyuncu engelli", "This player is blocked")); return; } if (!PhoneClientData.voiceCallingAvailable()) { toast(PhoneSettingsStore.text("Simple Voice Chat kullanılamıyor", "Simple Voice Chat unavailable")); return; } PhoneClientData.startCall(uuid); }
    private void toast(String value) { status=value; statusUntil=System.currentTimeMillis()+2200L; }
    private String directionSymbol(String direction) { return "OUTGOING".equals(direction) ? "↗" : "↙"; }
    private String resultLabel(String result) { return switch (result == null ? "" : result) { case "MISSED" -> PhoneSettingsStore.text("Cevapsız", "Missed"); case "DECLINED" -> PhoneSettingsStore.text("Reddedildi", "Declined"); case "NO_ANSWER" -> PhoneSettingsStore.text("Yanıt yok", "No answer"); case "BUSY" -> PhoneSettingsStore.text("Meşgul", "Busy"); case "OFFLINE" -> PhoneSettingsStore.text("Çevrimdışı", "Offline"); case "CANCELLED" -> PhoneSettingsStore.text("İptal edildi", "Cancelled"); case "INTERRUPTED" -> PhoneSettingsStore.text("Kesildi", "Interrupted"); default -> PhoneSettingsStore.text("Tamamlandı", "Completed"); }; }
    private int resultColor(String result) { return ("MISSED".equals(result)||"DECLINED".equals(result)||"NO_ANSWER".equals(result)) ? 0xFFFF6B6B : 0xFF7E8996; }
    private void drawHandset(GuiGraphics g,int cx,int cy,int color){int s=Math.max(1,size(1.2F));g.fill(cx-7*s,cy-2*s,cx-3*s,cy+3*s,color);g.fill(cx-4*s,cy-5*s,cx,cy,color);g.fill(cx,cy-5*s,cx+4*s,cy,color);g.fill(cx+3*s,cy-2*s,cx+7*s,cy+3*s,color);}
    private boolean insideCircle(double mx,double my,int cx,int cy,int radius){double dx=mx-x(cx),dy=my-y(cy);int r=size(radius);return dx*dx+dy*dy<=(double)r*r;}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (inside(mouseX,mouseY,26,540,108,578)){tab=Tab.RECENTS;scroll=0;return true;}
        if (inside(mouseX,mouseY,108,540,192,578)){tab=Tab.CONTACTS;scroll=0;return true;}
        if (inside(mouseX,mouseY,192,540,274,578)){tab=Tab.KEYPAD;scroll=0;return true;}
        if(tab==Tab.RECENTS){
            List<PhoneStateData.CallEntry> list=PhoneClientData.callHistory();int count=Math.min(7,Math.max(0,list.size()-scroll));
            for(int i=0;i<count;i++){int top=103+i*59;if(inside(mouseX,mouseY,245,top+5,288,top+52)){call(list.get(scroll+i).peerUuid);return true;}}
        } else if(tab==Tab.CONTACTS){
            List<PhoneStateData.PlayerEntry> list=PhoneClientData.sortedContacts();int count=Math.min(7,Math.max(0,list.size()-scroll));
            for(int i=0;i<count;i++){int top=103+i*59;if(inside(mouseX,mouseY,245,top+5,288,top+52)){call(list.get(scroll+i).uuid);return true;}}
        } else {
            for(int i=0;i<12;i++){int col=i%3,row=i/3,cx=79+col*71,cy=206+row*70;if(insideCircle(mouseX,mouseY,cx,cy,29)){String v=switch(i){case 0->"1";case 1->"2";case 2->"3";case 3->"4";case 4->"5";case 5->"6";case 6->"7";case 7->"8";case 8->"9";case 9->"*";case 10->"0";default->"#";};if(Character.isDigit(v.charAt(0))&&digits.length()<4)digits.append(v);return true;}}
            if(insideCircle(mouseX,mouseY,150,486,31)){PhoneStateData.PlayerEntry match=PhoneClientData.contactByNumber(digits.toString());if(match==null)toast("Number is not in contacts");else call(match.uuid);return true;}
            if(digits.length()>0&&insideCircle(mouseX,mouseY,220,486,22)){digits.deleteCharAt(digits.length()-1);return true;}
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX,double mouseY,double delta){if((tab==Tab.RECENTS||tab==Tab.CONTACTS)&&inside(mouseX,mouseY,10,95,290,530)&&delta!=0){int total=tab==Tab.RECENTS?PhoneClientData.callHistory().size():PhoneClientData.sortedContacts().size();int max=Math.max(0,total-7);scroll=delta<0?Math.min(max,scroll+1):Math.max(0,scroll-1);return true;}return super.mouseScrolled(mouseX,mouseY,delta);}

    @Override
    public boolean keyPressed(int keyCode,int scanCode,int modifiers){if(keyCode==GLFW.GLFW_KEY_ESCAPE){PhoneNavigationState.backOrHome(minecraft);return true;}if(tab==Tab.KEYPAD&&keyCode==GLFW.GLFW_KEY_BACKSPACE&&digits.length()>0){digits.deleteCharAt(digits.length()-1);return true;}return super.keyPressed(keyCode,scanCode,modifiers);}
}

package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.notes.PhoneNotesStore;
import com.mikasrevs.newphone.client.notes.PhoneNotesStore.NoteEntry;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ZPhoneNotesScreen extends AbstractZPhoneScreen {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HH:mm");
    private final StringBuilder search=new StringBuilder();
    private boolean searchFocused;
    private int scroll;

    public ZPhoneNotesScreen(){super(Component.literal(PhoneSettingsStore.text("Notlar", "Notes")));}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        renderBackground(g);drawWallpaper(g);drawRoundedRect(g,x(3),y(40),x(297),y(606),size(22),0xD90A0D12);drawStatusBar(g);
        PhoneModernText.drawColored(g,PhoneSettingsStore.text("Notlar", "Notes"),x(22),y(57),size(23),0xFFFFFFFF);
        drawRoundedRect(g,x(22),y(91),x(278),y(124),size(12),searchFocused?0xE0273446:0xC01A2534);PhoneModernText.drawColored(g,"⌕",x(34),y(99),size(13),0xFF9EABB9);
        String shown=search.length()==0?PhoneSettingsStore.text("Notlarda ara", "Search notes"):search.toString();PhoneModernText.drawColored(g,shown,x(57),y(101),size(9),search.length()==0?0xFF8C98A6:0xFFFFFFFF);
        List<NoteEntry> notes=filtered();int maxScroll=Math.max(0,notes.size()-6);scroll=Math.min(scroll,maxScroll);
        for(int row=0;row<6;row++){int index=scroll+row;if(index>=notes.size())break;NoteEntry note=notes.get(index);int top=137+row*67;boolean hover=inside(mouseX,mouseY,20,top,280,top+58);drawRoundedRect(g,x(20),y(top),x(280),y(top+58),size(13),hover?0xE5233348:0xC9162332);drawRoundedRect(g,x(30),y(top+10),x(66),y(top+46),size(9),0xFFFFC94A);PhoneModernText.drawCenteredColored(g,"N",x(48),y(top+20),size(13),0xFF3A2A00);PhoneModernText.drawColored(g,fit(note.title(),27),x(77),y(top+9),size(10),0xFFFFFFFF);String preview=note.body().replace('\n',' ').strip();if(preview.isBlank())preview=PhoneSettingsStore.text("Boş not", "Empty note");PhoneModernText.drawColored(g,fit(preview,34),x(77),y(top+29),size(7),0xFFAAB5C2);String time=TIME.format(Instant.ofEpochMilli(note.updatedAt()).atZone(ZoneId.systemDefault()));PhoneModernText.drawColored(g,time,x(239),y(top+10),size(6),0xFF8290A0);}
        if(notes.isEmpty()){PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Henüz not yok", "No notes yet"),x(150),y(270),size(13),0xFF9BA7B6);PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Oluşturmak için + düğmesine dokun", "Tap + to create one"),x(150),y(294),size(7),0xFF697584);}
        fillCircle(g,x(255),y(551),size(22),0xFFFFC83D);PhoneModernText.drawCenteredColored(g,"+",x(255),y(537),size(24),0xFF352900);drawHomeIndicator(g);drawFrame(g);
    }

    private List<NoteEntry> filtered(){String q=search.toString().trim().toLowerCase(Locale.ROOT);if(q.isEmpty())return PhoneNotesStore.all();List<NoteEntry> result=new ArrayList<>();for(NoteEntry n:PhoneNotesStore.all())if(n.title().toLowerCase(Locale.ROOT).contains(q)||n.body().toLowerCase(Locale.ROOT).contains(q))result.add(n);return result;}
    private String fit(String text,int max){String s=text==null?"":text.replace('\n',' ').trim();return s.length()<=max?s:s.substring(0,Math.max(1,max-1))+"…";}

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){if(button!=0)return super.mouseClicked(mouseX,mouseY,button);if(inside(mouseX,mouseY,22,91,278,124)){searchFocused=true;return true;}if(inside(mouseX,mouseY,230,525,282,579)){PhoneNavigationState.open(minecraft,this,new ZPhoneNoteEditorScreen(null));return true;}List<NoteEntry> notes=filtered();for(int row=0;row<6;row++){int index=scroll+row;if(index>=notes.size())break;int top=137+row*67;if(inside(mouseX,mouseY,20,top,280,top+58)){PhoneNavigationState.open(minecraft,this,new ZPhoneNoteEditorScreen(notes.get(index).id()));return true;}}searchFocused=false;return super.mouseClicked(mouseX,mouseY,button);}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double delta){if(inside(mouseX,mouseY,14,130,286,530)&&delta!=0){int max=Math.max(0,filtered().size()-6);scroll=delta<0?Math.min(max,scroll+1):Math.max(0,scroll-1);return true;}return super.mouseScrolled(mouseX,mouseY,delta);}
    @Override public boolean charTyped(char c,int modifiers){if(searchFocused&&c>=32&&c!=127&&search.length()<36){search.append(c);scroll=0;return true;}return super.charTyped(c,modifiers);}
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){if(keyCode==GLFW.GLFW_KEY_ESCAPE){PhoneNavigationState.backOrHome(minecraft);return true;}if(searchFocused&&keyCode==GLFW.GLFW_KEY_BACKSPACE){if(search.length()>0)search.deleteCharAt(search.length()-1);scroll=0;return true;}if(searchFocused&&keyCode==GLFW.GLFW_KEY_ENTER){searchFocused=false;return true;}return super.keyPressed(keyCode,scanCode,modifiers);}
}

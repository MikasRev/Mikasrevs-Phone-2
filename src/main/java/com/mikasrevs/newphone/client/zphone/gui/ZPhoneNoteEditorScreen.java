package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.notes.PhoneNotesStore;
import com.mikasrevs.newphone.client.notes.PhoneNotesStore.NoteEntry;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ZPhoneNoteEditorScreen extends AbstractZPhoneScreen {
    private final String noteId;
    private final StringBuilder title=new StringBuilder();
    private final StringBuilder body=new StringBuilder();
    private Field focus=Field.BODY;

    public ZPhoneNoteEditorScreen(String noteId){super(Component.literal(PhoneSettingsStore.text("Not", "Note")));this.noteId=noteId;NoteEntry note=PhoneNotesStore.find(noteId);if(note!=null){title.append(note.title());body.append(note.body());}}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        renderBackground(g);drawWallpaper(g);drawRoundedRect(g,x(3),y(40),x(297),y(606),size(22),0xF00B0E13);drawStatusBar(g);
        PhoneModernText.drawColored(g,PhoneSettingsStore.text("‹ Notlar", "‹ Notes"),x(18),y(57),size(10),0xFF71B7FF);PhoneModernText.drawCenteredColored(g,noteId==null?PhoneSettingsStore.text("Yeni Not", "New Note"):PhoneSettingsStore.text("Notu Düzenle", "Edit Note"),x(150),y(58),size(10),0xFFFFFFFF);
        drawRoundedRect(g,x(219),y(51),x(281),y(83),size(13),0xFFFFC83D);PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Kaydet", "Save"),x(250),y(62),size(8),0xFF3B2D00);
        drawRoundedRect(g,x(20),y(103),x(280),y(149),size(12),focus==Field.TITLE?0xFF24354A:0xFF182330);PhoneModernText.drawColored(g,title.length()==0?PhoneSettingsStore.text("Başlık", "Title"):fit(title.toString(),34),x(33),y(117),size(11),title.length()==0?0xFF778493:0xFFFFFFFF);
        drawRoundedRect(g,x(20),y(164),x(280),y(493),size(14),focus==Field.BODY?0xFF182635:0xFF141D27);drawBody(g);
        if(noteId!=null){drawRoundedRect(g,x(79),y(515),x(221),y(553),size(13),0xFF49252A);PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Notu Sil", "Delete Note"),x(150),y(528),size(8),0xFFFF8B91);}drawHomeIndicator(g);drawFrame(g);
    }

    private void drawBody(GuiGraphics g){String text=body.toString();if(text.isEmpty()){PhoneModernText.drawColored(g,PhoneSettingsStore.text("Bir şeyler yaz…", "Write something…"),x(34),y(180),size(8),0xFF778493);return;}int top=180;for(String line:PhoneModernText.wrap(text,size(8),size(232))){PhoneModernText.drawColored(g,line,x(34),y(top),size(8),0xFFFFFFFF);top+=17;if(top>465)return;}}
    private String fit(String value,int max){return value.length()<=max?value:value.substring(0,max-1)+"…";}
    private void save(){PhoneNotesStore.save(noteId,title.toString(),body.toString());PhoneNavigationState.backOrHome(minecraft);}

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){if(button!=0)return super.mouseClicked(mouseX,mouseY,button);if(inside(mouseX,mouseY,13,48,92,88)){PhoneNavigationState.backOrHome(minecraft);return true;}if(inside(mouseX,mouseY,219,51,281,83)){save();return true;}if(inside(mouseX,mouseY,20,103,280,149)){focus=Field.TITLE;return true;}if(inside(mouseX,mouseY,20,164,280,493)){focus=Field.BODY;return true;}if(noteId!=null&&inside(mouseX,mouseY,79,515,221,553)){PhoneNotesStore.delete(noteId);PhoneNavigationState.backOrHome(minecraft);return true;}return super.mouseClicked(mouseX,mouseY,button);}
    @Override public boolean charTyped(char c,int modifiers){if(c<32||c==127)return super.charTyped(c,modifiers);StringBuilder target=focus==Field.TITLE?title:body;int max=focus==Field.TITLE?80:1800;if(target.length()<max)target.append(c);return true;}
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){if(keyCode==GLFW.GLFW_KEY_ESCAPE){PhoneNavigationState.backOrHome(minecraft);return true;}StringBuilder target=focus==Field.TITLE?title:body;if(keyCode==GLFW.GLFW_KEY_BACKSPACE){if(target.length()>0)target.deleteCharAt(target.length()-1);return true;}if(keyCode==GLFW.GLFW_KEY_ENTER){if(focus==Field.TITLE){focus=Field.BODY;}else if(body.length()<1800){body.append('\n');}return true;}return super.keyPressed(keyCode,scanCode,modifiers);}
    private enum Field{TITLE,BODY}
}

package com.mikasrevs.newphone.client.duo;

import com.mikasrevs.newphone.client.gui.PhoneTimeDisplay;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Duo presentation only; unlocking and PIN checks remain in the shared MPhone session. */
public final class DuoLockScreen extends AbstractZPhoneScreen {
    private double swipeY;
    private boolean swiping;
    public DuoLockScreen(){super(Component.literal("iPhone Duo Lock"));}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);drawWallpaper(g);drawStatusBar(g);var s=DuoInterface.layout(width,height);
        DuoInterface.text(g,s,"iPhone Duo",43,80,14,246,DuoInterface.MUTED);
        DuoInterface.text(g,s,PhoneTimeDisplay.time(false),40,131,55,272,DuoInterface.INK);
        String date=PhoneSettingsStore.useMinecraftTime()?PhoneTimeDisplay.dayLabel():LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM",PhoneSettingsStore.locale()));
        DuoInterface.text(g,s,date,44,207,11,260,DuoInterface.INK);
        DuoInterface.round(g,s,42,283,237,85,22,DuoInterface.CARD);
        DuoInterface.text(g,s,DuoInterface.tr("Kilitli","Locked"),61,304,16,200,DuoInterface.INK);
        DuoInterface.text(g,s,DuoInterface.tr("Yukarı kaydır veya BOŞLUK","Swipe up or press SPACE"),61,337,9,203,DuoInterface.MUTED);
        DuoInterface.text(g,s,DuoInterface.tr("BİLDİRİMLER","NOTIFICATIONS"),342,109,9,254,DuoInterface.MUTED);
        DuoInterface.notices(g,s,342,139,5,true);
        DuoInterface.round(g,s,42,493,237,63,23,0xB697AED7);
        DuoInterface.text(g,s,DuoInterface.tr("Kilidi aç  ↑","Unlock  ↑"),72,516,13,182,DuoInterface.INK);drawFrame(g);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(super.mouseClicked(mx,my,button))return true;
        if(button==0){var s=DuoInterface.layout(width,height);if(s.inside(mx,my,DuoLayout.UNLOCK)){ZPhoneClient.finishLockUnlock();return true;}if(mx>=s.x(0)&&mx<=s.x(620)&&my>=s.y(0)&&my<=s.y(610)){swiping=true;swipeY=my;}}return true;
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return swiping||super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(swiping){swiping=false;if(swipeY-my>=DuoInterface.layout(width,height).size(55))ZPhoneClient.finishLockUnlock();return true;}return super.mouseReleased(mx,my,button);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_SPACE||key==GLFW.GLFW_KEY_ENTER){ZPhoneClient.finishLockUnlock();return true;}return super.keyPressed(key,scan,modifiers);}
}

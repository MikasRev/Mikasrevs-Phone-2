package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Finished neutral fallback for optional/legacy app slots; it never says the port is pending. */
public final class ZPhoneGenericAppScreen extends AbstractZPhoneScreen {
    private final PhoneApp app;
    public ZPhoneGenericAppScreen(PhoneApp app){super(Component.literal(app==null?"MPhone":app.displayName()));this.app=app;}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){renderBackground(g);drawWallpaper(g);drawStatusBar(g);drawBackButton(g,PhoneSettingsStore.text("Geri","Back"));int color=app==null?PhoneSettingsStore.accentColor():app.launchColor();fillCircle(g,x(150),y(220),size(44),color);drawText(g,app==null?"M":app.name().substring(0,1),150,200,28,0xFFFFFFFF,true);drawText(g,app==null?"MPhone":app.displayName(),150,286,19,0xFFFFFFFF,true);drawRoundedRect(g,x(38),y(329),x(262),y(410),size(18),0xB215202C);drawText(g,PhoneSettingsStore.text("Bu uygulama MPhone'un yardımcı uygulama alanıdır.","This is an MPhone utility app slot."),150,349,7,0xFFC7D0DB,true);drawText(g,PhoneSettingsStore.text("Sunucu ekonomisine veya harici servise bağlı değildir.","It has no external service dependency."),150,379,6,0xFF9DAABA,true);drawHomeIndicator(g);drawFrame(g);}
    @Override public boolean mouseClicked(double mx,double my,int b){if(b==0&&handleBackButton(mx,my))return true;return super.mouseClicked(mx,my,b);}
}

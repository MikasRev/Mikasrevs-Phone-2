package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.moderation.PhoneModerationClient;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** No content/actions from the restricted app are constructed on this screen. */
public final class ZPhoneAppAccessScreen extends AbstractZPhoneScreen {
    private final String platform;
    private int ticks;
    public ZPhoneAppAccessScreen(String platform){super(Component.literal(PhoneSettingsStore.text("Erişim", "Access")));this.platform=platform;}
    @Override protected void init(){super.init();PhoneModerationClient.request();}
    @Override public void tick(){
        super.tick();
        if(++ticks%100==0)PhoneModerationClient.request();
        if(!PhoneModerationClient.ready())return;
        if(platform.equals("ADMIN_PANEL")){if(PhoneModerationClient.canManage())ZPhoneClient.openSharedApp(PhoneApp.ADMIN_PANEL,this);return;}
        if(!PhoneModerationClient.banned(platform))ZPhoneClient.openSharedApp(switch(platform){case "BLOCKPOST"->PhoneApp.BLOCKPOST;case "BLOCKGRAM"->PhoneApp.BLOCKGRAM;default->PhoneApp.BROWSER;},this);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        renderBackground(g);g.fill(x(0),y(0),x(300),y(610),0xFF10141D);drawStatusBar(g);
        drawRoundedRect(g,x(28),y(169),x(272),y(383),size(18),0xFF242A36);
        drawText(g,"!",150,197,42,0xFFFF6B72,true);
        boolean loading=!PhoneModerationClient.ready();
        drawText(g,loading?PhoneSettingsStore.text("Yetki kontrol ediliyor", "Checking access"):
                platform.equals("ADMIN_PANEL")?PhoneSettingsStore.text("Yetkin yok", "Access denied"):
                        PhoneSettingsStore.text("Bu uygulamadan yasaklandın", "You are banned"),150,272,12,0xFFFFFFFF,true);
        if(!loading)drawText(g,PhoneSettingsStore.text("Bir yöneticiyle görüş.", "Talk to an admin."),150,312,10,0xFFACB7CA,true);
        drawRoundedRect(g,x(55),y(423),x(245),y(466),size(12),0xFF3866C7);
        drawText(g,PhoneSettingsStore.text("Ana ekrana dön", "Back to home"),150,437,10,0xFFFFFFFF,true);
        drawHomeIndicator(g);drawFrame(g);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0&&inside(mx,my,55,423,245,466)){PhoneNavigationState.goHome(minecraft);return true;}return super.mouseClicked(mx,my,button);}
}

package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Full weather page driven by the current Minecraft world's weather/time. */
public final class ZPhoneWeatherScreen extends AbstractZPhoneScreen {
    private static final ResourceLocation ICONS = new ResourceLocation(NewPhone.MOD_ID,"textures/gui/phone/weather_icons.png");
    private static final int CELL=64;
    public ZPhoneWeatherScreen(){super(Component.literal(PhoneSettingsStore.text("Hava Durumu", "Weather")));}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        renderBackground(g);drawWallpaper(g);drawStatusBar(g);drawBackButton(g,PhoneSettingsStore.text("Geri","Back"));
        WeatherState w=state();Locale locale=PhoneSettingsStore.locale();String date=LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM",locale));
        drawText(g,PhoneSettingsStore.text("Hava Durumu","Weather"),26,91,19,0xFFFFFFFF,false);drawText(g,date,26,121,7,0xFFAFBBC8,false);
        drawRoundedRect(g,x(20),y(151),x(280),y(340),size(24),w.cardColor);fillCircle(g,x(245),y(195),size(76),w.glowColor);
        g.blit(ICONS,x(40),y(179),size(72),size(72),(float)(w.iconIndex*CELL),0.0F,CELL,CELL,256,64);
        drawText(g,w.temperature,137,183,34,0xFFFFFFFF,false);com.mikasrevs.newphone.client.gui.PhoneModernText.drawColoredFitted(g,w.localized(),x(139),y(228),size(12),size(115),0xFFFFFFFF);drawText(g,w.range,139,257,8,0xFFD7E0EA,false);
        drawText(g,PhoneSettingsStore.text("Minecraft Dünyası","Minecraft World"),40,298,8,0xFFB8C4D0,false);drawText(g,minecraftClock(),238,298,8,0xFFFFFFFF,true);
        info(g,20,364,123,434,PhoneSettingsStore.text("Yağış","Rain"),minecraft!=null&&minecraft.level!=null&&minecraft.level.isRaining()?PhoneSettingsStore.text("Var","Yes"):PhoneSettingsStore.text("Yok","No"));
        info(g,133,364,280,434,PhoneSettingsStore.text("Gök Gürültüsü","Thunder"),minecraft!=null&&minecraft.level!=null&&minecraft.level.isThundering()?PhoneSettingsStore.text("Var","Yes"):PhoneSettingsStore.text("Yok","No"));
        drawRoundedRect(g,x(20),y(454),x(280),y(548),size(18),0xC2172433);drawText(g,PhoneSettingsStore.text("Dünya tahmini","World forecast"),35,470,8,0xFF8FA0B1,false);drawText(g,forecastText(),35,494,11,0xFFFFFFFF,false);drawText(g,PhoneSettingsStore.text("Hava bilgisi gerçek dünya API'si kullanmaz.","Weather follows the Minecraft world only."),35,522,6,0xFF96A5B4,false);
        drawHomeIndicator(g);drawFrame(g);
    }
    private void info(GuiGraphics g,int l,int t,int r,int b,String title,String value){drawRoundedRect(g,x(l),y(t),x(r),y(b),size(16),0xC2172433);drawText(g,title,l+14,t+14,7,0xFF8FA0B1,false);drawText(g,value,(l+r)/2.0F,t+38,12,0xFFFFFFFF,true);}
    private String forecastText(){if(minecraft==null||minecraft.level==null)return PhoneSettingsStore.text("Dünya yükleniyor","World loading");if(minecraft.level.isThundering())return PhoneSettingsStore.text("Fırtına devam ediyor","Storm in progress");if(minecraft.level.isRaining())return PhoneSettingsStore.text("Yağmur devam ediyor","Rain in progress");return PhoneSettingsStore.text("Gökyüzü açık","Clear skies");}
    private WeatherState state(){if(minecraft==null||minecraft.level==null)return WeatherState.CLEAR;if(minecraft.level.isThundering())return WeatherState.THUNDER;if(minecraft.level.isRaining())return WeatherState.RAIN;return WeatherState.CLEAR;}
    private String minecraftClock(){long t=minecraft==null||minecraft.level==null?0L:Math.floorMod(minecraft.level.getDayTime(),24000L);long h=Math.floorMod(t/1000L+6L,24L),m=(t%1000L)*60L/1000L;return String.format(Locale.ROOT,"%02d:%02d",h,m);}
    @Override public boolean mouseClicked(double mx,double my,int b){if(b==0&&handleBackButton(mx,my))return true;return super.mouseClicked(mx,my,b);}
    @Override public boolean keyPressed(int k,int s,int m){if(k==GLFW.GLFW_KEY_ESCAPE){PhoneNavigationState.backOrHome(minecraft);return true;}return super.keyPressed(k,s,m);}
    private enum WeatherState{CLEAR(0,"18°","↑ 24°   ↓ 12°",0xFF27486A,0x3788D2F7),RAIN(2,"14°","↑ 16°   ↓ 9°",0xFF284158,0x365A87B8),THUNDER(3,"12°","↑ 14°   ↓ 8°",0xFF443C64,0x308A68D4);final int iconIndex;final String temperature,range;final int cardColor,glowColor;WeatherState(int i,String temp,String range,int card,int glow){this.iconIndex=i;this.temperature=temp;this.range=range;this.cardColor=card;this.glowColor=glow;}String localized(){return switch(this){case CLEAR->PhoneSettingsStore.text("Açık","Clear","Despejado");case RAIN->PhoneSettingsStore.text("Yağmurlu","Rain","Lluvioso");case THUNDER->PhoneSettingsStore.text("Fırtınalı","Storm","Tormenta");};}}
}

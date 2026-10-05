package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.calendar.PhoneCalendarStore;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ZPhoneCalendarScreen extends AbstractZPhoneScreen {
    private int displayedMonth=-1,selectedDay=-1;
    public ZPhoneCalendarScreen(){super(Component.translatable("screen.newphone.app.calendar"));}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        renderBackground(g);drawWallpaper(g);drawStatusBar(g);long current=minecraftDay();int currentMonth=monthFor(current),currentDay=dayOfMonth(current);if(displayedMonth<1)displayedMonth=currentMonth;if(selectedDay<1)selectedDay=displayedMonth==currentMonth?currentDay:1;
        drawText(g,PhoneSettingsStore.text("Takvim","Calendar"),22,58,20,0xFFFFFFFF,false);drawText(g,PhoneSettingsStore.text("Minecraft Ayı ","Minecraft Month ")+displayedMonth,22,91,9,0xFFB7C2CE,false);
        drawRoundedRect(g,x(226),y(83),x(250),y(107),size(10),0x552F4055);drawRoundedRect(g,x(256),y(83),x(280),y(107),size(10),0x552F4055);drawText(g,"‹",238,85,14,0xFFFFFFFF,true);drawText(g,"›",268,85,14,0xFFFFFFFF,true);
        for(int col=0;col<7;col++)drawText(g,java.time.DayOfWeek.of(col+1).getDisplayName(java.time.format.TextStyle.SHORT,PhoneSettingsStore.locale()),37+col*38,126,6,0xFF8895A4,true);
        for(int day=1;day<=30;day++){int z=day-1,row=z/7,col=z%7,cx=37+col*38,cy=159+row*52;long abs=absoluteDay(displayedMonth,day);if(day==selectedDay)fillCircle(g,x(cx),y(cy),size(15),0xFFFF3B30);else if(displayedMonth==currentMonth&&day==currentDay)fillCircle(g,x(cx),y(cy),size(15),0xFF30445D);drawText(g,Integer.toString(day),cx,cy-7,9,0xFFFFFFFF,true);if(PhoneCalendarStore.hasReminder(abs))fillCircle(g,x(cx),y(cy+16),size(2),0xFFFFC94A);}
        long selected=absoluteDay(displayedMonth,selectedDay);drawRoundedRect(g,x(20),y(430),x(280),y(499),size(15),0xB91B2736);drawText(g,PhoneSettingsStore.text("Seçili","Selected"),34,444,7,0xFF8D9AAA,false);drawText(g,PhoneSettingsStore.text("Minecraft Günü ","Minecraft Day ")+selected,34,460,13,0xFFFFFFFF,false);
        boolean reminder=PhoneCalendarStore.hasReminder(selected);drawRoundedRect(g,x(20),y(513),x(280),y(552),size(14),reminder?0xFF7C3C45:0xFFFF3B30);drawText(g,PhoneSettingsStore.text(reminder?"Hatırlatıcıyı Kaldır":"Hatırlatıcı Ekle",reminder?"Remove Reminder":"Add Reminder",reminder?"Eliminar recordatorio":"Añadir recordatorio"),150,526,9,0xFFFFFFFF,true);drawText(g,PhoneSettingsStore.text("Bugün: Gün ","Today: Day ")+current+"  •  "+minecraftTime(),24,562,7,0xFF98A5B4,false);
        drawHomeIndicator(g);drawFrame(g);
    }
    @Override public boolean mouseClicked(double mx,double my,int b){if(b==0){if(inside(mx,my,226,83,250,107)){displayedMonth=Math.max(1,displayedMonth-1);selectedDay=1;return true;}if(inside(mx,my,256,83,280,107)){displayedMonth++;selectedDay=1;return true;}for(int day=1;day<=30;day++){int z=day-1,row=z/7,col=z%7,cx=37+col*38,cy=159+row*52;if(inside(mx,my,cx-16,cy-16,cx+16,cy+18)){selectedDay=day;return true;}}if(inside(mx,my,20,513,280,552)){PhoneCalendarStore.toggleReminder(absoluteDay(displayedMonth,selectedDay));return true;}}return super.mouseClicked(mx,my,b);}
    @Override public boolean keyPressed(int k,int s,int m){if(k==GLFW.GLFW_KEY_LEFT){displayedMonth=Math.max(1,displayedMonth-1);selectedDay=1;return true;}if(k==GLFW.GLFW_KEY_RIGHT){displayedMonth++;selectedDay=1;return true;}if(k==GLFW.GLFW_KEY_ESCAPE){PhoneNavigationState.backOrHome(minecraft);return true;}return super.keyPressed(k,s,m);}
    private long minecraftDay(){return minecraft==null||minecraft.level==null?1L:Math.floorDiv(minecraft.level.getDayTime(),24000L)+1L;}private String minecraftTime(){long t=minecraft==null||minecraft.level==null?0L:Math.floorMod(minecraft.level.getDayTime(),24000L);long h=Math.floorMod(t/1000L+6L,24L),m=(t%1000L)*60L/1000L;return String.format(java.util.Locale.ROOT,"%02d:%02d",h,m);}private static int monthFor(long d){return(int)((Math.max(1,d)-1)/30)+1;}private static int dayOfMonth(long d){return(int)((Math.max(1,d)-1)%30)+1;}private static long absoluteDay(int m,int d){return(Math.max(1,m)-1L)*30L+Math.max(1,Math.min(30,d));}
}

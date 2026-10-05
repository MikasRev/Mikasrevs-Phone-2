package com.mikasrevs.newphone.client.duo;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.*;
import com.mikasrevs.newphone.client.settings.PhoneAppLayoutStore;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.zphone.gui.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A wide, paged launcher. App selection still goes through MPhone's existing permission/router code. */
public final class DuoHomeScreen extends AbstractZPhoneScreen {
    private static final int PAGE_SIZE=DuoLayout.APPS_PER_PAGE;
    private static final List<PhoneApp> DOCK=List.of(PhoneApp.PHONE,PhoneApp.MESSAGES,PhoneApp.GALLERY,PhoneApp.MARKETPLACE);
    private int page;
    private PhoneApp launching;
    private long launchAt;
    public DuoHomeScreen(){this(0);}
    public DuoHomeScreen(int page){super(Component.literal("iPhone Duo"));this.page=Math.max(0,page);}
    public int currentPage(){return page;}
    private List<PhoneApp> apps(){var all=new LinkedHashSet<>(PhoneAppLayoutStore.dockApps());all.addAll(PhoneAppLayoutStore.visibleHomeApps());return List.copyOf(all);}
    @Override public void tick(){super.tick();if(launching!=null&&System.currentTimeMillis()-launchAt>=(com.mikasrevs.newphone.client.settings.PhoneSettingsStore.animationsEnabled()?160:0)){var app=launching;launching=null;ZPhoneClient.openSharedApp(app,this);}}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);drawWallpaper(g);drawStatusBar(g);var s=DuoInterface.layout(width,height);
        DuoInterface.text(g,s,"iPhone Duo",31,66,27,340,DuoInterface.INK);
        String date=com.mikasrevs.newphone.client.settings.PhoneSettingsStore.useMinecraftTime()?PhoneTimeDisplay.dayLabel():java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM",com.mikasrevs.newphone.client.settings.PhoneSettingsStore.locale()));
        DuoInterface.text(g,s,date,32,106,9,260,DuoInterface.MUTED);
        DuoInterface.round(g,s,366,65,219,70,21,DuoInterface.CARD);
        DuoInterface.text(g,s,DuoInterface.tr("Bildirimler","Notifications"),384,82,10,180,DuoInterface.MUTED);
        DuoInterface.text(g,s,Integer.toString(totalAttentionCount()),384,103,17,180,DuoInterface.INK);
        List<PhoneApp> apps=apps();page=Math.min(page,Math.max(0,(apps.size()-1)/PAGE_SIZE));
        for(int i=0;i<Math.min(PAGE_SIZE,apps.size()-page*PAGE_SIZE);i++)drawIcon(g,s,apps.get(page*PAGE_SIZE+i),DuoLayout.appIcon(i),mx,my);
        DuoInterface.round(g,s,106,493,408,87,27,0xA6B6C7EC);
        for(int i=0;i<DOCK.size();i++)drawIcon(g,s,DOCK.get(i),DuoLayout.dockIcon(i),mx,my);
        int pages=Math.max(1,(apps.size()+PAGE_SIZE-1)/PAGE_SIZE);
        for(int i=0;i<pages;i++)DuoInterface.round(g,s,310-(pages*14)/2+i*14,478,6,6,3,i==page?DuoInterface.INK:0x6696A3BD);
        if(launching!=null&&com.mikasrevs.newphone.client.settings.PhoneSettingsStore.animationsEnabled())DuoInterface.round(g,s,20,148,580,321,23,((int)(DuoAnimation.ease((System.currentTimeMillis()-launchAt)/160F)*60)<<24)|0xD9E4FF);
        drawFrame(g);
    }
    private void drawIcon(GuiGraphics g,DuoLayout.Surface s,PhoneApp app,DuoLayout.Region region,int mx,int my){
        boolean hover=s.inside(mx,my,region);int shift=hover?-2:0;
        MPhoneAppIconRenderer.draw(g,app,s.x(region.x()),s.y(region.y()+shift),s.size(region.width()),hover);
        DuoInterface.text(g,s,app.displayName(),region.x()-9,region.y()+65,8,region.width()+18,DuoInterface.INK);
        int unread=app==PhoneApp.MESSAGES?PhoneClientData.sortedMessagePeers().stream().mapToInt(p->PhoneClientData.unreadCount(p.uuid)).sum():app==PhoneApp.MARKETPLACE?com.mikasrevs.newphone.marketplace.client.MarketplaceClient.unread():0;
        if(unread>0){DuoInterface.round(g,s,region.x()+43,region.y()-4,22,19,9,0xFFFF536E);DuoInterface.text(g,s,unread>99?"99+":Integer.toString(unread),region.x()+47,region.y()+1,7,15,DuoInterface.INK);}
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(super.mouseClicked(mx,my,button))return true;if(button!=0||launching!=null)return true;
        var s=DuoInterface.layout(width,height);var apps=apps();
        if(s.inside(mx,my,DuoLayout.HOME_NOTIFICATIONS)){com.mikasrevs.newphone.client.zphone.PhoneNavigationState.open(minecraft,this,new ZPhoneNotificationCenterScreen());return true;}
        for(int i=0;i<Math.min(PAGE_SIZE,apps.size()-page*PAGE_SIZE);i++)if(s.inside(mx,my,DuoLayout.appIcon(i))){launch(apps.get(page*PAGE_SIZE+i));return true;}
        for(int i=0;i<DOCK.size();i++)if(s.inside(mx,my,DuoLayout.dockIcon(i))){launch(DOCK.get(i));return true;}
        return true;
    }
    private void launch(PhoneApp app){launching=app;launchAt=System.currentTimeMillis();}
    @Override public boolean mouseScrolled(double mx,double my,double delta){if(launching==null)page=Math.max(0,Math.min(Math.max(0,(apps().size()-1)/PAGE_SIZE),page+(delta<0?1:-1)));return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_RIGHT||key==GLFW.GLFW_KEY_LEFT)return mouseScrolled(0,0,key==GLFW.GLFW_KEY_RIGHT?-1:1);return super.keyPressed(key,scan,modifiers);}
}

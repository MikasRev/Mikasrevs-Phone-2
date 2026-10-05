package com.mikasrevs.newphone.client.duo;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.*;
import com.mikasrevs.newphone.client.settings.*;
import com.mikasrevs.newphone.client.zphone.*;
import com.mikasrevs.newphone.client.zphone.gui.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Physical shell and companion screen. Existing apps continue to render and handle input themselves. */
public final class DuoInterface {
    public static final int INK=0xFFF4F6FC,MUTED=0xFFA8B1C1,CARD=0xBD222D42,ACCENT=0xFFB8CCFF;
    private DuoInterface(){}
    public static String tr(String a,String b){return PhoneSettingsStore.text(a,b);}
    public static DuoLayout.Surface layout(int width,int height){return DuoLayout.fit(width,height,PhoneSettingsStore.phoneScale());}
    public static boolean wide(AbstractZPhoneScreen screen){return screen instanceof DuoHomeScreen||screen instanceof DuoLockScreen||screen instanceof DuoTransitionScreen;}
    public static boolean locked(AbstractZPhoneScreen screen){return screen instanceof DuoLockScreen||screen instanceof AbstractZPhonePinScreen||screen instanceof ZPhoneSetupScreen||!ZPhoneClient.isSessionUnlocked();}
    public static void text(GuiGraphics g,DuoLayout.Surface s,String value,int x,int y,int height,int width,int color){g.pose().pushPose();g.pose().translate(0,0,1);try{PhoneModernText.drawColoredFitted(g,value,s.x(x),s.y(y),s.size(height),s.size(width),color);}finally{g.pose().popPose();}}
    public static void round(GuiGraphics g,DuoLayout.Surface s,int x,int y,int width,int height,int radius,int color){
        int left=s.x(x),top=s.y(y),right=s.x(x+width),bottom=s.y(y+height),r=Math.max(1,s.size(radius));
        for(int py=top;py<bottom;py++){int dy=py<top+r?top+r-py:py>=bottom-r?py-(bottom-r-1):0;int inset=dy==0?0:r-(int)Math.sqrt(Math.max(0,r*r-dy*dy));g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),left+inset,py,right-inset,py+1,color);}
    }
    public static void device(GuiGraphics g,DuoLayout.Surface s){
        var mc=Minecraft.getInstance();
        var pose=g.pose();g.flush();float open=DuoAnimation.unfold(),closed=1-open;
        if(mc.player!=null&&mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer renderer){
            for(boolean right:new boolean[]{false,true}){
                pose.pushPose();pose.translate(s.x(Math.round(right?591-130*closed:29+158*closed)),s.y(599),-85);pose.scale(s.size(160),s.size(160),-s.size(160));pose.mulPose(Axis.ZP.rotationDegrees(right?-14:14));
                if(right)renderer.renderRightHand(pose,g.bufferSource(),LightTexture.FULL_BRIGHT,mc.player);else renderer.renderLeftHand(pose,g.bufferSource(),LightTexture.FULL_BRIGHT,mc.player);
                pose.popPose();
            }
        }
        pose.pushPose();pose.translate(s.x(310+Math.round(158*closed)),s.y(305),-40);pose.scale(s.scale(),s.scale(),s.scale());
        DuoMesh.render(pose,g.bufferSource(),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,open);pose.popPose();g.flush();
    }
    public static void wallpaper(GuiGraphics g,DuoLayout.Surface s,boolean wide,boolean lock){
        int width=wide?DuoLayout.WIDTH:300;
        // Use the existing player-selected wallpaper/photo. A neutral metallic scrim creates Duo's identity.
        var mc=Minecraft.getInstance();var photo=PhoneSettingsStore.wallpaperPhoto(lock);
        var texture=photo==null?null:PhoneGalleryStore.textureFor(mc,photo);
        if(texture!=null)PhoneWallpaperRenderer.blitCover(g,texture.location(),s.x(0),s.y(0),s.x(width),s.y(610),texture.width(),texture.height());
        else PhoneWallpaperRenderer.blitCover(g,PhoneWallpaperCatalog.texture(PhoneSettingsStore.wallpaperIndex(lock)),s.x(0),s.y(0),s.x(width),s.y(610),PhoneWallpaperCatalog.TEXTURE_WIDTH,PhoneWallpaperCatalog.TEXTURE_HEIGHT);
        g.fill(s.x(0),s.y(0),s.x(width),s.y(610),0x88202B41);
        if(wide)g.fill(s.x(302),s.y(0),s.x(318),s.y(610),0xAA070B10);
    }
    public static void status(GuiGraphics g,DuoLayout.Surface s){
        text(g,s,PhoneTimeDisplay.time(false),20,17,9,72,INK);
        round(g,s,118,12,64,23,11,0xFF0A0D15);
        String active=PhoneClientData.hasCall()?tr("Arama","Call"):PhoneSettingsStore.doNotDisturb()?"DND":"●";
        text(g,s,active,130,18,7,44,PhoneClientData.hasCall()?0xFF7CE1A3:MUTED);
        text(g,s,PhoneSettingsStore.airplaneMode()?tr("Uçuş","Airplane"):PhoneSettingsStore.wifiEnabled()?"Wi-Fi":"···",231,18,7,53,INK);
    }
    public static void frame(GuiGraphics g,DuoLayout.Surface s,AbstractZPhoneScreen screen){
        // Apps may flush clipped content or elevated popups; keep the device controls in one final layer.
        g.flush();g.pose().pushPose();g.pose().translate(0,0,300);
        try{
        if(!wide(screen))companion(g,s,screen);
        round(g,s,262,596,96,4,2,0xE6FFFFFF);
        text(g,s,"⌄",574,18,13,25,MUTED);
        var notice=DuoNotifications.current();
        if(notice!=null){round(g,s,338,48,264,59,17,0xF231405B);text(g,s,notice.title(),353,59,10,234,INK);text(g,s,locked(screen)&&!PhonePrivacyStore.previews()?tr("Yeni bildirim","New notification"):notice.body().replace('\n',' '),353,82,8,234,MUTED);}
        float p=DuoAnimation.tapProgress();
        if(p<1&&PhoneSettingsStore.animationsEnabled()){int radius=4+Math.round(p*12);round(g,s,DuoAnimation.tapX()-radius,DuoAnimation.tapY()-radius,radius*2,radius*2,radius,((int)((1-p)*65)<<24)|0xFFFFFF);}
        float brightness=PhoneSettingsStore.brightness();int alpha=Math.round((1-Math.max(.05F,Math.min(1,brightness)))*190);
        if(alpha>0)g.fill(s.x(0),s.y(0),s.x(620),s.y(610),alpha<<24);
        }finally{g.flush();g.pose().popPose();}
    }
    private static void companion(GuiGraphics g,DuoLayout.Surface s,AbstractZPhoneScreen screen){
        g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),s.x(320),s.y(0),s.x(620),s.y(610),0xFF131B2B);
        text(g,s,"iPhone Duo",342,22,17,210,INK);
        if(locked(screen)){
            text(g,s,tr("Kilitli","Locked"),342,89,18,240,INK);
            text(g,s,tr("Devam etmek için kilidi aç.","Unlock to continue."),342,125,9,240,MUTED);return;
        }
        round(g,s,340,55,260,46,14,CARD);
        text(g,s,PhoneClientData.hasCall()?PhoneClientData.callPeerName():tr("Bildirimler","Notifications"),356,72,11,225,INK);
        notices(g,s,342,124,4,false);
        text(g,s,tr("HIZLI GEÇİŞ","QUICK SWITCH"),342,411,8,238,MUTED);
        var apps=new PhoneApp[]{PhoneApp.PHONE,PhoneApp.MESSAGES,PhoneApp.GALLERY,PhoneApp.MARKETPLACE};
        for(int i=0;i<apps.length;i++){
            var region=DuoLayout.quickSwitch(i);int x=region.x(),y=region.y();round(g,s,x,y,region.width(),region.height(),13,CARD);
            text(g,s,apps[i].displayName(),x+12,y+21,8,94,INK);
        }
    }
    public static void notices(GuiGraphics g,DuoLayout.Surface s,int x,int y,int count,boolean lock){
        var entries=PhoneNotificationFeed.entries(true);
        if(entries.isEmpty()){
            round(g,s,x,y,256,74,16,CARD);text(g,s,PhoneSettingsStore.doNotDisturb()?tr("Rahatsız Etme","Do Not Disturb"):tr("Her şey güncel","You're all caught up"),x+16,y+19,12,224,INK);
            text(g,s,tr("Bildirimlerin burada görünür.","Your notifications appear here."),x+16,y+45,8,224,MUTED);
        }
        for(int i=0;i<Math.min(count,entries.size());i++){
            var n=entries.get(i);int top=y+i*69;round(g,s,x,top,256,61,15,CARD);
            text(g,s,n.title(),x+14,top+12,10,226,INK);
            text(g,s,lock&&!PhonePrivacyStore.previews()?tr("Yeni bildirim","New notification"):n.body().replace('\n',' '),x+14,top+33,8,226,MUTED);
        }
    }
    public static boolean click(AbstractZPhoneScreen screen,int width,int height,double mx,double my,int button){
        if(button!=0)return false;var s=layout(width,height);var mc=Minecraft.getInstance();
        if(s.inside(mx,my,DuoLayout.FOLD)){DuoTransitionScreen.close(mc);return true;}
        if(wide(screen)||locked(screen))return false;
        if(s.inside(mx,my,DuoLayout.HOME)){ZPhoneClient.openHomeScreen();return true;}
        if(s.inside(mx,my,DuoLayout.NOTIFICATIONS)){
            PhoneNavigationState.open(mc,screen,PhoneClientData.hasCall()?new ZPhoneCallScreen():new ZPhoneNotificationCenterScreen());return true;
        }
        if(mx>=s.x(320)&&mx<=s.x(620)&&my>=s.y(0)&&my<=s.y(610)){
            var apps=new PhoneApp[]{PhoneApp.PHONE,PhoneApp.MESSAGES,PhoneApp.GALLERY,PhoneApp.MARKETPLACE};
            for(int i=0;i<apps.length;i++)if(s.inside(mx,my,DuoLayout.quickSwitch(i)))ZPhoneClient.openSharedApp(apps[i],screen);
            return true;
        }return false;
    }
}

package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.network.PhoneStateData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class PhoneGroupVisuals {
    static final int BG=0xFF09090B,CARD=0xFF1C1C20,TEXT=0xFFF5F5F7,MUTED=0xFF98989F,BLUE=0xFF0A84FF;
    private PhoneGroupVisuals(){}
    static void avatar(AbstractZPhoneScreen s,GuiGraphics g,PhoneStateData.GroupEntry group,int cx,int cy,int diameter){
        int radius=diameter/2;
        s.fillCircle(g,s.x(cx),s.y(cy),s.size(radius),0xFF25354D);
        var photo=group==null||group.photoId.isBlank()?null:PhonePhotoStore.textureFor(Minecraft.getInstance(),group.photoId);
        if(photo!=null){
            // Circular row clips preserve an iOS-style avatar even for rectangular gallery photos.
            int side=Math.min(photo.width(),photo.height());
            for(int yy=-radius;yy<radius;yy+=2){
                int half=(int)Math.sqrt(Math.max(0,radius*radius-(yy+1)*(yy+1)));
                if(half<=0)continue;
                g.enableScissor(s.x(cx-half),s.y(cy+yy),s.x(cx+half),s.y(cy+Math.min(radius,yy+2)));
                g.blit(photo.location(),s.x(cx-radius),s.y(cy-radius),s.size(diameter),s.size(diameter),
                    (photo.width()-side)/2f,(photo.height()-side)/2f,side,side,photo.width(),photo.height());
                g.flush();g.disableScissor();
            }
        }else{
            float scale=diameter/48f;
            s.fillCircle(g,s.x(cx-7*scale),s.y(cy-6*scale),s.size(6*scale),0xFF8ABEFF);
            s.fillCircle(g,s.x(cx+8*scale),s.y(cy-4*scale),s.size(5*scale),0xFFCEE4FF);
            s.drawRoundedRect(g,s.x(cx-17*scale),s.y(cy+3*scale),s.x(cx+3*scale),s.y(cy+13*scale),s.size(5*scale),0xFF8ABEFF);
            s.drawRoundedRect(g,s.x(cx+3*scale),s.y(cy+4*scale),s.x(cx+17*scale),s.y(cy+13*scale),s.size(5*scale),0xFFCEE4FF);
        }
    }
    static void text(AbstractZPhoneScreen s,GuiGraphics g,String text,int l,int t,int font,int width,int color){
        PhoneModernText.drawColoredFitted(g,text,s.x(l),s.y(t),s.size(font),s.size(width),color);
    }
    static void center(AbstractZPhoneScreen s,GuiGraphics g,String text,int cy,int font,int color){
        PhoneModernText.drawCenteredColoredFitted(g,text,s.x(150),s.y(cy),s.size(font),s.size(254),color);
    }
}

package com.mikasrevs.newphone.client.duo;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Incoming-call cover display backed by the existing call state and existing X-to-open behavior. */
public final class DuoCallPresentation {
    private DuoCallPresentation(){}
    public static void incoming(GuiGraphics g,int width,int height,long elapsed){
        int h=Math.min(220,Math.round(height*.78F));float scale=h/634F;int w=Math.round(324*scale);
        float t=elapsed<760?DuoAnimation.ease(elapsed/760F):elapsed>3760?1-DuoAnimation.ease((elapsed-3760)/760F):1;
        int x=width-w-9,y=Math.round(height+8-(h+14)*t);var pose=g.pose();g.flush();
        pose.pushPose();pose.translate(x+w/2F,y+h/2F,-40);pose.scale(scale,scale,scale);pose.translate(158,0,0);
        DuoMesh.render(pose,g.bufferSource(),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);pose.popPose();g.flush();
        int contentX=x+Math.round(13*scale),contentW=Math.round(298*scale);
        g.fill(contentX,y+Math.round(12*scale),contentX+contentW,y+h-Math.round(12*scale),0xFF172239);
        int center=contentX+contentW/2;
        PhoneModernText.drawCenteredColoredFitted(g,"iPhone Duo",center,y+h/9,Math.max(7,Math.round(h*.04F)),contentW-8,0xFFB5C6E2);
        int avatar=Math.max(12,h/5);PhonePlayerHeadRenderer.draw(g,PhoneClientData.callPeerUuid(),center-avatar/2,y+h/3-avatar/2,avatar);
        PhoneModernText.drawCenteredColoredFitted(g,PhoneClientData.callPeerName(),center,y+h/2,Math.max(8,h/17),contentW-8,DuoInterface.INK);
        PhoneModernText.drawCenteredColoredFitted(g,PhoneClientData.isVideoCall()?DuoInterface.tr("Görüntülü arama","Video call"):DuoInterface.tr("Gelen arama","Incoming call"),center,y+h*2/3,Math.max(6,h/28),contentW-8,DuoInterface.MUTED);
        PhoneModernText.drawCenteredColoredFitted(g,PhoneSettingsStore.text("Açmak için X","Press X to open"),center,y+h*5/6,Math.max(6,h/28),contentW-8,0xFF8DE7B1);
    }
}

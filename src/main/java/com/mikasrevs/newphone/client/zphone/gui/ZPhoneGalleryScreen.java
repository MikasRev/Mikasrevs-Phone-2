package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ZPhoneGalleryScreen extends AbstractZPhoneScreen {
    private int scrollRow;
    private java.util.function.Consumer<Path> selection;
    public ZPhoneGalleryScreen(java.util.function.Consumer<Path> selection){this();this.selection=selection;}
    public ZPhoneGalleryScreen(){super(Component.literal(PhoneSettingsStore.text("Fotoğraflar", "Photos")));}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        renderBackground(g);drawWallpaper(g);drawRoundedRect(g,x(3),y(40),x(297),y(606),size(22),0xF4070A0F);drawStatusBar(g);
        List<Path> photos=PhoneGalleryStore.photos(minecraft);PhoneModernText.drawColored(g,PhoneSettingsStore.text("Fotoğraflar", "Photos"),x(22),y(57),size(23),0xFFFFFFFF);PhoneModernText.drawColored(g,photos.size()+PhoneSettingsStore.text(" fotoğraf", " photos"),x(23),y(87),size(7),0xFF858F9C);
        drawRoundedRect(g,x(238),y(54),x(281),y(89),size(15),0xFF18212C);PhoneModernText.drawCenteredColored(g,"●",x(259),y(62),size(15),0xFF74B9FF);
        if(photos.isEmpty()){fillCircle(g,x(150),y(245),size(28),0xFF17212E);PhoneModernText.drawCenteredColored(g,"□",x(150),y(232),size(20),0xFF79BFFF);PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Henüz Fotoğraf Yok", "No Photos Yet"),x(150),y(292),size(12),0xFFFFFFFF);drawRoundedRect(g,x(94),y(337),x(206),y(374),size(14),0xFF0A84FF);PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Kamerayı Aç", "Open Camera"),x(150),y(349),size(8),0xFFFFFFFF);}else{drawGrid(g,photos,mouseX,mouseY);}drawHomeIndicator(g);drawFrame(g);
    }

    private void drawGrid(GuiGraphics g,List<Path> photos,int mouseX,int mouseY){int cols=3,cell=80,gap=7,startX=24,startY=113;int first=scrollRow*cols;int max=Math.min(photos.size(),first+15);for(int i=first;i<max;i++){int local=i-first,col=local%cols,row=local/cols,left=startX+col*(cell+gap),top=startY+row*(cell+gap);boolean hover=inside(mouseX,mouseY,left,top,left+cell,top+cell);drawRoundedRect(g,x(left-2),y(top-2),x(left+cell+2),y(top+cell+2),size(8),hover?0xFF4DA5FF:0xFF171D25);PhoneGalleryStore.GalleryTexture tex=PhoneGalleryStore.textureFor(minecraft,photos.get(i));if(tex!=null)blitTexture(g,tex.location(),left,top,cell,cell,tex.width(),tex.height());else drawRoundedRect(g,x(left),y(top),x(left+cell),y(top+cell),size(7),0xFF25303C);} }

    private void blitTexture(GuiGraphics g,net.minecraft.resources.ResourceLocation loc,int dx,int dy,int dw,int dh,int tw,int th){int src=Math.min(tw,th),sx=Math.max(0,(tw-src)/2),sy=Math.max(0,(th-src)/2);g.blit(loc,x(dx),y(dy),size(dw),size(dh),sx,sy,src,src,tw,th);}
    private int maxScroll(){int rows=(PhoneGalleryStore.photos(minecraft).size()+2)/3;return Math.max(0,rows-5);}

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){if(button!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return super.mouseClicked(mouseX,mouseY,button);if(inside(mouseX,mouseY,234,48,286,94)){PhoneNavigationState.open(minecraft,this,new ZPhoneCameraScreen(),PhoneApp.CAMERA);return true;}List<Path> photos=PhoneGalleryStore.photos(minecraft);if(photos.isEmpty()&&inside(mouseX,mouseY,94,337,206,374)){PhoneNavigationState.open(minecraft,this,new ZPhoneCameraScreen(),PhoneApp.CAMERA);return true;}int cols=3,cell=80,gap=7,startX=24,startY=113,first=scrollRow*cols,max=Math.min(photos.size(),first+15);for(int i=first;i<max;i++){int local=i-first,left=startX+(local%cols)*(cell+gap),top=startY+(local/cols)*(cell+gap);if(inside(mouseX,mouseY,left,top,left+cell,top+cell)){if(selection!=null)selection.accept(photos.get(i));else PhoneNavigationState.open(minecraft,this,new ZPhonePhotoViewerScreen(photos,i));return true;}}return super.mouseClicked(mouseX,mouseY,button);}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double delta){if(inside(mouseX,mouseY,12,105,288,570)&&delta!=0){scrollRow=delta<0?Math.min(maxScroll(),scrollRow+1):Math.max(0,scrollRow-1);return true;}return super.mouseScrolled(mouseX,mouseY,delta);}
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){if(keyCode==GLFW.GLFW_KEY_ESCAPE){PhoneNavigationState.backOrHome(minecraft);return true;}return super.keyPressed(keyCode,scanCode,modifiers);}
}

package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.*;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Full gallery, explicit preview and an optional photo caption. */
public final class PhonePhotoPickerScreen extends AbstractZPhoneScreen {
    private final String peer;private final boolean gram;private final String channel;
    private final StringBuilder caption=new StringBuilder();private final PhoneChatComposer composer=new PhoneChatComposer(caption,240);
    private Path selected;private int row;private boolean focused;private String error="";
    public PhonePhotoPickerScreen(String peer,boolean gram){super(Component.literal(PhoneSettingsStore.text("Fotoğraflar", "Photos")));this.peer=peer;this.gram=gram;this.channel=gram?"GRAM":"MESSAGE";}
    public PhonePhotoPickerScreen(String peer,boolean gram,Path photo){this(peer,gram);this.selected=photo;}
    public PhonePhotoPickerScreen(String peer,String channel){super(Component.literal(PhoneSettingsStore.text("Fotoğraflar", "Photos")));this.peer=peer;this.channel=channel;this.gram=false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);drawWallpaper(g);drawRoundedRect(g,x(3),y(40),x(297),y(606),size(20),0xFF10151D);drawStatusBar(g);
        PhoneModernText.drawColored(g,"‹",x(17),y(60),size(20),0xFFFFFFFF);PhoneModernText.drawColored(g,PhoneSettingsStore.text("Fotoğraf seç","Choose photo"),x(52),y(64),size(15),0xFFFFFFFF);
        if(selected==null){var photos=PhoneGalleryStore.photos(minecraft);row=Math.max(0,Math.min(row,Math.max(0,(photos.size()+2)/3-5)));
            for(int i=row*3;i<Math.min(photos.size(),row*3+15);i++){int n=i-row*3,l=21+(n%3)*88,t=113+(n/3)*88;var texture=PhoneGalleryStore.textureFor(minecraft,photos.get(i));
                drawRoundedRect(g,x(l),y(t),x(l+82),y(t+82),size(7),0xFF273646);if(texture!=null)g.blit(texture.location(),x(l+2),y(t+2),size(78),size(78),0,0,texture.width(),texture.height(),texture.width(),texture.height());
            }
            PhoneModernText.drawColored(g,photos.size()+PhoneSettingsStore.text(" fotoğraf · kaydır"," photos · scroll"),x(22),y(562),size(8),0xFF9EAAB9);
        }else{var texture=PhoneGalleryStore.textureFor(minecraft,selected);if(texture!=null){float factor=Math.min(252f/texture.width(),270f/texture.height());int w=Math.round(texture.width()*factor),h=Math.round(texture.height()*factor);g.blit(texture.location(),x((300-w)/2f),y(110+(270-h)/2f),size(w),size(h),0,0,texture.width(),texture.height(),texture.width(),texture.height());}
            if(!channel.equals("AVATAR")){composer.layout(this,24,512,252,size(8));composer.render(g,focused,PhoneSettingsStore.text("Açıklama ekle (isteğe bağlı)","Add a caption (optional)"),0xFF233044);}
            drawRoundedRect(g,x(24),y(527),x(276),y(566),size(12),0xFF087FF1);PhoneModernText.drawCenteredColored(g,channel.equals("AVATAR")?PhoneSettingsStore.text("Grup fotoğrafı yap","Set group photo"):PhoneSettingsStore.text("Gönder","Send"),x(150),y(540),size(11),0xFFFFFFFF);
            PhoneModernText.drawColoredFitted(g,error,x(24),y(574),size(7),size(252),0xFFFF9689);
        }drawHomeIndicator(g);drawFrame(g);
    }
    private void send(){if(selected!=null&&(channel.equals("GROUP")||channel.equals("AVATAR")?com.mikasrevs.newphone.client.data.PhoneGroupClient.photo(peer,selected,caption.toString().trim(),channel.equals("AVATAR")):PhoneClientData.sendPhoto(peer,selected,gram,caption.toString().trim())))PhoneNavigationState.backOrHome(minecraft);else error=PhoneSettingsStore.text("Fotoğraf hazırlanamadı.","Could not prepare photo.");}
    @Override public boolean mouseClicked(double mx,double my,int b){if(b!=0)return super.mouseClicked(mx,my,b);if(inside(mx,my,10,47,46,94)){if(selected!=null){selected=null;focused=false;}else PhoneNavigationState.backOrHome(minecraft);return true;}
        if(selected==null){var photos=PhoneGalleryStore.photos(minecraft);for(int i=row*3;i<Math.min(photos.size(),row*3+15);i++){int n=i-row*3,l=21+n%3*88,t=113+n/3*88;if(inside(mx,my,l,t,l+82,t+82)){selected=photos.get(i);return true;}}}
        else{if(composer.contains(mx,my)){focused=true;composer.click(mx,my,hasShiftDown());return true;}if(inside(mx,my,24,527,276,566)){send();return true;}focused=false;}
        return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){if(selected==null)row=Math.max(0,row+(delta>0?-1:1));else composer.scroll(mx,my,delta);return true;}
    @Override public boolean charTyped(char c,int modifiers){if(focused){composer.type(c);return true;}return super.charTyped(c,modifiers);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_ESCAPE){if(selected!=null){selected=null;focused=false;}else PhoneNavigationState.backOrHome(minecraft);return true;}if(focused&&composer.key(key,mods))return true;if(selected!=null&&key==GLFW.GLFW_KEY_ENTER){send();return true;}return super.keyPressed(key,scan,mods);}
}

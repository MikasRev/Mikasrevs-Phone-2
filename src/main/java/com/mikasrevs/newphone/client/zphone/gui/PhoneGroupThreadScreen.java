package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.data.*;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.sound.PhoneSoundManager;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class PhoneGroupThreadScreen extends AbstractZPhoneScreen {
    private final String id;
    private final StringBuilder input=new StringBuilder();
    private final PhoneChatComposer composer=new PhoneChatComposer(input);
    private final List<Hit> hits=new ArrayList<>();
    private PhoneStateData.MessageEntry reply,context;
    private boolean focused=true;
    private int scroll;
    private long readAt;
    private static final int TOP=122;
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HH:mm");
    public PhoneGroupThreadScreen(String id){super(Component.literal(PhoneSettingsStore.text("Grup", "Group")));this.id=id;}
    public boolean matchesTarget(String target){return id.equals(target);}
    private int composerTop(){return composer.layout(this,56,558,188,size(9));}
    private int bottom(){return composerTop()-(reply==null?12:40);}
    @Override public void tick(){
        super.tick();
        if(PhoneGroupClient.group(id)==null){reply=null;context=null;hits.clear();return;}
        if(reply!=null&&PhoneGroupClient.messages(id).stream().noneMatch(m->m.id.equals(reply.id)))reply=null;
        if(context!=null&&PhoneGroupClient.messages(id).stream().noneMatch(m->m.id.equals(context.id)))context=null;
        if(PhoneGroupClient.unread(id)>0&&System.currentTimeMillis()-readAt>1000){
            readAt=System.currentTimeMillis();PhoneNetwork.sendAction("GROUP_READ",id,"");
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);drawRoundedRect(g,x(3),y(40),x(297),y(606),size(22),PhoneGroupVisuals.BG);drawStatusBar(g);
        PhoneGroupVisuals.text(this,g,"‹",17,58,23,24,PhoneGroupVisuals.BLUE);
        var group=PhoneGroupClient.group(id);
        if(group==null){PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Bu gruba erişimin yok","This group is unavailable"),267,12,PhoneGroupVisuals.MUTED);drawHomeIndicator(g);drawFrame(g);return;}
        PhoneGroupVisuals.avatar(this,g,group,65,78,34);
        PhoneGroupVisuals.text(this,g,group.name,91,61,12,166,PhoneGroupVisuals.TEXT);
        PhoneGroupVisuals.text(this,g,group.members.size()+PhoneSettingsStore.text(" kişi · grup bilgisi"," members · group info"),91,83,7,172,PhoneGroupVisuals.MUTED);
        PhoneGroupVisuals.text(this,g,"›",273,67,16,12,PhoneGroupVisuals.MUTED);
        g.fill(x(18),y(110),x(282),y(111),0xFF25252A);
        int end=bottom();
        var messages=PhoneGroupClient.messages(id);
        int height=0;for(var m:messages)height+=height(m)+12;
        scroll=Math.max(0,Math.min(scroll,Math.max(0,height-(end-TOP))));
        int yy=Math.min(TOP,end-height)+scroll;
        hits.clear();
        g.enableScissor(x(12),y(TOP),x(288),y(end));
        if(messages.isEmpty()){
            PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Sohbet burada başlıyor","Your conversation starts here"),254,11,PhoneGroupVisuals.TEXT);
            PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Grubuna bir merhaba gönder.","Say hello to your group."),277,8,PhoneGroupVisuals.MUTED);
        }
        for(var m:messages){int h=height(m);if(yy+h>=TOP&&yy<end){drawMessage(g,m,yy);hits.add(new Hit(m,Math.max(TOP,yy),Math.min(end,yy+h)));}yy+=h+12;}
        g.flush();g.disableScissor();
        if(reply!=null){
            drawRoundedRect(g,x(56),y(composerTop()-30),x(244),y(composerTop()-4),size(10),PhoneGroupVisuals.CARD);
            PhoneGroupVisuals.text(this,g,"↪ "+PhoneClientData.messagePreview(reply),65,composerTop()-22,7,152,PhoneGroupVisuals.MUTED);
            PhoneGroupVisuals.text(this,g,"×",226,composerTop()-25,13,16,PhoneGroupVisuals.TEXT);
        }
        fillCircle(g,x(33),y(541),size(17),PhoneGroupVisuals.CARD);
        PhoneGroupVisuals.text(this,g,"+",25,530,20,24,PhoneGroupVisuals.MUTED);
        composer.render(g,focused,PhoneSettingsStore.text("Mesaj","Message"),PhoneGroupVisuals.CARD);
        fillCircle(g,x(266),y(541),size(16),input.toString().isBlank()?0xFF33333A:PhoneGroupVisuals.BLUE);
        PhoneVoiceVisuals.send(this,g,266,541,PhoneGroupVisuals.TEXT);
        PhoneChatExtras.draw(this,g,id);
        if(context!=null){
            beginOverlayLayer(g);
            drawRoundedRect(g,x(99),y(400),x(282),y(494),size(17),0xFF303037);finishOverlayBackground(g);
            PhoneGroupVisuals.text(this,g,PhoneSettingsStore.text("Yanıtla","Reply"),118,417,11,150,PhoneGroupVisuals.TEXT);
            if(context.fromUuid.equals(PhoneClientData.selfUuid()))PhoneGroupVisuals.text(this,g,PhoneSettingsStore.text("Mesajı sil","Delete message"),118,463,11,150,0xFFFF6961);
        }
        drawHomeIndicator(g);drawFrame(g);
    }
    private int height(PhoneStateData.MessageEntry m){
        int content=m.type.endsWith("VOICE")?45:m.type.endsWith("PHOTO")?112+PhoneChatExtras.captionHeight(m):24+ZPhoneSocialRenderUtil.wrap(m.body,26,size(176),size(9)).size()*13;
        return content+30+(m.replyPreview.isBlank()?0:18);
    }
    private void drawMessage(GuiGraphics g,PhoneStateData.MessageEntry m,int top){
        boolean mine=m.fromUuid.equals(PhoneClientData.selfUuid());
        int left=mine?82:18,right=left+200,yy=top;
        if(!mine)PhoneGroupVisuals.text(this,g,m.fromName,left+10,yy,7,180,0xFF9CCBFF);
        yy+=13;
        if(!m.replyPreview.isBlank()){PhoneGroupVisuals.text(this,g,"↪ "+m.replyPreview,left+8,yy,7,182,PhoneGroupVisuals.MUTED);yy+=18;}
        int content=height(m)-30-(m.replyPreview.isBlank()?0:18);
        if(m.type.endsWith("VOICE"))PhoneChatExtras.voiceBubble(this,g,m,left,yy,right);
        else{
            drawRoundedRect(g,x(left),y(yy),x(right),y(yy+content),size(17),mine?PhoneGroupVisuals.BLUE:0xFF27272D);
            if(m.type.endsWith("PHOTO")){
                var photo=PhonePhotoStore.textureFor(minecraft,m.body);
                if(photo!=null)g.blit(photo.location(),x(left+6),y(yy+6),size(188),size(100),0,0,photo.width(),photo.height(),photo.width(),photo.height());
                else PhoneGroupVisuals.text(this,g,PhoneSettingsStore.text("Fotoğraf yükleniyor…","Loading photo…"),left+12,yy+42,9,176,PhoneGroupVisuals.TEXT);
                PhoneChatExtras.caption(this,g,m,left+10,yy+112,180);
            }else{var lines=ZPhoneSocialRenderUtil.wrap(m.body,26,size(176),size(9));for(int i=0;i<lines.size();i++)PhoneGroupVisuals.text(this,g,lines.get(i),left+12,yy+11+i*13,9,176,PhoneGroupVisuals.TEXT);}
        }
        String time=TIME.format(Instant.ofEpochMilli(m.timestamp).atZone(ZoneId.systemDefault()));
        PhoneGroupVisuals.text(this,g,time+(mine?" · "+PhoneMessageClient.status(m):""),left+6,yy+content+4,6,194,PhoneGroupVisuals.MUTED);
    }
    private void send(){
        if(input.toString().isBlank()||PhoneGroupClient.group(id)==null)return;
        PhoneMessageClient.send(id,"GROUP_TEXT",input.toString().trim(),reply==null?"":reply.id,"",null);
        input.setLength(0);reply=null;scroll=0;focused=true;PhoneSoundManager.playMessageSend();
    }
    @Override public boolean mouseClicked(double mx,double my,int b){
        if(b==0&&inside(mx,my,8,48,45,103)){PhoneNavigationState.backOrHome(minecraft);return true;}
        if(PhoneGroupClient.group(id)==null)return super.mouseClicked(mx,my,b);
        if(context!=null){
            if(b==0&&inside(mx,my,99,400,282,448)){reply=context;focused=true;}
            else if(b==0&&inside(mx,my,99,448,282,494)&&context.fromUuid.equals(PhoneClientData.selfUuid()))PhoneGroupClient.delete(context);
            context=null;return true;
        }
        if(b==0&&inside(mx,my,47,48,286,105)){PhoneNavigationState.open(minecraft,this,new PhoneGroupDetailsScreen(id));return true;}
        if(PhoneChatExtras.click(this,mx,my,b,id,"GROUP"))return true;
        if(b==0&&inside(mx,my,16,523,50,560)){PhoneNavigationState.open(minecraft,this,new PhonePhotoPickerScreen(id,"GROUP"));return true;}
        if(b==0&&inside(mx,my,248,523,286,560)){send();return true;}
        if(reply!=null&&b==0&&inside(mx,my,219,composerTop()-30,244,composerTop()-4)){reply=null;return true;}
        if(b==0&&composer.contains(mx,my)){focused=true;composer.click(mx,my,hasShiftDown());return true;}
        for(var hit:hits){
            int left=hit.message.fromUuid.equals(PhoneClientData.selfUuid())?82:18;
            if(!inside(mx,my,left,hit.top,left+200,hit.bottom))continue;
            if(b==1){context=hit.message;return true;}
            if(b==0){if(PhoneChatExtras.open(hit.message))return true;if(hit.message.type.endsWith("PHOTO"))PhoneNavigationState.open(minecraft,this,new ZPhonePhotoViewerScreen(hit.message.body,this));return true;}
        }
        focused=false;return super.mouseClicked(mx,my,b);
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){
        if(context!=null)return true;if(composer.scroll(mx,my,delta))return true;
        if(inside(mx,my,12,TOP,288,bottom())){
            int h=0;for(var m:PhoneGroupClient.messages(id))h+=height(m)+12;
            int max=Math.max(0,h-(bottom()-TOP));scroll=Math.max(0,Math.min(max,scroll+(delta>0?42:-42)));
            if(delta>0&&scroll>=max)PhoneMessageClient.older(id,"GROUP");return true;
        }
        return super.mouseScrolled(mx,my,delta);
    }
    @Override public boolean charTyped(char c,int mods){if(focused&&context==null&&c>=32){composer.type(c);return true;}return super.charTyped(c,mods);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE){if(context!=null)context=null;else if(reply!=null)reply=null;else PhoneNavigationState.backOrHome(minecraft);return true;}
        if(focused&&context==null){if(composer.key(key,mods))return true;if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){send();return true;}}
        return super.keyPressed(key,scan,mods);
    }
    private record Hit(PhoneStateData.MessageEntry message,int top,int bottom){}
}

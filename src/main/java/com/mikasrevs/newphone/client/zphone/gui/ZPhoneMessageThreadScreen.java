package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.gui.PhoneGalleryStore;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.gui.PhoneGpsState;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.sound.PhoneSoundManager;
import com.mikasrevs.newphone.network.PhoneStateData;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Messages thread with Forge 1.20.1 photo/location attachments, typing, replies and receipts. */
public final class ZPhoneMessageThreadScreen extends AbstractZPhoneScreen {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HH:mm");
    private static final int VIEW_TOP=108;
    private static final int MESSAGE_WRAP_CHARS=27,MESSAGE_LINE_HEIGHT=13;
    private static String pendingCameraTargetUuid="",pendingCameraTargetName="";
    private final String peerUuid,fallbackName;
    private final StringBuilder input=new StringBuilder();
    private final PhoneChatComposer composer = new PhoneChatComposer(input);
    private final List<MessageHitBox> hitBoxes=new ArrayList<>();
    private boolean inputFocused,typingSent,attachmentMenu,galleryOpen;
    private int scrollOffset;
    private long lastTypedAt;
    private PhoneStateData.MessageEntry contextMessage,replyTarget;
    private Path selectedPhoto;
    public ZPhoneMessageThreadScreen withDraft(String text){composer.setDraft(text);inputFocused=true;return this;}
    private String localNotice="";private long localNoticeUntil;
    private String outgoingAnimationBody="",outgoingAnimationPreviousId="",outgoingAnimationId="";
    private long outgoingAnimationStartedAt;

    public ZPhoneMessageThreadScreen(String peerUuid,String peerName){super(Component.literal(PhoneSettingsStore.text("Sohbet", "Conversation")));this.peerUuid=peerUuid==null?"":peerUuid;this.fallbackName=peerName==null||peerName.isBlank()?PhoneSettingsStore.text("Oyuncu", "Player"):peerName;PhoneClientData.markConversationOpened(this.peerUuid);PhoneClientData.requestSync();}
    @Override public void tick(){PhoneClientData.markConversationRead(peerUuid);if(typingSent&&lastTypedAt>0&&System.currentTimeMillis()-lastTypedAt>1500L){PhoneClientData.updateTyping(peerUuid,false);typingSent=false;}}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){renderBackground(g);drawWallpaper(g);drawRoundedRect(g,x(3),y(40),x(297),y(606),size(22),0xF5070B10);drawStatusBar(g);if(galleryOpen){beginOverlayLayer(g);drawGalleryPicker(g,mouseX,mouseY);drawHomeIndicator(g);drawFrame(g);return;}drawHeader(g);drawConversation(g);drawComposer(g);drawContextMenu(g);if(attachmentMenu)drawAttachmentMenu(g);drawNotice(g);PhoneChatExtras.draw(this,g,peerUuid);drawHomeIndicator(g);drawFrame(g);}
    private void drawHeader(GuiGraphics g){PhoneModernText.drawColored(g,"‹",x(16),y(57),readableTextSize(20),0xFF7DBBFF);PhonePlayerHeadRenderer.draw(g,peerUuid,x(55),y(55),size(34));PhoneStateData.PlayerEntry c=PhoneClientData.contactByUuid(peerUuid);String name=c==null?fallbackName:c.name;PhoneModernText.drawColoredFitted(g,name,x(98),y(57),readableTextSize(11),size(98),0xFFFFFFFF);PhoneModernText.drawColored(g,c!=null&&c.online?PhoneSettingsStore.text("Çevrimiçi", "Online"):PhoneSettingsStore.text("Çevrimdışı", "Offline"),x(98),y(76),readableTextSize(6),c!=null&&c.online?0xFF71D894:0xFF7D8793);fillCircle(g,x(222),y(70),size(15),0xFF173047);PhoneModernText.drawCenteredColored(g,"▣",x(222),y(62),readableTextSize(10),0xFF59A9FF);fillCircle(g,x(260),y(70),size(15),0xFF173326);drawHandset(g,x(260),y(70),0xFF31D158);g.fill(x(17),y(98),x(283),y(99),0x2836414E);}

    private void drawConversation(GuiGraphics g){hitBoxes.clear();List<PhoneStateData.MessageEntry> all=PhoneClientData.conversation(peerUuid);resolveOutgoingAnimation(all);int total=contentHeight(all),viewport=viewBottom()-VIEW_TOP,max=Math.max(0,total-viewport);scrollOffset=Math.max(0,Math.min(scrollOffset,max));int yy=viewBottom()-total+scrollOffset;g.enableScissor(x(12),y(VIEW_TOP),x(288),y(viewBottom()));if(all.isEmpty()){PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Henüz mesaj yok", "No messages yet"),x(150),y(275),readableTextSize(11),0xFF7F8A97);PhoneModernText.drawCenteredColoredFitted(g,PhoneSettingsStore.text("Merhaba de: ", "Say hello to ")+fallbackName,x(150),y(297),readableTextSize(7),size(250),0xFF606B77);}for(PhoneStateData.MessageEntry m:all){int h=messageHeight(m);if(yy+h>=VIEW_TOP&&yy<=viewBottom())drawMessageWithSendAnimation(g,m,yy,h);yy+=h+8;}g.flush();g.disableScissor();if(replyTarget==null&&PhoneClientData.isTyping(peerUuid)){drawRoundedRect(g,x(24),y(viewBottom()+3),x(76),y(viewBottom()+25),size(13),0xFF242D38);PhoneModernText.drawCenteredColored(g,"•••",x(50),y(viewBottom()+8),readableTextSize(8),0xFFB4BDC7);}}
    private int contentHeight(List<PhoneStateData.MessageEntry> list){int h=0;for(PhoneStateData.MessageEntry m:list)h+=messageHeight(m)+8;return Math.max(0,h-8);}
    private int messageHeight(PhoneStateData.MessageEntry m){if("VOICE".equals(m.type))return 70;if("PHOTO".equals(m.type))return 123+PhoneChatExtras.captionHeight(m);if("LOCATION".equals(m.type))return 96;int lines=Math.max(1,messageLines(m).size());return 62+(lines-1)*MESSAGE_LINE_HEIGHT+(m.replyPreview!=null&&!m.replyPreview.isBlank()?12:0);}
    private List<String> messageLines(PhoneStateData.MessageEntry m){List<String> lines=ZPhoneSocialRenderUtil.wrap(m==null?"":m.body,MESSAGE_WRAP_CHARS,size(164),readableTextSize(8));return lines.isEmpty()?List.of(""):lines;}
    private void drawMessage(GuiGraphics g,PhoneStateData.MessageEntry m,int top){boolean mine=PhoneClientData.isSelfMessageUuid(m.fromUuid);if("VOICE".equals(m.type)){int left=mine?84:18;PhoneChatExtras.voiceBubble(this,g,m,left,top,left+198);drawMeta(g,m,mine,left,left+198,top+47);addMessageHitBox(m,left,top,left+198,top+70);return;}if("PHOTO".equals(m.type)){drawPhoto(g,m,mine,top);return;}if("LOCATION".equals(m.type)){drawLocation(g,m,mine,top);return;}int left=mine?98:18,right=mine?282:202,h=messageHeight(m)-20;drawRoundedRect(g,x(left),y(top),x(right),y(top+h),size(13),mine?0xFF0A84FF:0xFF252E39);int textY=top+10;if(m.replyPreview!=null&&!m.replyPreview.isBlank()){PhoneModernText.drawColored(g,"↪ "+trim(m.replyPreview,24),x(left+10),y(top+6),readableTextSize(5),mine?0xFFD8ECFF:0xFFA7B3C0);textY+=12;}List<String> lines=messageLines(m);for(int i=0;i<lines.size();i++)PhoneModernText.drawColoredFitted(g,lines.get(i),x(left+10),y(textY+i*MESSAGE_LINE_HEIGHT),readableTextSize(8),size(right-left-20),0xFFFFFFFF);drawMeta(g,m,mine,left,right,top+h);addMessageHitBox(m,left,top,right,top+messageHeight(m));}
    private void drawPhoto(GuiGraphics g,PhoneStateData.MessageEntry m,boolean mine,int top){int w=156,h=99+PhoneChatExtras.captionHeight(m),left=mine?120:24;drawRoundedRect(g,x(left),y(top),x(left+w),y(top+h),size(13),mine?0xFF0A84FF:0xFF242A33);PhonePhotoStore.PhotoTexture t=PhonePhotoStore.textureFor(minecraft,m.body);if(t!=null)g.blit(t.location(),x(left+5),y(top+5),size(146),size(82),0,0,t.width(),t.height(),t.width(),t.height());else{PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Fotoğraf", "Photo"),x(left+w/2),y(top+42),readableTextSize(10),0xFFFFFFFF);}PhoneChatExtras.caption(this,g,m,left+7,top+94,w-14);drawMeta(g,m,mine,left,left+w,top+h);addMessageHitBox(m,left,top,left+w,top+h);}
    private void drawLocation(GuiGraphics g,PhoneStateData.MessageEntry m,boolean mine,int top){int w=184,h=70,left=mine?92:24;drawRoundedRect(g,x(left),y(top),x(left+w),y(top+h),size(14),mine?0xFF1268C4:0xFF202833);PhoneGpsState.Location loc=PhoneGpsState.Location.decode(m.body);fillCircle(g,x(left+25),y(top+27),size(15),0xFF31C56B);fillCircle(g,x(left+25),y(top+23),size(6),0xFFFFFFFF);PhoneModernText.drawColoredFitted(g,PhoneSettingsStore.text("Paylaşılan Konum", "Shared Location"),x(left+49),y(top+9),readableTextSize(8),size(w-58),0xFFFFFFFF);PhoneModernText.drawColored(g,loc.friendlyDimension(),x(left+49),y(top+28),readableTextSize(6),0xFFDCEAF7);PhoneModernText.drawColored(g,loc.compactCoordinates(),x(left+49),y(top+43),readableTextSize(5),0xFFBBD1E5);PhoneModernText.drawColoredFitted(g,PhoneSettingsStore.text("Haritalar'ı açmak için dokun", "Tap to open Maps"),x(left+49),y(top+57),readableTextSize(5),size(w-58),0xFF91C7FF);PhoneChatExtras.caption(this,g,m,left+7,top+94,w-14);drawMeta(g,m,mine,left,left+w,top+h);addMessageHitBox(m,left,top,left+w,top+h);}
    private void addMessageHitBox(PhoneStateData.MessageEntry message,int left,int top,int right,int bottom){int clippedTop=Math.max(VIEW_TOP,top),clippedBottom=Math.min(viewBottom(),bottom);if(clippedTop<clippedBottom)hitBoxes.add(new MessageHitBox(message,left,clippedTop,right,clippedBottom));}
    private void drawMeta(GuiGraphics g,PhoneStateData.MessageEntry m,boolean mine,int left,int right,int bottom){
        String time=TIME.format(Instant.ofEpochMilli(m.timestamp).atZone(ZoneId.systemDefault()));
        String meta=time;
        int color=0xFFB7C2CE;
        if(mine){
            meta=time+"  "+com.mikasrevs.newphone.client.data.PhoneMessageClient.status(m);
            color=m.read?0xFFB6E8FF:0xFFC6D0DB;
        }
        int textHeight=readableTextSize(5);
        int metaX=x(left+8);
        if(mine)metaX=Math.max(metaX,x(right-8)-PhoneModernText.width(meta,textHeight));
        PhoneModernText.drawColoredFitted(g,meta,metaX,y(bottom+2),textHeight,Math.max(1,x(right-8)-metaX),color);
    }

    private int composerTop() { return composer.layout(this, 57, 558, 186, readableTextSize(8)); }
    private int viewBottom() { return Math.min(488, composerTop() - (replyTarget == null ? 25 : 49)); }
    private void drawComposer(GuiGraphics g) {
        int top = composerTop();
        if (replyTarget != null) {
            drawRoundedRect(g,x(57),y(top-27),x(243),y(top-3),size(9),0xF01C2530);
            PhoneModernText.drawColored(g,PhoneSettingsStore.text("Yanıt: ", "Reply: ")+trim(PhoneClientData.messagePreview(replyTarget),22),x(68),y(top-20),readableTextSize(6),0xFFB8C2CE);
            PhoneModernText.drawColored(g,"×",x(225),y(top-24),readableTextSize(10),0xFFFFFFFF);
        }
        drawRoundedRect(g,x(18),y(526),x(50),y(558),size(16),attachmentMenu?0xFF33404E:0xFF202731);
        PhoneModernText.drawCenteredColored(g,attachmentMenu?"×":"+",x(34),y(531),readableTextSize(17),0xFFB5BDC7);
        composer.render(g,inputFocused,PhoneSettingsStore.text("Mesaj", "Message"),inputFocused?0xFF202B38:0xFF1B2029);
        fillCircle(g,x(263),y(542),size(17),input.toString().trim().isEmpty()?0xFF303641:0xFF0A84FF);
        PhoneModernText.drawCenteredColored(g,"↑",x(263),y(532),readableTextSize(13),0xFFFFFFFF);
    }
    
    private void drawAttachmentMenu(GuiGraphics g){beginOverlayLayer(g);drawRoundedRect(g,x(18),y(448),x(282),y(505),size(15),0xFF1A2029);finishOverlayBackground(g);button(g,27,458,101,495,PhoneSettingsStore.text("Galeri", "Gallery"),0xFF26384C,0xFF8CC3FF);button(g,113,458,187,495,PhoneSettingsStore.text("Kamera", "Camera"),0xFF32313D,0xFFFFD36B);button(g,199,458,273,495,PhoneSettingsStore.text("Konum", "Location"),0xFF203B31,0xFF79D79A);}
    private void button(GuiGraphics g,int l,int t,int r,int b,String text,int bg,int fg){drawRoundedRect(g,x(l),y(t),x(r),y(b),size(11),bg);PhoneModernText.drawCenteredColoredFitted(g,text,x((l+r)/2F),y(t+12),readableTextSize(7),size(r-l-10),fg);}
    private void drawGalleryPicker(GuiGraphics g,int mouseX,int mouseY){g.fill(x(3),y(40),x(297),y(606),0xFF070B10);drawRoundedRect(g,x(18),y(218),x(282),y(575),size(20),0xFF12171E);PhoneModernText.drawColored(g,PhoneSettingsStore.text("Fotoğraf Seç", "Choose Photo"),x(34),y(249),readableTextSize(15),0xFFFFFFFF);List<Path> photos=PhoneGalleryStore.photos(minecraft);for(int i=0;i<Math.min(6,photos.size());i++){int col=i%3,row=i/3,px=34+col*78,py=284+row*76;Path p=photos.get(i);drawRoundedRect(g,x(px-3),y(py-3),x(px+63),y(py+63),size(10),p.equals(selectedPhoto)?0xFF0A84FF:0xFF252C35);PhoneGalleryStore.GalleryTexture t=PhoneGalleryStore.textureFor(minecraft,p);if(t!=null)g.blit(t.location(),x(px),y(py),size(57),size(57),0,0,t.width(),t.height(),t.width(),t.height());}drawRoundedRect(g,x(34),y(461),x(266),y(501),size(12),selectedPhoto==null?0xFF303641:0xFF0A84FF);PhoneModernText.drawCenteredColoredFitted(g,PhoneSettingsStore.text("Seçili fotoğrafı gönder", "Send selected photo"),x(150),y(473),readableTextSize(9),size(214),0xFFFFFFFF);PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Vazgeç", "Cancel"),x(150),y(525),readableTextSize(8),0xFF9AA3AF);}
    private void drawContextMenu(GuiGraphics g){if(contextMessage==null||galleryOpen)return;boolean mine=PhoneClientData.isSelfMessageUuid(contextMessage.fromUuid);beginOverlayLayer(g);drawRoundedRect(g,x(150),y(386),x(286),y(mine?475:437),size(12),0xFF161C24);finishOverlayBackground(g);drawRoundedRect(g,x(158),y(395),x(278),y(428),size(8),0xFF283442);PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Yanıtla", "Reply"),x(218),y(406),readableTextSize(8),0xFFFFFFFF);if(mine){drawRoundedRect(g,x(158),y(434),x(278),y(467),size(8),0xFF43252A);PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Sil", "Delete"),x(218),y(445),readableTextSize(8),0xFFFF7C83);}}
    private void drawNotice(GuiGraphics g){String n=System.currentTimeMillis()<localNoticeUntil?localNotice:PhoneSettingsStore.localizeNetworkText(PhoneClientData.notice());if(n==null||n.isBlank())return;drawRoundedRect(g,x(44),y(101),x(256),y(129),size(12),0xEC26323F);PhoneModernText.drawCenteredColoredFitted(g,trim(n,48),x(150),y(109),readableTextSize(6),size(194),0xFFFFFFFF);}

    private void send(){String text=input.toString().trim();if(text.isEmpty())return;armOutgoingAnimation(text,PhoneClientData.conversation(peerUuid));if(replyTarget==null)PhoneClientData.sendText(peerUuid,text);else PhoneClientData.sendReply(peerUuid,text,replyTarget.id);PhoneSoundManager.playMessageSend();input.setLength(0);replyTarget=null;contextMessage=null;inputFocused=true;scrollOffset=0;PhoneClientData.updateTyping(peerUuid,false);typingSent=false;}

    private void armOutgoingAnimation(String body,List<PhoneStateData.MessageEntry> current){
        outgoingAnimationBody=body==null?"":body;
        outgoingAnimationPreviousId=current==null||current.isEmpty()?"":safeMessageId(current.get(current.size()-1));
        outgoingAnimationId="";
        outgoingAnimationStartedAt=0L;
    }

    private void resolveOutgoingAnimation(List<PhoneStateData.MessageEntry> messages){
        if(!outgoingAnimationId.isBlank()||outgoingAnimationBody.isBlank()||messages==null||messages.isEmpty())return;
        for(int i=messages.size()-1;i>=0;i--){
            PhoneStateData.MessageEntry message=messages.get(i);
            String id=safeMessageId(message);
            if(!outgoingAnimationPreviousId.isBlank()&&outgoingAnimationPreviousId.equals(id))break;
            if(PhoneClientData.isSelfMessageUuid(message.fromUuid)&&outgoingAnimationBody.equals(message.body)){
                outgoingAnimationId=id;
                outgoingAnimationStartedAt=System.currentTimeMillis();
                outgoingAnimationBody="";
                outgoingAnimationPreviousId="";
                return;
            }
        }
    }

    private void drawMessageWithSendAnimation(GuiGraphics g,PhoneStateData.MessageEntry message,int top,int height){
        String id=safeMessageId(message);
        if(outgoingAnimationId.isBlank()||!outgoingAnimationId.equals(id)){drawMessage(g,message,top);return;}
        float progress=Math.min(1.0F,(System.currentTimeMillis()-outgoingAnimationStartedAt)/230.0F);
        float eased=1.0F-(float)Math.pow(1.0F-progress,3.0D);
        if(progress>=1.0F){outgoingAnimationId="";drawMessage(g,message,top);return;}
        float bubbleScale=0.84F+0.16F*eased;
        int pivotX=x(282);
        int pivotY=y(top+Math.max(1,height-10));
        g.pose().pushPose();
        g.pose().translate(pivotX,pivotY+size((1.0F-eased)*8.0F),0.0F);
        g.pose().scale(bubbleScale,bubbleScale,1.0F);
        g.pose().translate(-pivotX,-pivotY,0.0F);
        drawMessage(g,message,top);
        g.pose().popPose();
    }

    private String safeMessageId(PhoneStateData.MessageEntry message){return message==null||message.id==null?"":message.id;}

    private String trim(String text,int max){String safe=text==null?"":text.replace('\n',' ').trim();return safe.length()<=max?safe:safe.substring(0,Math.max(1,max-1))+"…";}
    private void drawHandset(GuiGraphics g,int cx,int cy,int color){int s=Math.max(1,size(1.2F));g.fill(cx-7*s,cy-2*s,cx-3*s,cy+3*s,color);g.fill(cx-4*s,cy-5*s,cx,cy,color);g.fill(cx,cy-5*s,cx+4*s,cy,color);g.fill(cx+3*s,cy-2*s,cx+7*s,cy+3*s,color);}

    private boolean openAttachment(double mx,double my){for(MessageHitBox h:hitBoxes){if(!inside(mx,my,h.left,h.top,h.right,h.bottom))continue;if(PhoneChatExtras.open(h.message))return true;if("LOCATION".equals(h.message.type)){PhoneGpsState.openSharedLocation(minecraft,PhoneGpsState.Location.decode(h.message.body));return true;}if("PHOTO".equals(h.message.type)){minecraft.setScreen(new ZPhonePhotoViewerScreen(h.message.body,this));return true;}}return false;}

    private int readableTextSize(float designSize) {
        float factor = designSize <= 6.0F ? 1.18F : designSize <= 8.0F ? 1.10F : designSize <= 10.0F ? 1.05F : 1.0F;
        return Math.max(1, Math.round(size(designSize) * factor));
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        if(!galleryOpen&&contextMessage==null&&!attachmentMenu&&PhoneChatExtras.click(this,mx,my,button,peerUuid,"MESSAGE"))return true;if(galleryOpen&&button!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return true;
        if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT&&!galleryOpen){for(MessageHitBox h:hitBoxes)if(inside(mx,my,h.left,h.top,h.right,h.bottom)){contextMessage=h.message;inputFocused=false;return true;}contextMessage=null;return true;}
        if(button!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return super.mouseClicked(mx,my,button);
        if(galleryOpen){List<Path> photos=PhoneGalleryStore.photos(minecraft);for(int i=0;i<Math.min(6,photos.size());i++){int px=34+(i%3)*78,py=284+(i/3)*76;if(inside(mx,my,px-3,py-3,px+63,py+63)){selectedPhoto=photos.get(i);return true;}}if(inside(mx,my,34,461,266,501)&&selectedPhoto!=null){if(PhoneClientData.sendPhoto(peerUuid,selectedPhoto)){galleryOpen=false;attachmentMenu=false;selectedPhoto=null;scrollOffset=0;}return true;}if(inside(mx,my,80,510,220,548)){galleryOpen=false;selectedPhoto=null;return true;}return true;}
        if(inside(mx,my,8,49,48,92)){stopTyping();PhoneNavigationState.backOrHome(minecraft);return true;}if(inside(mx,my,202,50,240,91)){if(PhoneClientData.voiceCallingAvailable()){PhoneClientData.startVideoCall(peerUuid);localNotice=PhoneSettingsStore.text("Görüntülü aranıyor…", "Video calling…");}else localNotice=PhoneSettingsStore.text("Simple Voice Chat kullanılamıyor", "Simple Voice Chat unavailable");localNoticeUntil=System.currentTimeMillis()+1800L;return true;}if(inside(mx,my,240,50,285,91)){if(PhoneClientData.voiceCallingAvailable()){PhoneClientData.startCall(peerUuid);localNotice=PhoneSettingsStore.text("Aranıyor…", "Calling…");}else localNotice=PhoneSettingsStore.text("Simple Voice Chat kullanılamıyor", "Simple Voice Chat unavailable");localNoticeUntil=System.currentTimeMillis()+1800L;return true;}
        if(contextMessage!=null){if(inside(mx,my,158,395,278,428)){replyTarget=contextMessage;contextMessage=null;inputFocused=true;return true;}if(PhoneClientData.isSelfMessageUuid(contextMessage.fromUuid)&&inside(mx,my,158,434,278,467)){PhoneClientData.deleteMessage(contextMessage.id);contextMessage=null;return true;}contextMessage=null;return true;}
        if(replyTarget!=null&&inside(mx,my,216,composerTop()-27,243,composerTop()-3)){replyTarget=null;return true;}
        if(attachmentMenu){if(inside(mx,my,27,458,101,495)){PhoneNavigationState.open(minecraft,this,new PhonePhotoPickerScreen(peerUuid,false));attachmentMenu=false;return true;}if(inside(mx,my,113,458,187,495)){requestCameraCapture(peerUuid,fallbackName);attachmentMenu=false;inputFocused=false;PhoneNavigationState.open(minecraft,this,new ZPhoneCameraScreen(),PhoneApp.CAMERA);return true;}if(inside(mx,my,199,458,273,495)){PhoneClientData.sendLocation(peerUuid,PhoneGpsState.currentLocation(minecraft).encode());attachmentMenu=false;scrollOffset=0;return true;}}
        
        composerTop();if(inside(mx,my,18,526,50,558)){attachmentMenu=!attachmentMenu;inputFocused=false;return true;}if(composer.contains(mx,my)){inputFocused=true;attachmentMenu=false;composer.click(mx,my,hasShiftDown());return true;}if(inside(mx,my,244,518,286,566)){send();return true;}if(!attachmentMenu&&openAttachment(mx,my))return true;inputFocused=false;return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){if(galleryOpen||contextMessage!=null||attachmentMenu)return true;if(composer.scroll(mx,my,delta))return true;if(inside(mx,my,12,VIEW_TOP,288,viewBottom())&&delta!=0){int max=Math.max(0,contentHeight(PhoneClientData.conversation(peerUuid))-(viewBottom()-VIEW_TOP));scrollOffset=delta>0?Math.min(max,scrollOffset+46):Math.max(0,scrollOffset-46);if(delta>0&&scrollOffset>=max)com.mikasrevs.newphone.client.data.PhoneMessageClient.older(peerUuid,"MESSAGE");return true;}return super.mouseScrolled(mx,my,delta);}
    @Override public boolean charTyped(char c,int modifiers){
        if(galleryOpen||contextMessage!=null||attachmentMenu)return true;
        if(inputFocused&&c>=32&&c!=127){composer.type(c);lastTypedAt=System.currentTimeMillis();PhoneClientData.updateTyping(peerUuid,!input.isEmpty());typingSent=!input.isEmpty();return true;}
        return super.charTyped(c,modifiers);
    }
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){
        if(keyCode==GLFW.GLFW_KEY_ESCAPE){if(galleryOpen){galleryOpen=false;selectedPhoto=null;return true;}if(attachmentMenu){attachmentMenu=false;return true;}if(contextMessage!=null){contextMessage=null;return true;}if(replyTarget!=null){replyTarget=null;return true;}stopTyping();PhoneNavigationState.backOrHome(minecraft);return true;}
        if(galleryOpen||contextMessage!=null||attachmentMenu)return true;
        if(inputFocused&&composer.key(keyCode,modifiers)){lastTypedAt=System.currentTimeMillis();typingSent=!input.isEmpty();PhoneClientData.updateTyping(peerUuid,typingSent);return true;}
        if(inputFocused&&(keyCode==GLFW.GLFW_KEY_ENTER||keyCode==GLFW.GLFW_KEY_KP_ENTER)){send();return true;}
        return super.keyPressed(keyCode,scanCode,modifiers);
    }
    private void stopTyping(){if(typingSent)PhoneClientData.updateTyping(peerUuid,false);typingSent=false;}
    @Override public void removed(){stopTyping();super.removed();}

    public boolean matchesTarget(String uuid){return uuid!=null&&uuid.equals(peerUuid);}

    public static void requestCameraCapture(String uuid,String name){pendingCameraTargetUuid=uuid==null?"":uuid;pendingCameraTargetName=name==null||name.isBlank()?"Player":name;}
    public static boolean finishPendingCameraCapture(Minecraft minecraft){if(minecraft==null||pendingCameraTargetUuid.isBlank())return false;String uuid=pendingCameraTargetUuid;pendingCameraTargetUuid="";pendingCameraTargetName="";Path latest=PhoneGalleryStore.latestPhoto(minecraft);PhoneNavigationState.restoreCurrent(minecraft);if(latest!=null)PhoneNavigationState.open(minecraft,minecraft.screen,new PhonePhotoPickerScreen(uuid,false,latest));return true;}
    public static boolean cancelPendingCameraCapture(Minecraft minecraft){if(minecraft==null||pendingCameraTargetUuid.isBlank())return false;pendingCameraTargetUuid="";pendingCameraTargetName="";PhoneNavigationState.restoreCurrent(minecraft);return true;}
    private record MessageHitBox(PhoneStateData.MessageEntry message,int left,int top,int right,int bottom) { }
}

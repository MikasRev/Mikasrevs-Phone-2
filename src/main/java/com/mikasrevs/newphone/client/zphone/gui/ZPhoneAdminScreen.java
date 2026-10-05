package com.mikasrevs.newphone.client.zphone.gui;

import com.google.gson.Gson;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.moderation.PhoneAdminSession;
import com.mikasrevs.newphone.client.moderation.PhoneModerationClient;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.moderation.PhoneModerationRequest;
import com.mikasrevs.newphone.moderation.PhoneModerationState;
import com.mikasrevs.newphone.network.PhoneNetwork;
import net.minecraft.client.gui.GuiGraphics;
import com.mikasrevs.newphone.client.gui.PhoneTextField;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Locale;

/** Compact moderation dashboard. All changes wait for a correlated server response. */
public final class ZPhoneAdminScreen extends AbstractZPhoneScreen {
    private static final int BG=0xFF0C1019, CARD=0xFF171E2B, RAISED=0xFF202B3D;
    private static final int TEXT=0xFFF2F5FC, MUTED=0xFF96A6BD, BLUE=0xFF8BAFFF;
    private static final int GREEN=0xFF67DDB0, RED=0xFFFF909F, LINE=0xFF2B384C;
    private static final List<String> PLATFORMS=List.of("BLOCKPOST","BLOCKGRAM","TINDER");
    private static final List<String> APP_NAMES=List.of("BlockPost","Lumi","Minder");
    private final PhoneAdminSession view=new PhoneAdminSession();
    private PhoneTextField search;
    private Confirmation confirmation;
    private int refreshTicks;
    private int mouseX,mouseY;
    private record Confirmation(String action,String platform,boolean enabled,String label) {}

    public ZPhoneAdminScreen() { super(Component.literal(PhoneSettingsStore.text("Yönetim", "Admin Panel"))); }

    @Override protected void init() {
        super.init();
        String draft=search==null?view.query:search.getValue();
        boolean focused=search!=null&&search.isFocused();
        search=new PhoneTextField(178,18,64);
        search.setHint(text("İsim veya UUID ara","Search name or UUID"));
        search.setValue(draft);search.setFocused(focused);
        if(!view.loaded&&!view.busy()) requestList(0);
    }

    @Override public void tick() {
        super.tick();
        if(!PhoneModerationClient.canManage()) { view.clear();confirmation=null;return; }
        view.accept(PhoneModerationClient.response());
        if(view.expire(System.nanoTime())) confirmation=null;
        if(++refreshTicks>=100&&!view.busy()&&confirmation==null&&view.fresh) {
            if(view.selected!=null) send(view.begin("DETAIL",System.nanoTime()));
            else if(search.getValue().trim().equals(view.query)) requestList(view.page);
        }
    }

    private void send(PhoneModerationRequest request) {
        if(request==null||!PhoneModerationClient.canManage()) return;
        refreshTicks=0;
        PhoneNetwork.sendAction("MOD_PANEL","",new Gson().toJson(request));
    }

    private void requestList(int page) {
        if(view.busy()) return;
        String query=search.getValue().trim();
        view.page=query.equals(view.query)?Math.max(0,page):0;view.query=query;
        send(view.begin("LIST",System.nanoTime()));
    }

    private void select(PhoneModerationState.Player player) {
        view.selected=player;view.fresh=false;view.notice="";search.setFocused(false);
        send(view.begin("DETAIL",System.nanoTime()));
    }

    private void back() {
        if(confirmation!=null) { confirmation=null;return; }
        if(view.selected==null) { PhoneNavigationState.goHome(minecraft);return; }
        if(view.busy()) return;
        view.selected=null;view.notice="";requestList(view.page);
    }

    private boolean canBan(String platform) {
        var player=view.selected;
        return player!=null&&view.fresh&&!view.busy()
                &&(player.bans.contains(platform)||(!player.operator&&!player.moderator));
    }

    private boolean canRole() {
        return view.selected!=null&&!view.selected.operator&&view.fresh&&!view.busy()
                &&PhoneModerationClient.state().operator;
    }

    private void apply() {
        if(confirmation==null||view.busy()||view.selected==null) return;
        if(confirmation.action.equals("ROLE")?!canRole():!canBan(confirmation.platform)) { confirmation=null;return; }
        var request=view.begin(confirmation.action,System.nanoTime());
        request.platform=confirmation.platform;request.enabled=confirmation.enabled;
        confirmation=null;view.notice="";send(request);
    }

    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        mouseX=mx;mouseY=my;
        renderBackground(g);g.fill(x(0),y(0),x(300),y(610),BG);
        g.fillGradient(x(0),y(35),x(300),y(195),0xFF192944,BG);
        drawStatusBar(g);
        if(!PhoneModerationClient.canManage()) {
            label(g,text("Yetkin yok","Access denied"),150,265,15,240,RED,true);
            drawHomeIndicator(g);drawFrame(g);return;
        }
        if(confirmation!=null) {
            // GUI text writes depth slightly above ordinary fills. Render the modal
            // independently so labels from the player page cannot punch through it.
            renderConfirmation(g);
        } else {
            if(view.selected==null) renderList(g,partial);else renderDetail(g);
            renderNotice(g);
        }
        drawHomeIndicator(g);drawFrame(g);
    }

    private void renderList(GuiGraphics g,float partial) {
        label(g,text("Yönetim","Admin Panel"),22,57,23,188,TEXT,false);
        pill(g,222,57,278,77,PhoneModerationClient.state().operator?"OP":"MOD",0xFF293E63,BLUE);
        label(g,text("Topluluğunu yönet","Manage your community"),23,87,8,245,MUTED,false);
        stat(g,22,text("OYUNCU","PLAYERS"),view.total,TEXT);
        stat(g,110,text("ÇEVRİMİÇİ","ONLINE"),view.online,GREEN);
        stat(g,198,text("BANLI","RESTRICTED"),view.restricted,RED);

        box(g,22,158,278,195,11,search.isFocused()?LINE:CARD);
        box(g,23,159,277,194,10,CARD);
        // Selection and caret share the phone font's measurements with pointer input.
        g.pose().pushPose();g.pose().translate(x(34),y(171),0);
        float scale=searchScale();g.pose().scale(scale,scale,1);
        search.render(g,(int)((mouseX-x(34))/scale),(int)((mouseY-y(171))/scale),partial);
        g.pose().popPose();
        button(g,228,164,271,189,text("Ara","Find"),0xFF304C7C,BLUE,!view.busy());
        label(g,text("OYUNCULAR","PLAYERS"),23,207,7,130,MUTED,false);
        label(g,view.busy()?text("Güncelleniyor…","Updating…"):text("Detay için seç","Select to manage"),211,207,7,130,MUTED,true);

        if(view.players.isEmpty()) {
            box(g,22,229,278,402,16,CARD);
            avatar(g,"?",128,251,44,0xFF293C5A);
            label(g,!view.loaded?text("Liste yükleniyor","Loading players"):text("Oyuncu bulunamadı","No players found"),150,315,12,220,TEXT,true);
            label(g,text("İsim veya UUID ile arayabilirsin","Search by name or UUID"),150,344,8,220,MUTED,true);
        }
        for(int i=0;i<Math.min(8,view.players.size());i++) {
            var player=view.players.get(i);int top=224+i*37;
            boolean hover=confirmation==null&&!view.busy()&&inside(mouseX,mouseY,22,top,278,top+34);
            box(g,22,top,278,top+34,9,hover?RAISED:CARD);
            avatar(g,initial(player.name),29,top+5,24,player.operator?0xFF3C3759:0xFF273B54);
            fillCircle(g,x(51),y(top+27),size(3),player.online?GREEN:0xFF647189);
            label(g,shortName(player.name,20),62,top+5,9,148,TEXT,false);
            label(g,role(player)+" · "+text(player.online?"Çevrimiçi":"Çevrimdışı",player.online?"Online":"Offline"),62,top+21,6,158,MUTED,false);
            if(!player.bans.isEmpty()) pill(g,225,top+8,256,top+26,Integer.toString(player.bans.size()),0xFF422B3B,RED);
            label(g,"›",265,top+11,10,12,MUTED,true);
        }
        button(g,22,529,63,560,"‹",RAISED,BLUE,!view.busy()&&view.page>0);
        label(g,(view.page+1)+" / "+view.pages,116,540,9,92,TEXT,true);
        button(g,170,529,231,560,text("Yenile","Refresh"),RAISED,BLUE,!view.busy());
        button(g,240,529,278,560,"›",RAISED,BLUE,!view.busy()&&view.page+1<view.pages);
    }

    private void stat(GuiGraphics g,int left,String title,int number,int color) {
        box(g,left,107,left+80,146,10,0xFF1A2639);
        label(g,view.loaded?Integer.toString(number):"—",left+10,114,14,60,color,false);
        label(g,title,left+10,133,5,63,MUTED,false);
    }

    private void renderDetail(GuiGraphics g) {
        var player=view.selected;
        button(g,22,54,63,84,"‹",RAISED,BLUE,!view.busy());
        label(g,text("Oyuncu yönetimi","Player management"),77,62,13,200,TEXT,false);
        box(g,22,100,278,185,16,CARD);
        avatar(g,initial(player.name),36,116,42,0xFF2D436B);
        label(g,shortName(player.name,20),91,116,13,172,TEXT,false);
        label(g,role(player),92,138,8,165,BLUE,false);
        fillCircle(g,x(96),y(164),size(3),player.online?GREEN:0xFF647189);
        label(g,text(player.online?"Çevrimiçi":"Çevrimdışı",player.online?"Online":"Offline"),104,160,7,145,MUTED,false);
        label(g,text("UYGULAMA ERİŞİMİ","APP ACCESS"),23,203,7,200,MUTED,false);
        for(int i=0;i<3;i++) {
            String platform=PLATFORMS.get(i);int top=221+i*67;
            boolean banned=player.bans.contains(platform), protectedRole=player.operator||player.moderator;
            box(g,22,top,278,top+59,12,CARD);
            int accent=i==0?0xFFCAD6ED:i==1?0xFFCF9BFA:0xFFFF91AD;
            avatar(g,List.of("X","L","M").get(i),33,top+12,32,i==0?0xFF293548:i==1?0xFF493455:0xFF4D3143);
            label(g,APP_NAMES.get(i),76,top+11,11,105,TEXT,false);
            label(g,banned?text("Yasaklı","Banned"):text("Erişim açık","Allowed"),76,top+33,7,103,banned?RED:GREEN,false);
            box(g,33,top+48,65,top+50,1,accent);
            button(g,184,top+16,266,top+44,banned?text("Ban kaldır","Unban"):protectedRole?text("Korumalı","Protected"):text("Banla","Ban"),
                    banned?0xFF1F413C:0xFF412B3B,banned?GREEN:RED,canBan(platform));
        }
        box(g,22,433,278,506,13,CARD);
        label(g,text("Panel yetkisi","Panel access"),34,445,10,225,TEXT,false);
        label(g,player.operator?text("OP yetkisi sunucudan yönetilir","OP is managed by the server"):
                player.moderator?text("Moderatör · uygulama banlarını yönetir","Moderator · manages app bans"):
                        text("Oyuncu · panele erişimi yok","Player · no panel access"),34,464,7,229,MUTED,false);
        if(PhoneModerationClient.state().operator&&!player.operator)
            button(g,34,480,266,499,player.moderator?text("Moderatörlüğü kaldır","Remove moderator"):text("Moderatör ata","Appoint moderator"),RAISED,BLUE,canRole());
        else label(g,text("Atamaları yalnızca OP değiştirebilir","Only OP can change assignments"),34,486,6,230,MUTED,false);
        button(g,22,529,198,560,text("Oyunculara dön","Back to players"),RAISED,TEXT,!view.busy());
        button(g,208,529,278,560,text("Yenile","Refresh"),RAISED,BLUE,!view.busy());
    }

    private void renderNotice(GuiGraphics g) {
        String message=view.busy()?text("Sunucu yanıtı bekleniyor…","Waiting for the server…"):switch(view.notice) {
            case "UPDATED"->text("Değişiklik kaydedildi","Changes saved");
            case "CHANGED"->text("Bilgiler değişti. Kontrol edip tekrar dene.","Details changed. Review and try again.");
            case "DENIED"->text("İşlem reddedildi. Yetkini kontrol et.","Denied. Check your permissions.");
            case "NOT_FOUND"->text("Oyuncu bulunamadı. Listeyi yenile.","Player not found. Refresh the list.");
            case "TIMEOUT"->text("Yanıt alınamadı. Durumu yenile.","No response. Refresh to check the result.");
            case ""->text("Yetkiler sunucuyla eşitlenir","Permissions synced with the server");
            default->text("İşlem yapılamadı. Yeniden dene.","Unable to complete. Try again.");
        };
        label(g,message,150,574,7,258,view.notice.equals("UPDATED")?GREEN:
                !view.notice.isEmpty()&&!view.busy()?RED:MUTED,true);
    }

    private void renderConfirmation(GuiGraphics g) {
        beginOverlayLayer(g);
        g.pose().pushPose();
        g.pose().translate(0,0,200);
        g.fill(x(8),y(40),x(292),y(587),0xC7080D16);
        g.flush();
        box(g,22,206,278,416,18,LINE);box(g,23,207,277,415,17,CARD);
        avatar(g,"!",132,226,36,confirmation.enabled?0xFF493246:0xFF284638);
        label(g,confirmation.label,150,278,15,224,TEXT,true);
        label(g,shortName(view.selected.name,24),150,310,12,222,BLUE,true);
        label(g,text("Bu oyuncu için değişiklik uygulansın mı?","Apply this change to this player?"),150,337,8,224,MUTED,true);
        button(g,35,372,144,401,text("Vazgeç","Cancel"),RAISED,TEXT,true);
        button(g,154,372,265,401,text("Uygula","Apply"),0xFF345DA0,0xFFFFFFFF,true);
        g.flush();
        g.pose().popPose();
    }

    private void button(GuiGraphics g,int left,int top,int right,int bottom,String title,int bg,int fg,boolean enabled) {
        boolean hover=enabled&&inside(mouseX,mouseY,left,top,right,bottom);
        box(g,left,top,right,bottom,8,!enabled?0xFF1A2230:hover?0xFF384D6E:bg);
        label(g,title,(left+right)/2f,top+(bottom-top-8)/2f,8,right-left-12,enabled?fg:0xFF66758C,true);
    }
    private void pill(GuiGraphics g,int left,int top,int right,int bottom,String title,int bg,int fg) {
        box(g,left,top,right,bottom,7,bg);label(g,title,(left+right)/2f,top+(bottom-top-7)/2f,7,right-left-8,fg,true);
    }
    private void avatar(GuiGraphics g,String title,int left,int top,int width,int color) {
        box(g,left,top,left+width,top+width,Math.max(6,width/3),color);
        label(g,title,left+width/2f,top+width*.25f,width*.44f,width-8,TEXT,true);
    }
    private void box(GuiGraphics g,int left,int top,int right,int bottom,int radius,int color) {
        drawRoundedRect(g,x(left),y(top),x(right),y(bottom),size(radius),color);
    }
    private void label(GuiGraphics g,String value,float left,float top,float height,float width,int color,boolean center) {
        if(center)PhoneModernText.drawCenteredColoredFitted(g,value,x(left),y(top),size(height),size(width),color);
        else PhoneModernText.drawColoredFitted(g,value,x(left),y(top),size(height),size(width),color);
    }
    private static String text(String tr,String en) { return PhoneSettingsStore.text(tr,en); }
    private static String role(PhoneModerationState.Player player) { return player.operator?"OP":player.moderator?text("Moderatör","Moderator"):text("Oyuncu","Player"); }
    private static String initial(String name) { return name==null||name.isBlank()?"?":name.substring(0,name.offsetByCodePoints(0,1)).toUpperCase(Locale.ROOT); }
    private static String shortName(String name,int limit) { return name==null?"?":name.length()>limit?name.substring(0,limit-1)+"…":name; }
    private float searchScale() { return Math.max(.1f,(x(216)-x(34))/178f); }

    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(!PhoneModerationClient.canManage()) return super.mouseClicked(mx,my,button);
        if(button!=0) return confirmation!=null||super.mouseClicked(mx,my,button);
        if(confirmation!=null) {
            if(inside(mx,my,35,372,144,401)) confirmation=null;
            else if(inside(mx,my,154,372,265,401)) apply();
            return true;
        }
        if(view.selected==null) {
            if(inside(mx,my,28,158,220,195)) {
                search.setFocused(true);search.mouseClicked((mx-x(34))/searchScale(),(my-y(171))/searchScale(),button);return true;
            }
            search.setFocused(false);
            if(!view.busy()) {
                if(inside(mx,my,228,164,271,189)||inside(mx,my,170,529,231,560)) { requestList(view.page);return true; }
                if(inside(mx,my,22,529,63,560)&&view.page>0) { requestList(view.page-1);return true; }
                if(inside(mx,my,240,529,278,560)&&view.page+1<view.pages) { requestList(view.page+1);return true; }
                for(int i=0;i<Math.min(8,view.players.size());i++) if(inside(mx,my,22,224+i*37,278,258+i*37)) { select(view.players.get(i));return true; }
            }
        } else if(!view.busy()) {
            if(inside(mx,my,22,54,63,84)||inside(mx,my,22,529,198,560)) { back();return true; }
            if(inside(mx,my,208,529,278,560)) { send(view.begin("DETAIL",System.nanoTime()));return true; }
            for(int i=0;i<3;i++) if(inside(mx,my,184,237+i*67,266,265+i*67)&&canBan(PLATFORMS.get(i))) {
                boolean ban=!view.selected.bans.contains(PLATFORMS.get(i));
                confirmation=new Confirmation("BAN",PLATFORMS.get(i),ban,APP_NAMES.get(i)+" · "+text(ban?"Banla":"Ban kaldır",ban?"Ban":"Unban"));return true;
            }
            if(inside(mx,my,34,480,266,499)&&canRole()) {
                boolean appoint=!view.selected.moderator;
                confirmation=new Confirmation("ROLE","",appoint,text(appoint?"Moderatör ata":"Moderatörlüğü kaldır",appoint?"Appoint moderator":"Remove moderator"));return true;
            }
        }
        return super.mouseClicked(mx,my,button);
    }

    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==GLFW.GLFW_KEY_ESCAPE) { back();return true; }
        if(confirmation!=null) {
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER) apply();
            return true;
        }
        if(view.selected==null) {
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER) { requestList(0);return true; }
            if(key==GLFW.GLFW_KEY_TAB) { search.setFocused(!search.isFocused());return true; }
            if(search.isFocused()&&search.keyPressed(key,scan,modifiers)) return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char character,int modifiers) {
        if(confirmation!=null) return true;
        return view.selected==null&&search.isFocused()?search.charTyped(character,modifiers):super.charTyped(character,modifiers);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy) {
        if(confirmation!=null) return true;
        if(view.selected==null&&search.isFocused()&&button==0)
            return search.mouseDragged((mx-x(34))/searchScale(),(my-y(171))/searchScale(),button,dx/searchScale(),dy/searchScale());
        return super.mouseDragged(mx,my,button,dx,dy);
    }
}

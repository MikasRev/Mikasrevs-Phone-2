package com.mikasrevs.newphone.marketplace.client;

import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.gui.*;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.gui.*;
import com.mikasrevs.newphone.marketplace.data.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Shared responsive 300x610 phone design coordinates, matching the supplied reference. */
public abstract class MarketplaceBaseScreen extends AbstractZPhoneScreen {
    protected static final int BG=0xFF101114,WHITE=0xFF1D2026,INK=0xFFF8F3E7,MUTED=0xFFAFA99C;
    protected static final int GREEN=0xFFFFD451,PALE=0xFF292C33,LINE=0xFF3C3B36,BLUE=0xFF344256,RED=0xFFFF8791;
    protected static final int YELLOW=0xFFFFD451,YELLOW_DARK=0xFFE0AD19,DARK=0xFF15171C,LIGHT=0xFFFFFBEB,NAV_MUTED=0xFF888D97;
    protected final List<Hit> hits=new ArrayList<>();
    protected final List<Field> fields=new ArrayList<>();
    protected Field focused;
    protected int fieldViewportTop=0,fieldViewportBottom=610;
    protected String banner="";
    protected long bannerUntil;
    protected boolean bannerSuccess;
    protected MarketplaceBaseScreen(String title){super(Component.literal(title));}
    protected void begin(GuiGraphics g,String title,String subtitle,boolean back){
        renderBackground(g);g.fill(x(3),y(8),x(297),y(606),BG);
        g.fillGradient(x(3),y(35),x(297),y(115),0xFF29261D,BG);
        drawStatusBar(g,LIGHT,LINE);hits.clear();
        if(back)button(g,"‹",18,51,27,31,PALE,YELLOW,()->PhoneNavigationState.backOrHome(minecraft));
        text(g,title,back?55:18,53,20,back?226:263,LIGHT);
        text(g,subtitle,back?55:18,82,7,back?226:263,MUTED);
    }
    protected void finish(GuiGraphics g,String selected,boolean navigation){
        if(navigation)nav(g,selected);
        if(!banner.isEmpty()&&System.currentTimeMillis()<bannerUntil){
            g.flush();g.pose().pushPose();g.pose().translate(0,0,200);
            box(g,14,460,272,55,LINE);box(g,15,461,270,53,WHITE);
            text(g,bannerSuccess?"✓":"!",25,476,15,19,bannerSuccess?YELLOW:RED);
            wrap(g,banner,53,472,219,8,3,bannerSuccess?INK:RED);
            g.flush();g.pose().popPose();
        }
        drawHomeIndicator(g);drawFrame(g);
    }
    protected void nav(GuiGraphics g,String selected){
        g.flush();
        g.fill(x(3),y(556),x(297),y(591),DARK);
        g.fill(x(18),y(555),x(282),y(556),LINE);
        String[] ids={"MARKET","MINE","POST","PROFILE"};
        String[] names={tr("Pazar","Market"),tr("İlanlarım","My listings"),tr("İlan ver","Post"),tr("Profil","Profile")};
        for(int i=0;i<4;i++){
            int left=10+i*70;String id=ids[i];boolean active=id.equals(selected);
            int color=active?YELLOW:NAV_MUTED;
            if(active&&i!=2)box(g,left+12,559,46,23,0x26FFD451);
            drawNavIcon(g,id,left+35,569,id.equals("POST")?YELLOW:color);
            PhoneModernText.drawCenteredColoredFitted(g,names[i],x(left+35),y(584),size(6),size(62),color);
            hits.add(new Hit(left,557,left+70,591,()->navigate(id)));
        }
    }
    private void drawNavIcon(GuiGraphics g,String id,int center,int cy,int color){
        if(id.equals("POST")){
            fillCircle(g,x(center),y(cy),size(14),color);
            g.fill(x(center-1),y(cy-7),x(center+2),y(cy+8),DARK);
            g.fill(x(center-7),y(cy-1),x(center+8),y(cy+2),DARK);return;
        }
        if(id.equals("MARKET")){
            drawRoundedRect(g,x(center-9),y(cy-5),x(center+9),y(cy+9),size(3),color);
            g.fill(x(center-6),y(cy-2),x(center+6),y(cy+7),DARK);
            g.fill(x(center-4),y(cy-9),x(center+4),y(cy-7),color);
            g.fill(x(center-6),y(cy-8),x(center-4),y(cy-3),color);
            g.fill(x(center+4),y(cy-8),x(center+6),y(cy-3),color);return;
        }
        if(id.equals("MINE")){
            drawRoundedRect(g,x(center-9),y(cy-8),x(center+9),y(cy+9),size(2),color);
            for(int row=0;row<3;row++){
                g.fill(x(center-6),y(cy-5+row*5),x(center-3),y(cy-2+row*5),DARK);
                g.fill(x(center),y(cy-5+row*5),x(center+6),y(cy-3+row*5),DARK);
            }return;
        }
        fillCircle(g,x(center),y(cy-5),size(5),color);
        drawRoundedRect(g,x(center-9),y(cy+1),x(center+9),y(cy+9),size(5),color);
    }
    private void navigate(String id){
        switch(id){
            case "MARKET"->open(new MarketplaceScreen());case "MINE"->open(new MarketplaceMyListingsScreen());
            case "POST"->open(new MarketplaceCreateListingScreen(null));
            case "PROFILE"->open(new MarketplaceProfileScreen());default->{ }
        }
    }
    protected void open(AbstractZPhoneScreen screen){PhoneNavigationState.open(minecraft,this,screen);}
    protected void bank(){open(new ZPhoneBankScreen());}
    protected void notice(String text){banner=text==null?"":text;bannerSuccess=false;bannerUntil=System.currentTimeMillis()+6000;}
    public void serverNotice(String code){String message=MarketplaceClient.message(code);if(!message.isEmpty()){notice(message);bannerSuccess=Set.of("CREATED","UPDATED","REMOVED","PURCHASED").contains(code);}}
    protected void box(GuiGraphics g,int left,int top,int width,int height,int color){drawRoundedRect(g,x(left),y(top),x(left+width),y(top+height),size(8),color);}
    protected void text(GuiGraphics g,String value,int left,int top,int height,int maxWidth,int color){
        PhoneModernText.drawColoredFitted(g,value==null?"":value,x(left),y(top),size(Math.max(6,height)),size(maxWidth),color);
    }
    protected void button(GuiGraphics g,String label,int left,int top,int width,int height,int background,int color,Runnable action){
        box(g,left,top,width,height,action==null?PALE:background);PhoneModernText.drawCenteredColoredFitted(g,label,x(left+width/2f),y(top+(height-9)/2f),size(9),size(width-12),action==null?NAV_MUTED:color);
        if(action!=null)hits.add(new Hit(left,top,left+width,top+height,action));
    }
    protected void photo(GuiGraphics g,String id,int left,int top,int width,int height){
        box(g,left,top,width,height,PALE);var texture=PhonePhotoStore.textureFor(minecraft,id);
        if(texture==null){
            int cx=left+width/2,cy=top+height/2;
            box(g,cx-15,cy-12,30,24,LINE);box(g,cx-12,cy-9,24,18,PALE);
            fillCircle(g,x(cx-5),y(cy-3),size(3),MUTED);
            g.fill(x(cx-8),y(cy+5),x(cx+8),y(cy+7),MUTED);return;
        }
        float ratio=width/(float)height;int sw=texture.width(),sh=texture.height();
        if(sw/(float)sh>ratio)sw=Math.round(sh*ratio);else sh=Math.round(sw/ratio);
        g.blit(texture.location(),x(left+1),y(top+1),size(width-2),size(height-2),(texture.width()-sw)/2f,(texture.height()-sh)/2f,sw,sh,texture.width(),texture.height());
    }
    protected int wrap(GuiGraphics g,String value,int left,int top,int width,int height,int maxLines,int color){
        int readable=Math.max(8,height);StringBuilder buffer=new StringBuilder(value==null?"":value);PhoneChatDraft draft=new PhoneChatDraft(buffer,2000);
        var lines=draft.lines(size(width),s->PhoneModernText.width(s,size(readable)));int row=0;
        for(var line:lines){if(row>=maxLines)break;text(g,buffer.substring(line.start(),line.end()),left,top+row*(readable+4),readable,width,color);row++;}
        return row*(readable+4);
    }
    protected static String tr(String turkish,String english){return com.mikasrevs.newphone.client.settings.PhoneSettingsStore.text(turkish,english);}
    protected String listingPrice(MarketplaceListing listing){return listing.category.equals("ITEMS")?tr("↔ Takas","↔ Trade"):listing.priceText;}
    protected String category(String category){return switch(category){case "CARS"->tr("Araçlar","Cars");case "HOUSES"->tr("Evler","Houses");case "ITEMS"->tr("Eşyalar","Items");case "OTHERS"->tr("Diğer","Other");default->tr("Tümü","All");};}
    protected String status(String value){return switch(value){case "ACTIVE"->tr("Aktif","Active");case "SOLD"->tr("Satıldı","Sold");case "EXPIRED"->tr("Süresi doldu","Expired");case "UNAVAILABLE"->tr("Kapalı","Unavailable");case "REVIEW"->tr("İncelemede","Review");default->value;};}
    protected void panel(GuiGraphics g,int left,int top,int width,int height){box(g,left,top,width,height,LINE);box(g,left+1,top+1,width-2,height-2,WHITE);}

    protected Field field(String label,String value,int limit){Field f=new Field(label,value,limit);fields.add(f);return f;}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);
        if(super.mouseClicked(mx,my,button))return true;
        for(int i=hits.size()-1;i>=0;i--){Hit hit=hits.get(i);if(inside(mx,my,hit.l,hit.t,hit.r,hit.b)){focused=null;hit.action.run();return true;}}
        focused=null;for(Field f:fields)if(f.visible&&inside(mx,my,f.l,Math.max(f.t,fieldViewportTop),f.l+f.w,Math.min(f.t+f.h,fieldViewportBottom))){focused=f;f.editor.moveTo(f.value.length(),false);return true;}
        return true;
    }
    @Override public boolean charTyped(char c,int modifiers){if(focused!=null){focused.editor.insert(String.valueOf(c));focused.changedAt=System.currentTimeMillis();return true;}return super.charTyped(c,modifiers);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE){PhoneNavigationState.backOrHome(minecraft);return true;}
        if(focused!=null){
            var e=focused.editor;
            if(hasControlDown()&&key==GLFW.GLFW_KEY_A)e.selectAll();
            else if(hasControlDown()&&key==GLFW.GLFW_KEY_V)e.insert(minecraft.keyboardHandler.getClipboard());
            else if(hasControlDown()&&key==GLFW.GLFW_KEY_C)minecraft.keyboardHandler.setClipboard(e.selectedText());
            else if(hasControlDown()&&key==GLFW.GLFW_KEY_X){minecraft.keyboardHandler.setClipboard(e.selectedText());e.insert("");}
            else if(key==GLFW.GLFW_KEY_BACKSPACE)e.delete(true);else if(key==GLFW.GLFW_KEY_DELETE)e.delete(false);
            else if(key==GLFW.GLFW_KEY_LEFT)e.move(-1,hasShiftDown());else if(key==GLFW.GLFW_KEY_RIGHT)e.move(1,hasShiftDown());
            else if(key==GLFW.GLFW_KEY_HOME)e.moveTo(0,hasShiftDown());else if(key==GLFW.GLFW_KEY_END)e.moveTo(focused.value.length(),hasShiftDown());
            else if(key==GLFW.GLFW_KEY_ENTER&&focused.h>40)e.insert("\n");
            else if(key==GLFW.GLFW_KEY_TAB){focused=fields.get((fields.indexOf(focused)+1)%fields.size());}
            else return super.keyPressed(key,scan,mods);
            focused.changedAt=System.currentTimeMillis();return true;
        }return super.keyPressed(key,scan,mods);
    }
    protected record Hit(int l,int t,int r,int b,Runnable action) { public Hit {} }
    protected final class Field {
        final String label;final StringBuilder value;final PhoneChatDraft editor;int l,t,w,h;long changedAt;boolean visible;
        Field(String label,String value,int limit){this.label=label;this.value=new StringBuilder(value);editor=new PhoneChatDraft(this.value,limit);}
        String get(){return value.toString().trim();}
        void clear(){editor.selectAll();editor.insert("");}
        void draw(GuiGraphics g,int left,int top,int width,int height){
            l=left;t=top;w=width;h=height;
            visible=top+height>fieldViewportTop&&top<fieldViewportBottom;
            box(g,left,top,width,height,focused==this?YELLOW:LINE);box(g,left+1,top+1,width-2,height-2,WHITE);
            int font=8;var lines=editor.lines(size(width-18),s->PhoneModernText.width(s,size(font)));
            int rows=Math.max(1,(height-14)/12);int cursorRow=editor.cursorLine(lines),start=Math.max(0,cursorRow-rows+1);
            g.enableScissor(x(left+5),y(top+3),x(left+width-5),y(top+height-3));
            if(value.isEmpty())text(g,label,left+9,top+9,font,width-18,MUTED);
            for(int row=start;row<Math.min(lines.size(),start+rows);row++){
                var line=lines.get(row);text(g,value.substring(line.start(),line.end()),left+9,top+8+(row-start)*12,font,width-18,INK);
                if(focused==this&&row==cursorRow&&System.currentTimeMillis()%1000<550){int cursor=x(left+9)+PhoneModernText.width(value.substring(line.start(),Math.min(line.end(),editor.cursor())),size(font));g.fill(cursor,y(top+7+(row-start)*12),cursor+1,y(top+18+(row-start)*12),GREEN);}
            }g.flush();g.disableScissor();
        }
    }
}

package com.mikasrevs.newphone.marketplace.trade;

import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.gui.PhoneChatDraft;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Private, plain-text conversation rendered beside the phone. */
final class TradeChatPanel {
    private static final int GOLD=0xFFFFD451,INK=0xFFF2EFE8,MUTED=0xFFAAA699,DARK=0xFF15191E,LINE=0xFF353A41;
    private final StringBuilder draft=new StringBuilder();
    private final PhoneChatDraft editor=new PhoneChatDraft(draft,TradeRequest.CHAT_LENGTH);
    private TradeChatLayout.Rect bounds=new TradeChatLayout.Rect(0,0,0,0);
    private boolean focused,enabled;
    private int scroll,maxScroll,font=14;
    private String deal="";
    private Consumer<String> sender;
    void blur(){focused=false;}
    void waiting(){enabled=false;}
    void clear(){editor.selectAll();editor.insert("");}
    void hide(){bounds=new TradeChatLayout.Rect(0,0,0,0);focused=false;}
    private static String tr(String a,String b){return PhoneSettingsStore.text(a,b);}
    private void text(GuiGraphics g,String s,int x,int y,int h,int w,int color){
        String shown=s==null?"":s;
        if(PhoneModernText.width(shown,h)>w){
            int end=shown.length();
            while(end>0&&PhoneModernText.width(shown.substring(0,end)+"…",h)>w)end--;
            shown=shown.substring(0,end)+"…";
        }
        PhoneModernText.drawColored(g,shown,x,y,h,color);
    }
    private static void round(GuiGraphics g,int x,int y,int w,int h,int color){
        int r=Math.min(6,Math.min(w,h)/2);
        g.fill(x+r,y,x+w-r,y+h,color);g.fill(x,y+r,x+w,y+h-r,color);
        for(int row=0;row<r;row++){
            int inset=r-(int)Math.sqrt(r*r-(r-row)*(r-row));
            g.fill(x+inset,y+row,x+w-inset,y+row+1,color);
            g.fill(x+inset,y+h-row-1,x+w-inset,y+h-row,color);
        }
    }
    void render(GuiGraphics g,TradeState state,TradeChatLayout.Rect rect,boolean enabled,Consumer<String> sender){
        if(!deal.equals(state.id)){deal=state.id;clear();scroll=0;focused=false;}
        bounds=rect;this.enabled=enabled;this.sender=sender;
        if(rect.width()<60||rect.height()<160){hide();return;}
        int x=rect.x(),y=rect.y(),w=rect.width(),h=rect.height();font=w<110?12:14;
        round(g,x,y,w,h,LINE);round(g,x+1,y+1,w-2,h-2,DARK);
        round(g,x+8,y+10,3,29,GOLD);
        text(g,tr("Takas sohbeti","Trade chat"),x+17,y+9,15,w-25,GOLD);
        text(g,state.otherName,x+17,y+29,font,w-25,INK);
        g.fill(x+8,y+51,x+w-8,y+52,LINE);
        int top=y+60,bottom=y+h-82;
        record Bubble(List<String> lines,boolean own,int width,int height){}
        List<Bubble> bubbles=new ArrayList<>();
        int bodyWidth=Math.max(8,w-36),lineHeight=font+5,content=0;
        for(var message:state.chat){
            boolean own=message.actor.equals(state.self);
            StringBuilder body=new StringBuilder(message.text);
            var wrapped=new PhoneChatDraft(body,TradeRequest.CHAT_LENGTH).lines(bodyWidth,s->PhoneModernText.width(s,font));
            List<String> lines=new ArrayList<>();int widest=0;
            for(var line:wrapped){String value=body.substring(line.start(),line.end());lines.add(value);widest=Math.max(widest,PhoneModernText.width(value,font));}
            int bubbleHeight=27+lines.size()*lineHeight;
            bubbles.add(new Bubble(lines,own,Math.min(w-20,Math.max(40,widest+16)),bubbleHeight));
            content+=bubbleHeight+8;
        }
        int previousMax=maxScroll;maxScroll=Math.max(0,content-(bottom-top));
        if(scroll>0)scroll+=maxScroll-previousMax;
        scroll=Math.max(0,Math.min(scroll,maxScroll));
        g.enableScissor(x+4,top,x+w-4,Math.max(top+1,bottom));
        if(bubbles.isEmpty()){
            StringBuilder hint=new StringBuilder(tr("Takas hakkında buradan konuşabilirsiniz.","Discuss your trade here."));
            int yy=top+12;
            for(var line:new PhoneChatDraft(hint,TradeRequest.CHAT_LENGTH).lines(w-20,s->PhoneModernText.width(s,font))){text(g,hint.substring(line.start(),line.end()),x+10,yy,font,w-20,MUTED);yy+=lineHeight;}
        }
        int yy=top-maxScroll+scroll;
        for(var bubble:bubbles){
            if(yy+bubble.height()>=top&&yy<bottom){
                int bx=bubble.own()?x+w-10-bubble.width():x+10;
                round(g,bx,yy,bubble.width(),bubble.height(),bubble.own()?0xFF343022:0xFF242C36);
                text(g,bubble.own()?tr("Sen","You"):state.otherName,bx+8,yy+6,10,bubble.width()-16,bubble.own()?GOLD:0xFFACBFE0);
                int ty=yy+23;
                for(String line:bubble.lines()){text(g,line,bx+8,ty,font,bubble.width()-16,INK);ty+=lineHeight;}
            }
            yy+=bubble.height()+8;
        }
        g.flush();g.disableScissor();
        if(maxScroll>0){int track=bottom-top,thumb=Math.max(12,track*track/Math.max(track,content));int thumbY=top+(int)((maxScroll-scroll)/(double)maxScroll*(track-thumb));round(g,x+w-4,thumbY,2,thumb,0xFF7F7863);}
        int inputY=y+h-70;
        round(g,x+6,inputY,w-12,32,focused?GOLD:LINE);round(g,x+7,inputY+1,w-14,30,0xFF1B2027);
        String value=draft.toString();int start=editor.cursor();
        while(start>0&&PhoneModernText.width(value.substring(start-1,editor.cursor()),font)<w-26)start--;
        String shown=value.substring(start);
        g.enableScissor(x+11,inputY+3,x+w-11,inputY+29);
        if(value.isEmpty())text(g,tr("Mesaj yaz…","Write a message…"),x+13,inputY+10,font,w-26,MUTED);
        else PhoneModernText.drawColored(g,shown,x+13,inputY+10,font,INK);
        if(focused&&System.currentTimeMillis()%1000<550){int cx=x+13+PhoneModernText.width(value.substring(start,editor.cursor()),font);g.fill(cx,inputY+8,cx+1,inputY+font+12,GOLD);}
        g.flush();g.disableScissor();
        boolean canSend=enabled&&!value.isBlank();
        round(g,x+6,y+h-30,w-12,24,canSend?GOLD:LINE);
        text(g,enabled?tr("Gönder  ↵","Send  ↵"):tr("Bekleniyor…","Waiting…"),x+13,y+h-24,font,w-26,canSend?DARK:MUTED);
    }
    boolean click(double mx,double my,int button){
        if(!bounds.contains(mx,my)){focused=false;return false;}
        if(button==0){
            int localY=(int)my-bounds.y();
            focused=enabled&&localY>=bounds.height()-70&&localY<bounds.height()-38;
            if(focused)editor.moveTo(draft.length(),false);
            if(localY>=bounds.height()-30&&localY<bounds.height()-6)send();
        }return true;
    }
    boolean wheel(double mx,double my,double amount){if(!bounds.contains(mx,my))return false;scroll=Math.max(0,Math.min(maxScroll,scroll+(int)(amount*(font+4)*3)));return true;}
    private void insert(String value){editor.insert(value.replaceAll("[\\p{Cntrl}§]",""));}
    boolean character(char c){if(!focused)return false;if(enabled&&!Character.isISOControl(c)&&c!='§')insert(String.valueOf(c));return true;}
    private void send(){if(enabled&&!draft.toString().isBlank()&&sender!=null)sender.accept(draft.toString().strip());}
    boolean key(int key,int modifiers,Minecraft mc){
        if(!focused)return false;
        if(!enabled){if(key==GLFW.GLFW_KEY_ESCAPE)focused=false;return true;}
        boolean ctrl=(modifiers&GLFW.GLFW_MOD_CONTROL)!=0,shift=(modifiers&GLFW.GLFW_MOD_SHIFT)!=0;
        if(key==GLFW.GLFW_KEY_ESCAPE)focused=false;
        else if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)send();
        else if(ctrl&&key==GLFW.GLFW_KEY_A)editor.selectAll();
        else if(ctrl&&key==GLFW.GLFW_KEY_V)insert(mc.keyboardHandler.getClipboard());
        else if(ctrl&&key==GLFW.GLFW_KEY_C)mc.keyboardHandler.setClipboard(editor.selectedText());
        else if(ctrl&&key==GLFW.GLFW_KEY_X){mc.keyboardHandler.setClipboard(editor.selectedText());editor.insert("");}
        else if(key==GLFW.GLFW_KEY_BACKSPACE)editor.delete(true);
        else if(key==GLFW.GLFW_KEY_DELETE)editor.delete(false);
        else if(key==GLFW.GLFW_KEY_LEFT)editor.move(-1,shift);
        else if(key==GLFW.GLFW_KEY_RIGHT)editor.move(1,shift);
        else if(key==GLFW.GLFW_KEY_HOME)editor.moveTo(0,shift);
        else if(key==GLFW.GLFW_KEY_END)editor.moveTo(draft.length(),shift);
        return true;
    }
}

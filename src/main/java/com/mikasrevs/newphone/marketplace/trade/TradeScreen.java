package com.mikasrevs.newphone.marketplace.trade;

import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.marketplace.client.MarketplaceBaseScreen;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Responsive item trade view. Controls wait for correlated server replies. */
public final class TradeScreen extends MarketplaceBaseScreen {
    private boolean mailbox;
    private int inventoryPage,ticks,quantity=1;
    private TradeState.Row selected,hovered;
    private String renderedId="";
    private long renderedRevision;
    private int renderedPage;
    private String selectedDeal="";
    private long selectedRevision;
    private TradeRequest pending;
    private long sentAt;
    private boolean timedOut;
    private final TradeChatPanel chatPanel=new TradeChatPanel();
    private String chatRequest="";
    private long chatSentAt;
    private boolean confirming;
    private String confirmationId="",renderedGive="",renderedReceive="",confirmationGive="",confirmationReceive="";
    private long confirmationRevision;
    private final Map<String,ItemStack> itemCache=new HashMap<>();
    public TradeScreen(){this(false);}
    public TradeScreen(boolean mailbox){super("Marketplace Trade");this.mailbox=mailbox;}
    @Override protected float phoneWidthFactor(){return TradeClient.state().status.equals("ACTIVE")?Math.min(.54F,super.phoneWidthFactor()):super.phoneWidthFactor();}
    @Override protected void init(){super.init();TradeClient.sync(TradeClient.state().page);}
    @Override public void tick(){
        super.tick();
        if(!chatRequest.isEmpty()){
            String code=TradeClient.result(chatRequest);
            if(code!=null){chatRequest="";if(code.equals("TRADE_CHAT_SENT"))chatPanel.clear();else notice(TradeClient.message(code));}
            else if(System.currentTimeMillis()-chatSentAt>12000){chatRequest="";notice(tr("Mesaj yanıtı gecikti. Sohbeti kontrol et.","Message reply delayed. Check the conversation."));}
        }
        if(pending!=null){String code=TradeClient.result(pending.requestId);if(code!=null){pending=null;timedOut=false;selected=null;notice(TradeClient.message(code));bannerSuccess=Set.of("TRADE_COMPLETE","TRADE_CLAIMED","TRADE_SENT","TRADE_ACCEPTED","TRADE_READY").contains(code);TradeClient.sync(TradeClient.state().page);}
            else if(!timedOut&&System.currentTimeMillis()-sentAt>12000){timedOut=true;notice(tr("Yanıt gecikti. Yenile ve mevcut takası kontrol et.","Reply delayed. Refresh and check the current trade."));}}
        if(++ticks>=60){ticks=0;TradeClient.sync(TradeClient.state().page);}
    }
    private void submit(TradeRequest request){
        if(pending!=null)return;
        request.id=renderedId;request.revision=renderedRevision;request.page=renderedPage;
        if(request.action.equals("ADD")){request.id=selectedDeal;request.revision=selectedRevision;}
        if(request.action.equals("CONFIRM")){request.id=confirmationId;request.revision=confirmationRevision;confirming=false;}
        pending=request;timedOut=false;sentAt=System.currentTimeMillis();TradeClient.send(request);
    }
    private void action(String action){submit(TradeClient.command(action));}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        TradeState s=TradeClient.state();renderedId=s.id;renderedRevision=s.revision;renderedPage=s.page;hovered=null;
        renderedGive=summary(s.mine);renderedReceive=summary(s.theirs);
        begin(g,tr("Takas","Trade"),tr("Marketplace · güvenli eşya takası","Marketplace · secure item trading"),true);
        if(selected!=null||confirming){if(confirming)confirmation(g,s);else selection(g,mx,my);finish(g,"",false);chat(g,s,false);return;}
        button(g,tr("Takas","Trade")+(TradeClient.unread()>0?" · 1":""),18,105,128,25,mailbox?PALE:YELLOW,mailbox?INK:DARK,()->mailbox=false);
        button(g,tr("Otomatik iade","Auto delivery")+" · "+s.mailCount,154,105,128,25,mailbox?YELLOW:PALE,mailbox?DARK:INK,()->{mailbox=true;TradeClient.sync(s.page);});
        if(mailbox)mail(g,s,mx,my);else if(s.status.equals("ACTIVE"))active(g,s,mx,my);else invitation(g,s);
        if(timedOut)button(g,tr("İsteği kontrol et","Check request"),18,532,264,31,YELLOW,DARK,()->{TradeClient.sync(s.page);pending=null;timedOut=false;});
        finish(g,"",false);
        chat(g,s,true);
        if(hovered!=null){ItemStack stack=item(hovered);if(!stack.isEmpty())g.renderTooltip(minecraft.font,stack,mx,my);}
    }
    private void active(GuiGraphics g,TradeState s,int mx,int my){
        offer(g,s.self,tr("Senin teklifin","Your offer"),s.ready,s.mine,18,140,true,mx,my);
        text(g,"↔",135,260,19,31,YELLOW);
        offer(g,s.other,s.otherName+tr(" · teklifi"," · offer"),s.otherReady,s.theirs,18,286,false,mx,my);
        text(g,tr("ENVANTERİNDEN SEÇ","SELECT FROM YOUR INVENTORY"),20,407,7,215,YELLOW);
        button(g,"‹",229,401,23,20,PALE,YELLOW,inventoryPage>0?()->inventoryPage--:null);
        button(g,"›",259,401,23,20,PALE,YELLOW,inventoryPage<4?()->inventoryPage++:null);
        for(int i=0;i<8;i++){
            int slot=inventoryPage*8+i,left=24+(i%4)*65,top=429+(i/4)*36;
            var row=s.inventory.stream().filter(r->r.slot==slot).findFirst().orElse(null);
            slot(g,row,left,top,57,31,mx,my,pending==null&&row!=null&&!row.protectedPhone?()->{selected=row;quantity=1;selectedDeal=renderedId;selectedRevision=renderedRevision;chatPanel.blur();}:null);
        }
        text(g,tr("Geri almak için kendi teklifine dokun.","Tap your own offer to return an item."),20,502,6,260,MUTED);
        text(g,tr("Teklif değişirse iki onay da sıfırlanır.","Changing an offer resets both approvals."),20,516,7,260,MUTED);
        boolean allowed=pending==null;
        button(g,pending!=null?tr("İşleniyor…","Working…"):s.ready?tr("Onayı geri al","Withdraw approval"):tr("Onayla","Approve"),18,532,264,34,s.ready?PALE:YELLOW,s.ready?YELLOW:DARK,allowed?()->{if(s.ready)action("UNREADY");else{confirming=true;confirmationId=renderedId;confirmationRevision=renderedRevision;confirmationGive=renderedGive;confirmationReceive=renderedReceive;chatPanel.blur();}}:null);
        button(g,tr("Takası iptal et","Cancel trade"),18,575,264,18,PALE,RED,pending==null?()->action("CANCEL"):null);
    }
    private void offer(GuiGraphics g,String uuid,String name,boolean ready,List<TradeState.Row> rows,int left,int top,boolean own,int mx,int my){
        panel(g,left,top,264,110);PhonePlayerHeadRenderer.draw(g,uuid,x(left+11),y(top+10),size(24));
        text(g,name,left+44,top+11,10,151,INK);text(g,ready?tr("✓ Hazır","✓ Ready"):tr("Bekliyor","Waiting"),left+199,top+14,7,54,ready?0xFF6AD98B:MUTED);
        for(int i=0;i<8;i++){
            var row=i<rows.size()?rows.get(i):null;int xx=left+6+(i%4)*65,yy=top+40+(i/4)*32;
            slot(g,row,xx,yy,57,28,mx,my,own&&row!=null&&pending==null?()->{var r=TradeClient.command("REMOVE");r.parcel=row.id;submit(r);}:null);
        }
    }
    private void invitation(GuiGraphics g,TradeState s){
        panel(g,18,160,264,286);
        boolean requested=s.status.equals("REQUESTED");
        if(requested){
            PhonePlayerHeadRenderer.draw(g,s.other,x(122),y(183),size(56));
            text(g,s.otherName,40,255,19,220,INK);text(g,s.title,35,284,11,230,YELLOW);
            wrap(g,s.incoming?tr("Bu oyuncu seninle eşya takası yapmak istiyor.","This player wants to trade items with you."):tr("Takas isteğin gönderildi. Oyuncunun kabul etmesi bekleniyor.","Request sent. Waiting for the player to accept."),35,319,229,9,4,MUTED);
            if(s.incoming)button(g,tr("Takası kabul et","Accept trade"),35,383,230,35,YELLOW,DARK,pending==null?()->action("ACCEPT"):null);
            button(g,s.incoming?tr("Reddet","Decline"):tr("İsteği iptal et","Cancel request"),35,requested&&s.incoming?425:383,230,requested&&s.incoming?22:35,PALE,RED,pending==null?()->action("CANCEL"):null);
        }else{
            text(g,"↔",122,191,39,58,YELLOW);
            text(g,s.status.equals("COMPLETE")?tr("Takas tamamlandı","Trade completed"):tr("Yeni bir takas başlat","Start a new trade"),35,265,16,230,INK);
            wrap(g,tr("Eşya ilanında Takas Gönder'e dokun. İki oyuncu da çevrimiçi olmalı.","Tap Send Trade on an item listing. Both players must be online."),35,306,229,9,4,MUTED);
            button(g,tr("Otomatik teslimat","Automatic delivery")+" · "+s.mailCount,35,383,230,35,YELLOW,DARK,()->mailbox=true);
        }
        button(g,tr("Yenile","Refresh"),18,490,264,31,PALE,YELLOW,()->TradeClient.sync(s.page));
    }
    private void mail(GuiGraphics g,TradeState s,int mx,int my){
        text(g,tr("OTOMATİK TESLİMAT","AUTOMATIC DELIVERY"),20,150,8,260,YELLOW);
        wrap(g,tr("Eşyalar doğrudan envanterine gelir. Envanter doluysa yer açınca, çevrimdışıysan girişte otomatik verilir.","Items go straight to your inventory. Make room if it is full; offline returns arrive on login."),20,172,258,8,4,MUTED);
        if(s.mail.isEmpty()){panel(g,18,252,264,142);text(g,tr("Bekleyen eşyan yok","No items waiting"),35,297,16,230,INK);}
        for(int i=0;i<s.mail.size();i++){
            var row=s.mail.get(i);int top=231+i*34;
            slot(g,row,20,top,36,29,mx,my,null);text(g,row.name,64,top+3,9,145,INK);text(g,"× "+row.count,64,top+17,7,142,MUTED);
            text(g,tr("Otomatik","Auto"),225,top+10,7,57,MUTED);
        }
        text(g,(s.page+1)+" / "+Math.max(1,(s.mailCount+7)/8),125,516,8,50,MUTED);
        button(g,"‹",18,507,44,27,PALE,YELLOW,s.page>0?()->TradeClient.sync(s.page-1):null);
        button(g,"›",238,507,44,27,PALE,YELLOW,(s.page+1)*8<s.mailCount?()->TradeClient.sync(s.page+1):null);
        button(g,tr("Yenile","Refresh"),18,553,264,34,PALE,INK,()->TradeClient.sync(s.page));
    }
    private void selection(GuiGraphics g,int mx,int my){
        panel(g,18,173,264,282);text(g,tr("TEKLİFE EKLE","ADD TO OFFER"),35,190,8,230,YELLOW);
        slot(g,selected,35,220,58,58,mx,my,null);text(g,selected.name,105,229,12,158,INK);
        text(g,tr("Mevcut: ","Available: ")+selected.count,105,255,8,158,MUTED);
        quantity=Math.min(quantity,Math.min(64,selected.count));
        button(g,"−",35,304,51,32,PALE,YELLOW,pending==null&&quantity>1?()->quantity--:null);
        text(g,Integer.toString(quantity),136,311,17,50,INK);
        button(g,"+",214,304,51,32,PALE,YELLOW,pending==null&&quantity<Math.min(64,selected.count)?()->quantity++:null);
        button(g,tr("Tümünü seç","Select all"),35,345,230,24,PALE,INK,pending==null?()->quantity=Math.min(64,selected.count):null);
        button(g,pending==null?tr("Eşyayı ekle","Add item"):tr("İşleniyor…","Working…"),35,383,230,34,YELLOW,DARK,pending==null?()->{var r=TradeClient.command("ADD");r.slot=selected.slot;r.count=quantity;r.fingerprint=selected.fingerprint;submit(r);}:null);
        button(g,tr("Vazgeç","Back"),35,427,230,19,PALE,MUTED,pending==null?()->selected=null:null);
        wrap(g,tr("Eklenen eşya takas bitene kadar sunucuda saklanır.","Added items remain in server custody until the trade ends."),24,477,252,8,3,MUTED);
        if(timedOut)button(g,tr("İsteği kontrol et","Check request"),18,536,264,32,YELLOW,DARK,()->{selected=null;pending=null;timedOut=false;TradeClient.sync(TradeClient.state().page);});
    }
    private void slot(GuiGraphics g,TradeState.Row row,int left,int top,int width,int height,int mx,int my,Runnable click){
        box(g,left,top,width,height,LINE);box(g,left+1,top+1,width-2,height-2,DARK);
        if(row!=null){
            ItemStack stack=item(row);int icon=Math.min(22,height-6);
            g.pose().pushPose();g.pose().translate(x(left+4),y(top+(height-icon)/2f),0);g.pose().scale(size(icon)/16f,size(icon)/16f,1);g.renderItem(stack,0,0);g.pose().popPose();
            if(width>=50)text(g,Integer.toString(row.count),left+width-23,top+height-13,7,20,INK);
            if(row.protectedPhone)text(g,tr("Kilitli","Locked"),left+3,top+2,6,width-6,YELLOW);
            if(inside(mx,my,left,top,left+width,top+height))hovered=row;
            if(click!=null)hits.add(new Hit(left,top,left+width,top+height,click));
        }
    }
    private ItemStack item(TradeState.Row row){
        String key=row.item+"|"+row.tag+"|"+row.count;
        if(itemCache.size()>128)itemCache.clear();
        return itemCache.computeIfAbsent(key,k->{try{ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(row.item)),row.count);if(!row.tag.isEmpty())stack.setTag(TagParser.parseTag(row.tag));return stack;}catch(Exception e){return ItemStack.EMPTY;}});
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE&&confirming){confirming=false;return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE&&selected!=null){if(pending==null)selected=null;return true;}
        if(chatPanel.key(key,modifiers,minecraft))return true;
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char c,int modifiers){return chatPanel.character(c)||super.charTyped(c,modifiers);}
    @Override public boolean mouseScrolled(double mx,double my,double delta){return chatPanel.wheel(mx,my,delta)||super.mouseScrolled(mx,my,delta);}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(chatPanel.click(mx,my,button))return true;
        if(!banner.isEmpty()&&System.currentTimeMillis()<bannerUntil&&inside(mx,my,14,460,286,515))return true;
        return super.mouseClicked(mx,my,button);
    }
    private static String summary(List<TradeState.Row> rows){return rows.stream().map(r->r.count+" × "+r.name).collect(java.util.stream.Collectors.joining(", "));}
    private void confirmation(GuiGraphics g,TradeState s){
        panel(g,18,151,264,347);
        text(g,tr("Emin misin?","Are you sure?"),35,173,22,230,YELLOW);
        text(g,tr("VERECEĞİN","YOU GIVE"),35,214,8,230,MUTED);
        wrap(g,confirmationGive.isEmpty()?tr("Hiçbir eşya","No items"):confirmationGive,35,232,230,9,4,INK);
        text(g,tr("ALACAĞIN","YOU RECEIVE"),35,294,8,230,MUTED);
        wrap(g,confirmationReceive.isEmpty()?tr("Hiçbir eşya","No items"):confirmationReceive,35,312,230,9,4,INK);
        boolean current=s.id.equals(confirmationId)&&s.revision==confirmationRevision&&s.status.equals("ACTIVE");
        wrap(g,!current?tr("Teklif değişti. Geri dönüp yeniden kontrol et.","Offer changed. Go back and review it."):confirmationReceive.isEmpty()&&!confirmationGive.isEmpty()?tr("Karşılığında eşya almadan vereceksin.","You will give these items without receiving anything."):tr("İki taraf onaylayınca takas tamamlanır.","The trade completes when both players approve."),35,369,230,8,3,current?MUTED:RED);
        button(g,tr("Evet, onayla","Yes, approve"),35,416,230,34,YELLOW,DARK,current&&pending==null?()->action("CONFIRM"):null);
        button(g,tr("Geri dön","Go back"),35,466,230,21,PALE,MUTED,()->confirming=false);
    }
    private void chat(GuiGraphics g,TradeState s,boolean interactive){
        if(!s.status.equals("ACTIVE")){chatPanel.hide();return;}
        boolean duo=com.mikasrevs.newphone.client.device.PhoneDeviceSession.isDuo();
        chatPanel.render(g,s,TradeChatLayout.place(width,height,x(duo?-13:-43),x(duo?633:343)),interactive&&chatRequest.isEmpty(),text->{
            if(!chatRequest.isEmpty())return;
            var request=TradeClient.command("CHAT");request.id=renderedId;request.text=text;
            chatRequest=TradeClient.send(request);chatSentAt=System.currentTimeMillis();chatPanel.waiting();
        });
    }
}

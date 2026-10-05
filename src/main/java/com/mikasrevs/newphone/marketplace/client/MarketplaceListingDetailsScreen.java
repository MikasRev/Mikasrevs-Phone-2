package com.mikasrevs.newphone.marketplace.client;

import com.mikasrevs.newphone.marketplace.data.MarketplaceListing;
import net.minecraft.client.gui.GuiGraphics;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class MarketplaceListingDetailsScreen extends MarketplaceBaseScreen {
    private final String id;
    private int scroll,photo,ticks;
    private boolean confirmDelete;
    public MarketplaceListingDetailsScreen(String id){super("Listing details");this.id=id;}
    @Override protected void init(){super.init();MarketplaceClient.detail(id);}
    @Override public void tick(){super.tick();if(++ticks>=80){ticks=0;MarketplaceClient.detail(id);}}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        MarketplaceListing listing=MarketplaceClient.state().detail;
        begin(g,tr("İlan detayları","Listing details"),tr("Aradığın şey burada olabilir","A closer look at your next find"),true);
        if(listing==null||!listing.id.equals(id)){
            panel(g,18,194,264,186);text(g,tr("İlan yükleniyor","Loading listing"),34,234,15,233,INK);
            wrap(g,tr("İlan bulunamazsa listeye dönüp tekrar deneyebilirsin.","If this listing is unavailable, return to the market and try again."),34,270,231,8,3,MUTED);
            button(g,tr("Yenile","Refresh"),34,329,232,31,YELLOW,DARK,()->MarketplaceClient.detail(id));
            finish(g,"MARKET",true);return;
        }
        g.flush();g.enableScissor(x(12),y(103),x(288),y(483));
        int top=105-scroll;
        photo=Math.max(0,Math.min(photo,listing.images.size()-1));
        photo(g,listing.images.isEmpty()?"":listing.images.get(photo),18,top,264,144);
        for(int i=0;listing.images.size()>1&&i<listing.images.size();i++){
            final int index=i;int left=18+i*67;
            if(i==photo)box(g,left,top+151,62,39,YELLOW);
            photo(g,listing.images.get(i),left+2,top+153,58,35);
            if(top+151<483&&top+190>103)hits.add(new Hit(left,Math.max(103,top+151),left+62,Math.min(483,top+190),()->photo=index));
        }
        int yy=top+(listing.images.size()>1?203:159);
        text(g,listing.title,20,yy,18,260,INK);
        text(g,listingPrice(listing),20,yy+29,17,260,YELLOW);
        text(g,category(listing.category)+"  ·  "+status(listing.status),20,yy+60,8,260,MUTED);
        panel(g,18,yy+83,264,65);
        text(g,tr("SATICI","SELLER"),30,yy+95,6,241,MUTED);
        text(g,listing.ownerName,30,yy+108,11,241,INK);
        text(g,listing.location.isBlank()?tr("Konum belirtilmedi","Location not provided"):listing.location,30,yy+129,7,241,MUTED);
        text(g,tr("AÇIKLAMA","DESCRIPTION"),20,yy+168,8,260,YELLOW);
        int descriptionHeight=wrap(g,listing.description,20,yy+188,258,9,180,INK);
        String date=DateTimeFormatter.ofPattern("dd MMM yyyy",com.mikasrevs.newphone.client.settings.PhoneSettingsStore.locale())
                .format(Instant.ofEpochMilli(listing.createdAt).atZone(ZoneId.systemDefault()));
        text(g,date+"  ·  "+listing.views+tr(" görüntülenme"," views"),20,yy+205+descriptionHeight,7,260,MUTED);
        wrap(g,listing.category.equals("ITEMS")?tr("Eşya takası için istek gönder. Her iki oyuncu da çevrimiçi olmalı.","Send a trade request. Both players must be online."):
                tr("Satıcıyla iletişime geçerek anlaş.","Contact the seller to arrange this sale."),20,yy+228+descriptionHeight,258,8,3,MUTED);
        int contentHeight=(yy-top)+228+descriptionHeight+60;
        scroll=Math.max(0,Math.min(scroll,Math.max(0,contentHeight-378)));
        g.flush();g.disableScissor();
        boolean own=listing.ownerUuid.equals(MarketplaceClient.state().selfUuid);
        if(own){
            boolean editable=java.util.Set.of("ACTIVE","EXPIRED","UNAVAILABLE").contains(listing.status);
            button(g,tr("Düzenle","Edit"),18,495,81,30,YELLOW,DARK,editable?()->open(new MarketplaceCreateListingScreen(listing)):null);
            button(g,listing.status.equals("ACTIVE")?tr("Kapat","Pause"):tr("Yenile","Relist"),107,495,81,30,PALE,INK,
                    editable?()->MarketplaceClient.send(MarketplaceClient.command(listing.status.equals("ACTIVE")?"UNAVAILABLE":"RELIST",id)):null);
            button(g,confirmDelete?tr("Onayla?","Confirm?"):tr("Kaldır","Remove"),196,495,86,30,confirmDelete?RED:PALE,confirmDelete?DARK:RED,
                    editable?()->{if(confirmDelete){MarketplaceClient.send(MarketplaceClient.command("DELETE",id));open(new MarketplaceMyListingsScreen());}else confirmDelete=true;}:null);
            text(g,listing.status.equals("REVIEW")?tr("Ödeme sunucu incelemesinde.","Payment locked for server review."):
                    tr("Bu ilanı yalnızca sen yönetebilirsin.","Only you can manage this listing."),20,537,7,260,MUTED);
        }else{
            button(g,listing.favorite?tr("♥ Kaydedildi","♥ Saved"):tr("♡ Kaydet","♡ Save"),18,495,85,30,PALE,YELLOW,()->MarketplaceClient.favorite(listing));
            button(g,tr("Satıcıya mesaj","Message seller"),111,495,171,30,PALE,INK,()->MarketplaceClient.contact(listing));
            if(listing.category.equals("ITEMS")&&listing.status.equals("ACTIVE"))
                button(g,tr("↔ Takas Gönder","↔ Send Trade"),18,531,264,23,YELLOW,DARK,()->com.mikasrevs.newphone.marketplace.trade.TradeClient.invite(listing));
            else text(g,listing.status.equals("ACTIVE")?tr("Satış için satıcıyla iletişime geç.","Message the seller to arrange this sale."):status(listing.status),21,537,7,258,MUTED);
        }
        finish(g,"MARKET",true);
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){
        if(inside(mx,my,12,103,288,483)){scroll=Math.max(0,scroll+(int)(-delta*30));return true;}return super.mouseScrolled(mx,my,delta);
    }
}

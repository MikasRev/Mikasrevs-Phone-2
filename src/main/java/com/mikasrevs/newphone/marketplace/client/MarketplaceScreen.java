package com.mikasrevs.newphone.marketplace.client;

import com.mikasrevs.newphone.marketplace.data.*;
import net.minecraft.client.gui.GuiGraphics;

/** Paged market with a gold accent, readable cards and separate scroll/navigation regions. */
public class MarketplaceScreen extends MarketplaceBaseScreen {
    final MarketplaceRequest query=new MarketplaceRequest();
    private final Field search=field(tr("İlan ara…","Search listings…"),"",100);
    private int scroll,ticks;
    private String sentSearch="";
    public MarketplaceScreen(){this("MARKET");}
    protected MarketplaceScreen(String section){super("Marketplace");query.section=section;}
    @Override protected void init(){super.init();refresh();}
    void refresh(){query.search=search.get();sentSearch=query.search;query.requestId="";MarketplaceClient.send(query);}
    void applyFilters(MarketplaceRequest filters){
        query.category=filters.category;query.currencyId=filters.currencyId;query.minPrice=filters.minPrice;query.maxPrice=filters.maxPrice;
        query.sort=filters.sort;query.favoritesOnly=filters.favoritesOnly;query.page=0;scroll=0;refresh();
    }
    @Override public void tick(){
        super.tick();
        if(!search.get().equals(sentSearch)&&System.currentTimeMillis()-search.changedAt>350){query.page=0;scroll=0;refresh();ticks=0;}
        else if(++ticks>=80){ticks=0;refresh();}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        String heading=switch(query.section){case "MINE"->tr("İlanlarım","My listings");case "FAVORITES"->tr("Favoriler","Favorites");case "PURCHASES"->tr("Alışverişlerim","Purchases");default->"Marketplace";};
        begin(g,heading,tr("Sunucunun pazarı · Al, sat, keşfet","Your server's market · Buy, sell, explore"),!query.section.equals("MARKET"));
        var state=MarketplaceClient.state();
        panel(g,18,103,176,32);
        text(g,tr("CÜZDAN","WALLET"),29,109,6,64,MUTED);
        String balance=MarketplaceClient.balances().isEmpty()?tr("Bankayı aç","Open Bank"):MarketplaceClient.balances().get(0).formatted();
        text(g,balance,29,121,8,126,YELLOW);
        button(g,"›",158,107,27,24,PALE,YELLOW,this::bank);
        button(g,tr("↔ Takas","↔ Trade")+(com.mikasrevs.newphone.marketplace.trade.TradeClient.unread()>0?" · 1":""),202,103,80,32,YELLOW,DARK,()->open(new com.mikasrevs.newphone.marketplace.trade.TradeScreen()));
        search.draw(g,18,146,203,33);
        button(g,tr("Filtre","Filter"),229,146,53,33,PALE,YELLOW,()->open(new MarketplaceFilterScreen(this,query)));
        if(query.section.equals("MINE")){
            String[] statuses={"ACTIVE","SOLD","EXPIRED","UNAVAILABLE","REVIEW"};
            String[] labels={tr("Aktif","Active"),tr("Satıldı","Sold"),tr("Doldu","Expired"),tr("Kapalı","Off"),tr("İnceleme","Review")};
            for(int i=0;i<statuses.length;i++){String status=statuses[i];boolean selected=query.status.equals(status);
                button(g,labels[i],18+i*54,192,50,25,selected?YELLOW:PALE,selected?DARK:MUTED,()->{query.status=status;query.page=0;scroll=0;refresh();});
            }
        }else{
            String[] categories={"CARS","HOUSES","ITEMS","OTHERS"};
            for(int i=0;i<4;i++){String category=categories[i];boolean selected=query.category.equals(category);
                button(g,category(category),18+i*67,192,63,25,selected?YELLOW:PALE,selected?DARK:MUTED,()->{
                    query.category=selected?"":category;
                    if(query.category.equals("ITEMS")){query.currencyId="";query.minPrice="";query.maxPrice="";if(query.sort.equals("LOWEST")||query.sort.equals("HIGHEST"))query.sort="NEWEST";}
                    query.page=0;scroll=0;refresh();
                });
            }
        }
        g.flush();g.enableScissor(x(12),y(226),x(288),y(518));
        scroll=Math.max(0,Math.min(scroll,Math.max(0,state.listings.size()*127-292)));
        if(state.listings.isEmpty()){
            panel(g,18,262,264,205);
            text(g,MarketplaceClient.receivedAt==0?tr("İlanlar yükleniyor…","Loading listings…"):tr("Henüz bir ilan yok","No listings found"),35,301,14,230,INK);
            wrap(g,tr("Kategoriyi veya filtreleri değiştir. İlk ilanı sen verebilirsin.","Try a different category or filter. You can post the first listing."),35,338,229,8,3,MUTED);
            if(MarketplaceClient.receivedAt!=0)button(g,tr("+ İlan ver","+ Post a listing"),35,410,230,34,YELLOW,DARK,()->open(new MarketplaceCreateListingScreen(null)));
        }
        for(int i=0;i<state.listings.size();i++){
            int top=227+i*127-scroll;if(top+119<226||top>518)continue;
            card(g,state.listings.get(i),top);
        }
        g.flush();g.disableScissor();
        button(g,"‹",18,526,34,24,PALE,YELLOW,state.page>0?()->{query.page=state.page-1;scroll=0;refresh();}:null);
        text(g,(state.page+1)+" / "+Math.max(1,(state.total+11)/12)+"  ·  "+state.total+tr(" ilan"," listings"),66,534,7,168,MUTED);
        button(g,"›",248,526,34,24,PALE,YELLOW,state.hasMore?()->{query.page=state.page+1;scroll=0;refresh();}:null);
        finish(g,query.section.equals("MINE")?"MINE":"MARKET",true);
    }
    private void card(GuiGraphics g,MarketplaceListing listing,int top){
        panel(g,18,top,264,119);
        hits.add(new Hit(18,Math.max(226,top),282,Math.min(518,top+119),()->open(new MarketplaceListingDetailsScreen(listing.id))));
        photo(g,listing.images.isEmpty()?"":listing.images.get(0),25,top+9,88,99);
        wrap(g,listing.title,124,top+11,112,10,2,INK);
        wrap(g,listing.description,124,top+42,146,7,1,MUTED);
        text(g,listingPrice(listing),124,top+61,12,144,YELLOW);
        text(g,listing.ownerName,124,top+83,7,144,MUTED);
        text(g,listing.location.isBlank()?category(listing.category):listing.location,124,top+103,6,64,MUTED);
        g.flush();
        if(!listing.status.equals("ACTIVE")){
            box(g,29,top+13,76,18,DARK);text(g,status(listing.status),34,top+19,6,66,YELLOW);
        }else if(System.currentTimeMillis()-listing.createdAt<86_400_000L){
            box(g,29,top+13,36,18,YELLOW);text(g,tr("YENİ","NEW"),34,top+19,6,27,DARK);
        }
        clippedButton(g,listing.favorite?"♥":"♡",246,top+7,28,26,PALE,listing.favorite?YELLOW:MUTED,()->MarketplaceClient.favorite(listing));
        boolean own=listing.ownerUuid.equals(MarketplaceClient.state().selfUuid);
        clippedButton(g,own?tr("Yönet","Manage"):tr("Mesaj","Message"),196,top+95,77,18,PALE,YELLOW,
                ()->{if(own)open(new MarketplaceListingDetailsScreen(listing.id));else MarketplaceClient.contact(listing);});
    }
    private void clippedButton(GuiGraphics g,String label,int left,int top,int width,int height,int bg,int fg,Runnable action){
        if(top>=518||top+height<=226)return;
        button(g,label,left,top,width,height,bg,fg,action);
        Hit hit=hits.remove(hits.size()-1);hits.add(new Hit(hit.l(),Math.max(226,hit.t()),hit.r(),Math.min(518,hit.b()),hit.action()));
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){
        if(inside(mx,my,12,226,288,518)){scroll+=(int)(-delta*35);return true;}return super.mouseScrolled(mx,my,delta);
    }
}

package com.mikasrevs.newphone.marketplace.client;

import net.minecraft.client.gui.GuiGraphics;

public final class MarketplaceProfileScreen extends MarketplaceBaseScreen {
    private int scroll,ticks;
    public MarketplaceProfileScreen(){super("Marketplace profile");}
    @Override protected void init(){super.init();MarketplaceClient.send(MarketplaceClient.command("PROFILE",""));}
    @Override public void tick(){super.tick();if(++ticks>=80){ticks=0;MarketplaceClient.send(MarketplaceClient.command("PROFILE",""));}}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        begin(g,tr("Pazar profilim","Your Marketplace"),tr("İlanların, alışverişlerin ve kaydettiklerin","Your listings, purchases and saved finds"),false);
        var state=MarketplaceClient.state();
        panel(g,18,105,264,69);
        com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer.draw(g,state.selfUuid,x(31),y(120),size(35));
        text(g,minecraft.player==null?"Player":minecraft.player.getGameProfile().getName(),81,122,15,187,INK);
        text(g,tr("MARKETPLACE HESABI","MARKETPLACE ACCOUNT"),81,149,6,188,YELLOW);
        stat(g,18,state.activeCount,tr("AKTİF","ACTIVE"));stat(g,109,state.soldCount,tr("SATILDI","SOLD"));stat(g,200,state.purchaseCount,tr("ALIM","PURCHASES"));
        button(g,tr("İlanlarım","My listings"),18,239,128,31,PALE,INK,()->open(new MarketplaceMyListingsScreen()));
        button(g,tr("Takaslarım","My trades")+" · "+com.mikasrevs.newphone.marketplace.trade.TradeClient.unread(),154,239,128,31,YELLOW,DARK,()->open(new com.mikasrevs.newphone.marketplace.trade.TradeScreen()));
        button(g,tr("Favoriler","Favorites")+" · "+state.favoriteCount,18,278,128,31,PALE,YELLOW,()->open(new MarketplaceFavoritesScreen()));
        button(g,tr("Bankayı aç","Open Bank"),154,278,128,31,PALE,INK,this::bank);
        text(g,tr("SON İŞLEMLER","RECENT ACTIVITY"),20,329,8,260,MUTED);
        scroll=Math.max(0,Math.min(scroll,Math.max(0,state.purchases.size()*68-168)));
        g.flush();g.enableScissor(x(16),y(350),x(284),y(518));
        if(state.purchases.isEmpty()){
            panel(g,18,361,264,139);
            text(g,tr("İlk alışverişin seni bekliyor","Your first find is waiting"),32,389,12,236,INK);
            wrap(g,tr("Alım ve satım kayıtlarını burada görebilirsin.","Your purchases and sales will appear here."),32,421,234,8,3,MUTED);
        }
        for(int i=0;i<state.purchases.size();i++){
            var receipt=state.purchases.get(i);int top=351+i*68-scroll;
            if(top+61<350||top>518)continue;
            panel(g,18,top,264,61);text(g,receipt.title(),29,top+9,11,239,INK);
            text(g,(receipt.buyerUuid().equals(state.selfUuid)?tr("Satıcı: ","Bought from ")+receipt.sellerName():tr("Alıcı: ","Sold to ")+receipt.buyerName()),29,top+28,7,239,MUTED);
            text(g,receipt.amountText()+" · "+status(receipt.status()),29,top+45,8,239,YELLOW);
            hits.add(new Hit(18,Math.max(350,top),282,Math.min(518,top+61),()->open(new MarketplaceListingDetailsScreen(receipt.listingId()))));
        }
        g.flush();g.disableScissor();
        button(g,tr("Bildirimleri okundu işaretle","Mark notifications read")+" · "+MarketplaceClient.unread(),18,526,264,24,PALE,YELLOW,MarketplaceClient::readNotifications);
        finish(g,"PROFILE",true);
    }
    private void stat(GuiGraphics g,int left,int count,String label){
        box(g,left,185,82,42,WHITE);text(g,Integer.toString(count),left+11,192,15,63,YELLOW);text(g,label,left+11,214,6,63,MUTED);
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){
        if(inside(mx,my,16,350,284,518)){scroll=Math.max(0,scroll+(int)(-delta*28));return true;}return super.mouseScrolled(mx,my,delta);
    }
}

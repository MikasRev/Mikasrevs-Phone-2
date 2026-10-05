package com.mikasrevs.newphone;

import com.mikasrevs.newphone.marketplace.data.*;
import com.mikasrevs.newphone.marketplace.network.MarketplaceStatePacket;
import com.mikasrevs.newphone.integration.currency.PhoneCurrencyIntegration.*;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;

/** Isolated world-data tests: no real accounts, player saves or client are changed. */
public final class MarketplaceChecks {
    private static final String SELLER=UUID.randomUUID().toString(),BUYER=UUID.randomUUID().toString();
    public static void main(String[] args) throws Exception {run();}
    public static void run() throws Exception {
        validation();persistence();directSellRemoved();lifecycle();packetBounds();
        System.out.println("Marketplace checks passed: validation, ownership, persistence, disabled Direct Sell, legacy payment review, packet limits.");
    }
    private static MarketplaceRequest draft(){var r=new MarketplaceRequest();r.title="Sports Car";r.category="CARS";r.price="4500";r.currencyId="test:coins";r.directPurchase=true;r.requestId=UUID.randomUUID().toString();return r;}
    private static MarketplaceListing create(MarketplaceSavedData data){return data.create(SELLER,"Alex",draft(),"Coins","4500 Coins");}
    private static MarketplaceRequest buy(MarketplaceListing l){var r=new MarketplaceRequest();r.id=l.id;r.expectedPrice=l.price;r.expectedRevision=l.revision;r.requestId=UUID.randomUUID().toString();return r;}
    private static TransferResult success(){return new TransferResult(TransferStatus.SUCCESS,"4500 Coins");}
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static void rejects(Runnable action,String reason){try{action.run();throw new AssertionError(reason);}catch(IllegalArgumentException expected){}}
    private static void validation(){
        for(String amount:List.of("-1","1.5","NaN","9223372036854775808","1000000000001","1e9",""))rejects(()->MarketplaceRequest.amount(amount,false),"accepted invalid amount "+amount);
        check(MarketplaceRequest.amount("0",false)==0,"zero rejected");check(MarketplaceRequest.amount("1000000000000",false)==MarketplaceRequest.MAX_PRICE,"upper bound rejected");
        var r=draft();r.title=" ";rejects(r::validateDraft,"blank title accepted");r.title="Valid";r.description="x".repeat(1801);rejects(r::validateDraft,"unbounded description");
        r.description="";r.category=null;rejects(r::validateDraft,"null category accepted");r.category="ITEMS";r.images=List.of("../../outside");rejects(r::validateDraft,"forged photo accepted");
        var spoof=new Gson().fromJson("{\"balance\":99999,\"ownerUuid\":\"other\",\"sold\":true,\"price\":\"0\"}",MarketplaceRequest.class);
        check(!new Gson().toJson(spoof).contains("ownerUuid"),"client owns authoritative fields");
    }
    private static void persistence(){
        var data=new MarketplaceSavedData();var draft=draft();String image="market-"+UUID.randomUUID();
        data.putImage(SELLER,image,new byte[]{1,2,3});draft.images=List.of(image);var listing=data.create(SELLER,"Alex",draft,"Coins","4500 Coins");
        check(data.create(SELLER,"Alex",draft,"Coins","4500 Coins").id.equals(listing.id),"create replay duplicates listing");
        data.favorite(BUYER,listing.id,true);data.allowConversation(SELLER,BUYER);data.view(BUYER,listing.id);data.view(BUYER,listing.id);
        check(listing.views==1,"view refresh inflation");var saved=MarketplaceSavedData.load(data.save(new CompoundTag()));
        check(saved.isFavorite(BUYER,listing.id)&&saved.canMessage(SELLER,BUYER)&&saved.canMessage(BUYER,SELLER),"favorites/conversations lost");
        check(Arrays.equals(saved.imageFor(BUYER,image,(a,b)->false),new byte[]{1,2,3}),"listing photo lost");
        check(!create(saved).id.equals(listing.id),"persistent identity reused");
        draft.id=listing.id;draft.expectedRevision=listing.revision;
        rejects(()->data.edit(BUYER,draft,"Coins","1"),"ownership spoof on edit");rejects(()->data.changeStatus(BUYER,listing.id,true),"ownership spoof on delete");
        rejects(()->data.putImage(BUYER,image,new byte[]{9}),"image reference overwritten");
        var stolen=draft();stolen.images=List.of(image);rejects(()->data.create(BUYER,"Steve",stolen,"Coins","1"),"stolen photo published");
    }
    private static void directSellRemoved() throws Exception {
        var data=new MarketplaceSavedData();var listing=create(data);listing.directPurchase=true;
        for(String buyer:List.of(SELLER,BUYER,UUID.randomUUID().toString()))
            check(data.purchase(buyer,"Player",buy(listing),
                ()->{throw new AssertionError("Removed Direct Sell charged money");},
                ()->{throw new AssertionError("Removed Direct Sell wrote a payment");}).equals("DIRECT_SELL_REMOVED"),"Direct Sell still enabled");
        check(MarketplaceSavedData.load(data.save(new CompoundTag())).find(listing.id).status.equals("ACTIVE"),"disabled purchase changed listing");
        listing.status="PROCESSING";
        check(MarketplaceSavedData.load(data.save(new CompoundTag())).find(listing.id).status.equals("REVIEW"),"legacy interrupted payment lost review lock");
    }
    private static void packetBounds(){
        MarketplaceState worst=new MarketplaceState();var listing=new MarketplaceListing();listing.title="<".repeat(64);listing.description="<".repeat(1800);listing.location="<".repeat(64);
        for(int i=0;i<12;i++)worst.listings.add(listing);worst.detail=listing;
        String worstJson=new Gson().toJson(worst);check(worstJson.length()<MarketplaceStatePacket.MAX_LENGTH,"valid escaped page exceeds packet limit");
        var escaped=draft();escaped.description="<".repeat(1800);escaped.validateDraft();check(new Gson().toJson(escaped).length()<=16000,"valid escaped draft exceeds request limit");
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{var packet=new MarketplaceStatePacket(new Gson().toJson(new MarketplaceState()));MarketplaceStatePacket.encode(packet,buffer);check(MarketplaceStatePacket.decode(buffer).equals(packet),"snapshot round trip");
            boolean rejected=false;try{MarketplaceStatePacket.encode(new MarketplaceStatePacket("x".repeat(MarketplaceStatePacket.MAX_LENGTH+1)),buffer);}catch(RuntimeException expected){rejected=true;}check(rejected,"unbounded packet");
        }finally{buffer.release();}
    }
    private static void lifecycle(){
        var data=new MarketplaceSavedData();var listing=create(data);
        data.changeStatus(SELLER,listing.id,false);check(listing.status.equals("UNAVAILABLE"),"unavailable transition");
        rejects(()->data.relist(BUYER,listing.id),"non-owner relisted");data.relist(SELLER,listing.id);
        listing.expiresAt=1;check(data.find(listing.id).status.equals("EXPIRED"),"expiry missing");data.relist(SELLER,listing.id);
        check(listing.status.equals("ACTIVE")&&listing.expiresAt>System.currentTimeMillis(),"expiry not renewed");
        rejects(()->data.resolveReview(listing.id,true,"Operator"),"active listing marked paid by review command");
        listing.status="REVIEW";listing.buyerUuid=BUYER;listing.buyerName="Steve";
        data.resolveReview(listing.id,true,"Operator");check(listing.status.equals("SOLD")&&listing.reviewedBy.equals("Operator"),"paid reconciliation not audited");
        var other=create(data);other.status="REVIEW";other.buyerUuid=BUYER;
        data.resolveReview(other.id,false,"Operator");check(other.status.equals("UNAVAILABLE")&&other.buyerUuid.isEmpty(),"unpaid reconciliation");
        data.changeStatus(SELLER,other.id,true);rejects(()->data.relist(SELLER,other.id),"deleted listing resurrected");
    }
}

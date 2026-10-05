package com.mikasrevs.newphone.marketplace.trade;

import com.google.gson.Gson;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.sound.PhoneSoundManager;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.marketplace.client.MarketplaceBaseScreen;
import com.mikasrevs.newphone.marketplace.data.MarketplaceListing;
import com.mikasrevs.newphone.network.PhoneNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class TradeClient {
    private static final Gson JSON=new Gson();
    private static TradeState state=new TradeState();
    private static final Map<String,String> results=new LinkedHashMap<>();
    private static String invitation="";
    private static Screen origin;
    private static boolean initialized;
    private static long invitationAt;
    public static long receivedAt;
    private TradeClient(){}
    public static TradeState state(){return state;}
    public static void clear(){state=new TradeState();results.clear();invitation="";origin=null;initialized=false;receivedAt=0;}
    public static void receive(String json){
        try{
            TradeState next=JSON.fromJson(json,TradeState.class);
            if(next==null||next.mine==null||next.theirs==null||next.mail==null||next.inventory==null||next.chat==null||next.chat.size()>60||next.mine.size()>8||next.theirs.size()>8||next.mail.size()>8||next.inventory.size()>36)return;
            boolean fresh=next.status.equals("REQUESTED")&&next.incoming&&!next.id.equals(state.id);
            state=next;receivedAt=System.currentTimeMillis();
            if(!next.requestId.isEmpty()){results.put(next.requestId,next.code);while(results.size()>64)results.remove(results.keySet().iterator().next());}
            if(initialized&&fresh)PhoneSoundManager.playEmergencyNotification();initialized=true;
            if(next.requestId.equals(invitation)&&!invitation.isEmpty()){
                Minecraft mc=Minecraft.getInstance();
                if(next.code.equals("TRADE_SENT")&&mc.screen==origin&&origin instanceof MarketplaceBaseScreen)
                    PhoneNavigationState.open(mc,origin,new TradeScreen());
                else if(mc.screen==origin&&origin instanceof MarketplaceBaseScreen market)market.serverNotice(next.code);
                invitation="";origin=null;
            }
        }catch(RuntimeException failure){com.mojang.logging.LogUtils.getLogger().warn("Invalid trade state rejected",failure);}
    }
    public static String send(TradeRequest r){
        if(r.requestId.isEmpty())r.requestId=UUID.randomUUID().toString();r.validate();results.remove(r.requestId);
        PhoneNetwork.sendAction("MARKET_TRADE","",JSON.toJson(r));return r.requestId;
    }
    public static TradeRequest command(String action){TradeRequest r=new TradeRequest();r.action=action;r.id=state.id;r.revision=state.revision;r.page=state.page;return r;}
    public static String result(String id){return results.remove(id);}
    public static void sync(int page){var r=command("SYNC");r.page=page;send(r);}
    public static void invite(MarketplaceListing listing){
        if(state.listing.equals(listing.id)&&Set.of("ACTIVE","REQUESTED").contains(state.status)){
            Minecraft mc=Minecraft.getInstance();PhoneNavigationState.open(mc,mc.screen,new TradeScreen());return;
        }
        if(!invitation.isEmpty()&&System.currentTimeMillis()-invitationAt<15000)return;
        var r=new TradeRequest();r.action="INVITE";r.listing=listing.id;origin=Minecraft.getInstance().screen;invitation=send(r);invitationAt=System.currentTimeMillis();
    }
    public static int unread(){return state.incoming&&state.status.equals("REQUESTED")?1:0;}
    public static String message(String code){return switch(code){
        case "TRADE_SENT" -> tr("Takas isteği gönderildi.","Trade request sent.");
        case "TRADE_ACCEPTED" -> tr("Takas açıldı. Eşyalarını ekleyebilirsin.","Trade opened. Add your items.");
        case "TRADE_UPDATED" -> tr("Teklif güncellendi. Onaylar sıfırlandı.","Offer changed. Approvals reset.");
        case "TRADE_READY" -> tr("Onayladın. Diğer oyuncu bekleniyor.","Approved. Waiting for the other player.");
        case "TRADE_COMPLETE" -> tr("Takas tamamlandı. Eşyalar envanterine otomatik verilir.","Trade complete. Items are delivered automatically.");
        case "TRADE_CANCELLED" -> tr("Takas iptal edildi. Eşyalar envanterine otomatik döner.","Trade cancelled. Items return to your inventory automatically.");
        case "TRADE_CHAT_SENT" -> "";
        case "TRADE_PHONE_IN_USE" -> tr("Kullandığın telefonu takasa koyamazsın.","You cannot trade the phone you are using.");
        case "TRADE_CLAIMED" -> tr("Eşya envanterine teslim edildi.","Item delivered to your inventory.");
        case "TRADE_INVENTORY_FULL" -> tr("Envanterinde yeterli yer yok. Eşyan güvenle bekliyor.","Inventory full. Your item remains safely stored.");
        case "TRADE_CHANGED","TRADE_ITEM_CHANGED" -> tr("Teklif veya envanter değişti. Yenileyip tekrar seç.","Offer or inventory changed. Refresh and select again.");
        case "TRADE_BUSY" -> tr("Oyunculardan birinin açık takası var.","One player already has an open trade.");
        case "TRADE_OFFLINE" -> tr("Diğer oyuncu çevrimiçi olmalı ve telefonu bulunmalı.","The other player must be online and have a phone.");
        case "TRADE_ITEMS_ONLY" -> tr("Yalnızca eşya ilanlarında takas yapılabilir.","Trading is available for item listings only.");
        case "TRADE_OFFER_FULL" -> tr("Teklifine en fazla 8 eşya yığını ekleyebilirsin.","You can offer up to 8 item stacks.");
        case "TRADE_MAIL_FULL" -> tr("Bekleyen eşyaların için envanterinde yer aç.","Make inventory space for your waiting items.");
        case "TRADE_ITEM_LARGE" -> tr("Bu eşyanın verisi takas için fazla büyük.","This item's data is too large to trade.");
        case "TRADE_ITEM_UNAVAILABLE" -> tr("Eşyanın modu kullanılamıyor. Eşya depoda korunuyor.","The item's mod is unavailable. Custody is retained.");
        case "TRADE_RECOVERY","TRADE_STORAGE" -> tr("Takas kaydı doğrulanamadı. Eşyalar işlem günlüğünde korunuyor; yöneticiyle görüş.","Trade storage needs verification. Custody journal retained; contact an admin.");
        case "TRADE_CLOSED","TRADE_NOT_FOUND" -> tr("Takas sona erdi. İadeler envanterine otomatik gelir.","Trade ended. Returns arrive in your inventory automatically.");
        case "DIRECT_SELL_REMOVED" -> tr("Doğrudan satış kaldırıldı. Takas isteği gönder.","Direct Sell was removed. Send a trade request.");
        case "" -> "";
        default -> code.startsWith("TRADE_")?tr("Takas isteği işlenemedi. Yenile.","Trade request failed. Refresh."):com.mikasrevs.newphone.marketplace.client.MarketplaceClient.message(code);
    };}
    private static String tr(String tr,String en){return PhoneSettingsStore.text(tr,en);}
}

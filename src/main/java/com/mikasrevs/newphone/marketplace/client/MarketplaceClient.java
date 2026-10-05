package com.mikasrevs.newphone.marketplace.client;

import com.google.gson.Gson;
import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.sound.PhoneSoundManager;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneMessageThreadScreen;
import com.mikasrevs.newphone.integration.currency.PhoneCurrencyIntegration;
import com.mikasrevs.newphone.marketplace.data.*;
import com.mikasrevs.newphone.network.PhoneNetwork;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Read-only client cache; all mutations wait for a server acknowledgement. */
public final class MarketplaceClient {
    private static final Gson JSON=new Gson();
    private static MarketplaceState state=new MarketplaceState();
    private static final Map<String,Result> results=new LinkedHashMap<>();
    private static String contactRequest="",contactDraft="";
    private static boolean initialized;
    public static long receivedAt;
    private MarketplaceClient() { }
    public static MarketplaceState state(){return state;}
    public static void clear(){com.mikasrevs.newphone.marketplace.trade.TradeClient.clear();state=new MarketplaceState();results.clear();initialized=false;receivedAt=0;contactRequest="";contactDraft="";}
    public static void receive(String json) {
        try {
            MarketplaceState incoming=JSON.fromJson(json,MarketplaceState.class);if(incoming==null)return;
            Set<String> previous=new HashSet<>();state.notifications.forEach(n->previous.add(n.id()));
            boolean fresh=incoming.notifications.stream().anyMatch(n->!n.read()&&!previous.contains(n.id()));
            state=incoming;receivedAt=System.currentTimeMillis();
            if(Minecraft.getInstance().screen instanceof MarketplaceBaseScreen screen&&!incoming.code.isEmpty())screen.serverNotice(incoming.code);
            if(initialized&&fresh)PhoneSoundManager.playEmergencyNotification();
            initialized=true;
            if(!incoming.requestId.isEmpty()){
                results.put(incoming.requestId,new Result(incoming.code,incoming.listingId,incoming.uploadedId));
                while(results.size()>64)results.remove(results.keySet().iterator().next());
            }
            if(!contactRequest.isEmpty()&&incoming.requestId.equals(contactRequest)) {
                if(incoming.code.equals("CONTACT_READY")) {
                    Minecraft mc=Minecraft.getInstance();
                    if(mc.screen instanceof MarketplaceBaseScreen)
                        PhoneNavigationState.open(mc,mc.screen,new ZPhoneMessageThreadScreen(incoming.contactUuid,incoming.contactName).withDraft(contactDraft));
                }
                contactRequest="";contactDraft="";
            }
        }catch(RuntimeException e){com.mojang.logging.LogUtils.getLogger().warn("Rejected invalid Marketplace snapshot",e);}
    }
    public static String send(MarketplaceRequest request){
        if(request.requestId==null||request.requestId.isEmpty())request.requestId=UUID.randomUUID().toString();
        results.remove(request.requestId);PhoneNetwork.sendAction("MARKETPLACE","",JSON.toJson(request));return request.requestId;
    }
    public static MarketplaceRequest command(String action,String id){MarketplaceRequest r=new MarketplaceRequest();r.action=action;r.id=id;return r;}
    public static void detail(String id){send(command("DETAIL",id));}
    public static void favorite(MarketplaceListing listing){var r=command("FAVORITE",listing.id);r.favorite=!listing.favorite;send(r);}
    public static void contact(MarketplaceListing listing){
        contactDraft=PhoneSettingsStore.text("Merhaba, Marketplace ilanınızla ilgileniyorum: ",
                "Hi, I'm interested in your Marketplace listing: ")+listing.title+" ("+listing.id+")";
        contactRequest=send(command("CONTACT",listing.id));
    }
    public static void readNotifications(){send(command("READ",""));}
    public static int unread(){return (int)state.notifications.stream().filter(n->!n.read()).count()+com.mikasrevs.newphone.marketplace.trade.TradeClient.unread();}
    public static Result result(String id){return results.get(id);}
    public static String upload(Path photo){
        byte[] bytes=PhonePhotoStore.encodeForMessage(photo);if(bytes==null)throw new IllegalArgumentException("Could not prepare this photo.");
        String id="market-"+UUID.randomUUID();PhonePhotoStore.receive(id,bytes);PhoneNetwork.sendPhotoUpload("MARKETPLACE",id,bytes);return id;
    }
    public static List<PhoneCurrencyIntegration.Balance> balances(){return state.bank==null?List.of():state.bank.balances();}
    public static long balance(String currency){return balances().stream().filter(b->b.id().equals(currency)).mapToLong(PhoneCurrencyIntegration.Balance::coreValue).findFirst().orElse(0);}
    public static boolean bankAvailable(){return state.bank!=null&&state.bank.available();}
    public static String format(String currency,long amount){
        var balance=balances().stream().filter(b->b.id().equals(currency)).findFirst().orElse(null);
        if(balance==null)return Long.toString(amount);
        var units=new ArrayList<>(balance.units());units.sort(Comparator.comparingLong(PhoneCurrencyIntegration.Unit::coreValue).reversed());
        StringBuilder text=new StringBuilder();long remaining=Math.max(0,amount);
        for(var unit:units){long quantity=remaining/unit.coreValue();remaining%=unit.coreValue();if(quantity>0){if(!text.isEmpty())text.append(" ");text.append(quantity).append(" ").append(unit.name());}}
        return text.isEmpty()?"0 "+balance.name():text.toString();
    }
    public static String message(String code){
        if(code!=null&&(code.startsWith("TRADE_")||code.equals("DIRECT_SELL_REMOVED")))return com.mikasrevs.newphone.marketplace.trade.TradeClient.message(code);
        return switch(code){
            case "CREATED"->PhoneSettingsStore.text("İlan yayınlandı","Listing published");case "UPDATED"->PhoneSettingsStore.text("İlan güncellendi","Listing updated");case "REMOVED"->PhoneSettingsStore.text("İlan kaldırıldı","Listing removed");
            case "PURCHASED"->PhoneSettingsStore.text("Satın alma tamamlandı. Kayıt kaydedildi.","Purchase successful. Receipt saved.");case "INSUFFICIENT_FUNDS"->PhoneSettingsStore.text("Bakiye yetersiz","Insufficient Funds");
            case "PRICE_CHANGED"->PhoneSettingsStore.text("İlan değişti. Güncel detayları incele.","Listing changed. Review the new details.");case "OWN_LISTING"->PhoneSettingsStore.text("Kendi ilanını satın alamazsın.","You cannot buy your own listing.");
            case "NOT_FOUND","UNAVAILABLE"->PhoneSettingsStore.text("Bu ilan artık kullanılamıyor.","This listing is no longer available.");case "CONTACT_ONLY"->PhoneSettingsStore.text("Satış için satıcıyla iletişime geç.","Contact the seller to arrange this sale.");
            case "PAYMENT_REVIEW"->PhoneSettingsStore.text("Ödeme sunucu incelemesinde. Tekrar ödeme yapma.","Payment needs server review. Do not pay again.");
            case "STORAGE_ERROR"->PhoneSettingsStore.text("Kayıt yapılamadı. Tekrar denemeden alışverişlerini kontrol et.","Could not save. Check Purchases before retrying.");
            case "INVALID_TITLE"->PhoneSettingsStore.text("En fazla 64 karakterlik bir başlık gir.","Enter a title (up to 64 characters).");case "INVALID_DESCRIPTION"->PhoneSettingsStore.text("Açıklama veya konum çok uzun.","Description or location is too long.");
            case "INVALID_PRICE"->PhoneSettingsStore.text("Sıfır veya daha büyük bir tam sayı gir.","Enter a valid non-negative whole amount.");case "INVALID_CURRENCY"->PhoneSettingsStore.text("Bankadaki kullanılabilir para birimlerinden birini seç.","Select an available Bank currency.");
            case "INVALID_FILTER"->PhoneSettingsStore.text("Kategori ve en az/en çok fiyat alanlarını kontrol et.","Check your category and minimum/maximum price.");
            case "INVALID_CATEGORY"->PhoneSettingsStore.text("Araçlar, Evler, Eşyalar veya Diğer kategorisini seç.","Choose Cars, Houses, Items or Others.");
            case "INVALID_TEXT"->PhoneSettingsStore.text("Metinden biçimlendirme ve kontrol karakterlerini kaldır.","Remove formatting/control characters from the text.");
            case "FAVORITE_LIMIT"->PhoneSettingsStore.text("Favori sınırına ulaşıldı (1.000). Kaydedilmiş bir ilanı kaldır.","Favorite limit reached (1,000). Remove a saved listing.");
            case "INVALID_PHOTO","PHOTO_LIMIT"->PhoneSettingsStore.text("Fotoğraf kullanılamıyor veya fotoğraf sınırına ulaşıldı.","Photo unavailable or photo limit reached.");case "LISTING_LIMIT"->PhoneSettingsStore.text("Aktif ilan sınırına ulaşıldı (50).","Active listing limit reached (50).");
            case "CONTACT_BLOCKED"->PhoneSettingsStore.text("Bu satıcıyla mesajlaşma engellendi.","Messaging this seller is blocked.");case "NOT_OWNER","NOT_EDITABLE"->PhoneSettingsStore.text("Bu ilanı değiştiremezsin.","You cannot change this listing.");
            case "RATE_LIMIT"->PhoneSettingsStore.text("Biraz bekleyip yeniden dene.","Please wait a moment and try again.");case "ACCOUNT_UNAVAILABLE"->PhoneSettingsStore.text("Banka hesabı kullanılamıyor. Bankayı aç.","Bank account unavailable. Open Bank.");
            case "UNAVAILABLE_BANK","MOD_MISSING"->PhoneSettingsStore.text("Banka hesabına ulaşılamadı","Bank account unavailable");case "SELECT_CURRENCY"->PhoneSettingsStore.text("Fiyat filtreleri için bir para birimi seç.","Choose a currency for price filters.");
            case "FAVORITE_UPDATED","PHOTO_READY","CONTACT_READY",""->"";
            default->PhoneSettingsStore.text("İşlem yapılamadı. Yenileyip tekrar dene.","Request failed. Refresh and try again.");
        };
    }
    public record Result(String code,String listingId,String photoId) { }
}

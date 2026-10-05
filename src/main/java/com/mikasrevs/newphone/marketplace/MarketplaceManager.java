package com.mikasrevs.newphone.marketplace;

import com.google.gson.Gson;
import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.marketplace.data.*;
import com.mikasrevs.newphone.marketplace.economy.MarketplaceEconomyBridge;
import com.mikasrevs.newphone.marketplace.network.MarketplaceStatePacket;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.network.message.PhonePhotoUploadMessage;
import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import java.io.IOException;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** All entry points execute on the server thread via the existing phone packet queue. */
public final class MarketplaceManager {
    private static final Gson JSON=new Gson();
    private static final int PAGE_SIZE=12;
    private static final Map<MinecraftServer,Map<UUID,Session>> SESSIONS=new WeakHashMap<>();
    private MarketplaceManager() { }
    private static Session session(ServerPlayer p){return SESSIONS.computeIfAbsent(p.server,k->new HashMap<>()).computeIfAbsent(p.getUUID(),k->new Session());}
    public static void logout(ServerPlayer player){var sessions=SESSIONS.get(player.server);if(sessions!=null)sessions.remove(player.getUUID());}
    public static void login(ServerPlayer player){send(player,new MarketplaceRequest(),"","");}
    public static void handle(ServerPlayer player,String body) {
        if(!PhoneInventoryUtil.hasPhone(player)||body==null||body.length()>16000)return;
        Session gate=session(player);long clock=System.currentTimeMillis();
        gate.tokens=Math.min(20,gate.tokens+(clock-gate.refill)/200.0);gate.refill=clock;
        if(gate.tokens<1)return;gate.tokens--;
        MarketplaceRequest request=null;
        try {
            request=JSON.fromJson(body,MarketplaceRequest.class);
            if(request==null||request.action==null||request.requestId==null)throw new IllegalArgumentException("INVALID_REQUEST");
            if(request.requestId.length()!=36||!UUID.fromString(request.requestId).toString().equals(request.requestId)
                ||request.id==null||(!request.id.isEmpty()&&!request.id.matches("MP-[0-9]{1,19}")))throw new IllegalArgumentException("INVALID_REQUEST");
            Session session=session(player);long now=System.currentTimeMillis();
            boolean read=Set.of("BROWSE","DETAIL","PROFILE","READ").contains(request.action);
            if(!read&&now-session.lastWrite<350){send(player,request,"RATE_LIMIT",request.id);return;}
            if(read)session.lastRead=now;else session.lastWrite=now;
            var data=MarketplaceSavedData.get(player.server);String self=player.getUUID().toString();String id=request.id;
            String code="";
            switch(request.action) {
                case "BROWSE" -> {validateQuery(request);session.query=request;session.detail="";}
                case "DETAIL" -> {requireVisible(player,data.find(id));session.detail=id;data.view(self,id);}
                case "PROFILE" -> {session.query=new MarketplaceRequest();session.query.section="PROFILE";session.detail="";}
                case "READ" -> data.readNotifications(self);
                case "CREATE","EDIT" -> {
                    request.directPurchase=false;
                    String currencyName,priceText;
                    if("ITEMS".equals(request.category)){
                        request.price="0";request.currencyId="newphone:trade";currencyName="Trade";priceText="Trade";
                    }else{
                        var quote=MarketplaceEconomyBridge.quote(player,request.currencyId,MarketplaceRequest.amount(request.price,false));
                        if(!quote.valid())throw new IllegalArgumentException("INVALID_CURRENCY");currencyName=quote.currencyName();priceText=quote.formatted();
                    }
                    request.validateDraft();
                    MarketplaceListing listing=request.action.equals("CREATE")?data.create(self,player.getGameProfile().getName(),request,currencyName,priceText):data.edit(self,request,currencyName,priceText);
                    id=listing.id;session.detail=id;code=request.action.equals("CREATE")?"CREATED":"UPDATED";
                }
                case "DELETE","UNAVAILABLE" -> {data.changeStatus(self,id,request.action.equals("DELETE"));code=request.action.equals("DELETE")?"REMOVED":"UPDATED";session.detail="";}
                case "RELIST" -> {data.relist(self,id);code="UPDATED";session.detail=id;}
                case "FAVORITE" -> {requireVisible(player,data.find(id));data.favorite(self,id,request.favorite);code="FAVORITE_UPDATED";}
                case "PURCHASE" -> {
                    throw new IllegalArgumentException("DIRECT_SELL_REMOVED");
                }
                case "CONTACT" -> {
                    MarketplaceListing listing=data.find(id);requireVisible(player,listing);
                    if(listing.ownerUuid.equals(self))throw new IllegalArgumentException("OWN_LISTING");
                    if(PhoneCommunicationSavedData.get(player.server).eitherBlocks(self,listing.ownerUuid))throw new IllegalArgumentException("CONTACT_BLOCKED");
                    data.allowConversation(self,listing.ownerUuid);
                    var communication=PhoneCommunicationSavedData.get(player.server);var snapshot=communication.copySnapshot();
                    snapshot.knownNames.put(self,player.getGameProfile().getName());snapshot.knownNames.put(listing.ownerUuid,listing.ownerName);communication.replace(snapshot);
                    MarketplaceState state=state(player);state.requestId=request.requestId;state.code="CONTACT_READY";
                    state.contactUuid=listing.ownerUuid;state.contactName=listing.ownerName;state.listingId=id;sendPacket(player,state);return;
                }
                default -> throw new IllegalArgumentException("INVALID_REQUEST");
            }
            send(player,request,code,id);
            if(!read)broadcast(player.server,player.getUUID());
        }catch(IllegalArgumentException failure){send(player,request,knownCode(failure.getMessage()),request==null?"":request.id);}
        catch(RuntimeException failure){com.mojang.logging.LogUtils.getLogger().error("Marketplace request failed",failure);send(player,request,"FAILED","");}
    }
    private static String knownCode(String code) {
        return code!=null&&code.matches("[A-Z_]{2,32}")?code:"INVALID_REQUEST";
    }
    private static void requireVisible(ServerPlayer viewer,MarketplaceListing l) {
        if(l==null||l.status.equals("DELETED"))throw new IllegalArgumentException("NOT_FOUND");
        if(PhoneCommunicationSavedData.get(viewer.server).eitherBlocks(viewer.getUUID().toString(),l.ownerUuid))throw new IllegalArgumentException("CONTACT_BLOCKED");
    }
    public static void validateQuery(MarketplaceRequest r) {
        if(r.search==null||r.search.length()>100||r.category==null||(!r.category.isEmpty()&&!MarketplaceRequest.CATEGORIES.contains(r.category)))throw new IllegalArgumentException("INVALID_FILTER");
        if(!Set.of("MARKET","MINE","FAVORITES","PURCHASES","PROFILE").contains(r.section)
            || !Set.of("NEWEST","OLDEST","LOWEST","HIGHEST","VIEWS").contains(r.sort)
            || !Set.of("ACTIVE","SOLD","EXPIRED","UNAVAILABLE","REVIEW","ALL").contains(r.status))throw new IllegalArgumentException("INVALID_FILTER");
        long min=MarketplaceRequest.amount(r.minPrice,true),max=MarketplaceRequest.amount(r.maxPrice,true);
        if(max>=0&&min>max||r.page<0||r.page>1000||r.currencyId==null||r.currencyId.length()>160)throw new IllegalArgumentException("INVALID_FILTER");
        if((min>=0||max>=0||r.sort.equals("LOWEST")||r.sort.equals("HIGHEST"))&&r.currencyId.isEmpty())throw new IllegalArgumentException("SELECT_CURRENCY");
    }
    private static MarketplaceState state(ServerPlayer player) {
        var data=MarketplaceSavedData.get(player.server);var session=session(player);var q=session.query;
        String self=player.getUUID().toString();MarketplaceState state=new MarketplaceState();state.selfUuid=self;state.revision=data.revision();
        state.bank=MarketplaceEconomyBridge.balance(player);var all=data.all();
        var blocked=PhoneCommunicationSavedData.get(player.server);
        long min=MarketplaceRequest.amount(q.minPrice,true),max=MarketplaceRequest.amount(q.maxPrice,true);
        String search=q.search.toLowerCase(Locale.ROOT);
        List<MarketplaceListing> matches=new ArrayList<>();
        for(var l:all) {
            boolean own=l.ownerUuid.equals(self),bought=l.buyerUuid.equals(self)&&l.status.equals("SOLD"),fav=data.isFavorite(self,l.id);
            if(own&&l.status.equals("ACTIVE"))state.activeCount++;if(own&&l.status.equals("SOLD"))state.soldCount++;
            if(bought)state.purchaseCount++;if(fav&&!l.status.equals("DELETED"))state.favoriteCount++;
            if(l.status.equals("DELETED")||blocked.eitherBlocks(self,l.ownerUuid))continue;
            boolean include=switch(q.section){case "MINE"->own&&(q.status.equals("ALL")||l.status.equals(q.status));case "PURCHASES"->bought;case "FAVORITES"->fav;case "PROFILE"->false;default->l.status.equals("ACTIVE");};
            if(!include||q.favoritesOnly&&!fav)continue;
            if(!q.category.isEmpty()&&!l.category.equals(q.category)||!q.currencyId.isEmpty()&&!l.currencyId.equals(q.currencyId))continue;
            if(min>=0&&l.price<min||max>=0&&l.price>max)continue;
            if(!search.isBlank()&&!(l.title+" "+l.description+" "+l.ownerName+" "+l.category).toLowerCase(Locale.ROOT).contains(search))continue;
            matches.add(l);
        }
        Comparator<MarketplaceListing> order=switch(q.sort){case "OLDEST"->Comparator.comparingLong(l->l.createdAt);case "LOWEST"->Comparator.comparingLong(l->l.price);case "HIGHEST"->Comparator.comparingLong((MarketplaceListing l)->l.price).reversed();case "VIEWS"->Comparator.comparingLong((MarketplaceListing l)->l.views).reversed();default->Comparator.comparingLong((MarketplaceListing l)->l.createdAt).reversed();};
        matches.sort(order.thenComparing(l->l.id));state.total=matches.size();state.page=Math.min(q.page,Math.max(0,(matches.size()-1)/PAGE_SIZE));
        int from=state.page*PAGE_SIZE,to=Math.min(matches.size(),from+PAGE_SIZE);state.hasMore=to<matches.size();
        for(var l:matches.subList(from,to))state.listings.add(project(l,self,data));
        var detail=data.find(session.detail);if(detail!=null&&!detail.status.equals("DELETED")&&!blocked.eitherBlocks(self,detail.ownerUuid))state.detail=project(detail,self,data);
        state.notifications=data.notifications(self).stream().limit(40).toList();
        state.purchases=data.receipts(self).stream().limit(20).toList();return state;
    }
    private static MarketplaceListing project(MarketplaceListing source,String viewer,MarketplaceSavedData data) {
        MarketplaceListing copy=JSON.fromJson(JSON.toJson(source),MarketplaceListing.class);copy.favorite=data.isFavorite(viewer,source.id);
        if(!source.ownerUuid.equals(viewer)&&!source.buyerUuid.equals(viewer)){copy.buyerUuid="";copy.buyerName="";}
        copy.purchaseRequestId="";copy.createRequestId="";return copy;
    }
    private static void send(ServerPlayer p,MarketplaceRequest request,String code,String id) {
        MarketplaceState state=state(p);state.requestId=request==null||request.requestId==null?"":request.requestId;
        state.code=code;state.listingId=id==null?"":id;sendPacket(p,state);
    }
    private static void sendPacket(ServerPlayer p,MarketplaceState state) {
        String json=JSON.toJson(state);if(json.length()>MarketplaceStatePacket.MAX_LENGTH)throw new IllegalStateException("Marketplace page exceeds packet budget");
        PhoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MarketplaceStatePacket(json));
    }
    public static void broadcast(MinecraftServer server,UUID except) {
        var sessions=SESSIONS.get(server);if(sessions==null)return;
        for(var player:server.getPlayerList().getPlayers())if(!player.getUUID().equals(except)&&sessions.containsKey(player.getUUID()))send(player,null,"","");
    }
    public static void upload(ServerPlayer player,PhonePhotoUploadMessage message) {
        if(!PhoneInventoryUtil.hasPhone(player))return;
        MarketplaceRequest response=new MarketplaceRequest();response.requestId=message.photoId();
        try {
            if(!message.photoId().matches("market-[a-f0-9-]{36}")||!PhoneImageLimits.accepts(message.imageData()))throw new IllegalArgumentException("INVALID_PHOTO");
            Session s=session(player);long now=System.currentTimeMillis();
            if(now-s.lastUpload<400)throw new IllegalArgumentException("RATE_LIMIT");s.lastUpload=now;
            MarketplaceSavedData.get(player.server).putImage(player.getUUID().toString(),message.photoId(),message.imageData());
            var state=state(player);state.requestId=message.photoId();state.code="PHOTO_READY";state.uploadedId=message.photoId();sendPacket(player,state);
        }catch(RuntimeException e){send(player,response,knownCode(e.getMessage()),"");}
    }
    public static void photo(ServerPlayer player,String id) {
        var bytes=MarketplaceSavedData.get(player.server).imageFor(player.getUUID().toString(),id,
                PhoneCommunicationSavedData.get(player.server)::eitherBlocks);
        if(bytes!=null)PhoneNetwork.sendPhotoData(player,id,bytes);
    }
    private static final class Session {
        MarketplaceRequest query=new MarketplaceRequest();String detail="";long lastRead,lastWrite,lastUpload,refill=System.currentTimeMillis();double tokens=20;
    }
}

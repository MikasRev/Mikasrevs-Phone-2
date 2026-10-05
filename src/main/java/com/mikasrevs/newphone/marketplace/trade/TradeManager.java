package com.mikasrevs.newphone.marketplace.trade;

import com.google.gson.Gson;
import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.marketplace.data.MarketplaceSavedData;
import com.mikasrevs.newphone.network.PhoneNetwork;
import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.PacketDistributor;

/** All calls execute on the server thread. Inventory saves use a durable operation journal. */
public final class TradeManager {
    private static final Gson JSON=new Gson();
    private static final String MARKER="newphone_trade_operation";
    private static final Map<MinecraftServer,Store> STORES=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,Long>> LAST_WRITE=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,Integer>> PAGES=new WeakHashMap<>();
    private TradeManager(){}
    public static void start(MinecraftServer server){store(server);}
    private static Store store(MinecraftServer server){return STORES.computeIfAbsent(server,Store::new);}
    public static void handle(ServerPlayer player,String body){
        if(body==null||body.length()>2048||!PhoneInventoryUtil.hasPhone(player))return;
        TradeRequest r=null;
        try{
            r=JSON.fromJson(body,TradeRequest.class);if(r==null)throw new IllegalArgumentException("INVALID_REQUEST");r.validate();
            Store s=store(player.server);s.requireHealthy();String actor=player.getUUID().toString();long now=System.currentTimeMillis();
            if(!r.action.equals("SYNC")){
                var times=LAST_WRITE.computeIfAbsent(player.server,k->new HashMap<>());
                if(now-times.getOrDefault(player.getUUID(),0L)<250)throw new IllegalArgumentException("RATE_LIMIT");times.put(player.getUUID(),now);
            }
            recover(player,s);
            if(s.data.expire(now))s.flush();
            PAGES.computeIfAbsent(player.server,k->new HashMap<>()).put(player.getUUID(),r.page);
            String code="";
            switch(r.action){
                case "SYNC" -> {}
                case "INVITE" -> {
                    var listing=MarketplaceSavedData.get(player.server).find(r.listing);
                    if(listing==null||!listing.status.equals("ACTIVE"))throw new IllegalArgumentException("UNAVAILABLE");
                    if(!listing.category.equals("ITEMS"))throw new IllegalArgumentException("TRADE_ITEMS_ONLY");
                    ServerPlayer target=player.server.getPlayerList().getPlayer(UUID.fromString(listing.ownerUuid));
                    if(target==null||!target.isAlive()||!PhoneInventoryUtil.hasPhone(target))throw new IllegalArgumentException("TRADE_OFFLINE");
                    if(PhoneCommunicationSavedData.get(player.server).eitherBlocks(actor,listing.ownerUuid))throw new IllegalArgumentException("CONTACT_BLOCKED");
                    s.data.invite(r.requestId,actor,player.getGameProfile().getName(),listing.ownerUuid,listing.ownerName,listing.id,listing.title,now);
                    s.flush();code="TRADE_SENT";
                }
                case "ACCEPT" -> {requirePartner(player,s,r.id);s.data.accept(actor,r.id,r.revision,now);s.flush();code="TRADE_ACCEPTED";}
                case "CANCEL" -> {s.data.cancel(actor,r.id);s.flush();returnBoth(player.server,s,s.data.require(actor,r.id));code="TRADE_CANCELLED";}
                case "ADD" -> {requirePartner(player,s,r.id);deposit(player,s,r,now);code="TRADE_UPDATED";}
                case "REMOVE" -> {requirePartner(player,s,r.id);s.data.remove(actor,r.id,r.revision,r.parcel,now);s.flush();autoClaim(player,s);code="TRADE_UPDATED";}
                case "CHAT" -> {requirePartner(player,s,r.id);s.data.chat(actor,r.id,r.requestId,r.text,now);s.flush();code="TRADE_CHAT_SENT";}
                case "CONFIRM" -> {
                    requirePartner(player,s,r.id);boolean completed=s.data.confirm(actor,r.id,r.revision,now);s.flush();code=completed?"TRADE_COMPLETE":"TRADE_READY";
                    if(completed){var d=s.data.require(actor,r.id);autoClaim(player.server.getPlayerList().getPlayer(UUID.fromString(d.a)),s);autoClaim(player.server.getPlayerList().getPlayer(UUID.fromString(d.b)),s);}
                }
                case "UNREADY" -> {s.data.unready(actor,r.id,r.revision,now);s.flush();code="TRADE_UPDATED";}
                case "CLAIM" -> {claim(player,s,r.parcel);code="TRADE_CLAIMED";}
                default -> throw new IllegalArgumentException("INVALID_REQUEST");
            }
            send(player,r,code);
            if(!r.action.equals("SYNC")&&!r.action.equals("CLAIM"))notifyPartner(player,s,r.id,code);
        }catch(IllegalArgumentException e){send(player,r,known(e.getMessage()));}
        catch(IOException|RuntimeException e){LogUtils.getLogger().error("Trade request interrupted; custody journal retained",e);send(player,r,"TRADE_STORAGE");}
    }
    private static String known(String code){return code!=null&&code.matches("[A-Z_]{2,32}")?code:"INVALID_REQUEST";}
    private static void requirePartner(ServerPlayer player,Store store,String id){
        TradeLedger.Deal d=store.data.require(player.getUUID().toString(),id);
        ServerPlayer other=player.server.getPlayerList().getPlayer(UUID.fromString(d.a.equals(player.getUUID().toString())?d.b:d.a));
        if(!player.isAlive()||other==null||!other.isAlive()||!PhoneInventoryUtil.hasPhone(other))throw new IllegalArgumentException("TRADE_OFFLINE");
        if(PhoneCommunicationSavedData.get(player.server).eitherBlocks(d.a,d.b))throw new IllegalArgumentException("CONTACT_BLOCKED");
    }
    private static void deposit(ServerPlayer player,Store store,TradeRequest request,long now)throws IOException{
        ItemStack actual=player.getInventory().getItem(request.slot);
        if(PhoneInventoryUtil.protectedPhone(player,actual))throw new IllegalArgumentException("TRADE_PHONE_IN_USE");
        if(actual.isEmpty()||actual.getCount()<request.count||!TradeInventory.fingerprint(actual).equals(request.fingerprint))throw new IllegalArgumentException("TRADE_ITEM_CHANGED");
        CompoundTag full=actual.copyWithCount(request.count).serializeNBT();
        if(full.toString().length()>65536 || (actual.getShareTag()!=null&&actual.getShareTag().toString().length()>8192))throw new IllegalArgumentException("TRADE_ITEM_LARGE");
        String actor=player.getUUID().toString(),op=UUID.randomUUID().toString();
        store.data.prepareDeposit(actor,request.id,request.revision,op,full,now);
        store.flush(); // The exact item is journaled before inventory changes.
        actual.shrink(request.count);player.getInventory().setChanged();
        player.getPersistentData().putString(MARKER,op);
        savePlayer(player,op);
        store.data.recover(actor,true);store.flush();syncInventory(player);
    }
    private static void claim(ServerPlayer player,Store store,String parcel)throws IOException{
        String actor=player.getUUID().toString();
        TradeLedger.Parcel p=store.data.parcels.get(parcel);
        if(p==null||!p.owner.equals(actor))throw new IllegalArgumentException("TRADE_NOT_FOUND");
        ItemStack stack=ItemStack.of(p.stack);
        if(stack.isEmpty())throw new IllegalArgumentException("TRADE_ITEM_UNAVAILABLE");
        List<ItemStack> plan=TradeInventory.insert(player.getInventory().items,stack);
        if(plan==null)throw new IllegalArgumentException("TRADE_INVENTORY_FULL");
        String op=UUID.randomUUID().toString();store.data.prepareClaim(actor,parcel,op);store.flush();
        for(int i=0;i<36;i++)player.getInventory().setItem(i,plan.get(i));
        player.getPersistentData().putString(MARKER,op);savePlayer(player,op);
        store.data.recover(actor,true);store.flush();syncInventory(player);
    }
    private static void autoClaim(ServerPlayer player,Store store)throws IOException{
        if(player==null||!player.isAlive())return;
        recover(player,store);
        for(var p:new ArrayList<>(store.data.mail(player.getUUID().toString()))){
            try{claim(player,store,p.id);}catch(IllegalArgumentException e){if(!e.getMessage().equals("TRADE_INVENTORY_FULL")&&!e.getMessage().equals("TRADE_ITEM_UNAVAILABLE"))throw e;}
        }
    }
    private static void returnBoth(MinecraftServer server,Store store,TradeLedger.Deal deal)throws IOException{
        autoClaim(server.getPlayerList().getPlayer(UUID.fromString(deal.a)),store);
        autoClaim(server.getPlayerList().getPlayer(UUID.fromString(deal.b)),store);
    }
    private static void syncInventory(ServerPlayer player){player.getInventory().setChanged();player.containerMenu.broadcastChanges();player.inventoryMenu.broadcastChanges();}
    private static void recover(ServerPlayer player,Store store)throws IOException{
        String actor=player.getUUID().toString();var op=store.data.pending.get(actor);if(op==null)return;
        boolean applied=player.getPersistentData().getString(MARKER).equals(op.id);
        if(applied)savePlayer(player,op.id);
        store.data.recover(actor,applied);store.flush();syncInventory(player);
    }
    private static void savePlayer(ServerPlayer player,String op)throws IOException{
        com.mikasrevs.newphone.server.data.PhonePlayerStorage.save(player,MARKER,op);
    }
    public static void login(ServerPlayer player){
        try{Store s=store(player.server);s.requireHealthy();autoClaim(player,s);send(player,null,"");}
        catch(IOException|RuntimeException e){LogUtils.getLogger().error("Trade login recovery deferred",e);send(player,null,"TRADE_STORAGE");}
    }
    public static void unavailable(ServerPlayer player){
        try{
            Store s=store(player.server);s.requireHealthy();recover(player,s);
            var d=s.data.current(player.getUUID().toString());if(d!=null){s.data.cancel(player.getUUID().toString(),d.id);s.flush();returnBoth(player.server,s,d);notifyPartner(player,s,d.id,"TRADE_CANCELLED");}
        }catch(IOException|RuntimeException e){LogUtils.getLogger().error("Trade cancellation deferred; items remain in custody",e);}
        var times=LAST_WRITE.get(player.server);if(times!=null)times.remove(player.getUUID());
        var pages=PAGES.get(player.server);if(pages!=null)pages.remove(player.getUUID());
    }
    public static void tick(MinecraftServer server){
        try{
            Store s=store(server);s.requireHealthy();boolean changed=s.data.expire(System.currentTimeMillis());
            for(var d:s.data.deals.values())if(d.live()){
                ServerPlayer a=server.getPlayerList().getPlayer(UUID.fromString(d.a)),b=server.getPlayerList().getPlayer(UUID.fromString(d.b));
                if(a==null||b==null||!a.isAlive()||!b.isAlive()||!PhoneInventoryUtil.hasPhone(a)||!PhoneInventoryUtil.hasPhone(b)||PhoneCommunicationSavedData.get(server).eitherBlocks(d.a,d.b)){
                    s.data.cancel(d.a,d.id);changed=true;
                }
            }
            if(changed)s.flush();
            for(ServerPlayer player:server.getPlayerList().getPlayers()){
                String actor=player.getUUID().toString();
                if(s.data.pending.containsKey(actor)||s.data.mailCount(actor)>0){
                    int before=s.data.mailCount(actor);autoClaim(player,s);
                    if(before!=s.data.mailCount(actor)&&PhoneInventoryUtil.hasPhone(player))send(player,null,"");
                }
            }
            if(changed)broadcast(server,null);
        }catch(IOException|RuntimeException e){LogUtils.getLogger().error("Trade maintenance deferred",e);}
    }
    public static void stop(MinecraftServer server){
        Store s=STORES.get(server);
        try{if(s!=null&&!s.failed){s.data.cancelAll();s.flush();for(ServerPlayer p:server.getPlayerList().getPlayers())autoClaim(p,s);}}catch(IOException e){LogUtils.getLogger().error("Trade shutdown journal could not be written",e);}
        STORES.remove(server);LAST_WRITE.remove(server);PAGES.remove(server);
    }
    private static void notifyPartner(ServerPlayer actor,Store s,String id,String code){
        TradeLedger.Deal d=id.isEmpty()?s.data.current(actor.getUUID().toString()):s.data.deals.get(id);
        if(d==null||!d.member(actor.getUUID().toString()))return;
        String other=d.a.equals(actor.getUUID().toString())?d.b:d.a;
        ServerPlayer target=actor.server.getPlayerList().getPlayer(UUID.fromString(other));
        if(target!=null){TradeRequest signal=new TradeRequest();signal.id=d.id;send(target,signal,code);}
    }
    public static void broadcast(MinecraftServer server,UUID except){
        Set<String> relevant=new HashSet<>();
        for(var d:store(server).data.deals.values())if(d.live()||d.status.equals("CANCELLED")){relevant.add(d.a);relevant.add(d.b);}
        for(ServerPlayer p:server.getPlayerList().getPlayers())if(!p.getUUID().equals(except)&&relevant.contains(p.getUUID().toString())&&PhoneInventoryUtil.hasPhone(p))send(p,null,"");
    }
    private static void send(ServerPlayer player,TradeRequest request,String code){
        TradeState view=new TradeState();view.self=player.getUUID().toString();view.requestId=request==null||request.requestId==null?"":request.requestId;view.code=code;
        Store s=store(player.server);
        if(s.failed){view.code="TRADE_STORAGE";}else{
            TradeLedger.Deal d=s.data.current(view.self);
            if(d==null&&request!=null&&request.id!=null&&!request.id.isEmpty()){var old=s.data.deals.get(request.id);if(old!=null&&old.member(view.self))d=old;}
            if(d!=null){
                view.id=d.id;view.status=d.status;view.title=d.title;view.listing=d.listing;view.revision=d.revision;view.deadline=d.deadline;
                boolean first=d.a.equals(view.self);view.other=first?d.b:d.a;view.otherName=first?d.bName:d.aName;
                view.incoming=!first;view.ready=first?d.readyA:d.readyB;view.otherReady=first?d.readyB:d.readyA;
                view.chat.addAll(d.chat);
                for(var p:d.offer(view.self))view.mine.add(row(p.id,-1,ItemStack.of(p.stack)));
                for(var p:d.offer(view.other))view.theirs.add(row(p.id,-1,ItemStack.of(p.stack)));
            }
            var mail=s.data.mail(view.self);view.mailCount=mail.size();
            int page=PAGES.getOrDefault(player.server,Map.of()).getOrDefault(player.getUUID(),0);view.page=Math.min(page,Math.max(0,(mail.size()-1)/8));
            for(var p:mail.subList(view.page*8,Math.min(mail.size(),view.page*8+8)))view.mail.add(row(p.id,-1,ItemStack.of(p.stack)));
            for(int i=0;i<36;i++){
                ItemStack stack=player.getInventory().getItem(i);
                if(!stack.isEmpty()){TradeState.Row row=row("",i,stack);row.fingerprint=TradeInventory.fingerprint(stack);row.protectedPhone=PhoneInventoryUtil.protectedPhone(player,stack);view.inventory.add(row);}
            }
        }
        String json=JSON.toJson(view);
        if(json.length()>TradeStatePacket.MAX_LENGTH){LogUtils.getLogger().error("Trade view exceeds packet budget");return;}
        PhoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new TradeStatePacket(json));
    }
    private static TradeState.Row row(String id,int slot,ItemStack stack){
        TradeState.Row r=new TradeState.Row();r.id=id;r.slot=slot;r.count=stack.getCount();r.item=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        r.name=stack.getHoverName().getString();if(r.name.length()>128)r.name=r.name.substring(0,128);
        CompoundTag tag=stack.getShareTag();if(tag!=null&&tag.toString().length()<=8192)r.tag=tag.toString();return r;
    }
    private static final class Store {
        final Path target;TradeLedger data=new TradeLedger();boolean failed;
        Store(MinecraftServer server){
            target=server.getWorldPath(LevelResource.ROOT).resolve("data/newphone_trades.dat");
            try{
                if(Files.exists(target))data=TradeLedger.load(NbtIo.readCompressed(target.toFile()));
                data.cancelAll();flush(); // Restarted sessions return custody; pending deposits are recovered on login.
            }catch(IOException|RuntimeException e){failed=true;LogUtils.getLogger().error("Trade storage unavailable; original file kept untouched",e);}
        }
        void requireHealthy(){if(failed)throw new IllegalArgumentException("TRADE_STORAGE");}
        void flush()throws IOException{
            if(failed)throw new IOException("Trade journal is locked");
            try{
                Files.createDirectories(target.getParent());Path temp=target.resolveSibling("newphone_trades.pending");
                NbtIo.writeCompressed(data.save(),temp.toFile());
                try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
                try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException e){throw new IOException("Trade custody requires atomic file replacement",e);}
            }catch(IOException|RuntimeException e){failed=true;throw e;}
        }
    }
}

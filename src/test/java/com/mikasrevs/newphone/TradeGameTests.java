package com.mikasrevs.newphone;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import com.google.gson.Gson;
import com.mikasrevs.newphone.marketplace.trade.*;
import com.mikasrevs.newphone.marketplace.data.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Registered only in the isolated Gradle GameTest run, never packaged in the released mod. */
@GameTestHolder("newphone")
@PrefixGameTestTemplate(false)
public final class TradeGameTests {
    @GameTest(templateNamespace="forge",template="empty3x3",timeoutTicks=200)
    public static void inventoryDelivery(GameTestHelper helper){TradeInventoryChecks.run();helper.succeed();}

    @GameTest(templateNamespace="forge",template="empty3x3",timeoutTicks=400)
    public static void completeTradeWithPlayerSaves(GameTestHelper helper)throws Exception{
        var server=helper.getLevel().getServer();
        var a=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"TradeBuyer"));
        var b=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"TradeSeller"));
        var spectator=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"SaveSpectator"));
        // Register server test actors without sockets. Vanilla saveAll and UUID lookups use these two indexes.
        Class<?> field=server.getPlayerList().getClass();java.lang.reflect.Field index=null;
        while(field!=null){try{index=field.getDeclaredField("playersByUUID");break;}catch(NoSuchFieldException e){field=field.getSuperclass();}}
        index.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID,ServerPlayer> players=(Map<UUID,ServerPlayer>)index.get(server.getPlayerList());
        var rosterField=field.getDeclaredField("players");rosterField.setAccessible(true);
        @SuppressWarnings("unchecked") List<ServerPlayer> roster=(List<ServerPlayer>)rosterField.get(server.getPlayerList());
        roster.add(a);roster.add(b);players.put(a.getUUID(),a);players.put(b.getUUID(),b);
        roster.add(spectator);players.put(spectator.getUUID(),spectator);
        spectator.getInventory().setItem(0,new ItemStack(Items.STONE,1));
        spectator.getPersistentData().putString("audit_save","baseline");
        com.mikasrevs.newphone.server.data.PhonePlayerStorage.save(spectator,"audit_save","baseline");
        spectator.getInventory().setItem(0,new ItemStack(Items.STONE,2));
        a.getInventory().clearContent();b.getInventory().clearContent();
        a.getInventory().setItem(8,new ItemStack(NewPhone.Z_PHONE.get()));b.getInventory().setItem(8,new ItemStack(NewPhone.Z_PHONE.get()));
        var diamond=new ItemStack(Items.DIAMOND,16);var tag=new CompoundTag();tag.putString("TradeTest","Exact NBT survives");diamond.setTag(tag);
        a.getInventory().setItem(0,diamond);b.getInventory().setItem(0,new ItemStack(Items.EMERALD,7));
        var draft=new MarketplaceRequest();draft.requestId=UUID.randomUUID().toString();draft.category="ITEMS";draft.title="Trade fixture";draft.currencyId="newphone:trade";
        var listing=MarketplaceSavedData.get(server).create(b.getUUID().toString(),"TradeSeller",draft,"Trade","Trade");
        var invite=new TradeRequest();invite.action="INVITE";invite.listing=listing.id;invite.requestId=UUID.randomUUID().toString();
        helper.runAtTickTime(10,()->handle(a,invite));
        helper.runAtTickTime(20,()->{var d=ledger(server).current(a.getUUID().toString());helper.assertTrue(d!=null,"invitation was rejected");handle(b,command("ACCEPT",d));});
        helper.runAtTickTime(30,()->{var d=ledger(server).current(a.getUUID().toString());var r=command("ADD",d);r.slot=0;r.count=16;r.fingerprint=TradeInventory.fingerprint(a.getInventory().getItem(0));handle(a,r);});
        helper.runAtTickTime(40,()->{var d=ledger(server).current(b.getUUID().toString());helper.assertTrue(a.getInventory().countItem(Items.DIAMOND)==0&&d.offerA.size()==1,"deposit did not reach custody");
            try {
                var file=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(spectator.getUUID()+".dat");
                var inv=net.minecraft.nbt.NbtIo.readCompressed(file.toFile()).getList("Inventory",10);
                helper.assertTrue(inv.getCompound(0).getByte("Count")==1,"Trade saved an unrelated online player's inventory");
            } catch(java.io.IOException e){throw new AssertionError(e);}
            var r=command("ADD",d);r.slot=0;r.count=7;r.fingerprint=TradeInventory.fingerprint(b.getInventory().getItem(0));handle(b,r);});
        helper.runAtTickTime(50,()->{var d=ledger(server).current(a.getUUID().toString());handle(a,command("CONFIRM",d));helper.assertTrue(d.readyA&&d.status.equals("ACTIVE"),"single confirmation completed trade");});
        helper.runAtTickTime(60,()->{var d=ledger(server).current(b.getUUID().toString());handle(b,command("CONFIRM",d));});
        helper.runAtTickTime(70,()->{
            helper.assertTrue(a.getInventory().countItem(Items.EMERALD)==7&&b.getInventory().countItem(Items.DIAMOND)==16,"automatic inventory delivery failed");
            helper.assertTrue(b.getInventory().items.stream().anyMatch(s->s.getItem()==Items.DIAMOND&&tag.equals(s.getTag())),"delivery lost exact NBT");
            helper.assertTrue(ledger(server).pending.isEmpty()&&ledger(server).mail(a.getUUID().toString()).isEmpty()&&ledger(server).mail(b.getUUID().toString()).isEmpty(),"player save journal not finalized");
            var closed=ledger(server).deals.values().stream().filter(d->d.member(a.getUUID().toString())&&d.status.equals("COMPLETE")).reduce((x,y)->y).orElseThrow();
            handle(b,command("CONFIRM",closed));
            helper.assertTrue(a.getInventory().countItem(Items.EMERALD)==7&&b.getInventory().countItem(Items.DIAMOND)==16,"confirmation replay duplicated items");
        });
        helper.runAtTickTime(80,()->{var r=new TradeRequest();r.action="INVITE";r.listing=listing.id;r.requestId=UUID.randomUUID().toString();handle(a,r);});
        helper.runAtTickTime(90,()->handle(b,command("ACCEPT",ledger(server).current(b.getUUID().toString()))));
        helper.runAtTickTime(100,()->{var r=command("ADD",ledger(server).current(a.getUUID().toString()));r.slot=0;r.count=7;r.fingerprint=TradeInventory.fingerprint(a.getInventory().getItem(0));handle(a,r);});
        helper.runAtTickTime(110,()->{var r=command("ADD",ledger(server).current(b.getUUID().toString()));r.slot=0;r.count=16;r.fingerprint=TradeInventory.fingerprint(b.getInventory().getItem(0));handle(b,r);});
        helper.runAtTickTime(120,()->{
            for(int i=0;i<36;i++)if(i!=8)a.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            handle(a,command("CONFIRM",ledger(server).current(a.getUUID().toString())));
        });
        helper.runAtTickTime(130,()->handle(b,command("CONFIRM",ledger(server).current(b.getUUID().toString()))));
        helper.runAtTickTime(140,()->{
            helper.assertTrue(a.getInventory().countItem(Items.DIAMOND)==0&&ledger(server).mailCount(a.getUUID().toString())==1,"full inventory lost the delivery parcel");
            TradeManager.tick(server);helper.assertTrue(ledger(server).mailCount(a.getUUID().toString())==1,"full-inventory auto delivery consumed custody");
            a.getInventory().setItem(0,ItemStack.EMPTY);TradeManager.tick(server);
            helper.assertTrue(a.getInventory().countItem(Items.DIAMOND)==16&&ledger(server).mailCount(a.getUUID().toString())==0,"automatic delivery after freeing space failed");
        });
        helper.runAtTickTime(150,()->{var r=new TradeRequest();r.action="INVITE";r.listing=listing.id;r.requestId=UUID.randomUUID().toString();handle(a,r);});
        helper.runAtTickTime(160,()->handle(b,command("ACCEPT",ledger(server).current(b.getUUID().toString()))));
        helper.runAtTickTime(170,()->{var r=command("ADD",ledger(server).current(a.getUUID().toString()));r.slot=0;r.count=16;r.fingerprint=TradeInventory.fingerprint(a.getInventory().getItem(0));handle(a,r);});
        helper.runAtTickTime(180,()->{var r=command("ADD",ledger(server).current(b.getUUID().toString()));r.slot=0;r.count=7;r.fingerprint=TradeInventory.fingerprint(b.getInventory().getItem(0));handle(b,r);});
        helper.runAtTickTime(190,()->TradeManager.unavailable(a));
        helper.runAtTickTime(200,()->{
            helper.assertTrue(ledger(server).current(a.getUUID().toString())==null&&ledger(server).mailCount(a.getUUID().toString())==0&&ledger(server).mailCount(b.getUUID().toString())==0,"disconnect did not deliver refunds automatically");
            helper.assertTrue(a.getInventory().countItem(Items.DIAMOND)==16&&b.getInventory().countItem(Items.EMERALD)==7,"direct inventory refund failed");
            for(String bad:List.of("{\"action\":null}","{\"action\":\"SYNC\",\"id\":null,\"requestId\":\""+UUID.randomUUID()+"\"}"))TradeManager.handle(a,bad);
            helper.assertTrue(ledger(server).pending.isEmpty(),"malformed request damaged inventory journal");
        });
        helper.runAtTickTime(210,()->{var r=new TradeRequest();r.action="INVITE";r.listing=listing.id;r.requestId=UUID.randomUUID().toString();handle(a,r);});
        helper.runAtTickTime(220,()->{
            var d=ledger(server).current(b.getUUID().toString());handle(b,command("ACCEPT",d));
            var r=command("ADD",d);r.slot=8;r.count=1;r.fingerprint=TradeInventory.fingerprint(a.getInventory().getItem(8));handle(a,r);
            helper.assertTrue(a.getInventory().getItem(8).is(NewPhone.Z_PHONE.get())&&d.offerA.isEmpty(),"used phone entered escrow");
        });
        helper.runAtTickTime(230,()->{
            var d=ledger(server).current(a.getUUID().toString());var r=command("ADD",d);r.slot=0;r.count=16;r.fingerprint=TradeInventory.fingerprint(a.getInventory().getItem(0));handle(a,r);
            r=command("REMOVE",d);r.parcel=d.offerA.get(0).id;handle(a,r);
            helper.assertTrue(a.getInventory().countItem(Items.DIAMOND)==16&&ledger(server).mailCount(a.getUUID().toString())==0,"offer removal did not refund directly");
            r=command("ADD",d);r.slot=0;r.count=16;r.fingerprint=TradeInventory.fingerprint(a.getInventory().getItem(0));handle(a,r);
            handle(a,command("CONFIRM",d));
            long revision=d.revision;var chat=command("CHAT",d);chat.text="Gift, no return needed.";handle(b,chat);
            helper.assertTrue(d.readyA&&d.revision==revision&&d.chat.size()==1,"chat changed the offer or approval");
            handle(b,command("CONFIRM",d));
            helper.assertTrue(b.getInventory().countItem(Items.DIAMOND)==16&&d.status.equals("COMPLETE"),"one-sided gift did not complete");
        });
        helper.runAtTickTime(240,()->{var r=new TradeRequest();r.action="INVITE";r.listing=listing.id;r.requestId=UUID.randomUUID().toString();handle(a,r);});
        helper.runAtTickTime(250,()->{
            var d=ledger(server).current(b.getUUID().toString());handle(b,command("ACCEPT",d));handle(a,command("CONFIRM",d));handle(b,command("CONFIRM",d));
            helper.assertTrue(d.status.equals("COMPLETE")&&ledger(server).pending.isEmpty(),"both-empty approval failed");
        });
        helper.runAtTickTime(260,()->{var r=new TradeRequest();r.action="INVITE";r.listing=listing.id;r.requestId=UUID.randomUUID().toString();handle(a,r);});
        helper.runAtTickTime(270,()->{
            var d=ledger(server).current(b.getUUID().toString());handle(b,command("ACCEPT",d));
            a.getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));var r=command("ADD",d);r.slot=0;r.count=3;r.fingerprint=TradeInventory.fingerprint(a.getInventory().getItem(0));handle(a,r);
            roster.remove(a);players.remove(a.getUUID());TradeManager.unavailable(a);
            helper.assertTrue(ledger(server).mailCount(a.getUUID().toString())==1,"offline return was lost");
        });
        helper.runAtTickTime(280,()->{
            roster.add(a);players.put(a.getUUID(),a);TradeManager.login(a);
            helper.assertTrue(a.getInventory().countItem(Items.DIAMOND)==3&&ledger(server).mailCount(a.getUUID().toString())==0&&ledger(server).pending.isEmpty(),"login did not deliver offline refund automatically");
            roster.remove(a);roster.remove(b);roster.remove(spectator);players.remove(a.getUUID());players.remove(b.getUUID());players.remove(spectator.getUUID());helper.succeed();
        });
    }
    private static TradeRequest command(String action,TradeLedger.Deal d){var r=new TradeRequest();r.action=action;r.id=d.id;r.revision=d.revision;r.requestId=UUID.randomUUID().toString();return r;}
    private static void handle(ServerPlayer player,TradeRequest request){
        // GameTest servers can advance ticks faster than wall time. Isolate custody from the real-time rate gate.
        try{var f=TradeManager.class.getDeclaredField("LAST_WRITE");f.setAccessible(true);var servers=(Map<?,?>)f.get(null);var actors=(Map<?,?>)servers.get(player.server);if(actors!=null)actors.remove(player.getUUID());}
        catch(Exception e){throw new AssertionError(e);}
        TradeManager.handle(player,new Gson().toJson(request));
    }
    private static TradeLedger ledger(net.minecraft.server.MinecraftServer server){
        try{var f=TradeManager.class.getDeclaredField("STORES");f.setAccessible(true);Object store=((Map<?,?>)f.get(null)).get(server);var d=store.getClass().getDeclaredField("data");d.setAccessible(true);return (TradeLedger)d.get(store);}catch(Exception e){throw new AssertionError(e);}
    }
}

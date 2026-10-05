package com.mikasrevs.newphone;

import com.google.gson.Gson;
import com.mikasrevs.newphone.marketplace.trade.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;

/** No real world, inventory or bank writes. */
public final class TradeChecks {
    private static final String A=UUID.randomUUID().toString(),B=UUID.randomUUID().toString(),C=UUID.randomUUID().toString();
    private static int assertions;
    private static String uuid(){return UUID.randomUUID().toString();}
    private static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable r,String message){assertions++;try{r.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){}}
    public static void run(){validation();approval();giftsAndChat();layout();custody();recovery();expiry();conservation();packet();System.out.println("Trade checks passed: "+assertions+" assertions (custody, stale approvals, replay, cancellation, restart, journal boundaries, gifts, private chat, layout).");}
    private static CompoundTag stack(int count){
        CompoundTag item=new CompoundTag();item.putString("id","minecraft:diamond");item.putByte("Count",(byte)count);
        CompoundTag tag=new CompoundTag();tag.putInt("Damage",17);tag.putString("Custom","preserve");
        ListTag enchants=new ListTag();CompoundTag enchant=new CompoundTag();enchant.putString("id","minecraft:sharpness");enchant.putShort("lvl",(short)5);enchants.add(enchant);tag.put("Enchantments",enchants);item.put("tag",tag);
        CompoundTag caps=new CompoundTag();caps.putString("mod:capability","opaque server data");item.put("ForgeCaps",caps);return item;
    }
    private static TradeLedger.Deal active(TradeLedger ledger){var d=ledger.invite(uuid(),A,"Alex",B,"Steve","MP-000001","Sword",1000);ledger.accept(B,d.id,d.revision,1001);return d;}
    private static String add(TradeLedger data,TradeLedger.Deal d,String actor,int count){String op=uuid();data.prepareDeposit(actor,d.id,d.revision,op,stack(count),2000);data.recover(actor,true);return op;}
    private static TradeLedger restore(TradeLedger data){return TradeLedger.load(data.save());}
    private static void validation(){
        TradeRequest r=new TradeRequest();r.requestId=uuid();r.validate();r.action="ADD";rejects(r::validate,"negative slot accepted");
        r.slot=36;r.fingerprint="a".repeat(64);rejects(r::validate,"offhand slot accepted");r.slot=0;r.count=65;rejects(r::validate,"excessive quantity accepted");
        r.count=1;r.validate();r.fingerprint="client NBT";rejects(r::validate,"forged fingerprint accepted");r.action="TRANSFER_MONEY";rejects(r::validate,"unknown action accepted");
        var data=new TradeLedger();String id=uuid();var d=data.invite(id,A,"Alex",B,"Steve","MP-000001","Sword",1000);
        check(data.invite(id,A,"Alex",B,"Steve","MP-000001","Sword",1000)==d,"invite replay duplicates session");
        rejects(()->data.invite(id,A,"Alex",C,"Other","MP-000001","Sword",1000),"request changed target");
        rejects(()->data.invite(uuid(),A,"Alex",A,"Alex","MP-000001","Sword",1000),"self trade accepted");
        rejects(()->data.invite(uuid(),C,"Other",B,"Steve","MP-000001","Sword",1000),"multiple concurrent sessions");
        rejects(()->data.accept(A,id,d.revision,1001),"sender accepted own invitation");rejects(()->data.accept(C,id,d.revision,1001),"outsider accepted");
        rejects(()->data.accept(B,id,d.revision+1,1001),"stale acceptance");rejects(()->data.accept(B,id,d.revision,121000),"expired acceptance");
    }
    private static void approval(){
        var data=new TradeLedger();var d=active(data);
        add(data,d,A,1);add(data,d,B,1);long seen=d.revision;check(!data.confirm(A,d.id,seen,2001)&&d.readyA&&!d.readyB,"single approval completed trade");
        add(data,d,B,1);check(!d.readyA&&!d.readyB,"addition retained approvals");rejects(()->data.confirm(A,d.id,seen,2002),"old offer approved new items");
        long changed=d.revision;data.confirm(A,d.id,changed,2003);data.unready(A,d.id,changed,2004);check(!d.readyA,"withdraw approval ignored");
        data.confirm(A,d.id,changed,2005);check(data.confirm(B,d.id,changed,2006),"mutual approval failed");
        check(d.status.equals("COMPLETE")&&d.offerA.isEmpty()&&d.offerB.isEmpty(),"completed deal retained escrow");
        check(data.mailCount(A)==2&&data.mailCount(B)==1,"wrong delivery owners");data.confirm(A,d.id,changed,2007);data.cancel(B,d.id);
        check(data.mailCount(A)==2&&data.mailCount(B)==1,"completion replay duplicated custody");rejects(()->data.require(C,d.id),"outsider read deal");
        check(restore(data).mailCount(A)==2,"completion not persisted");
    }
    private static void custody(){
        var data=new TradeLedger();var d=active(data);String item=add(data,d,A,1);add(data,d,B,1);
        check(d.offerA.get(0).stack.equals(stack(1)),"NBT/capabilities changed");rejects(()->data.remove(B,d.id,d.revision,item,2001),"stolen escrow withdrawn");
        data.confirm(A,d.id,d.revision,2002);data.remove(A,d.id,d.revision,item,2003);check(!d.readyA&&!d.readyB&&data.mailCount(A)==1,"withdraw did not reset approval");
        data.cancel(B,d.id);data.cancel(A,d.id);check(data.mailCount(A)==1&&data.mailCount(B)==1,"cancel lost or duplicated items");
        var cap=new TradeLedger();var c=active(cap);for(int i=0;i<8;i++)add(cap,c,A,1);rejects(()->add(cap,c,A,1),"more than eight offered stacks");
        cap.cancel(A,c.id);check(cap.mailCount(A)==8,"full offer not returned");
    }
    private static void giftsAndChat(){
        var data=new TradeLedger();var d=active(data);
        check(!data.confirm(A,d.id,d.revision,2000)&&data.confirm(B,d.id,d.revision,2001),"empty mutual approval rejected");
        check(data.parcels.isEmpty(),"empty trade invented items");
        d=active(data);add(data,d,A,7);long revision=d.revision;data.confirm(A,d.id,revision,2002);
        String id=d.id,msg=uuid();data.chat(A,id,msg,"Hello, gift for you!",2003);data.chat(A,id,msg,"Hello, gift for you!",2004);
        check(d.chat.size()==1&&d.readyA&&d.revision==revision,"chat duplicated, changed offer or reset approval");
        rejects(()->data.chat(C,id,uuid(),"spy",2004),"outsider sent chat");
        rejects(()->data.chat(B,id,msg,"spoof",2004),"chat request replay changed sender");
        rejects(()->data.chat(A,id,uuid(),"x".repeat(241),2004),"oversized chat accepted");
        rejects(()->data.chat(A,id,uuid(),"bad\nmessage",2004),"control character accepted");
        for(int i=0;i<80;i++)data.chat(B,id,uuid(),"Message "+i,2005+i);
        check(d.chat.size()==60,"chat unbounded");
        var restored=restore(data);var saved=restored.require(A,id);
        check(saved.chat.size()==60&&saved.chat.get(59).text.equals("Message 79"),"chat serialization lost content");
        check(restored.confirm(B,id,revision,2200)&&restored.mailCount(B)==1&&restored.mailCount(A)==0,"one-sided gift rejected or ownership wrong");
        long deadline=d.deadline;rejects(()->data.chat(A,id,uuid(),"expired",deadline),"expired chat accepted");
        var r=new TradeRequest();r.requestId=uuid();r.id=id;r.action="CHAT";r.text="Valid";r.validate();
        r.text="§afake";rejects(r::validate,"chat formatting accepted");r.text=null;rejects(r::validate,"null chat accepted");
    }
    private static void layout(){
        for(int width:new int[]{320,427,854,1280,1920})for(int height:new int[]{240,480,720,1080}){
            int phoneWidth=Math.min((int)(width*.54),(int)(height*.6));int left=(width-phoneWidth)/2,right=left+phoneWidth;
            var rect=TradeChatLayout.place(width,height,left,right);
            check(rect.x()>=0&&rect.x()+rect.width()<=width&&rect.y()+rect.height()<=height,"chat outside viewport");
            check(rect.x()+rect.width()<=left||rect.x()>=right,"chat overlaps phone");
        }
    }
    private static void recovery(){
        for(boolean saved:new boolean[]{false,true}){
            var data=new TradeLedger();var d=active(data);data.prepareDeposit(A,d.id,d.revision,uuid(),stack(1),2000);
            var after=restore(data);after.cancelAll();after.recover(A,saved);after.recover(A,saved);
            check(after.mailCount(A)==(saved?1:0),"deposit journal boundary duplicated or lost item");check(after.pending.isEmpty(),"deposit not finalized");
            check(restore(after).mailCount(A)==(saved?1:0),"recovery not persisted");
        }
        var liveData=new TradeLedger();var live=active(liveData);liveData.prepareDeposit(A,live.id,live.revision,uuid(),stack(1),2000);
        rejects(()->liveData.prepareDeposit(A,live.id,live.revision,uuid(),stack(1),2001),"unresolved operation overwritten");
        liveData.recover(A,true);liveData.recover(A,true);check(live.offerA.size()==1,"deposit replay duplicated offer");liveData.cancel(A,live.id);
        for(boolean saved:new boolean[]{false,true}){
            var data=restore(liveData);String parcel=data.mail(A).get(0).id;rejects(()->data.prepareClaim(B,parcel,uuid()),"claim owner spoof");
            data.prepareClaim(A,parcel,uuid());var after=restore(data);after.recover(A,saved);after.recover(A,saved);
            check(after.mailCount(A)==(saved?0:1),"claim journal boundary delivered twice or lost item");
        }
        var damaged=liveData.save();var ps=damaged.getList("Parcels",Tag.TAG_COMPOUND);ps.add(ps.getCompound(0).copy());rejects(()->TradeLedger.load(damaged),"duplicate corrupt custody accepted");
        var unknown=liveData.save();unknown.putInt("Version",999);rejects(()->TradeLedger.load(unknown),"unknown storage version accepted");
    }
    private static void expiry(){
        var data=new TradeLedger();var d=active(data);add(data,d,A,1);add(data,d,B,1);
        check(!data.expire(d.deadline-1),"early expiry");check(data.expire(d.deadline),"no expiry");check(data.mailCount(A)==1&&data.mailCount(B)==1,"expiry lost items");
        var restored=restore(data);restored.cancelAll();check(restored.mailCount(A)==1,"restart duplicated return");
    }
    private static void conservation(){
        Random random=new Random(3489745);
        for(int run=0;run<200;run++){
            var data=new TradeLedger();var d=active(data);int a=1+random.nextInt(8),b=1+random.nextInt(8),total=0;
            for(int i=0;i<a;i++){int n=1+random.nextInt(64);add(data,d,A,n);total+=n;}
            for(int i=0;i<b;i++){int n=1+random.nextInt(64);add(data,d,B,n);total+=n;}
            data=restore(data);d=data.deals.get(d.id);
            if(random.nextBoolean()){data.confirm(A,d.id,d.revision,3000);data.confirm(B,d.id,d.revision,3001);}else data.cancel(A,d.id);
            data=restore(data);check(data.parcels.values().stream().mapToInt(p->p.stack.getByte("Count")).sum()==total,"random trade broke item conservation");
            int delivered=0;
            for(var p:new ArrayList<>(data.parcels.values())){
                String actor=p.owner;data.prepareClaim(actor,p.id,uuid());data=restore(data);boolean success=random.nextBoolean();data.recover(actor,success);
                if(success)delivered+=p.stack.getByte("Count");
            }
            check(delivered+data.parcels.values().stream().mapToInt(p->p.stack.getByte("Count")).sum()==total,"recovered claim broke conservation");
        }
    }
    private static void packet(){
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());try{var packet=new TradeStatePacket(new Gson().toJson(new TradeState()));TradeStatePacket.encode(packet,buffer);check(TradeStatePacket.decode(buffer).equals(packet),"packet round trip failed");}finally{buffer.release();}
        var worst=new TradeState();var row=new TradeState.Row();row.tag="<".repeat(8192);row.name="<".repeat(128);for(int i=0;i<36;i++)worst.inventory.add(row);for(int i=0;i<8;i++){worst.mine.add(row);worst.theirs.add(row);worst.mail.add(row);}
        check(new Gson().toJson(worst).length()<TradeStatePacket.MAX_LENGTH,"escaped trade exceeds packet limit");
    }
}

package com.mikasrevs.newphone.marketplace.trade;

import java.util.*;
import net.minecraft.nbt.*;

/** Server-owned custody. No client-provided item stacks ever enter this ledger. */
public final class TradeLedger {
    public static final int OFFER_LIMIT = 8;
    public static final int CHAT_LIMIT = 60, CHAT_LENGTH = 240;
    public final Map<String, Deal> deals = new LinkedHashMap<>();
    public final Map<String, Parcel> parcels = new LinkedHashMap<>();
    public final Map<String, Operation> pending = new LinkedHashMap<>();

    public Deal invite(String id, String sender, String senderName, String target, String targetName,
                       String listing, String title, long now) {
        Deal previous = deals.get(id);
        if (previous != null) {
            if (!previous.a.equals(sender) || !previous.b.equals(target) || !previous.listing.equals(listing)) fail("TRADE_CHANGED");
            return previous;
        }
        if (sender.equals(target)) fail("OWN_LISTING");
        if (current(sender) != null || current(target) != null) fail("TRADE_BUSY");
        if (mailCount(sender) >= 256 || mailCount(target) >= 256) fail("TRADE_MAIL_FULL");
        Deal d = new Deal(); d.id=id; d.a=sender; d.b=target; d.aName=senderName; d.bName=targetName;
        d.listing=listing; d.title=title; d.deadline=now+120_000; deals.put(id,d); return d;
    }
    public Deal current(String actor) {
        return deals.values().stream().filter(d -> d.live() && d.member(actor)).findFirst().orElse(null);
    }
    public Deal require(String actor, String id) {
        Deal d=deals.get(id); if(d==null || !d.member(actor)) fail("TRADE_NOT_FOUND"); return d;
    }
    public void accept(String actor, String id, long revision, long now) {
        Deal d=require(actor,id); check(d,revision,"REQUESTED",now);
        if(!d.b.equals(actor)) fail("TRADE_NOT_FOUND");
        d.status="ACTIVE"; touch(d,now);
    }
    public void cancel(String actor,String id) {
        Deal d=require(actor,id); if(!d.live()) return;
        cancel(d);
    }
    private void cancel(Deal d) {
        for(Parcel p:new ArrayList<>(d.offerA)) deliver(p,p.origin);
        for(Parcel p:new ArrayList<>(d.offerB)) deliver(p,p.origin);
        d.offerA.clear(); d.offerB.clear(); d.status="CANCELLED"; d.readyA=false; d.readyB=false; d.revision++;
    }
    public void cancelAll() { for(Deal d:deals.values()) if(d.live()) cancel(d); }
    public boolean expire(long now) {
        boolean changed=false;
        for(Deal d:deals.values()) if(d.live() && now>=d.deadline) {cancel(d);changed=true;}
        // Closed trades carry no custody; pending inventory operations keep their referenced trade.
        if(deals.size()>2000) {
            Set<String> referenced=new HashSet<>(); pending.values().forEach(p->referenced.add(p.deal));
            var it=deals.values().iterator();
            while(it.hasNext() && deals.size()>2000) {Deal d=it.next();if(!d.live()&&!referenced.contains(d.id)){it.remove();changed=true;}}
        }
        return changed;
    }
    public void prepareDeposit(String actor,String id,long revision,String operation,CompoundTag stack,long now) {
        if(pending.containsKey(actor)) fail("TRADE_RECOVERY");
        Deal d=require(actor,id); check(d,revision,"ACTIVE",now);
        if(mailCount(actor)>=256)fail("TRADE_MAIL_FULL");
        if(d.offer(actor).size()>=OFFER_LIMIT) fail("TRADE_OFFER_FULL");
        if(stack==null || stack.getByte("Count")<=0 || stack.getString("id").isEmpty()) fail("TRADE_ITEM_CHANGED");
        Operation p=new Operation();p.id=operation;p.actor=actor;p.kind="DEPOSIT";p.deal=id;
        p.parcel=new Parcel(operation,actor,actor,stack.copy());pending.put(actor,p);
        // Even an interrupted deposit invalidates all approvals before the player save changes.
        touch(d,now);
    }
    public void prepareClaim(String actor,String parcel,String operation) {
        if(pending.containsKey(actor)) fail("TRADE_RECOVERY");
        Parcel item=parcels.get(parcel); if(item==null||!item.owner.equals(actor)) fail("TRADE_NOT_FOUND");
        Operation p=new Operation();p.id=operation;p.actor=actor;p.kind="CLAIM";p.parcel=item.copy();pending.put(actor,p);
    }
    /** Marker and inventory were saved together in the player's vanilla .dat file. */
    public void recover(String actor, boolean playerSaveContainsOperation) {
        Operation p=pending.get(actor); if(p==null)return;
        if(playerSaveContainsOperation) {
            if(p.kind.equals("DEPOSIT")) {
                Deal d=deals.get(p.deal);
                if(d!=null && d.status.equals("ACTIVE")) d.offer(actor).add(p.parcel.copy());
                else deliver(p.parcel,actor);
            } else parcels.remove(p.parcel.id);
        }
        pending.remove(actor);
    }
    public void remove(String actor,String id,long revision,String parcel,long now) {
        Deal d=require(actor,id);check(d,revision,"ACTIVE",now);ensureSettled(d);
        var it=d.offer(actor).iterator();
        while(it.hasNext()){Parcel p=it.next();if(p.id.equals(parcel)){deliver(p,actor);it.remove();touch(d,now);return;}}
        fail("TRADE_NOT_FOUND");
    }
    public boolean confirm(String actor,String id,long revision,long now) {
        Deal d=require(actor,id);
        if(d.status.equals("COMPLETE"))return true;
        check(d,revision,"ACTIVE",now);ensureSettled(d);
        if(d.a.equals(actor))d.readyA=true;else d.readyB=true;
        // Approval does not change the offer revision, so both parties can approve the same snapshot.
        if(!d.readyA||!d.readyB)return false;
        for(Parcel p:d.offerA)deliver(p,d.b);
        for(Parcel p:d.offerB)deliver(p,d.a);
        d.offerA.clear();d.offerB.clear();d.status="COMPLETE";d.revision++;return true;
    }
    public void unready(String actor,String id,long revision,long now) {
        Deal d=require(actor,id);check(d,revision,"ACTIVE",now);
        if(d.a.equals(actor))d.readyA=false;else d.readyB=false;
    }
    /** Chat never changes the offer revision or either participant's approval. */
    public void chat(String actor,String id,String messageId,String text,long now) {
        Deal d=require(actor,id);check(d,d.revision,"ACTIVE",now);
        if(text==null||text.isBlank()||text.length()>CHAT_LENGTH||text.chars().anyMatch(c->Character.isISOControl(c)||c==0x00a7))fail("INVALID_REQUEST");
        UUID.fromString(messageId);
        for(TradeState.Message message:d.chat)if(message.id.equals(messageId)){
            if(!message.actor.equals(actor)||!message.text.equals(text))fail("INVALID_REQUEST");
            return;
        }
        d.chat.add(new TradeState.Message(messageId,actor,text,now));
        while(d.chat.size()>CHAT_LIMIT)d.chat.remove(0);
    }
    private void ensureSettled(Deal d){if(pending.containsKey(d.a)||pending.containsKey(d.b))fail("TRADE_RECOVERY");}
    private void deliver(Parcel p,String owner){Parcel copy=p.copy();copy.owner=owner;parcels.putIfAbsent(copy.id,copy);}
    public int mailCount(String actor){return (int)parcels.values().stream().filter(p->p.owner.equals(actor)).count();}
    public List<Parcel> mail(String actor){return parcels.values().stream().filter(p->p.owner.equals(actor)).toList();}
    private static void check(Deal d,long revision,String status,long now){
        if(!d.status.equals(status)||now>=d.deadline)fail("TRADE_CLOSED");
        if(d.revision!=revision)fail("TRADE_CHANGED");
    }
    private static void touch(Deal d,long now){d.readyA=false;d.readyB=false;d.revision++;d.deadline=now+600_000;}
    private static void fail(String code){throw new IllegalArgumentException(code);}

    public CompoundTag save() {
        CompoundTag root=new CompoundTag();root.putInt("Version",1);
        ListTag ds=new ListTag();deals.values().forEach(d->ds.add(d.save()));root.put("Deals",ds);
        ListTag ps=new ListTag();parcels.values().forEach(p->ps.add(p.save()));root.put("Parcels",ps);
        ListTag ops=new ListTag();pending.values().forEach(p->ops.add(p.save()));root.put("Pending",ops);return root;
    }
    public static TradeLedger load(CompoundTag root) {
        if(root.getInt("Version")!=1)throw new IllegalArgumentException("Unsupported trade storage version");
        TradeLedger data=new TradeLedger();
        for(Tag t:root.getList("Deals",Tag.TAG_COMPOUND)){
            Deal d=Deal.load((CompoundTag)t);
            if(!Set.of("REQUESTED","ACTIVE","COMPLETE","CANCELLED").contains(d.status)||d.a.equals(d.b)||d.offerA.size()>OFFER_LIMIT||d.offerB.size()>OFFER_LIMIT)
                throw new IllegalArgumentException("Invalid trade record");
            if(!d.live()&&(!d.offerA.isEmpty()||!d.offerB.isEmpty()))throw new IllegalArgumentException("Closed trade still owns escrow");
            UUID.fromString(d.id);UUID.fromString(d.a);UUID.fromString(d.b);
            if(data.deals.putIfAbsent(d.id,d)!=null)throw new IllegalArgumentException("Duplicate trade");
        }
        Set<String> custody=new HashSet<>();
        for(Deal d:data.deals.values())for(Parcel p:concat(d.offerA,d.offerB))if(!d.member(p.origin)||!custody.add(p.id))throw new IllegalArgumentException("Invalid escrow ownership");
        for(Tag t:root.getList("Parcels",Tag.TAG_COMPOUND)){
            Parcel p=Parcel.load((CompoundTag)t);if(!custody.add(p.id)||data.parcels.putIfAbsent(p.id,p)!=null)throw new IllegalArgumentException("Duplicate parcel");
        }
        for(Tag t:root.getList("Pending",Tag.TAG_COMPOUND)){
            Operation p=Operation.load((CompoundTag)t);
            if(!Set.of("DEPOSIT","CLAIM").contains(p.kind)||!p.actor.equals(p.parcel.owner)||data.pending.putIfAbsent(p.actor,p)!=null)
                throw new IllegalArgumentException("Invalid pending operation");
            if(p.kind.equals("CLAIM")&&!data.parcels.containsKey(p.parcel.id))throw new IllegalArgumentException("Missing pending parcel");
            if(p.kind.equals("CLAIM")&&!data.parcels.get(p.parcel.id).save().equals(p.parcel.save()))throw new IllegalArgumentException("Pending parcel differs from custody");
            if(p.kind.equals("DEPOSIT")&&(data.deals.get(p.deal)==null||!data.deals.get(p.deal).member(p.actor)))throw new IllegalArgumentException("Invalid pending deposit owner");
            if(p.kind.equals("DEPOSIT")&&!custody.add(p.parcel.id))throw new IllegalArgumentException("Duplicate pending escrow");
        }
        return data;
    }
    private static List<Parcel> concat(List<Parcel>a,List<Parcel>b){List<Parcel> all=new ArrayList<>(a);all.addAll(b);return all;}
    public static final class Deal {
        public String id="",a="",b="",aName="",bName="",listing="",title="",status="REQUESTED";
        public long revision=1,deadline; public boolean readyA,readyB;
        public final List<Parcel> offerA=new ArrayList<>(),offerB=new ArrayList<>();
        public final List<TradeState.Message> chat=new ArrayList<>();
        public boolean member(String actor){return a.equals(actor)||b.equals(actor);}
        public boolean live(){return status.equals("REQUESTED")||status.equals("ACTIVE");}
        public List<Parcel> offer(String actor){if(!member(actor))fail("TRADE_NOT_FOUND");return a.equals(actor)?offerA:offerB;}
        CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("Id",id);t.putString("A",a);t.putString("B",b);t.putString("AName",aName);t.putString("BName",bName);t.putString("Listing",listing);t.putString("Title",title);t.putString("Status",status);t.putLong("Revision",revision);t.putLong("Deadline",deadline);t.putBoolean("ReadyA",readyA);t.putBoolean("ReadyB",readyB);ListTag x=new ListTag();offerA.forEach(p->x.add(p.save()));t.put("OfferA",x);ListTag y=new ListTag();offerB.forEach(p->y.add(p.save()));t.put("OfferB",y);ListTag messages=new ListTag();for(var m:chat){CompoundTag v=new CompoundTag();v.putString("Id",m.id);v.putString("Actor",m.actor);v.putString("Text",m.text);v.putLong("Time",m.time);messages.add(v);}t.put("Chat",messages);return t;}
        static Deal load(CompoundTag t){Deal d=new Deal();d.id=t.getString("Id");d.a=t.getString("A");d.b=t.getString("B");d.aName=t.getString("AName");d.bName=t.getString("BName");d.listing=t.getString("Listing");d.title=t.getString("Title");d.status=t.getString("Status");d.revision=t.getLong("Revision");d.deadline=t.getLong("Deadline");d.readyA=t.getBoolean("ReadyA");d.readyB=t.getBoolean("ReadyB");for(Tag p:t.getList("OfferA",Tag.TAG_COMPOUND))d.offerA.add(Parcel.load((CompoundTag)p));for(Tag p:t.getList("OfferB",Tag.TAG_COMPOUND))d.offerB.add(Parcel.load((CompoundTag)p));Set<String> ids=new HashSet<>();for(Tag message:t.getList("Chat",Tag.TAG_COMPOUND)){CompoundTag v=(CompoundTag)message;String id=v.getString("Id"),actor=v.getString("Actor"),text=v.getString("Text");UUID.fromString(id);if(!ids.add(id)||!d.member(actor)||text.isBlank()||text.length()>CHAT_LENGTH||text.chars().anyMatch(c->Character.isISOControl(c)||c==0x00a7)||d.chat.size()>=CHAT_LIMIT)throw new IllegalArgumentException("Invalid trade chat");d.chat.add(new TradeState.Message(id,actor,text,v.getLong("Time")));}return d;}
    }
    public static final class Parcel {
        public String id,origin,owner; public CompoundTag stack;
        public Parcel(String id,String origin,String owner,CompoundTag stack){this.id=id;this.origin=origin;this.owner=owner;this.stack=stack.copy();}
        public Parcel copy(){return new Parcel(id,origin,owner,stack);}
        CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("Id",id);t.putString("Origin",origin);t.putString("Owner",owner);t.put("Stack",stack.copy());return t;}
        static Parcel load(CompoundTag t){Parcel p=new Parcel(t.getString("Id"),t.getString("Origin"),t.getString("Owner"),t.getCompound("Stack"));UUID.fromString(p.id);UUID.fromString(p.origin);UUID.fromString(p.owner);if(p.stack.getByte("Count")<=0||p.stack.getString("id").isEmpty())throw new IllegalArgumentException("Invalid escrow stack");return p;}
    }
    public static final class Operation {
        public String id="",actor="",kind="",deal="";public Parcel parcel;
        CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("Id",id);t.putString("Actor",actor);t.putString("Kind",kind);t.putString("Deal",deal);t.put("Parcel",parcel.save());return t;}
        static Operation load(CompoundTag t){Operation p=new Operation();p.id=t.getString("Id");p.actor=t.getString("Actor");p.kind=t.getString("Kind");p.deal=t.getString("Deal");p.parcel=Parcel.load(t.getCompound("Parcel"));UUID.fromString(p.id);UUID.fromString(p.actor);return p;}
    }
}

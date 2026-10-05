package com.mikasrevs.newphone.marketplace.data;

import com.google.gson.Gson;
import com.mikasrevs.newphone.integration.currency.PhoneCurrencyIntegration;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** World-owned records, persisted with the same SavedData architecture as the phone. */
public final class MarketplaceSavedData extends SavedData {
    public static final String NAME = "newphone_marketplace";
    private static final Gson JSON = new Gson();
    private final Map<String, MarketplaceListing> listings = new LinkedHashMap<>();
    private final Map<String, Set<String>> favorites = new HashMap<>();
    private final Map<String, Set<String>> conversations = new HashMap<>();
    private final Map<String, List<MarketplaceState.Notice>> notices = new HashMap<>();
    private final Map<String, Image> images = new LinkedHashMap<>();
    private long sequence, revision;
    private final Map<String, Long> viewed = new LinkedHashMap<>();
    private CompoundTag recoveryBackup;

    public static MarketplaceSavedData get(MinecraftServer server) {
        return com.mikasrevs.newphone.server.data.PhoneSavedDataRecovery.get(server, NAME,
                MarketplaceSavedData::load, MarketplaceSavedData::new);
    }
    public static MarketplaceSavedData load(CompoundTag tag) {
        MarketplaceSavedData data = new MarketplaceSavedData();
        if (tag.getBoolean("UnreadableArchive")) data.preserve(tag);
        if (tag.contains("RecoveryBackup", Tag.TAG_COMPOUND)) data.recoveryBackup = tag.getCompound("RecoveryBackup").copy();
        data.sequence = tag.getLong("Sequence"); data.revision = tag.getLong("Revision");
        ListTag list = tag.getList("Listings", Tag.TAG_STRING);
        for (Tag entry : list) {
            try {
                MarketplaceListing l = JSON.fromJson(entry.getAsString(), MarketplaceListing.class);
                if (l == null || l.id == null || !l.id.matches("MP-[0-9]{1,19}")) throw new IllegalArgumentException("Invalid listing identity");
                long number = Long.parseLong(l.id.substring(3));
                if (number <= 0 || number == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid listing sequence");
                data.sequence = Math.max(data.sequence, number);
                // Fill optional legacy/null fields with their declared defaults. Invalid payment state fails closed.
                MarketplaceListing defaults = new MarketplaceListing();
                boolean invalidStatus = l.status == null;
                for (var field : MarketplaceListing.class.getFields()) {
                    if (field.getType() == String.class && field.get(l) == null) {
                        field.set(l, field.get(defaults)); data.preserve(tag);
                    }
                }
                if (l.images == null) { l.images = new ArrayList<>(); data.preserve(tag); }
                if (l.images.removeIf(Objects::isNull)) data.preserve(tag);
                if (invalidStatus || !Set.of("ACTIVE","UNAVAILABLE","EXPIRED","DELETED","SOLD","REVIEW","PROCESSING").contains(l.status)
                        || l.price < 0 || l.price > MarketplaceRequest.MAX_PRICE || l.ownerUuid.isBlank()) {
                    l.status = "REVIEW"; data.preserve(tag);
                }
                if ("PROCESSING".equals(l.status)) { l.status = "REVIEW"; data.setDirty(); }
                if (data.listings.putIfAbsent(l.id, l) != null) data.preserve(tag);
            } catch (RuntimeException | ReflectiveOperationException failure) {
                data.preserve(tag);
            }
        }
        readSets(tag, "Favorites", data.favorites); readSets(tag, "Conversations", data.conversations);
        ListTag notes = tag.getList("Notices", Tag.TAG_COMPOUND);
        for (Tag value : notes) {
            CompoundTag n = (CompoundTag)value;
            List<MarketplaceState.Notice> entries = new ArrayList<>();
            for (Tag text : n.getList("Entries", Tag.TAG_STRING)) {
                try {
                    var notice = JSON.fromJson(text.getAsString(), MarketplaceState.Notice.class);
                    if (notice == null || notice.id() == null || notice.listingId() == null || notice.actorUuid() == null
                            || notice.title() == null || notice.body() == null) throw new IllegalArgumentException();
                    entries.add(notice);
                } catch (RuntimeException failure) { data.preserve(tag); }
            }
            data.notices.put(n.getString("Player"), entries);
        }
        for (Tag value : tag.getList("Images", Tag.TAG_COMPOUND)) {
            CompoundTag image = (CompoundTag)value;
            data.images.put(image.getString("Id"), new Image(image.getString("Owner"), image.getLong("Created"), image.getByteArray("Bytes")));
        }
        return data;
    }
    private void preserve(CompoundTag original) {
        if (recoveryBackup == null) {
            recoveryBackup = original.copy(); recoveryBackup.remove("RecoveryBackup");
            com.mojang.logging.LogUtils.getLogger().warn("Recovered damaged Marketplace records; original data retained in RecoveryBackup");
        }
        setDirty();
    }
    @Override public synchronized CompoundTag save(CompoundTag tag) {
        if (recoveryBackup != null) tag.put("RecoveryBackup", recoveryBackup.copy());
        tag.putLong("Sequence", sequence); tag.putLong("Revision", revision);
        ListTag list = new ListTag(); listings.values().forEach(l -> list.add(StringTag.valueOf(JSON.toJson(l)))); tag.put("Listings", list);
        writeSets(tag, "Favorites", favorites); writeSets(tag, "Conversations", conversations);
        ListTag noteList = new ListTag();
        notices.forEach((player, notes) -> {
            CompoundTag n = new CompoundTag(); n.putString("Player", player);
            ListTag entries = new ListTag(); notes.forEach(note -> entries.add(StringTag.valueOf(JSON.toJson(note))));
            n.put("Entries", entries); noteList.add(n);
        }); tag.put("Notices", noteList);
        ListTag photoList = new ListTag();
        images.forEach((id, image) -> {
            CompoundTag n = new CompoundTag(); n.putString("Id", id); n.putString("Owner", image.owner);
            n.putLong("Created", image.time); n.putByteArray("Bytes", image.bytes); photoList.add(n);
        }); tag.put("Images", photoList);
        return tag;
    }
    private static void readSets(CompoundTag tag, String key, Map<String, Set<String>> into) {
        for (Tag value : tag.getList(key, Tag.TAG_COMPOUND)) {
            CompoundTag n = (CompoundTag)value; Set<String> set = new LinkedHashSet<>();
            for (Tag id : n.getList("Values", Tag.TAG_STRING)) set.add(id.getAsString());
            into.put(n.getString("Player"), set);
        }
    }
    private static void writeSets(CompoundTag tag, String key, Map<String, Set<String>> values) {
        ListTag list = new ListTag(); values.forEach((player, set) -> {
            CompoundTag n = new CompoundTag(); n.putString("Player", player);
            ListTag ids = new ListTag(); set.forEach(id -> ids.add(StringTag.valueOf(id))); n.put("Values", ids); list.add(n);
        }); tag.put(key, list);
    }
    private void changed() { revision++; setDirty(); }
    public synchronized long revision() { return revision; }
    public synchronized Collection<MarketplaceListing> all() { expire(); return List.copyOf(listings.values()); }
    public synchronized MarketplaceListing find(String id) { expire(); return listings.get(id); }
    public synchronized boolean isFavorite(String player, String id) { return favorites.getOrDefault(player, Set.of()).contains(id); }
    public synchronized void favorite(String player, String id, boolean enabled) {
        if (!listings.containsKey(id)) throw new IllegalArgumentException("NOT_FOUND");
        Set<String> set = favorites.computeIfAbsent(player, k -> new LinkedHashSet<>());
        if (enabled && set.size() >= 1000 && !set.contains(id)) throw new IllegalArgumentException("FAVORITE_LIMIT");
        if (enabled) set.add(id); else set.remove(id); changed();
    }
    public synchronized MarketplaceListing create(String owner, String ownerName, MarketplaceRequest draft, String name, String priceText) {
        draft.validateDraft();
        if(draft.requestId!=null&&!draft.requestId.isEmpty())for(var previous:listings.values())
            if(previous.ownerUuid.equals(owner)&&previous.createRequestId.equals(draft.requestId))return previous;
        expire();
        if (listings.values().stream().filter(l -> l.status.equals("ACTIVE")).count() >= 10000
                || listings.values().stream().filter(l -> l.ownerUuid.equals(owner) && l.status.equals("ACTIVE")).count() >= 50)
            throw new IllegalArgumentException("LISTING_LIMIT");
        validateImages(owner, draft.images);
        if (sequence == Long.MAX_VALUE) throw new IllegalArgumentException("LISTING_LIMIT");
        MarketplaceListing l = new MarketplaceListing();
        l.id = String.format(Locale.ROOT,"MP-%06d", ++sequence); l.ownerUuid = owner; l.ownerName = ownerName;
        l.createdAt = System.currentTimeMillis(); l.expiresAt = l.createdAt + 30L*24*60*60*1000;
        l.createRequestId=draft.requestId;assign(l,draft,name,priceText); listings.put(l.id,l); changed(); return l;
    }
    public synchronized MarketplaceListing edit(String owner, MarketplaceRequest draft, String name, String priceText) {
        draft.validateDraft(); MarketplaceListing l = owned(owner,draft.id);
        if (!Set.of("ACTIVE","UNAVAILABLE","EXPIRED").contains(l.status)) throw new IllegalArgumentException("NOT_EDITABLE");
        if (l.revision != draft.expectedRevision) throw new IllegalArgumentException("PRICE_CHANGED");
        validateImages(owner,draft.images); assign(l,draft,name,priceText); l.revision++;
        changed(); pruneImages(); return l;
    }
    private static void assign(MarketplaceListing l, MarketplaceRequest d, String name, String text) {
        l.title=d.title.trim(); l.description=d.description.trim(); l.location=d.location.trim(); l.category=d.category;
        l.price=MarketplaceRequest.amount(d.price,false); l.currencyId=d.currencyId; l.currencyName=name; l.priceText=text;
        l.directPurchase=false; l.images=new ArrayList<>(new LinkedHashSet<>(d.images)); l.updatedAt=System.currentTimeMillis();
    }
    private MarketplaceListing owned(String owner,String id) {
        MarketplaceListing l=listings.get(id); if(l==null)throw new IllegalArgumentException("NOT_FOUND");
        if(!l.ownerUuid.equals(owner))throw new IllegalArgumentException("NOT_OWNER"); return l;
    }
    public synchronized void changeStatus(String owner,String id,boolean delete) {
        MarketplaceListing l=owned(owner,id);
        if(!Set.of("ACTIVE","EXPIRED","UNAVAILABLE").contains(l.status))throw new IllegalArgumentException("NOT_EDITABLE");
        l.status=delete?"DELETED":"UNAVAILABLE"; l.revision++; l.updatedAt=System.currentTimeMillis();
        if(delete) {l.images.clear();favorites.values().forEach(f -> f.remove(id));}
        changed(); pruneImages();
    }
    public synchronized void relist(String owner,String id) {
        expire();
        MarketplaceListing l=owned(owner,id);
        if(!Set.of("EXPIRED","UNAVAILABLE").contains(l.status))throw new IllegalArgumentException("NOT_EDITABLE");
        if(listings.values().stream().filter(row->row.status.equals("ACTIVE")).count()>=10000
                ||listings.values().stream().filter(row->row.ownerUuid.equals(owner)&&row.status.equals("ACTIVE")).count()>=50)throw new IllegalArgumentException("LISTING_LIMIT");
        l.status="ACTIVE";l.expiresAt=System.currentTimeMillis()+30L*24*60*60*1000;l.updatedAt=System.currentTimeMillis();l.revision++;changed();
    }
    /** Operator attestation after checking LC history; this never moves currency. */
    public synchronized void resolveReview(String id,boolean paid,String operator) {
        MarketplaceListing l=listings.get(id);
        if(l==null||!l.status.equals("REVIEW"))throw new IllegalArgumentException("Listing is not awaiting payment review");
        l.reviewedBy=operator;l.reviewedAt=System.currentTimeMillis();l.reviewResolution=paid?"CONFIRMED_PAID":"CONFIRMED_UNPAID";
        if(paid){
            l.status="SOLD";
            notify(l.buyerUuid,l.id,l.ownerUuid,"Marketplace payment confirmed",l.title+" - "+l.priceText);
            notify(l.ownerUuid,l.id,l.buyerUuid,"Marketplace sale confirmed",l.title+" - "+l.priceText);
        }else{
            notify(l.buyerUuid,l.id,l.ownerUuid,"Marketplace payment review complete",l.title+" - no completed payment; listing is unavailable.");
            l.status="UNAVAILABLE";l.buyerUuid="";l.buyerName="";l.purchaseRequestId="";l.soldAt=0;
        }
        l.revision++;l.updatedAt=System.currentTimeMillis();changed();
    }
    private void expire() {
        long now=System.currentTimeMillis();
        for(MarketplaceListing l:listings.values())if(l.status.equals("ACTIVE")&&l.expiresAt<=now){l.status="EXPIRED";l.revision++;changed();}
    }
    public synchronized void view(String viewer,String id) {
        MarketplaceListing l=listings.get(id); if(l==null||l.ownerUuid.equals(viewer))return;
        String key=viewer+":"+id;long now=System.currentTimeMillis();
        if(now-viewed.getOrDefault(key,0L)<3_600_000L)return;
        viewed.put(key,now);while(viewed.size()>8192)viewed.remove(viewed.keySet().iterator().next());
        if(l.views<Long.MAX_VALUE)l.views++;changed();
    }
    public synchronized void putImage(String owner,String id,byte[] bytes) {
        pruneImages();
        Image old=images.get(id);
        if(old!=null){if(!old.owner.equals(owner)||!Arrays.equals(old.bytes,bytes))throw new IllegalArgumentException("INVALID_PHOTO");return;}
        long count=images.values().stream().filter(i->i.owner.equals(owner)).count();
        if (count >= 220) {
            // Receipts remain immutable; only the oldest completed sales' photo attachments are retired.
            Set<String> protectedPhotos = new HashSet<>();
            listings.values().stream().filter(l -> !l.status.equals("SOLD") && !l.status.equals("DELETED"))
                    .forEach(l -> protectedPhotos.addAll(l.images));
            var completed = listings.values().stream().filter(l -> l.ownerUuid.equals(owner) && l.status.equals("SOLD"))
                    .sorted(Comparator.comparingLong(l -> l.soldAt)).toList();
            for (var sale : completed) {
                for (String photo : new ArrayList<>(sale.images)) {
                    Image oldImage = images.get(photo);
                    if (oldImage == null || !oldImage.owner.equals(owner) || protectedPhotos.contains(photo)) continue;
                    images.remove(photo);
                    for (var row : listings.values()) row.images.remove(photo);
                    changed();
                    if (--count < 220) break;
                }
                if (count < 220) break;
            }
        }
        if(count>=220)throw new IllegalArgumentException("PHOTO_LIMIT");
        images.put(id,new Image(owner,System.currentTimeMillis(),bytes.clone()));changed();
    }
    private void validateImages(String owner,List<String> ids) {
        for(String id:ids){Image image=images.get(id);if(image==null||!image.owner.equals(owner))throw new IllegalArgumentException("INVALID_PHOTO");}
    }
    public synchronized byte[] imageFor(String viewer,String id,java.util.function.BiPredicate<String,String> blocked) {
        Image image=images.get(id);if(image==null)return null;
        if (!image.owner.equals(viewer) && blocked.test(viewer,image.owner)) return null;
        boolean allowed=image.owner.equals(viewer)||listings.values().stream().anyMatch(l->!l.status.equals("DELETED")&&l.images.contains(id)&&!blocked.test(viewer,l.ownerUuid));
        return allowed?image.bytes.clone():null;
    }
    private void pruneImages() {
        Set<String> used=new HashSet<>();listings.values().forEach(l->used.addAll(l.images));
        long cutoff=System.currentTimeMillis()-86_400_000L;
        if(images.entrySet().removeIf(e->!used.contains(e.getKey())&&e.getValue().time<cutoff))changed();
    }
    public synchronized void allowConversation(String a,String b) {
        conversations.computeIfAbsent(a,k->new LinkedHashSet<>()).add(b);
        conversations.computeIfAbsent(b,k->new LinkedHashSet<>()).add(a);changed();
    }
    public synchronized boolean canMessage(String a,String b) {return conversations.getOrDefault(a,Set.of()).contains(b);}
    public synchronized List<MarketplaceState.Notice> notifications(String player) {return List.copyOf(notices.getOrDefault(player,List.of()));}
    public synchronized void readNotifications(String player) {
        List<MarketplaceState.Notice> rows=notices.get(player);if(rows==null)return;
        rows.replaceAll(n->new MarketplaceState.Notice(n.id(),n.listingId(),n.actorUuid(),n.title(),n.body(),n.time(),true));changed();
    }
    private void notify(String player,String listing,String actor,String title,String body) {
        List<MarketplaceState.Notice> rows=notices.computeIfAbsent(player,k->new ArrayList<>());
        rows.add(0,new MarketplaceState.Notice(UUID.randomUUID().toString(),listing,actor,title,body,System.currentTimeMillis(),false));
        while(rows.size()>100)rows.remove(rows.size()-1);
    }
    public synchronized List<MarketplaceState.Receipt> receipts(String player) {
        return listings.values().stream().filter(l->!l.buyerUuid.isEmpty()&&(l.ownerUuid.equals(player)||l.buyerUuid.equals(player)))
            .sorted(Comparator.comparingLong((MarketplaceListing l)->l.soldAt).reversed())
            .map(l->new MarketplaceState.Receipt(l.id,l.title,l.buyerUuid,l.buyerName,l.ownerUuid,l.ownerName,l.currencyId,l.price,l.priceText,l.soldAt,l.status)).toList();
    }
    /** Kept for legacy callers; Direct Sell no longer performs any payment. */
    public synchronized String purchase(String buyer,String buyerName,MarketplaceRequest request,
            Supplier<PhoneCurrencyIntegration.TransferResult> transfer, DurableWrite persist) throws IOException {
        return "DIRECT_SELL_REMOVED";
    }
    /** Fail closed before touching money if the reservation cannot be made durable. */
    public synchronized void flush(MinecraftServer server) throws IOException {
        Path dir=server.getWorldPath(LevelResource.ROOT).resolve("data");Files.createDirectories(dir);
        Path temp=dir.resolve(NAME+".pending");Path target=dir.resolve(NAME+".dat");
        CompoundTag root=new CompoundTag();root.put("data",save(new CompoundTag()));
        root.putInt("DataVersion",SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        NbtIo.writeCompressed(root,temp.toFile());
        try(FileChannel file=FileChannel.open(temp,StandardOpenOption.WRITE)){file.force(true);}
        try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        setDirty(false);
    }
    @FunctionalInterface public interface DurableWrite {void write() throws IOException;}
    private record Image(String owner,long time,byte[] bytes) { }
}

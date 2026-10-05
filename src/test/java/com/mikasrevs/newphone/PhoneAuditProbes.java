package com.mikasrevs.newphone;

import com.google.gson.Gson;
import com.mikasrevs.newphone.client.calendar.PhoneCalendarReminders;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneBlockSnakeScreen;
import com.mikasrevs.newphone.marketplace.data.*;
import com.mikasrevs.newphone.network.PhoneRequestLimits;
import com.mikasrevs.newphone.network.PhoneStateData;
import com.mikasrevs.newphone.server.PhonePhotoCleanup;
import com.mikasrevs.newphone.server.data.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Regression fixtures for the nine audit findings. Never opens a world or changes player saves. */
public final class PhoneAuditProbes {
    private static final String OWNER = UUID.randomUUID().toString();
    private static int passed;
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        passed = 0;
        deletedListingQuota();
        activeListingQuota();
        soldPhotoQuota();
        malformedMarketplaceRecord();
        malformedSocialRecord();
        requestBudgets();
        metadataAndHistory();
        photoCleanup();
        blockedMarketplacePhotos();
        snakeRules();
        Path temp = Files.createTempDirectory("phone-audit-regression-");
        try { archiveBackup(temp); calendarReminders(temp.resolve("calendar")); }
        finally {
            // Only remove this test's freshly allocated temporary directory.
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("Phone audit regression checks passed: " + passed + " assertions across all nine findings.");
    }
    private static MarketplaceRequest draft() {
        var r = new MarketplaceRequest();
        r.title = "Audit fixture"; r.category = "ITEMS"; r.currencyId = "test:coins";
        r.requestId = UUID.randomUUID().toString(); return r;
    }
    private static void deletedListingQuota() {
        var root = new CompoundTag(); var rows = new ListTag(); var gson = new Gson();
        for (int i = 1; i <= 10_000; i++) {
            var l = new MarketplaceListing(); l.id = "MP-" + i; l.ownerUuid = OWNER; l.status = "DELETED";
            rows.add(StringTag.valueOf(gson.toJson(l)));
        }
        root.put("Listings", rows);
        var data = MarketplaceSavedData.load(root);
        var created = data.create(OWNER, "Audit", draft(), "Coins", "0 Coins");
        require(created.id.equals("MP-010001"), "deleted listings still exhaust the global quota");
        require(data.all().size() == 10_001, "deleted records lost instead of being excluded from quota");
    }
    private static void activeListingQuota() {
        var data = new MarketplaceSavedData();
        for (int i = 0; i < 50; i++) data.create(OWNER, "Audit", draft(), "Coins", "0 Coins");
        rejected(() -> data.create(OWNER,"Audit",draft(),"Coins","0 Coins"), "LISTING_LIMIT");
        data.changeStatus(OWNER,"MP-000001",false);
        data.create(OWNER,"Audit",draft(),"Coins","0 Coins");
        rejected(() -> data.relist(OWNER,"MP-000001"), "LISTING_LIMIT");
        data.changeStatus(OWNER,"MP-000002",true);
        data.relist(OWNER,"MP-000001");
        require(data.find("MP-000001").status.equals("ACTIVE"), "relisting did not reuse released quota");
    }
    private static void soldPhotoQuota() {
        var data = new MarketplaceSavedData();
        for (int i = 0; i < 55; i++) {
            var r = draft();
            for (int j = 0; j < 4; j++) {
                String id = "market-" + UUID.randomUUID();
                data.putImage(OWNER,id,new byte[]{1}); r.images.add(id);
            }
            var sale = data.create(OWNER,"Audit",r,"Coins","0 Coins");
            sale.status="SOLD"; sale.buyerUuid="buyer"; sale.price=7; sale.soldAt=i;
        }
        String next = "market-" + UUID.randomUUID();
        data.putImage(OWNER,next,new byte[]{2});
        require(data.imageFor(OWNER,next,(a,b)->false)[0] == 2, "sold images permanently exhaust quota");
        require(data.receipts(OWNER).size()==55 && data.receipts(OWNER).stream().allMatch(r->r.amount()==7), "sale receipts changed");
        rejected(()->data.changeStatus(OWNER,"MP-000001",true),"NOT_EDITABLE");
        var restored=MarketplaceSavedData.load(data.save(new CompoundTag()));
        require(restored.receipts(OWNER).size()==55, "receipts did not survive reload");

        // A photo shared with an active listing must not be retired as part of an old sale.
        var protectedData = new MarketplaceSavedData(); var first=draft();
        for(int i=0;i<4;i++){String id="market-"+UUID.randomUUID();protectedData.putImage(OWNER,id,new byte[]{1});first.images.add(id);}
        var sale=protectedData.create(OWNER,"Audit",first,"Coins","0 Coins"); sale.status="SOLD";
        var active=draft();active.images.addAll(first.images);protectedData.create(OWNER,"Audit",active,"Coins","0 Coins");
        for(int i=4;i<220;i++)protectedData.putImage(OWNER,"market-pending-"+i,new byte[]{1});
        rejected(()->protectedData.putImage(OWNER,"market-new",new byte[]{1}),"PHOTO_LIMIT");
        require(protectedData.imageFor(OWNER,first.images.get(0),(a,b)->false)!=null,"active listing lost shared photo");
    }
    private static void malformedMarketplaceRecord() {
        var root=new CompoundTag();var rows=new ListTag();
        var healthy=new MarketplaceListing(); healthy.id="MP-40";healthy.ownerUuid=OWNER;healthy.status="SOLD";healthy.price=25;
        rows.add(StringTag.valueOf(new Gson().toJson(healthy)));
        for(String bad:List.of("{\"id\":null}","null","broken json","{\"id\":\"MP-9999999999999999999\"}"))rows.add(StringTag.valueOf(bad));
        rows.add(StringTag.valueOf("{\"id\":\"MP-41\",\"status\":null,\"images\":null}"));
        root.put("Listings",rows);
        var data=MarketplaceSavedData.load(root);
        require(data.find("MP-40").price==25 && data.find("MP-40").status.equals("SOLD"),"healthy sale lost on partial corruption");
        require(data.find("MP-41").status.equals("REVIEW"),"damaged payment state reopened for purchase");
        var saved=data.save(new CompoundTag());
        require(saved.getCompound("RecoveryBackup").equals(root),"original damaged data not preserved");
        var restored=MarketplaceSavedData.load(saved);
        require(restored.find("MP-40")!=null && restored.save(new CompoundTag()).getCompound("RecoveryBackup").equals(root),"recovery copy lost after reload");
        require(restored.create(OWNER,"Audit",draft(),"Coins","0 Coins").id.equals("MP-000042"),"recovered sequence reused an ID");
    }
    private static void malformedSocialRecord() throws Exception {
        var root=new CompoundTag();
        root.putString("Json","{\"gramPosts\":[null,{\"id\":\"healthy\",\"authorUuid\":\""+OWNER+"\",\"caption\":null,\"likedBy\":[null,{},\"liker\"]},{\"createdAt\":{}}],\"profiles\":{\"bad\":null},\"tinderLikes\":{\"bad\":null},\"blockPosts\":[null]}");
        var data=PhoneSocialSavedData.load(root);
        var state=data.toClientState(UUID.fromString(OWNER),100);
        require(state.blockGramPosts.size()==1 && state.blockGramPosts.get(0).id.equals("healthy"),"healthy social row lost or null row survived");
        require(state.blockGramPosts.get(0).caption!=null,"null caption survived normalization");
        var saved=data.save(new CompoundTag());
        require(saved.getCompound("RecoveryBackup").equals(root),"social recovery copy missing");
        var restored=PhoneSocialSavedData.load(saved);
        require(restored.toClientState(UUID.fromString(OWNER),100).blockGramPosts.size()==1,"social reload failed");
        var assets=new PhoneSocialAssetSavedData();assets.put("social-unknown",new byte[]{1});
        var field=PhoneSocialSavedData.class.getDeclaredField("assets");field.setAccessible(true);field.set(restored,assets);
        var cleanup=PhoneSocialSavedData.class.getDeclaredMethod("removeUnreferencedImages");cleanup.setAccessible(true);cleanup.invoke(restored);
        require(assets.getImage("social-unknown")!=null,"recovered archive deleted potentially recoverable social images");
    }
    private static void requestBudgets() {
        var budget=new PhoneRequestLimits.Budget();
        for(int i=0;i<4;i++)require(budget.allow(PhoneRequestLimits.Kind.SYNC,0),"initial sync rejected");
        for(int i=0;i<1000;i++)require(!budget.allow(PhoneRequestLimits.Kind.SYNC,0),"sync flood admitted");
        require(!budget.allow(PhoneRequestLimits.Kind.SYNC,999_999_999L),"sync refilled early");
        require(budget.allow(PhoneRequestLimits.Kind.SYNC,1_000_000_000L),"sync did not refill");
        require(PhoneRequestLimits.kind("SYNC_FULL")==PhoneRequestLimits.kind("SYNC"),"sync aliases bypass lane");
        require(PhoneRequestLimits.kind("GROUP_CREATE")==PhoneRequestLimits.Kind.GROUP,"group action bypasses limit");
        require(PhoneRequestLimits.kind("arbitrary-key")==PhoneRequestLimits.Kind.CONTROL,"unknown action allocates a lane");
        var mixed=new PhoneRequestLimits.Budget();int admitted=0;
        for(int repeat=0;repeat<100;repeat++)for(var kind:PhoneRequestLimits.Kind.values())if(mixed.allow(kind,0))admitted++;
        require(admitted<200,"switching actions bypassed aggregate budget");
        var media=new PhoneRequestLimits.Budget();
        for(int tick=0;tick<2400;tick++){
            long now=tick*25_000_000L;
            require(media.allow(PhoneRequestLimits.Kind.VOICE,now),"normal 40 Hz voice rejected");
            if(tick%2==0)require(media.allow(PhoneRequestLimits.Kind.VIDEO,now),"normal 20 Hz video rejected");
            if(tick%40==0)require(media.allow(PhoneRequestLimits.Kind.CONTROL,now),"normal call control rejected");
        }
    }
    private static void metadataAndHistory() {
        var data=new PhoneCommunicationSavedData();var snapshot=data.copySnapshot();
        String peer=UUID.randomUUID().toString();
        for(int i=0;i<300;i++){var m=new PhoneCommunicationSavedData.StoredMessage();m.id="m-"+i;m.fromUuid=OWNER;m.toUuid=peer;m.body="hello";m.timestamp=i;snapshot.messages.add(m);}
        data.replace(snapshot);data.rememberName(OWNER,"Audit");
        var metadata=data.copyMetadata();
        require(metadata.messages.isEmpty() && metadata.knownNames.get(OWNER).equals("Audit"),"metadata sync still copies full message archive");
        metadata.knownNames.clear();require(data.copyMetadata().knownNames.containsKey(OWNER),"metadata exposed mutable saved state");
        var expected=new PhoneStateData();PhoneHistory.recent(snapshot.messages,OWNER,expected);
        var actual=new PhoneStateData();data.recentFor(OWNER,actual);
        require(new Gson().toJson(actual.messages).equals(new Gson().toJson(expected.messages)),"direct recent-message read changed visible history");
        require(data.historyPage(OWNER,peer,"MESSAGE","").messages.size()==50,"direct history page is empty");
        require(new Gson().toJson(data.historyPage(OWNER,peer,"MESSAGE",""))
                .equals(new Gson().toJson(PhoneHistory.page(snapshot.messages,OWNER,peer,"MESSAGE",""))),"direct history page changed contents");
        require(data.copySnapshot().messages.size()==300,"metadata path overwrote history");
    }
    private static void photoCleanup() {
        var photos=new PhonePhotoSavedData();for(String id:List.of("shared","orphan","avatar-old","avatar-new","legacy"))photos.put(id,new byte[]{1});
        var data=new PhoneCommunicationSavedData();var s=data.copySnapshot();
        for(int i=0;i<2;i++){var m=new PhoneCommunicationSavedData.StoredMessage();m.type="PHOTO";m.body="shared";s.messages.add(m);}
        var group=new PhoneGroups.Group();group.photoId="avatar-new";group.members.put(OWNER,0L);s.groups.put("group",group);
        s.photosBase64.put("legacy","legacy-bytes");data.replace(s);
        PhonePhotoCleanup.prune(data,photos);
        require(photos.contains("shared")&&photos.contains("avatar-new")&&photos.contains("legacy"),"live photo reference deleted");
        require(!photos.contains("orphan")&&!photos.contains("avatar-old"),"orphan message photo or replaced avatar retained");
        s.messages.remove(0);data.replace(s);PhonePhotoCleanup.prune(data,photos);
        require(photos.contains("shared"),"one deletion removed another message's shared photo");
        s.messages.clear();data.replace(s);PhonePhotoCleanup.prune(data,photos);
        require(!photos.contains("shared"),"last reference deletion did not release photo");
        for(String json:List.of("broken","{\"messages\":[null]}","{\"groups\":null}","{\"photosBase64\":null}")){
            var tag=new CompoundTag();tag.putString("Json",json);
            var damaged=PhoneCommunicationSavedData.load(tag);photos.put("recoverable",new byte[]{1});
            PhonePhotoCleanup.prune(damaged,photos);
            require(photos.contains("recoverable"),"corrupt archive permitted destructive photo cleanup");
            PhonePhotoCleanup.prune(PhoneCommunicationSavedData.load(damaged.save(new CompoundTag())),photos);
            require(photos.contains("recoverable"),"cleanup safety flag lost after reload");
        }
    }
    private static void blockedMarketplacePhotos() {
        var data=new MarketplaceSavedData();String photo="market-"+UUID.randomUUID();
        data.putImage(OWNER,photo,new byte[]{9});var r=draft();r.images.add(photo);data.create(OWNER,"Audit",r,"Coins","0 Coins");
        String viewer=UUID.randomUUID().toString();
        require(data.imageFor(viewer,photo,(a,b)->false)!=null,"visible listing image denied");
        require(data.imageFor(viewer,photo,(a,b)->a.equals(viewer)&&b.equals(OWNER))==null,"viewer block bypassed");
        var communication=new PhoneCommunicationSavedData();var snapshot=communication.copySnapshot();
        snapshot.blocked.put(OWNER,List.of(viewer));communication.replace(snapshot);
        require(data.imageFor(viewer,photo,communication::eitherBlocks)==null,"seller block bypassed");
        require(data.imageFor(OWNER,photo,communication::eitherBlocks)!=null,"owner lost own image");
    }
    @SuppressWarnings("unchecked")
    private static void snakeRules() throws Exception {
        var screen=new ZPhoneBlockSnakeScreen();
        Class<?> cell=Class.forName(ZPhoneBlockSnakeScreen.class.getName()+"$CellPos");
        var ctor=cell.getDeclaredConstructor(int.class,int.class);ctor.setAccessible(true);
        Deque<Object> snake=(Deque<Object>)field("snake").get(screen);
        snake.clear();for(int[] p:new int[][]{{1,1},{1,2},{2,2},{2,1}})snake.add(ctor.newInstance(p[0],p[1]));
        field("food").set(screen,ctor.newInstance(10,10));
        var step=ZPhoneBlockSnakeScreen.class.getDeclaredMethod("step");step.setAccessible(true);step.invoke(screen);
        require(!field("gameOver").getBoolean(screen)&&snake.size()==4,"vacating-tail move killed snake or changed length");
        snake.clear();for(int[] p:new int[][]{{1,1},{2,1},{2,2},{1,2}})snake.add(ctor.newInstance(p[0],p[1]));
        step.invoke(screen);require(field("gameOver").getBoolean(screen),"true body collision ignored");
        var place=ZPhoneBlockSnakeScreen.class.getDeclaredMethod("placeFood");place.setAccessible(true);
        snake.clear();for(int y=0;y<18;y++)for(int x=0;x<13;x++)if(x!=12||y!=17)snake.add(ctor.newInstance(x,y));
        for(int i=0;i<20;i++){place.invoke(screen);require(field("food").get(screen).equals(ctor.newInstance(12,17)),"food placed on occupied cell");}
        snake.add(ctor.newInstance(12,17));place.invoke(screen);
        require(field("food").get(screen)==null&&field("won").getBoolean(screen),"full board did not finish as win");
    }
    private static Field field(String name) throws Exception {var f=ZPhoneBlockSnakeScreen.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void archiveBackup(Path temp) throws Exception {
        Path archive=temp.resolve("broken.dat");byte[] original={3,7,1,9};Files.write(archive,original);
        PhoneSavedDataRecovery.backupUnreadable(archive);
        List<Path> backups;try(var paths=Files.list(temp)){backups=paths.filter(p->p.getFileName().toString().startsWith("broken.dat.recovery-")).toList();}
        require(backups.size()==1&&Arrays.equals(Files.readAllBytes(backups.get(0)),original),"unreadable archive not preserved byte for byte");
        require(Arrays.equals(Files.readAllBytes(archive),original),"backup modified original archive");
    }
    private static void calendarReminders(Path temp) throws Exception {
        Files.createDirectories(temp);Files.writeString(temp.resolve("calendar.properties"),"days=2,4,bad,-1\n");
        var first=new PhoneCalendarReminders(temp,"world-a/player-a");
        require(first.days().equals(Set.of(2L,4L)),"legacy calendar migration lost dates");
        require(new PhoneCalendarReminders(temp,"world-b/player-a").days().isEmpty(),"reminders leaked to another world");
        require(new PhoneCalendarReminders(temp,"world-a/player-b").days().isEmpty(),"reminders leaked to another player");
        require(first.due(1).isEmpty()&&first.due(2).equals(List.of(2L)),"reminder fired on wrong Minecraft day");
        first.acknowledge(first.due(2));first=new PhoneCalendarReminders(temp,"world-a/player-a");
        require(first.due(2).isEmpty(),"notification repeated after reload");
        require(first.due(10).equals(List.of(4L)),"missed reminder was never delivered");
        first.acknowledge(first.due(10));require(first.due(2).isEmpty(),"clock rollback repeated notification");
        require(!first.toggle(2)&&first.toggle(2),"reminder toggle state incorrect");
        require(first.due(2).equals(List.of(2L)),"re-enabled reminder stayed acknowledged");
        require(Files.readString(temp.resolve("calendar.properties")).contains("days=2,4,bad,-1"),"legacy dates destroyed during migration");
    }
    private static void rejected(Runnable action,String expected){
        try{action.run();throw new AssertionError("Expected rejection: "+expected);}
        catch(IllegalArgumentException e){require(expected.equals(e.getMessage()),"wrong rejection: "+e.getMessage());}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);passed++;}
}

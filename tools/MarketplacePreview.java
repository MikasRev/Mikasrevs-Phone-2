import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import javax.tools.ToolProvider;

/** Java2D layout/input adapter for the real Marketplace screens; no player saves or payments. */
public final class MarketplacePreview {
    public static void main(String[] args)throws Exception {
        Path root=Path.of("").toAbsolutePath();
        String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        if(new ProcessBuilder(java,root.resolve("tools/AdminPanelPreview.java").toString()).inheritIO().start().waitFor()!=0)
            throw new IllegalStateException("Shared preview adapter failed");
        Path out=root.resolve("build/marketplace-preview"),sources=out.resolve("sources"),classes=out.resolve("classes");
        Files.createDirectories(classes);
        Path adapters=root.resolve("build/admin-panel-preview/sources");
        try(var files=Files.walk(adapters)){
            for(Path source:files.filter(p->p.toString().endsWith(".java")).toList()){
                if(source.endsWith("Gson.java")||source.endsWith("CaptureAdmin.java"))continue;
                Path target=sources.resolve(adapters.relativize(source));Files.createDirectories(target.getParent());Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path gson;
        try(var files=Files.walk(Path.of(System.getProperty("user.home"),".gradle/caches/modules-2/files-2.1/com.google.code.gson/gson"))){
            gson=files.filter(p->p.toString().endsWith(".jar")&&!p.toString().endsWith("-sources.jar")&&!p.toString().endsWith("-javadoc.jar")).findFirst().orElseThrow();
        }
        Map<String,String> stubs=new LinkedHashMap<>();
        stubs.put("com.mikasrevs.newphone.client.device.PhoneDeviceSession", "package com.mikasrevs.newphone.client.device; public final class PhoneDeviceSession {public static boolean isDuo(){return false;}}");
        stubs.put("net.minecraft.client.Minecraft", """
            package net.minecraft.client;
            public class Minecraft {
                public Object font=new Object();
                public final Player player=new Player();
                public final Keyboard keyboardHandler=new Keyboard();
                public Object screen;
                public static class Player { public Profile getGameProfile(){return new Profile();} }
                public static class Profile { public String getName(){return "PlayerOne";} }
                public static class Keyboard { String text="";public String getClipboard(){return text;}public void setClipboard(String s){text=s;} }
            }
            """);
        stubs.put("com.mikasrevs.newphone.client.zphone.PhoneNavigationState", """
            package com.mikasrevs.newphone.client.zphone;
            import net.minecraft.client.Minecraft;
            public class PhoneNavigationState {
                public static void goHome(Object m){}
                public static void backOrHome(Minecraft m){}
                public static void open(Minecraft m,Object from,Object next){m.screen=next;}
                public static void returnTo(Minecraft m,Object next){m.screen=next;}
                public static void replaceCurrent(Minecraft m,Object next){m.screen=next;}
            }
            """);
        stubs.put("com.mikasrevs.newphone.client.data.PhonePhotoStore", """
            package com.mikasrevs.newphone.client.data;
            public class PhonePhotoStore {
                public record Texture(Object location,int width,int height){}
                public static Texture textureFor(Object mc,String id){return id.isBlank()?null:new Texture(id,640,480);}
            }
            """);
        stubs.put("com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer", """
            package com.mikasrevs.newphone.client.gui;
            import net.minecraft.client.gui.GuiGraphics;
            public class PhonePlayerHeadRenderer {public static void draw(GuiGraphics g,String id,int x,int y,int s){g.round(x,y,x+s,y+s,s/4,0xFF544B34);g.text("T",x+s/2,y+s/4,s/2,s-5,0xFFFFD451,true);}}
            """);
        stubs.put("com.mikasrevs.newphone.client.zphone.gui.ZPhoneBankScreen", """
            package com.mikasrevs.newphone.client.zphone.gui;
            import net.minecraft.network.chat.Component;
            public class ZPhoneBankScreen extends AbstractZPhoneScreen {public ZPhoneBankScreen(){super(Component.literal("Bank"));}}
            """);
        stubs.put("com.mikasrevs.newphone.client.zphone.gui.ZPhoneGalleryScreen", """
            package com.mikasrevs.newphone.client.zphone.gui;
            import java.nio.file.Path;import java.util.function.Consumer;import net.minecraft.network.chat.Component;
            public class ZPhoneGalleryScreen extends AbstractZPhoneScreen {public ZPhoneGalleryScreen(Consumer<Path> callback){super(Component.literal("Gallery"));}}
            """);
        stubs.put("com.mikasrevs.newphone.integration.currency.PhoneCurrencyIntegration", """
            package com.mikasrevs.newphone.integration.currency;
            import java.util.List;
            public interface PhoneCurrencyIntegration {
                record Snapshot(boolean available,String status,String provider,String accountName,List<Balance> balances){}
                record Balance(String id,String name,String formatted,long coreValue,List<Unit> units){}
                record Unit(String name,long coreValue){}
            }
            """);
        stubs.put("com.mikasrevs.newphone.network.PhoneMessageLimits", """
            package com.mikasrevs.newphone.network;
            public class PhoneMessageLimits {public static final int TEXT_LENGTH=2000;}
            """);
        stubs.put("com.mikasrevs.newphone.marketplace.MarketplaceManager", """
            package com.mikasrevs.newphone.marketplace;
            public class MarketplaceManager {public static void validateQuery(Object request){}}
            """);
        stubs.put("com.mikasrevs.newphone.marketplace.client.MarketplaceClient", """
            package com.mikasrevs.newphone.marketplace.client;
            import com.mikasrevs.newphone.marketplace.data.*;
            import com.mikasrevs.newphone.integration.currency.PhoneCurrencyIntegration;
            import java.util.*;import java.nio.file.Path;
            public class MarketplaceClient {
                public static MarketplaceState state=new MarketplaceState();
                public static Map<String,Result> results=new HashMap<>();public static int sent;
                public static MarketplaceRequest lastRequest;
                public static long receivedAt=1;
                public record Result(String code,String listingId,String photoId){}
                public static MarketplaceState state(){return state;}
                public static String send(MarketplaceRequest r){
                    if(r.requestId.isBlank())r.requestId=UUID.randomUUID().toString();
                    sent++;lastRequest=r;results.remove(r.requestId);return r.requestId;
                }
                public static MarketplaceRequest command(String a,String id){var r=new MarketplaceRequest();r.action=a;r.id=id;return r;}
                public static void detail(String id){}
                public static void favorite(MarketplaceListing l){l.favorite=!l.favorite;}
                public static void contact(MarketplaceListing l){}
                public static void readNotifications(){}
                public static int unread(){return 3;}
                public static String upload(Path p){return "market-"+UUID.randomUUID();}
                public static Result result(String id){return results.get(id);}
                public static String message(String code){return code;}
                public static List<PhoneCurrencyIntegration.Balance> balances(){return List.of(new PhoneCurrencyIntegration.Balance("gold","Gold","1,200 Gold",1200,List.of(new PhoneCurrencyIntegration.Unit("Gold",1))));}
                public static long balance(String c){return 1200;}
                public static boolean bankAvailable(){return true;}
                public static String format(String c,long amount){return amount+" Gold";}
            }
            """);
        stubs.put("com.mikasrevs.newphone.marketplace.client.CaptureMarketplace", """
            package com.mikasrevs.newphone.marketplace.client;
            import com.mikasrevs.newphone.marketplace.data.*;
            import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
            import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
            import net.minecraft.client.gui.GuiGraphics;
            import java.nio.file.Path;import java.util.*;
            public class CaptureMarketplace {
                static MarketplaceListing listing(int index){
                    var l=new MarketplaceListing();l.id="listing-"+index;l.title=index==0?"Modern lakeside house":index==1?"Diamond tool collection":"Vintage offroad vehicle";
                    l.description="A carefully kept listing, ready for a new owner. Message the seller to learn more.";
                    l.ownerUuid=new UUID(0,2).toString();l.ownerName="SkyBuilder";l.category=index==0?"HOUSES":index==1?"ITEMS":"CARS";l.location="Spawn district";
                    l.currencyId="gold";l.price=250;l.priceText="250 Gold";l.images=List.of("market-"+new UUID(0,index+1));l.createdAt=System.currentTimeMillis();l.directPurchase=true;
                    return l;
                }
                static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
                static void set(Object o,String n,Object v)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
                static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
                static void capture(MarketplaceBaseScreen screen,Path out,String name)throws Exception{
                    var g=new GuiGraphics(Math.round(300*AbstractZPhoneScreen.scale),Math.round(610*AbstractZPhoneScreen.scale));
                    screen.render(g,-1,-1,0);g.save(out.resolve(name+".png"));
                }
                public static void run(Path out)throws Exception{
                    AbstractZPhoneScreen.scale=2;
                    MarketplaceClient.state.selfUuid=new UUID(0,1).toString();
                    MarketplaceClient.state.total=24;MarketplaceClient.state.hasMore=true;
                    MarketplaceClient.state.activeCount=5;MarketplaceClient.state.soldCount=12;MarketplaceClient.state.purchaseCount=8;MarketplaceClient.state.favoriteCount=3;
                    for(int i=0;i<12;i++)MarketplaceClient.state.listings.add(listing(i));
                    var market=new MarketplaceScreen();market.init();capture(market,out,"market-tr");
                    capture(new MarketplaceFilterScreen(market,market.query),out,"filters-tr");
                    var itemFilter=new com.mikasrevs.newphone.marketplace.data.MarketplaceRequest();itemFilter.category="ITEMS";itemFilter.currencyId="gold";itemFilter.minPrice="100";itemFilter.sort="LOWEST";
                    var filtered=new MarketplaceFilterScreen(market,itemFilter);capture(filtered,out,"filters-trade-tr");
                    var normalized=(MarketplaceRequest)field(filtered,"filter");require(normalized.currencyId.isEmpty()&&normalized.minPrice.isEmpty()&&normalized.sort.equals("NEWEST"),"bank filter hid barter listings");
                    MarketplaceClient.state.detail=listing(0);
                    capture(new MarketplaceListingDetailsScreen("listing-0"),out,"details-tr");
                    MarketplaceClient.state.detail.images=List.of("market-"+new UUID(0,1),"market-"+new UUID(0,2));
                    capture(new MarketplaceListingDetailsScreen("listing-0"),out,"gallery-tr");
                    MarketplaceClient.state.purchases=List.of(new MarketplaceState.Receipt("listing-0","Modern lakeside house",MarketplaceClient.state.selfUuid,"PlayerOne",
                        new UUID(0,2).toString(),"SkyBuilder","gold",250,"250 Gold",System.currentTimeMillis(),"SOLD"));
                    capture(new MarketplaceProfileScreen(),out,"profile-tr");
                    var create=new MarketplaceCreateListingScreen(null);
                    MarketplaceBaseScreen.Field title=(MarketplaceBaseScreen.Field)field(create,"title");title.editor.insert("Handmade spawn house");
                    capture(create,out,"create-tr");create.mouseScrolled(150*2,350*2,-9);capture(create,out,"create-bottom-tr");
                    int count=MarketplaceClient.sent;
                    create.mouseClicked(150*2,568*2,0);require(MarketplaceClient.sent==count+1,"publish did not send");
                    var submitted=MarketplaceClient.lastRequest;create.mouseClicked(150*2,568*2,0);require(MarketplaceClient.sent==count+1,"double publish sent twice");
                    set(create,"sentAt",0L);create.tick();create.charTyped('x',0);require(title.get().equals("Handmade spawn house"),"timeout retry edited payload");
                    create.mouseClicked(150*2,568*2,0);
                    require(MarketplaceClient.lastRequest==submitted&&MarketplaceClient.sent==count+2,"retry changed request identity or draft");
                    for(float scale:new float[]{.5f,1,1.5f}){
                        AbstractZPhoneScreen.scale=scale;
                        MarketplaceClient.state.detail=listing(0);
                        var detail=new MarketplaceListingDetailsScreen("listing-0");
                        capture(detail,out,"detail-scale-"+scale);
                        detail.mouseClicked(100*scale,573*scale,0);
                        capture(new MarketplaceFilterScreen(market,market.query),out,"filters-scale-"+scale);
                    }
                    AbstractZPhoneScreen.scale=2;PhoneSettingsStore.english=true;
                    capture(new MarketplaceScreen(),out,"market-en");
                    capture(new MarketplaceCreateListingScreen(listing(0)),out,"edit-en");
                    System.out.println("Marketplace UI checks passed: real screen rendering, four scales, clipped form controls, duplicate-submit protection and identical retry draft.");
                }
            }
            """);
        stubs.put("net.minecraft.resources.ResourceLocation","package net.minecraft.resources; public record ResourceLocation(String value) {public String toString(){return value;}}");
        stubs.put("net.minecraft.core.registries.BuiltInRegistries","package net.minecraft.core.registries;public class BuiltInRegistries{public static final Registry ITEM=new Registry();public static class Registry{public String get(net.minecraft.resources.ResourceLocation id){return id.toString();}}}");
        stubs.put("net.minecraft.nbt.TagParser","package net.minecraft.nbt;public class TagParser {public static String parseTag(String tag){return tag;}}");
        stubs.put("net.minecraft.world.item.ItemStack", """
            package net.minecraft.world.item;
            public class ItemStack {public static final ItemStack EMPTY=new ItemStack("minecraft:air",0);public final String id;public final int count;public ItemStack(String id,int count){this.id=id;this.count=count;}public boolean isEmpty(){return count==0;}public void setTag(String tag){} }
            """);
        stubs.put("com.mikasrevs.newphone.marketplace.trade.TradeClient", """
            package com.mikasrevs.newphone.marketplace.trade;
            import java.util.*;
            public class TradeClient {
                public static TradeState state=new TradeState();public static long receivedAt=1;public static int sent;
                public static TradeRequest lastRequest;public static final Map<String,String> results=new HashMap<>();
                public static TradeState state(){return state;}
                public static void sync(int page){}
                public static int unread(){return state.incoming&&state.status.equals("REQUESTED")?1:0;}
                public static TradeRequest command(String action){var r=new TradeRequest();r.action=action;r.id=state.id;r.revision=state.revision;return r;}
                public static String send(TradeRequest r){if(r.requestId.isEmpty())r.requestId=UUID.randomUUID().toString();sent++;lastRequest=r;return r.requestId;}
                public static String result(String id){return results.remove(id);}
                public static String message(String code){return code;}
                public static void invite(com.mikasrevs.newphone.marketplace.data.MarketplaceListing listing){}
            }
            """);
        stubs.put("com.mikasrevs.newphone.marketplace.trade.CaptureTrade", """
            package com.mikasrevs.newphone.marketplace.trade;
            import java.nio.file.Path;import java.util.*;
            import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
            import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
            import net.minecraft.client.gui.GuiGraphics;
            public class CaptureTrade {
                static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
                static Object get(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
                static void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
                static TradeState.Row row(String item,int slot,int count){var r=new TradeState.Row();r.id=UUID.randomUUID().toString();r.item="minecraft:"+item;r.name=item;r.slot=slot;r.count=count;r.fingerprint="a".repeat(64);return r;}
                static TradeState fixture(){var s=new TradeState();s.id=UUID.randomUUID().toString();s.self=UUID.randomUUID().toString();s.other=UUID.randomUUID().toString();s.otherName="SkyBuilder";s.title="Diamond tool collection";s.status="ACTIVE";s.revision=10;s.deadline=System.currentTimeMillis()+600000;s.otherReady=true;
                    String[] items={"diamond","diamond_sword","emerald","gold_ingot","iron_ingot","ender_pearl","oak_log","enchanted_book"};
                    for(int i=0;i<8;i++){s.mine.add(row(items[i],-1,i+1));s.theirs.add(row(items[7-i],-1,i+2));s.mail.add(row(items[i],-1,i+1));}
                    for(int i=0;i<36;i++)s.inventory.add(row(items[i%8],i,64));s.mailCount=8;return s;}
                static GuiGraphics capture(TradeScreen screen,Path out,String name)throws Exception{screen.width=Math.round(343*AbstractZPhoneScreen.scale)+256;screen.height=Math.round(610*AbstractZPhoneScreen.scale);var g=new GuiGraphics(screen.width,screen.height);screen.render(g,-1,-1,0);g.save(out.resolve(name+".png"));return g;}
                public static void run(Path out)throws Exception{
                    AbstractZPhoneScreen.scale=2;PhoneSettingsStore.english=false;TradeClient.state=fixture();
                    var screen=new TradeScreen();capture(screen,out,"trade-active-tr");
                    TradeClient.state.revision=11;screen.mouseClicked(150*2,547*2,0);
                    check(TradeClient.sent==0&&Boolean.TRUE.equals(get(screen,"confirming")),"Approve skipped the confirmation question");
                    var stale=capture(screen,out,"trade-confirm-stale");screen.mouseClicked(150*2,433*2,0);check(TradeClient.sent==0,"changed offer bypassed confirmation");
                    screen.keyPressed(256,0,0);TradeClient.state=fixture();capture(screen,out,"trade-before-confirm");screen.mouseClicked(150*2,547*2,0);
                    var confirm=capture(screen,out,"trade-confirm-tr");check(confirm.drawnTexts.stream().noneMatch(t->t.contains("Senin teklifin")),"confirmation rendered offer labels underneath");
                    TradeClient.state.revision=11;screen.mouseClicked(150*2,433*2,0);
                    check(TradeClient.lastRequest.revision==10,"click approved an offer that was never rendered");int sent=TradeClient.sent;
                    screen.mouseClicked(150*2,433*2,0);check(TradeClient.sent==sent,"double click duplicated confirmation");
                    set(screen,"sentAt",0L);screen.tick();TradeClient.results.put(TradeClient.lastRequest.requestId,"TRADE_CHANGED");screen.tick();check(get(screen,"pending")==null,"late reply left screen locked");
                    TradeClient.state=fixture();screen=new TradeScreen();capture(screen,out,"trade-before-selection");screen.mouseClicked(44*2,441*2,0);
                    var modal=capture(screen,out,"trade-select-tr");check(modal.drawnTexts.stream().noneMatch(t->t.contains("Senin teklifin")),"modal rendered trade labels underneath");
                    TradeClient.state.revision=12;screen.mouseClicked(150*2,399*2,0);check(TradeClient.lastRequest.revision==10,"selected item approved a changed trade");
                    TradeClient.state=fixture();TradeClient.state.mine.clear();TradeClient.state.theirs.clear();screen=new TradeScreen();capture(screen,out,"trade-empty");screen.mouseClicked(150*2,547*2,0);check(Boolean.TRUE.equals(get(screen,"confirming")),"empty trade could not approve");capture(screen,out,"trade-confirm-empty");
                    TradeClient.state=fixture();TradeClient.state.inventory.get(0).protectedPhone=true;screen=new TradeScreen();capture(screen,out,"trade-protected-phone");screen.mouseClicked(44*2,441*2,0);check(get(screen,"selected")==null,"used phone selected");
                    var chatBounds=TradeChatLayout.place(screen.width,screen.height,-86,686);screen.mouseClicked(chatBounds.x()+20,chatBounds.y()+chatBounds.height()-40,0);screen.charTyped('H',0);screen.charTyped('i',0);screen.keyPressed(257,0,0);
                    check(TradeClient.lastRequest.action.equals("CHAT")&&TradeClient.lastRequest.text.equals("Hi"),"side chat typing/Enter failed");
                    screen.charTyped('x',0);screen.keyPressed(257,0,0);check(TradeClient.lastRequest.text.equals("Hi"),"pending message draft changed or duplicated");
                    TradeClient.results.put(TradeClient.lastRequest.requestId,"TRADE_CHAT_SENT");screen.tick();
                    TradeClient.state.chat.add(new TradeState.Message(UUID.randomUUID().toString(),TradeClient.state.self,"Hi",1));TradeClient.state.chat.add(new TradeState.Message(UUID.randomUUID().toString(),TradeClient.state.other,"Hello, what would you like in exchange?",2));capture(screen,out,"trade-with-chat");
                    TradeClient.state.status="COMPLETE";capture(screen,out,"trade-chat-closed");int before=TradeClient.sent;screen.charTyped('x',0);screen.keyPressed(257,0,0);check(TradeClient.sent==before,"chat sent after trade closed");
                    for(float scale:new float[]{.5f,1,1.5f,2}){AbstractZPhoneScreen.scale=scale;TradeClient.state=fixture();capture(new TradeScreen(),out,"trade-scale-"+scale);capture(new TradeScreen(true),out,"trade-mail-scale-"+scale);}
                    AbstractZPhoneScreen.scale=2;TradeClient.state=fixture();TradeClient.state.status="REQUESTED";TradeClient.state.incoming=true;capture(new TradeScreen(),out,"trade-invite-tr");
                    PhoneSettingsStore.english=true;TradeClient.state=fixture();capture(new TradeScreen(),out,"trade-active-en");
                    System.out.println("Trade UI checks passed: real screen rendering, four scales, confirmation dialog, stale approvals, stale selection, empty trade, phone lock, side chat, pending/closed chat, double click, late reply and modal isolation.");
                }
            }
            """);
        for(var entry:stubs.entrySet()){
            Path path=sources.resolve(entry.getKey().replace('.','/')+".java");Files.createDirectories(path.getParent());Files.writeString(path,entry.getValue());
        }
        replace(sources,"com/mikasrevs/newphone/client/zphone/gui/AbstractZPhoneScreen.java",
            "protected Object font=new Object(),minecraft=new Object();",
            "public int width=1200,height=1300;protected Object font=new Object();protected net.minecraft.client.Minecraft minecraft=new net.minecraft.client.Minecraft();");
        replace(sources,"com/mikasrevs/newphone/client/zphone/gui/AbstractZPhoneScreen.java","public void tick(){}",
            "public void tick(){} protected float phoneWidthFactor(){return .54f;}protected boolean hasControlDown(){return false;}protected boolean hasShiftDown(){return false;}public boolean mouseScrolled(double x,double y,double d){return false;}protected void drawStatusBar(GuiGraphics g,int c,int line){drawStatusBar(g);}");
        replace(sources,"com/mikasrevs/newphone/client/gui/PhoneModernText.java","public class PhoneModernText {",
            "public class PhoneModernText { public static int width(String t,int h){return Math.round(t.length()*h*.6f);} public static void drawColored(GuiGraphics g,String t,int x,int y,int h,int c){g.text(t,x,y,h,Math.max(1,width(t,h)),c,false);}");
        replace(sources,"com/mikasrevs/newphone/client/settings/PhoneSettingsStore.java","public static boolean english;",
            "public static boolean english;public static java.util.Locale locale(){return english?java.util.Locale.ENGLISH:java.util.Locale.forLanguageTag(\"tr\");}");
        replace(sources,"org/lwjgl/glfw/GLFW.java","public class GLFW {",
            "public class GLFW { public static final int GLFW_MOD_CONTROL=2,GLFW_MOD_SHIFT=1,GLFW_KEY_A=65,GLFW_KEY_V=86,GLFW_KEY_C=67,GLFW_KEY_X=88,GLFW_KEY_BACKSPACE=259,GLFW_KEY_DELETE=261,GLFW_KEY_LEFT=263,GLFW_KEY_RIGHT=262,GLFW_KEY_HOME=268,GLFW_KEY_END=269;");
        replace(sources,"net/minecraft/client/gui/GuiGraphics.java","public void flush(){}",
            """
            public void flush(){}
            public void renderTooltip(Object font,net.minecraft.world.item.ItemStack stack,int x,int y){}
            public void renderItem(net.minecraft.world.item.ItemStack stack,int x,int y){
                if(stack.isEmpty())return;
                int color=stack.id.contains("diamond")?0xFF48DDE4:stack.id.contains("emerald")?0xFF57D382:stack.id.contains("gold")?0xFFFFD451:stack.id.contains("pearl")?0xFF208C7B:0xFFAD9BBF;
                g.setColor(new java.awt.Color(color,true));int[] xx={x+8,x+14,x+8,x+2};int[] yy={y+1,y+8,y+15,y+8};g.fillPolygon(xx,yy,4);
            }
            private final java.util.ArrayList<java.awt.Shape> clips=new java.util.ArrayList<>();
            public void enableScissor(int l,int t,int r,int b){clips.add(g.getClip());g.clipRect(l,t,r-l,b-t);}
            public void disableScissor(){g.setClip(clips.remove(clips.size()-1));}
            public void blit(Object location,int x,int y,int w,int h,float u,float v,int sw,int sh,int tw,int th){
                g.setPaint(new java.awt.GradientPaint(x,y,new java.awt.Color(0xFF426465,true),x,y+h,new java.awt.Color(0xFF222E36,true)));g.fillRect(x,y,w,h);
                int[] xs={x+w/8,x+w/2,x+w*7/8};int[] ys={y+h*3/4,y+h/3,y+h*3/4};g.setColor(new java.awt.Color(0xFFB7AD8A,true));g.fillPolygon(xs,ys,3);
                g.setColor(new java.awt.Color(0xFFFFD451,true));g.fillOval(x+w*3/4,y+h/6,Math.max(2,w/9),Math.max(2,w/9));
            }
            """);
        List<String> compile=new ArrayList<>(List.of("-encoding","UTF-8","-cp",gson.toString(),"-d",classes.toString()));
        try(var files=Files.walk(sources)){files.filter(p->p.toString().endsWith(".java")).forEach(p->compile.add(p.toString()));}
        for(String path:List.of(
                "com/mikasrevs/newphone/client/zphone/gui/ZPhoneAdminScreen.java",
                "com/mikasrevs/newphone/client/moderation/PhoneAdminSession.java",
                "com/mikasrevs/newphone/moderation/PhoneModerationState.java",
                "com/mikasrevs/newphone/moderation/PhoneModerationRequest.java",
                "com/mikasrevs/newphone/client/gui/PhoneChatDraft.java",
                "com/mikasrevs/newphone/marketplace/data/MarketplaceListing.java",
                "com/mikasrevs/newphone/marketplace/data/MarketplaceRequest.java",
                "com/mikasrevs/newphone/marketplace/data/MarketplaceState.java"))
            compile.add(root.resolve("src/main/java").resolve(path).toString());
        for(String path:List.of("TradeScreen.java","TradeState.java","TradeRequest.java","TradeChatPanel.java","TradeChatLayout.java"))compile.add(root.resolve("src/main/java/com/mikasrevs/newphone/marketplace/trade").resolve(path).toString());
        try(var files=Files.list(root.resolve("src/main/java/com/mikasrevs/newphone/marketplace/client"))){
            files.filter(p->p.toString().endsWith(".java")&&!p.endsWith("MarketplaceClient.java")).forEach(p->compile.add(p.toString()));
        }
        if(ToolProvider.getSystemJavaCompiler().run(null,null,null,compile.toArray(String[]::new))!=0)throw new IllegalStateException("Marketplace preview compilation failed");
        try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL(),gson.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            loader.loadClass("com.mikasrevs.newphone.marketplace.client.CaptureMarketplace").getMethod("run",Path.class).invoke(null,out);
            loader.loadClass("com.mikasrevs.newphone.marketplace.trade.CaptureTrade").getMethod("run",Path.class).invoke(null,out);
        }
        System.out.println("Marketplace layout previews: "+out);
    }
    private static void replace(Path sources,String path,String old,String value)throws Exception {
        Path target=sources.resolve(path);Files.writeString(target,Files.readString(target).replace(old,value));
    }
}

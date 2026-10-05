package com.mikasrevs.newphone;

import com.mikasrevs.newphone.client.device.PhoneDeviceSession;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.network.PhoneStateData;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.zphone.gui.*;
import com.mikasrevs.newphone.marketplace.client.MarketplaceScreen;
import com.mikasrevs.newphone.marketplace.trade.*;
import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in isolated render, language input and persistence check; never shipped in the release. */
@Mod.EventBusSubscriber(modid="newphone",value=Dist.CLIENT)
public final class ChineseClientPreview {
    private static final boolean ALL=Boolean.getBoolean("newphone.phoneTypographyPreview");
    private static final java.util.List<String> CODES=System.getProperty("newphone.phoneTypographyLanguages","").isBlank()?PhoneSettingsStore.languageCodes():java.util.List.of(System.getProperty("newphone.phoneTypographyLanguages").split(","));
    private static final String[] APPS=ALL?new String[]{"setup","home","settings","marketplace","trade-chat","emergency","lock","messages","models","contacts","notes","music","admin","lumi-chat","minder-chat"}:new String[]{"setup","home","settings","marketplace","trade-chat","emergency","lock","messages","models"};
    private static int phase=-1,ticks;
    private static boolean loading,capture,advance;
    private static long since;
    private static TradeState trade;
    private static PhoneStateData messagePreview;
    private static final String MESSAGE_PEER=UUID.randomUUID().toString();

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if(!Boolean.getBoolean("newphone.phoneLanguagePreview")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        if(phase<0&&mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());
            System.out.println("Phone preview: skipped Minecraft onboarding in the isolated test profile.");return;
        }
        if(phase<0) {
            if(!loading) {
                if(!(mc.screen instanceof TitleScreen)||++ticks<30)return;
                loading=true;mc.options.guiScale().set(Integer.getInteger("newphone.phoneLanguageGuiScale",1));mc.resizeDisplay();
                mc.createWorldOpenFlows().createFreshLevel("chinese-preview-"+System.currentTimeMillis(),
                        new net.minecraft.world.level.LevelSettings("Chinese Phone Preview",net.minecraft.world.level.GameType.CREATIVE,false,
                                net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(1L,false,false),
                        registry->registry.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).createWorldDimensions());return;
            }
            if(mc.player==null||mc.level==null)return;
            ChineseTextClientChecks.run();
            mc.player.getInventory().setItem(0,new ItemStack(NewPhone.Z_PHONE.get()));mc.player.getInventory().selected=0;
            PhoneDeviceSession.select(mc.player.getMainHandItem());ZPhoneClient.authorizeCurrentSession();
            phase=0;show(mc);return;
        }
        if(advance) {
            advance=false;
            if(++phase==(ALL?APPS.length*CODES.size():20)) {System.out.println("Phone native preview passed: language hit regions, persistence, "+phase+" screens.");mc.stop();return;}
            show(mc);
        }
        if(app()==4)TradeClient.receive(new com.google.gson.Gson().toJson(trade));
        if(app()==7||app()>=13)PhoneClientData.setState(messagePreview);
        if(System.currentTimeMillis()-since>850)capture=true;
    }

    private static void show(Minecraft mc) throws Exception {
        String code=code();
        PhoneSettingsStore.setLanguage(code);checkPersistence(mc,code);
        PhoneSettingsStore.completeSetup();ZPhoneClient.authorizeCurrentSession();
        int app=app();
        if(app==0)PhoneSettingsStore.restartSetup();
        if(app==7||app>=13) {
            com.mikasrevs.newphone.client.data.PhoneMessageClient.clear();
            messagePreview=new PhoneStateData();messagePreview.selfUuid=mc.player.getStringUUID();
            messagePreview.selfName=mc.player.getName().getString();
            messagePreview.contacts.add(new PhoneStateData.PlayerEntry(MESSAGE_PEER,"Alex","5551234",true));
            String body=sample(code);
            String type=app==13?"GRAM_TEXT":app==14?"TINDER_TEXT":"TEXT";
            messagePreview.messages.add(new PhoneStateData.MessageEntry("preview-in-"+code,MESSAGE_PEER,"Alex",messagePreview.selfUuid,messagePreview.selfName,type,body,System.currentTimeMillis()-1000,true,"",""));
            messagePreview.messages.add(new PhoneStateData.MessageEntry("preview-out-"+code,messagePreview.selfUuid,messagePreview.selfName,MESSAGE_PEER,"Alex",type,PhoneSettingsStore.text("Merhaba!","Hello!"),System.currentTimeMillis(),true,"",""));
            PhoneClientData.setState(messagePreview);
        }
        mc.setScreen(switch(app) {
            case 0->new ZPhoneSetupScreen();case 1->new ZPhoneHomeScreen();case 2->new ZPhoneSettingsScreen();
            case 3->new MarketplaceScreen();case 4->new TradeScreen();case 5->new ZPhoneEmergencyScreen();
            case 6->new ZPhoneLockScreen();
            case 8->new ZPhoneModelScreen();
            case 9->new ZPhoneContactsScreen();case 10->new ZPhoneNotesScreen();case 11->new ZPhoneMusicScreen();case 12->new ZPhoneAdminScreen();
            case 13->new ZPhoneBlockGramThreadScreen(MESSAGE_PEER,"Alex");case 14->new ZPhoneTinderChatScreen(MESSAGE_PEER,"Alex");
            default->new ZPhoneMessageThreadScreen(MESSAGE_PEER,"Alex").withDraft(code.startsWith("zh")?"你好，這是 MPhone 的中文訊息。":"İstanbul • Türkçe • MPhone 😀");
        });
        if(app==0) {
            var x=AbstractZPhoneScreen.class.getDeclaredMethod("x",float.class);x.setAccessible(true);
            var y=AbstractZPhoneScreen.class.getDeclaredMethod("y",float.class);y.setAccessible(true);
            var codes=PhoneSettingsStore.languageCodes();
            for(int i=0;i<codes.size();i++) {
                mc.screen.mouseClicked((int)x.invoke(mc.screen,90F),(int)y.invoke(mc.screen,188F+i*29+14),0);
                if(!PhoneSettingsStore.language().equals(codes.get(i)))throw new AssertionError("Language row selected wrong code: "+codes.get(i));
            }
            PhoneSettingsStore.setLanguage(code);
        }
        if(app==4) {
            trade=new TradeState();trade.id=UUID.randomUUID().toString();trade.self=mc.player.getStringUUID();
            trade.other=UUID.randomUUID().toString();trade.otherName="Alex";trade.status="ACTIVE";
            trade.chat.add(new TradeState.Message("1",trade.other,sample(code),1));
            trade.chat.add(new TradeState.Message("2",trade.self,PhoneSettingsStore.text("Merhaba!","Hello!"),2));
            TradeClient.receive(new com.google.gson.Gson().toJson(trade));
        }
        since=System.currentTimeMillis();
    }

    private static int app(){return ALL?phase%APPS.length:phase<18?phase%9:phase==18?1:0;}
    private static String code(){return ALL?CODES.get(phase/APPS.length):phase<9?"zh_cn":phase<18?"zh_tw":phase==18?"tr":"en";}
    private static String sample(String code){return switch(code){
        case "zh_cn"->"你好，我们可以先把物品放入交易栏，双方确认之后再完成交易。没有空格的中文消息也应该正常换行。";
        case "zh_tw"->"你好，我們可以先把物品放入交易欄，雙方確認之後再完成交易。沒有空格的中文訊息也應該正常換行。";
        case "tr"->"Merhaba! Eşyaları takas alanına koyabiliriz. İki taraf da onayladıktan sonra işlem tamamlanır. Türkçe karakterler: İı Şş Ğğ Çç Öö Üü.";
        case "ru"->"Привет! Давай сначала положим предметы в окно обмена. После подтверждения обеими сторонами обмен завершится.";
        case "de"->"Hallo! Wir können die Gegenstände zuerst ins Tauschfenster legen. Sobald beide Spieler bestätigen, wird der Tausch abgeschlossen.";
        default->"Hello! We can place the items in the trade window first. Once both players approve, the trade will be completed. Café • España • Português • Łódź.";
    };}

    private static void checkPersistence(Minecraft mc,String code) throws Exception {
        var file=mc.gameDirectory.toPath().resolve("config/newphone/phone_settings.properties");
        var properties=new Properties();try(var input=Files.newInputStream(file)){properties.load(input);}
        if(!code.equals(properties.getProperty("language")))throw new AssertionError("Language not saved: "+code);
        var loaded=PhoneSettingsStore.class.getDeclaredField("loaded");loaded.setAccessible(true);loaded.set(null,false);
        if(!PhoneSettingsStore.language().equals(code))throw new AssertionError("Language changed after reload: "+code);
    }

    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) throws Exception {
        if(!capture||phase>=(ALL?APPS.length*CODES.size():20))return;capture=false;
        var mc=Minecraft.getInstance();event.getGuiGraphics().flush();
        String name=ALL?code()+"-"+APPS[app()]:phase<18?(phase<9?"zh-cn-":"zh-tw-")+APPS[phase%9]:phase==18?"regression-tr-home":"regression-en-setup";
        int guiScale=Integer.getInteger("newphone.phoneLanguageGuiScale",1);
        var dir=mc.gameDirectory.toPath().resolve(guiScale==1?"previews":"previews-gui-"+guiScale);Files.createDirectories(dir);
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve(name+".png"));}
        System.out.println("Captured "+name);advance=true;
    }
}

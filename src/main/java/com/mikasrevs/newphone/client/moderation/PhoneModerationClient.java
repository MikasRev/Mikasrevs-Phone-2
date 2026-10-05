package com.mikasrevs.newphone.client.moderation;

import com.google.gson.Gson;
import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.zphone.gui.*;
import com.mikasrevs.newphone.moderation.PhoneModerationState;
import com.mikasrevs.newphone.network.PhoneNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=NewPhone.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class PhoneModerationClient {
    private static PhoneModerationState state=new PhoneModerationState();
    private static boolean ready;
    private static PhoneModerationState response;
    public static PhoneModerationState response(){return response;}
    public static boolean ready(){return ready;}
    public static boolean canManage(){return ready&&state.canManage();}
    public static PhoneModerationState state(){return state;}
    public static boolean banned(String platform){return ready&&state.bans!=null&&state.bans.contains(platform);}
    public static void receive(String json){
        PhoneModerationState next=new Gson().fromJson(json,PhoneModerationState.class);if(next==null)return;
        if(next.canManage()&&state.canManage()&&next.players==null){next.players=state.players;next.page=state.page;next.pages=state.pages;}
        if(!next.canManage()){next.players=null;next.selected=null;response=null;}
        else if(next.requestId!=null&&!next.requestId.isEmpty())response=next;
        state=next;ready=true;
    }
    public static void request(){if(Minecraft.getInstance().getConnection()!=null)PhoneNetwork.sendAction("MOD_SYNC","","");}
    public static String platform(PhoneApp app){return switch(app){case BLOCKPOST->"BLOCKPOST";case BLOCKGRAM->"BLOCKGRAM";case BROWSER->"TINDER";default->"";};}
    private static String platform(Screen screen){
        String name=screen.getClass().getSimpleName();
        if(name.contains("BlockPost"))return "BLOCKPOST";
        if(name.contains("BlockGram"))return "BLOCKGRAM";
        if(name.equals("ZPhoneBrowserScreen")||name.contains("Tinder"))return "TINDER";
        return "";
    }
    private static Screen guard(Screen screen){
        if(screen==null||screen instanceof ZPhoneAppAccessScreen)return screen;
        if(screen instanceof ZPhoneAdminScreen&&!canManage())return new ZPhoneAppAccessScreen("ADMIN_PANEL");
        String platform=platform(screen);
        if(!platform.isEmpty()&&(!ready||banned(platform)))return new ZPhoneAppAccessScreen(platform);
        return screen;
    }
    @SubscribeEvent public static void opening(ScreenEvent.Opening event){
        Screen next=guard(event.getNewScreen());if(next!=event.getNewScreen())event.setNewScreen(next);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var minecraft=Minecraft.getInstance();Screen next=guard(minecraft.screen);
        if(next!=minecraft.screen)minecraft.setScreen(next);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){state=new PhoneModerationState();response=null;ready=false;}
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event){state=new PhoneModerationState();response=null;ready=false;request();}
}

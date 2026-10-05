package com.mikasrevs.newphone;

import com.mikasrevs.newphone.client.device.PhoneDeviceSession;
import com.mikasrevs.newphone.client.duo.*;
import com.mikasrevs.newphone.client.zphone.gui.*;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;

/** Opt-in native OpenGL screenshots in an isolated client directory; excluded from the release JAR. */
@Mod.EventBusSubscriber(modid="newphone",value=Dist.CLIENT)
public final class DuoClientPreview {
    private static int phase=-1,ticks;
    private static boolean capture;
    private static boolean loading;
    private static long clickedAt;
    private static final String[] NAMES={"duo-home","duo-lock","duo-settings","duo-messages","duo-marketplace","duo-item-closed","mphone-home","duo-browser-held"};
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("newphone.duoPreview")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null)return;
        if(phase<0){
            if(!loading){if(!(mc.screen instanceof TitleScreen)||++ticks<30)return;loading=true;
                mc.createWorldOpenFlows().createFreshLevel("duo-preview-"+System.currentTimeMillis(),new net.minecraft.world.level.LevelSettings("Duo Preview",net.minecraft.world.level.GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(1L,false,false),registry->registry.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).createWorldDimensions());return;
            }
            if(mc.player==null||mc.level==null)return;
            PhoneSettingsStore.completeSetup();
            com.mikasrevs.newphone.client.security.PhoneSecurityStore.savePin("2468");
            mc.player.getInventory().setItem(0,new ItemStack(NewPhone.IPHONE_DUO.get()));mc.player.getInventory().setItem(1,new ItemStack(NewPhone.Z_PHONE.get()));mc.player.getInventory().selected=0;
            var owner=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var alex=net.minecraftforge.common.util.FakePlayerFactory.get(server.overworld(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Alex"));
                var actual=server.getPlayerList().getPlayer(owner);actual.getInventory().setItem(0,new ItemStack(NewPhone.IPHONE_DUO.get()));actual.getInventory().setItem(1,new ItemStack(NewPhone.Z_PHONE.get()));actual.getInventory().selected=0;actual.inventoryMenu.broadcastChanges();
                alex.getInventory().setItem(0,new ItemStack(NewPhone.Z_PHONE.get()));
                var data=com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData.get(server);var state=data.copySnapshot();
                state.knownNames.put(alex.getUUID().toString(),"Alex");state.contacts.put(owner.toString(),new java.util.ArrayList<>(java.util.List.of(alex.getUUID().toString())));state.contacts.put(alex.getUUID().toString(),new java.util.ArrayList<>(java.util.List.of(owner.toString())));data.replace(state);
                var request=new com.mikasrevs.newphone.network.PhoneSendRequest();request.id=java.util.UUID.randomUUID().toString();request.peer=owner.toString();request.body="MPhone'dan Duo'ya merhaba!";
                com.mikasrevs.newphone.server.PhoneMessagingService.send(alex,request,null);
            });
            phase=0;ticks=0;show(mc);
        }
        else if(++ticks==35)capture=true;
        else if(ticks==36&&phase==0){
            var apps=new java.util.LinkedHashSet<>(com.mikasrevs.newphone.client.settings.PhoneAppLayoutStore.dockApps());apps.addAll(com.mikasrevs.newphone.client.settings.PhoneAppLayoutStore.visibleHomeApps());
            int index=new java.util.ArrayList<>(apps).indexOf(com.mikasrevs.newphone.client.gui.PhoneApp.SETTINGS);
            var region=DuoLayout.appIcon(index);var surface=DuoInterface.layout(mc.screen.width,mc.screen.height);
            clickedAt=System.currentTimeMillis();
            mc.screen.mouseClicked(surface.x(region.x()+29),surface.y(region.y()+29),0);
        }
        else if(ticks==36&&phase==1){mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE,0,0);if(!(mc.screen instanceof ZPhonePinEntryScreen))throw new AssertionError("Duo lock bypassed shared PIN");}
        else if(ticks==41&&phase==0){
            long elapsed=System.currentTimeMillis()-clickedAt;
            if(!(mc.screen instanceof ZPhoneSettingsScreen)){
                System.out.println("Native transition pending: elapsed="+elapsed+"ms, screen="+mc.screen.getClass().getSimpleName());
                if(elapsed<2000){ticks=40;return;}throw new AssertionError("Duo touch did not open shared Settings");
            }
            System.out.println("Native Duo touch routing passed after "+elapsed+"ms.");
        }
        else if(ticks>42){ticks=0;if(++phase>=NAMES.length){System.out.println("Duo native preview complete.");mc.stop();}else show(mc);}
    }
    private static void show(Minecraft mc){
        mc.player.getInventory().selected=phase==6?1:0;
        PhoneDeviceSession.select(mc.player.getMainHandItem());
        DuoAnimation.reset();
        com.mikasrevs.newphone.client.zphone.PhoneYouTubeState.stop();
        ZPhoneClient.authorizeCurrentSession();
        if(phase==1)ZPhoneClient.resetLockSession();
        mc.setScreen(switch(phase){case 0->new DuoHomeScreen();case 1->new DuoLockScreen();case 2->new ZPhoneSettingsScreen();case 3->new ZPhoneMessagesScreen();case 4->new com.mikasrevs.newphone.marketplace.client.MarketplaceScreen();case 5,7->null;default->new ZPhoneHomeScreen();});
        if(phase==7){
            var browser=com.cinemamod.mcef.MCEF.createBrowser("data:text/html,%3Cbody%20style='margin:0;background:%230d1729;color:white;display:flex;align-items:center;justify-content:center;font:80px%20sans-serif'%3E%3Cdiv%3EDUO%20DISPLAY%3Cbr%3E%3Csmall%3EMPhone%20MCEF%3C/small%3E%3C/div%3E%3C/body%3E",false,1280,720);
            com.mikasrevs.newphone.client.zphone.ZPhoneHeldModelState.lightUpHeldPhone();
            com.mikasrevs.newphone.client.zphone.PhoneYouTubeState.enterModelMode(new com.mikasrevs.newphone.client.zphone.PhoneYouTubeState.BrowserHandle(){
                public int textureId(){return browser.getRenderer().getTextureID();}public void setFocus(boolean focused){browser.setFocus(focused);}public void resize(int w,int h){browser.resize(w,h);}public void close(){browser.close();}public net.minecraft.client.gui.screens.Screen createScreen(){return new DuoHomeScreen();}
            });
        }
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event){
        if(!capture)return;take(event.getGuiGraphics());
    }
    @SubscribeEvent public static void gui(RenderGuiEvent.Post event){if(capture&&(phase==5||phase==7))take(event.getGuiGraphics());}
    private static void take(net.minecraft.client.gui.GuiGraphics graphics){
        capture=false;
        try{
            graphics.flush();var mc=Minecraft.getInstance();
            Path dir=mc.gameDirectory.toPath().resolve("previews");Files.createDirectories(dir);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                image.writeToFile(dir.resolve(NAMES[phase]+".png"));
                if(phase==4){var s=DuoInterface.layout(mc.screen.width,mc.screen.height);double scale=mc.getWindow().getGuiScale();int inside=image.getPixelRGBA((int)(s.x(355)*scale),(int)(s.y(76)*scale)),outside=image.getPixelRGBA((int)(s.x(605)*scale),(int)(s.y(76)*scale));if(inside==outside)throw new AssertionError("Marketplace hid Duo companion controls");System.out.println("Native Duo companion layer check passed.");}
            }
            System.out.println("Captured "+NAMES[phase]+" ("+(mc.screen==null?"world":mc.screen.getClass().getSimpleName())+")");
        }catch(Exception e){throw new IllegalStateException("Native preview failed",e);}
    }
}

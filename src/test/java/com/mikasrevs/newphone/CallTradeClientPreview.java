package com.mikasrevs.newphone;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.*;
import com.mikasrevs.newphone.client.zphone.gui.*;
import com.mikasrevs.newphone.integration.voice.*;
import com.mikasrevs.newphone.marketplace.trade.*;
import com.mikasrevs.newphone.network.message.PhoneActionMessage;
import com.mikasrevs.newphone.server.PhoneServerService;
import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.Field;

/** Opt-in real client render/input check; only creates worlds under build, never shipped. */
@Mod.EventBusSubscriber(modid="newphone",value=Dist.CLIENT)
public final class CallTradeClientPreview {
    private static final String[] SHOTS={"trade-chat","call-outgoing-screen","call-outgoing-right","call-active-third-right","call-active-first-right","call-active-third-left","call-active-first-left","call-active-crouch","call-active-turn","call-active-walking","call-active-night","call-active-bunny-third","call-active-bunny-first","call-lowering","call-ended"};
    private static int phase=-1,ticks;
    private static long since;
    private static boolean loading,capture;
    private static boolean advance;
    private static boolean loweringSeen;
    private static TradeState trade;
    private static ServerPlayer partner;
    private static PhoneVoiceProvider original;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("newphone.callTradePreview")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        if(mc.player!=null){float yaw=phase==8?35:0,pitch=phase==8?20:0;mc.player.setXRot(pitch);mc.player.setYRot(yaw);mc.player.xRotO=pitch;mc.player.yRotO=yaw;mc.player.yHeadRot=yaw;mc.player.yHeadRotO=yaw;mc.player.yBodyRot=0;mc.player.yBodyRotO=0;mc.options.keyShift.setDown(phase==7);mc.options.keyUp.setDown(phase==9);}
        if(advance){advance=false;
            if(++phase==SHOTS.length){System.out.println("Call/trade native preview passed.");mc.getSingleplayerServer().execute(()->cleanup(mc));mc.stop();return;}
            show(mc);
        }
        if(phase<0){
            if(!loading){if(!(mc.screen instanceof TitleScreen)||++ticks<30)return;loading=true;
                mc.createWorldOpenFlows().createFreshLevel("call-trade-preview-"+System.currentTimeMillis(),new net.minecraft.world.level.LevelSettings("Call Trade Preview",net.minecraft.world.level.GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(1L,false,false),registry->registry.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).createWorldDimensions());return;
            }
            if(mc.player==null||mc.level==null)return;
            PhoneSettingsStore.completeSetup();ZPhoneClient.authorizeCurrentSession();
            mc.player.getInventory().setItem(0,new ItemStack(NewPhone.Z_PHONE.get()));mc.player.getInventory().selected=0;
            mc.player.setYRot(0);mc.player.setXRot(0);
            mc.getSingleplayerServer().execute(()->setupServer(mc));
            phase=0;show(mc);return;
        }
        if(capture)return;
        if(phase==0&&trade!=null)TradeClient.receive(new com.google.gson.Gson().toJson(trade));
        if(System.currentTimeMillis()-since>10_000)throw new AssertionError("Preview timed out in "+SHOTS[phase]);
        if(phase==1&&!"OUTGOING".equals(PhoneClientData.callStatus()))return;
        if(phase>=3&&phase<=12&&!PhoneClientData.callActive())return;
        if(phase==2&&mc.screen!=null)return;
        if(phase==13){
            if(PhoneClientData.hasCall())return;
            if(!loweringSeen){loweringSeen=true;since=System.currentTimeMillis();}
        }
        if(phase==14&&PhoneClientData.hasCall())return;
        long delay=phase==13?180:850;
        if(System.currentTimeMillis()-since>=delay)capture=true;
    }
    private static void setupServer(Minecraft mc){try{
        var server=mc.getSingleplayerServer();var actual=server.getPlayerList().getPlayer(mc.player.getUUID());
        actual.getInventory().setItem(0,new ItemStack(NewPhone.Z_PHONE.get()));actual.getInventory().selected=0;actual.inventoryMenu.broadcastChanges();
        partner=net.minecraftforge.common.util.FakePlayerFactory.get(server.overworld(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SkyBuilder"));partner.getInventory().setItem(0,new ItemStack(NewPhone.Z_PHONE.get()));
        @SuppressWarnings("unchecked") var ids=(Map<UUID,ServerPlayer>)field(server.getPlayerList().getClass(),"playersByUUID").get(server.getPlayerList());
        @SuppressWarnings("unchecked") var roster=(List<ServerPlayer>)field(server.getPlayerList().getClass(),"players").get(server.getPlayerList());ids.put(partner.getUUID(),partner);roster.add(partner);
        original=(PhoneVoiceProvider)field(PhoneVoiceManager.class,"provider").get(null);
        PhoneVoiceManager.install(new PhoneVoiceProvider(){public String id(){return "preview";}public String displayName(){return "Preview";}public String playerProblem(UUID p){return "";}public PhoneVoiceManager.StartResult startCall(UUID a,UUID b){return PhoneVoiceManager.StartResult.success(UUID.randomUUID().toString());}public void endCall(String id,UUID a,UUID b){}});
        var data=PhoneCommunicationSavedData.get(server);var snapshot=data.copySnapshot();snapshot.contacts.put(actual.getUUID().toString(),new ArrayList<>(List.of(partner.getUUID().toString())));snapshot.contacts.put(partner.getUUID().toString(),new ArrayList<>(List.of(actual.getUUID().toString())));data.replace(snapshot);
    }catch(Exception ex){throw new IllegalStateException(ex);}}
    private static void show(Minecraft mc){
        since=System.currentTimeMillis();mc.options.setCameraType(phase==3||phase==5||phase==7||phase==8||phase==10||phase==11?CameraType.THIRD_PERSON_FRONT:CameraType.FIRST_PERSON);
        if(phase==0){
            var s=new TradeState();s.id=UUID.randomUUID().toString();s.self=mc.player.getUUID().toString();s.other=UUID.randomUUID().toString();s.otherName="SkyBuilder";s.status="ACTIVE";
            s.chat.add(new TradeState.Message("1",s.other,"Merhaba! Elmas kılıcıma karşılık ne teklif edersin?",1));
            s.chat.add(new TradeState.Message("2",s.self,"8 elmas ve bir büyülü kitap verebilirim.",2));
            s.chat.add(new TradeState.Message("3",s.other,"Olur, eşyaları ekleyelim. İkimiz de onaylayınca takas tamamlanacak.",3));
            trade=s;
            var screen=new TradeScreen();mc.setScreen(screen);TradeClient.receive(new com.google.gson.Gson().toJson(s));
        }else if(phase==1){
            mc.getSingleplayerServer().execute(()->PhoneServerService.handleAction(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()),new PhoneActionMessage("CALL_START",partner.getUUID().toString(),"")));
        }else{
            if(phase!=2)mc.setScreen(null);
            if(phase==3)mc.getSingleplayerServer().execute(()->PhoneServerService.handleAction(partner,new PhoneActionMessage("CALL_ACCEPT",PhoneClientData.callId(),"")));
            if(phase==5){
                mc.player.stopUsingItem();mc.player.getInventory().setItem(0,ItemStack.EMPTY);mc.player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,bunnyPhone());
                mc.getSingleplayerServer().execute(()->{var actual=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());actual.stopUsingItem();actual.getInventory().setItem(0,ItemStack.EMPTY);actual.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,bunnyPhone());actual.inventoryMenu.broadcastChanges();});
            }
            if(phase==10||phase==11){
                long time=phase==10?18000:6000;
                mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().setDayTime(time));
                mc.level.setDayTime(time);
            }
            if(phase==13)PhoneClientData.endCall();
        }
    }
    private static ItemStack bunnyPhone(){
        var stack=new ItemStack(NewPhone.Z_PHONE.get());PhoneCaseStyle.apply(stack,PhoneCaseStyle.BUNNY_BLUSH);return stack;
    }
    @SubscribeEvent public static void screen(ScreenEvent.Render.Post event){if(capture)take(event.getGuiGraphics());}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){if(capture&&Minecraft.getInstance().screen==null)take(event.getGuiGraphics());}
    private static void take(net.minecraft.client.gui.GuiGraphics graphics){
        capture=false;var mc=Minecraft.getInstance();
        try{
            if(phase>=2&&phase<=12)checkInventoryTransform(mc);
            graphics.flush();var dir=mc.gameDirectory.toPath().resolve("previews");Files.createDirectories(dir);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve(SHOTS[phase]+".png"));}
            float p=PhoneCallAnimation.progress(mc.player,phase>=5?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND);
            if(phase>=2&&phase<=12){if(p<.99||!mc.player.isUsingItem())throw new AssertionError("Held call pose missing in "+SHOTS[phase]);}
            if(phase==14&&mc.player.isUsingItem())throw new AssertionError("Call ended but hand remained in use pose");
            if(phase==13&&(p<=0||p>=1))throw new AssertionError("Lowering skipped its smooth transition");
            System.out.println("Captured "+SHOTS[phase]+" status="+PhoneClientData.callStatus()+" handProgress="+p+" camera="+mc.options.getCameraType());
            if(phase==1){mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);}
            advance=true;
        }catch(Exception e){throw new IllegalStateException("Native call preview failed",e);}
    }
    private static void checkInventoryTransform(Minecraft mc){
        var stack=phase>=5?mc.player.getOffhandItem():mc.player.getMainHandItem();
        var held=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
        var resting=mc.getItemRenderer().getModel(stack.copy(),mc.level,mc.player,0);
        for(var context:new ItemDisplayContext[]{ItemDisplayContext.GUI,ItemDisplayContext.GROUND,ItemDisplayContext.FIXED}){
            var callPose=new com.mojang.blaze3d.vertex.PoseStack();
            var normalPose=new com.mojang.blaze3d.vertex.PoseStack();
            held.applyTransform(context,callPose,false);resting.applyTransform(context,normalPose,false);
            if(!callPose.last().pose().equals(normalPose.last().pose(),.00001F))
                throw new AssertionError("Call moved phone outside normal "+context+" placement in "+SHOTS[phase]);
        }
    }
    private static void cleanup(Minecraft mc){try{var list=mc.getSingleplayerServer().getPlayerList();@SuppressWarnings("unchecked") var ids=(Map<UUID,ServerPlayer>)field(list.getClass(),"playersByUUID").get(list);@SuppressWarnings("unchecked") var roster=(List<ServerPlayer>)field(list.getClass(),"players").get(list);ids.remove(partner.getUUID());roster.remove(partner);PhoneVoiceManager.install(original);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Field field(Class<?> c,String name)throws Exception{while(c!=null){try{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(name);}
}

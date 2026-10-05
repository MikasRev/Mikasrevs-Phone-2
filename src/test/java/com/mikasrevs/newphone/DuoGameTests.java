package com.mikasrevs.newphone;

import com.mojang.authlib.GameProfile;
import com.mikasrevs.newphone.integration.voice.*;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.network.message.PhoneActionMessage;
import com.mikasrevs.newphone.server.*;
import com.mikasrevs.newphone.server.data.*;
import com.mikasrevs.newphone.phone.device.PhoneDeviceType;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.lang.reflect.Field;

/** Real Forge items and shared authoritative services. Fake actors have no live client/audio sockets. */
@GameTestHolder("newphone")
@PrefixGameTestTemplate(false)
public final class DuoGameTests {
    @GameTest(templateNamespace="forge",template="empty3x3",timeoutTicks=200)
    public static void sharedAccountMessagesAndCalls(GameTestHelper h)throws Exception{
        var server=h.getLevel().getServer();
        var a=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"DuoUser"));
        var b=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"MPhoneUser"));
        @SuppressWarnings("unchecked") var players=(Map<UUID,ServerPlayer>)field(server.getPlayerList().getClass(),"playersByUUID").get(server.getPlayerList());
        @SuppressWarnings("unchecked") var roster=(List<ServerPlayer>)field(server.getPlayerList().getClass(),"players").get(server.getPlayerList());
        var original=(PhoneVoiceProvider)field(PhoneVoiceManager.class,"provider").get(null);
        var voice=new TestVoice();
        players.put(a.getUUID(),a);players.put(b.getUUID(),b);roster.add(a);roster.add(b);
        try{
            PhoneVoiceManager.install(voice);
            a.getInventory().clearContent();b.getInventory().clearContent();
            var duo=new ItemStack(NewPhone.IPHONE_DUO.get());var mphone=new ItemStack(NewPhone.Z_PHONE.get());
            a.getInventory().selected=0;b.getInventory().selected=0;
            a.getInventory().setItem(0,duo);b.getInventory().setItem(0,mphone);
            h.assertTrue(PhoneInventoryUtil.hasPhone(a)&&PhoneInventoryUtil.hasPhone(b),"Duo did not satisfy shared ownership checks");
            h.assertTrue(PhoneInventoryUtil.deviceType(duo)==PhoneDeviceType.IPHONE_DUO&&PhoneInventoryUtil.deviceType(mphone)==PhoneDeviceType.MPHONE,"Device identity did not stay presentation-only");
            var numbers=PhoneNumberSavedData.get(server);String number=numbers.getOrAssign(a.getUUID());
            var data=PhoneCommunicationSavedData.get(server);var snapshot=data.copySnapshot();
            String self=a.getUUID().toString(),peer=b.getUUID().toString();
            snapshot.contacts.put(self,new ArrayList<>(List.of(peer)));snapshot.contacts.put(peer,new ArrayList<>(List.of(self)));data.replace(snapshot);
            send(a,peer,"Duo to MPhone");send(b,self,"MPhone to Duo");
            h.assertTrue(data.copySnapshot().messages.stream().filter(m->m.fromUuid.equals(self)||m.fromUuid.equals(peer)).count()==2,"Cross-device messaging forked or lost the conversation");
            var unread=new PhoneStateData();data.recentFor(self,unread);
            h.assertTrue(unread.unreadCounts.values().stream().mapToInt(Integer::intValue).sum()==1,"Duo did not receive shared unread notifications");
            a.getInventory().setItem(1,new ItemStack(NewPhone.Z_PHONE.get()));a.getInventory().selected=1;
            action(a,"SYNC_FULL","","");
            h.assertTrue(number.equals(numbers.getOrAssign(a.getUUID())),"Switching device generated another number");
            send(a,peer,"Same account after switching");
            h.assertTrue(data.copySnapshot().messages.stream().filter(m->m.fromUuid.equals(self)||m.fromUuid.equals(peer)).count()==3,"Switching device reset existing messages");
            a.getInventory().selected=0;
            // Cosmetics change the old handset only. Duo never becomes an MPhone cosmetic model.
            PhoneModelSelectionService.select(a,"model_01");
            String cosmeticKey=com.mikasrevs.newphone.phone.model.PhoneModelRegistry.STACK_TAG;
            h.assertTrue(duo.getTag()==null||!duo.getTag().contains(cosmeticKey),"MPhone cosmetic state leaked into Duo");
            h.assertTrue("model_01".equals(a.getInventory().getItem(1).getTag().getString(cosmeticKey)),"Duo integration broke MPhone cosmetics");
            a.getInventory().offhand.set(0,new ItemStack(NewPhone.Z_PHONE.get()));
            h.assertTrue(PhoneInventoryUtil.protectedPhone(a,duo)&&PhoneInventoryUtil.protectedPhone(a,a.getOffhandItem()),"A held handset could enter trade escrow");
            call(h,a,b,false,voice);call(h,b,a,true,voice);
            var restored=PhoneCommunicationSavedData.load(data.save(new CompoundTag())).copySnapshot();
            h.assertTrue(restored.contacts.get(self).contains(peer)&&restored.messages.stream().anyMatch(m->m.body.equals("Duo to MPhone")),"Device switching broke persisted contacts/messages");
            h.assertTrue(restored.callHistory.get(self).size()==2&&restored.callHistory.get(peer).size()==2,"Cross-device calls bypassed existing call history");
            var numberRestore=PhoneNumberSavedData.load(numbers.save(new CompoundTag()));
            h.assertTrue(number.equals(numberRestore.getOrAssign(a.getUUID()))&&a.getUUID().equals(numberRestore.findOwner(number)),"Restart changed the shared number");
            PhoneServerService.handlePlayerUnavailable(a);players.remove(a.getUUID());roster.remove(a);
            players.put(a.getUUID(),a);roster.add(a);action(a,"SYNC_FULL","","");
            h.assertTrue(number.equals(numbers.getOrAssign(a.getUUID()))&&data.hasContact(self,peer),"Reconnect reset shared identity");
            h.succeed();System.out.println("Duo shared-core GameTest passed: two device items, one UUID account/number, bidirectional messages/unreads, audio/video call state, history and NBT reload/reconnect.");
        }finally{
            PhoneServerService.handlePlayerUnavailable(a);PhoneServerService.handlePlayerUnavailable(b);
            PhoneVoiceManager.install(original);roster.remove(a);roster.remove(b);players.remove(a.getUUID());players.remove(b.getUUID());
        }
    }
    private static void send(ServerPlayer actor,String peer,String text){var r=new PhoneSendRequest();r.id=UUID.randomUUID().toString();r.peer=peer;r.body=text;PhoneMessagingService.send(actor,r,null);}
    private static void call(GameTestHelper h,ServerPlayer caller,ServerPlayer callee,boolean video,TestVoice voice)throws Exception{
        @SuppressWarnings("unchecked") var rate=(Map<String,Long>)field(PhoneServerService.class,"LAST_ACTION_AT").get(null);
        rate.keySet().removeIf(k->k.startsWith(caller.getUUID().toString()));
        action(caller,video?"CALL_START_VIDEO":"CALL_START",callee.getUUID().toString(),"");
        @SuppressWarnings("unchecked") var calls=(Map<UUID,Object>)field(PhoneServerService.class,"CALLS").get(null);
        var call=calls.get(caller.getUUID());
        h.assertTrue(call!=null&&calls.get(callee.getUUID())==call,"Devices did not use the same call object");
        h.assertTrue((boolean)field(call.getClass(),"video").get(call)==video,"Shared video state lost");
        String id=field(call.getClass(),"id").get(call).toString();
        action(callee,"CALL_POSE_START",id,"");
        h.assertTrue(!callee.isUsingItem(),"Ringing recipient raised phone before accepting");
        action(caller,"CALL_POSE_START",UUID.randomUUID().toString(),"");
        h.assertTrue(!caller.isUsingItem(),"Stale call ID activated hand pose");
        action(caller,"CALL_POSE_START",id,"");
        h.assertTrue(caller.isUsingItem()==!video,"Outgoing voice pose/video exclusion failed");
        caller.stopUsingItem();PhoneServerService.maintainCallPose(caller);PhoneServerService.maintainCallPose(callee);
        h.assertTrue(caller.isUsingItem()==!video&&!callee.isUsingItem(),"Released use key/ringing recipient maintenance failed");
        action(callee,"CALL_ACCEPT",id,"");
        h.assertTrue(field(call.getClass(),"phase").get(call).toString().equals("ACTIVE")&&voice.starts>0,"Cross-device call could not be accepted");
        action(callee,"CALL_POSE_START",id,"");
        h.assertTrue(callee.isUsingItem()==!video,"Accepted voice pose/video exclusion failed");
        callee.stopUsingItem();PhoneServerService.maintainCallPose(callee);
        h.assertTrue(callee.isUsingItem()==!video,"Use-key release broke remote active call pose");
        action(caller,"CALL_END",id,"");
        h.assertTrue(!calls.containsKey(caller.getUUID())&&!calls.containsKey(callee.getUUID()),"Shared call was not cleaned up");
        h.assertTrue(!caller.isUsingItem()&&!callee.isUsingItem(),"Ended call left a hand raised");
        PhoneServerService.maintainCallPose(caller);PhoneServerService.maintainCallPose(callee);
        h.assertTrue(!caller.isUsingItem()&&!callee.isUsingItem(),"Ended call restarted remote pose");
    }
    private static void action(ServerPlayer actor,String action,String target,String body){PhoneServerService.handleAction(actor,new PhoneActionMessage(action,target,body));}
    private static Field field(Class<?> type,String name)throws Exception{while(type!=null){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException e){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
    private static final class TestVoice implements PhoneVoiceProvider{
        int starts;
        public String id(){return "test-transport";}public String displayName(){return "Test transport";}
        public String playerProblem(UUID player){return "";}
        public PhoneVoiceManager.StartResult startCall(UUID a,UUID b){return PhoneVoiceManager.StartResult.success("fixture-"+(++starts));}
        public void endCall(String id,UUID a,UUID b){}
    }
}

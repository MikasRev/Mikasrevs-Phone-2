package com.mikasrevs.newphone.client.device;

import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.phone.device.PhoneDeviceType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Selects a frontend for this opening. Switching shells never clears shared account data. */
public final class PhoneDeviceSession {
    private static PhoneDeviceType current=PhoneDeviceType.MPHONE;
    private static boolean selected;
    private PhoneDeviceSession(){}
    public static void select(ItemStack stack){current=PhoneInventoryUtil.deviceType(stack);selected=true;}
    public static PhoneDeviceType hudType(){
        var mc=Minecraft.getInstance();var player=mc.player;
        if(mc.screen instanceof com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen&&selected&&hasSelectedDevice())return current;
        if(player!=null){
            if(PhoneInventoryUtil.isPhone(player.getMainHandItem()))return PhoneInventoryUtil.deviceType(player.getMainHandItem());
            if(PhoneInventoryUtil.isPhone(player.getOffhandItem()))return PhoneInventoryUtil.deviceType(player.getOffhandItem());
        }
        return selected&&hasSelectedDevice()?current:PhoneInventoryUtil.deviceType(PhoneInventoryUtil.findPhone(player));
    }
    public static PhoneDeviceType current(){return current;}
    public static boolean isDuo(){return current==PhoneDeviceType.IPHONE_DUO;}
    public static boolean hasSelectedDevice(){
        var player=Minecraft.getInstance().player;if(player==null)return false;
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);
            if(PhoneInventoryUtil.isPhone(stack)&&PhoneInventoryUtil.deviceType(stack)==current)return true;
        }return false;
    }
    public static void reset(){current=PhoneDeviceType.MPHONE;selected=false;com.mikasrevs.newphone.client.duo.DuoAnimation.reset();com.mikasrevs.newphone.client.duo.DuoNotifications.reset();}
}

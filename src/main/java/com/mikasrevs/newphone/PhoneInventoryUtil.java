package com.mikasrevs.newphone;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Shared check for whether a player is still carrying the MPhone item. */
public final class PhoneInventoryUtil {
    private PhoneInventoryUtil() {}

    public static boolean hasPhone(Player player) {
        return !findPhone(player).isEmpty();
    }
    public static boolean isPhone(ItemStack stack){return stack!=null&&!stack.isEmpty()&&stack.getItem() instanceof com.mikasrevs.newphone.phone.device.PhoneDevice;}
    public static com.mikasrevs.newphone.phone.device.PhoneDeviceType deviceType(ItemStack stack){return isPhone(stack)?((com.mikasrevs.newphone.phone.device.PhoneDevice)stack.getItem()).deviceType():com.mikasrevs.newphone.phone.device.PhoneDeviceType.MPHONE;}
    /** Both held handsets are protected; either hand may have opened the interface. */
    public static boolean protectedPhone(Player player,ItemStack stack){return isPhone(stack)&&(stack==player.getMainHandItem()||stack==player.getOffhandItem()||stack==findPhone(player));}

    /** Prefers the currently held handset, then falls back to the first inventory handset. */
    public static ItemStack findPhone(Player player) {
        if (player == null) return ItemStack.EMPTY;
        ItemStack mainHand = player.getMainHandItem();
        if (isPhone(mainHand)) return mainHand;
        ItemStack offHand = player.getOffhandItem();
        if (isPhone(offHand)) return offHand;

        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isPhone(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }
}

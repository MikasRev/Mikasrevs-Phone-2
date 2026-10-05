package com.mikasrevs.newphone.marketplace.trade;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import net.minecraft.world.item.ItemStack;

/** Computes an all-or-nothing insertion before touching a player's inventory. */
public final class TradeInventory {
    private TradeInventory(){}
    public static List<ItemStack> insert(List<ItemStack> inventory,ItemStack offered){
        if(inventory.size()!=36||offered.isEmpty())return null;
        List<ItemStack> result=new ArrayList<>();inventory.forEach(s->result.add(s.copy()));
        ItemStack remaining=offered.copy();
        for(ItemStack s:result){
            if(!s.isEmpty()&&ItemStack.isSameItemSameTags(s,remaining)){
                int add=Math.min(remaining.getCount(),Math.max(0,Math.min(64,s.getMaxStackSize())-s.getCount()));
                s.grow(add);remaining.shrink(add);if(remaining.isEmpty())return result;
            }
        }
        for(int i=0;i<result.size();i++)if(result.get(i).isEmpty()){
            int add=Math.min(remaining.getCount(),Math.min(64,remaining.getMaxStackSize()));
            result.set(i,remaining.copyWithCount(add));remaining.shrink(add);if(remaining.isEmpty())return result;
        }
        return null;
    }
    public static String fingerprint(ItemStack stack){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stack.serializeNBT().toString().getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}

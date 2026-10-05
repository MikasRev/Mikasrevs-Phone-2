package com.mikasrevs.newphone;

import com.mikasrevs.newphone.marketplace.trade.TradeInventory;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;

public final class TradeInventoryChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static List<ItemStack> empty(){List<ItemStack> rows=new ArrayList<>();for(int i=0;i<36;i++)rows.add(ItemStack.EMPTY);return rows;}
    public static void run(){
        var inventory=empty();inventory.set(0,new ItemStack(Items.DIAMOND,60));var result=TradeInventory.insert(inventory,new ItemStack(Items.DIAMOND,16));
        check(result!=null&&result.get(0).getCount()==64&&result.get(1).getCount()==12,"partial stack insertion failed");
        check(inventory.get(0).getCount()==60&&inventory.get(1).isEmpty(),"planning changed source");
        var full=empty();for(int i=0;i<36;i++)full.set(i,new ItemStack(Items.STONE,64));
        check(TradeInventory.insert(full,new ItemStack(Items.DIAMOND,1))==null,"full inventory accepted item");check(full.stream().allMatch(s->s.getCount()==64),"failed plan destroyed stacks");
        var special=new ItemStack(Items.DIAMOND_SWORD);CompoundTag tag=new CompoundTag();tag.putInt("Damage",17);tag.putString("Custom","keep");special.setTag(tag);
        check(TradeInventory.insert(empty(),special).get(0).serializeNBT().equals(special.serializeNBT()),"delivery lost NBT");
        var pearls=empty();pearls.set(0,new ItemStack(Items.ENDER_PEARL,15));var pearlPlan=TradeInventory.insert(pearls,new ItemStack(Items.ENDER_PEARL,16));
        check(pearlPlan.get(0).getCount()==16&&pearlPlan.get(1).getCount()==15,"item stack limit ignored");
        var tagged=empty();var old=new ItemStack(Items.DIAMOND,4);old.setTag(tag.copy());tagged.set(0,old);
        var namedPlan=TradeInventory.insert(tagged,new ItemStack(Items.DIAMOND,3));check(namedPlan.get(0).getCount()==4&&namedPlan.get(1).getCount()==3,"different NBT merged");
        check(!TradeInventory.fingerprint(old).equals(TradeInventory.fingerprint(new ItemStack(Items.DIAMOND,4))),"fingerprint ignored NBT");
        Random random=new Random(462735);
        for(int run=0;run<1000;run++){
            var rows=empty();int before=0;
            for(int i=0;i<36;i++)if(random.nextBoolean()){int count=1+random.nextInt(64);rows.set(i,new ItemStack(Items.DIAMOND,count));before+=count;}
            int count=1+random.nextInt(64);var plan=TradeInventory.insert(rows,new ItemStack(Items.DIAMOND,count));
            if(plan!=null)check(plan.stream().mapToInt(ItemStack::getCount).sum()==before+count,"random insertion lost items");
            check(rows.stream().mapToInt(ItemStack::getCount).sum()==before,"random plan mutated source");
        }
        System.out.println("Trade inventory checks passed (real items, stack limits, full inventory, NBT, fingerprints, 1,000 insertion scenarios).");
    }
}

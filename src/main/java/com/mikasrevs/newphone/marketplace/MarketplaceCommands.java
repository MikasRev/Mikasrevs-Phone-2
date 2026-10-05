package com.mikasrevs.newphone.marketplace;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.marketplace.data.MarketplaceSavedData;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicit operator reconciliation for an interrupted/uncertain provider payment. */
@Mod.EventBusSubscriber(modid=NewPhone.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class MarketplaceCommands {
    private MarketplaceCommands(){}
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("marketplace").requires(source->source.hasPermission(4))
            .then(Commands.literal("review").executes(context->{
                var source=context.getSource();int count=0;
                for(var listing:MarketplaceSavedData.get(source.getServer()).all())if(listing.status.equals("REVIEW")){
                    source.sendSuccess(()->Component.literal(listing.id+" | "+listing.title+" | "+listing.buyerName+" -> "+listing.ownerName+" | "+listing.priceText),false);count++;
                }
                int total=count;source.sendSuccess(()->Component.literal(total+" payment(s) awaiting review. Check both LC bank histories before resolving."),false);return count;
            }))
            .then(Commands.literal("resolve").then(Commands.argument("listing",StringArgumentType.word())
                .then(Commands.literal("confirm_paid").executes(c->resolve(c.getSource(),StringArgumentType.getString(c,"listing"),true)))
                .then(Commands.literal("confirm_unpaid").executes(c->resolve(c.getSource(),StringArgumentType.getString(c,"listing"),false))))));
    }
    private static int resolve(CommandSourceStack source,String id,boolean paid){
        try{
            var data=MarketplaceSavedData.get(source.getServer());data.resolveReview(id,paid,source.getTextName());data.flush(source.getServer());
            MarketplaceManager.broadcast(source.getServer(),null);
            source.sendSuccess(()->Component.literal(id+" reconciled. No currency was moved by this command."),true);return 1;
        }catch(java.io.IOException|IllegalArgumentException e){source.sendFailure(Component.literal("Could not reconcile: "+e.getMessage()));return 0;}
    }
}

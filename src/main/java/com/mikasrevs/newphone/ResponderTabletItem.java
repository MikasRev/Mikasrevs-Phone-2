package com.mikasrevs.newphone;

import com.mikasrevs.newphone.client.emergency.EmergencyClientData;
import com.mikasrevs.newphone.client.tablet.ResponderTabletScreen;
import com.mikasrevs.newphone.emergency.EmergencyService;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Dedicated role-gated Police/EMS dispatch device; it never opens the civilian phone UI. */
public final class ResponderTabletItem extends Item {
    private final EmergencyService service;

    public ResponderTabletItem(EmergencyService service, Properties properties) {
        super(properties);
        this.service = service;
    }

    public EmergencyService service() {
        return service;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                EmergencyClientData.requestSync();
                if (!EmergencyClientData.isAuthorized(service)) {
                    player.displayClientMessage(
                            Component.translatable("emergency.newphone.tablet.unauthorized"), true);
                    return;
                }
                ResponderTabletScreen.open(service);
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("item.newphone.responder_tablet.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(service.translationKey())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean hasTablet(Player player, EmergencyService service) {
        if (player == null || service == null) return false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof ResponderTabletItem tablet && tablet.service == service) return true;
        }
        return false;
    }
}

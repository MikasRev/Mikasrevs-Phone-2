package com.mikasrevs.newphone;

import com.mikasrevs.newphone.client.sound.PhoneSoundManager;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.zphone.ZPhoneClientItemExtensions;
import com.mikasrevs.newphone.client.zphone.ZPhoneHeldModelState;
import com.mikasrevs.newphone.client.zphone.PhoneYouTubeState;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;

/** Primary MPhone item for the Forge 1.20.1 port. */
public class ZPhoneItem extends Item implements com.mikasrevs.newphone.phone.device.PhoneDevice {
    public ZPhoneItem(Properties properties) {
        super(properties);
    }
    @Override public com.mikasrevs.newphone.phone.device.PhoneDeviceType deviceType(){return com.mikasrevs.newphone.phone.device.PhoneDeviceType.MPHONE;}

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        InteractionHand otherHand = usedHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);

        if (deviceType().supportsCases() && otherStack.getItem() instanceof PhoneCaseItem caseItem) {
            if (!level.isClientSide()) {
                PhoneCaseItem.install(level, player, stack, otherStack, caseItem.style());
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (deviceType().supportsCases() && player.isShiftKeyDown() && PhoneCaseStyle.fromStack(stack) != PhoneCaseStyle.NONE) {
            if (!level.isClientSide()) PhoneCaseItem.remove(level, player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ZPhoneHeldModelState.lightUpHeldPhone();
                PhoneSoundManager.playPhoneOpen();
                com.mikasrevs.newphone.client.device.PhoneDeviceSession.select(stack);
                if (PhoneYouTubeState.isModelMode()) {
                    PhoneYouTubeState.reopen(net.minecraft.client.Minecraft.getInstance());
                    return;
                }
                ZPhoneClient.openPhone(stack);
            });
        }
        // Consume the click without forcing the vanilla eating/use animation. The long
        // phone-to-ear use pose is activated explicitly by the call system later.
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (deviceType() == com.mikasrevs.newphone.phone.device.PhoneDeviceType.MPHONE && entity instanceof net.minecraft.server.level.ServerPlayer player) {
            String id = com.mikasrevs.newphone.server.data.PhoneNumberSavedData.get(player.server).getPhoneModel(player.getUUID());
            com.mikasrevs.newphone.phone.model.PhoneModelRegistry.applyToStack(stack, id);
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72_000;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ZPhoneClientItemExtensions.INSTANCE);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if(!deviceType().supportsCases()){
            tooltip.add(Component.translatable("item.newphone.iphone_duo.tooltip"));return;
        }
        tooltip.add(Component.translatable("item.newphone.z_phone.tooltip_1"));
        tooltip.add(Component.translatable("item.newphone.z_phone.tooltip_2"));
        PhoneCaseStyle caseStyle = PhoneCaseStyle.fromStack(stack);
        if (caseStyle != PhoneCaseStyle.NONE) {
            tooltip.add(Component.translatable("item.newphone.z_phone.case",
                    Component.translatable(caseStyle.translationKey())));
            tooltip.add(Component.translatable("item.newphone.phone_case.tooltip_remove")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

package com.mikasrevs.newphone;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A physical, reusable case that can be fitted to an MPhone held in the other hand. */
public final class PhoneCaseItem extends Item {
    private final PhoneCaseStyle style;

    public PhoneCaseItem(PhoneCaseStyle style, Properties properties) {
        super(properties);
        if (style == null || style == PhoneCaseStyle.NONE) {
            throw new IllegalArgumentException("A phone case item requires a concrete style");
        }
        this.style = style;
    }

    public PhoneCaseStyle style() {
        return style;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack caseStack = player.getItemInHand(usedHand);
        InteractionHand otherHand = usedHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        ItemStack phoneStack = player.getItemInHand(otherHand);

        if (phoneStack.is(NewPhone.Z_PHONE.get())) {
            if (!level.isClientSide()) install(level, player, phoneStack, caseStack, style);
        } else if (!level.isClientSide()) {
            player.displayClientMessage(
                    Component.translatable("message.newphone.phone_case.need_phone"), true
            );
        }
        return InteractionResultHolder.sidedSuccess(caseStack, level.isClientSide());
    }

    static void install(Level level, Player player, ItemStack phoneStack,
                        ItemStack newCaseStack, PhoneCaseStyle newStyle) {
        PhoneCaseStyle oldStyle = PhoneCaseStyle.fromStack(phoneStack);
        if (oldStyle == newStyle) {
            player.displayClientMessage(
                    Component.translatable("message.newphone.phone_case.already_equipped"), true
            );
            return;
        }

        PhoneCaseStyle.apply(phoneStack, newStyle);
        if (!player.getAbilities().instabuild) newCaseStack.shrink(1);
        if (oldStyle != PhoneCaseStyle.NONE) giveOrDrop(player, NewPhone.phoneCaseStack(oldStyle));
        syncInventory(player);
        playCaseSound(level, player, 1.05F);
        player.displayClientMessage(
                Component.translatable("message.newphone.phone_case.equipped",
                        Component.translatable(newStyle.translationKey())), true
        );
    }

    static void remove(Level level, Player player, ItemStack phoneStack) {
        PhoneCaseStyle oldStyle = PhoneCaseStyle.fromStack(phoneStack);
        if (oldStyle == PhoneCaseStyle.NONE) return;

        PhoneCaseStyle.apply(phoneStack, PhoneCaseStyle.NONE);
        giveOrDrop(player, NewPhone.phoneCaseStack(oldStyle));
        syncInventory(player);
        playCaseSound(level, player, 0.9F);
        player.displayClientMessage(
                Component.translatable("message.newphone.phone_case.removed",
                        Component.translatable(oldStyle.translationKey())), true
        );
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void syncInventory(Player player) {
        player.getInventory().setChanged();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
            if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
                serverPlayer.containerMenu.broadcastChanges();
            }
        }
    }

    private static void playCaseSound(Level level, Player player, float pitch) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.7F, pitch);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.newphone.phone_case.tooltip_equip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.newphone.phone_case.tooltip_remove")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

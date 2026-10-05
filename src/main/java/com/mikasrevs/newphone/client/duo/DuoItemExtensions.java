package com.mikasrevs.newphone.client.duo;

import com.mikasrevs.newphone.client.zphone.ZPhoneClientItemExtensions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class DuoItemExtensions implements IClientItemExtensions {
    public static final DuoItemExtensions INSTANCE=new DuoItemExtensions();
    private DuoItemRenderer renderer;
    private DuoItemExtensions(){}
    @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new DuoItemRenderer();return renderer;}
    @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity,InteractionHand hand,ItemStack stack){return ZPhoneClientItemExtensions.INSTANCE.getArmPose(entity,hand,stack);}
    @Override public boolean applyForgeHandTransform(PoseStack pose,LocalPlayer player,HumanoidArm arm,ItemStack stack,float partial,float equip,float swing){
        if(player.isUsingItem())return false; // Reuse MPhone's existing phone-to-ear call pose.
        int side=arm==HumanoidArm.RIGHT?1:-1;
        float idle=com.mikasrevs.newphone.client.settings.PhoneSettingsStore.animationsEnabled()?(float)Math.sin((player.tickCount+partial)*.075F)*.003F:0;
        pose.translate(side*.46F,-.18F-equip*.40F+idle,-1.02F);
        pose.mulPose(Axis.YP.rotationDegrees(side*-12));pose.mulPose(Axis.XP.rotationDegrees(-8));
        return true;
    }
}

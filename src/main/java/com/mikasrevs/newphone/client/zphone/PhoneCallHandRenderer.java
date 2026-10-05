package com.mikasrevs.newphone.client.zphone;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.phone.device.PhoneDeviceType;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Renders the player's own sleeve/hand and handset along one continuous earward path. */
@Mod.EventBusSubscriber(modid=NewPhone.MOD_ID,value=Dist.CLIENT)
public final class PhoneCallHandRenderer {
    private PhoneCallHandRenderer() {}
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void hand(RenderHandEvent event) {
        var mc=Minecraft.getInstance();
        if(mc.player==null||!PhoneInventoryUtil.isPhone(event.getItemStack())
                ||PhoneInventoryUtil.deviceType(event.getItemStack())!=PhoneDeviceType.MPHONE)return;
        float p=PhoneCallAnimation.progress(mc.player,event.getHand());
        if(p<=0.001F)return;
        event.setCanceled(true);
        var arm=event.getHand()==InteractionHand.MAIN_HAND?mc.player.getMainArm():mc.player.getMainArm().getOpposite();
        int side=arm==HumanoidArm.RIGHT?1:-1;
        var pose=event.getPoseStack();pose.pushPose();
        float partial=event.getPartialTick();
        float walk=-(mc.player.walkDist+(mc.player.walkDist-mc.player.walkDistO)*partial);
        float bob=mc.options.bobView().get()?net.minecraft.util.Mth.lerp(partial,mc.player.oBob,mc.player.bob):0;
        float pitch=(mc.player.getViewXRot(partial)-net.minecraft.util.Mth.lerp(partial,mc.player.xBobO,mc.player.xBob))*.1F;
        float yaw=(mc.player.getViewYRot(partial)-net.minecraft.util.Mth.lerp(partial,mc.player.yBobO,mc.player.yBob))*.1F;
        var steady=com.mikasrevs.newphone.phone.animation.PhoneCallPlacement.blend(new org.joml.Matrix4f(),
                com.mikasrevs.newphone.phone.animation.PhoneCallPlacement.viewMovement(walk,bob,pitch,yaw).invert(),p);
        pose.last().pose().mul(steady);pose.last().normal().mul(new org.joml.Matrix3f(steady).invert().transpose());
        // Settle at the edge of the view, beside the cheek; keep the centre clear.
        pose.translate(side*(0.56F+0.07F*p),-0.52F+0.57F*p,-0.72F+0.21F*p);
        pose.mulPose(Axis.ZP.rotationDegrees(side*(-24F*p)));
        if(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer renderer){
            pose.pushPose();pose.translate(side*0.33F,-0.33F-0.60F*p,-0.67F+0.42F*p);
            pose.mulPose(Axis.XP.rotationDegrees(12*p));
            if(arm==HumanoidArm.RIGHT)renderer.renderRightHand(pose,event.getMultiBufferSource(),event.getPackedLight(),mc.player);
            else renderer.renderLeftHand(pose,event.getMultiBufferSource(),event.getPackedLight(),mc.player);
            pose.popPose();
        }
        pose.pushPose();
        float idleX=side==1?0.265625F:0.625F,idleY=side==1?-0.15625F:-0.140625F,idleZ=side==1?-1F:-1.1875F;
        // The OBJ origin is off-centre; the left-hand rotation needs its own
        // translation so the handset remains just as close to the wrist/camera.
        float callX=side==1?-0.004375F:-0.025F,callZ=side==1?-0.07F:0.1655F;
        pose.translate(side*(idleX+(callX-idleX)*p),idleY+(-0.31625F-idleY)*p,idleZ+(callZ-idleZ)*p);
        pose.mulPose(Axis.YP.rotationDegrees(side*((side==1?148:154)*(1-p)+85*p)));
        if(side==-1){pose.mulPose(Axis.XP.rotationDegrees(1-p));pose.mulPose(Axis.ZP.rotationDegrees(-(1-p)));}
        float scale=1.1F-0.47F*p;pose.scale(scale,scale,scale);
        // NONE avoids the old abrupt call-specific JSON transform. The same mesh,
        // cosmetic shell and fitted case are still selected by the existing item renderer.
        mc.getItemRenderer().renderStatic(mc.player,event.getItemStack(),ItemDisplayContext.NONE,arm==HumanoidArm.LEFT,
                pose,event.getMultiBufferSource(),mc.level,event.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,mc.player.getId());
        pose.popPose();pose.popPose();
    }
}

package com.mikasrevs.newphone.client.duo;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.device.PhoneDeviceSession;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.phone.device.PhoneDeviceType;
import com.mikasrevs.newphone.client.zphone.PhoneYouTubeState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

@Mod.EventBusSubscriber(modid=NewPhone.MOD_ID,value=Dist.CLIENT)
public final class DuoRenderEvents {
    private DuoRenderEvents(){}
    @SubscribeEvent public static void hand(RenderHandEvent event){
        var mc=Minecraft.getInstance();
        if(PhoneDeviceSession.isDuo()&&mc.screen instanceof AbstractZPhoneScreen){event.setCanceled(true);return;}
        if(mc.player==null||PhoneInventoryUtil.deviceType(event.getItemStack())!=PhoneDeviceType.IPHONE_DUO||!PhoneInventoryUtil.isPhone(event.getItemStack()))return;
        var arm=event.getHand()==InteractionHand.MAIN_HAND?mc.player.getMainArm():mc.player.getMainArm().getOpposite();
        var pose=event.getPoseStack();pose.pushPose();
        boolean transformed=DuoItemExtensions.INSTANCE.applyForgeHandTransform(pose,mc.player,arm,event.getItemStack(),event.getPartialTick(),event.getEquipProgress(),event.getSwingProgress());
        if(transformed&&mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer renderer){
            pose.pushPose();pose.translate(arm==HumanoidArm.RIGHT?.44:-.44,-.30,.09);
            pose.mulPose(Axis.ZP.rotationDegrees(arm==HumanoidArm.RIGHT?-10:10));pose.mulPose(Axis.XP.rotationDegrees(180));
            if(arm==HumanoidArm.RIGHT)renderer.renderRightHand(pose,event.getMultiBufferSource(),event.getPackedLight(),mc.player);else renderer.renderLeftHand(pose,event.getMultiBufferSource(),event.getPackedLight(),mc.player);
            pose.popPose();
        }
        if(transformed&&PhoneYouTubeState.isModelMode()&&PhoneYouTubeState.textureId()!=0){
            pose.scale(.0011F,-.0011F,.0011F);
            VertexConsumer consumer=event.getMultiBufferSource().getBuffer(PhoneYouTubeState.renderType());
            // Two aligned display surfaces leave the physical crease visible; MCEF remains the shared browser.
            float halfVideoHeight=620F*720F/1280F/2;
            for(int side=0;side<2;side++){
                float left=side==0?-310:10,right=left+300,u=(left+310)/620F,v=(right+310)/620F;
                var p=pose.last();vertex(consumer,p,left,-halfVideoHeight,u,0);vertex(consumer,p,right,-halfVideoHeight,v,0);vertex(consumer,p,right,halfVideoHeight,v,1);vertex(consumer,p,left,halfVideoHeight,u,1);
            }
        }
        pose.popPose();
    }
    private static void vertex(VertexConsumer c,com.mojang.blaze3d.vertex.PoseStack.Pose p,float x,float y,float u,float v){c.vertex(p.pose(),x,y,9.3F).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(p.normal(),0,0,1).endVertex();}
}

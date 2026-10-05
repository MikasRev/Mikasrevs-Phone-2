package com.mikasrevs.newphone.client.zphone;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.ZPhoneItem;
import com.mikasrevs.newphone.client.phone.model.ClientPhoneModels;
import com.mikasrevs.newphone.phone.model.PhoneModelDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Projects the live MCEF texture onto the actual screen plane of the held OBJ phone. */
@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, value = Dist.CLIENT)
public final class PhoneYouTubeModelRenderer {
    private PhoneYouTubeModelRenderer() {
    }

    @SubscribeEvent
    public static void renderHeldScreen(RenderHandEvent event) {
        if (!PhoneYouTubeState.isModelMode() || PhoneYouTubeState.textureId() == 0) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ZPhoneItem)) return;
        if(com.mikasrevs.newphone.PhoneInventoryUtil.deviceType(stack)==com.mikasrevs.newphone.phone.device.PhoneDeviceType.IPHONE_DUO)return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;
        HumanoidArm arm = mainHand
                ? minecraft.player.getMainArm()
                : minecraft.player.getMainArm().getOpposite();
        boolean rightHand = arm == HumanoidArm.RIGHT;
        boolean leftHand = !rightHand;
        int side = rightHand ? 1 : -1;
        ItemDisplayContext context = rightHand
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        applyVanillaHandMotion(poseStack, side, event.getSwingProgress(), event.getEquipProgress());

        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        int seed = minecraft.player.getId() + context.ordinal();
        BakedModel model = itemRenderer.getModel(stack, minecraft.level, minecraft.player, seed);
        model = ForgeHooksClient.handleCameraTransforms(poseStack, model, context, leftHand);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        PhoneModelDefinition.ScreenSurface surface =
                ClientPhoneModels.selected(minecraft.player, stack).screen();
        drawBrowserQuad(event, poseStack, surface, leftHand);
        poseStack.popPose();
    }

    private static void applyVanillaHandMotion(PoseStack poseStack, int side, float swing, float equip) {
        float swingRoot = Mth.sqrt(swing);
        float swingX = -0.4F * Mth.sin(swingRoot * (float) Math.PI);
        float swingY = 0.2F * Mth.sin(swingRoot * ((float) Math.PI * 2.0F));
        float swingZ = -0.2F * Mth.sin(swing * (float) Math.PI);
        poseStack.translate(side * swingX, swingY, swingZ);
        poseStack.translate(side * 0.56F, -0.52F + equip * -0.6F, -0.72F);

        float attackCurve = Mth.sin(swing * swing * (float) Math.PI);
        float attackRoot = Mth.sin(swingRoot * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0F + attackCurve * -20.0F)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * attackRoot * -20.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(attackRoot * -80.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0F));
    }

    private static void drawBrowserQuad(
            RenderHandEvent event,
            PoseStack poseStack,
            PhoneModelDefinition.ScreenSurface surface,
            boolean leftHand
    ) {
        float left = surface.left() / 16.0F;
        float right = surface.right() / 16.0F;
        float bottom = surface.bottom() / 16.0F;
        float top = surface.top() / 16.0F;

        // The visible display face points toward -Z in every supplied phone mesh.
        float depth = surface.depth() / 16.0F - 0.0012F;
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(PhoneYouTubeState.renderType());

        if (!leftHand) {
            vertex(consumer, pose, left, bottom, depth, 0.0F, 1.0F);
            vertex(consumer, pose, right, bottom, depth, 0.0F, 0.0F);
            vertex(consumer, pose, right, top, depth, 1.0F, 0.0F);
            vertex(consumer, pose, left, top, depth, 1.0F, 1.0F);
        } else {
            vertex(consumer, pose, left, bottom, depth, 1.0F, 0.0F);
            vertex(consumer, pose, right, bottom, depth, 1.0F, 1.0F);
            vertex(consumer, pose, right, top, depth, 0.0F, 1.0F);
            vertex(consumer, pose, left, top, depth, 0.0F, 0.0F);
        }
    }

    private static void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v
    ) {
        consumer.vertex(pose.pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0.0F, 0.0F, -1.0F)
                .endVertex();
    }
}

package com.mikasrevs.newphone.client.zphone;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.phone.device.PhoneDeviceType;
import com.mikasrevs.newphone.phone.animation.PhoneCallPlacement;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.*;

/** Two skin-preserving arm segments meet a fixed head-relative hand target. */
@Mod.EventBusSubscriber(modid=NewPhone.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class PhoneCallPlayerLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
    private static final ModelLayerLocation NORMAL=new ModelLayerLocation(new ResourceLocation(NewPhone.MOD_ID,"call_arm"),"main");
    private static final ModelLayerLocation SLIM=new ModelLayerLocation(new ResourceLocation(NewPhone.MOD_ID,"call_arm_slim"),"main");
    private final ModelPart mesh;
    private final boolean slim;
    private PhoneCallPlayerLayer(PlayerRenderer renderer,ModelPart mesh,boolean slim){super(renderer);this.mesh=mesh;this.slim=slim;}
    @SubscribeEvent public static void definitions(EntityRenderersEvent.RegisterLayerDefinitions event){
        event.registerLayerDefinition(NORMAL,()->definition(false));event.registerLayerDefinition(SLIM,()->definition(true));
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event){
        for(String skin:event.getSkins()){
            PlayerRenderer renderer=event.getSkin(skin);boolean slim=skin.equals("slim");
            if(renderer!=null)renderer.addLayer(new PhoneCallPlayerLayer(renderer,event.getEntityModels().bakeLayer(slim?SLIM:NORMAL),slim));
        }
    }
    private static LayerDefinition definition(boolean slim){
        var mesh=new MeshDefinition();float width=slim?3:4;
        for(String side:new String[]{"right","left"})for(int segment=0;segment<2;segment++)for(boolean sleeve:new boolean[]{false,true}){
            boolean right=side.equals("right");int u=right?40:sleeve?48:32,v=right?(sleeve?32:16):48;
            mesh.getRoot().addOrReplaceChild(side+segment+(sleeve?"_sleeve":""),CubeListBuilder.create().texOffs(u,v+6*segment)
                    .addBox(-width/2,0,-2,width,6,4,new CubeDeformation(sleeve?.25F:0)),PartPose.ZERO);
        }
        return LayerDefinition.create(mesh,64,64);
    }
    public static float progress(net.minecraft.world.entity.LivingEntity player,HumanoidArm arm){
        InteractionHand hand=player.getMainArm()==arm?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
        return PhoneInventoryUtil.isPhone(player.getItemInHand(hand))&&PhoneInventoryUtil.deviceType(player.getItemInHand(hand))==PhoneDeviceType.MPHONE
                ?PhoneCallAnimation.progress(player,hand):0;
    }
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,float walk,float walkAmount,float partial,float age,float yaw,float pitch){
        var model=getParentModel();var head=PhoneCallPoseGeometry.part(model.head,0);
        for(HumanoidArm arm:HumanoidArm.values()){
            float p=progress(player,arm);if(p<=.001F)continue;
            int side=arm==HumanoidArm.RIGHT?1:-1;var bone=side==1?model.rightArm:model.leftArm;
            var neutral=PhoneCallPoseGeometry.part(bone,0);
            float centre=-side*(slim?.5F:1F)/16;
            var shoulder=neutral.transformPosition(new Vector3f(centre,-2F/16,0));
            var idleWrist=neutral.transformPosition(new Vector3f(centre,10F/16,0));
            var wrist=new Vector3f(idleWrist).lerp(PhoneCallPlacement.grip(head,side),p);
            var straight=new Vector3f(shoulder).lerp(wrist,.5F);
            var elbow=straight.lerp(PhoneCallPlacement.elbow(shoulder,wrist,side),p);
            segment(pose,buffers,light,player,side,0,shoulder,elbow);
            segment(pose,buffers,light,player,side,1,elbow,wrist);
        }
    }
    private void segment(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,int side,int segment,Vector3f start,Vector3f end){
        var delta=new Vector3f(end).sub(start);float length=delta.length();if(length<.0001F)return;
        pose.pushPose();pose.translate(start.x,start.y,start.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),delta.div(length)));
        pose.scale(1,length/(6F/16),1);
        String key=(side==1?"right":"left")+segment;
        mesh.getChild(key).render(pose,buffers.getBuffer(RenderType.entitySolid(player.getSkinTextureLocation())),light,OverlayTexture.NO_OVERLAY);
        if(player.isModelPartShown(side==1?PlayerModelPart.RIGHT_SLEEVE:PlayerModelPart.LEFT_SLEEVE))
            mesh.getChild(key+"_sleeve").render(pose,buffers.getBuffer(RenderType.entityTranslucent(player.getSkinTextureLocation())),light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    @Mod.EventBusSubscriber(modid=NewPhone.MOD_ID,value=Dist.CLIENT)
    public static final class Visibility {
        private static final java.util.Map<PlayerRenderer,boolean[]> previous=new java.util.IdentityHashMap<>();
        @SubscribeEvent public static void before(RenderPlayerEvent.Pre event){
            var model=event.getRenderer().getModel();
            boolean right=progress(event.getEntity(),HumanoidArm.RIGHT)>.001F,left=progress(event.getEntity(),HumanoidArm.LEFT)>.001F;
            if(!right&&!left)return;
            previous.put(event.getRenderer(),new boolean[]{model.rightArm.visible,model.leftArm.visible,model.rightSleeve.visible,model.leftSleeve.visible});
            if(right){model.rightArm.visible=false;model.rightSleeve.visible=false;}
            if(left){model.leftArm.visible=false;model.leftSleeve.visible=false;}
        }
        @SubscribeEvent public static void after(RenderPlayerEvent.Post event){
            boolean[] saved=previous.remove(event.getRenderer());if(saved==null)return;
            var model=event.getRenderer().getModel();model.rightArm.visible=saved[0];model.leftArm.visible=saved[1];
            model.rightSleeve.visible=saved[2];model.leftSleeve.visible=saved[3];
        }
    }
}

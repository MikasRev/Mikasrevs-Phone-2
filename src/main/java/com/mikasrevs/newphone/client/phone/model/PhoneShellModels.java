package com.mikasrevs.newphone.client.phone.model;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.zphone.ZPhoneHeldModelState;
import com.mikasrevs.newphone.phone.model.PhoneModelRegistry;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Selects a baked OBJ shell before vanilla applies existing held/call/item transforms. */
@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PhoneShellModels {
    private PhoneShellModels() {}
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        PhoneModelRegistry.all().stream().filter(d -> d != PhoneModelRegistry.CLASSIC)
                .forEach(d -> event.register(d.model()));
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        var key = new ModelResourceLocation(NewPhone.MOD_ID, "z_phone", "inventory");
        BakedModel classic = event.getModels().get(key);
        if (classic == null) return;
        Map<String, BakedModel> shells = new HashMap<>();
        for (var definition : PhoneModelRegistry.all()) {
            BakedModel model = event.getModels().get(definition.model());
            if (model != null) shells.put(definition.id(), model);
        }
        shells.put(PhoneModelRegistry.CLASSIC.id(), classic);
        event.getModels().put(key, new ShellWrapper(classic, Map.copyOf(shells)));
    }
    private static final class ShellWrapper extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;
        private final Map<BakedModel, BakedModel> fittedModels =
                Collections.synchronizedMap(new IdentityHashMap<>());

        ShellWrapper(BakedModel classic, Map<String, BakedModel> shells) {
            super(classic);
            overrides = new ItemOverrides() {
                @Override public BakedModel resolve(BakedModel base, ItemStack stack, ClientLevel level,
                        LivingEntity entity, int seed) {
                    BakedModel shell = shells.getOrDefault(ClientPhoneModels.selected(entity, stack).id(), classic);
                    // Each shell reuses the current screen animation, case, and call predicates.
                    BakedModel resolved = shell.getOverrides().resolve(shell, stack, level, entity, seed);
                    BakedModel selected = resolved == null ? shell : resolved;
                    if(entity instanceof net.minecraft.client.player.AbstractClientPlayer player){
                        var hand=player.getMainHandItem()==stack?net.minecraft.world.InteractionHand.MAIN_HAND
                                :player.getOffhandItem()==stack?net.minecraft.world.InteractionHand.OFF_HAND:null;
                        float progress=hand==null?0:com.mikasrevs.newphone.client.zphone.PhoneCallAnimation.progress(player,hand);
                        if(progress>.001F)return new HeadCallModel(selected,shell,player,progress);
                    }
                    return fittedModels.computeIfAbsent(selected, ThirdPersonGripModel::new);
                }
            };
        }
        @Override public ItemOverrides getOverrides() { return overrides; }
    }

    private static final class HeadCallModel extends BakedModelWrapper<BakedModel> {
        private final BakedModel idle;
        private final net.minecraft.client.player.AbstractClientPlayer player;
        private final float progress;
        HeadCallModel(BakedModel model,BakedModel idle,net.minecraft.client.player.AbstractClientPlayer player,float progress){super(model);this.idle=idle;this.player=player;this.progress=progress;}
        @Override public BakedModel applyTransform(ItemDisplayContext context,PoseStack pose,boolean leftHand){
            if((context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    &&net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer){
                int side=context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND?1:-1;
                var model=renderer.getModel();var arm=side==1?model.rightArm:model.leftArm;
                var armMatrix=com.mikasrevs.newphone.client.zphone.PhoneCallPoseGeometry.part(arm,player.getModelName().equals("slim")?side*.5F:0);
                var head=com.mikasrevs.newphone.client.zphone.PhoneCallPoseGeometry.part(model.head,0);
                PoseStack normal=new PoseStack();normal.translate(-1.26F/16,-1.30F/16,-3.63F/16);
                idle.getTransforms().getTransform(context).apply(leftHand,normal);
                var aligned=com.mikasrevs.newphone.phone.animation.PhoneCallPlacement.align(armMatrix,head,normal.last().pose(),side,progress);
                pose.last().pose().mul(aligned);
                pose.last().normal().mul(new org.joml.Matrix3f(aligned).invert().transpose());
                return this;
            }
            originalModel.applyTransform(context,pose,leftHand);return this;
        }
    }

    /**
     * Corrects F5/third-person grip placement and applies YouTube's landscape pose.
     * The OBJ phone origin sits near the
     * screen instead of at the lower grip, which left it several model pixels away
     * from the palm. Outside held YouTube mode, first-person, GUI, ground and fixed
     * transforms remain exactly as authored in the model JSON files.
     */
    private static final class ThirdPersonGripModel extends BakedModelWrapper<BakedModel> {
        // The OBJ phone is not centred on Minecraft's usual (0.5, 0.5) item pivot.
        // Rotate around the physical display/body centre so landscape mode keeps the
        // exact same held distance and placement as portrait mode.
        private static final float LANDSCAPE_PIVOT_X =
                ((PhoneModelRegistry.STANDARD_SCREEN.left() + PhoneModelRegistry.STANDARD_SCREEN.right())
                        / 32.0F) - 0.5F;
        private static final float LANDSCAPE_PIVOT_Y =
                ((PhoneModelRegistry.STANDARD_SCREEN.bottom() + PhoneModelRegistry.STANDARD_SCREEN.top())
                        / 32.0F) - 0.5F;
        // Offsets are in hand-space blocks. They place the bare phone and every
        // fitted case's lower centre on the vanilla wrist attachment point.
        private static final float NORMAL_X = -1.26F / 16.0F;
        private static final float NORMAL_Y = -1.30F / 16.0F;
        private static final float NORMAL_Z = -3.63F / 16.0F;

        ThirdPersonGripModel(BakedModel originalModel) {
            super(originalModel);
        }

        @Override
        public BakedModel applyTransform(
                ItemDisplayContext context,
                PoseStack poseStack,
                boolean leftHand
        ) {
            if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
                poseStack.translate(NORMAL_X, NORMAL_Y, NORMAL_Z);
            }

            BakedModel transformed = originalModel.applyTransform(context, poseStack, leftHand);
            if (ZPhoneHeldModelState.isLandscapeActive()
                    && (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                poseStack.translate(LANDSCAPE_PIVOT_X, LANDSCAPE_PIVOT_Y, 0.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(leftHand ? -90.0F : 90.0F));
                poseStack.translate(-LANDSCAPE_PIVOT_X, -LANDSCAPE_PIVOT_Y, 0.0F);
            }
            return transformed == originalModel ? this : transformed;
        }
    }
}

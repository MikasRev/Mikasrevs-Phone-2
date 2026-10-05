package com.mikasrevs.newphone.client.duo;

import com.mikasrevs.newphone.client.device.PhoneDeviceSession;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
import com.mikasrevs.newphone.client.zphone.ZPhoneHeldModelState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;

public final class DuoItemRenderer extends BlockEntityWithoutLevelRenderer {
    DuoItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var mc=Minecraft.getInstance();boolean held=context.firstPerson()||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        boolean browser=com.mikasrevs.newphone.client.zphone.PhoneYouTubeState.isModelMode();
        float open=held&&PhoneDeviceSession.isDuo()&&ZPhoneHeldModelState.isScreenOnFor(mc.player,stack)?(browser?1:mc.screen instanceof AbstractZPhoneScreen?DuoAnimation.unfold():0):0;
        pose.pushPose();pose.translate(.5,.5,.5);pose.scale(.0011F,-.0011F,.0011F);
        // A closed handset is centred on its grip rather than on the open hinge.
        pose.translate(158*(1-open),0,0);DuoMesh.render(pose,buffers,light,overlay,open);pose.popPose();
    }
}

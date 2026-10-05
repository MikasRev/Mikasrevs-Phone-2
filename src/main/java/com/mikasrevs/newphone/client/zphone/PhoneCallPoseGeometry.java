package com.mikasrevs.newphone.client.zphone;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;

public final class PhoneCallPoseGeometry {
    private PhoneCallPoseGeometry() {}
    public static Matrix4f part(ModelPart part,float extraX) {
        return new Matrix4f().translate((part.x+extraX)/16,part.y/16,part.z/16)
                .rotateZYX(part.zRot,part.yRot,part.xRot).scale(part.xScale,part.yScale,part.zScale);
    }
}

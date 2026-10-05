package com.mikasrevs.newphone.client.duo;

import com.mikasrevs.newphone.NewPhone;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;

/** The same hinged mesh is used in-world, in inventory and under the touch interface. */
public final class DuoMesh {
    private static final ResourceLocation MATERIAL=new ResourceLocation(NewPhone.MOD_ID,"textures/item/iphone_duo_material.png");
    private DuoMesh(){}
    public static void render(PoseStack pose,MultiBufferSource buffers,int light,int overlay,float open){
        VertexConsumer consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL));
        for(var group:DuoModelGeometry.Group.values()){
            pose.pushPose();
            if(group==DuoModelGeometry.Group.BODY_RIGHT){float closed=1-Math.max(0,Math.min(1,open));pose.translate(0,0,12*closed);pose.mulPose(Axis.YP.rotationDegrees(-180*closed));}
            for(var box:DuoModelGeometry.BOXES)if(box.group()==group)draw(consumer,pose.last(),box,light,overlay);
            pose.popPose();
        }
    }
    private static void draw(VertexConsumer c,PoseStack.Pose pose,DuoModelGeometry.Box b,int light,int overlay){
        float x=b.x(),y=b.y(),z=b.z(),r=x+b.width(),bottom=y+b.height(),back=z+b.depth();
        face(c,pose,b.color(),light,overlay,0,0,1,x,y,back,r,y,back,r,bottom,back,x,bottom,back);
        face(c,pose,b.color(),light,overlay,0,0,-1,r,y,z,x,y,z,x,bottom,z,r,bottom,z);
        face(c,pose,b.color(),light,overlay,-1,0,0,x,y,z,x,y,back,x,bottom,back,x,bottom,z);
        face(c,pose,b.color(),light,overlay,1,0,0,r,y,back,r,y,z,r,bottom,z,r,bottom,back);
        face(c,pose,b.color(),light,overlay,0,-1,0,x,y,z,r,y,z,r,y,back,x,y,back);
        face(c,pose,b.color(),light,overlay,0,1,0,x,bottom,back,r,bottom,back,r,bottom,z,x,bottom,z);
    }
    private static void face(VertexConsumer c,PoseStack.Pose p,int color,int light,int overlay,float nx,float ny,float nz,float...v){
        for(int i=0;i<12;i+=3)c.vertex(p.pose(),v[i],v[i+1],v[i+2]).color((color>>16)&255,(color>>8)&255,color&255,255).uv(.5F,.5F).overlayCoords(overlay).uv2(light).normal(p.normal(),nx,ny,nz).endVertex();
    }
}

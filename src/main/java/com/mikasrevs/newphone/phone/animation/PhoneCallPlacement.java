package com.mikasrevs.newphone.phone.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared head-relative placement, independent of walking, item-use resets and mesh overrides. */
public final class PhoneCallPlacement {
    public static final float EAR_X=4.65F/16, EAR_Y=-4F/16;
    private PhoneCallPlacement() {}
    public static Matrix4f viewMovement(float phase,float bob,float pitchLag,float yawLag) {
        float sine=(float)Math.sin(phase*Math.PI),cosine=(float)Math.cos(phase*Math.PI);
        return new Matrix4f().translate(sine*bob*.5F,-Math.abs(cosine*bob),0)
                .rotateZ((float)Math.toRadians(sine*bob*3))
                .rotateX((float)Math.toRadians(Math.abs(Math.cos(phase*Math.PI-.2)*bob)*5))
                .rotateX((float)Math.toRadians(pitchLag)).rotateY((float)Math.toRadians(yawLag));
    }
    public static Matrix4f heldItem(int side) {
        return new Matrix4f().rotateX(-(float)Math.PI/2).rotateY((float)Math.PI)
                .translate(side/16F,.125F,-.625F);
    }
    public static Matrix4f atEar(Matrix4f head,int side) {
        return new Matrix4f(head).translate(-side*EAR_X,EAR_Y,0)
                // The exported screen faces -Z: turn it inward towards the ear.
                .rotateX((float)Math.PI).rotateY(-side*(float)Math.PI/2).scale(.78F)
                .translate(-5F/16,-13.4516F/16,-.35F/16);
    }
    public static Vector3f grip(Matrix4f head,int side) {
        return head.transformPosition(new Vector3f(-side*5.8F/16,-2.2F/16,.2F/16));
    }
    public static Matrix4f align(Matrix4f arm,Matrix4f head,Matrix4f idle,int side,float progress) {
        Matrix4f hand=new Matrix4f(arm).mul(heldItem(side));
        Matrix4f start=new Matrix4f(hand).mul(idle).translate(-.5F,-.5F,-.5F);
        Matrix4f end=atEar(head,side);
        return new Matrix4f(hand).invert().mul(blend(start,end,progress)).translate(.5F,.5F,.5F);
    }
    public static Matrix4f blend(Matrix4f a,Matrix4f b,float progress) {
        float p=Math.max(0,Math.min(1,progress));
        if(p==0)return new Matrix4f(a);if(p==1)return new Matrix4f(b);
        var position=a.getTranslation(new Vector3f()).lerp(b.getTranslation(new Vector3f()),p);
        var scale=a.getScale(new Vector3f()).lerp(b.getScale(new Vector3f()),p);
        var rotation=a.getUnnormalizedRotation(new Quaternionf()).normalize()
                .slerp(b.getUnnormalizedRotation(new Quaternionf()).normalize(),p);
        return new Matrix4f().translationRotateScale(position,rotation,scale);
    }
    public static Vector3f elbow(Vector3f shoulder,Vector3f wrist,int side) {
        var delta=new Vector3f(wrist).sub(shoulder);
        float distance=delta.length();
        if(distance<.0001F)return new Vector3f(shoulder);
        var direction=delta.div(distance);
        var bend=new Vector3f(-side*.25F,0,-1);
        bend.sub(new Vector3f(direction).mul(bend.dot(direction))).normalize();
        float reach=6F/16;
        float extension=(float)Math.sqrt(Math.max(0,reach*reach-distance*distance*.25F));
        return new Vector3f(shoulder).lerp(wrist,.5F).add(bend.mul(extension));
    }
}

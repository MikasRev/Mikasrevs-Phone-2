package com.mikasrevs.newphone;

import com.mikasrevs.newphone.phone.animation.PhoneCallMotion;

final class PhoneCallMotionChecks {
    static void run() {
        var motion=new PhoneCallMotion();long start=1_000_000_000L;
        require(motion.update(true,start,true)==0,"Lift jumped on first frame");
        float previous=0;
        for(long dt=0;dt<=550_000_000L;dt+=5_000_000L){
            float p=motion.update(true,start+dt,true);
            require(p>=previous&&p<=1,"Lift overshot or moved backwards");previous=p;
        }
        require(previous==1,"Lift did not reach ear");
        float before=motion.value(start+600_000_000L);
        require(motion.update(false,start+600_000_000L,true)==before,"Lowering jumped");
        require(motion.update(false,start+1_000_000_000L,true)==0,"Lowering did not finish");
        motion.update(true,start+1_100_000_000L,true);
        before=motion.value(start+1_300_000_000L);
        require(Math.abs(motion.update(false,start+1_300_000_000L,true)-before)<.0001,"Quick cancel snapped hand");
        require(motion.update(true,start+1_350_000_000L,false)==1,"Disabled animations did not snap to endpoint");
        require(motion.update(false,start+1_360_000_000L,false)==0,"Disabled animation left hand raised");
        require(PhoneCallMotion.shouldRaise("OUTGOING",false)&&PhoneCallMotion.shouldRaise("ACTIVE",false),"Voice states missing");
        require(!PhoneCallMotion.shouldRaise("INCOMING",false)&&!PhoneCallMotion.shouldRaise("IDLE",false)
                &&!PhoneCallMotion.shouldRaise("ACTIVE",true)&&!PhoneCallMotion.shouldRaise("OUTGOING",true),"Unanswered/video call raised hand");
        var budget=new com.mikasrevs.newphone.network.PhoneRequestLimits.Budget();
        var pose=com.mikasrevs.newphone.network.PhoneRequestLimits.kind("CALL_POSE_START");
        require(pose!=com.mikasrevs.newphone.network.PhoneRequestLimits.Kind.CONTROL,"Cosmetic pose consumes call-control budget");
        for(int i=0;i<240;i++)budget.allow(pose,start+i*50_000_000L);
        require(budget.allow(com.mikasrevs.newphone.network.PhoneRequestLimits.kind("CALL_END"),start+12_000_000_000L),"Repeated poses swallowed hang-up");
        var remote=new PhoneCallMotion();remote.updateRemote(true,start,true);remote.updateRemote(true,start+600_000_000L,true);
        require(remote.updateRemote(false,start+650_000_000L,true)==1,"One missing remote-use tick lowered phone");
        require(remote.updateRemote(false,start+800_000_000L,true)==1,"Remote lower transition jumped");
        require(remote.updateRemote(false,start+1_200_000_000L,true)==0,"Remote phone stayed raised after call ended");
        placement();
        System.out.println("Call motion checks passed (smooth lift/lower, quick reversal, animation setting, voice/video phases).");
    }
    private static void placement(){
        var random=new java.util.Random(9127);
        for(int side:new int[]{-1,1})for(int i=0;i<300;i++){
            var arm=new org.joml.Matrix4f().translate(side*.3125F,.125F,0).rotateXYZ(random.nextFloat()*2,random.nextFloat(),random.nextFloat()).scale(.9F+random.nextFloat()*.2F);
            var head=new org.joml.Matrix4f().translate(0,i%2==0?0:.15F,0).rotateYXZ(random.nextFloat()*1.5F-.75F,random.nextFloat()-.5F,0);
            var idle=new org.joml.Matrix4f().rotateY((float)Math.PI).scale(.82F);
            var offset=com.mikasrevs.newphone.phone.animation.PhoneCallPlacement.align(arm,head,idle,side,1);
            var actual=new org.joml.Matrix4f(arm).mul(com.mikasrevs.newphone.phone.animation.PhoneCallPlacement.heldItem(side)).mul(offset).translate(-.5F,-.5F,-.5F);
            var expected=com.mikasrevs.newphone.phone.animation.PhoneCallPlacement.atEar(head,side);
            require(actual.equals(expected,.00002F),"Ear placement changed with walking arm/use pose");
            var centre=actual.transformPosition(new org.joml.Vector3f(5F/16,13.4516F/16,.35F/16));
            var ear=head.transformPosition(new org.joml.Vector3f(-side*com.mikasrevs.newphone.phone.animation.PhoneCallPlacement.EAR_X,com.mikasrevs.newphone.phone.animation.PhoneCallPlacement.EAR_Y,0));
            require(centre.distance(ear)<.00002F,"Phone centre is not on side-of-head midpoint");
            var screenNormal=actual.transformDirection(new org.joml.Vector3f(0,0,-1)).normalize();
            var towardsHead=head.transformDirection(new org.joml.Vector3f(side,0,0)).normalize();
            require(screenNormal.dot(towardsHead)>.9999F,"Phone screen faces outwards instead of towards the ear");
            var movement=com.mikasrevs.newphone.phone.animation.PhoneCallPlacement.viewMovement(random.nextFloat()*30,random.nextFloat()*.1F,random.nextFloat()*8,random.nextFloat()*8);
            require(new org.joml.Matrix4f(movement).mul(new org.joml.Matrix4f(movement).invert()).equals(new org.joml.Matrix4f(),.00001F),"Walking/view lag was not cancelled");
            var shoulder=new org.joml.Vector3f(-side*5F/16,2F/16,0);
            var wrist=com.mikasrevs.newphone.phone.animation.PhoneCallPlacement.grip(new org.joml.Matrix4f(),side);
            var elbow=com.mikasrevs.newphone.phone.animation.PhoneCallPlacement.elbow(shoulder,wrist,side);
            require(Math.abs(elbow.distance(shoulder)-6F/16)<.0001&&Math.abs(elbow.distance(wrist)-6F/16)<.0001,"Bent arm did not preserve segment length");
        }
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

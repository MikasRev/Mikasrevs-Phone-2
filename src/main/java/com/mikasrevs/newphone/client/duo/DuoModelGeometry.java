package com.mikasrevs.newphone.client.duo;

import java.util.*;

/** Shared editable geometry for item rendering and the physical on-screen device. Units are display pixels. */
public final class DuoModelGeometry {
    public enum Group{BODY_LEFT,BODY_RIGHT,HINGE}
    public record Box(String name,Group group,float x,float y,float z,float width,float height,float depth,int color){}
    public static final List<Box> BOXES=build();
    private DuoModelGeometry(){}
    private static List<Box> build(){
        List<Box> parts=new ArrayList<>();
        body(parts,Group.BODY_LEFT,-322);body(parts,Group.BODY_RIGHT,-2);
        box(parts,"hinge",Group.HINGE,-6,-314,-7,12,628,14,0xFF555C66);
        box(parts,"hinge_top",Group.HINGE,-9,-318,-9,18,13,18,0xFFABB1B8);
        box(parts,"hinge_bottom",Group.HINGE,-9,305,-9,18,13,18,0xFFABB1B8);
        box(parts,"volume_up",Group.BODY_LEFT,-325,-210,-2,4,38,6,0xFF9097A0);
        box(parts,"volume_down",Group.BODY_LEFT,-325,-156,-2,4,38,6,0xFF9097A0);
        box(parts,"power",Group.BODY_RIGHT,322,-165,-2,4,56,6,0xFF9097A0);
        box(parts,"usb_c",Group.BODY_RIGHT,153,315,-2,30,2,5,0xFF080A0F);
        for(int i=0;i<5;i++)box(parts,"speaker_"+i,Group.BODY_RIGHT,211+i*10,315,-1,4,2,3,0xFF080A0F);
        rounded(parts,"camera",Group.BODY_LEFT,-298,-286,-16,78,142,9,14,0xFF303641);
        rounded(parts,"lens_top",Group.BODY_LEFT,-290,-275,-19,58,58,4,26,0xFF080B12);
        rounded(parts,"lens_bottom",Group.BODY_LEFT,-290,-207,-19,58,58,4,26,0xFF080B12);
        rounded(parts,"lens_top_glass",Group.BODY_LEFT,-279,-264,-20,36,36,1,16,0xFF122A43);
        rounded(parts,"lens_bottom_glass",Group.BODY_LEFT,-279,-196,-20,36,36,1,16,0xFF122A43);
        rounded(parts,"flash",Group.BODY_LEFT,-216,-228,-11,17,17,3,7,0xFFFFEAD0);
        return List.copyOf(parts);
    }
    private static void body(List<Box> p,Group g,float x){
        rounded(p,g.name().toLowerCase(),g,x,-317,-6,324,634,12,22,0xFF757C86);
        rounded(p,"outer_shell",g,x+2,-315,-7,320,630,2,20,0xFF333943);
        rounded(p,"screen_glass",g,x+3,-313,6,318,626,2,20,0xFF080B10);
        rounded(p,"screen_"+g.name().toLowerCase(),g,x+12,-305,8,300,610,1,15,0xFF111B2B);
        if(g==Group.BODY_RIGHT)rounded(p,"outer_display",g,x+12,-303,-9,300,606,2,15,0xFF101B2C);
    }
    private static void box(List<Box> p,String n,Group g,float x,float y,float z,float w,float h,float d,int color){p.add(new Box(n,g,x,y,z,w,h,d,color));}
    private static void rounded(List<Box> p,String n,Group g,float x,float y,float z,float w,float h,float d,float radius,int color){
        box(p,n,g,x,y+radius,z,w,h-2*radius,d,color);
        int steps=6;float band=radius/steps;
        for(int i=0;i<steps;i++){
            float dy=radius-(i+.5F)*band,inset=radius-(float)Math.sqrt(radius*radius-dy*dy);
            box(p,n+"_top_"+i,g,x+inset,y+i*band,z,w-2*inset,band,d,color);
            box(p,n+"_bottom_"+i,g,x+inset,y+h-(i+1)*band,z,w-2*inset,band,d,color);
        }
    }
}

package com.mikasrevs.newphone;

import com.mikasrevs.newphone.client.duo.DuoAnimation;
import com.mikasrevs.newphone.client.duo.DuoLayout;
import com.mikasrevs.newphone.client.duo.DuoModelGeometry;
import java.util.*;

public final class DuoChecks {
    public static void run(){
        for(int[] view:new int[][]{{320,180},{426,300},{640,360},{1280,720},{1920,1080},{3440,1440}}){
            for(float preference:new float[]{.7F,1F,1.12F}){
                var s=DuoLayout.fit(view[0],view[1],preference);
                require(s.x(-12)>=0&&s.x(632)<=view[0],"Duo shell leaves viewport");
                require(s.y(-12)>=0&&s.y(622)<=view[1],"Duo shell leaves viewport vertically");
                var regions=new ArrayList<DuoLayout.Region>();
                for(int i=0;i<18;i++)regions.add(DuoLayout.appIcon(i));
                for(int i=0;i<4;i++)regions.add(DuoLayout.dockIcon(i));
                for(var r:regions){
                    require(r.x()+r.width()<=300||r.x()>=320,"Icon enters physical hinge");
                    require(s.inside(s.x(r.x()+r.width()/2),s.y(r.y()+r.height()/2),r),"Drawn icon cannot be clicked");
                    require(!s.inside(s.x(r.x()-15),s.y(r.y()-15),r),"Outside icon click accepted");
                }
                for(int i=0;i<regions.size();i++)for(int j=i+1;j<regions.size();j++){
                    var a=regions.get(i);var b=regions.get(j);
                    require(a.x()+a.width()<=b.x()||b.x()+b.width()<=a.x()||a.y()+a.height()<=b.y()||b.y()+b.height()<=a.y(),"App hit regions overlap");
                }
            }
        }
        require(DuoAnimation.ease(0)==0&&DuoAnimation.ease(1)==1,"Animation endpoints moved");
        float previous=-1;
        for(int i=0;i<=320;i++){float p=DuoAnimation.ease(i/320F);require(p>=previous&&p>=0&&p<=1,"Unfold animation overshoots");previous=p;}
        Set<DuoModelGeometry.Group> groups=new HashSet<>();
        for(var b:DuoModelGeometry.BOXES){groups.add(b.group());require(b.width()>0&&b.height()>0&&b.depth()>0,"Degenerate physical part");}
        require(groups.size()==3,"Missing independently animated body/hinge");
        require(DuoModelGeometry.BOXES.stream().anyMatch(b->b.name().equals("camera")),"Missing camera module");
        require(DuoModelGeometry.BOXES.stream().filter(b->b.name().startsWith("screen_body")&&!b.name().contains("_top_")&&!b.name().contains("_bottom_")).count()==2,"Missing inner displays");
        System.out.println("Duo checks passed: viewport bounds, model parts, hinge-safe icons, touch mapping and smooth motion.");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

package com.mikasrevs.newphone.client.duo;

/** One coordinate system shared by hardware rendering, app placement and touch regions. */
public final class DuoLayout {
    public static final int WIDTH=620,HEIGHT=610,RIGHT=320,APP_WIDTH=300,APPS_PER_PAGE=18;
    private DuoLayout(){}
    public record Surface(int x,int y,float scale){
        public int x(float design){return x+Math.round(design*scale);}
        public int y(float design){return y+Math.round(design*scale);}
        public int size(float design){return Math.max(1,Math.round(design*scale));}
        public boolean inside(double px,double py,Region r){return px>=x(r.x())&&px<x(r.x()+r.width())&&py>=y(r.y())&&py<y(r.y()+r.height());}
    }
    public record Region(int x,int y,int width,int height){}
    public static Surface fit(int width,int height,float preference){
        float scale=Math.min(width*.64F/644F,height*.76F/634F)*Math.max(.7F,Math.min(1.12F,preference));
        scale=Math.max(.01F,Math.min(scale,Math.min((width-16)/644F,(height-16)/680F)));
        return new Surface(Math.round((width-WIDTH*scale)/2),Math.round(height*.11F),scale);
    }
    public static Region appIcon(int index){return new Region(35+(index%6)*96,193+(index/6)*91,58,58);}
    public static Region dockIcon(int index){return new Region(129+index*104,510,58,58);}
    public static Region quickSwitch(int index){return new Region(342+(index%2)*128,437+(index/2)*66,118,52);}
    public static final Region HOME=new Region(262,594,96,16),FOLD=new Region(560,16,44,28),NOTIFICATIONS=new Region(340,55,260,46);
    public static final Region HOME_NOTIFICATIONS=new Region(366,65,219,70),UNLOCK=new Region(42,493,237,63);
}

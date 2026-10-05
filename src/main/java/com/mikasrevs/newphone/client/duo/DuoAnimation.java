package com.mikasrevs.newphone.client.duo;

/** Time based, reversible hinge movement. No camera rotation or world-view shake. */
public final class DuoAnimation {
    public static final long DURATION_MS=320;
    private static long started;
    private static float from=1,target=1;
    private static long tapAt;
    private static int tapX,tapY;
    private DuoAnimation(){}
    public static void open(){from=started==0?0:unfold();target=1;started=System.nanoTime();}
    public static void close(){from=unfold();target=0;started=System.nanoTime();}
    public static void reset(){started=0;from=target=1;tapAt=0;}
    public static void finish(){from=target;started=0;}
    public static float progress(){return started==0?1:ease((System.nanoTime()-started)/1_000_000F/DURATION_MS);}
    public static float unfold(){return from+(target-from)*progress();}
    public static boolean done(){return progress()>=1;}
    public static float ease(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static void tap(int x,int y){tapX=x;tapY=y;tapAt=System.nanoTime();}
    public static float tapProgress(){return tapAt==0?1:Math.max(0,Math.min(1,(System.nanoTime()-tapAt)/180_000_000F));}
    public static int tapX(){return tapX;}public static int tapY(){return tapY;}
}

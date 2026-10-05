package com.mikasrevs.newphone.marketplace.trade;

/** Pixel coordinates outside the phone frame, independent of phone design coordinates. */
public final class TradeChatLayout {
    private TradeChatLayout(){}
    public record Rect(int x,int y,int width,int height){
        public boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    }
    public static Rect place(int width,int height,int phoneLeft,int phoneRight){
        int margin=4,gap=6;
        int left=Math.max(0,phoneLeft-margin-gap),right=Math.max(0,width-margin-gap-phoneRight);
        boolean onRight=right>=left;int available=onRight?right:left;
        int panel=Math.min(320,available);
        return new Rect(onRight?phoneRight+gap:phoneLeft-gap-panel,margin,panel,Math.max(0,height-2*margin));
    }
}

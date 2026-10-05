package com.mikasrevs.newphone.client.zphone.gui;

import net.minecraft.client.gui.GuiGraphics;

/** Small geometric controls that stay consistent with the phone's scale. */
final class PhoneVoiceVisuals {
    static final int BLUE = 0xFF0A84FF, RED = 0xFFFF453A, TEXT = 0xFFF5F5F7, SECONDARY = 0xFF98989F;
    private PhoneVoiceVisuals() { }

    static void microphone(AbstractZPhoneScreen s, GuiGraphics g, float cx, float cy, float scale, int color) {
        pill(s,g,cx-3*scale,cy-8*scale,cx+3*scale,cy+3*scale,3*scale,color);
        // The lower U is separate from the capsule, like the native microphone symbol.
        pill(s,g,cx-6*scale,cy-1*scale,cx-4.5f*scale,cy+5*scale,scale,color);
        pill(s,g,cx+4.5f*scale,cy-1*scale,cx+6*scale,cy+5*scale,scale,color);
        pill(s,g,cx-4.5f*scale,cy+4*scale,cx+4.5f*scale,cy+6*scale,scale,color);
        pill(s,g,cx-scale,cy+5*scale,cx+scale,cy+9*scale,scale,color);
        pill(s,g,cx-4*scale,cy+8*scale,cx+4*scale,cy+9.5f*scale,scale,color);
    }

    static void play(AbstractZPhoneScreen s, GuiGraphics g, float cx, float cy, float scale, boolean stop, int color) {
        if (stop) { pill(s,g,cx-4*scale,cy-4*scale,cx+4*scale,cy+4*scale,scale,color); return; }
        for(int i=-6;i<=6;i++) {
            float width=(6-Math.abs(i))*1.4f;
            g.fill(s.x(cx-3*scale),s.y(cy+i*scale),s.x(cx+(-3+Math.max(1,width))*scale),s.y(cy+(i+1)*scale),color);
        }
    }

    static void send(AbstractZPhoneScreen s, GuiGraphics g, float cx, float cy, int color) {
        pill(s,g,cx-1,cy-7,cx+1,cy+8,1,color);
        for(int i=0;i<7;i++) {
            g.fill(s.x(cx-i-1),s.y(cy-7+i),s.x(cx-i+1),s.y(cy-5+i),color);
            g.fill(s.x(cx+i-1),s.y(cy-7+i),s.x(cx+i+1),s.y(cy-5+i),color);
        }
    }

    static void trash(AbstractZPhoneScreen s, GuiGraphics g, float cx, float cy, int color) {
        pill(s,g,cx-5,cy-3,cx-3.5f,cy+7,1,color);
        pill(s,g,cx+3.5f,cy-3,cx+5,cy+7,1,color);
        pill(s,g,cx-4,cy+6,cx+4,cy+8,1,color);
        pill(s,g,cx-7,cy-6,cx+7,cy-4,1,color);
        pill(s,g,cx-3,cy-9,cx+3,cy-7,1,color);
    }

    static void waveform(AbstractZPhoneScreen s, GuiGraphics g, float left, float centerY, float width,
                         float height, float[] samples, float progress, int active, int inactive) {
        int count=Math.max(1,Math.min(44,(int)(width/4)));
        float step=width/count;
        for(int i=0;i<count;i++) {
            float value=samples.length==0?0:samples[Math.min(samples.length-1,i*samples.length/count)];
            float bar=Math.max(2,Math.min(1,value)*height);
            int color=progress<0||i/(float)count<progress?active:inactive;
            pill(s,g,left+i*step,centerY-bar/2,left+i*step+Math.max(1.5f,step*.44f),centerY+bar/2,1.1f,color);
        }
    }

    static void pill(AbstractZPhoneScreen s,GuiGraphics g,float l,float t,float r,float b,float radius,int color) {
        s.drawRoundedRect(g,s.x(l),s.y(t),s.x(r),s.y(b),Math.max(1,s.size(radius)),color);
    }

    static String time(long millis) {
        long seconds=Math.max(0,millis)/1000;
        return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
}

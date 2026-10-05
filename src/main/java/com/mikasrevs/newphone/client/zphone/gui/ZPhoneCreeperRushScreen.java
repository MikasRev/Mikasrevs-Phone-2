package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ZPhoneCreeperRushScreen extends AbstractZPhoneScreen {
    private static final float GROUND_Y=451F,PLAYER_X=61F,PLAYER_SIZE=30F;
    private final List<Obstacle> obstacles=new ArrayList<>();private final Random random=new Random();
    private float playerY=GROUND_Y-PLAYER_SIZE,velocityY;private int spawnTicks=28,score,best;private boolean gameOver,started;
    public ZPhoneCreeperRushScreen(){super(Component.literal("Creeper Rush"));}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){renderBackground(g);g.fill(x(3),y(8),x(297),y(606),0xFF0B1020);fillCircle(g,x(252),y(96),size(96),0x273D55FF);fillCircle(g,x(44),y(418),size(115),0x1C7F5DFF);drawStatusBar(g);drawBackButton(g,PhoneSettingsStore.text("Geri","Back"));PhoneModernText.drawColored(g,"Creeper Rush",x(61),y(52),size(18),0xFFFFFFFF);PhoneModernText.drawColored(g,PhoneSettingsStore.text("Skor ", "Score ")+score,x(24),y(88),size(9),0xFFE7E8FF);PhoneModernText.drawColored(g,PhoneSettingsStore.text("En İyi ", "Best ")+best,x(213),y(88),size(9),0xFFB5A9F8);drawRoundedRect(g,x(20),y(105),x(280),y(486),size(22),0xFF151B32);g.enableScissor(x(22),y(107),x(278),y(484));drawWorld(g);g.disableScissor();drawRoundedRect(g,x(65),y(508),x(235),y(556),size(24),gameOver?0xFFE75555:0xFF7552DB);PhoneModernText.drawCenteredColored(g,gameOver?PhoneSettingsStore.text("Yeniden Başla","Restart"):started?PhoneSettingsStore.text("Zıpla","Jump"):PhoneSettingsStore.text("Başla","Start"),x(150),y(522),size(12),0xFFFFFFFF);PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Boşluk veya ekrana dokun","Press SPACE or tap the screen"),x(150),y(566),size(7),0xFF9EA8C4);drawHomeIndicator(g);drawFrame(g);}
    private void drawWorld(GuiGraphics g){int left=22,top=107,right=278,bottom=484;for(int py=top;py<bottom;py+=3){float t=(py-top)/(float)(bottom-top);int r=Math.round(25+16*t),gr=Math.round(34+26*t),b=Math.round(67+35*t);g.fill(x(left),y(py),x(right),y(Math.min(bottom,py+3)),0xFF000000|(r<<16)|(gr<<8)|b);}fillCircle(g,x(230),y(160),size(32),0x36C5E0FF);g.fill(x(22),y((int)GROUND_Y),x(278),y(484),0xFF19251C);g.fill(x(22),y((int)GROUND_Y),x(278),y((int)GROUND_Y+7),0xFF58B85D);for(int i=0;i<9;i++){int gx=28+i*32-(score*3%32);g.fill(x(gx),y(466),x(gx+18),y(470),0xFF243429);}drawRunner(g,Math.round(PLAYER_X),Math.round(playerY));for(Obstacle o:obstacles)drawCreeper(g,Math.round(o.x),Math.round(GROUND_Y-o.height),Math.round(o.width),Math.round(o.height));if(!started||gameOver){drawRoundedRect(g,x(54),y(242),x(246),y(340),size(22),0xDC090D18);PhoneModernText.drawCenteredColored(g,gameOver?PhoneSettingsStore.text("Creeper seni yakaladı!","The Creeper caught you!"):PhoneSettingsStore.text("Creeperlardan kaç","Run from the Creepers"),x(150),y(267),size(14),0xFFFFFFFF);PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Zamanlamanı iyi yap","Time your jumps carefully"),x(150),y(303),size(8),0xFFB2BED7);}}
    private void drawRunner(GuiGraphics g,int px,int py){drawRoundedRect(g,x(px),y(py),x(px+30),y(py+30),size(7),0xFF4C9DFF);g.fill(x(px+5),y(py+8),x(px+10),y(py+13),0xFFFFFFFF);g.fill(x(px+20),y(py+8),x(px+25),y(py+13),0xFFFFFFFF);g.fill(x(px+7),y(py+20),x(px+23),y(py+23),0xFF12243A);g.fill(x(px+4),y(py+29),x(px+12),y(py+35),0xFF2A66B4);g.fill(x(px+18),y(py+29),x(px+26),y(py+35),0xFF2A66B4);}
    private void drawCreeper(GuiGraphics g,int px,int py,int w,int h){drawRoundedRect(g,x(px),y(py),x(px+w),y(py+h),size(5),0xFF69D35C);int u=Math.max(2,w/7);g.fill(x(px+u),y(py+u),x(px+3*u),y(py+3*u),0xFF172117);g.fill(x(px+w-3*u),y(py+u),x(px+w-u),y(py+3*u),0xFF172117);g.fill(x(px+3*u),y(py+3*u),x(px+w-3*u),y(py+5*u),0xFF172117);}
    private void jumpOrStart(){if(gameOver){resetGame();started=true;velocityY=-12.5F;return;}if(!started)started=true;if(playerY>=GROUND_Y-PLAYER_SIZE-1F)velocityY=-12.5F;}
    private void resetGame(){obstacles.clear();playerY=GROUND_Y-PLAYER_SIZE;velocityY=0;spawnTicks=28;score=0;gameOver=false;}
    private void updateGame(){velocityY+=1.05F;playerY+=velocityY;if(playerY>=GROUND_Y-PLAYER_SIZE){playerY=GROUND_Y-PLAYER_SIZE;velocityY=0;}if(--spawnTicks<=0){float w=25+random.nextInt(12),h=31+random.nextInt(18);obstacles.add(new Obstacle(282F,w,h));spawnTicks=Math.max(24,42+random.nextInt(22)-score/6);}Iterator<Obstacle> it=obstacles.iterator();while(it.hasNext()){Obstacle o=it.next();o.x-=Math.min(9F,5.2F+score*0.045F);if(o.x+o.width<22F){it.remove();score++;best=Math.max(best,score);continue;}if(intersects(PLAYER_X+3,playerY+3,PLAYER_SIZE-6,PLAYER_SIZE-3,o.x+2,GROUND_Y-o.height+2,o.width-4,o.height-2)){gameOver=true;best=Math.max(best,score);return;}}}
    private boolean intersects(float ax,float ay,float aw,float ah,float bx,float by,float bw,float bh){return ax<bx+bw&&ax+aw>bx&&ay<by+bh&&ay+ah>by;}
    @Override public void tick(){super.tick();if(started&&!gameOver)updateGame();}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button!=0)return super.mouseClicked(mx,my,button);if(handleBackButton(mx,my))return true;if(inside(mx,my,22,107,278,484)||inside(mx,my,65,508,235,556)){jumpOrStart();return true;}return super.mouseClicked(mx,my,button);}
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){if(keyCode==GLFW.GLFW_KEY_SPACE||keyCode==GLFW.GLFW_KEY_UP||keyCode==GLFW.GLFW_KEY_W){jumpOrStart();return true;}if(keyCode==GLFW.GLFW_KEY_ESCAPE){PhoneNavigationState.backOrHome(minecraft);return true;}return super.keyPressed(keyCode,scanCode,modifiers);}
    private static final class Obstacle{float x;final float width,height;Obstacle(float x,float w,float h){this.x=x;this.width=w;this.height=h;}}
}

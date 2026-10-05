package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ZPhoneBlockSnakeScreen extends AbstractZPhoneScreen {
    private static final int COLS=13, ROWS=18, BOARD_LEFT=33, BOARD_TOP=112, CELL=18;
    private final Deque<CellPos> snake=new ArrayDeque<>();
    private final Random random=new Random();
    private Direction direction=Direction.RIGHT, queuedDirection=Direction.RIGHT;
    private CellPos food; private int moveTicks,score,highScore; private boolean gameOver,paused,won;
    public ZPhoneBlockSnakeScreen(){super(Component.literal("Block Snake"));restart();}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        renderBackground(g);g.fill(x(3),y(8),x(297),y(606),0xFF07150E);fillCircle(g,x(247),y(76),size(88),0x252FCF78);drawStatusBar(g);drawBackButton(g,PhoneSettingsStore.text("Geri","Back"));
        PhoneModernText.drawColored(g,"Block Snake",x(61),y(52),size(18),0xFFFFFFFF);PhoneModernText.drawColored(g,PhoneSettingsStore.text("Skor ", "Score ")+score,x(25),y(88),size(9),0xFFDDF8E8);PhoneModernText.drawColored(g,PhoneSettingsStore.text("En İyi ", "Best ")+highScore,x(212),y(88),size(9),0xFF9CCDB1);
        drawRoundedRect(g,x(25),y(104),x(275),y(446),size(18),0xFF10281B);drawRoundedRect(g,x(31),y(110),x(269),y(440),size(14),0xFF091A12);
        for(int col=0;col<=COLS;col++){int gx=BOARD_LEFT+col*CELL;g.fill(x(gx),y(BOARD_TOP),x(gx)+size(1),y(BOARD_TOP+ROWS*CELL),0x1629A866);}for(int row=0;row<=ROWS;row++){int gy=BOARD_TOP+row*CELL;g.fill(x(BOARD_LEFT),y(gy),x(BOARD_LEFT+COLS*CELL),y(gy)+size(1),0x1629A866);}
        if(food!=null){int fx=BOARD_LEFT+food.x*CELL+4,fy=BOARD_TOP+food.y*CELL+4;fillCircle(g,x(fx+5),y(fy+5),size(5),0xFFFFD45C);fillCircle(g,x(fx+7),y(fy+3),size(2),0xFFFFFFFF);}
        int idx=0;for(CellPos p:snake){int px=BOARD_LEFT+p.x*CELL+2,py=BOARD_TOP+p.y*CELL+2;int c=idx==0?0xFF70F0A7:(idx%2==0?0xFF27C874:0xFF31D982);drawRoundedRect(g,x(px),y(py),x(px+14),y(py+14),size(4),c);if(idx==0){g.fill(x(px+4),y(py+4),x(px+6),y(py+6),0xFF092014);g.fill(x(px+9),y(py+4),x(px+11),y(py+6),0xFF092014);}idx++;}
        drawPad(g,124,458,176,493,"▲",mouseX,mouseY);drawPad(g,66,500,118,540,"◀",mouseX,mouseY);drawPad(g,124,500,176,540,paused?"▶":"Ⅱ",mouseX,mouseY);drawPad(g,182,500,234,540,"▶",mouseX,mouseY);drawPad(g,124,547,176,579,"▼",mouseX,mouseY);
        if(paused||gameOver){drawRoundedRect(g,x(55),y(220),x(245),y(332),size(22),0xEB07110B);PhoneModernText.drawCenteredColored(g,won?PhoneSettingsStore.text("Kazandın","You won"):gameOver?PhoneSettingsStore.text("Oyun Bitti","Game Over"):PhoneSettingsStore.text("Duraklatıldı","Paused"),x(150),y(245),size(18),0xFFFFFFFF);PhoneModernText.drawCenteredColored(g,gameOver?PhoneSettingsStore.text("Ortaya dokun ve yeniden başla","Tap the center to restart"):PhoneSettingsStore.text("Devam etmek için ortaya dokun","Tap the center to continue"),x(150),y(283),size(7),0xFF9FB6A8);}
        drawHomeIndicator(g);drawFrame(g);
    }
    private void drawPad(GuiGraphics g,int l,int t,int r,int b,String s,int mx,int my){drawRoundedRect(g,x(l),y(t),x(r),y(b),size(14),inside(mx,my,l,t,r,b)?0xFF2D6D49:0xFF1C4931);PhoneModernText.drawCenteredColored(g,s,x((l+r)/2),y(t+9),size(13),0xFFFFFFFF);}
    private void restart(){snake.clear();snake.addFirst(new CellPos(6,9));snake.addLast(new CellPos(5,9));snake.addLast(new CellPos(4,9));direction=queuedDirection=Direction.RIGHT;score=moveTicks=0;gameOver=paused=won=false;placeFood();}
    private void placeFood(){
        var free=new java.util.ArrayList<CellPos>();
        for(int y=0;y<ROWS;y++)for(int x=0;x<COLS;x++){
            CellPos cell=new CellPos(x,y);if(!snake.contains(cell))free.add(cell);
        }
        if(free.isEmpty()){food=null;won=gameOver=true;return;}
        food=free.get(random.nextInt(free.size()));
    }
    private void step(){
        direction=queuedDirection;CellPos h=snake.peekFirst();if(h==null)return;
        CellPos n=new CellPos(h.x+direction.dx,h.y+direction.dy);
        boolean eating=n.equals(food);
        boolean collision=snake.contains(n)&&(eating||!n.equals(snake.peekLast()));
        if(n.x<0||n.x>=COLS||n.y<0||n.y>=ROWS||collision){gameOver=true;highScore=Math.max(highScore,score);return;}
        snake.addFirst(n);
        if(eating){score++;highScore=Math.max(highScore,score);placeFood();}else snake.removeLast();
    }
    private void queue(Direction n){if(!direction.opposite(n))queuedDirection=n;}
    @Override public void tick(){super.tick();if(paused||gameOver)return;if(++moveTicks>=Math.max(2,6-score/5)){moveTicks=0;step();}}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button!=0)return super.mouseClicked(mx,my,button);if(handleBackButton(mx,my))return true;if(gameOver&&inside(mx,my,55,220,245,332)){restart();return true;}if(paused&&inside(mx,my,55,220,245,332)){paused=false;return true;}if(inside(mx,my,124,458,176,493))queue(Direction.UP);else if(inside(mx,my,66,500,118,540))queue(Direction.LEFT);else if(inside(mx,my,124,500,176,540))paused=!paused;else if(inside(mx,my,182,500,234,540))queue(Direction.RIGHT);else if(inside(mx,my,124,547,176,579))queue(Direction.DOWN);else return super.mouseClicked(mx,my,button);return true;}
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){switch(keyCode){case GLFW.GLFW_KEY_UP,GLFW.GLFW_KEY_W->queue(Direction.UP);case GLFW.GLFW_KEY_DOWN,GLFW.GLFW_KEY_S->queue(Direction.DOWN);case GLFW.GLFW_KEY_LEFT,GLFW.GLFW_KEY_A->queue(Direction.LEFT);case GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_KEY_D->queue(Direction.RIGHT);case GLFW.GLFW_KEY_SPACE->{if(gameOver)restart();else paused=!paused;}case GLFW.GLFW_KEY_ESCAPE->{PhoneNavigationState.backOrHome(minecraft);return true;}default->{return super.keyPressed(keyCode,scanCode,modifiers);}}return true;}
    private record CellPos(int x,int y){}
    private enum Direction{UP(0,-1),DOWN(0,1),LEFT(-1,0),RIGHT(1,0);final int dx,dy;Direction(int dx,int dy){this.dx=dx;this.dy=dy;}boolean opposite(Direction o){return dx+o.dx==0&&dy+o.dy==0;}}
}

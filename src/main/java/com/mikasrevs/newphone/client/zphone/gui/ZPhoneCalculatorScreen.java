package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Native 1.20.1 MPhone calculator. */
public final class ZPhoneCalculatorScreen extends AbstractZPhoneScreen {
    private static final List<Button> BUTTONS = List.of(
            new Button("AC",26,235,80,283,Action.CLEAR), new Button("+/−",91,235,145,283,Action.SIGN),
            new Button("%",156,235,210,283,Action.PERCENT), new Button("÷",221,235,275,283,Action.DIV),
            new Button("7",26,296,80,344,Action.D7), new Button("8",91,296,145,344,Action.D8), new Button("9",156,296,210,344,Action.D9), new Button("×",221,296,275,344,Action.MUL),
            new Button("4",26,357,80,405,Action.D4), new Button("5",91,357,145,405,Action.D5), new Button("6",156,357,210,405,Action.D6), new Button("−",221,357,275,405,Action.SUB),
            new Button("1",26,418,80,466,Action.D1), new Button("2",91,418,145,466,Action.D2), new Button("3",156,418,210,466,Action.D3), new Button("+",221,418,275,466,Action.ADD),
            new Button("0",26,479,145,527,Action.D0), new Button(",",156,479,210,527,Action.DECIMAL), new Button("=",221,479,275,527,Action.EQUALS)
    );
    private String display="0", expression="";
    private double accumulator;
    private Operator pending=Operator.NONE;
    private boolean newNumber=true, error;

    public ZPhoneCalculatorScreen(){ super(Component.translatable("screen.newphone.app.calculator")); }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float pt){
        renderBackground(g); g.fill(x(3),y(8),x(297),y(606),0xFF080B10); drawStatusBar(g);
        drawText(g, PhoneSettingsStore.text("Hesap Makinesi","Calculator"),22,58,20,0xFFFFFFFF,false);
        drawRoundedRect(g,x(20),y(100),x(280),y(215),size(17),0xFF111720);
        drawText(g, expression.isBlank()?" ":fit(expression,30),31,119,8,0xFF778493,false);
        drawText(g, display,267,155,30,error?0xFFFF6868:0xFFFFFFFF,true);
        for(Button b:BUTTONS) drawButton(g,b,mouseX,mouseY);
        drawHomeIndicator(g); drawFrame(g);
    }

    private void drawButton(GuiGraphics g,Button b,int mx,int my){
        boolean hover=inside(mx,my,b.l,b.t,b.r,b.b); boolean special=b.a==Action.CLEAR||b.a==Action.SIGN||b.a==Action.PERCENT;
        int color=special?(hover?0xFFB9BEC5:0xFF9FA4AA):(b.a.operator?(hover?0xFFFFA73B:0xFFFF9500):(hover?0xFF39414C:0xFF262B31));
        drawRoundedRect(g,x(b.l),y(b.t),x(b.r),y(b.b),size(24),color);
        drawText(g,b.label,(b.l+b.r)/2.0F,b.t+14,15,special?0xFF111317:0xFFFFFFFF,true);
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0){ for(Button b:BUTTONS) if(inside(mx,my,b.l,b.t,b.r,b.b)){perform(b.a);return true;} }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key>=GLFW.GLFW_KEY_0&&key<=GLFW.GLFW_KEY_9){digit(key-GLFW.GLFW_KEY_0);return true;}
        if(key>=GLFW.GLFW_KEY_KP_0&&key<=GLFW.GLFW_KEY_KP_9){digit(key-GLFW.GLFW_KEY_KP_0);return true;}
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){equalsResult();return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE){PhoneNavigationState.backOrHome(minecraft);return true;}
        return super.keyPressed(key,scan,mods);
    }
    private void perform(Action a){switch(a){case D0,D1,D2,D3,D4,D5,D6,D7,D8,D9->digit(a.digit);case DECIMAL->decimal();case CLEAR->clear();case SIGN->{if(!error&&!"0".equals(display))display=display.startsWith("-")?display.substring(1):"-"+display;}case PERCENT->{if(!error){display=format(value()/100.0);newNumber=true;}}case ADD->choose(Operator.ADD);case SUB->choose(Operator.SUB);case MUL->choose(Operator.MUL);case DIV->choose(Operator.DIV);case EQUALS->equalsResult();}}
    private void digit(int d){if(error||newNumber){display=Integer.toString(d);newNumber=false;error=false;return;}if(display.replace("-","").length()<12)display="0".equals(display)?Integer.toString(d):display+d;}
    private void decimal(){if(error||newNumber){display="0.";newNumber=false;error=false;return;}if(!display.contains("."))display+=".";}
    private void choose(Operator op){if(error)return;if(pending!=Operator.NONE&&!newNumber)apply();else accumulator=value();pending=op;expression=format(accumulator)+" "+op.symbol;newNumber=true;}
    private void equalsResult(){if(error||pending==Operator.NONE)return;apply();expression="";pending=Operator.NONE;newNumber=true;}
    private void apply(){double rhs=value();double result=switch(pending){case ADD->accumulator+rhs;case SUB->accumulator-rhs;case MUL->accumulator*rhs;case DIV->rhs==0.0?Double.NaN:accumulator/rhs;default->rhs;};if(!Double.isFinite(result)){display="Error";error=true;pending=Operator.NONE;expression="";return;}accumulator=result;display=format(result);}
    private double value(){try{return Double.parseDouble(display);}catch(NumberFormatException e){return 0.0;}}
    private void clear(){display="0";expression="";accumulator=0;pending=Operator.NONE;newNumber=true;error=false;}
    private static String format(double v){BigDecimal d=BigDecimal.valueOf(v).setScale(8,RoundingMode.HALF_UP).stripTrailingZeros();String s=d.toPlainString();return s.length()>14?String.format(java.util.Locale.ROOT,"%.6g",v):s;}
    private static String fit(String s,int max){return s.length()<=max?s:s.substring(s.length()-max);}
    private record Button(String label,int l,int t,int r,int b,Action a){}
    private enum Operator{NONE(""),ADD("+"),SUB("−"),MUL("×"),DIV("÷");final String symbol;Operator(String s){symbol=s;}}
    private enum Action{D0(0,false),D1(1,false),D2(2,false),D3(3,false),D4(4,false),D5(5,false),D6(6,false),D7(7,false),D8(8,false),D9(9,false),DECIMAL(-1,false),CLEAR(-1,false),SIGN(-1,false),PERCENT(-1,false),ADD(-1,true),SUB(-1,true),MUL(-1,true),DIV(-1,true),EQUALS(-1,true);final int digit;final boolean operator;Action(int d,boolean o){digit=d;operator=o;}}
}

package com.mikasrevs.newphone;

import com.mikasrevs.newphone.client.zphone.PhoneBrowserInput;
import com.mikasrevs.newphone.client.zphone.PhoneYouTubeState;
import java.util.*;

/** Checks the production input/ownership logic without Chromium, network access or an OpenGL window. */
final class PhoneYouTubeChecks {
    static void run(){
        var input=new PhoneBrowserInput();var buttons=new ArrayList<Integer>();var keys=new ArrayList<Integer>();
        require(!input.releaseButton(0),"phone navigation forwarded an unmatched mouse release");
        require(!input.pressButton(-1)&&!input.pressButton(8),"unsupported browser button accepted");
        require(input.pressButton(0)&&!input.pressButton(0)&&input.dragging(),"held button was not tracked");
        input.pressButton(1);input.pressKey(341);input.pressKey(65);input.pressKey(65);
        require(input.releaseButton(0)&&!input.releaseButton(0)&&input.dragging(),"button released twice");
        require(input.releaseKey(65)&&!input.releaseKey(65),"key released twice");
        input.releaseAll(buttons::add,keys::add);
        require(buttons.equals(List.of(1))&&keys.equals(List.of(341))&&!input.dragging(),"screen close did not release remaining input");
        input.releaseAll(buttons::add,keys::add);require(buttons.size()==1&&keys.size()==1,"repeated close released input again");
        for(boolean closed:List.of(false,true))for(boolean screen:List.of(false,true))
            for(boolean hovered:List.of(false,true))for(boolean focused:List.of(false,true))
                require(PhoneBrowserInput.cursorAllowed(closed,screen,hovered,focused)==(!closed&&screen&&hovered&&focused),"browser cursor escaped its active screen");
        input.pressButton(0);
        try{input.releaseAll(button->{throw new IllegalStateException("native failure");},key->{});}
        catch(IllegalStateException expected){}
        require(!input.dragging(),"failed native cleanup retained held input");
        var first=new Browser();var second=new Browser();
        PhoneYouTubeState.attach(first);PhoneYouTubeState.attach(first);require(first.closed==0,"reattaching same browser closed it");
        PhoneYouTubeState.attach(second);require(first.closed==1&&PhoneYouTubeState.browser()==second,"browser replacement leaked old instance");
        require(PhoneYouTubeState.enterModelMode(second)&&!second.focused&&second.width==1280&&second.height==720,"held mode did not release browser focus/resize");
        PhoneYouTubeState.stop();require(second.closed==1&&!PhoneYouTubeState.isModelMode()&&PhoneYouTubeState.browser()==null,"closing held video retained browser");
        PhoneYouTubeState.stop();require(second.closed==1,"stop closed browser twice");
        var failing=new Browser();failing.fail=true;PhoneYouTubeState.attach(failing);
        try{PhoneYouTubeState.stop();}catch(IllegalStateException expected){}
        require(PhoneYouTubeState.browser()==null&&!PhoneYouTubeState.isModelMode(),"native close failure retained session");
        PhoneYouTubeState.stop();
        System.out.println("YouTube checks passed: scoped cursor policy, mouse/key release, held mode, browser replacement and close-failure cleanup.");
    }
    private static final class Browser implements PhoneYouTubeState.BrowserHandle {
        int closed,width,height;boolean focused=true,fail;
        public int textureId(){return closed==0?7:0;}
        public void setFocus(boolean value){focused=value;}
        public void resize(int w,int h){width=w;height=h;}
        public void close(){closed++;if(fail)throw new IllegalStateException("native close");}
        public net.minecraft.client.gui.screens.Screen createScreen(){return null;}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

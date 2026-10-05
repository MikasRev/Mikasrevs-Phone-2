package com.mikasrevs.newphone.client.zphone;

import java.util.*;
import java.util.function.IntConsumer;

/** Tracks only events actually sent to the page, so phone controls cannot leave a held button/key. */
public final class PhoneBrowserInput {
    private final Set<Integer> buttons=new HashSet<>(),keys=new HashSet<>();
    public boolean pressButton(int button){return button>=0&&button<=2&&buttons.add(button);}
    public boolean releaseButton(int button){return buttons.remove(button);}
    public boolean dragging(){return !buttons.isEmpty();}
    public void pressKey(int key){keys.add(key);}
    public boolean releaseKey(int key){return keys.remove(key);}
    public void releaseAll(IntConsumer buttonRelease,IntConsumer keyRelease){
        var oldButtons=Set.copyOf(buttons);var oldKeys=Set.copyOf(keys);buttons.clear();keys.clear();
        oldButtons.forEach(buttonRelease::accept);oldKeys.forEach(keyRelease::accept);
    }
    public static boolean cursorAllowed(boolean closed,boolean currentScreen,boolean hovered,boolean focused){
        return !closed&&currentScreen&&hovered&&focused;
    }
}

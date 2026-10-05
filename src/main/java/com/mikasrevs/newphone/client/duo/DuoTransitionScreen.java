package com.mikasrevs.newphone.client.duo;

import com.mikasrevs.newphone.client.zphone.*;
import com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Input is withheld until the physical display finishes unfolding; app logic isn't wrapped or cloned. */
public final class DuoTransitionScreen extends AbstractZPhoneScreen {
    private final Supplier<Screen> next;
    private final boolean closing;
    public DuoTransitionScreen(Supplier<Screen> next){super(Component.literal("iPhone Duo"));this.next=next;closing=next==null;if(closing)DuoAnimation.close();else DuoAnimation.open();if(!com.mikasrevs.newphone.client.settings.PhoneSettingsStore.animationsEnabled())DuoAnimation.finish();}
    public static void close(Minecraft mc){if(mc!=null&&!(mc.screen instanceof DuoTransitionScreen)){ZPhoneClient.markPhoneClosed();PhoneNavigationState.clear();mc.setScreen(new DuoTransitionScreen(null));}}
    @Override protected void init(){super.init();if(closing)ZPhoneClient.markPhoneClosed();}
    @Override public void tick(){super.tick();if(DuoAnimation.done()){if(closing){PhoneNavigationState.clear();ZPhoneHeldModelState.turnOffHeldPhone();minecraft.setScreen(null);}else{PhoneNavigationState.clear();minecraft.setScreen(next.get());}}}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);}
    @Override public boolean mouseClicked(double x,double y,int b){return true;}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return true;}
    @Override public boolean mouseReleased(double x,double y,int b){return true;}
    @Override public boolean charTyped(char c,int modifiers){return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_ESCAPE)onClose();return true;}
    @Override public void onClose(){if(!closing){ZPhoneClient.markPhoneClosed();PhoneNavigationState.clear();minecraft.setScreen(new DuoTransitionScreen(null));}}
}

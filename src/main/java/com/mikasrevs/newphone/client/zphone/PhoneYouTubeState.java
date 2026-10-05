package com.mikasrevs.newphone.client.zphone;

import com.mikasrevs.newphone.NewPhone;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Owns an optional browser without linking the base phone mod to MCEF classes. */
public final class PhoneYouTubeState {
    private static final int HELD_BROWSER_WIDTH = 1280;
    private static final int HELD_BROWSER_HEIGHT = 720;
    private static final ResourceLocation BROWSER_TEXTURE =
            new ResourceLocation(NewPhone.MOD_ID, "dynamic/youtube_browser");

    private static BrowserHandle browser;
    private static boolean modelMode;
    private static boolean textureRegistered;

    private PhoneYouTubeState() {
    }

    /** Implemented only by the MCEF-backed screen, which is loaded on demand. */
    public interface BrowserHandle {
        int textureId();
        void setFocus(boolean focused);
        void resize(int width, int height);
        void close();
        Screen createScreen();
    }

    public static BrowserHandle browser() {
        return browser;
    }

    public static boolean isModelMode() {
        return modelMode && browser != null;
    }

    public static int textureId() {
        return browser == null ? 0 : browser.textureId();
    }

    public static RenderType renderType() {
        return BrowserRenderType.TYPE;
    }

    /** Browser ownership/input can be initialized without initializing Minecraft's renderer. */
    private static final class BrowserRenderType {
        private static final RenderType TYPE=RenderType.entityCutoutNoCull(BROWSER_TEXTURE,false);
    }

    public static void attach(BrowserHandle newBrowser) {
        if (newBrowser == null) return;
        if (browser != null && browser != newBrowser) browser.close();
        browser = newBrowser;
        modelMode = false;
        ensureTextureRegistered();
    }

    public static boolean enterModelMode(BrowserHandle activeBrowser) {
        if (activeBrowser == null) return false;
        attach(activeBrowser);
        activeBrowser.setFocus(false);
        activeBrowser.resize(HELD_BROWSER_WIDTH, HELD_BROWSER_HEIGHT);
        modelMode = true;
        return true;
    }

    public static void reopen(Minecraft minecraft) {
        if (!isModelMode() || minecraft == null) return;
        modelMode = false;
        Screen screen = browser.createScreen();
        if (screen == null) {
            stop();
            return;
        }
        minecraft.setScreen(screen);
    }

    public static void stop() {
        modelMode = false;
        BrowserHandle old=browser;
        browser=null;
        if(old!=null)old.close();
    }

    private static void ensureTextureRegistered() {
        if (textureRegistered) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        minecraft.getTextureManager().register(BROWSER_TEXTURE, new BrowserTexture());
        textureRegistered = true;
    }

    /** The optional browser owns the OpenGL texture; Minecraft only binds its current ID. */
    private static final class BrowserTexture extends AbstractTexture {
        @Override
        public int getId() {
            return PhoneYouTubeState.textureId();
        }

        @Override
        public void load(ResourceManager resourceManager) throws IOException {
        }

        @Override
        public void releaseId() {
        }

        @Override
        public void close() {
        }
    }
}

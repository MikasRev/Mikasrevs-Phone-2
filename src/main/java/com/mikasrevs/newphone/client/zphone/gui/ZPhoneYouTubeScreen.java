package com.mikasrevs.newphone.client.zphone.gui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneYouTubeState;
import com.mikasrevs.newphone.client.zphone.PhoneBrowserInput;
import org.cef.misc.CefCursorType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A client-side Chromium surface inside the regular portrait MPhone shell. */
public final class ZPhoneYouTubeScreen extends AbstractZPhoneScreen {
    private static final String YOUTUBE_URL = "https://www.youtube.com/";

    private MCEFBrowser browser;
    private McefBrowserHandle browserHandle;
    private boolean waitingForMcef;
    private boolean initializationFailed;
    private boolean closed;
    private int initializationGeneration;
    private final PhoneBrowserInput input=new PhoneBrowserInput();
    private boolean webHovered;
    private int browserLeft;
    private int browserTop;
    private int browserWidth;
    private int browserHeight;
    private int modelCenterX;
    private int modelCenterY;
    private int modelRadius;

    public ZPhoneYouTubeScreen() {
        super(Component.literal("YouTube"));
    }

    @Override
    protected void init() {
        super.init();
        closed=false;
        resetCursor();
        updateLayout();
        if (browser == null && PhoneYouTubeState.browser() instanceof McefBrowserHandle existing) {
            browserHandle = existing;
            browser = existing.browser();
            browser.setFocus(true);
        }
        if (browser != null) {
            resizeBrowser();
            return;
        }
        initializeBrowserWhenReady();
    }

    private void initializeBrowserWhenReady() {
        if (MCEF.isInitialized()) {
            createBrowser();
            return;
        }
        if (waitingForMcef) return;
        waitingForMcef = true;
        int generation=initializationGeneration;
        MCEF.scheduleForInit(success -> Minecraft.getInstance().execute(() -> {
            if(generation!=initializationGeneration)return;
            waitingForMcef = false;
            if (closed || Minecraft.getInstance().screen != this) return;
            if (!success) {
                initializationFailed = true;
                return;
            }
            createBrowser();
        }));
    }

    private void createBrowser() {
        if (closed || browser != null || !MCEF.isInitialized()) return;
        initializationFailed = false;
        try {
            browser = MCEF.createBrowser(YOUTUBE_URL, false, physicalWidth(), physicalHeight());
            if(browser==null)throw new IllegalStateException("MCEF returned no browser");
            browserHandle = new McefBrowserHandle(browser);
            browser.setFocus(true);
            PhoneYouTubeState.attach(browserHandle);
        } catch(RuntimeException|LinkageError failure) {
            initializationFailed=true;
            if(browserHandle!=null)browserHandle.close();
            else if(browser!=null)browser.close();
            browser=null;browserHandle=null;
            resetCursor();
            com.mojang.logging.LogUtils.getLogger().warn("YouTube browser initialization failed",failure);
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        updateLayout();
        resizeBrowser();
    }

    private void updateLayout() {
        // Match every other app: the site fills the regular portrait glass.
        browserLeft = x(3);
        // Keep the web page below the phone status/island area, otherwise the
        // top YouTube controls are hidden behind the physical phone UI.
        browserTop = y(40);
        browserWidth = Math.max(1, x(297) - browserLeft);
        browserHeight = Math.max(1, y(590) - browserTop);
        modelCenterX = x(280);
        modelCenterY = y(305);
        modelRadius = size(17);
    }

    private void resizeBrowser() {
        if (browser != null && browserWidth > 0 && browserHeight > 0) {
            browser.resize(physicalWidth(), physicalHeight());
        }
    }

    @Override
    public void tick(){
        super.tick();
        if(minecraft!=null&&!minecraft.isWindowActive())resetCursor();
    }

    private int physicalWidth() {
        return Math.max(1, (int) Math.round(browserWidth * guiScale()));
    }

    private int physicalHeight() {
        return Math.max(1, (int) Math.round(browserHeight * guiScale()));
    }

    private double guiScale() {
        return minecraft == null || minecraft.getWindow() == null
                ? 1.0D : Math.max(1.0D, minecraft.getWindow().getGuiScale());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.flush();
        graphics.enableScissor(browserLeft, browserTop,
                browserLeft + browserWidth, browserTop + browserHeight);
        if (browser != null && browser.getRenderer().getTextureID() != 0) {
            renderBrowserTexture();
        } else {
            graphics.fill(browserLeft, browserTop,
                    browserLeft + browserWidth, browserTop + browserHeight, 0xFF0F0F0F);
            String message = initializationFailed
                    ? PhoneSettingsStore.text("YouTube tarayıcısı başlatılamadı", "YouTube browser could not start")
                    : PhoneSettingsStore.text("YouTube hazırlanıyor...", "Preparing YouTube...");
            com.mikasrevs.newphone.client.gui.PhoneModernText.drawCenteredColoredFitted(
                    graphics, message, x(150), y(298), size(9), size(266), 0xFFF1F1F1);
        }
        graphics.disableScissor();

        drawStatusBar(graphics);
        drawModelButton(graphics, mouseX, mouseY);
        drawHomeIndicator(graphics);
        drawFrame(graphics);
    }

    private void drawModelButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = insideCircle(mouseX, mouseY, modelCenterX, modelCenterY, modelRadius + size(2));
        fillCircle(graphics, modelCenterX + size(1), modelCenterY + size(2), modelRadius, 0x78000000);
        fillCircle(graphics, modelCenterX, modelCenterY, modelRadius,
                hovered ? 0xFF2D8CFF : 0xE51970D3);
        graphics.fill(modelCenterX - size(7), modelCenterY - size(4),
                modelCenterX + size(7), modelCenterY + size(4), 0xFFFFFFFF);
        graphics.fill(modelCenterX + size(5), modelCenterY - size(2),
                modelCenterX + size(7), modelCenterY + size(3), 0xFF2D8CFF);
    }

    private void renderBrowserTexture() {
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(browserLeft, browserTop + browserHeight, 0.0D)
                .uv(0.0F, 1.0F).color(255, 255, 255, 255).endVertex();
        builder.vertex(browserLeft + browserWidth, browserTop + browserHeight, 0.0D)
                .uv(1.0F, 1.0F).color(255, 255, 255, 255).endVertex();
        builder.vertex(browserLeft + browserWidth, browserTop, 0.0D)
                .uv(1.0F, 0.0F).color(255, 255, 255, 255).endVertex();
        builder.vertex(browserLeft, browserTop, 0.0D)
                .uv(0.0F, 0.0F).color(255, 255, 255, 255).endVertex();
        tesselator.end();

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && insideCircle(mouseX, mouseY, modelCenterX, modelCenterY, modelRadius + size(3))) {
            enterHeldModelMode();
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (browser != null && insideBrowser(mouseX, mouseY)) {
            if(!input.pressButton(button))return true;
            browser.sendMousePress(browserMouseX(mouseX), browserMouseY(mouseY), button);
            browser.setFocus(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (super.mouseReleased(mouseX, mouseY, button)) return true;
        if (browser != null && input.releaseButton(button)) {
            browser.sendMouseRelease(browserMouseX(mouseX), browserMouseY(mouseY), button);
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        webHovered=insideBrowser(mouseX,mouseY)
                &&!insideCircle(mouseX,mouseY,modelCenterX,modelCenterY,modelRadius+size(3));
        if(browser!=null)browser.sendMouseMove(webHovered||input.dragging()?browserMouseX(mouseX):-1,
                webHovered||input.dragging()?browserMouseY(mouseY):-1);
        if(webHovered&&browserHandle!=null)browserHandle.applyCursor();else resetCursor();
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (browser != null && input.dragging()) {
            browser.sendMouseMove(browserMouseX(mouseX), browserMouseY(mouseY));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (browser != null && insideBrowser(mouseX, mouseY)
                &&!insideCircle(mouseX,mouseY,modelCenterX,modelCenterY,modelRadius+size(3))) {
            browser.sendMouseWheel(browserMouseX(mouseX), browserMouseY(mouseY), delta, 0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (browser != null) {
            input.pressKey(keyCode);
            browser.sendKeyPress(keyCode, scanCode, modifiers);
            browser.setFocus(true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (browser != null && input.releaseKey(keyCode)) {
            browser.sendKeyRelease(keyCode, scanCode, modifiers);
            browser.setFocus(true);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (browser != null && character != 0) {
            browser.sendKeyTyped(character, modifiers);
            browser.setFocus(true);
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    private boolean insideBrowser(double mouseX, double mouseY) {
        return mouseX >= browserLeft && mouseX < browserLeft + browserWidth
                && mouseY >= browserTop && mouseY < browserTop + browserHeight;
    }

    private int browserMouseX(double mouseX) {
        return (int) Math.round(Math.max(0.0D,
                Math.min(browserWidth - 1.0D, mouseX - browserLeft)) * guiScale());
    }

    private int browserMouseY(double mouseY) {
        return (int) Math.round(Math.max(0.0D,
                Math.min(browserHeight - 1.0D, mouseY - browserTop)) * guiScale());
    }

    @Override
    public void onClose() {
        releaseInput();
        PhoneYouTubeState.stop();
        browser = null;
        browserHandle = null;
        resetCursor();
        super.onClose();
    }

    private void enterHeldModelMode() {
        if (browser == null || browserHandle == null || minecraft == null)return;
        releaseInput();
        if (!PhoneYouTubeState.enterModelMode(browserHandle)) return;
        browser = null;
        browserHandle = null;
        minecraft.setScreen(null);
        // A late CEF callback may previously have made GLFW visible while
        // Minecraft still considered the mouse grabbed; restore the actual mode.
        resetCursor();
    }

    @Override
    public void removed() {
        closed = true;
        initializationGeneration++;waitingForMcef=false;
        releaseInput();
        if (browser != null) {
            if (PhoneYouTubeState.browser() == browserHandle) PhoneYouTubeState.stop();
            else if (browserHandle != null) browserHandle.close();
            else browser.close();
            browser = null;
            browserHandle = null;
        }
        resetCursor();
        super.removed();
    }

    private void releaseInput(){
        if(browser!=null) {
            try {
                input.releaseAll(button->browser.sendMouseRelease(0,0,button),key->browser.sendKeyRelease(key,0,0));
                browser.setFocus(false);
            } catch(RuntimeException|LinkageError failure) {
                com.mojang.logging.LogUtils.getLogger().warn("Could not release YouTube input",failure);
            }
        }else input.releaseAll(button->{},key->{});
        webHovered=false;
    }
    private static void resetCursor(){
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.getWindow()==null)return;
        long window=mc.getWindow().getWindow();
        GLFW.glfwSetCursor(window,0);
        if(mc.screen!=null)GLFW.glfwSetInputMode(window,GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_NORMAL);
        else if(mc.mouseHandler.isMouseGrabbed())GLFW.glfwSetInputMode(window,GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_DISABLED);
    }

    private static final class McefBrowserHandle implements PhoneYouTubeState.BrowserHandle {
        private final MCEFBrowser browser;
        private boolean closed;
        private CefCursorType cursor=CefCursorType.POINTER;

        private McefBrowserHandle(MCEFBrowser browser) {
            this.browser = browser;
            browser.setCursorChangeListener(type->{
                Minecraft mc=Minecraft.getInstance();
                mc.execute(()->{if(!closed){cursor=CefCursorType.fromId(type);applyCursor();}});
            });
        }

        private void applyCursor(){
            Minecraft mc=Minecraft.getInstance();
            if(!(mc.screen instanceof ZPhoneYouTubeScreen screen)
                    ||screen.browserHandle!=this||!PhoneBrowserInput.cursorAllowed(closed,true,screen.webHovered,mc.isWindowActive()))return;
            browser.setCursor(cursor==null?CefCursorType.POINTER:cursor);
        }

        private MCEFBrowser browser() {
            return browser;
        }

        @Override
        public int textureId() {
            return closed ? 0 : browser.getRenderer().getTextureID();
        }

        @Override
        public void setFocus(boolean focused) {
            if (!closed) browser.setFocus(focused);
        }

        @Override
        public void resize(int width, int height) {
            if (!closed) browser.resize(Math.max(1, width), Math.max(1, height));
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            browser.setCursorChangeListener(type->{});
            try {browser.setFocus(false);browser.close();}
            catch(RuntimeException|LinkageError failure){com.mojang.logging.LogUtils.getLogger().warn("Could not close YouTube browser",failure);}
            resetCursor();
        }

        @Override
        public net.minecraft.client.gui.screens.Screen createScreen() {
            return closed ? null : new ZPhoneYouTubeScreen();
        }
    }

    private static boolean insideCircle(double x, double y, int centerX, int centerY, int radius) {
        double dx = x - centerX;
        double dy = y - centerY;
        return dx * dx + dy * dy <= (double) radius * radius;
    }
}

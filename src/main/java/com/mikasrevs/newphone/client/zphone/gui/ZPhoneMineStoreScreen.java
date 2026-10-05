package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.settings.PhoneAppInstallStore;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** MineStore storefront for the three local MPhone mini games. */
public final class ZPhoneMineStoreScreen extends AbstractZPhoneScreen {
    private static final long INSTALL_DURATION_MS = 850L;
    private static final PhoneApp[] GAMES = {PhoneApp.BLOCK_SNAKE, PhoneApp.CREEPER_RUSH, PhoneApp.GEM_PAIRS};
    private static final int[] CARD_TOPS = {112, 260, 408};
    private PhoneApp installing;
    private long installStartedAt;
    private String toast = "";
    private long toastUntil;

    public ZPhoneMineStoreScreen() { super(Component.literal("MineStore")); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(x(3), y(8), x(297), y(606), 0xFF07101A);
        fillCircle(g, x(256), y(78), size(82), 0x241E8DFF);
        drawStatusBar(g); drawBackButton(g, PhoneSettingsStore.text("Geri", "Back"));
        PhoneModernText.drawColored(g, "MineStore", x(61), y(52), size(20), 0xFFFFFFFF);
        PhoneModernText.drawColored(g, PhoneSettingsStore.text("Telefonuna yeni oyunlar indir", "Download new games to your phone"), x(24), y(86), size(8), 0xFF9FB0C1);
        for (int i=0;i<GAMES.length;i++) drawGameCard(g,GAMES[i],CARD_TOPS[i],mouseX,mouseY);
        if (!toast.isBlank() && System.currentTimeMillis() < toastUntil) {
            drawRoundedRect(g,x(34),y(548),x(266),y(579),size(15),0xE6232D3A);
            PhoneModernText.drawCenteredColored(g,toast,x(150),y(557),size(8),0xFFFFFFFF);
        }
        drawHomeIndicator(g); drawFrame(g);
    }

    private void drawGameCard(GuiGraphics g, PhoneApp app, int top, int mouseX, int mouseY) {
        boolean installed=PhoneAppInstallStore.isInstalled(app), working=installing==app, hover=inside(mouseX,mouseY,18,top,282,top+130);
        drawRoundedRect(g,x(18),y(top),x(282),y(top+130),size(22),hover?0xFF1D2B3B:0xFF151F2B);
        int iconSize=size(57); MPhoneAppIconRenderer.draw(g,app,x(31),y(top+21),iconSize,hover);
        PhoneModernText.drawColored(g,app.displayName(),x(101),y(top+20),size(13),0xFFFFFFFF);
        PhoneModernText.drawColored(g,description(app),x(101),y(top+47),size(7),0xFF9EADBC);
        PhoneModernText.drawColored(g,detail(app),x(101),y(top+65),size(7),0xFF75889B);
        drawRoundedRect(g,x(194),y(top+91),x(267),y(top+119),size(14),installed?0xFF2D3946:app.launchColor());
        String button=installed?PhoneSettingsStore.text("Aç","Open"):working?PhoneSettingsStore.text("İniyor","Loading"):PhoneSettingsStore.text("Yükle","Install");
        PhoneModernText.drawCenteredColored(g,button,x(230),y(top+99),size(8),0xFFFFFFFF);
        if(working){float progress=Math.min(1F,(System.currentTimeMillis()-installStartedAt)/(float)INSTALL_DURATION_MS);drawRoundedRect(g,x(101),y(top+100),x(184),y(top+108),size(4),0xFF344353);int fill=101+Math.round(83F*progress);if(fill>101)drawRoundedRect(g,x(101),y(top+100),x(fill),y(top+108),size(4),0xFFEAF4FF);}else PhoneModernText.drawColored(g,installed?PhoneSettingsStore.text("Ana ekranda","On Home Screen"):"24 MB",x(101),y(top+99),size(7),installed?0xFF71DAA0:0xFF8C9BAA);
    }
    private String description(PhoneApp app){return switch(app){case BLOCK_SNAKE->PhoneSettingsStore.text("Klasik yılan, Minecraft tarzı","Classic snake, Minecraft style");case CREEPER_RUSH->PhoneSettingsStore.text("Engellerden zıpla ve kaç","Jump and dodge the obstacles");case GEM_PAIRS->PhoneSettingsStore.text("Eşleşen taşları bul","Find the matching gems");default->"";};}
    private String detail(PhoneApp app){return switch(app){case BLOCK_SNAKE->PhoneSettingsStore.text("Arcade • Yön tuşları","Arcade • Arrow controls");case CREEPER_RUSH->PhoneSettingsStore.text("Koşu • Tek dokunuş","Runner • One tap");case GEM_PAIRS->PhoneSettingsStore.text("Bulmaca • Hafıza","Puzzle • Memory");default->"";};}

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(button!=0)return super.mouseClicked(mouseX,mouseY,button);
        if(handleBackButton(mouseX,mouseY))return true;
        for(int i=0;i<GAMES.length;i++){PhoneApp app=GAMES[i];int top=CARD_TOPS[i];if(inside(mouseX,mouseY,188,top+84,274,top+125)){if(PhoneAppInstallStore.isInstalled(app))ZPhoneClient.openSharedApp(app,this);else if(installing==null){installing=app;installStartedAt=System.currentTimeMillis();}return true;}}
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){if(keyCode==GLFW.GLFW_KEY_ESCAPE){PhoneNavigationState.backOrHome(minecraft);return true;}return super.keyPressed(keyCode,scanCode,modifiers);}
    @Override public void tick(){super.tick();if(installing!=null&&System.currentTimeMillis()-installStartedAt>=INSTALL_DURATION_MS){PhoneApp app=installing;installing=null;if(PhoneAppInstallStore.install(app)){toast=PhoneSettingsStore.text("Ana ekrana yüklendi","Installed to Home Screen");toastUntil=System.currentTimeMillis()+2200L;}}}
}

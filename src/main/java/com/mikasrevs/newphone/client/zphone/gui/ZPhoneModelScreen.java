package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.phone.model.ClientPhoneModels;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.PhoneNetwork;
import com.mikasrevs.newphone.network.message.PhoneActionMessage;
import com.mikasrevs.newphone.phone.model.PhoneModelRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The same phone, shown with Blender-rendered shell previews. */
public final class ZPhoneModelScreen extends AbstractZPhoneScreen {
    private static final int TOP = 116, STEP = 145, BOTTOM = 555;
    private float scroll;
    private String pending;
    private long pendingAt;
    public ZPhoneModelScreen() { super(Component.literal(PhoneSettingsStore.text("Telefon modeli", "Phone Model"))); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(x(0), y(0), x(300), y(610), 0xFF08090C);
        drawStatusBar(g, 0xFFF7F7FA, 0xFF08090C);
        text(g, "< " + tr("Ayarlar", "Settings"), 20, 42, 8, 0xFFB4B5BD);
        text(g, tr("Telefon modeli", "Phone Model"), 20, 64, 15, 0xFFF7F7FA);
        text(g, tr("Senin telefonun. Yeni bir görünüm.", "Your phone. A new look."), 20, 91, 8, 0xFFB4B5BD);
        String selected = ClientPhoneModels.local().id();
        if (pending != null && (pending.equals(selected) || System.currentTimeMillis() - pendingAt > 2500)) pending = null;
        var models = PhoneModelRegistry.all();
        g.enableScissor(x(12), y(TOP), x(288), y(BOTTOM));
        for (int i = 0; i < models.size(); i++) {
            var model = models.get(i);
            int left = 18 + (i % 2) * 137, top = TOP + (i / 2) * STEP - Math.round(scroll);
            boolean active = selected.equals(model.id());
            boolean hover = inside(mouseX, mouseY, left, top, left + 127, top + 135);
            int accent = PhoneSettingsStore.accentColor() | 0xFF000000;
            drawRoundedRect(g, x(left), y(top), x(left + 127), y(top + 135), size(10),
                    active ? accent : hover ? 0xFF44464F : 0xFF24252B);
            drawRoundedRect(g, x(left + 2), y(top + 2), x(left + 125), y(top + 133), size(9), 0xFF1C1D22);
            g.blit(model.preview(), x(left + 25), y(top + 5), size(77), size(87), 0, 0, 256, 288, 256, 288);
            PhoneModernText.drawCenteredColoredNoShadow(g, ClientPhoneModels.label(model.name()), x(left + 63), y(top + 94), size(10), 0xFFF7F7FA);
            PhoneModernText.drawCenteredColoredNoShadow(g, ClientPhoneModels.label(model.finish()), x(left + 63), y(top + 111), size(7), 0xFFB4B5BD);
            if (active) {
                fillCircle(g, x(left + 111), y(top + 16), size(7), accent);
                for (int step = 0; step < 4; step++) {
                    g.fill(x(left + 107 + step), y(top + 16 + step), x(left + 109 + step), y(top + 18 + step), 0xFFFFFFFF);
                    g.fill(x(left + 110 + step), y(top + 18 - step * 2), x(left + 112 + step), y(top + 20 - step * 2), 0xFFFFFFFF);
                }
            }
        }
        g.disableScissor();
        PhoneModernText.drawCenteredColoredNoShadow(g,
                pending == null ? tr("Seçili: ", "Selected: ") + ClientPhoneModels.label(ClientPhoneModels.local().name()) : tr("Kaydediliyor...", "Saving..."),
                x(150), y(568), size(8), 0xFFB4B5BD);
        drawHomeIndicator(g);
        drawFrame(g);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inside(mx, my, 12, 34, 110, 61)) return PhoneNavigationState.backOrHome(minecraft);
        if (button == 0 && inside(mx, my, 12, TOP, 288, BOTTOM)) {
            var models = PhoneModelRegistry.all();
            for (int i = 0; i < models.size(); i++) {
                int left = 18 + i % 2 * 137, top = TOP + i / 2 * STEP - Math.round(scroll);
                if (inside(mx, my, left, top, left + 127, top + 135)) {
                    if (pending != null || models.get(i).id().equals(ClientPhoneModels.local().id())) return true;
                    pending = models.get(i).id();
                    pendingAt = System.currentTimeMillis();
                    PhoneNetwork.CHANNEL.sendToServer(new PhoneActionMessage("PHONE_MODEL", "", pending));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (inside(mx, my, 12, TOP, 288, BOTTOM)) {
            float max = Math.max(0, ((PhoneModelRegistry.all().size() + 1) / 2) * STEP - (BOTTOM - TOP));
            scroll = Math.max(0, Math.min(max, scroll - (float) delta * 35));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
    private void text(GuiGraphics g, String value, int left, int top, int height, int color) {
        PhoneModernText.drawColoredNoShadow(g, value, x(left), y(top), size(height), color);
    }
    private static String tr(String tr, String en) { return PhoneSettingsStore.text(tr, en); }
}

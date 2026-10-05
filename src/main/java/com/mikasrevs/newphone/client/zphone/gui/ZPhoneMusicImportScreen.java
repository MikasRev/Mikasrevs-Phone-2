package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhoneMusicPlayerState;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** In-phone explanation and confirmation step before opening the native music file picker. */
public final class ZPhoneMusicImportScreen extends AbstractZPhoneScreen {
    private static final int PAGE_BG = 0xFF0C0D12;
    private static final int CARD_BG = 0xFF1B1D25;
    private static final int CARD_LINE = 0xFF30333D;
    private static final int PRIMARY = 0xFFF7F7FA;
    private static final int SECONDARY = 0xFFB4B6C0;
    private static final int MUTED = 0xFF7D808B;
    private static final int ACCENT = 0xFFFF4F76;
    private static final int BUTTON_TOP = 472;
    private static final int BUTTON_BOTTOM = 522;

    private final ZPhoneMusicScreen parent;
    private String errorMessage = "";

    public ZPhoneMusicImportScreen(ZPhoneMusicScreen parent) {
        super(Component.literal(PhoneSettingsStore.text("Müzik Ekle", "Add Music")));
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(x(0), y(0), x(DESIGN_WIDTH), y(DESIGN_HEIGHT), PAGE_BG);
        drawStatusBar(g);
        drawHeader(g, mouseX, mouseY);
        drawIntro(g);
        drawLimits(g);
        drawChooseButton(g, mouseX, mouseY);
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, 15, 47, 45, 77);
        fillCircle(g, x(30), y(62), size(15), hover ? 0xFF383B45 : 0xFF252731);
        drawLine(g, 34, 55, 27, 62, PRIMARY);
        drawLine(g, 27, 62, 34, 69, PRIMARY);
        PhoneModernText.drawCenteredColoredFitted(g,
                PhoneSettingsStore.text("M\u00fczik Ekle", "Add Music"),
                x(150), y(53), size(13), size(180), PRIMARY);
    }

    private void drawIntro(GuiGraphics g) {
        fillCircle(g, x(150), y(137), size(47), 0xFF2A1B24);
        fillCircle(g, x(150), y(137), size(38), 0xFF3B202C);
        drawMusicUploadIcon(g, 150, 137);

        PhoneModernText.drawCenteredColoredFitted(g,
                PhoneSettingsStore.text("Kendi m\u00fczi\u011fini ekle", "Add your own music"),
                x(150), y(201), size(15), size(250), PRIMARY);
        PhoneModernText.drawCenteredColoredFitted(g,
                PhoneSettingsStore.text("Cihaz\u0131ndan bir ses dosyas\u0131 se\u00e7", "Choose an audio file from your device"),
                x(150), y(228), size(8), size(250), SECONDARY);
    }

    private void drawLimits(GuiGraphics g) {
        drawRoundedRect(g, x(22), y(260), x(278), y(458), size(13), CARD_BG);
        PhoneModernText.drawColoredFitted(g,
                PhoneSettingsStore.text("Dosya gereksinimleri", "File requirements"),
                x(38), y(276), size(10), size(220), PRIMARY);

        drawRequirement(g, 306,
                PhoneSettingsStore.text("Desteklenen t\u00fcrler", "Supported types"), "OGG, WAV");
        g.fill(x(38), y(337), x(262), y(338), CARD_LINE);
        drawRequirement(g, 348,
                PhoneSettingsStore.text("En b\u00fcy\u00fck dosya", "Maximum file size"), "50 MB");
        g.fill(x(38), y(379), x(262), y(380), CARD_LINE);
        drawRequirement(g, 390,
                PhoneSettingsStore.text("En uzun s\u00fcre", "Maximum duration"),
                PhoneSettingsStore.text("7 dakika", "7 minutes"));
        g.fill(x(38), y(421), x(262), y(422), CARD_LINE);
        drawRequirement(g, 432,
                PhoneSettingsStore.text("Ses kanal\u0131", "Audio channels"),
                PhoneSettingsStore.text("Mono veya stereo", "Mono or stereo"));
    }

    private void drawRequirement(GuiGraphics g, int top, String label, String value) {
        fillCircle(g, x(44), y(top + 9), size(4), ACCENT);
        PhoneModernText.drawColoredFitted(g, label, x(57), y(top), size(7), size(132), SECONDARY);
        PhoneModernText.drawCenteredColoredFitted(g, value, x(226), y(top), size(8), size(68), PRIMARY);
    }

    private void drawChooseButton(GuiGraphics g, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, 25, BUTTON_TOP, 275, BUTTON_BOTTOM);
        drawRoundedRect(g, x(25), y(BUTTON_TOP), x(275), y(BUTTON_BOTTOM), size(15),
                hover ? 0xFFFF6383 : ACCENT);
        drawFolderIcon(g, 49, 497);
        PhoneModernText.drawCenteredColoredFitted(g,
                PhoneSettingsStore.text("Dosyalardan m\u00fczik se\u00e7", "Choose music from files"),
                x(161), y(488), size(10), size(190), PRIMARY);

        if (!errorMessage.isBlank()) {
            PhoneModernText.drawCenteredColoredFitted(g, errorMessage, x(150), y(536), size(7), size(250), 0xFFFF8E9F);
        } else {
            PhoneModernText.drawCenteredColoredFitted(g,
                    PhoneSettingsStore.text("Se\u00e7ilen dosya yaln\u0131zca bu cihazda saklan\u0131r", "The selected file stays on this device"),
                    x(150), y(536), size(6), size(250), MUTED);
        }
    }

    private void drawMusicUploadIcon(GuiGraphics g, int centerX, int centerY) {
        g.fill(x(centerX + 8), y(centerY - 23), x(centerX + 13), y(centerY + 10), PRIMARY);
        g.fill(x(centerX + 10), y(centerY - 23), x(centerX + 27), y(centerY - 18), PRIMARY);
        fillCircle(g, x(centerX + 3), y(centerY + 13), size(8), PRIMARY);
        g.fill(x(centerX - 23), y(centerY - 3), x(centerX - 19), y(centerY + 22), ACCENT);
        drawLine(g, centerX - 30, centerY + 4, centerX - 21, centerY - 5, ACCENT);
        drawLine(g, centerX - 21, centerY - 5, centerX - 12, centerY + 4, ACCENT);
    }

    private void drawFolderIcon(GuiGraphics g, int centerX, int centerY) {
        drawRoundedRect(g, x(centerX - 10), y(centerY - 7), x(centerX + 11), y(centerY + 9), size(3), 0xFFFFFFFF);
        drawRoundedRect(g, x(centerX - 9), y(centerY - 11), x(centerX + 1), y(centerY - 4), size(2), 0xFFFFFFFF);
        drawRoundedRect(g, x(centerX - 7), y(centerY - 3), x(centerX + 8), y(centerY + 6), size(2), ACCENT);
    }

    private void drawLine(GuiGraphics g, int fromX, int fromY, int toX, int toY, int color) {
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
        for (int step = 0; step <= steps; step++) {
            float progress = steps == 0 ? 0.0F : step / (float) steps;
            int px = x(fromX + (toX - fromX) * progress);
            int py = y(fromY + (toY - fromY) * progress);
            g.fill(px - size(1), py - size(1), px + size(1) + 1, py + size(1) + 1, color);
        }
    }

    private void chooseMusicFile() {
        errorMessage = "";
        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(2);
            filters.put(stack.UTF8("*.ogg"));
            filters.put(stack.UTF8("*.wav"));
            filters.flip();
            selected = TinyFileDialogs.tinyfd_openFileDialog(
                    PhoneSettingsStore.text("M\u00fczik dosyas\u0131 se\u00e7", "Choose a music file"),
                    System.getProperty("user.home", ""), filters, "OGG / WAV", false);
        } catch (RuntimeException | LinkageError error) {
            errorMessage = PhoneSettingsStore.text("Dosya se\u00e7ici a\u00e7\u0131lamad\u0131", "Could not open the file picker");
            return;
        }
        if (selected == null || selected.isBlank()) return;

        PhoneMusicPlayerState.ImportResult result;
        try {
            result = PhoneMusicPlayerState.importTrack(Path.of(selected));
        } catch (RuntimeException | LinkageError error) {
            errorMessage = PhoneSettingsStore.text("Dosya okunamad\u0131", "The file could not be read");
            return;
        }
        if (!result.success()) {
            errorMessage = importErrorText(result.error());
            return;
        }

        PhoneMusicPlayerState.play(result.trackIndex());
        parent.showImportNotice(PhoneSettingsStore.text("Par\u00e7a eklendi", "Track added"), false);
        PhoneNavigationState.returnTo(minecraft, parent);
    }

    private String importErrorText(PhoneMusicPlayerState.ImportError error) {
        if (error == null) return PhoneSettingsStore.text("Par\u00e7a eklenemedi", "Could not add the track");
        return switch (error) {
            case UNSUPPORTED_FORMAT -> PhoneSettingsStore.text("Yaln\u0131zca OGG ve WAV desteklenir", "Only OGG and WAV are supported");
            case FILE_TOO_LARGE -> PhoneSettingsStore.text("Dosya 50 MB'den b\u00fcy\u00fck", "The file is larger than 50 MB");
            case AUDIO_TOO_LONG -> PhoneSettingsStore.text("Ses dosyas\u0131 i\u015flenmek i\u00e7in \u00e7ok uzun", "The audio is too long to process");
            case INVALID_AUDIO -> PhoneSettingsStore.text("Ses dosyas\u0131 bozuk veya desteklenmiyor", "The audio file is invalid or unsupported");
            case INVALID_FILE, READ_FAILED -> PhoneSettingsStore.text("Dosya okunamad\u0131", "The file could not be read");
            case COPY_FAILED -> PhoneSettingsStore.text("Par\u00e7a k\u00fct\u00fcphaneye kopyalanamad\u0131", "The track could not be copied to the library");
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (inside(mouseX, mouseY, 15, 47, 45, 77)) {
                PhoneNavigationState.returnTo(minecraft, parent);
                return true;
            }
            if (inside(mouseX, mouseY, 25, BUTTON_TOP, 275, BUTTON_BOTTOM)) {
                chooseMusicFile();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            PhoneNavigationState.returnTo(minecraft, parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

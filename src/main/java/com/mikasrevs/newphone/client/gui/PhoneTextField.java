package com.mikasrevs.newphone.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** Single-line phone input using the same glyph metrics for text, selection and pointer input. */
public final class PhoneTextField {
    private final StringBuilder value = new StringBuilder();
    private final PhoneChatDraft editor;
    private final int width, height;
    private static final int FONT_HEIGHT = 9;
    private int start;
    private boolean focused;
    private String hint = "";

    public PhoneTextField(int width, int height, int limit) {
        this.width = width; this.height = height; editor = new PhoneChatDraft(value, limit);
    }

    public String getValue() { return value.toString(); }
    public void setValue(String text) { editor.selectAll(); editor.insert(text); start = 0; }
    public void setHint(String text) { hint = text; }
    public boolean isFocused() { return focused; }
    public void setFocused(boolean focused) { this.focused = focused; }

    private void revealCursor() {
        int cursor = editor.cursor();
        start = Math.min(start, cursor);
        while (start < cursor && measure(start, cursor) > width - 3)
            start = value.offsetByCodePoints(start, 1);
    }

    private int measure(int from, int to) {
        return from == to ? 0 : PhoneModernText.width(value.substring(from, to), FONT_HEIGHT);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        revealCursor();
        int end = start;
        while (end < value.length()) {
            int next = value.offsetByCodePoints(end, 1);
            if (measure(start, next) > width - 2) break;
            end = next;
        }
        int from = Math.max(start, editor.selectionStart()), to = Math.min(end, editor.selectionEnd());
        if (focused && to > from) g.fill(measure(start, from), -1, measure(start, to), height - 2, 0x88549FEA);
        if (value.isEmpty()) PhoneModernText.drawColoredFitted(g, hint, 0, 0, FONT_HEIGHT, width, 0xFFADB8C9);
        else PhoneModernText.drawColored(g, value.substring(start, end), 0, 0, FONT_HEIGHT, 0xFFF2F5FC);
        if (focused && System.currentTimeMillis() % 1000 < 600) {
            int x = Math.min(width - 1, measure(start, editor.cursor()));
            g.fill(x, 0, x + 1, height - 3, 0xFFF2F5FC);
        }
    }

    private int position(double x) {
        revealCursor();
        int at = start;
        while (at < value.length()) {
            int next = value.offsetByCodePoints(at, 1);
            if (x < (measure(start, at) + measure(start, next)) / 2.0) break;
            at = next;
        }
        return at;
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        focused = true; editor.moveTo(position(x), Screen.hasShiftDown()); return true;
    }

    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!focused || button != 0) return false;
        editor.moveTo(position(x), true); return true;
    }

    public boolean charTyped(char character, int modifiers) {
        if (!focused || character < 32 || character == 127) return false;
        editor.insert(String.valueOf(character)); return true;
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!focused) return false;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || Screen.hasControlDown();
        var keyboard = Minecraft.getInstance().keyboardHandler;
        if (control && key == GLFW.GLFW_KEY_A) editor.selectAll();
        else if (control && key == GLFW.GLFW_KEY_C) keyboard.setClipboard(editor.selectedText());
        else if (control && key == GLFW.GLFW_KEY_X) { keyboard.setClipboard(editor.selectedText()); editor.insert(""); }
        else if (control && key == GLFW.GLFW_KEY_V) editor.insert(keyboard.getClipboard().replace('\n', ' ').replace('\r', ' '));
        else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            boolean backwards = key == GLFW.GLFW_KEY_BACKSPACE;
            if (control && editor.selectionStart() == editor.selectionEnd()) {
                editor.moveTo(wordPosition(backwards ? -1 : 1), true); editor.insert("");
            } else editor.delete(backwards);
        }
        else if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            int direction = key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            if (control) editor.moveTo(wordPosition(direction), shift);
            else editor.move(direction, shift);
        }
        else if (key == GLFW.GLFW_KEY_HOME) editor.moveTo(0, shift);
        else if (key == GLFW.GLFW_KEY_END) editor.moveTo(value.length(), shift);
        else return false;
        return true;
    }

    private int wordPosition(int direction) {
        int at = editor.cursor();
        if (direction < 0) {
            while (at > 0 && Character.isWhitespace(value.codePointBefore(at))) at = value.offsetByCodePoints(at, -1);
            while (at > 0 && !Character.isWhitespace(value.codePointBefore(at))) at = value.offsetByCodePoints(at, -1);
        } else {
            while (at < value.length() && !Character.isWhitespace(value.codePointAt(at))) at = value.offsetByCodePoints(at, 1);
            while (at < value.length() && Character.isWhitespace(value.codePointAt(at))) at = value.offsetByCodePoints(at, 1);
        }
        return at;
    }
}

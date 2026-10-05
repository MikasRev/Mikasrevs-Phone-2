package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneChatDraft;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** A growing, vertically scrollable composer shared by all three messaging apps. */
final class PhoneChatComposer {
    private static final int MAX_ROWS = 5;
    private static final int LINE_HEIGHT = 14;
    private final StringBuilder text;
    private final PhoneChatDraft editor;
    private List<PhoneChatDraft.Line> lines = List.of(new PhoneChatDraft.Line(0, 0));
    private int firstRow, left, top, width, height, fontHeight;
    private boolean revealCursor = true;
    private boolean dirtyLayout = true;
    private int previousLength = -1, previousWidth = -1, previousFont = -1;
    private AbstractZPhoneScreen screen;

    PhoneChatComposer(StringBuilder text) { this(text,PhoneChatDraft.MAX_LENGTH); }
    PhoneChatComposer(StringBuilder text,int limit) { this.text = text; editor = new PhoneChatDraft(text,limit); }
    void setDraft(String draft) { editor.selectAll(); editor.insert(draft); dirtyLayout=true; revealCursor=true; }
    int layout(AbstractZPhoneScreen screen, int left, int bottom, int width, int fontHeight) {
        this.screen = screen; this.left = left; this.width = width; this.fontHeight = fontHeight;
        int pixelWidth = screen.size(width - 24);
        if (dirtyLayout || previousLength != text.length() || previousWidth != pixelWidth || previousFont != fontHeight) {
            lines = editor.lines(pixelWidth, s -> PhoneModernText.width(s, fontHeight));
            previousLength = text.length(); previousWidth = pixelWidth; previousFont = fontHeight;
            dirtyLayout = false;
            revealCursor = true;
        }
        height = 20 + Math.min(MAX_ROWS, lines.size()) * LINE_HEIGHT;
        top = bottom - height;
        firstRow = Math.max(0, Math.min(firstRow, lines.size() - Math.min(MAX_ROWS, lines.size())));
        if (revealCursor) {
            int row = editor.cursorLine(lines);
            if (row < firstRow) firstRow = row;
            if (row >= firstRow + MAX_ROWS) firstRow = row - MAX_ROWS + 1;
            revealCursor = false;
        }
        return top;
    }
    void render(GuiGraphics g, boolean focused, String placeholder, int background) {
        screen.drawRoundedRect(g, screen.x(left), screen.y(top), screen.x(left + width),
                screen.y(top + height), screen.size(15), background);
        int sx = screen.x(left + 12), sy = screen.y(top + 10);
        g.enableScissor(screen.x(left + 8), screen.y(top + 6), screen.x(left + width - 6), screen.y(top + height - 5));
        if (text.isEmpty() && !focused) PhoneModernText.drawColored(g, placeholder, sx, sy, fontHeight, 0xFF788492);
        for (int row = firstRow; row < Math.min(lines.size(), firstRow + MAX_ROWS); row++) {
            var line = lines.get(row);
            int yy = sy + screen.size((row - firstRow) * LINE_HEIGHT);
            int from = Math.max(line.start(), editor.selectionStart()), to = Math.min(line.end(), editor.selectionEnd());
            if (focused && to > from) {
                g.fill(sx + measure(line.start(), from), yy - 1,
                        sx + measure(line.start(), to), yy + screen.size(LINE_HEIGHT), 0x88549FEA);
            }
            PhoneModernText.drawColored(g, text.substring(line.start(), line.end()), sx, yy, fontHeight, 0xFFFFFFFF);
            if (focused && editor.cursorLine(lines) == row && System.currentTimeMillis() % 1000 < 600) {
                int cx = Math.min(screen.x(left + width - 9), sx + measure(line.start(), editor.cursor()));
                g.fill(cx, yy, cx + Math.max(1, screen.size(1)), yy + screen.size(12), 0xFFFFFFFF);
            }
        }
        g.flush(); g.disableScissor();
        if (lines.size() > MAX_ROWS) {
            int track = height - 14, thumb = Math.max(8, track * MAX_ROWS / lines.size());
            int yy = top + 7 + (track - thumb) * firstRow / (lines.size() - MAX_ROWS);
            g.fill(screen.x(left + width - 5), screen.y(yy), screen.x(left + width - 3), screen.y(yy + thumb), 0xFF6A7888);
        }
    }
    boolean contains(double mx, double my) {
        return screen != null && mx >= screen.x(left) && mx < screen.x(left + width)
                && my >= screen.y(top) && my < screen.y(top + height);
    }
    void click(double mx, double my, boolean selecting) {
        int row = firstRow + (int)((my - screen.y(top + 10)) / Math.max(1, screen.size(LINE_HEIGHT)));
        moveToPixel(Math.max(0, Math.min(lines.size() - 1, row)), (int)mx - screen.x(left + 12), selecting);
        revealCursor = true;
    }
    boolean scroll(double mx, double my, double delta) {
        if (!contains(mx, my) || delta == 0) return false;
        firstRow = Math.max(0, Math.min(Math.max(0, lines.size() - MAX_ROWS), firstRow + (delta > 0 ? -1 : 1)));
        revealCursor = false;
        return true;
    }
    void type(char c) { editor.insert(Character.toString(c)); dirtyLayout = revealCursor = true; }
    boolean key(int key, int modifiers) {
        if (screen != null) layout(screen, left, top + height, width, fontHeight);
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        var keyboard = Minecraft.getInstance().keyboardHandler;
        if (control && key == GLFW.GLFW_KEY_A) editor.selectAll();
        else if (control && key == GLFW.GLFW_KEY_C) keyboard.setClipboard(editor.selectedText());
        else if (control && key == GLFW.GLFW_KEY_X) { keyboard.setClipboard(editor.selectedText()); editor.insert(""); }
        else if (control && key == GLFW.GLFW_KEY_V) editor.insert(keyboard.getClipboard());
        else if (key == GLFW.GLFW_KEY_BACKSPACE) editor.delete(true);
        else if (key == GLFW.GLFW_KEY_DELETE) editor.delete(false);
        else if (key == GLFW.GLFW_KEY_LEFT) editor.move(-1, shift);
        else if (key == GLFW.GLFW_KEY_RIGHT) editor.move(1, shift);
        else if (key == GLFW.GLFW_KEY_HOME) editor.moveTo(control ? 0 : lines.get(editor.cursorLine(lines)).start(), shift);
        else if (key == GLFW.GLFW_KEY_END) editor.moveTo(control ? text.length() : lines.get(editor.cursorLine(lines)).end(), shift);
        else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            int row = editor.cursorLine(lines);
            int target = Math.max(0, Math.min(lines.size() - 1, row + (key == GLFW.GLFW_KEY_UP ? -1 : 1)));
            moveToPixel(target, measure(lines.get(row).start(), editor.cursor()), shift);
        } else if (shift && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) editor.insert("\n");
        else return false;
        dirtyLayout = revealCursor = true;
        return true;
    }
    private int measure(int start, int end) {
        if (start == end) return 0;
        return PhoneModernText.width(text.substring(start, Math.max(start, end)), fontHeight);
    }
    private void moveToPixel(int row, int px, boolean selecting) {
        var line = lines.get(row);
        int at = line.start();
        while (at < line.end()) {
            int next = Character.offsetByCodePoints(text, at, 1);
            if (px < (measure(line.start(), at) + measure(line.start(), next)) / 2) break;
            at = next;
        }
        editor.moveTo(at, selecting);
    }
}

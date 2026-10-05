package com.mikasrevs.newphone.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Editing and wrapping independent of the phone's scale and renderer. */
public final class PhoneChatDraft {
    public static final int MAX_LENGTH = com.mikasrevs.newphone.network.PhoneMessageLimits.TEXT_LENGTH;
    private final StringBuilder text;
    private final int limit;
    private int cursor;
    private int anchor;

    public PhoneChatDraft(StringBuilder text) { this(text,MAX_LENGTH); }
    public PhoneChatDraft(StringBuilder text,int limit) { this.text = text;this.limit=limit; cursor = anchor = text.length(); }
    public int cursor() { clamp(); return cursor; }
    public int selectionStart() { clamp(); return Math.min(cursor, anchor); }
    public int selectionEnd() { clamp(); return Math.max(cursor, anchor); }
    public String selectedText() { return text.substring(selectionStart(), selectionEnd()); }
    public void selectAll() { anchor = 0; cursor = text.length(); }
    public void moveTo(int position, boolean selecting) {
        cursor = Math.max(0, Math.min(text.length(), position));
        if (cursor > 0 && cursor < text.length() && Character.isLowSurrogate(text.charAt(cursor))
                && Character.isHighSurrogate(text.charAt(cursor - 1))) cursor--;
        if (!selecting) anchor = cursor;
    }
    public void move(int direction, boolean selecting) {
        if (!selecting && selectionStart() != selectionEnd()) {
            moveTo(direction < 0 ? selectionStart() : selectionEnd(), false);
        } else if (direction < 0 && cursor() > 0) {
            moveTo(Character.offsetByCodePoints(text, cursor, -1), selecting);
        } else if (direction > 0 && cursor() < text.length()) {
            moveTo(Character.offsetByCodePoints(text, cursor, 1), selecting);
        }
    }
    public void insert(String value) {
        String safe = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        safe = safe.codePoints().filter(c -> c == '\n' || (c >= 32 && c != 127 && c != 167))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        int start = selectionStart(), end = selectionEnd();
        int available = limit - (text.length() - (end - start));
        if (safe.length() > available) {
            int cut = Math.max(0, available);
            if (cut > 0 && Character.isHighSurrogate(safe.charAt(cut - 1))) cut--;
            safe = safe.substring(0, cut);
        }
        text.replace(start, end, safe);
        cursor = anchor = start + safe.length();
    }
    public void delete(boolean backwards) {
        if (selectionStart() != selectionEnd()) { insert(""); return; }
        int old = cursor();
        move(backwards ? -1 : 1, true);
        if (cursor != old) insert("");
    }
    private void clamp() {
        cursor = Math.min(cursor, text.length());
        anchor = Math.min(anchor, text.length());
    }
    public List<Line> lines(int width, ToIntFunction<String> measure) {
        List<Line> lines = new ArrayList<>();
        int start = 0, at = 0, lastSpace = -1;
        while (at < text.length()) {
            int next = Character.offsetByCodePoints(text, at, 1);
            if (text.charAt(at) == '\n') {
                lines.add(new Line(start, at)); start = next; at = next; lastSpace = -1; continue;
            }
            if (at > start && measure.applyAsInt(text.substring(start, next)) > Math.max(1, width)) {
                int end = lastSpace >= start ? lastSpace + 1 : at;
                lines.add(new Line(start, end)); start = at = end; lastSpace = -1; continue;
            }
            if (text.charAt(at) == ' ') lastSpace = at;
            at = next;
        }
        lines.add(new Line(start, text.length()));
        return lines;
    }
    public int cursorLine(List<Line> lines) {
        int row = 0;
        while (row + 1 < lines.size() && lines.get(row + 1).start() <= cursor()) row++;
        return row;
    }
    public record Line(int start, int end) { }
}

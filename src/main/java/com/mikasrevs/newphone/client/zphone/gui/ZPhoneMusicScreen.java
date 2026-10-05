package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhoneMusicPlayerState;
import com.mikasrevs.newphone.client.gui.PhoneMusicPlayerState.Track;
import com.mikasrevs.newphone.client.gui.PhoneWallpaperCatalog;
import com.mikasrevs.newphone.client.gui.PhoneWallpaperRenderer;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Dark artwork-first music player with a persistent favorites library. */
public final class ZPhoneMusicScreen extends AbstractZPhoneScreen {
    private static final int PAGE_BG = 0xFF08090C;
    private static final int PANEL_BG = 0xE523252C;
    private static final int PANEL_HOVER = 0xF02D3038;
    private static final int ARTWORK_FRAME = 0xFF15171D;
    private static final int PRIMARY_TEXT = 0xFFF7F7FA;
    private static final int SECONDARY_TEXT = 0xFFB1B2BB;
    private static final int TERTIARY_TEXT = 0xFF777982;
    private static final int DIVIDER = 0xFF3A3C44;
    private static final int MUSIC_ACCENT = 0xFFFF4F76;

    private static final int QUEUE_TOP = 414;
    private static final int QUEUE_BOTTOM = 529;
    private static final int QUEUE_ROW_HEIGHT = 37;
    private static final int FAVORITES_LIST_TOP = 171;
    private static final int FAVORITES_LIST_BOTTOM = 525;
    private static final int FAVORITE_ROW_HEIGHT = 56;
    private static final int TAB_TOP = 535;
    private static final int TAB_BOTTOM = 590;

    private Tab tab = Tab.MUSIC;
    private float favoriteScroll;
    private float favoriteTargetScroll;
    private float queueScroll;
    private float queueTargetScroll;
    private String importNotice = "";
    private boolean importNoticeError;
    private long importNoticeUntil;

    public ZPhoneMusicScreen() {
        super(Component.translatable("screen.newphone.app.music"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateFavoriteScroll();
        updateQueueScroll();
        renderBackground(g);
        drawArtworkBackdrop(g);
        drawStatusBar(g);
        drawHeader(g, mouseX, mouseY);

        if (tab == Tab.MUSIC) drawPlayer(g, mouseX, mouseY);
        else drawFavorites(g, mouseX, mouseY);

        drawBottomTabs(g, mouseX, mouseY);
        drawImportNotice(g);
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void drawArtworkBackdrop(GuiGraphics g) {
        ResourceLocation artwork = artworkTexture(PhoneMusicPlayerState.currentTrackIndex());
        PhoneWallpaperRenderer.blitCover(
                g,
                artwork,
                x(0),
                y(0),
                x(DESIGN_WIDTH),
                y(DESIGN_HEIGHT),
                PhoneWallpaperCatalog.TEXTURE_WIDTH,
                PhoneWallpaperCatalog.TEXTURE_HEIGHT
        );
        g.fill(x(0), y(0), x(DESIGN_WIDTH), y(DESIGN_HEIGHT), 0xB5000000);
        g.fill(x(0), y(350), x(DESIGN_WIDTH), y(DESIGN_HEIGHT), 0x5C000000);
        g.fill(x(0), y(0), x(DESIGN_WIDTH), y(46), 0x35000000);
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        boolean backHover = inside(mouseX, mouseY, 15, 47, 43, 75);
        fillCircle(g, x(29), y(61), size(14), backHover ? 0xE0444650 : 0xB52B2D35);
        drawDesignLine(g, 23, 58, 29, 64, PRIMARY_TEXT);
        drawDesignLine(g, 29, 64, 35, 58, PRIMARY_TEXT);

        boolean importHover = inside(mouseX, mouseY, 253, 47, 287, 75);
        fillCircle(g, x(271), y(61), size(14), importHover ? 0xFFF05A7D : 0xDD343640);
        drawRoundedRect(g, x(264), y(59), x(278), y(63), size(2), PRIMARY_TEXT);
        drawRoundedRect(g, x(269), y(54), x(273), y(68), size(2), PRIMARY_TEXT);

        String title = tab == Tab.MUSIC
                ? PhoneSettingsStore.text("Müzik", "Music")
                : PhoneSettingsStore.text("Favoriler", "Favorites");
        PhoneModernText.drawCenteredColoredFitted(g, title, x(150), y(53), size(13), size(180), PRIMARY_TEXT);
    }

    private void drawImportNotice(GuiGraphics g) {
        if (importNotice.isBlank() || System.currentTimeMillis() >= importNoticeUntil) return;
        int color = importNoticeError ? 0xF0A62935 : 0xF02A764F;
        drawRoundedRect(g, x(29), y(82), x(271), y(108), size(9), color);
        PhoneModernText.drawCenteredColoredFitted(g, importNotice, x(150), y(90), size(7), size(224), PRIMARY_TEXT);
    }

    private void drawPlayer(GuiGraphics g, int mouseX, int mouseY) {
        int currentIndex = PhoneMusicPlayerState.currentTrackIndex();
        Track current = PhoneMusicPlayerState.currentTrack();
        drawArtwork(g, currentIndex, 51, 76, 249, 274, 20);

        PhoneModernText.drawColoredFitted(g, current.title(), x(31), y(284), size(13), size(195), PRIMARY_TEXT);
        PhoneModernText.drawColoredFitted(g, current.artist(), x(31), y(305), size(7), size(195), SECONDARY_TEXT);

        boolean favorite = PhoneMusicPlayerState.isCurrentFavorite();
        boolean favoriteHover = inside(mouseX, mouseY, 239, 278, 281, 320);
        fillCircle(g, x(260), y(298), size(18), favoriteHover ? 0xE33A3C45 : 0xC92B2D34);
        drawHeart(g, 260, 298, 16, favorite ? MUSIC_ACCENT : 0xFFD4D5DA, favorite);

        int progressLeft = x(31);
        int progressRight = x(269);
        int progressY = y(328);
        int fill = progressLeft + Math.round((progressRight - progressLeft) * PhoneMusicPlayerState.progress());
        drawRoundedRect(g, progressLeft, progressY, progressRight, progressY + size(4), size(2), 0xFF53555E);
        if (fill > progressLeft) {
            drawRoundedRect(g, progressLeft, progressY, fill, progressY + size(4), size(2), 0xFFF2F3F6);
        }
        fillCircle(g, fill, progressY + size(2), size(4), 0xFFF7F7F9);
        PhoneModernText.drawColored(g, time(PhoneMusicPlayerState.elapsedMs()), x(31), y(338), size(6), SECONDARY_TEXT);
        PhoneModernText.drawCenteredColoredFitted(g, remainingTime(current), x(255), y(338), size(6), size(48), SECONDARY_TEXT);

        drawPlaybackControls(g, mouseX, mouseY);
        drawQueue(g, mouseX, mouseY);
    }

    private void drawPlaybackControls(GuiGraphics g, int mouseX, int mouseY) {
        boolean previousHover = inside(mouseX, mouseY, 65, 348, 111, 392);
        boolean playHover = inside(mouseX, mouseY, 120, 340, 180, 400);
        boolean nextHover = inside(mouseX, mouseY, 189, 348, 235, 392);

        fillCircle(g, x(88), y(370), size(20), previousHover ? 0xD63C3F48 : 0xB52A2C33);
        fillCircle(g, x(150), y(370), size(27), playHover ? 0xFF484052 : 0xFF393341);
        fillCircle(g, x(212), y(370), size(20), nextHover ? 0xD63C3F48 : 0xB52A2C33);

        drawSkipIcon(g, 88, 370, false, PRIMARY_TEXT);
        drawPlayPauseIcon(g, 150, 370, PRIMARY_TEXT);
        drawSkipIcon(g, 212, 370, true, PRIMARY_TEXT);
    }

    private void drawQueue(GuiGraphics g, int mouseX, int mouseY) {
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Şarkılar", "Songs"),
                x(24), y(399), size(8), size(180), PRIMARY_TEXT);

        drawRoundedRect(g, x(22), y(QUEUE_TOP), x(278), y(QUEUE_BOTTOM), size(9), PANEL_BG);
        Track[] tracks = PhoneMusicPlayerState.tracks();
        int current = PhoneMusicPlayerState.currentTrackIndex();
        int contentTop = QUEUE_TOP - Math.round(queueScroll);
        g.enableScissor(x(22), y(QUEUE_TOP), x(278), y(QUEUE_BOTTOM));
        for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
            int rowTop = contentTop + trackIndex * QUEUE_ROW_HEIGHT;
            if (rowTop + QUEUE_ROW_HEIGHT <= QUEUE_TOP || rowTop >= QUEUE_BOTTOM) continue;
            if (inside(mouseX, mouseY, 22, Math.max(rowTop, QUEUE_TOP), 278,
                    Math.min(rowTop + QUEUE_ROW_HEIGHT, QUEUE_BOTTOM))) {
                drawRoundedRect(g, x(24), y(rowTop + 2), x(276), y(rowTop + QUEUE_ROW_HEIGHT - 2),
                        size(6), PANEL_HOVER);
            }
            drawArtwork(g, trackIndex, 29, rowTop + 4, 58, rowTop + 33, 5);
            PhoneModernText.drawColoredFitted(g, tracks[trackIndex].title(), x(67), y(rowTop + 7),
                    size(7), size(139), trackIndex == current ? MUSIC_ACCENT : PRIMARY_TEXT);
            PhoneModernText.drawColoredFitted(g, tracks[trackIndex].artist(), x(67), y(rowTop + 22),
                    size(5), size(139), SECONDARY_TEXT);
            PhoneModernText.drawCenteredColoredFitted(g, time(tracks[trackIndex].durationMs()),
                    x(250), y(rowTop + 13), size(6), size(40), SECONDARY_TEXT);
            if (trackIndex + 1 < tracks.length) {
                g.fill(x(67), y(rowTop + QUEUE_ROW_HEIGHT - 1), x(278), y(rowTop + QUEUE_ROW_HEIGHT), DIVIDER);
            }
        }
        g.disableScissor();
    }

    private void drawFavorites(GuiGraphics g, int mouseX, int mouseY) {
        int[] favorites = PhoneMusicPlayerState.favoriteTrackIndexes();

        drawRoundedRect(g, x(22), y(83), x(278), y(153), size(10), PANEL_BG);
        fillCircle(g, x(56), y(118), size(20), 0x403F202B);
        drawHeart(g, 56, 118, 18, MUSIC_ACCENT, true);
        PhoneModernText.drawColoredFitted(g, PhoneSettingsStore.text("Beğendiğin parçalar", "Songs you like"),
                x(88), y(98), size(11), size(172), PRIMARY_TEXT);
        String count = favorites.length + PhoneSettingsStore.text(" favori", " favorites");
        PhoneModernText.drawColoredFitted(g, count, x(88), y(124), size(7), size(172), SECONDARY_TEXT);

        if (favorites.length == 0) {
            drawEmptyFavorites(g);
            return;
        }

        g.enableScissor(x(15), y(FAVORITES_LIST_TOP), x(285), y(FAVORITES_LIST_BOTTOM));
        int cardTop = FAVORITES_LIST_TOP - Math.round(favoriteScroll);
        int cardBottom = cardTop + favorites.length * FAVORITE_ROW_HEIGHT;
        drawRoundedRect(g, x(22), y(cardTop), x(278), y(cardBottom), size(10), PANEL_BG);

        Track[] tracks = PhoneMusicPlayerState.tracks();
        for (int row = 0; row < favorites.length; row++) {
            int trackIndex = favorites[row];
            int rowTop = cardTop + row * FAVORITE_ROW_HEIGHT;
            if (rowTop + FAVORITE_ROW_HEIGHT < FAVORITES_LIST_TOP || rowTop > FAVORITES_LIST_BOTTOM) continue;
            if (inside(mouseX, mouseY, 22, Math.max(rowTop, FAVORITES_LIST_TOP), 278,
                    Math.min(rowTop + FAVORITE_ROW_HEIGHT, FAVORITES_LIST_BOTTOM))) {
                drawRoundedRect(g, x(24), y(rowTop + 2), x(276), y(rowTop + FAVORITE_ROW_HEIGHT - 2),
                        size(6), PANEL_HOVER);
            }

            drawArtwork(g, trackIndex, 30, rowTop + 8, 70, rowTop + 48, 7);
            PhoneModernText.drawColoredFitted(g, tracks[trackIndex].title(), x(80), y(rowTop + 12),
                    size(8), size(125), PRIMARY_TEXT);
            PhoneModernText.drawColoredFitted(g, tracks[trackIndex].artist(), x(80), y(rowTop + 31),
                    size(6), size(125), SECONDARY_TEXT);
            PhoneModernText.drawCenteredColoredFitted(g, time(tracks[trackIndex].durationMs()),
                    x(224), y(rowTop + 19), size(6), size(42), SECONDARY_TEXT);
            drawHeart(g, 261, rowTop + 28, 12, MUSIC_ACCENT, true);
            if (row + 1 < favorites.length) {
                g.fill(x(80), y(rowTop + FAVORITE_ROW_HEIGHT - 1), x(278), y(rowTop + FAVORITE_ROW_HEIGHT), DIVIDER);
            }
        }
        g.disableScissor();
        drawFavoriteScrollBar(g, favorites.length);
    }

    private void drawEmptyFavorites(GuiGraphics g) {
        drawRoundedRect(g, x(22), y(177), x(278), y(291), size(10), PANEL_BG);
        drawHeart(g, 150, 211, 24, 0xFF686A73, false);
        PhoneModernText.drawCenteredColoredFitted(g,
                PhoneSettingsStore.text("Henüz favori yok", "No favorites yet"),
                x(150), y(242), size(9), size(220), PRIMARY_TEXT);
        PhoneModernText.drawCenteredColoredFitted(g,
                PhoneSettingsStore.text("Kalbe dokunduğun parçalar burada görünür", "Songs you heart will appear here"),
                x(150), y(264), size(6), size(224), SECONDARY_TEXT);
    }

    private void drawFavoriteScrollBar(GuiGraphics g, int favoriteCount) {
        int contentHeight = favoriteCount * FAVORITE_ROW_HEIGHT;
        int visibleHeight = FAVORITES_LIST_BOTTOM - FAVORITES_LIST_TOP;
        if (contentHeight <= visibleHeight) return;
        int thumbHeight = Math.max(42, Math.round(visibleHeight * (visibleHeight / (float) contentHeight)));
        int travel = visibleHeight - thumbHeight;
        float max = favoriteMaxScroll();
        int top = FAVORITES_LIST_TOP + Math.round(travel * (favoriteScroll / Math.max(1.0F, max)));
        drawRoundedRect(g, x(283), y(FAVORITES_LIST_TOP), x(286), y(FAVORITES_LIST_BOTTOM), size(2), 0x554C4E57);
        drawRoundedRect(g, x(282), y(top), x(287), y(top + thumbHeight), size(3), 0xC47C7E87);
    }

    private void drawBottomTabs(GuiGraphics g, int mouseX, int mouseY) {
        drawRoundedRect(g, x(13), y(TAB_TOP), x(287), y(TAB_BOTTOM), size(20), 0xF0181A20);
        drawTab(g, Tab.MUSIC, 15, 150, mouseX, mouseY);
        drawTab(g, Tab.FAVORITES, 150, 285, mouseX, mouseY);
    }

    private void drawTab(GuiGraphics g, Tab candidate, int left, int right, int mouseX, int mouseY) {
        boolean selected = tab == candidate;
        boolean hover = inside(mouseX, mouseY, left, TAB_TOP, right, TAB_BOTTOM);
        if (selected || hover) {
            drawRoundedRect(g, x(left + 3), y(TAB_TOP + 3), x(right - 3), y(TAB_BOTTOM - 3), size(17),
                    selected ? 0xFF292B33 : 0xB023252C);
        }
        int color = selected ? MUSIC_ACCENT : 0xFF92949D;
        int center = (left + right) / 2;
        if (candidate == Tab.MUSIC) drawMusicNote(g, center, 551, color);
        else drawHeart(g, center, 552, 14, color, selected);
        PhoneModernText.drawCenteredColoredFitted(g, candidate.label(), x(center), y(571), size(7),
                size(right - left - 16), color);
    }

    private void drawArtwork(GuiGraphics g, int trackIndex, int left, int top, int right, int bottom, int radius) {
        drawRoundedRect(g, x(left - 2), y(top + 2), x(right + 2), y(bottom + 4), size(radius + 2), 0x55000000);
        drawRoundedRect(g, x(left - 1), y(top - 1), x(right + 1), y(bottom + 1), size(radius + 1), ARTWORK_FRAME);
        PhoneWallpaperRenderer.blitCover(
                g,
                artworkTexture(trackIndex),
                x(left),
                y(top),
                x(right),
                y(bottom),
                PhoneWallpaperCatalog.TEXTURE_WIDTH,
                PhoneWallpaperCatalog.TEXTURE_HEIGHT
        );
        maskArtworkCorners(g, left, top, right, bottom, radius, ARTWORK_FRAME);
    }

    private void maskArtworkCorners(GuiGraphics g, int left, int top, int right, int bottom, int radius, int color) {
        int safeRadius = Math.max(1, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
        for (int offset = 0; offset < safeRadius; offset++) {
            int dy = safeRadius - offset;
            int inset = safeRadius - (int) Math.sqrt(Math.max(0, safeRadius * safeRadius - dy * dy));
            if (inset <= 0) continue;
            g.fill(x(left), y(top + offset), x(left + inset), y(top + offset + 1), color);
            g.fill(x(right - inset), y(top + offset), x(right), y(top + offset + 1), color);
            g.fill(x(left), y(bottom - offset - 1), x(left + inset), y(bottom - offset), color);
            g.fill(x(right - inset), y(bottom - offset - 1), x(right), y(bottom - offset), color);
        }
    }

    private ResourceLocation artworkTexture(int trackIndex) {
        return PhoneWallpaperCatalog.texture(trackIndex);
    }

    private void drawPlayPauseIcon(GuiGraphics g, int centerX, int centerY, int color) {
        if (PhoneMusicPlayerState.isPlaying()) {
            drawRoundedRect(g, x(centerX - 7), y(centerY - 10), x(centerX - 2), y(centerY + 10), size(2), color);
            drawRoundedRect(g, x(centerX + 2), y(centerY - 10), x(centerX + 7), y(centerY + 10), size(2), color);
            return;
        }
        drawTriangle(g, centerX - 5, centerY, 11, true, color);
    }

    private void drawSkipIcon(GuiGraphics g, int centerX, int centerY, boolean forward, int color) {
        int first = forward ? centerX - 12 : centerX + 12;
        int second = forward ? centerX - 2 : centerX + 2;
        drawTriangle(g, first, centerY, 8, forward, color);
        drawTriangle(g, second, centerY, 8, forward, color);
        int barX = forward ? centerX + 11 : centerX - 13;
        drawRoundedRect(g, x(barX), y(centerY - 9), x(barX + 3), y(centerY + 9), size(1), color);
    }

    private void drawTriangle(GuiGraphics g, int baseX, int centerY, int radius, boolean right, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int width = radius - Math.abs(dy) + 1;
            if (right) {
                g.fill(x(baseX), y(centerY + dy), x(baseX + width), y(centerY + dy + 1), color);
            } else {
                g.fill(x(baseX - width), y(centerY + dy), x(baseX), y(centerY + dy + 1), color);
            }
        }
    }

    private void drawHeart(GuiGraphics g, int centerX, int centerY, int height, int color, boolean filled) {
        PhoneModernText.drawCenteredColored(g, filled ? "♥" : "♡", x(centerX), y(centerY - height / 2),
                size(height), color);
    }

    private void drawMusicNote(GuiGraphics g, int centerX, int centerY, int color) {
        g.fill(x(centerX + 3), y(centerY - 9), x(centerX + 6), y(centerY + 6), color);
        g.fill(x(centerX + 4), y(centerY - 9), x(centerX + 12), y(centerY - 6), color);
        fillCircle(g, x(centerX), y(centerY + 7), size(5), color);
        drawDesignLine(g, centerX, centerY + 5, centerX + 4, centerY + 2, color);
    }

    private void drawDesignLine(GuiGraphics g, int fromX, int fromY, int toX, int toY, int color) {
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
        int pixel = Math.max(1, size(2));
        if (steps == 0) return;
        for (int step = 0; step <= steps; step++) {
            float progress = step / (float) steps;
            int px = x(fromX + (toX - fromX) * progress);
            int py = y(fromY + (toY - fromY) * progress);
            g.fill(px - pixel / 2, py - pixel / 2, px + (pixel + 1) / 2, py + (pixel + 1) / 2, color);
        }
    }

    private void updateFavoriteScroll() {
        float max = favoriteMaxScroll();
        favoriteTargetScroll = clamp(favoriteTargetScroll, 0.0F, max);
        favoriteScroll += (favoriteTargetScroll - favoriteScroll) * 0.24F;
        if (Math.abs(favoriteTargetScroll - favoriteScroll) < 0.4F) favoriteScroll = favoriteTargetScroll;
    }

    private float favoriteMaxScroll() {
        int contentHeight = PhoneMusicPlayerState.favoriteTrackIndexes().length * FAVORITE_ROW_HEIGHT;
        return Math.max(0.0F, contentHeight - (FAVORITES_LIST_BOTTOM - FAVORITES_LIST_TOP));
    }

    private void updateQueueScroll() {
        float max = queueMaxScroll();
        queueTargetScroll = clamp(queueTargetScroll, 0.0F, max);
        queueScroll += (queueTargetScroll - queueScroll) * 0.24F;
        if (Math.abs(queueTargetScroll - queueScroll) < 0.4F) queueScroll = queueTargetScroll;
    }

    private float queueMaxScroll() {
        int contentHeight = PhoneMusicPlayerState.tracks().length * QUEUE_ROW_HEIGHT;
        return Math.max(0.0F, contentHeight - (QUEUE_BOTTOM - QUEUE_TOP));
    }

    void showImportNotice(String message, boolean error) {
        importNotice = message;
        importNoticeError = error;
        importNoticeUntil = System.currentTimeMillis() + 4_000L;
        if (!error) queueTargetScroll = queueMaxScroll();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && handleBackButton(mouseX, mouseY)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (inside(mouseX, mouseY, 253, 47, 287, 75)) {
                PhoneNavigationState.open(minecraft, this, new ZPhoneMusicImportScreen(this));
                return true;
            }
            if (inside(mouseX, mouseY, 13, TAB_TOP, 150, TAB_BOTTOM)) {
                tab = Tab.MUSIC;
                return true;
            }
            if (inside(mouseX, mouseY, 150, TAB_TOP, 287, TAB_BOTTOM)) {
                tab = Tab.FAVORITES;
                favoriteTargetScroll = clamp(favoriteTargetScroll, 0.0F, favoriteMaxScroll());
                return true;
            }

            if (tab == Tab.MUSIC) {
                if (inside(mouseX, mouseY, 239, 278, 281, 320)) {
                    PhoneMusicPlayerState.toggleFavorite(PhoneMusicPlayerState.currentTrackIndex());
                    return true;
                }
                if (inside(mouseX, mouseY, 65, 348, 111, 392)) {
                    PhoneMusicPlayerState.previous();
                    return true;
                }
                if (inside(mouseX, mouseY, 120, 340, 180, 400)) {
                    PhoneMusicPlayerState.toggle();
                    return true;
                }
                if (inside(mouseX, mouseY, 189, 348, 235, 392)) {
                    PhoneMusicPlayerState.next();
                    return true;
                }
                Track[] tracks = PhoneMusicPlayerState.tracks();
                int contentTop = QUEUE_TOP - Math.round(queueScroll);
                for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
                    int rowTop = contentTop + trackIndex * QUEUE_ROW_HEIGHT;
                    int visibleTop = Math.max(rowTop, QUEUE_TOP);
                    int visibleBottom = Math.min(rowTop + QUEUE_ROW_HEIGHT, QUEUE_BOTTOM);
                    if (visibleTop < visibleBottom && inside(mouseX, mouseY, 22, visibleTop, 278, visibleBottom)) {
                        PhoneMusicPlayerState.play(trackIndex);
                        return true;
                    }
                }
            } else {
                int[] favorites = PhoneMusicPlayerState.favoriteTrackIndexes();
                int cardTop = FAVORITES_LIST_TOP - Math.round(favoriteScroll);
                for (int row = 0; row < favorites.length; row++) {
                    int rowTop = cardTop + row * FAVORITE_ROW_HEIGHT;
                    if (inside(mouseX, mouseY, 22, Math.max(rowTop, FAVORITES_LIST_TOP), 278,
                            Math.min(rowTop + FAVORITE_ROW_HEIGHT, FAVORITES_LIST_BOTTOM))) {
                        if (mouseX >= x(244)) {
                            PhoneMusicPlayerState.toggleFavorite(favorites[row]);
                            favoriteTargetScroll = clamp(favoriteTargetScroll, 0.0F, favoriteMaxScroll());
                        } else {
                            PhoneMusicPlayerState.play(favorites[row]);
                            tab = Tab.MUSIC;
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.MUSIC && delta != 0.0D
                && inside(mouseX, mouseY, 15, QUEUE_TOP, 285, QUEUE_BOTTOM)) {
            queueTargetScroll = clamp(queueTargetScroll + (delta < 0.0D ? 48.0F : -48.0F),
                    0.0F, queueMaxScroll());
            return true;
        }
        if (tab == Tab.FAVORITES && delta != 0.0D
                && inside(mouseX, mouseY, 15, FAVORITES_LIST_TOP, 285, FAVORITES_LIST_BOTTOM)) {
            favoriteTargetScroll = clamp(favoriteTargetScroll + (delta < 0.0D ? 48.0F : -48.0F),
                    0.0F, favoriteMaxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            PhoneMusicPlayerState.toggle();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            PhoneMusicPlayerState.previous();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            PhoneMusicPlayerState.next();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            PhoneNavigationState.backOrHome(minecraft);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static String remainingTime(Track track) {
        long remaining = Math.max(0L, track.durationMs() - PhoneMusicPlayerState.elapsedMs());
        return "-" + time(remaining);
    }

    private static String time(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Tab {
        MUSIC("Music", "Müzik"),
        FAVORITES("Favorites", "Favoriler");

        final String english;
        final String turkish;

        Tab(String english, String turkish) {
            this.english = english;
            this.turkish = turkish;
        }

        String label() {
            return PhoneSettingsStore.text(turkish, english);
        }
    }
}

package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.settings.PhoneAppLayoutStore;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.zphone.ZPhoneTransitionState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class ZPhoneHomeScreen extends AbstractZPhoneScreen {
    private static final ResourceLocation ICONS = new ResourceLocation(NewPhone.MOD_ID, "textures/gui/phone/app_icons.png");
    private static final ResourceLocation ICONS_HOVER = new ResourceLocation(NewPhone.MOD_ID, "textures/gui/phone/app_icons_hover.png");
    private static final ResourceLocation WEATHER_ICONS = new ResourceLocation(NewPhone.MOD_ID, "textures/gui/phone/weather_icons.png");
    private static final int ICON_TEXTURE_SIZE = 384;
    private static final int ICON_CELL = 96;
    private static final int WEATHER_CELL = 64;
    private static final int WEATHER_LEFT = 18;
    private static final int WEATHER_TOP = 54;
    private static final int WEATHER_RIGHT = 282;
    private static final int WEATHER_BOTTOM = 204;
    private static final int SEARCH_LEFT = 18;
    private static final int SEARCH_TOP = 223;
    private static final int SEARCH_RIGHT = 282;
    private static final int SEARCH_BOTTOM = 255;
    private static final int SEARCH_IDLE_LEFT = 100;
    private static final int SEARCH_IDLE_RIGHT = 200;
    private static final int SEARCH_CENTER_Y = (SEARCH_TOP + SEARCH_BOTTOM) / 2;
    private static final long APP_LAUNCH_DURATION_MS = 280L;
    private static final long PAGE_TRANSITION_DURATION_MS = 320L;
    private static int rememberedPage;

    private static final int[] PAGE_ONE_X = {62, 150, 238, 62, 150, 238};
    private static final int[] PAGE_ONE_Y = {319, 319, 319, 436, 436, 436};
    private static final int[] GRID_X = {62, 150, 238, 62, 150, 238, 62, 150, 238};
    private static final int[] GRID_Y = {150, 150, 150, 275, 275, 275, 400, 400, 400};
    private static final int[] ALL_APPS_X = {
            42, 114, 186, 258,
            42, 114, 186, 258,
            42, 114, 186, 258,
            42, 114, 186, 258,
            42, 114, 186, 258
    };
    private static final int[] ALL_APPS_Y = {
            77, 77, 77, 77,
            169, 169, 169, 169,
            261, 261, 261, 261,
            353, 353, 353, 353,
            445, 445, 445, 445
    };
    private static final int[] DOCK_X = {110, 190};
    private static final int DOCK_Y = 531;

    private PhoneApp launchingApp;
    private int launchOriginX;
    private int launchOriginY;
    private long launchStartedAt;
    private int appPage;
    private int pageFrom;
    private int pageTo;
    private long pageTransitionStartedAt;
    private boolean pageTransitioning;
    private float pageTransitionStartOffset;
    private boolean pageDragTracking;
    private double pageDragStartX;
    private float pageDragOffset;
    private boolean pageSnapBack;
    private float pageSnapStartOffset;
    private long pageSnapStartedAt;
    private static final long PAGE_SNAP_DURATION_MS = 180L;
    private final StringBuilder searchText = new StringBuilder();
    private boolean searchFocused;
    private final com.mikasrevs.newphone.client.zphone.PhoneSearchMotion searchMotion =
            new com.mikasrevs.newphone.client.zphone.PhoneSearchMotion();
    private int searchPage;
    private boolean editMode;
    private int selectedEditSlot = -1;
    private int addPickerSlot = -1;

    public ZPhoneHomeScreen() {
        this(rememberedPage);
    }

    public ZPhoneHomeScreen(int initialPage) {
        super(Component.translatable("screen.newphone.z_phone.home.title"));
        this.appPage = Math.max(0, initialPage);
        rememberedPage = this.appPage;
    }

    public int currentPage() {
        return appPage;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        if (allAppsLayout()) {
            appPage = 0;
            rememberedPage = 0;
            pageTransitioning = false;
            pageDragTracking = false;
            pageSnapBack = false;
            searchFocused = false;
            searchText.setLength(0);
            searchPage = 0;
            editMode = false;
            selectedEditSlot = -1;
            addPickerSlot = -1;
        }
        if (this.launchingApp != null) {
            drawAppLaunchTransition(graphics);
            drawFrame(graphics);
            return;
        }

        drawFrame(graphics);
        drawWallpaper(graphics);

        boolean homeReturning = ZPhoneTransitionState.kind() == ZPhoneTransitionState.Kind.HOME_RETURN;
        float homeReturnProgress = homeReturning ? ZPhoneTransitionState.progress() : 1.0F;
        if (homeReturning) {
            float contentScale = 0.94F + (0.06F * homeReturnProgress);
            float contentOffsetY = size(12) * (1.0F - homeReturnProgress);
            int centerX = x(150);
            int centerY = y(305);
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, centerY + contentOffsetY, 0.0F);
            graphics.pose().scale(contentScale, contentScale, 1.0F);
            graphics.pose().translate(-centerX, -centerY, 0.0F);
        }

        drawStatusBar(graphics);

        if (pageDragTracking && searchText.isEmpty()) {
            int fromOffset = Math.round(pageDragOffset);
            drawPageContent(graphics, appPage, fromOffset, mouseX, mouseY);
            int target = pageDragTarget();
            if (target >= 0) {
                int direction = target > appPage ? 1 : -1;
                drawPageContent(graphics, target, fromOffset + direction * DESIGN_WIDTH, mouseX, mouseY);
            }
        } else if (pageSnapBack && searchText.isEmpty()) {
            float progress = pageSnapProgress();
            int fromOffset = Math.round(pageSnapStartOffset * (1.0F - progress));
            drawPageContent(graphics, appPage, fromOffset, mouseX, mouseY);
            int target = pageSnapStartOffset < 0 ? appPage + 1 : appPage - 1;
            if (target >= 0 && target < pageCount()) {
                int direction = target > appPage ? 1 : -1;
                drawPageContent(graphics, target, fromOffset + direction * DESIGN_WIDTH, mouseX, mouseY);
            }
        } else if (pageTransitioning && searchText.isEmpty()) {
            float progress = pageTransitionProgress();
            int direction = pageTo > pageFrom ? 1 : -1;
            float endOffset = -direction * DESIGN_WIDTH;
            int fromOffset = Math.round(pageTransitionStartOffset + (endOffset - pageTransitionStartOffset) * progress);
            int toOffset = fromOffset + direction * DESIGN_WIDTH;
            drawPageContent(graphics, pageFrom, fromOffset, mouseX, mouseY);
            drawPageContent(graphics, pageTo, toOffset, mouseX, mouseY);
        } else {
            drawPageContent(graphics, appPage, 0, mouseX, mouseY);
        }

        drawAppPageIndicator(graphics, mouseX, mouseY);
        drawDock(graphics, mouseX, mouseY);
        if (editMode) {
            drawEditChrome(graphics);
            if (addPickerSlot >= 0) drawAddAppPicker(graphics, mouseX, mouseY);
        }

        if (homeReturning) {
            graphics.pose().popPose();
            int veilAlpha = Math.round(92.0F * (1.0F - homeReturnProgress));
            if (veilAlpha > 0) graphics.fill(x(0), y(0), x(300), y(610), veilAlpha << 24);
        }

        drawHomeIndicator(graphics);
        drawFrame(graphics);
    }

    private void drawPageContent(GuiGraphics graphics, int page, int offset, int mouseX, int mouseY) {
        graphics.enableScissor(x(8), y(40), x(292), y(486));
        if (allAppsLayout()) {
            drawAllApps(graphics, offset, mouseX, mouseY);
            graphics.disableScissor();
            return;
        }
        if (page == 0) {
            drawWeatherCard(graphics, offset);
            drawSearchBar(graphics, offset);
        }
        drawAppsForPage(graphics, page, offset, mouseX, mouseY);
        if (editMode) drawEditOverlayForPage(graphics, page, offset);
        graphics.disableScissor();
    }

    private void drawWeatherCard(GuiGraphics graphics, int offset) {
        WeatherVisual weather = resolveWeather();
        boolean night = isWeatherNight();
        int left = x(WEATHER_LEFT + offset), right = x(WEATHER_RIGHT + offset);
        int top = y(WEATHER_TOP), bottom = y(WEATHER_BOTTOM);
        drawRoundedRect(graphics, left, top + size(2), right, bottom + size(2), size(24), 0x24000000);
        drawWeatherSurface(graphics, left, top, right, bottom, size(24),
                weather.topColor(night), weather.bottomColor(night));

        PhoneModernText.drawColoredNoShadow(graphics, "Minecraft", x(36 + offset), y(72), size(12), 0xFFFFFFFF);
        PhoneModernText.drawColoredNoShadow(graphics, weather.temperature, x(33 + offset), y(96), size(43), 0xFFFFFFFF);
        PhoneModernText.drawColoredNoShadow(graphics, weather.localizedDescription(night),
                x(36 + offset), y(150), size(10), 0xFFFFFFFF);
        PhoneModernText.drawColoredNoShadow(graphics, weather.range,
                x(36 + offset), y(173), size(8), 0xDDEAF3FA);

        drawWeatherSymbol(graphics, offset, weather, night);
        PhoneModernText.drawCenteredColoredNoShadow(graphics, PhoneSettingsStore.text("Yağış", "Rain"),
                x(238 + offset), y(151), size(7), 0xCDEAF3FA);
        int rain = minecraft == null || minecraft.level == null
                ? 0 : Math.round(minecraft.level.getRainLevel(1.0F) * 100.0F);
        PhoneModernText.drawCenteredColoredNoShadow(graphics, rain + "%",
                x(238 + offset), y(168), size(13), 0xFFFFFFFF);
    }

    /** One continuous rounded surface; it also keeps the parent page-swipe scissor intact. */
    private void drawWeatherSurface(GuiGraphics graphics, int left, int top, int right, int bottom,
                                    int radius, int topColor, int bottomColor) {
        int height = bottom - top;
        int r = Math.max(1, Math.min(radius, Math.min((right - left) / 2, height / 2)));
        for (int row = 0; row < height; row++) {
            double dy = row < r ? r - row - 0.5D
                    : row >= height - r ? row - (height - r) + 0.5D : 0.0D;
            int inset = dy == 0.0D ? 0 : (int) Math.ceil(r - Math.sqrt(Math.max(0.0D, r * r - dy * dy)));
            float progress = row / (float) Math.max(1, height - 1);
            int red = Math.round(((topColor >> 16) & 255) * (1 - progress) + ((bottomColor >> 16) & 255) * progress);
            int green = Math.round(((topColor >> 8) & 255) * (1 - progress) + ((bottomColor >> 8) & 255) * progress);
            int blue = Math.round((topColor & 255) * (1 - progress) + (bottomColor & 255) * progress);
            graphics.fill(left + inset, top + row, right - inset, top + row + 1,
                    0xFF000000 | red << 16 | green << 8 | blue);
        }
    }

    private WeatherVisual resolveWeather() {
        if (minecraft == null || minecraft.level == null) return WeatherVisual.CLEAR;
        if (minecraft.level.isThundering()) return WeatherVisual.THUNDER;
        if (minecraft.level.isRaining()) return WeatherVisual.RAIN;
        return WeatherVisual.CLEAR;
    }

    private boolean isWeatherNight() {
        if (minecraft == null || minecraft.level == null) return false;
        long dayTime = Math.floorMod(minecraft.level.getDayTime(), 24_000L);
        return dayTime >= 13_000L && dayTime < 23_000L;
    }

    private void drawWeatherSymbol(GuiGraphics graphics, int offset, WeatherVisual weather, boolean night) {
        if (weather == WeatherVisual.CLEAR && night) {
            // Draw the crescent itself so its cutout reveals the gradient without an opaque patch.
            int centerX = x(238 + offset), centerY = y(106), radius = size(19);
            int cutX = size(8), cutY = -size(6), cutRadius = size(18);
            for (int dy = -radius; dy <= radius; dy++) {
                int outer = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
                int innerY = dy - cutY;
                int end = outer;
                if (Math.abs(innerY) <= cutRadius) {
                    int inner = (int) Math.sqrt(Math.max(0, cutRadius * cutRadius - innerY * innerY));
                    end = Math.min(end, cutX - inner);
                }
                if (end > -outer) graphics.fill(centerX - outer, centerY + dy, centerX + end, centerY + dy + 1, 0xFFF5F2DE);
            }
            return;
        }
        graphics.blit(WEATHER_ICONS, x(213 + offset), y(81), size(50), size(50),
                (float) (weather.iconIndex * WEATHER_CELL), 0.0F, WEATHER_CELL, WEATHER_CELL, 256, 64);
    }

    private boolean searchExpanded() {
        return searchFocused || !searchText.isEmpty();
    }

    private int searchLeft() {
        return com.mikasrevs.newphone.client.zphone.PhoneSearchMotion.left(searchProgress());
    }

    private int searchRight() {
        return com.mikasrevs.newphone.client.zphone.PhoneSearchMotion.right(searchProgress());
    }

    private float searchProgress() {
        return searchMotion.sample(searchExpanded(), PhoneSettingsStore.animationsEnabled(), System.nanoTime());
    }

    private void drawSearchBar(GuiGraphics graphics, int offset) {
        float expansion = searchProgress();
        int designLeft = com.mikasrevs.newphone.client.zphone.PhoneSearchMotion.left(expansion) + offset;
        int designRight = com.mikasrevs.newphone.client.zphone.PhoneSearchMotion.right(expansion) + offset;
        int left = x(designLeft), top = y(SEARCH_TOP), right = x(designRight), bottom = y(SEARCH_BOTTOM);
        drawRoundedRect(graphics, left, top + size(1), right, bottom + size(1), size(16), 0x18000000);
        drawRoundedRect(graphics, left, top, right, bottom, size(16), searchFocused ? 0x40FFFFFF : 0x20FFFFFF);
        drawRoundedRect(graphics, left + size(1), top + size(1), right - size(1), bottom - size(1),
                size(15), searchFocused ? 0xE0242C38 : 0xB0242C38);

        String idleLabel = PhoneSettingsStore.text("Ara", "Search");
        int idleHeight = size(9);
        int groupLeft = x(150 + offset) - (size(21) + PhoneModernText.width(idleLabel, idleHeight)) / 2;
        int iconX = Math.round((groupLeft + size(5)) * (1F - expansion) + x(designLeft + 17) * expansion);
        drawSearchGlyph(graphics, iconX, y(SEARCH_CENTER_Y - 2), 0xE6FFFFFF);
        float idleOpacity = Math.max(0F, 1F - expansion * 3F);
        if (idleOpacity > 0F) PhoneModernText.drawColoredNoShadow(graphics, idleLabel,
                groupLeft + size(21), y(SEARCH_CENTER_Y) - idleHeight / 2, idleHeight,
                (Math.round(242 * idleOpacity) << 24) | 0xFFFFFF);
        float textOpacity = Math.max(0F, (expansion - .25F) / .75F);
        if (textOpacity <= 0F) return;
        graphics.enableScissor(left + size(28), top + size(3), right - size(8), bottom - size(3));
        int textLeft = x(designLeft + 35), textRight = x(designRight - 39);
        String label = searchText.isEmpty() ? PhoneSettingsStore.text("Uygulama ara", "Search apps") : searchText.toString();
        int textHeight = PhoneModernText.fittedHeight(label, size(10), Math.max(1, textRight - textLeft));
        int textY = y(SEARCH_CENTER_Y) - textHeight / 2;
        PhoneModernText.drawColoredNoShadow(graphics, label, textLeft, textY, textHeight,
                (Math.round((searchText.isEmpty() ? 166 : 255) * textOpacity) << 24) | 0xFFFFFF);

        if (!searchText.isEmpty()) {
            int clearX = designRight - 18;
            fillCircle(graphics, x(clearX), y(SEARCH_CENTER_Y), size(7), 0x33FFFFFF);
            drawSearchLine(graphics, clearX - 2, SEARCH_CENTER_Y - 2, clearX + 2, SEARCH_CENTER_Y + 2,
                    0xE6FFFFFF, Math.max(1, size(0.5F)));
            drawSearchLine(graphics, clearX + 2, SEARCH_CENTER_Y - 2, clearX - 2, SEARCH_CENTER_Y + 2,
                    0xE6FFFFFF, Math.max(1, size(0.5F)));
        }
        if (searchFocused && expansion > .85F && isCaretVisible()) {
            // Text widths are already screen pixels; do not scale them a second time.
            int caretX = searchText.isEmpty() ? textLeft
                    : Math.min(textRight, textLeft + PhoneModernText.width(searchText.toString(), textHeight) + size(2));
            graphics.fill(caretX, y(SEARCH_CENTER_Y - 7), caretX + Math.max(1, size(1)),
                    y(SEARCH_CENTER_Y + 7), 0xFF8ABEFF);
        }
        graphics.disableScissor();
    }

    /** Pixel coordinates keep the icon and localized label centered at every phone scale. */
    private void drawSearchGlyph(GuiGraphics graphics, int centerX, int centerY, int color) {
        int radius = size(4);
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2.0D * i / 24.0D;
            int px = centerX + (int) Math.round(Math.cos(angle) * radius);
            int py = centerY + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(px, py, px + Math.max(1, size(1)), py + Math.max(1, size(1)), color);
        }
        for (int step = 0; step <= size(4); step++) {
            int px = centerX + size(3) + step, py = centerY + size(3) + step;
            graphics.fill(px, py, px + Math.max(1, size(1)), py + Math.max(1, size(1)), color);
        }
    }

    private void drawSearchLine(GuiGraphics graphics, int fromX, int fromY, int toX, int toY,
                                int color, int thickness) {
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY)) * 2;
        for (int i = 0; i <= steps; i++) {
            double progress = steps == 0 ? 0.0D : (double) i / steps;
            int px = (int) Math.round(fromX + (toX - fromX) * progress);
            int py = (int) Math.round(fromY + (toY - fromY) * progress);
            fillCircle(graphics, x(px), y(py), thickness, color);
        }
    }

    private void drawAppPageIndicator(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean searching = !searchText.isEmpty();
        int count = searching ? searchResultPages().size() : pageCount();
        if (count <= 1) return;
        int shownPage = searching ? Math.min(count - 1, searchPage)
                : Math.min(count - 1, pageTransitioning ? pageTo : appPage);
        int centerY = 485;
        int firstCenter = 150 - ((count - 1) * 14) / 2;
        int backgroundWidth = Math.max(40, count * 14 + 18);
        drawRoundedRect(graphics, x(150 - backgroundWidth / 2), y(477), x(150 + backgroundWidth / 2), y(491), size(7), 0x300B111B);
        for (int i = 0; i < count; i++) {
            int centerX = firstCenter + i * 14;
            boolean hover = inside(mouseX, mouseY, centerX - 7, centerY - 7, centerX + 7, centerY + 7);
            int radius = i == shownPage ? size(4) : size(3);
            int color = i == shownPage ? 0xFFF3F6FB : hover ? 0xA6C2D1E0 : 0x6B94A6B8;
            fillCircle(graphics, x(centerX), y(centerY), radius, color);
        }
    }

    private void drawAppsForPage(GuiGraphics graphics, int page, int offset, int mouseX, int mouseY) {
        List<PhoneApp> apps;
        int[] xs;
        int[] ys;
        if (!searchText.isEmpty()) {
            if (page != 0) return;
            apps = visibleApps();
            xs = PAGE_ONE_X;
            ys = PAGE_ONE_Y;
        } else {
            apps = appsForPage(page);
            xs = page == 0 ? PAGE_ONE_X : GRID_X;
            ys = page == 0 ? PAGE_ONE_Y : GRID_Y;
        }

        int slotCount = searchText.isEmpty() ? xs.length : Math.min(apps.size(), xs.length);
        for (int i = 0; i < slotCount; i++) {
            PhoneApp app = i < apps.size() ? apps.get(i) : null;
            int centerX = xs[i] + offset;
            int centerY = ys[i];

            if (app == null) continue;

            drawApp(graphics, app, centerX, centerY, mouseX, mouseY, true, false);
        }
    }

    private void drawAllApps(GuiGraphics graphics, int offset, int mouseX, int mouseY) {
        List<PhoneApp> apps = PhoneAppLayoutStore.visibleHomeApps();
        int max = Math.min(apps.size(), ALL_APPS_X.length);
        for (int i = 0; i < max; i++) {
            drawApp(graphics, apps.get(i), ALL_APPS_X[i] + offset, ALL_APPS_Y[i], mouseX, mouseY, true, false, true);
        }
    }

    private void drawDock(GuiGraphics graphics, int mouseX, int mouseY) {
        drawRoundedRect(graphics, x(40), y(491), x(260), y(568), size(22), 0x4E1E2A41);
        drawRoundedRect(graphics, x(41), y(492), x(259), y(567), size(21), 0x19FFFFFF);
        List<PhoneApp> dock = PhoneAppLayoutStore.dockApps();
        int max = Math.min(dock.size(), DOCK_X.length);
        for (int i = 0; i < max; i++) {
            PhoneApp app = dock.get(i);
            drawApp(graphics, app, DOCK_X[i], DOCK_Y, mouseX, mouseY, false, true);
        }
    }

    private void drawApp(GuiGraphics graphics, PhoneApp app, int centerX, int centerY, int mouseX, int mouseY, boolean label, boolean dock) {
        drawApp(graphics, app, centerX, centerY, mouseX, mouseY, label, dock, false);
    }

    private void drawApp(GuiGraphics graphics, PhoneApp app, int centerX, int centerY, int mouseX, int mouseY,
                         boolean label, boolean dock, boolean compact) {
        int hoverRadius = compact ? 24 : 29;
        boolean hover = inside(mouseX, mouseY, centerX - hoverRadius, centerY - hoverRadius,
                centerX + hoverRadius, centerY + (label ? (compact ? 33 : 36) : hoverRadius));
        int iconDesignSize = compact ? 38 : dock ? 52 : 50;
        int iconSize = size(iconDesignSize);

        if (MPhoneAppIconRenderer.handles(app)) {
            MPhoneAppIconRenderer.draw(graphics, app, x(centerX) - iconSize / 2, y(centerY) - iconSize / 2, iconSize, hover);
        } else {
            int sourceX = (app.iconIndex() % 4) * ICON_CELL;
            int sourceY = (app.iconIndex() / 4) * ICON_CELL;
            ResourceLocation texture = hover ? ICONS_HOVER : ICONS;
            graphics.blit(
                    texture,
                    x(centerX) - iconSize / 2, y(centerY) - iconSize / 2,
                    iconSize, iconSize, (float) sourceX, (float) sourceY,
                    ICON_CELL, ICON_CELL, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE
            );
        }

        if (app == PhoneApp.MARKETPLACE) {
            int unread = com.mikasrevs.newphone.marketplace.client.MarketplaceClient.unread();
            if (unread > 0) {
                int offset = compact ? 15 : 20;
                fillCircle(graphics,x(centerX+offset),y(centerY-offset),size(compact?7:9),0xFFFF3B5C);
                PhoneModernText.drawCenteredColored(graphics,unread>9?"9+":Integer.toString(unread),x(centerX+offset),y(centerY-offset-(compact?4:5)),size(compact?5:6),0xFFFFFFFF);
            }
        }
        if (app == PhoneApp.BROWSER) {
            int unreadTinder = PhoneSocialClientData.unreadNotificationCount("TINDER", PhoneClientData.selfUuid())
                    + PhoneClientData.totalUnreadTinderCount();
            if (unreadTinder > 0) {
                int badgeOffset = compact ? 15 : 20;
                fillCircle(graphics, x(centerX + badgeOffset), y(centerY - badgeOffset), size(compact ? 7 : 9), 0xFFFF3B5C);
                PhoneModernText.drawCenteredColored(graphics, unreadTinder > 9 ? "9+" : Integer.toString(unreadTinder),
                        x(centerX + badgeOffset), y(centerY - badgeOffset - (compact ? 4 : 5)), size(compact ? 5 : 6), 0xFFFFFFFF);
            }
        }
        if (label) {
            String labelText = app == PhoneApp.BLOCKGRAM ? "Lumi" : app.displayName();
            int labelX = x(centerX);
            int labelY = y(centerY + (compact ? 27 : 35));
            int labelSize = size(compact ? 6 : 7);
            PhoneModernText.drawCenteredColoredFitted(
                    graphics, labelText, labelX, labelY, labelSize, size(compact ? 68 : 82), 0xFFF8FAFD);
        }
    }

    private List<List<PhoneApp>> appPages() {
        List<PhoneApp> slots = new ArrayList<>(PhoneAppLayoutStore.homeSlots());
        while (slots.size() < 15) slots.add(null);

        List<List<PhoneApp>> pages = new ArrayList<>();
        pages.add(new ArrayList<>(slots.subList(0, Math.min(6, slots.size()))));
        int index = 6;
        while (index < slots.size()) {
            int end = Math.min(slots.size(), index + 9);
            pages.add(new ArrayList<>(slots.subList(index, end)));
            index = end;
        }
        if (pages.size() == 1) pages.add(new ArrayList<>());
        return pages;
    }

    private int pageCount() {
        if (allAppsLayout()) return 1;
        return appPages().size();
    }

    private boolean allAppsLayout() {
        return PhoneSettingsStore.homeLayoutMode() == PhoneSettingsStore.HomeLayoutMode.ALL_APPS;
    }

    private List<PhoneApp> appsForPage(int page) {
        List<List<PhoneApp>> pages = appPages();
        int safe = Math.max(0, Math.min(pages.size() - 1, page));
        return pages.get(safe);
    }

    private List<PhoneApp> searchApps() {
        List<PhoneApp> result = new ArrayList<>(PhoneAppLayoutStore.visibleHomeApps());
        result.addAll(PhoneAppLayoutStore.dockApps());
        return result;
    }

    private List<PhoneApp> visibleApps() {
        if (searchText.isEmpty()) return appsForPage(appPage);
        List<List<PhoneApp>> pages = searchResultPages();
        searchPage = Math.max(0, Math.min(searchPage, pages.size() - 1));
        return pages.get(searchPage);
    }

    private List<List<PhoneApp>> searchResultPages() {
        String query = searchText.toString().trim().toLowerCase(Locale.ROOT);
        List<PhoneApp> result = new ArrayList<>();
        for (PhoneApp app : searchApps()) {
            String name = (app == PhoneApp.BLOCKGRAM ? "Lumi" : app.displayName()).toLowerCase(Locale.ROOT);
            if (name.contains(query)) result.add(app);
        }
        return paginateSearchResults(result);
    }

    private static List<List<PhoneApp>> paginateSearchResults(List<PhoneApp> matches) {
        List<List<PhoneApp>> pages = new ArrayList<>();
        for (int start = 0; start < matches.size(); start += PAGE_ONE_X.length) {
            pages.add(new ArrayList<>(matches.subList(start, Math.min(start + PAGE_ONE_X.length, matches.size()))));
        }
        if (pages.isEmpty()) pages.add(List.of());
        return pages;
    }

    private void drawEditOverlayForPage(GuiGraphics graphics, int page, int offset) {
        int[] xs = page == 0 ? PAGE_ONE_X : GRID_X;
        int[] ys = page == 0 ? PAGE_ONE_Y : GRID_Y;
        int base = pageSlotBase(page);
        List<PhoneApp> pageApps = appsForPage(page);

        for (int i = 0; i < xs.length; i++) {
            int slot = base + i;
            PhoneApp app = i < pageApps.size() ? pageApps.get(i) : null;
            int centerX = xs[i] + offset;
            int centerY = ys[i];

            if (app == null) {
                fillCircle(graphics, x(centerX), y(centerY), size(22), 0x252A3544);
                PhoneModernText.drawCenteredColored(graphics, "+", x(centerX), y(centerY - 10), size(16), 0x809DAABA);
                continue;
            }

            if (slot == selectedEditSlot) {
                int left = x(centerX - 29);
                int top = y(centerY - 29);
                int right = x(centerX + 29);
                int bottom = y(centerY + 29);
                int stroke = Math.max(1, size(2));
                graphics.fill(left, top, right, top + stroke, 0xFF6BB5FF);
                graphics.fill(left, bottom - stroke, right, bottom, 0xFF6BB5FF);
                graphics.fill(left, top, left + stroke, bottom, 0xFF6BB5FF);
                graphics.fill(right - stroke, top, right, bottom, 0xFF6BB5FF);
            }

            fillCircle(graphics, x(centerX + 21), y(centerY - 21), size(9), 0xFFF04452);
            PhoneModernText.drawCenteredColored(graphics, "−", x(centerX + 21), y(centerY - 28), size(11), 0xFFFFFFFF);
        }
    }

    private void drawEditChrome(GuiGraphics graphics) {
        drawRoundedRect(graphics, x(16), y(43), x(284), y(88), size(15), 0xF019222E);
        drawRoundedRect(graphics, x(23), y(51), x(82), y(80), size(10), 0xFF323D4B);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Sıfırla", "Reset"), x(52), y(59), size(7), 0xFFE7EDF4);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Ana Ekranı Düzenle", "Edit Home Screen"), x(150), y(58), size(8), 0xFFFFFFFF);
        drawRoundedRect(graphics, x(224), y(51), x(277), y(80), size(10), 0xFF2D78C7);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Bitti", "Done"), x(250), y(59), size(7), 0xFFFFFFFF);
    }

    private void drawAddAppPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        List<PhoneApp> removed = PhoneAppLayoutStore.removedHomeApps();
        graphics.fill(x(8), y(40), x(292), y(568), 0xB8000000);
        drawRoundedRect(graphics, x(24), y(104), x(276), y(472), size(18), 0xFF111923);
        drawRoundedRect(graphics, x(25), y(105), x(275), y(471), size(17), 0xFF182330);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Uygulama Ekle", "Add App"), x(39), y(119), size(10), 0xFFFFFFFF);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Silinen uygulamalardan birini seç", "Choose one of the removed apps"), x(39), y(139), size(6), 0xFF93A4B8);
        drawRoundedRect(graphics, x(239), y(116), x(263), y(140), size(8), 0xFF303C49);
        PhoneModernText.drawCenteredColored(graphics, "×", x(251), y(118), size(10), 0xFFFFFFFF);

        if (removed.isEmpty()) {
            PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Silinmiş uygulama yok", "No removed apps"), x(150), y(286), size(8), 0xFFB8C4D2);
            return;
        }

        int max = Math.min(18, removed.size());
        for (int i = 0; i < max; i++) {
            PhoneApp app = removed.get(i);
            int col = i % 2;
            int row = i / 2;
            int left = 35 + col * 118;
            int top = 162 + row * 32;
            int right = left + 108;
            int bottom = top + 26;
            boolean hover = inside(mouseX, mouseY, left, top, right, bottom);
            drawRoundedRect(graphics, x(left), y(top), x(right), y(bottom), size(8), hover ? 0xFF2B72B9 : 0xFF222E3B);
            fillCircle(graphics, x(left + 13), y(top + 13), size(6), app.launchColor());
            String name = app == PhoneApp.BLOCKGRAM ? "Lumi" : app.displayName();
            if (name.length() > 15) name = name.substring(0, 14) + "…";
            PhoneModernText.drawColored(graphics, name, x(left + 25), y(top + 7), size(6), 0xFFF0F5FA);
        }
    }

    private int addPickerIndexAt(double mouseX, double mouseY) {
        List<PhoneApp> removed = PhoneAppLayoutStore.removedHomeApps();
        int max = Math.min(18, removed.size());
        for (int i = 0; i < max; i++) {
            int col = i % 2;
            int row = i / 2;
            int left = 35 + col * 118;
            int top = 162 + row * 32;
            if (inside(mouseX, mouseY, left, top, left + 108, top + 26)) return i;
        }
        return -1;
    }

    private int pageSlotBase(int page) {
        return page <= 0 ? 0 : 6 + (page - 1) * 9;
    }

    private int homeSlotAt(double mouseX, double mouseY) {
        if (allAppsLayout() || pageTransitioning || pageDragTracking || pageSnapBack || !searchText.isEmpty()) return -1;
        int[] xs = appPage == 0 ? PAGE_ONE_X : GRID_X;
        int[] ys = appPage == 0 ? PAGE_ONE_Y : GRID_Y;
        int base = pageSlotBase(appPage);
        for (int i = 0; i < xs.length; i++) {
            if (inside(mouseX, mouseY, xs[i] - 31, ys[i] - 31, xs[i] + 31, ys[i] + 39)) return base + i;
        }
        return -1;
    }

    private int removeBadgeAt(double mouseX, double mouseY) {
        if (!editMode || allAppsLayout()) return -1;
        int[] xs = appPage == 0 ? PAGE_ONE_X : GRID_X;
        int[] ys = appPage == 0 ? PAGE_ONE_Y : GRID_Y;
        int base = pageSlotBase(appPage);
        for (int i = 0; i < xs.length; i++) {
            int slot = base + i;
            if (PhoneAppLayoutStore.homeAppAt(slot) == null) continue;
            int cx = xs[i] + 21;
            int cy = ys[i] - 21;
            if (inside(mouseX, mouseY, cx - 12, cy - 12, cx + 12, cy + 12)) return slot;
        }
        return -1;
    }

    private void enterEditMode(int selectedSlot) {
        editMode = true;
        selectedEditSlot = selectedSlot;
        addPickerSlot = -1;
        searchFocused = false;
        searchText.setLength(0);
        searchPage = 0;
        launchingApp = null;
        pageDragTracking = false;
        pageSnapBack = false;
    }

    private void leaveEditMode() {
        editMode = false;
        selectedEditSlot = -1;
        addPickerSlot = -1;
    }

    private boolean handleEditClick(double mouseX, double mouseY) {
        if (!editMode) return false;

        if (addPickerSlot >= 0) {
            if (inside(mouseX, mouseY, 239, 116, 263, 140) || !inside(mouseX, mouseY, 24, 104, 276, 472)) {
                addPickerSlot = -1;
                return true;
            }
            int pickerIndex = addPickerIndexAt(mouseX, mouseY);
            if (pickerIndex >= 0) {
                List<PhoneApp> removed = PhoneAppLayoutStore.removedHomeApps();
                if (pickerIndex < removed.size()) PhoneAppLayoutStore.addHomeAppAt(addPickerSlot, removed.get(pickerIndex));
                addPickerSlot = -1;
                selectedEditSlot = -1;
            }
            return true;
        }

        if (inside(mouseX, mouseY, 23, 51, 82, 80)) {
            PhoneAppLayoutStore.resetHomeLayout();
            appPage = 0;
            rememberedPage = 0;
            selectedEditSlot = -1;
            addPickerSlot = -1;
            return true;
        }
        if (inside(mouseX, mouseY, 224, 51, 277, 80)) {
            leaveEditMode();
            return true;
        }

        int count = pageCount();
        int firstCenter = 150 - ((count - 1) * 14) / 2;
        for (int i = 0; i < count; i++) {
            int centerX = firstCenter + i * 14;
            if (inside(mouseX, mouseY, centerX - 10, 476, centerX + 10, 492)) {
                startPageTransition(i);
                selectedEditSlot = -1;
                addPickerSlot = -1;
                return true;
            }
        }

        int removeSlot = removeBadgeAt(mouseX, mouseY);
        if (removeSlot >= 0) {
            PhoneAppLayoutStore.removeHomeAppAt(removeSlot);
            if (selectedEditSlot == removeSlot) selectedEditSlot = -1;
            return true;
        }

        int slot = homeSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            PhoneApp app = PhoneAppLayoutStore.homeAppAt(slot);
            if (selectedEditSlot < 0) {
                if (app != null) {
                    selectedEditSlot = slot;
                } else if (!PhoneAppLayoutStore.removedHomeApps().isEmpty()) {
                    addPickerSlot = slot;
                }
                return true;
            }
            if (selectedEditSlot == slot) {
                selectedEditSlot = -1;
                return true;
            }
            PhoneAppLayoutStore.moveHomeApp(selectedEditSlot, slot);
            selectedEditSlot = -1;
            return true;
        }
        return true;
    }

    private void startPageTransition(int targetPage) {
        if (targetPage < 0 || targetPage >= pageCount() || targetPage == appPage || pageTransitioning) return;
        if (editMode || !PhoneSettingsStore.animationsEnabled()) {
            appPage = targetPage;
            rememberedPage = appPage;
            return;
        }
        searchFocused = false;
        searchText.setLength(0);
        searchPage = 0;
        pageFrom = appPage;
        pageTo = targetPage;
        pageTransitionStartOffset = 0.0F;
        pageTransitionStartedAt = System.currentTimeMillis();
        pageTransitioning = true;
    }

    private float pageTransitionProgress() {
        float raw = clamp((System.currentTimeMillis() - pageTransitionStartedAt) / (float) PAGE_TRANSITION_DURATION_MS, 0.0F, 1.0F);
        return raw < 0.5F
                ? 4.0F * raw * raw * raw
                : 1.0F - (float) Math.pow(-2.0F * raw + 2.0F, 3.0D) / 2.0F;
    }

    private int pageDragTarget() {
        if (pageDragOffset < 0.0F && appPage < pageCount() - 1) return appPage + 1;
        if (pageDragOffset > 0.0F && appPage > 0) return appPage - 1;
        return -1;
    }

    private float pageSnapProgress() {
        float raw = clamp((System.currentTimeMillis() - pageSnapStartedAt) / (float) PAGE_SNAP_DURATION_MS, 0.0F, 1.0F);
        return 1.0F - (1.0F - raw) * (1.0F - raw) * (1.0F - raw);
    }

    private void finishPageDrag() {
        int target = pageDragTarget();
        float threshold = 62.0F;
        float releasedOffset = pageDragOffset;
        pageDragTracking = false;
        pageDragOffset = 0.0F;
        if (target >= 0 && Math.abs(releasedOffset) >= threshold) {
            pageFrom = appPage;
            pageTo = target;
            pageTransitionStartOffset = releasedOffset;
            pageTransitionStartedAt = System.currentTimeMillis();
            pageTransitioning = true;
            pageSnapBack = false;
        } else if (Math.abs(releasedOffset) > 1.0F) {
            pageSnapStartOffset = releasedOffset;
            pageSnapStartedAt = System.currentTimeMillis();
            pageSnapBack = true;
        }
    }

    private void startAppLaunch(PhoneApp app, int originX, int originY) {
        rememberedPage = appPage;
        if (!PhoneSettingsStore.animationsEnabled()) {
            ZPhoneClient.openSharedApp(app);
            return;
        }
        if (this.launchingApp != null) return;
        this.launchingApp = app;
        this.launchOriginX = originX;
        this.launchOriginY = originY;
        this.launchStartedAt = System.currentTimeMillis();
        this.searchFocused = false;
    }

    private void drawAppLaunchTransition(GuiGraphics graphics) {
        if (this.launchingApp == null) return;

        float raw = (System.currentTimeMillis() - this.launchStartedAt) / (float) APP_LAUNCH_DURATION_MS;
        float progress = clamp(raw, 0.0F, 1.0F);
        float eased = easeOutCubic(progress);
        int originX = x(this.launchOriginX);
        int originY = y(this.launchOriginY);
        int half = size(23);
        int left = lerp(originX - half, x(0), eased);
        int top = lerp(originY - half, y(0), eased);
        int right = lerp(originX + half, x(300), eased);
        int bottom = lerp(originY + half, y(610), eased);

        // The transition is physically clipped to the Classic glass opening. It can
        // never paint into the real Classic bezel/case, even on the final frame.
        graphics.enableScissor(x(0), y(0), x(300), y(610));
        graphics.fill(x(0), y(0), x(300), y(610), darken(this.launchingApp.launchColor(), 0.34F));
        graphics.fill(left, top, right, bottom, this.launchingApp.launchColor());

        float logoMove = easeOutCubic(clamp(progress / 0.72F, 0.0F, 1.0F));
        int logoX = lerp(originX, x(150), logoMove);
        int logoY = lerp(originY, y(305), logoMove);
        int logoSize = size(lerp(28, 62, easeOutCubic(clamp((progress - 0.08F) / 0.70F, 0.0F, 1.0F))));
        drawTransitionLogo(graphics, this.launchingApp, logoX, logoY, logoSize);
        graphics.disableScissor();
    }

    private void drawTransitionLogo(GuiGraphics graphics, PhoneApp app, int centerX, int centerY, int iconSize) {
        if (MPhoneAppIconRenderer.handles(app)) {
            MPhoneAppIconRenderer.draw(graphics, app, centerX - iconSize / 2, centerY - iconSize / 2, iconSize, false);
            return;
        }
        int sourceX = (app.iconIndex() % 4) * ICON_CELL;
        int sourceY = (app.iconIndex() / 4) * ICON_CELL;
        graphics.blit(ICONS,
                centerX - iconSize / 2, centerY - iconSize / 2,
                iconSize, iconSize, (float) sourceX, (float) sourceY,
                ICON_CELL, ICON_CELL, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static int lerp(int start, int end, float progress) {
        return Math.round(start + (end - start) * progress);
    }

    private static int darken(int color, float brightness) {
        int red = Math.round(((color >> 16) & 0xFF) * brightness);
        int green = Math.round(((color >> 8) & 0xFF) * brightness);
        int blue = Math.round((color & 0xFF) * brightness);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!allAppsLayout() && !pageTransitioning && appPage == 0 && this.searchFocused
                && SharedConstants.isAllowedChatCharacter(codePoint) && this.searchText.length() < 26) {
            this.searchText.append(codePoint);
            searchPage = 0;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && editMode) {
            leaveEditMode();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (this.searchFocused || !this.searchText.isEmpty())) {
            this.searchFocused = false;
            this.searchText.setLength(0);
            searchPage = 0;
            return true;
        }
        if (this.searchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!this.searchText.isEmpty()) {
                this.searchText.deleteCharAt(this.searchText.length() - 1);
                searchPage = 0;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (ZPhoneTransitionState.kind() == ZPhoneTransitionState.Kind.HOME_RETURN) return true;
        if (this.launchingApp != null || pageDragTracking || pageSnapBack) return true;
        if (handleDynamicIslandClick(mouseX, mouseY)) return true;

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !allAppsLayout()) {
            if (editMode) {
                leaveEditMode();
                return true;
            }
            int slot = homeSlotAt(mouseX, mouseY);
            if (slot >= 0 && PhoneAppLayoutStore.homeAppAt(slot) != null) {
                enterEditMode(slot);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (editMode) return handleEditClick(mouseX, mouseY);

        if (!allAppsLayout() && !pageTransitioning && appPage == 0
                && inside(mouseX, mouseY, WEATHER_LEFT, WEATHER_TOP, WEATHER_RIGHT, WEATHER_BOTTOM)) {
            rememberedPage = appPage;
            this.minecraft.setScreen(new ZPhoneWeatherScreen());
            return true;
        }
        if (!allAppsLayout() && !pageTransitioning && appPage == 0
                && inside(mouseX, mouseY, searchLeft(), SEARCH_TOP, searchRight(), SEARCH_BOTTOM)) {
            if (!searchText.isEmpty()
                    && inside(mouseX, mouseY, searchRight() - 34, SEARCH_TOP, searchRight(), SEARCH_BOTTOM)) {
                searchText.setLength(0);
                searchPage = 0;
            }
            this.searchFocused = true;
            return true;
        }
        this.searchFocused = false;

        if (!allAppsLayout() && !pageTransitioning) {
            boolean searching = !searchText.isEmpty();
            int count = searching ? searchResultPages().size() : pageCount();
            int firstCenter = 150 - ((count - 1) * 14) / 2;
            for (int i = 0; count > 1 && i < count; i++) {
                int centerX = firstCenter + i * 14;
                if (inside(mouseX, mouseY, centerX - 7, 476, centerX + 7, 492)) {
                    if (searching) searchPage = i;
                    else startPageTransition(i);
                    return true;
                }
            }
        }

        if (!pageTransitioning) {
            List<PhoneApp> apps = allAppsLayout() ? PhoneAppLayoutStore.visibleHomeApps() : visibleApps();
            int[] xs = allAppsLayout() ? ALL_APPS_X : appPage == 0 || !searchText.isEmpty() ? PAGE_ONE_X : GRID_X;
            int[] ys = allAppsLayout() ? ALL_APPS_Y : appPage == 0 || !searchText.isEmpty() ? PAGE_ONE_Y : GRID_Y;
            int max = Math.min(apps.size(), xs.length);
            for (int i = 0; i < max; i++) {
                PhoneApp app = apps.get(i);
                if (app == null) continue;
                int cx = xs[i];
                int cy = ys[i];
                int radius = allAppsLayout() ? 25 : 30;
                int bottom = allAppsLayout() ? 34 : 38;
                if (inside(mouseX, mouseY, cx - radius, cy - radius, cx + radius, cy + bottom)) {
                    startAppLaunch(app, cx, cy);
                    return true;
                }
            }
        }
        List<PhoneApp> dock = PhoneAppLayoutStore.dockApps();
        for (int i = 0; i < Math.min(dock.size(), DOCK_X.length); i++) {
            int cx = DOCK_X[i];
            if (inside(mouseX, mouseY, cx - 30, DOCK_Y - 30, cx + 30, DOCK_Y + 30)) {
                startAppLaunch(dock.get(i), cx, DOCK_Y);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!editMode
                && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && pageCount() > 1
                && !pageTransitioning
                && !pageSnapBack
                && launchingApp == null
                && searchText.isEmpty()
                && !searchFocused
                && mouseY >= y(70)
                && mouseY <= y(480)) {
            double previousX = mouseX - dragX;
            if (!pageDragTracking) {
                if (Math.abs(dragX) >= Math.abs(dragY) && Math.abs(dragX) >= Math.max(2.0D, size(3))) {
                    pageDragTracking = true;
                    pageDragStartX = previousX;
                } else {
                    return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
                }
            }
            double pixels = mouseX - pageDragStartX;
            float designOffset = (float) (pixels / Math.max(0.001F, phone().scale()));
            if ((appPage == 0 && designOffset > 0.0F) || (appPage == pageCount() - 1 && designOffset < 0.0F)) {
                designOffset *= 0.22F;
            }
            pageDragOffset = Math.max(-DESIGN_WIDTH, Math.min(DESIGN_WIDTH, designOffset));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pageDragTracking) {
            finishPageDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (appPage >= pageCount()) appPage = Math.max(0, pageCount() - 1);
        if (pageTransitioning && System.currentTimeMillis() - pageTransitionStartedAt >= PAGE_TRANSITION_DURATION_MS) {
            appPage = pageTo;
            pageTransitioning = false;
            pageTransitionStartOffset = 0.0F;
        }
        rememberedPage = appPage;
        if (pageSnapBack && System.currentTimeMillis() - pageSnapStartedAt >= PAGE_SNAP_DURATION_MS) {
            pageSnapBack = false;
            pageSnapStartOffset = 0.0F;
        }
        if (this.launchingApp != null && System.currentTimeMillis() - this.launchStartedAt >= APP_LAUNCH_DURATION_MS) {
            PhoneApp app = this.launchingApp;
            this.launchingApp = null;
            ZPhoneClient.openSharedApp(app);
        }
    }

    private enum WeatherVisual {
        CLEAR(0, "18°", "↑ 22°   ↓ 12°", 0xFF245D8A, 0xFF5795BB),
        RAIN(2, "14°", "↑ 16°   ↓ 9°", 0xFF354D63, 0xFF627F93),
        THUNDER(3, "12°", "↑ 14°   ↓ 8°", 0xFF343C54, 0xFF60667F);

        private final int iconIndex;
        private final String temperature;
        private final String range;
        private final int dayTopColor;
        private final int dayBottomColor;

        WeatherVisual(int iconIndex, String temperature, String range, int dayTopColor, int dayBottomColor) {
            this.iconIndex = iconIndex;
            this.temperature = temperature;
            this.range = range;
            this.dayTopColor = dayTopColor;
            this.dayBottomColor = dayBottomColor;
        }

        private int topColor(boolean night) { return night ? 0xFF182A48 : dayTopColor; }
        private int bottomColor(boolean night) { return night ? 0xFF334767 : dayBottomColor; }

        private String localizedDescription(boolean night) {
            return switch (this) {
                case CLEAR -> night ? PhoneSettingsStore.text("Açık Gece", "Clear Night") : PhoneSettingsStore.text("Açık", "Clear");
                case RAIN -> PhoneSettingsStore.text("Yağmurlu", "Rain");
                case THUNDER -> PhoneSettingsStore.text("Fırtınalı", "Storm");
            };
        }
    }
}

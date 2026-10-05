package com.mikasrevs.newphone.client.tablet;

import com.mikasrevs.newphone.client.emergency.EmergencyClientData;
import com.mikasrevs.newphone.client.gui.PhoneGpsMapRenderer;
import com.mikasrevs.newphone.client.gui.PhoneGpsState;
import com.mikasrevs.newphone.emergency.EmergencyCategory;
import com.mikasrevs.newphone.emergency.EmergencyRisk;
import com.mikasrevs.newphone.emergency.EmergencyService;
import com.mikasrevs.newphone.network.EmergencyStateData;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Dedicated Police/EMS tablet: dispatch, service group chat, settings and tablet-only navigation. */
public final class ResponderTabletScreen extends AbstractResponderTabletScreen {
    private static final int VISIBLE_CALLS = 4;
    private static final int VISIBLE_CHAT = 6;
    private static final long PAGE_ANIMATION_MS = 120L;
    private static final long FOCUS_ANIMATION_MS = 140L;
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private enum TabletPage {
        DISPATCH,
        MESSAGES,
        SETTINGS
    }

    private long selectedDispatchId;
    private boolean incomingFocus;
    private int scroll;
    private int chatScroll;
    private int mapZoom;
    private boolean mapFocusUnit = true;

    private TabletPage page = TabletPage.DISPATCH;
    private long pageChangedAt = System.currentTimeMillis();
    private int pageSlideDirection = 1;

    private boolean focusTarget;
    private float focusAnimationFrom;
    private long focusAnimationStartedAt;

    private EditBox chatInput;
    private long closeConfirmDispatchId;

    private ResponderTabletScreen(
            EmergencyService service,
            long selectedDispatchId,
            boolean incomingFocus,
            boolean startFocused
    ) {
        super(service, Component.translatable("emergency.newphone.tablet.title"));
        this.selectedDispatchId = selectedDispatchId;
        this.incomingFocus = incomingFocus;
        this.focusTarget = startFocused;
        this.focusAnimationFrom = startFocused ? 1.0F : 0.0F;
        this.focusAnimationStartedAt = 0L;
        if (selectedDispatchId > 0L) EmergencyClientData.consumeIncoming(selectedDispatchId, service);
        EmergencyClientData.requestSync();
    }

    public static void open(EmergencyService service) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || service == null) return;
        EmergencyClientData.IncomingDispatch incoming = EmergencyClientData.peekIncoming();
        if (incoming != null && incoming.service() == service) {
            EmergencyClientData.pollIncoming();
            minecraft.setScreen(new ResponderTabletScreen(service, incoming.dispatchId(), true, false));
        } else {
            minecraft.setScreen(new ResponderTabletScreen(service, 0L, false, false));
        }
    }

    public static void openIncoming(EmergencyService service, long dispatchId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || service == null || dispatchId <= 0L) return;
        EmergencyClientData.consumeIncoming(dispatchId, service);
        minecraft.setScreen(new ResponderTabletScreen(service, dispatchId, true, false));
    }

    public static void openFocused(EmergencyService service, long dispatchId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || service == null || dispatchId <= 0L) return;
        minecraft.setScreen(new ResponderTabletScreen(service, dispatchId, false, true));
    }

    @Override
    protected void init() {
        String draft = chatInput == null ? "" : chatInput.getValue();
        super.init();
        chatInput = new EditBox(
                font,
                sx(262), sy(426), ss(512), ss(29),
                Component.literal(ui("Mesaj yaz", "Write a message"))
        );
        chatInput.setMaxLength(180);
        chatInput.setBordered(false);
        chatInput.setTextColor(0xFFE7EEF4);
        chatInput.setTextColorUneditable(0xFF73889A);
        chatInput.setValue(draft);
        chatInput.visible = page == TabletPage.MESSAGES;
        chatInput.active = page == TabletPage.MESSAGES;
        chatInput.setEditable(page == TabletPage.MESSAGES);
        addRenderableWidget(chatInput);
    }

    @Override
    public void tick() {
        super.tick();
        if (chatInput != null) chatInput.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<EmergencyStateData.DispatchEntry> calls = EmergencyClientData.activeFor(service);
        EmergencyStateData.DispatchEntry assignment = EmergencyClientData.activeAssignment(service);
        EmergencyStateData.DispatchEntry selected = selectedDispatchId <= 0L
                ? null : EmergencyClientData.find(service, selectedDispatchId);

        // Once a responder accepts a dispatch, the tablet is locked to that assignment until it ends.
        if (assignment != null) {
            selectedDispatchId = assignment.id;
            selected = assignment;
            incomingFocus = false;
            if (page == TabletPage.DISPATCH) setAssignmentFocus(true);
        } else {
            if (selectedDispatchId > 0L && (selected == null || !EmergencyClientData.isActive(selected))) {
                if (TabletNavigationState.matches(service, selectedDispatchId)) TabletNavigationState.clear();
                selectedDispatchId = 0L;
                incomingFocus = false;
                setAssignmentFocus(false);
                closeConfirmDispatchId = 0L;
                selected = null;
            }
            if (selectedDispatchId <= 0L && !calls.isEmpty()) {
                selectedDispatchId = calls.get(0).id;
                selected = calls.get(0);
            }
        }

        updateChatInputVisibility();
        drawTabletShell(graphics);
        drawHeader(graphics);

        if (EmergencyClientData.isAuthorized(service)) {
            drawAnimatedPage(graphics, selected, mouseX, mouseY);
            drawBottomNavigation(graphics, mouseX, mouseY);
        } else {
            drawUnauthorized(graphics);
        }
        drawNotice(graphics);

        if (page == TabletPage.MESSAGES && chatInput != null && chatInput.visible) {
            chatInput.render(graphics, mouseX, mouseY, partialTick);
        }

        if (closeConfirmDispatchId > 0L) {
            EmergencyStateData.DispatchEntry confirmEntry = EmergencyClientData.find(service, closeConfirmDispatchId);
            if (confirmEntry == null || !EmergencyClientData.isActive(confirmEntry)
                    || !confirmEntry.acceptedBySelf || !confirmEntry.onSceneBySelf) {
                closeConfirmDispatchId = 0L;
            } else {
                drawCloseConfirmation(graphics, confirmEntry, mouseX, mouseY);
            }
        }
    }

    private void drawTabletShell(GuiGraphics graphics) {
        renderBackground(graphics);
        drawRoundedRect(graphics, canvasX(0), canvasY(0), canvasX(RESPONDER_DESIGN_WIDTH),
                canvasY(RESPONDER_DESIGN_HEIGHT), ss(28), 0xFF080B0F);
        drawRoundedRect(graphics, canvasX(4), canvasY(4), canvasX(RESPONDER_DESIGN_WIDTH - 4),
                canvasY(RESPONDER_DESIGN_HEIGHT - 4), ss(25), 0xFF2A333B);
        drawRoundedRect(graphics, canvasX(9), canvasY(9), canvasX(RESPONDER_DESIGN_WIDTH - 9),
                canvasY(RESPONDER_DESIGN_HEIGHT - 9), ss(22), 0xFF080D12);
        drawRoundedRect(graphics, canvasX(15), canvasY(15), canvasX(RESPONDER_DESIGN_WIDTH - 15),
                canvasY(RESPONDER_DESIGN_HEIGHT - 15), ss(18), 0xFF111922);
        fillCircle(graphics, canvasX(RESPONDER_DESIGN_WIDTH / 2.0F - 18), canvasY(12), ss(4), 0xFF03070A);
        fillCircle(graphics, canvasX(RESPONDER_DESIGN_WIDTH / 2.0F - 18), canvasY(12), ss(2), 0xFF164469);
        drawRoundedRect(graphics, canvasX(RESPONDER_DESIGN_WIDTH / 2.0F - 7), canvasY(9),
                canvasX(RESPONDER_DESIGN_WIDTH / 2.0F + 32), canvasY(15), ss(2), 0xFF03070A);
        drawRoundedRect(graphics, sx(0), sy(0), sx(RESPONDER_CONTENT_WIDTH), sy(RESPONDER_CONTENT_HEIGHT),
                ss(12), 0xFF06111B);
        drawRoundedRect(graphics, sx(2), sy(2), sx(RESPONDER_CONTENT_WIDTH - 2), sy(RESPONDER_CONTENT_HEIGHT - 2),
                ss(10), 0xFF091622);
    }

    private void drawHeader(GuiGraphics graphics) {
        drawRoundedRect(graphics, sx(8), sy(8), sx(912), sy(54), ss(8), 0xFF0B1825);
        graphics.fill(sx(8), sy(53), sx(912), sy(54), serviceDividerColor());

        for (int row = 0; row < 3; row++) {
            graphics.fill(sx(22), sy(21 + row * 7), sx(38), sy(23 + row * 7), 0xFFC6D2DC);
        }
        drawServiceBadge(graphics, 57, 31);
        text(graphics, service == EmergencyService.POLICE
                        ? ui("POLİS SEVK", "POLICE DISPATCH")
                        : ui("AMBULANS SEVK", "EMS DISPATCH"),
                78, 18, 10, 0xFFF2F7FB, 215);
        text(graphics, ui("GÜVENLİ SERVİS AĞI", "SECURE SERVICE NETWORK"),
                79, 37, 5, 0xFF6F8AA0, 195);

        String pageTitle = switch (page) {
            case DISPATCH -> ui("AKTİF GÖREVLER", "ACTIVE DISPATCH");
            case MESSAGES -> ui("EKİP MESAJLARI", "TEAM MESSAGES");
            case SETTINGS -> ui("TABLET AYARLARI", "TABLET SETTINGS");
        };
        drawRoundedRect(graphics, sx(367), sy(16), sx(555), sy(46), ss(6), 0xFF0D2030);
        textCentered(graphics, pageTitle, 461, 25, 6, serviceAccentBright());

        textCentered(graphics, LocalTime.now().format(CLOCK_FORMAT), 817, 24, 8, 0xFFE7EEF4);
        drawSignalBars(graphics, 850, 25);
        fillCircle(graphics, sx(876), sy(32), ss(3), 0xFF55CE89);
        drawRoundedRect(graphics, sx(882), sy(15), sx(904), sy(45), ss(6), 0xFF172433);
        textCentered(graphics, "X", 893, 24, 7, 0xFFD8E1E8);
    }

    private void drawAnimatedPage(
            GuiGraphics graphics,
            EmergencyStateData.DispatchEntry selected,
            int mouseX,
            int mouseY
    ) {
        float progress = pageIntroProgress();
        float eased = easeOut(progress);
        float offset = (1.0F - eased) * 72.0F * pageSlideDirection;
        graphics.pose().pushPose();
        graphics.pose().translate(ss(offset), 0.0F, 0.0F);
        switch (page) {
            case DISPATCH -> drawDispatchPage(graphics, selected, mouseX, mouseY);
            case MESSAGES -> drawMessagesPage(graphics, mouseX, mouseY);
            case SETTINGS -> drawSettingsPage(graphics, mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    private void drawDispatchPage(
            GuiGraphics graphics,
            EmergencyStateData.DispatchEntry selected,
            int mouseX,
            int mouseY
    ) {
        float focus = assignmentFocusProgress();
        if (focus < 0.999F) {
            graphics.pose().pushPose();
            graphics.pose().translate(-ss(RESPONDER_CONTENT_WIDTH * focus), 0.0F, 0.0F);
            drawCallList(graphics, selected, mouseX, mouseY);
            drawMainPanel(graphics, selected, mouseX, mouseY);
            graphics.pose().popPose();
        }
        if (focus > 0.001F && selected != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(ss(RESPONDER_CONTENT_WIDTH * (1.0F - focus)), 0.0F, 0.0F);
            drawFocusedAssignment(graphics, selected, mouseX, mouseY);
            graphics.pose().popPose();
        }
    }

    private void drawCallList(
            GuiGraphics graphics,
            EmergencyStateData.DispatchEntry selected,
            int mouseX,
            int mouseY
    ) {
        List<EmergencyStateData.DispatchEntry> calls = EmergencyClientData.activeFor(service);
        int maximumScroll = Math.max(0, calls.size() - VISIBLE_CALLS);
        scroll = Math.min(scroll, maximumScroll);

        drawPanel(graphics, 10, 64, 238, 464, 0xFF08131E);
        text(graphics, ui("Aktif Görevler", "Active Calls"), 22, 76, 8, 0xFFD8E3EB, 145);
        drawRoundedRect(graphics, sx(202), sy(70), sx(226), sy(91), ss(5), serviceControlFill());
        textCentered(graphics, Integer.toString(calls.size()), 214, 76, 7, serviceAccentBright());
        graphics.fill(sx(19), sy(99), sx(229), sy(100), serviceDividerColor());

        if (calls.isEmpty()) {
            fillCircle(graphics, sx(124), sy(225), ss(28), serviceSelectedFill());
            drawTargetIcon(graphics, 124, 225, serviceAccentBright());
            textCentered(graphics, ui("Aktif çağrı yok", "No active calls"), 124, 273, 8, 0xFFE6EEF4);
            textCentered(graphics, ui("Yeni acil çağrılar bekleniyor", "Monitoring for emergencies"),
                    124, 296, 5, 0xFF6E8496);
            return;
        }

        int count = Math.min(VISIBLE_CALLS, calls.size() - scroll);
        for (int index = 0; index < count; index++) {
            EmergencyStateData.DispatchEntry entry = calls.get(scroll + index);
            int top = 108 + index * 77;
            boolean hovered = inside(mouseX, mouseY, 18, top, 230, top + 68);
            boolean selectedRow = selected != null && selected.id == entry.id;
            int fill = selectedRow ? serviceSelectedFill() : hovered ? 0xFF112536 : 0xFF0C1A27;
            int border = selectedRow ? serviceAccent() : 0xFF173047;
            drawPanel(graphics, 18, top, 230, top + 68, fill, border);
            graphics.fill(sx(18), sy(top + 7), sx(22), sy(top + 61), riskColor(entry.risk));
            text(graphics, categoryName(entry), 31, top + 9, 8, 0xFFF1F5F8, 136);
            text(graphics, "#" + entry.id, 180, top + 10, 5, 0xFF7890A3, 38);
            text(graphics, distanceLabel(entry), 31, top + 31, 6, 0xFF8FA9BB, 111);
            drawRoundedRect(graphics, sx(31), sy(top + 48), sx(88), sy(top + 64), ss(4),
                    withAlpha(riskColor(entry.risk), 46));
            textCentered(graphics, t(riskKey(entry.risk)), 59.5F, top + 52, 5, riskColor(entry.risk));
            text(graphics, entry.responderCount + " / " + entry.maximumResponders,
                    157, top + 50, 6, 0xFFC5D1DA, 58);
        }

        drawRoundedRect(graphics, sx(18), sy(420), sx(230), sy(450), ss(5), 0xFF0C1B28);
        fillCircle(graphics, sx(31), sy(435), ss(4), 0xFF55CC89);
        text(graphics, ui("Çevrimiçi", "Online"), 42, 428, 6, 0xFF8DA4B5, 72);
        text(graphics, ui("Önceliğe göre", "Priority sort"), 135, 428, 5, 0xFF668095, 82);
    }

    private void drawMainPanel(
            GuiGraphics graphics,
            EmergencyStateData.DispatchEntry entry,
            int mouseX,
            int mouseY
    ) {
        drawPanel(graphics, 248, 64, 910, 252, 0xFF07131E);
        drawRoundedRect(graphics, sx(256), sy(72), sx(902), sy(94), ss(5), 0xFF0C1C29);
        text(graphics, ui("CANLI HARİTA", "LIVE MAP"), 266, 78, 7, serviceAccentBright(), 100);
        String focus = mapFocusUnit || entry == null
                ? ui("BİRİM ODAĞI", "UNIT FOCUS")
                : ui("OLAY ODAĞI", "INCIDENT FOCUS");
        text(graphics, focus, 699, 79, 5, 0xFF8299AA, 105);
        fillCircle(graphics, sx(821), sy(84), ss(3), 0xFF56CB89);
        text(graphics, "GPS ON", 831, 78, 5, 0xFF9EB0BD, 56);

        PhoneGpsState.Location incident = entry == null ? null : location(entry);
        PhoneGpsState.Location center = mapFocusUnit || incident == null
                ? currentLocation() : incident;
        boolean routeVisible = entry != null && TabletNavigationState.matches(service, entry.id);
        PhoneGpsMapRenderer.Projection projection = PhoneGpsMapRenderer.drawMap(graphics, minecraft,
                sx(257), sy(98), ss(644), ss(145), center, mapZoom, incident,
                false, PhoneGpsMapRenderer.MapStyle.CONTRAST);
        if (routeVisible && incident != null) drawTabletRoute(graphics, projection, incident, serviceAccentBright());

        drawMapControl(graphics, mouseX, mouseY, 862, 105, "+", mapZoom > 0, false);
        drawMapControl(graphics, mouseX, mouseY, 862, 137, "-", mapZoom < 3, false);
        drawMapControl(graphics, mouseX, mouseY, 862, 169, "", true, mapFocusUnit);
        drawTargetIcon(graphics, 878, 184, mapFocusUnit ? serviceAccentBright() : 0xFFD4E1EA);

        if (entry == null) {
            drawEmptyIncident(graphics);
            return;
        }
        drawIncidentCard(graphics, entry);
        drawInfoCards(graphics, entry);
        drawActions(graphics, entry, mouseX, mouseY, false);
    }

    private void drawFocusedAssignment(
            GuiGraphics graphics,
            EmergencyStateData.DispatchEntry entry,
            int mouseX,
            int mouseY
    ) {
        drawPanel(graphics, 10, 64, 910, 330, 0xFF07131E);
        drawRoundedRect(graphics, sx(18), sy(72), sx(146), sy(95), ss(5),
                entry.acceptedBySelf ? serviceSelectedFill() : 0xFF102233);
        textCentered(graphics, entry.acceptedBySelf
                        ? ui("AKTİF GÖREV", "ACTIVE ASSIGNMENT")
                        : "←  " + ui("TÜM GÖREVLER", "ALL CALLS"),
                82, 78, 6, entry.acceptedBySelf ? serviceAccentBright() : 0xFFC4D1DA);

        text(graphics, ui("ATANAN GÖREV", "ASSIGNED CALL"), 165, 76, 6, serviceAccentBright(), 110);
        text(graphics, categoryName(entry), 165, 91, 10, 0xFFF3F7FA, 260);
        drawRoundedRect(graphics, sx(790), sy(72), sx(900), sy(96), ss(5), withAlpha(riskColor(entry.risk), 45));
        textCentered(graphics, t(riskKey(entry.risk)), 845, 79, 6, riskColor(entry.risk));

        PhoneGpsState.Location incident = location(entry);
        PhoneGpsMapRenderer.Projection projection = PhoneGpsMapRenderer.drawMap(graphics, minecraft,
                sx(18), sy(108), ss(882), ss(212), incident, mapZoom, incident,
                false, PhoneGpsMapRenderer.MapStyle.CONTRAST);
        if (TabletNavigationState.matches(service, entry.id)) {
            drawTabletRoute(graphics, projection, incident, serviceAccentBright());
        }
        drawMapControl(graphics, mouseX, mouseY, 860, 116, "+", mapZoom > 0, false);
        drawMapControl(graphics, mouseX, mouseY, 860, 148, "-", mapZoom < 3, false);

        drawPanel(graphics, 10, 338, 910, 414, 0xFF091725);
        graphics.fill(sx(10), sy(347), sx(14), sy(405), riskColor(entry.risk));
        text(graphics, ui("OLAY", "INCIDENT"), 28, 348, 5, 0xFF70899C, 60);
        text(graphics, categoryName(entry), 28, 365, 9, 0xFFF0F5F8, 180);
        text(graphics, "#" + entry.id + "  •  " + distanceLabel(entry), 28, 389, 6, 0xFF8DA3B3, 200);

        text(graphics, ui("ARAYAN", "CALLER"), 245, 348, 5, 0xFF70899C, 60);
        text(graphics, entry.callerName, 245, 365, 8, 0xFFE5EDF3, 160);
        text(graphics, location(entry).compactCoordinates(), 245, 389, 5, 0xFF8098A9, 170);

        int focusedOnScene = Math.max(0, Math.min(entry.onSceneResponderCount, entry.responderCount));
        int focusedEnRoute = Math.max(0, entry.responderCount - focusedOnScene);
        text(graphics, ui("EKİPLER", "RESPONDERS"), 447, 348, 5, 0xFF70899C, 80);
        text(graphics, entry.responderCount + " / " + entry.maximumResponders,
                447, 365, 10, serviceAccentBright(), 85);
        text(graphics, ui("Sahada ", "Scene ") + focusedOnScene
                        + "  •  " + ui("Yolda ", "Route ") + focusedEnRoute,
                447, 389, 5, focusedOnScene > 0 ? 0xFF61D799 : 0xFF8098A9, 125);

        text(graphics, ui("AÇIKLAMA", "DESCRIPTION"), 585, 348, 5, 0xFF70899C, 85);
        wrappedText(graphics, entry.description == null || entry.description.isBlank()
                        ? ui("Açıklama yok.", "No description.") : entry.description,
                585, 365, 6, 0xFFB7C7D2, 300, 2);

        drawActions(graphics, entry, mouseX, mouseY, true);
    }

    private void drawEmptyIncident(GuiGraphics graphics) {
        drawPanel(graphics, 248, 260, 910, 414, 0xFF08131E);
        fillCircle(graphics, sx(579), sy(315), ss(28), serviceSelectedFill());
        drawTargetIcon(graphics, 579, 315, serviceAccentBright());
        textCentered(graphics, ui("ÇAĞRI SEÇ", "SELECT A CALL"), 579, 354, 10, 0xFFE7EEF4);
        textCentered(graphics, ui("Detayları görüntülemek için soldan aktif bir görev seç.",
                        "Choose an active dispatch to inspect its details."),
                579, 379, 5, 0xFF748A9B);
    }

    private void drawIncidentCard(GuiGraphics graphics, EmergencyStateData.DispatchEntry entry) {
        drawPanel(graphics, 248, 260, 910, 344, 0xFF091725);
        graphics.fill(sx(248), sy(270), sx(252), sy(334), riskColor(entry.risk));
        text(graphics, incomingFocus && !entry.acceptedBySelf
                        ? ui("YENİ ÇAĞRI", "NEW CALL") : ui("SEÇİLİ OLAY", "SELECTED INCIDENT"),
                264, 272, 5, 0xFFFF6871, 130);
        text(graphics, categoryName(entry), 264, 290, 12, 0xFFF4F8FB, 250);
        text(graphics, "#" + entry.id, 516, 293, 6, 0xFF71889B, 55);

        drawRoundedRect(graphics, sx(264), sy(316), sx(330), sy(337), ss(5),
                withAlpha(riskColor(entry.risk), 48));
        textCentered(graphics, t(riskKey(entry.risk)), 297, 322, 6, riskColor(entry.risk));
        text(graphics, distanceLabel(entry), 347, 320, 6, 0xFF9CB1C0, 122);
        text(graphics, entry.responderCount + " / " + entry.maximumResponders,
                477, 320, 6, 0xFFC8D3DC, 72);
        text(graphics, statusLabel(entry.status), 558, 320, 6, statusColor(entry.status), 100);

        drawRoundedRect(graphics, sx(674), sy(270), sx(898), sy(336), ss(6), 0xFF0B1B28);
        text(graphics, ui("AÇIKLAMA", "DESCRIPTION"), 687, 279, 5, 0xFF6D879A, 92);
        wrappedText(graphics, entry.description == null || entry.description.isBlank()
                        ? ui("Açıklama verilmedi.", "No description provided.") : entry.description,
                687, 295, 6, 0xFFB9C9D4, 196, 2);
    }

    private void drawInfoCards(GuiGraphics graphics, EmergencyStateData.DispatchEntry entry) {
        drawInfoCard(graphics, 248, 352, 458, 414,
                ui("ARAYAN", "CALLER"), entry.callerName,
                location(entry).compactCoordinates(), 0xFF1A6CA8);
        drawInfoCard(graphics, 466, 352, 676, 414,
                ui("RİSK", "RISK"), t(riskKey(entry.risk)),
                statusLabel(entry.status), riskColor(entry.risk));
        int onScene = Math.max(0, Math.min(entry.onSceneResponderCount, entry.responderCount));
        int enRoute = Math.max(0, entry.responderCount - onScene);
        drawInfoCard(graphics, 684, 352, 910, 414,
                ui("MÜDAHALE EKİPLERİ", "RESPONDERS"),
                entry.responderCount + " / " + entry.maximumResponders,
                ui("Olay yerinde: ", "On scene: ") + onScene
                        + "  •  " + ui("Yolda: ", "En route: ") + enRoute,
                serviceAccent());
    }

    private void drawInfoCard(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            String label,
            String primary,
            String secondary,
            int accent
    ) {
        drawPanel(graphics, left, top, right, bottom, 0xFF0A1824);
        graphics.fill(sx(left), sy(top + 8), sx(left + 3), sy(bottom - 8), accent);
        text(graphics, label, left + 13, top + 9, 5, 0xFF6E879A, right - left - 24);
        text(graphics, primary, left + 13, top + 26, 8, 0xFFE7EEF4, right - left - 24);
        text(graphics, secondary, left + 13, top + 45, 5, 0xFF89A0B1, right - left - 24);
    }

    private void drawActions(
            GuiGraphics graphics,
            EmergencyStateData.DispatchEntry entry,
            int mouseX,
            int mouseY,
            boolean focused
    ) {
        int top = 422;
        int bottom = 464;
        int left = focused ? 10 : 248;
        int right = 910;
        if (!EmergencyClientData.isActive(entry)) {
            drawPanel(graphics, left, top, right, bottom, 0xFF0A1722);
            textCentered(graphics, statusLabel(entry.status), (left + right) / 2.0F, 435, 8, statusColor(entry.status));
            return;
        }

        if (!entry.acceptedBySelf) {
            boolean full = entry.responderCount >= entry.maximumResponders;
            drawActionButton(graphics, mouseX, mouseY, left, top, right, bottom,
                    full ? ui("ÇAĞRI DOLU", "CALL FULL") : ui("KABUL ET", "ACCEPT"),
                    full ? 0xFF26323C : serviceActionColor(), !full);
            return;
        }

        int mid = left + (right - left) / 2 - 4;
        if (!entry.onSceneBySelf) {
            drawActionButton(graphics, mouseX, mouseY, left, top, mid, bottom,
                    ui("NAVİGASYON", "NAVIGATE"), serviceActionColor(), true);
            drawActionButton(graphics, mouseX, mouseY, mid + 8, top, right, bottom,
                    ui("VARIŞTA OTOMATİK", "AUTO ON ARRIVAL"), 0xFF25634B, false);
        } else {
            drawActionButton(graphics, mouseX, mouseY, left, top, mid, bottom,
                    ui("OLAY YERİNE ULAŞILDI", "ON SCENE"), 0xFF25634B, false);
            drawActionButton(graphics, mouseX, mouseY, mid + 8, top, right, bottom,
                    ui("ÇAĞRIYI KAPAT", "CLOSE CALL"), 0xFF6C2832, true);
        }
    }

    private void drawCloseConfirmation(
            GuiGraphics graphics,
            EmergencyStateData.DispatchEntry entry,
            int mouseX,
            int mouseY
    ) {
        graphics.fill(sx(0), sy(0), sx(RESPONDER_CONTENT_WIDTH), sy(RESPONDER_CONTENT_HEIGHT), 0xB8000000);
        drawPanel(graphics, 255, 142, 665, 356, 0xFF0A1722, servicePanelBorder());
        fillCircle(graphics, sx(460), sy(187), ss(24), 0xFF5A2029);
        textCentered(graphics, "!", 460, 174, 18, 0xFFFF6874);
        textCentered(graphics, ui("ÇAĞRIYI KAPAT?", "CLOSE THIS CALL?"),
                460, 220, 12, 0xFFF1F6F9);
        textCentered(graphics, "#" + entry.id + "  •  " + categoryName(entry),
                460, 248, 6, 0xFF8EA4B4);
        wrappedText(graphics,
                ui("Bu işlem olayı herkes için tamamlanmış olarak işaretler. Kapatmak istediğine emin misin?",
                        "This marks the incident resolved for everyone. Are you sure you want to close it?"),
                300, 270, 6, 0xFFB8C7D1, 320, 2);

        drawActionButton(graphics, mouseX, mouseY, 285, 312, 445, 346,
                ui("VAZGEÇ", "CANCEL"), 0xFF2B3A46, true);
        drawActionButton(graphics, mouseX, mouseY, 475, 312, 635, 346,
                ui("EVET, KAPAT", "YES, CLOSE"), 0xFF7B2732, true);
    }

    private void drawMessagesPage(GuiGraphics graphics, int mouseX, int mouseY) {
        List<EmergencyStateData.ChatEntry> messages = EmergencyClientData.chatFor(service);
        drawPanel(graphics, 10, 64, 232, 464, 0xFF08131E);
        drawServiceBadge(graphics, 49, 101);
        text(graphics, service == EmergencyService.POLICE
                        ? ui("POLİS EKİP AĞI", "POLICE TEAM NETWORK")
                        : ui("EMS EKİP AĞI", "EMS TEAM NETWORK"),
                72, 83, 8, 0xFFEAF1F6, 145);
        text(graphics, ui("Sadece yetkili ekip üyeleri", "Authorized members only"),
                72, 105, 5, 0xFF758C9D, 145);
        graphics.fill(sx(22), sy(132), sx(220), sy(133), serviceDividerColor());

        text(graphics, ui("KANAL DURUMU", "CHANNEL STATUS"), 24, 151, 5, 0xFF6E8798, 120);
        fillCircle(graphics, sx(30), sy(177), ss(4), 0xFF55CE89);
        text(graphics, ui("Şifreli bağlantı aktif", "Encrypted link active"), 42, 170, 6, 0xFFB9C8D2, 160);
        text(graphics, messages.size() + " " + ui("mesaj", "messages"), 24, 197, 6, serviceAccentBright(), 150);

        text(graphics, ui("SON KONUŞANLAR", "RECENT MEMBERS"), 24, 237, 5, 0xFF6E8798, 120);
        List<String> recent = recentChatMembers(messages);
        if (recent.isEmpty()) {
            text(graphics, ui("Henüz mesaj yok", "No messages yet"), 24, 260, 6, 0xFF718697, 160);
        } else {
            for (int i = 0; i < Math.min(5, recent.size()); i++) {
                fillCircle(graphics, sx(31), sy(262 + i * 27), ss(5), serviceSelectedFill());
                text(graphics, recent.get(i), 44, 256 + i * 27, 6, 0xFFC6D2DB, 150);
            }
        }
        drawRoundedRect(graphics, sx(22), sy(420), sx(220), sy(450), ss(6), 0xFF0C1B28);
        textCentered(graphics, ui("SERVİS GRUP SOHBETİ", "SERVICE GROUP CHAT"), 121, 429, 5, serviceAccentBright());

        drawPanel(graphics, 242, 64, 910, 464, 0xFF07131E);
        text(graphics, ui("Ekip Mesajları", "Team Messages"), 258, 77, 10, 0xFFF1F6F9, 180);
        text(graphics, ui("Bu kanaldaki herkes aynı servis yetkisine sahiptir.",
                        "Everyone in this channel has the same service authorization."),
                258, 101, 5, 0xFF73899A, 390);
        drawRoundedRect(graphics, sx(810), sy(76), sx(892), sy(98), ss(5), serviceSelectedFill());
        textCentered(graphics, ui("CANLI", "LIVE"), 851, 82, 5, serviceAccentBright());
        graphics.fill(sx(255), sy(116), sx(897), sy(117), serviceDividerColor());

        drawChatMessages(graphics, messages);

        drawRoundedRect(graphics, sx(255), sy(419), sx(782), sy(459), ss(7), 0xFF0D1C29);
        graphics.fill(sx(255), sy(419), sx(782), sy(420), 0x32FFFFFF);
        if (chatInput != null && chatInput.getValue().isEmpty() && !chatInput.isFocused()) {
            text(graphics, ui("Ekibine mesaj yaz...", "Message your team..."),
                    270, 432, 6, 0xFF617789, 490);
        }
        drawActionButton(graphics, mouseX, mouseY, 790, 419, 898, 459,
                ui("GÖNDER", "SEND"), serviceActionColor(), true);
    }

    private void drawChatMessages(GuiGraphics graphics, List<EmergencyStateData.ChatEntry> messages) {
        int maxScroll = Math.max(0, messages.size() - VISIBLE_CHAT);
        chatScroll = Math.min(chatScroll, maxScroll);
        int end = Math.max(0, messages.size() - chatScroll);
        int start = Math.max(0, end - VISIBLE_CHAT);
        boolean any = start < end;
        if (!any) {
            fillCircle(graphics, sx(575), sy(245), ss(28), serviceSelectedFill());
            drawMessageIcon(graphics, 575, 245, serviceAccentBright());
            textCentered(graphics, ui("Grup sohbeti hazır", "Group chat is ready"), 575, 286, 9, 0xFFE5EDF3);
            textCentered(graphics, ui("İlk mesajı ekibine sen gönder.", "Send the first message to your team."),
                    575, 310, 5, 0xFF748999);
            return;
        }

        String selfUuid = minecraft != null && minecraft.player != null ? minecraft.player.getUUID().toString() : "";
        int row = 0;
        for (int index = start; index < end; index++, row++) {
            EmergencyStateData.ChatEntry message = messages.get(index);
            boolean own = selfUuid.equals(message.senderUuid);
            int top = 128 + row * 46;
            int left = own ? 548 : 260;
            int right = own ? 890 : 602;
            int fill = own ? serviceSelectedFill() : 0xFF0D1C29;
            int border = own ? serviceAccent() : 0xFF1A3549;
            drawPanel(graphics, left, top, right, top + 39, fill, border);
            text(graphics, own ? ui("Sen", "You") : safe(message.senderName), left + 10, top + 6,
                    5, own ? serviceAccentBright() : 0xFF8EB2CA, 120);
            text(graphics, formatChatTime(message.timestamp), right - 46, top + 6,
                    4, 0xFF62798B, 38);
            text(graphics, safe(message.message), left + 10, top + 20, 6, 0xFFE1E9EF, right - left - 20);
        }
    }

    private void drawSettingsPage(GuiGraphics graphics, int mouseX, int mouseY) {
        drawPanel(graphics, 10, 64, 910, 464, 0xFF07131E);
        text(graphics, ui("Tablet Ayarları", "Tablet Settings"), 32, 82, 12, 0xFFF1F6F9, 250);
        text(graphics, ui("Bu seçenekler yalnızca polis/ambulans tabletlerini etkiler; telefon ayarları değişmez.",
                        "These options affect responder tablets only; civilian phone settings stay untouched."),
                32, 110, 6, 0xFF7890A1, 560);
        drawRoundedRect(graphics, sx(760), sy(80), sx(888), sy(104), ss(5), serviceSelectedFill());
        textCentered(graphics, service == EmergencyService.POLICE ? "POLICE" : "EMS", 824, 86, 6, serviceAccentBright());

        drawSettingRow(graphics, mouseX, mouseY, 32, 138,
                ui("Dil", "Language"),
                ui("Tablet arayüz dilini bağımsız seç.", "Choose the tablet interface language independently."),
                TabletSettingsStore.languageLabel(), true, false);
        drawSettingRow(graphics, mouseX, mouseY, 32, 202,
                ui("Acil çağrı bildirimi", "Incoming call popup"),
                ui("Yeni görev geldiğinde ekran üstü bildirim göster.", "Show an on-screen alert when a new dispatch arrives."),
                onOff(TabletSettingsStore.incomingPopup()), false, TabletSettingsStore.incomingPopup());
        drawSettingRow(graphics, mouseX, mouseY, 32, 266,
                ui("Yeni görevi otomatik aç", "Auto-open new calls"),
                ui("Uygun olduğunda gelen görevi tablette otomatik aç.", "Open incoming dispatches automatically when possible."),
                onOff(TabletSettingsStore.autoOpenIncoming()), false, TabletSettingsStore.autoOpenIncoming());
        drawSettingRow(graphics, mouseX, mouseY, 32, 330,
                ui("Tek aktif görev", "Single active assignment"),
                ui("Bir çağrıyı kabul edince bitene kadar diğer çağrılar kilitlenir.",
                        "After accepting a call, other dispatches stay locked until it ends."),
                ui("ZORUNLU", "ENFORCED"), false, true);
        drawSettingRow(graphics, mouseX, mouseY, 32, 394,
                ui("Mini navigasyon HUD", "Mini navigation HUD"),
                ui("Navigasyonda dünyada tablet haritasını göster. X tableti geri açar.",
                        "Show the tablet route HUD in-world. X restores the tablet."),
                onOff(TabletSettingsStore.navigationHud()), false, TabletSettingsStore.navigationHud());

        drawRoundedRect(graphics, sx(760), sy(109), sx(888), sy(133), ss(6), 0xFF2A333C);
        textCentered(graphics, ui("AYARLARI SIFIRLA", "RESET SETTINGS"), 824, 116, 5, 0xFFC4CFD7);
    }

    private void drawSettingRow(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int left,
            int top,
            String label,
            String description,
            String value,
            boolean languageControl,
            boolean enabled
    ) {
        boolean hovered = inside(mouseX, mouseY, left, top, 888, top + 54);
        drawPanel(graphics, left, top, 888, top + 54, hovered ? 0xFF0D2231 : 0xFF0A1925);
        graphics.fill(sx(left), sy(top + 9), sx(left + 3), sy(top + 45), serviceAccent());
        text(graphics, label, left + 17, top + 9, 8, 0xFFE7EEF4, 250);
        text(graphics, description, left + 17, top + 31, 5, 0xFF778D9D, 510);
        if (languageControl) {
            drawRoundedRect(graphics, sx(690), sy(top + 12), sx(846), sy(top + 42), ss(6), serviceSelectedFill());
            textCentered(graphics, value, 768, top + 21, 6, serviceAccentBright());
            textCentered(graphics, ">", 865, top + 21, 7, 0xFF8FA2B0);
        } else {
            drawToggle(graphics, 803, top + 27, enabled);
            text(graphics, value, 690, top + 20, 6, enabled ? 0xFF69D99A : 0xFF7D8C97, 78);
        }
    }

    private void drawBottomNavigation(GuiGraphics graphics, int mouseX, int mouseY) {
        drawRoundedRect(graphics, sx(10), sy(470), sx(910), sy(496), ss(6), 0xFF07121C);
        graphics.fill(sx(20), sy(470), sx(900), sy(471), serviceDividerColor());
        drawNavItem(graphics, mouseX, mouseY, 18, 300, TabletPage.DISPATCH,
                ui("SEVK", "DISPATCH"), 0);
        drawNavItem(graphics, mouseX, mouseY, 318, 600, TabletPage.MESSAGES,
                ui("MESAJLAR", "MESSAGES"), 1);
        drawNavItem(graphics, mouseX, mouseY, 618, 900, TabletPage.SETTINGS,
                ui("AYARLAR", "SETTINGS"), 2);
    }

    private void drawNavItem(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int left,
            int right,
            TabletPage targetPage,
            String label,
            int icon
    ) {
        boolean selected = page == targetPage;
        boolean hovered = inside(mouseX, mouseY, left, 473, right, 493);
        int fill = selected ? serviceSelectedFill() : hovered ? 0xFF0D2231 : 0x00000000;
        if (selected || hovered) {
            drawRoundedRect(graphics, sx(left), sy(473), sx(right), sy(493), ss(5), fill);
        }
        int center = (left + right) / 2;
        int color = selected ? serviceAccentBright() : 0xFF768B9B;
        if (icon == 0) drawTargetIcon(graphics, center - 42, 483, color);
        else if (icon == 1) drawMessageIcon(graphics, center - 48, 483, color);
        else drawGearIcon(graphics, center - 42, 483, color);
        textCentered(graphics, label, center + 15, 478, 6, color);
        if (selected) graphics.fill(sx(left + 74), sy(494), sx(right - 74), sy(496), serviceAccent());
    }

    private void drawUnauthorized(GuiGraphics graphics) {
        drawPanel(graphics, 210, 120, 710, 410, 0xFF091722);
        fillCircle(graphics, sx(460), sy(227), ss(35), 0xFF42212A);
        textCentered(graphics, "!", 460, 206, 24, 0xFFFF7984);
        textCentered(graphics, ui("ERİŞİM REDDEDİLDİ", "ACCESS DENIED"), 460, 286, 13, 0xFFF3F7FA);
        textCentered(graphics, ui("Bu servis için yetkili role ve doğru tablete ihtiyacın var.",
                        "You need the authorized service role and matching tablet."),
                460, 321, 7, 0xFF8397A8);
    }

    private void drawNotice(GuiGraphics graphics) {
        String notice = EmergencyClientData.noticeText();
        if (notice.isBlank()) return;
        drawRoundedRect(graphics, sx(285), sy(59), sx(635), sy(91), ss(6), 0xE80A1118);
        graphics.fill(sx(285), sy(65), sx(289), sy(85), serviceAccent());
        text(graphics, notice, 301, 70, 6, 0xFFF0F5F9, 320);
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom, int fill) {
        drawPanel(graphics, left, top, right, bottom, fill, servicePanelBorder());
    }

    private void drawPanel(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int fill,
            int border
    ) {
        drawRoundedRect(graphics, sx(left + 2), sy(top + 3), sx(right + 2), sy(bottom + 3), ss(7), 0x5A000000);
        drawRoundedRect(graphics, sx(left), sy(top), sx(right), sy(bottom), ss(7), border);
        drawRoundedRect(graphics, sx(left + 1), sy(top + 1), sx(right - 1), sy(bottom - 1), ss(6), fill);
    }

    private void drawActionButton(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int left,
            int top,
            int right,
            int bottom,
            String label,
            int color,
            boolean enabled
    ) {
        boolean hovered = enabled && inside(mouseX, mouseY, left, top, right, bottom);
        int fill = hovered ? lighten(color) : color;
        if (!enabled) fill = 0xFF27333D;
        drawRoundedRect(graphics, sx(left + 2), sy(top + 2), sx(right + 2), sy(bottom + 2), ss(6), 0x60000000);
        drawRoundedRect(graphics, sx(left), sy(top), sx(right), sy(bottom), ss(6), lighten(fill));
        drawRoundedRect(graphics, sx(left + 1), sy(top + 1), sx(right - 1), sy(bottom - 1), ss(5), fill);
        graphics.fill(sx(left + 10), sy(top + 1), sx(right - 10), sy(top + 2), 0x35FFFFFF);
        textCentered(graphics, label, (left + right) / 2.0F, top + 14, 7,
                enabled ? 0xFFFFFFFF : 0xFF77838D);
    }

    private void drawMapControl(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int left,
            int top,
            String label,
            boolean enabled,
            boolean active
    ) {
        int right = left + 32;
        int bottom = top + 26;
        boolean hovered = enabled && inside(mouseX, mouseY, left, top, right, bottom);
        int fill = active ? serviceControlFill() : hovered ? 0xFF173149 : 0xD50A1722;
        drawRoundedRect(graphics, sx(left), sy(top), sx(right), sy(bottom), ss(5), 0xFF244157);
        drawRoundedRect(graphics, sx(left + 1), sy(top + 1), sx(right - 1), sy(bottom - 1), ss(4), fill);
        if (!label.isBlank()) textCentered(graphics, label, left + 16, top + 7, 7,
                enabled ? 0xFFEAF1F6 : 0xFF536473);
    }

    private void drawServiceBadge(GuiGraphics graphics, int centerX, int centerY) {
        fillCircle(graphics, sx(centerX), sy(centerY), ss(12), serviceSelectedFill());
        if (service == EmergencyService.EMS) {
            graphics.fill(sx(centerX - 2), sy(centerY - 7), sx(centerX + 2), sy(centerY + 7), serviceAccentBright());
            graphics.fill(sx(centerX - 7), sy(centerY - 2), sx(centerX + 7), sy(centerY + 2), serviceAccentBright());
        } else {
            drawPoliceShield(graphics, centerX, centerY);
        }
    }

    private void drawPoliceShield(GuiGraphics graphics, int centerX, int centerY) {
        int accent = serviceAccentBright();
        graphics.fill(sx(centerX - 6), sy(centerY - 7), sx(centerX + 6), sy(centerY + 2), accent);
        for (int row = 0; row < 7; row++) {
            int inset = row / 2;
            graphics.fill(sx(centerX - 6 + inset), sy(centerY + 2 + row),
                    sx(centerX + 6 - inset), sy(centerY + 3 + row), accent);
        }
    }

    private void drawTargetIcon(GuiGraphics graphics, int centerX, int centerY, int color) {
        fillCircle(graphics, sx(centerX), sy(centerY), ss(8), withAlpha(color, 46));
        fillCircle(graphics, sx(centerX), sy(centerY), ss(4), color);
        graphics.fill(sx(centerX - 10), sy(centerY), sx(centerX - 5), sy(centerY + 1), color);
        graphics.fill(sx(centerX + 5), sy(centerY), sx(centerX + 10), sy(centerY + 1), color);
        graphics.fill(sx(centerX), sy(centerY - 10), sx(centerX + 1), sy(centerY - 5), color);
        graphics.fill(sx(centerX), sy(centerY + 5), sx(centerX + 1), sy(centerY + 10), color);
    }

    private void drawMessageIcon(GuiGraphics graphics, int centerX, int centerY, int color) {
        drawRoundedRect(graphics, sx(centerX - 8), sy(centerY - 6), sx(centerX + 8), sy(centerY + 5), ss(3), color);
        graphics.fill(sx(centerX - 4), sy(centerY + 4), sx(centerX), sy(centerY + 9), color);
    }

    private void drawGearIcon(GuiGraphics graphics, int centerX, int centerY, int color) {
        fillCircle(graphics, sx(centerX), sy(centerY), ss(7), color);
        fillCircle(graphics, sx(centerX), sy(centerY), ss(3), 0xFF08131D);
        graphics.fill(sx(centerX - 1), sy(centerY - 10), sx(centerX + 2), sy(centerY - 6), color);
        graphics.fill(sx(centerX - 1), sy(centerY + 6), sx(centerX + 2), sy(centerY + 10), color);
        graphics.fill(sx(centerX - 10), sy(centerY - 1), sx(centerX - 6), sy(centerY + 2), color);
        graphics.fill(sx(centerX + 6), sy(centerY - 1), sx(centerX + 10), sy(centerY + 2), color);
    }

    private void drawToggle(GuiGraphics graphics, int centerX, int centerY, boolean enabled) {
        int fill = enabled ? serviceActionColor() : 0xFF2B3944;
        drawRoundedRect(graphics, sx(centerX - 25), sy(centerY - 10), sx(centerX + 25), sy(centerY + 10), ss(10), fill);
        fillCircle(graphics, sx(centerX + (enabled ? 14 : -14)), sy(centerY), ss(7), 0xFFF0F4F7);
    }

    private void drawTabletRoute(
            GuiGraphics graphics,
            PhoneGpsMapRenderer.Projection projection,
            PhoneGpsState.Location target,
            int color
    ) {
        if (minecraft == null || minecraft.player == null || projection == null || target == null) return;
        String dimension = minecraft.player.level().dimension().location().toString();
        if (!dimension.equals(target.dimension()) || !dimension.equals(projection.center().dimension())) return;
        double x1 = projection.worldToScreenX(minecraft.player.getX());
        double y1 = projection.worldToScreenZ(minecraft.player.getZ());
        double x2 = projection.worldToScreenX(target.x());
        double y2 = projection.worldToScreenZ(target.z());
        double distance = Math.hypot(x2 - x1, y2 - y1);
        int steps = Math.max(1, (int) Math.ceil(distance / Math.max(4.0D, ss(6))));
        for (int index = 0; index <= steps; index++) {
            double t = index / (double) steps;
            int x = (int) Math.round(x1 + (x2 - x1) * t);
            int y = (int) Math.round(y1 + (y2 - y1) * t);
            if (projection.contains(x, y)) fillCircle(graphics, x, y, Math.max(1, ss(2)), withAlpha(color, 215));
        }
    }

    private void drawSignalBars(GuiGraphics graphics, int x, int y) {
        for (int index = 0; index < 4; index++) {
            graphics.fill(sx(x + index * 5), sy(y + 9 - index * 3), sx(x + index * 5 + 3), sy(y + 13), 0xFFD7E0E8);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);

        if (closeConfirmDispatchId > 0L) {
            if (inside(mouseX, mouseY, 285, 312, 445, 346)) {
                closeConfirmDispatchId = 0L;
                return true;
            }
            if (inside(mouseX, mouseY, 475, 312, 635, 346)) {
                EmergencyStateData.DispatchEntry entry = EmergencyClientData.find(service, closeConfirmDispatchId);
                if (entry != null && EmergencyClientData.isActive(entry)
                        && entry.acceptedBySelf && entry.onSceneBySelf) {
                    TabletNavigationState.clear();
                    EmergencyClientData.close(entry.id, service);
                }
                closeConfirmDispatchId = 0L;
                return true;
            }
            return true;
        }

        if (inside(mouseX, mouseY, 878, 11, 908, 49)) {
            onClose();
            return true;
        }
        if (!EmergencyClientData.isAuthorized(service)) return true;

        if (inside(mouseX, mouseY, 18, 473, 300, 493)) {
            switchPage(TabletPage.DISPATCH, -1);
            return true;
        }
        if (inside(mouseX, mouseY, 318, 473, 600, 493)) {
            switchPage(TabletPage.MESSAGES, page == TabletPage.SETTINGS ? -1 : 1);
            return true;
        }
        if (inside(mouseX, mouseY, 618, 473, 900, 493)) {
            switchPage(TabletPage.SETTINGS, 1);
            return true;
        }

        if (page == TabletPage.MESSAGES) {
            if (chatInput != null && super.mouseClicked(mouseX, mouseY, button)) return true;
            if (inside(mouseX, mouseY, 790, 419, 898, 459)) {
                sendChat();
                return true;
            }
            return true;
        }
        if (page == TabletPage.SETTINGS) {
            if (inside(mouseX, mouseY, 32, 138, 888, 192)) TabletSettingsStore.cycleLanguage();
            else if (inside(mouseX, mouseY, 32, 202, 888, 256)) TabletSettingsStore.toggleIncomingPopup();
            else if (inside(mouseX, mouseY, 32, 266, 888, 320)) TabletSettingsStore.toggleAutoOpenIncoming();
            else if (inside(mouseX, mouseY, 32, 330, 888, 384)) return true;
            else if (inside(mouseX, mouseY, 32, 394, 888, 448)) TabletSettingsStore.toggleNavigationHud();
            else if (inside(mouseX, mouseY, 760, 109, 888, 133)) TabletSettingsStore.reset();
            return true;
        }

        EmergencyStateData.DispatchEntry selected = selectedDispatchId <= 0L
                ? null : EmergencyClientData.find(service, selectedDispatchId);
        float focus = assignmentFocusProgress();
        if (focus > 0.5F && selected != null) {
            if (!selected.acceptedBySelf && inside(mouseX, mouseY, 18, 72, 146, 95)) {
                setAssignmentFocus(false);
                return true;
            }
            if (inside(mouseX, mouseY, 860, 116, 892, 142) && mapZoom > 0) {
                mapZoom--;
                return true;
            }
            if (inside(mouseX, mouseY, 860, 148, 892, 174) && mapZoom < 3) {
                mapZoom++;
                return true;
            }
            return handleActions(mouseX, mouseY, selected, true);
        }

        List<EmergencyStateData.DispatchEntry> calls = EmergencyClientData.activeFor(service);
        int count = Math.min(VISIBLE_CALLS, Math.max(0, calls.size() - scroll));
        for (int index = 0; index < count; index++) {
            int top = 108 + index * 77;
            if (inside(mouseX, mouseY, 18, top, 230, top + 68)) {
                EmergencyStateData.DispatchEntry clicked = calls.get(scroll + index);
                selectedDispatchId = clicked.id;
                incomingFocus = false;
                mapFocusUnit = true;
                setAssignmentFocus(false);
                return true;
            }
        }
        if (inside(mouseX, mouseY, 862, 105, 894, 131) && mapZoom > 0) {
            mapZoom--;
            return true;
        }
        if (inside(mouseX, mouseY, 862, 137, 894, 163) && mapZoom < 3) {
            mapZoom++;
            return true;
        }
        if (inside(mouseX, mouseY, 862, 169, 894, 195)) {
            mapFocusUnit = !mapFocusUnit;
            return true;
        }
        if (selected == null || !EmergencyClientData.isActive(selected)) return true;
        return handleActions(mouseX, mouseY, selected, false);
    }

    private boolean handleActions(
            double mouseX,
            double mouseY,
            EmergencyStateData.DispatchEntry selected,
            boolean focused
    ) {
        int left = focused ? 10 : 248;
        int right = 910;
        if (!selected.acceptedBySelf) {
            if (inside(mouseX, mouseY, left, 422, right, 464)
                    && selected.responderCount < selected.maximumResponders) {
                EmergencyClientData.accept(selected.id, service);
                selectedDispatchId = selected.id;
                incomingFocus = false;
                mapFocusUnit = false;
                return true;
            }
            return true;
        }

        int mid = left + (right - left) / 2 - 4;
        if (!selected.onSceneBySelf) {
            if (inside(mouseX, mouseY, left, 422, mid, 464)) {
                navigate(selected);
                return true;
            }
        } else if (inside(mouseX, mouseY, mid + 8, 422, right, 464)) {
            closeConfirmDispatchId = selected.id;
            return true;
        }
        return true;
    }

    private void navigate(EmergencyStateData.DispatchEntry selected) {
        TabletNavigationState.begin(minecraft, service, selected.id, location(selected));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D) return super.mouseScrolled(mouseX, mouseY, delta);
        if (page == TabletPage.MESSAGES && inside(mouseX, mouseY, 250, 118, 900, 412)) {
            int maximum = Math.max(0, EmergencyClientData.chatFor(service).size() - VISIBLE_CHAT);
            chatScroll = delta < 0.0D ? Math.min(maximum, chatScroll + 1) : Math.max(0, chatScroll - 1);
            return true;
        }
        if (page != TabletPage.DISPATCH) return super.mouseScrolled(mouseX, mouseY, delta);
        if (assignmentFocusProgress() > 0.5F) {
            if (inside(mouseX, mouseY, 10, 100, 910, 330)) {
                mapZoom = delta > 0.0D ? Math.max(0, mapZoom - 1) : Math.min(3, mapZoom + 1);
                return true;
            }
        } else {
            if (inside(mouseX, mouseY, 10, 100, 238, 418)) {
                int maximum = Math.max(0, EmergencyClientData.activeFor(service).size() - VISIBLE_CALLS);
                scroll = delta < 0.0D ? Math.min(maximum, scroll + 1) : Math.max(0, scroll - 1);
                return true;
            }
            if (inside(mouseX, mouseY, 248, 96, 910, 252)) {
                mapZoom = delta > 0.0D ? Math.max(0, mapZoom - 1) : Math.min(3, mapZoom + 1);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closeConfirmDispatchId > 0L && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeConfirmDispatchId = 0L;
            return true;
        }
        if (page == TabletPage.MESSAGES && chatInput != null && chatInput.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            sendChat();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendChat() {
        if (chatInput == null) return;
        String message = chatInput.getValue().trim();
        if (message.isEmpty()) return;
        EmergencyClientData.sendTabletChat(service, message);
        chatInput.setValue("");
        chatScroll = 0;
        chatInput.setFocused(true);
    }

    private void switchPage(TabletPage next, int direction) {
        if (next == null || next == page) return;
        page = next;
        pageSlideDirection = direction == 0 ? 1 : direction;
        pageChangedAt = System.currentTimeMillis();
        if (page != TabletPage.DISPATCH) setAssignmentFocus(false);
        updateChatInputVisibility();
        if (page == TabletPage.MESSAGES) EmergencyClientData.requestSync();
    }

    private void updateChatInputVisibility() {
        if (chatInput == null) return;
        boolean visible = page == TabletPage.MESSAGES && EmergencyClientData.isAuthorized(service);
        chatInput.visible = visible;
        chatInput.active = visible;
        chatInput.setEditable(visible);
        if (!visible) chatInput.setFocused(false);
    }

    private void setAssignmentFocus(boolean target) {
        float current = assignmentFocusProgress();
        if (focusTarget == target && Math.abs(current - (target ? 1.0F : 0.0F)) < 0.001F) return;
        focusAnimationFrom = current;
        focusTarget = target;
        focusAnimationStartedAt = System.currentTimeMillis();
    }

    private float assignmentFocusProgress() {
        if (focusAnimationStartedAt <= 0L) return focusTarget ? 1.0F : 0.0F;
        float elapsed = (System.currentTimeMillis() - focusAnimationStartedAt) / (float) FOCUS_ANIMATION_MS;
        if (elapsed >= 1.0F) return focusTarget ? 1.0F : 0.0F;
        // Assignment transitions should feel snappy; ease-out avoids the old sluggish start.
        float eased = easeOut(Math.max(0.0F, elapsed));
        float target = focusTarget ? 1.0F : 0.0F;
        return focusAnimationFrom + (target - focusAnimationFrom) * eased;
    }

    private float pageIntroProgress() {
        return Math.max(0.0F, Math.min(1.0F,
                (System.currentTimeMillis() - pageChangedAt) / (float) PAGE_ANIMATION_MS));
    }

    private PhoneGpsState.Location currentLocation() {
        if (minecraft == null || minecraft.player == null) {
            return new PhoneGpsState.Location("minecraft:overworld", 0, 64, 0);
        }
        return new PhoneGpsState.Location(
                minecraft.player.level().dimension().location().toString(),
                (int) Math.floor(minecraft.player.getX()),
                (int) Math.floor(minecraft.player.getY()),
                (int) Math.floor(minecraft.player.getZ())
        );
    }

    private String distanceLabel(EmergencyStateData.DispatchEntry entry) {
        if (minecraft == null || minecraft.player == null) return ui("Konum alınıyor", "Locating");
        String currentDimension = minecraft.player.level().dimension().location().toString();
        if (!currentDimension.equals(entry.dimension)) return ui("Farklı boyut", "Different dimension");
        double dx = minecraft.player.getX() - entry.x;
        double dy = minecraft.player.getY() - entry.y;
        double dz = minecraft.player.getZ() - entry.z;
        return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + " " + ui("blok", "blocks");
    }

    private static PhoneGpsState.Location location(EmergencyStateData.DispatchEntry entry) {
        return new PhoneGpsState.Location(entry.dimension, entry.x, entry.y, entry.z);
    }

    private static String categoryName(EmergencyStateData.DispatchEntry entry) {
        EmergencyCategory category = EmergencyClientData.category(entry);
        return category == null ? entry.categoryId : t(category.translationKey());
    }

    private static String statusLabel(String status) {
        String normalized = status == null ? "waiting" : status.toLowerCase(Locale.ROOT);
        return t("emergency.newphone.status." + normalized);
    }

    private static String riskKey(String risk) {
        return "emergency.newphone.risk." + (risk == null ? "low" : risk.toLowerCase(Locale.ROOT));
    }

    private List<String> recentChatMembers(List<EmergencyStateData.ChatEntry> messages) {
        Set<String> names = new LinkedHashSet<>();
        for (int i = messages.size() - 1; i >= 0 && names.size() < 5; i--) {
            String name = safe(messages.get(i).senderName);
            if (!name.isBlank()) names.add(name);
        }
        return new ArrayList<>(names);
    }

    private static String formatChatTime(long timestamp) {
        if (timestamp <= 0L) return "--:--";
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime().format(CLOCK_FORMAT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String ui(String turkish, String english) {
        return TabletSettingsStore.text(turkish, english);
    }

    private static String onOff(boolean enabled) {
        return enabled ? ui("AÇIK", "ON") : ui("KAPALI", "OFF");
    }

    private int serviceAccent() {
        return service == EmergencyService.POLICE ? 0xFF1E91FF : 0xFFE94B59;
    }

    private int serviceAccentBright() {
        return service == EmergencyService.POLICE ? 0xFF53B7FF : 0xFFFF7781;
    }

    private int serviceDividerColor() {
        return service == EmergencyService.POLICE ? 0xFF153A59 : 0xFF4A2730;
    }

    private int servicePanelBorder() {
        return service == EmergencyService.POLICE ? 0xFF17344B : 0xFF482932;
    }

    private int serviceSelectedFill() {
        return service == EmergencyService.POLICE ? 0xFF0D2A42 : 0xFF342029;
    }

    private int serviceControlFill() {
        return service == EmergencyService.POLICE ? 0xFF154D7B : 0xFF6B2B37;
    }

    private int serviceActionColor() {
        return service == EmergencyService.POLICE ? 0xFF0C65B6 : 0xFFB33140;
    }

    private static int statusColor(String status) {
        if ("ON_SCENE".equalsIgnoreCase(status)) return 0xFF62CD92;
        if ("CLOSED".equalsIgnoreCase(status)) return 0xFF62CD92;
        if ("CANCELLED".equalsIgnoreCase(status)) return 0xFFFF7782;
        if ("RESPONDING".equalsIgnoreCase(status)) return 0xFF5FB5FF;
        return 0xFFFFBD59;
    }

    private static int riskColor(String risk) {
        try {
            EmergencyRisk parsed = EmergencyRisk.valueOf(risk);
            return parsed == EmergencyRisk.HIGH ? 0xFFFF5361
                    : parsed == EmergencyRisk.MEDIUM ? 0xFFFFB54A
                    : 0xFF5BCB8A;
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return 0xFF5BCB8A;
        }
    }

    private static int lighten(int color) {
        int red = Math.min(255, ((color >> 16) & 0xFF) + 16);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 16);
        int blue = Math.min(255, (color & 0xFF) + 16);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static float easeOut(float value) {
        float inverse = 1.0F - Math.max(0.0F, Math.min(1.0F, value));
        return 1.0F - inverse * inverse * inverse;
    }

    private static float easeInOut(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F;
    }
}

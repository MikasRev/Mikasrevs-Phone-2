package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.data.PhoneBankClientData;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.PhoneBankStateData;
import com.mikasrevs.newphone.network.PhoneStateData;
import com.mikasrevs.newphone.integration.currency.PhoneCurrencyAmounts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Server-backed account and contact transfer screen for the selected economy provider. */
public final class ZPhoneBankScreen extends AbstractZPhoneScreen {
    private static final long REQUEST_TIMEOUT_MS = 10_000L;
    private static final int MAX_QUANTITY_DIGITS = 19;
    private static final int PICKER_ROWS = 5;

    private static final int BG = 0xFF0B0D0E;
    private static final int SURFACE = 0xFF171A1B;
    private static final int SURFACE_RAISED = 0xFF202425;
    private static final int SURFACE_HOVER = 0xFF282D2E;
    private static final int DIVIDER = 0xFF303536;
    private static final int PRIMARY = 0xFF39C98B;
    private static final int PRIMARY_DARK = 0xFF163D2E;
    private static final int WARM = 0xFFF0B85A;
    private static final int ERROR = 0xFFFF7474;
    private static final int TEXT = 0xFFF5F7F6;
    private static final int TEXT_SECONDARY = 0xFFA7B0AD;
    private static final int TEXT_TERTIARY = 0xFF8B9692;

    private Tab tab = Tab.ACCOUNT;
    private String selectedBalanceId = "";
    private long selectedUnitCore;
    private int contactScroll;
    private int syncTicks;
    private final StringBuilder quantity = new StringBuilder();
    private final StringBuilder contactSearch = new StringBuilder();
    private PhoneStateData.PlayerEntry selectedContact;
    private boolean amountFocused;
    private boolean contactPicker;
    private boolean contactSearchFocused;
    private boolean confirmation;
    private String pendingRequestId = "";
    private long pendingSince;
    private String retryRequestId = "";
    private String retryTargetUuid = "";
    private String retryCurrencyId = "";
    private long retryCoreAmount;
    private int retryAttempts;
    private long observedStateAt;
    private String bannerCode = "";
    private String bannerPeer = "";
    private String bannerAmount = "";
    private long bannerUntil;

    public ZPhoneBankScreen() {
        super(Component.translatable("screen.newphone.app.bank"));
    }

    @Override
    protected void init() {
        super.init();
        restoreTransferOperation();
        PhoneBankClientData.requestSync();
    }

    @Override
    public void tick() {
        super.tick();
        restoreTransferOperation();
        PhoneBankStateData state = PhoneBankClientData.state();
        if (state.updatedAt > 0L && state.updatedAt != observedStateAt) {
            observedStateAt = state.updatedAt;
            if (state.resultCode != null && !state.resultCode.isBlank()) {
                showBanner(state.resultCode, state.resultPeerName, state.resultAmount);
            }
            boolean matchesPending = !pendingRequestId.isBlank() && pendingRequestId.equals(state.responseRequestId);
            boolean matchesRetry = !retryRequestId.isBlank() && retryRequestId.equals(state.responseRequestId);
            if (matchesPending || matchesRetry) {
                if ("SENT".equals(state.resultCode)) quantity.setLength(0);
                pendingRequestId = "";
                pendingSince = 0L;
                confirmation = false;
                if ("PENDING".equals(state.resultCode)) PhoneBankClientData.markTransferTimedOut();
                else clearRetry();
            }
        }
        if (!pendingRequestId.isBlank() && System.currentTimeMillis() - pendingSince > REQUEST_TIMEOUT_MS) {
            if (retryAttempts == 0 && pendingRequestId.equals(retryRequestId)) {
                pendingRequestId = PhoneBankClientData.retryTransfer();
                PhoneBankClientData.TransferOperation operation = PhoneBankClientData.operation();
                pendingSince = operation == null ? System.currentTimeMillis() : operation.lastSentAt();
                retryAttempts = operation == null ? 1 : operation.retryAttempts();
                showBanner("RETRYING", "", "");
            } else {
                pendingRequestId = "";
                pendingSince = 0L;
                confirmation = false;
                PhoneBankClientData.markTransferTimedOut();
                showBanner("TIMEOUT", "", "");
            }
        }
        if (++syncTicks >= 40 && pendingRequestId.isBlank()) {
            syncTicks = 0;
            PhoneBankClientData.requestSync();
        }
        clampSelections();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(x(3), y(8), x(297), y(606), BG);
        drawStatusBar(graphics);
        drawHeader(graphics, mouseX, mouseY);
        drawTabs(graphics);
        String status = PhoneBankClientData.status();
        if ("LOADING".equals(status)) {
            renderLoading(graphics);
        } else if (!PhoneBankClientData.available()) {
            renderUnavailable(graphics, status);
        } else if (tab == Tab.ACCOUNT) {
            renderAccount(graphics, mouseX, mouseY);
        } else {
            renderTransfer(graphics, mouseX, mouseY);
        }

        renderBanner(graphics);
        if (contactPicker) renderContactPicker(graphics, mouseX, mouseY);
        if (confirmation) renderConfirmation(graphics, mouseX, mouseY);
        drawHomeIndicator(graphics);
        drawFrame(graphics);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        drawBackButton(graphics);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Banka", "Bank"),
                x(57), y(49), size(14), TEXT);
        String provider = safe(PhoneBankClientData.provider(), PhoneSettingsStore.text("Banka", "Bank"));
        PhoneModernText.drawColored(graphics, fitToPixels(provider, size(7), size(170)),
                x(57), y(68), size(7), TEXT_TERTIARY);

        boolean hovered = inside(mouseX, mouseY, 248, 47, 282, 80) && !contactPicker && !confirmation;
        drawRoundedRect(graphics, x(249), y(48), x(281), y(79), size(8),
                hovered ? SURFACE_HOVER : SURFACE_RAISED);
        drawRefreshGlyph(graphics, 265, 63, PhoneBankClientData.available() ? PRIMARY : TEXT_SECONDARY);
        graphics.fill(x(18), y(83), x(282), y(84), DIVIDER);
    }

    private void drawTabs(GuiGraphics graphics) {
        drawRoundedRect(graphics, x(18), y(91), x(282), y(120), size(8), SURFACE);
        drawRoundedRect(graphics, x(tab == Tab.ACCOUNT ? 20 : 151), y(93),
                x(tab == Tab.ACCOUNT ? 149 : 280), y(118), size(6), PRIMARY_DARK);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Hesap", "Account"),
                x(84), y(100), size(8), tab == Tab.ACCOUNT ? TEXT : TEXT_SECONDARY);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Transfer", "Transfer"),
                x(216), y(100), size(8), tab == Tab.TRANSFER ? TEXT : TEXT_SECONDARY);
    }

    private void renderLoading(GuiGraphics graphics) {
        long phase = (System.currentTimeMillis() / 320L) % 3L;
        drawRoundedRect(graphics, x(18), y(132), x(282), y(260), size(8), SURFACE);
        drawRoundedRect(graphics, x(34), y(148), x(154), y(157), size(4), SURFACE_RAISED);
        drawRoundedRect(graphics, x(34), y(181), x(225), y(205), size(6), SURFACE_RAISED);
        drawRoundedRect(graphics, x(34), y(225), x(126), y(234), size(4), SURFACE_RAISED);
        for (int row = 0; row < 3; row++) {
            int top = 350 + row * 54;
            drawRoundedRect(graphics, x(18), y(top), x(282), y(top + 45), size(7), SURFACE);
            fillCircle(graphics, x(39), y(top + 22), size(11), SURFACE_RAISED);
            drawRoundedRect(graphics, x(60), y(top + 13), x(145 + row * 12), y(top + 21), size(3), SURFACE_RAISED);
            drawRoundedRect(graphics, x(197), y(top + 27), x(266), y(top + 35), size(3), SURFACE_RAISED);
        }
        for (int i = 0; i < 3; i++) {
            fillCircle(graphics, x(138 + i * 12), y(296), size(3), i == phase ? PRIMARY : DIVIDER);
        }
        PhoneModernText.drawCenteredColored(graphics,
                PhoneSettingsStore.text("Hesap bağlanıyor", "Connecting account"),
                x(150), y(312), size(8), TEXT_SECONDARY);
    }

    private void renderUnavailable(GuiGraphics graphics, String status) {
        fillCircle(graphics, x(150), y(196), size(28), 0xFF45351D);
        PhoneModernText.drawCenteredColored(graphics, "!", x(150), y(180), size(22), WARM);
        drawCenteredFitted(graphics, safe(PhoneBankClientData.provider(),
                PhoneSettingsStore.text("Banka", "Bank")), 150, 240, 248, 12, TEXT);
        String detail = "MOD_MISSING".equals(status)
                ? PhoneSettingsStore.text("Sunucuda kurulu değil", "Not installed on the server")
                : PhoneSettingsStore.text("Hesaba ulaşılamadı", "Account unavailable");
        PhoneModernText.drawCenteredColored(graphics, detail, x(150), y(269), size(8), TEXT_SECONDARY);
        drawRoundedRect(graphics, x(52), y(313), x(248), y(359), size(8), PRIMARY_DARK);
        drawRefreshGlyph(graphics, 94, 336, PRIMARY);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Yenile", "Refresh"),
                x(159), y(326), size(9), TEXT);
    }

    private void renderAccount(GuiGraphics graphics, int mouseX, int mouseY) {
        PhoneBankStateData.BalanceEntry balance = selectedBalance();
        List<PhoneBankStateData.BalanceEntry> balances = PhoneBankClientData.balances();
        int selectedIndex = selectedBalanceIndex();

        drawRoundedRect(graphics, x(18), y(132), x(282), y(260), size(8), SURFACE);
        drawRoundedRect(graphics, x(18), y(132), x(22), y(260), size(2), WARM);
        String account = safe(PhoneBankClientData.accountName(), PhoneClientData.selfName());
        PhoneModernText.drawColored(graphics, fitToPixels(account, size(8), size(154)),
                x(34), y(147), size(8), TEXT_SECONDARY);
        fillCircle(graphics, x(226), y(153), size(4), PRIMARY);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Bağlı", "Connected"),
                x(238), y(146), size(8), PRIMARY);
        PhoneModernText.drawColored(graphics,
                PhoneSettingsStore.text("Kullanılabilir bakiye", "Available balance"),
                x(34), y(174), size(8), TEXT_TERTIARY);
        if (balance == null) {
            PhoneModernText.drawColored(graphics, "0", x(34), y(194), size(24), TEXT);
        } else {
            drawFitted(graphics, safe(balance.formatted, "0"), 34, 194, 212, 24, TEXT);
            PhoneModernText.drawColored(graphics,
                    fitToPixels(safe(balance.name, PhoneSettingsStore.text("Para birimi", "Currency")),
                            size(8), size(160)), x(34), y(230), size(8), WARM);
        }
        PhoneModernText.drawColored(graphics, balances.isEmpty() ? "0 / 0" : (selectedIndex + 1) + " / " + balances.size(),
                x(34), y(246), size(7), TEXT_TERTIARY);
        if (balances.size() > 1) {
            drawCircleChevronButton(graphics, 230, 236, false,
                    inside(mouseX, mouseY, 214, 219, 246, 254));
            drawCircleChevronButton(graphics, 263, 236, true,
                    inside(mouseX, mouseY, 247, 219, 280, 254));
        }

        boolean sendHover = inside(mouseX, mouseY, 18, 275, 282, 323);
        drawRoundedRect(graphics, x(18), y(275), x(282), y(323), size(8),
                sendHover ? 0xFF48D99A : PRIMARY);
        drawSendGlyph(graphics, 91, 299, BG);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Para gönder", "Send money"),
                x(112), y(289), size(8), BG);

        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Bakiyeler", "Balances"),
                x(18), y(344), size(9), TEXT);
        String count = balances.size() + " " + PhoneSettingsStore.text("para birimi", "currencies");
        drawRightFitted(graphics, count, 282, 345, 118, 7, TEXT_TERTIARY);
        int balanceStart = visibleBalanceStart(balances.size(), selectedIndex);
        int shown = Math.min(3, balances.size());
        drawRoundedRect(graphics, x(18), y(363), x(282), y(363 + Math.max(1, shown) * 52),
                size(8), SURFACE);
        for (int row = 0; row < shown; row++) {
            int i = balanceStart + row;
            PhoneBankStateData.BalanceEntry entry = balances.get(i);
            int top = 363 + row * 52;
            boolean selected = i == selectedIndex;
            boolean hovered = inside(mouseX, mouseY, 18, top, 282, top + 52);
            if (selected || hovered) {
                graphics.fill(x(19), y(top + 1), x(281), y(top + 51), selected ? 0xFF222B27 : SURFACE_RAISED);
            }
            if (selected) graphics.fill(x(18), y(top + 7), x(22), y(top + 45), PRIMARY);
            if (row > 0) graphics.fill(x(58), y(top), x(270), y(top + 1), DIVIDER);
            fillCircle(graphics, x(40), y(top + 26), size(13), selected ? 0xFF5A4521 : SURFACE_RAISED);
            PhoneModernText.drawCenteredColored(graphics, currencyInitial(entry), x(40), y(top + 18),
                    size(9), selected ? WARM : TEXT_SECONDARY);
            String fallback = PhoneSettingsStore.text("Para birimi", "Currency");
            PhoneModernText.drawColored(graphics, fitToPixels(safe(entry.name, fallback), size(8), size(112)),
                    x(62), y(top + 11), size(8), TEXT);
            drawRightFitted(graphics, safe(entry.formatted, "0"), 268, top + 28, 116, 8,
                    selected ? PRIMARY : TEXT_SECONDARY);
        }
    }

    private void renderTransfer(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean pending = !pendingRequestId.isBlank();
        PhoneBankStateData.BalanceEntry balance = selectedBalance();
        PhoneBankStateData.UnitEntry unit = selectedUnit();

        boolean recipientHover = !pending && inside(mouseX, mouseY, 18, 132, 282, 188);
        drawRoundedRect(graphics, x(18), y(132), x(282), y(188), size(8),
                recipientHover ? SURFACE_HOVER : SURFACE);
        if (selectedContact == null) {
            fillCircle(graphics, x(47), y(160), size(18), SURFACE_RAISED);
            drawPersonGlyph(graphics, 47, 160, TEXT_SECONDARY);
            PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Alıcı", "Recipient"),
                    x(76), y(143), size(7), TEXT_TERTIARY);
            PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Kişi seç", "Select contact"),
                    x(76), y(160), size(10), TEXT);
        } else {
            PhonePlayerHeadRenderer.draw(graphics, selectedContact.uuid, x(28), y(141), size(38));
            PhoneModernText.drawColored(graphics,
                    fitToPixels(safe(selectedContact.name, PhoneSettingsStore.text("Oyuncu", "Player")),
                            size(10), size(150)), x(76), y(142), size(10), TEXT);
            String phone = PhoneClientData.formatPhoneNumber(selectedContact.phoneNumber);
            String presence = selectedContact.online
                    ? PhoneSettingsStore.text("Çevrimiçi", "Online")
                    : PhoneSettingsStore.text("Çevrimdışı", "Offline");
            PhoneModernText.drawColored(graphics, fitToPixels(phone + "  ·  " + presence, size(7), size(158)),
                    x(76), y(165), size(7), selectedContact.online ? PRIMARY : TEXT_TERTIARY);
        }
        drawChevron(graphics, 263, 160, true, TEXT_SECONDARY);

        int balanceCount = PhoneBankClientData.balances().size();
        int unitCount = balance == null || balance.units == null ? 0 : balance.units.size();
        drawSelector(graphics, 18, 146, PhoneSettingsStore.text("Para birimi", "Currency"),
                balance == null ? "-" : safe(balance.name, PhoneSettingsStore.text("Para birimi", "Currency")),
                balanceCount, !pending, mouseX, mouseY);
        drawSelector(graphics, 154, 282, PhoneSettingsStore.text("Kupür", "Denomination"),
                unit == null ? "-" : safe(unit.name, PhoneSettingsStore.text("Birim", "Unit")),
                unitCount, !pending, mouseX, mouseY);

        drawRoundedRect(graphics, x(18), y(271), x(282), y(410), size(8),
                amountFocused ? PRIMARY : SURFACE);
        drawRoundedRect(graphics, x(amountFocused ? 20 : 18), y(amountFocused ? 273 : 271),
                x(amountFocused ? 280 : 282), y(amountFocused ? 408 : 410), size(7), SURFACE);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Miktar", "Amount"),
                x(34), y(287), size(8), TEXT_SECONDARY);
        boolean maxHover = !pending && inside(mouseX, mouseY, 220, 280, 269, 309);
        drawRoundedRect(graphics, x(220), y(280), x(269), y(309), size(7),
                maxHover ? SURFACE_HOVER : pending ? SURFACE : SURFACE_RAISED);
        PhoneModernText.drawCenteredColored(graphics, "MAX", x(244), y(289), size(7),
                pending ? TEXT_TERTIARY : WARM);

        String shown = quantity.length() == 0 ? "0" : quantity.toString();
        drawCenteredFitted(graphics, shown, 150, 320, 142, 27,
                quantity.length() == 0 ? TEXT_TERTIARY : TEXT);
        drawStepper(graphics, 44, 341, false,
                !pending && inside(mouseX, mouseY, 27, 324, 61, 359), !pending);
        drawStepper(graphics, 256, 341, true,
                !pending && inside(mouseX, mouseY, 239, 324, 273, 359), !pending);
        if (amountFocused && isCaretVisible()) {
            int textWidth = Math.min(size(142), PhoneModernText.width(shown, size(27)));
            int caretX = x(150) + textWidth / 2 + size(3);
            graphics.fill(caretX, y(319), caretX + size(1), y(349), TEXT);
        }
        long coreAmount = requestedCoreAmount();
        boolean enough = balance != null && coreAmount > 0L && balance.coreValue >= coreAmount;
        String summary = unit == null || quantity.length() == 0 ? ""
                : quantity + " × " + safe(unit.name, PhoneSettingsStore.text("Birim", "Unit"));
        PhoneModernText.drawCenteredColored(graphics, fitToPixels(summary, size(8), size(178)),
                x(150), y(359), size(8), WARM);
        graphics.fill(x(34), y(383), x(266), y(384), DIVIDER);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Kullanılabilir", "Available"),
                x(34), y(392), size(8), TEXT_TERTIARY);
        boolean invalidAmount = quantity.length() > 0 && coreAmount <= 0L;
        if (invalidAmount) {
            drawRightFitted(graphics, PhoneSettingsStore.text("Tutar çok büyük", "Amount too large"),
                    266, 393, 138, 7, ERROR);
        } else if (coreAmount > 0L && !enough) {
            drawRightFitted(graphics, PhoneSettingsStore.text("Yetersiz bakiye", "Insufficient"),
                    266, 393, 126, 7, ERROR);
        } else if (balance != null) {
            drawRightFitted(graphics, safe(balance.formatted, "0"), 266, 393, 126, 7, PRIMARY);
        }

        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Gönderilecek", "To send"),
                x(20), y(426), size(8), TEXT_SECONDARY);
        drawRightFitted(graphics, summary.isBlank() ? "-" : summary, 280, 426, 160, 8,
                summary.isBlank() ? TEXT_TERTIARY : TEXT);

        var rules = PhoneBankClientData.state();
        if (rules.onlineRecipientRequired) {
            String hint = PhoneSettingsStore.text("Minimum", "Minimum") + " "
                    + PhoneCurrencyAmounts.dollars(rules.minimumTransfer) + " · "
                    + PhoneSettingsStore.text("Alıcı çevrimiçi olmalı", "Online recipient required");
            PhoneModernText.drawCenteredColored(graphics, fitToPixels(hint, size(7), size(264)),
                    x(150), y(447), size(7), WARM);
        }

        boolean ready = transferReady();
        boolean buttonHover = ready && inside(mouseX, mouseY, 18, 461, 282, 514);
        int buttonColor = ready ? (buttonHover ? 0xFF4DDB9E : PRIMARY)
                : pending ? PRIMARY_DARK : SURFACE_RAISED;
        drawRoundedRect(graphics, x(18), y(461), x(282), y(514), size(8), buttonColor);
        if (pending) {
            drawPendingIndicator(graphics, 93, 487);
            PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("İşleniyor", "Processing"),
                    x(112), y(477), size(9), TEXT);
        } else {
            drawSendGlyph(graphics, 93, 487, ready ? BG : TEXT_TERTIARY);
            PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Devam et", "Continue"),
                    x(113), y(477), size(9), ready ? BG : TEXT_TERTIARY);
        }
    }

    private void renderBanner(GuiGraphics graphics) {
        if (bannerCode.isBlank() || System.currentTimeMillis() >= bannerUntil) return;
        boolean success = "SENT".equals(bannerCode) || "RECEIVED".equals(bannerCode);
        boolean waiting = "RETRYING".equals(bannerCode) || "TIMEOUT".equals(bannerCode) || "PENDING".equals(bannerCode);
        int color = success ? 0xFF174C37 : waiting ? 0xFF55401E : 0xFF52292A;
        drawRoundedRect(graphics, x(18), y(528), x(282), y(563), size(8), color);
        fillCircle(graphics, x(38), y(545), size(10), success ? PRIMARY : waiting ? WARM : ERROR);
        drawToastGlyph(graphics, 38, 545, success, waiting);
        String detail = resultDetail();
        PhoneModernText.drawColored(graphics,
                fitToPixels(resultTitle(), size(8), size(208)), x(56), y(detail.isBlank() ? 539 : 533), size(8), TEXT);
        if (!detail.isBlank()) {
            PhoneModernText.drawColored(graphics, fitToPixels(detail, size(7), size(208)),
                    x(56), y(549), size(7), TEXT_SECONDARY);
        }
    }

    private void renderContactPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        drawOpaqueModalBackdrop(graphics, x(8), y(70), x(292), y(574), BG);
        beginOverlayLayer(graphics);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Alıcı seç", "Select recipient"),
                x(24), y(87), size(14), TEXT);
        drawRoundedRect(graphics, x(248), y(82), x(280), y(114), size(8),
                inside(mouseX, mouseY, 246, 80, 282, 116) ? SURFACE_HOVER : SURFACE_RAISED);
        drawCross(graphics, 264, 98, TEXT_SECONDARY);

        drawRoundedRect(graphics, x(18), y(124), x(282), y(163), size(8),
                contactSearchFocused ? PRIMARY : SURFACE_RAISED);
        drawRoundedRect(graphics, x(contactSearchFocused ? 20 : 18), y(contactSearchFocused ? 126 : 124),
                x(contactSearchFocused ? 280 : 282), y(contactSearchFocused ? 161 : 163), size(7), SURFACE_RAISED);
        drawSearchGlyph(graphics, 37, 143, TEXT_TERTIARY);
        String searchText = contactSearch.length() == 0 && !contactSearchFocused
                ? PhoneSettingsStore.text("Kişilerde ara", "Search contacts") : contactSearch.toString();
        PhoneModernText.drawColored(graphics, fitToPixels(searchText, size(8), size(205)),
                x(54), y(137), size(8), contactSearch.length() == 0 ? TEXT_TERTIARY : TEXT);
        if (contactSearchFocused && isCaretVisible()) {
            int width = PhoneModernText.width(contactSearch.toString(), size(8));
            int caretX = Math.min(x(263), x(54) + width + size(2));
            graphics.fill(caretX, y(136), caretX + size(1), y(150), TEXT);
        }

        List<PhoneStateData.PlayerEntry> contacts = visibleContacts();
        int maxScroll = Math.max(0, contacts.size() - PICKER_ROWS);
        contactScroll = Math.max(0, Math.min(contactScroll, maxScroll));
        if (contacts.isEmpty()) {
            String empty = contactSearch.length() == 0
                    ? PhoneSettingsStore.text("Henüz kişi yok", "No contacts yet")
                    : PhoneSettingsStore.text("Eşleşen kişi bulunamadı", "No matching contacts");
            drawPersonGlyph(graphics, 150, 271, TEXT_TERTIARY);
            PhoneModernText.drawCenteredColored(graphics, empty, x(150), y(300), size(9), TEXT_SECONDARY);
        }
        int end = Math.min(contacts.size(), contactScroll + PICKER_ROWS);
        for (int i = contactScroll; i < end; i++) {
            PhoneStateData.PlayerEntry contact = contacts.get(i);
            int row = i - contactScroll;
            int top = 174 + row * 68;
            boolean hovered = inside(mouseX, mouseY, 16, top, 284, top + 61);
            boolean selected = selectedContact != null && contact.uuid.equals(selectedContact.uuid);
            if (hovered || selected) {
                drawRoundedRect(graphics, x(16), y(top), x(284), y(top + 61), size(7),
                        selected ? PRIMARY_DARK : SURFACE_RAISED);
            }
            PhonePlayerHeadRenderer.draw(graphics, contact.uuid, x(25), y(top + 11), size(39));
            String fallback = PhoneSettingsStore.text("Oyuncu", "Player");
            PhoneModernText.drawColored(graphics, fitToPixels(safe(contact.name, fallback), size(9), size(150)),
                    x(76), y(top + 11), size(9), TEXT);
            String phone = PhoneClientData.formatPhoneNumber(contact.phoneNumber);
            String presence = contact.online ? PhoneSettingsStore.text("Çevrimiçi", "Online")
                    : PhoneSettingsStore.text("Çevrimdışı", "Offline");
            PhoneModernText.drawColored(graphics, fitToPixels(phone + "  ·  " + presence, size(7), size(155)),
                    x(76), y(top + 34), size(7), contact.online ? PRIMARY : TEXT_TERTIARY);
            if (selected) drawCheck(graphics, 260, top + 30, PRIMARY);
            else drawChevron(graphics, 261, top + 30, true, TEXT_TERTIARY);
            if (row < PICKER_ROWS - 1) graphics.fill(x(76), y(top + 61), x(270), y(top + 62), DIVIDER);
        }
        if (maxScroll > 0) {
            int trackTop = 174;
            int trackHeight = 332;
            int thumbHeight = Math.max(28, trackHeight * PICKER_ROWS / contacts.size());
            int thumbTop = trackTop + (trackHeight - thumbHeight) * contactScroll / maxScroll;
            drawRoundedRect(graphics, x(286), y(trackTop), x(289), y(trackTop + trackHeight), size(1), DIVIDER);
            drawRoundedRect(graphics, x(285), y(thumbTop), x(290), y(thumbTop + thumbHeight), size(2), TEXT_TERTIARY);
        }
        String footer = contacts.size() + " " + PhoneSettingsStore.text("kişi", "contacts");
        PhoneModernText.drawCenteredColored(graphics, footer, x(150), y(548), size(7), TEXT_TERTIARY);
        finishOverlayBackground(graphics);
        graphics.pose().popPose();
    }

    private void renderConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        graphics.flush();
        graphics.fill(x(8), y(70), x(292), y(574), 0xDE080A0B);
        graphics.flush();
        beginOverlayLayer(graphics);
        drawRoundedRect(graphics, x(10), y(257), x(290), y(574), size(8), SURFACE);
        drawRoundedRect(graphics, x(128), y(269), x(172), y(273), size(2), DIVIDER);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Transferi onayla", "Confirm transfer"),
                x(24), y(286), size(13), TEXT);
        if (selectedContact != null) {
            PhonePlayerHeadRenderer.draw(graphics, selectedContact.uuid, x(25), y(318), size(39));
        } else {
            fillCircle(graphics, x(44), y(337), size(19), SURFACE_RAISED);
            drawPersonGlyph(graphics, 44, 337, TEXT_SECONDARY);
        }
        String peer = selectedContact == null ? "-"
                : safe(selectedContact.name, PhoneSettingsStore.text("Oyuncu", "Player"));
        PhoneModernText.drawColored(graphics, fitToPixels(peer, size(10), size(176)),
                x(77), y(320), size(10), TEXT);
        String phone = selectedContact == null ? "" : PhoneClientData.formatPhoneNumber(selectedContact.phoneNumber);
        PhoneModernText.drawColored(graphics, fitToPixels(phone, size(7), size(176)),
                x(77), y(345), size(7), TEXT_TERTIARY);

        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Gönderilecek", "To send"),
                x(150), y(374), size(8), TEXT_TERTIARY);
        PhoneBankStateData.UnitEntry unit = selectedUnit();
        String unitFallback = PhoneSettingsStore.text("Birim", "Unit");
        drawCenteredFitted(graphics, quantity.length() == 0 ? "0" : quantity.toString(),
                150, 392, 236, 18, WARM);
        String denomination = unit == null ? "-" : safe(unit.name, unitFallback);
        PhoneModernText.drawCenteredColored(graphics, fitToPixels(denomination, size(8), size(220)),
                x(150), y(416), size(8), TEXT_SECONDARY);
        graphics.fill(x(24), y(435), x(276), y(436), DIVIDER);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Para birimi", "Currency"),
                x(25), y(448), size(8), TEXT_TERTIARY);
        PhoneBankStateData.BalanceEntry balance = selectedBalance();
        drawRightFitted(graphics, balance == null ? "-" : safe(balance.name, "-"),
                275, 448, 150, 8, TEXT);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Kupür", "Denomination"),
                x(25), y(474), size(8), TEXT_TERTIARY);
        drawRightFitted(graphics, unit == null ? "-" : safe(unit.name, unitFallback),
                275, 474, 150, 8, TEXT);

        boolean ready = transferReady();
        boolean cancelHover = inside(mouseX, mouseY, 20, 505, 119, 551);
        boolean confirmHover = ready && inside(mouseX, mouseY, 127, 505, 280, 551);
        drawRoundedRect(graphics, x(20), y(505), x(119), y(551), size(8),
                cancelHover ? SURFACE_HOVER : SURFACE_RAISED);
        drawRoundedRect(graphics, x(127), y(505), x(280), y(551), size(8),
                ready ? (confirmHover ? 0xFF4DDB9E : PRIMARY) : SURFACE_RAISED);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Vazgeç", "Cancel"),
                x(69), y(519), size(8), TEXT_SECONDARY);
        drawSendGlyph(graphics, 169, 528, ready ? BG : TEXT_TERTIARY);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Gönder", "Send"),
                x(219), y(519), size(8), ready ? BG : TEXT_TERTIARY);
        finishOverlayBackground(graphics);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (contactPicker) return handleContactPickerClick(mouseX, mouseY);
        if (confirmation) return handleConfirmationClick(mouseX, mouseY);
        if (handleSharedHomeClick(mouseX, mouseY, button)) return true;
        if (handleBackButton(mouseX, mouseY)) return true;
        if (inside(mouseX, mouseY, 248, 47, 282, 80)) {
            PhoneBankClientData.requestSync();
            return true;
        }

        if (inside(mouseX, mouseY, 18, 91, 150, 120)) {
            tab = Tab.ACCOUNT;
            amountFocused = false;
            return true;
        }
        if (inside(mouseX, mouseY, 150, 91, 282, 120)) {
            tab = Tab.TRANSFER;
            return true;
        }
        if (!PhoneBankClientData.available()) {
            if (inside(mouseX, mouseY, 52, 313, 248, 359)) PhoneBankClientData.requestSync();
            return true;
        }
        if (tab == Tab.ACCOUNT) {
            if (PhoneBankClientData.balances().size() > 1 && inside(mouseX, mouseY, 214, 219, 246, 254)) {
                cycleBalance(-1);
                return true;
            }
            if (PhoneBankClientData.balances().size() > 1 && inside(mouseX, mouseY, 247, 219, 280, 254)) {
                cycleBalance(1);
                return true;
            }
            if (inside(mouseX, mouseY, 18, 275, 282, 323)) {
                tab = Tab.TRANSFER;
                return true;
            }
            List<PhoneBankStateData.BalanceEntry> balances = PhoneBankClientData.balances();
            int start = visibleBalanceStart(balances.size(), selectedBalanceIndex());
            for (int row = 0; row < Math.min(3, balances.size()); row++) {
                int i = start + row;
                int top = 363 + row * 52;
                if (inside(mouseX, mouseY, 18, top, 282, top + 52)) {
                    selectBalance(balances.get(i), true);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (!pendingRequestId.isBlank()) return true;
        if (inside(mouseX, mouseY, 18, 132, 282, 188)) {
            contactPicker = true;
            contactSearch.setLength(0);
            contactScroll = 0;
            contactSearchFocused = false;
            amountFocused = false;
            return true;
        }
        if (inside(mouseX, mouseY, 18, 201, 146, 259)) {
            if (PhoneBankClientData.balances().size() > 1) {
                if (mouseX <= x(54)) cycleBalance(-1);
                else if (mouseX >= x(110)) cycleBalance(1);
            }
            return true;
        }
        if (inside(mouseX, mouseY, 154, 201, 282, 259)) {
            PhoneBankStateData.BalanceEntry balance = selectedBalance();
            int unitCount = balance == null || balance.units == null ? 0 : balance.units.size();
            if (unitCount > 1) {
                if (mouseX <= x(190)) cycleUnit(-1);
                else if (mouseX >= x(246)) cycleUnit(1);
            }
            return true;
        }
        if (inside(mouseX, mouseY, 220, 280, 269, 309)) {
            setMaximumQuantity();
            return true;
        }
        if (inside(mouseX, mouseY, 27, 324, 61, 359)) {
            adjustQuantity(-1);
            amountFocused = true;
            return true;
        }
        if (inside(mouseX, mouseY, 239, 324, 273, 359)) {
            adjustQuantity(1);
            amountFocused = true;
            return true;
        }
        if (inside(mouseX, mouseY, 18, 271, 282, 410)) {
            amountFocused = true;
            return true;
        }
        if (inside(mouseX, mouseY, 18, 461, 282, 514) && transferReady()) {
            amountFocused = false;
            confirmation = true;
            return true;
        }
        amountFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleContactPickerClick(double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, 246, 80, 282, 116)) {
            contactPicker = false;
            contactSearchFocused = false;
            return true;
        }
        if (inside(mouseX, mouseY, 18, 124, 282, 163)) {
            contactSearchFocused = true;
            return true;
        }
        contactSearchFocused = false;
        List<PhoneStateData.PlayerEntry> contacts = visibleContacts();
        int end = Math.min(contacts.size(), contactScroll + PICKER_ROWS);
        for (int i = contactScroll; i < end; i++) {
            int top = 174 + (i - contactScroll) * 68;
            if (inside(mouseX, mouseY, 16, top, 284, top + 61)) {
                PhoneStateData.PlayerEntry chosen = contacts.get(i);
                PhoneBankClientData.TransferOperation operation = PhoneBankClientData.operation();
                if (operation != null && !operation.awaitingResponse()
                        && !operation.targetUuid().equals(chosen.uuid)) {
                    clearRetry();
                }
                selectedContact = chosen;
                contactPicker = false;
                contactSearchFocused = false;
                return true;
            }
        }
        return true;
    }

    private boolean handleConfirmationClick(double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, 20, 505, 119, 551)) {
            confirmation = false;
            return true;
        }
        if (inside(mouseX, mouseY, 127, 505, 280, 551) && transferReady()) {
            PhoneBankStateData.BalanceEntry balance = selectedBalance();
            long amount = requestedCoreAmount();
            String targetUuid = selectedContact.uuid;
            boolean sameRequest = !retryRequestId.isBlank()
                    && retryTargetUuid.equals(targetUuid)
                    && retryCurrencyId.equals(balance.id)
                    && retryCoreAmount == amount;
            String requestId = sameRequest ? retryRequestId : "";
            PhoneBankStateData.UnitEntry unit = selectedUnit();
            pendingRequestId = PhoneBankClientData.transfer(
                    targetUuid, balance.id, amount, requestId,
                    unit == null ? 1L : unit.coreValue, quantity.toString()
            );
            if (!pendingRequestId.isBlank()) {
                retryRequestId = pendingRequestId;
                retryTargetUuid = targetUuid;
                retryCurrencyId = balance.id;
                retryCoreAmount = amount;
                PhoneBankClientData.TransferOperation operation = PhoneBankClientData.operation();
                retryAttempts = operation == null ? 0 : operation.retryAttempts();
                pendingSince = operation == null ? System.currentTimeMillis() : operation.lastSentAt();
                confirmation = false;
            }
            return true;
        }
        return true;
    }

    private List<PhoneStateData.PlayerEntry> visibleContacts() {
        List<PhoneStateData.PlayerEntry> contacts = new ArrayList<>(PhoneClientData.sortedContacts());
        String query = contactSearch.toString().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return contacts;
        String digits = PhoneClientData.normalizePhoneNumber(query);
        contacts.removeIf(contact -> {
            String name = safe(contact.name, "").toLowerCase(Locale.ROOT);
            String phone = PhoneClientData.normalizePhoneNumber(contact.phoneNumber);
            return !name.contains(query) && (digits.isEmpty() || !phone.contains(digits));
        });
        return contacts;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (contactPicker && delta != 0.0D) {
            int max = Math.max(0, visibleContacts().size() - PICKER_ROWS);
            contactScroll = delta < 0.0D ? Math.min(max, contactScroll + 1) : Math.max(0, contactScroll - 1);
            return true;
        }
        if (tab == Tab.ACCOUNT && inside(mouseX, mouseY, 18, 343, 282, 519)
                && delta != 0.0D && PhoneBankClientData.balances().size() > 1) {
            cycleBalance(delta < 0.0D ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (contactPicker && contactSearchFocused && character >= 32 && character != 127
                && contactSearch.length() < 24) {
            contactSearch.append(character);
            contactScroll = 0;
            return true;
        }
        if (amountFocused && character >= '0' && character <= '9'
                && quantity.length() < MAX_QUANTITY_DIGITS) {
            appendQuantityDigit(character);
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (contactPicker) {
                contactPicker = false;
                contactSearchFocused = false;
                return true;
            }
            if (confirmation) {
                confirmation = false;
                return true;
            }
            PhoneNavigationState.backOrHome(minecraft);
            return true;
        }
        if (contactPicker && contactSearchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (contactSearch.length() > 0) contactSearch.deleteCharAt(contactSearch.length() - 1);
            contactScroll = 0;
            return true;
        }
        if (contactPicker && contactSearchFocused && keyCode == GLFW.GLFW_KEY_V
                && hasControlDown() && minecraft != null) {
            String clipboard = minecraft.keyboardHandler.getClipboard();
            for (int i = 0; i < clipboard.length() && contactSearch.length() < 24; i++) {
                char c = clipboard.charAt(i);
                if (c >= 32 && c != 127) contactSearch.append(c);
            }
            contactScroll = 0;
            return true;
        }
        if (amountFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (quantity.length() > 0) quantity.deleteCharAt(quantity.length() - 1);
            return true;
        }
        if (amountFocused && keyCode == GLFW.GLFW_KEY_V && hasControlDown() && minecraft != null) {
            String clipboard = minecraft.keyboardHandler.getClipboard();
            for (int i = 0; i < clipboard.length() && quantity.length() < MAX_QUANTITY_DIGITS; i++) {
                char c = clipboard.charAt(i);
                if (c >= '0' && c <= '9') appendQuantityDigit(c);
            }
            return true;
        }
        if (amountFocused && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && transferReady()) {
            amountFocused = false;
            confirmation = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void cycleBalance(int direction) {
        List<PhoneBankStateData.BalanceEntry> balances = PhoneBankClientData.balances();
        int size = balances.size();
        if (size <= 0) return;
        int next = Math.floorMod(selectedBalanceIndex() + direction, size);
        selectBalance(balances.get(next), true);
    }

    private void cycleUnit(int direction) {
        PhoneBankStateData.BalanceEntry balance = selectedBalance();
        int size = balance == null || balance.units == null ? 0 : balance.units.size();
        if (size <= 0) return;
        int current = selectedUnitIndex(balance);
        PhoneBankStateData.UnitEntry unit = balance.units.get(Math.floorMod(current + direction, size));
        selectedUnitCore = unit.coreValue;
        quantity.setLength(0);
        confirmation = false;
        clearRetry();
    }

    private void adjustQuantity(int direction) {
        long current = 0L;
        if (quantity.length() > 0) {
            try {
                current = Long.parseLong(quantity.toString());
            } catch (NumberFormatException ignored) {
                showBanner("AMOUNT_TOO_LARGE", "", "");
                return;
            }
        }
        long next;
        try {
            next = Math.addExact(current, direction);
        } catch (ArithmeticException ignored) {
            next = direction > 0 ? Long.MAX_VALUE : 0L;
        }
        setQuantity(Math.min(maximumQuantityByCore(), Math.max(0L, next)));
    }

    private void appendQuantityDigit(char digit) {
        if (digit < '0' || digit > '9' || quantity.length() >= MAX_QUANTITY_DIGITS) return;
        if (quantity.length() == 0 && digit == '0') return;
        String candidate = quantity.toString() + digit;
        try {
            long parsed = Long.parseLong(candidate);
            if (parsed > maximumQuantityByCore()) {
                showBanner("AMOUNT_TOO_LARGE", "", "");
                return;
            }
            quantity.append(digit);
        } catch (NumberFormatException ignored) {
            showBanner("AMOUNT_TOO_LARGE", "", "");
        }
    }

    private long maximumQuantityByCore() {
        PhoneBankStateData.UnitEntry unit = selectedUnit();
        if (unit == null || unit.coreValue <= 0L) return Long.MAX_VALUE;
        return Long.MAX_VALUE / unit.coreValue;
    }

    private void setMaximumQuantity() {
        PhoneBankStateData.BalanceEntry balance = selectedBalance();
        PhoneBankStateData.UnitEntry unit = selectedUnit();
        if (balance == null || unit == null || unit.coreValue <= 0L) return;
        setQuantity(Math.max(0L, balance.coreValue / unit.coreValue));
        amountFocused = true;
    }

    private void setQuantity(long value) {
        quantity.setLength(0);
        long bounded = Math.min(maximumQuantityByCore(), Math.max(0L, value));
        if (bounded > 0L) quantity.append(bounded);
        confirmation = false;
    }

    private void clampSelections() {
        List<PhoneBankStateData.BalanceEntry> balances = PhoneBankClientData.balances();
        if (balances.isEmpty()) {
            if ("LOADING".equals(PhoneBankClientData.status())) return;
            if (!selectedBalanceId.isBlank()) invalidateSelection();
            selectedBalanceId = "";
            selectedUnitCore = 0L;
            return;
        }
        PhoneBankStateData.BalanceEntry balance = findBalance(selectedBalanceId);
        if (balance == null) {
            selectBalance(balances.get(0), !selectedBalanceId.isBlank());
            return;
        }
        if (balance.units == null || balance.units.isEmpty()) {
            if (selectedUnitCore != 0L) invalidateSelection();
            selectedUnitCore = 0L;
            return;
        }
        boolean unitStillExists = balance.units.stream().anyMatch(unit -> unit.coreValue == selectedUnitCore);
        if (!unitStillExists) {
            if (selectedUnitCore != 0L) invalidateSelection();
            selectedUnitCore = balance.units.get(0).coreValue;
        }
    }

    private PhoneBankStateData.BalanceEntry selectedBalance() {
        List<PhoneBankStateData.BalanceEntry> balances = PhoneBankClientData.balances();
        if (balances.isEmpty()) return null;
        PhoneBankStateData.BalanceEntry selected = findBalance(selectedBalanceId);
        return selected == null ? balances.get(0) : selected;
    }

    private PhoneBankStateData.UnitEntry selectedUnit() {
        PhoneBankStateData.BalanceEntry balance = selectedBalance();
        if (balance == null || balance.units == null || balance.units.isEmpty()) return null;
        for (PhoneBankStateData.UnitEntry unit : balance.units) {
            if (unit.coreValue == selectedUnitCore) return unit;
        }
        return balance.units.get(0);
    }

    private PhoneBankStateData.BalanceEntry findBalance(String id) {
        if (id == null || id.isBlank()) return null;
        for (PhoneBankStateData.BalanceEntry balance : PhoneBankClientData.balances()) {
            if (id.equals(balance.id)) return balance;
        }
        return null;
    }

    private int selectedBalanceIndex() {
        List<PhoneBankStateData.BalanceEntry> balances = PhoneBankClientData.balances();
        for (int i = 0; i < balances.size(); i++) {
            if (selectedBalanceId.equals(balances.get(i).id)) return i;
        }
        return 0;
    }

    private static int visibleBalanceStart(int size, int selectedIndex) {
        if (size <= 3) return 0;
        return Math.max(0, Math.min(selectedIndex - 1, size - 3));
    }

    private int selectedUnitIndex(PhoneBankStateData.BalanceEntry balance) {
        if (balance == null || balance.units == null) return 0;
        for (int i = 0; i < balance.units.size(); i++) {
            if (balance.units.get(i).coreValue == selectedUnitCore) return i;
        }
        return 0;
    }

    private void selectBalance(PhoneBankStateData.BalanceEntry balance, boolean invalidateAmount) {
        if (balance == null) return;
        selectedBalanceId = safe(balance.id, "");
        selectedUnitCore = balance.units == null || balance.units.isEmpty() ? 0L : balance.units.get(0).coreValue;
        if (invalidateAmount) invalidateSelection();
    }

    private void invalidateSelection() {
        quantity.setLength(0);
        amountFocused = false;
        confirmation = false;
        if (pendingRequestId.isBlank()) clearRetry();
    }

    private long requestedCoreAmount() {
        PhoneBankStateData.UnitEntry unit = selectedUnit();
        if (unit == null || quantity.length() == 0) return 0L;
        try {
            long count = Long.parseLong(quantity.toString());
            return Math.multiplyExact(count, unit.coreValue);
        } catch (NumberFormatException | ArithmeticException exception) {
            return 0L;
        }
    }

    private boolean transferReady() {
        PhoneBankStateData.BalanceEntry balance = selectedBalance();
        long amount = requestedCoreAmount();
        return pendingRequestId.isBlank() && selectedContact != null && balance != null
                && amount >= Math.max(1, PhoneBankClientData.state().minimumTransfer)
                && balance.coreValue >= amount
                && (!PhoneBankClientData.state().onlineRecipientRequired || selectedContact.online);
    }

    private void showBanner(String code, String peer, String amount) {
        bannerCode = safe(code, "FAILED");
        bannerPeer = safe(peer, "Player");
        bannerAmount = safe(amount, "");
        bannerUntil = System.currentTimeMillis() + 5_000L;
    }

    private String resultText() {
        return switch (bannerCode) {
            case "SENT" -> bannerAmount
                    + PhoneSettingsStore.text(" · ", " · Sent to ")
                    + bannerPeer;
            case "RECEIVED" -> bannerPeer
                    + PhoneSettingsStore.text(" sana ", " sent you ")
                    + bannerAmount;
            case "INVALID_TARGET" -> PhoneSettingsStore.text("Alıcı bulunamadı", "Recipient not found");
            case "NOT_CONTACT" -> PhoneSettingsStore.text("Alıcı kişilerinde değil", "Recipient is not a contact");
            case "SELF_TRANSFER" -> PhoneSettingsStore.text("Kendine transfer yapamazsın", "You cannot transfer to yourself");
            case "INVALID_AMOUNT" -> PhoneSettingsStore.text("Geçersiz tutar", "Invalid amount");
            case "AMOUNT_TOO_LARGE" -> PhoneSettingsStore.text("Tutar çok büyük", "Amount is too large");
            case "INSUFFICIENT_FUNDS" -> PhoneSettingsStore.text("Yetersiz bakiye", "Insufficient balance");
            case "ACCOUNT_UNAVAILABLE" -> PhoneSettingsStore.text("Banka hesabına ulaşılamadı", "Bank account unavailable");
            case "MOD_MISSING" -> PhoneSettingsStore.text("Banka hesabına ulaşılamadı", "Bank account unavailable");
            case "TARGET_OFFLINE" -> PhoneSettingsStore.text("Alıcı çevrimiçi olmalı", "Online recipient required");
            case "TOO_FAST" -> PhoneSettingsStore.text("Biraz bekleyip tekrar dene", "Wait before trying again");
            case "DUPLICATE" -> PhoneSettingsStore.text("Bu işlem zaten işlendi", "This transfer was already processed");
            case "RETRYING" -> PhoneSettingsStore.text("İşlem sonucu doğrulanıyor", "Verifying transfer result");
            case "TIMEOUT" -> PhoneSettingsStore.text("Sunucu yanıt vermedi", "The server did not respond");
            case "PENDING" -> PhoneSettingsStore.text("Tekrar göndermeden bakiyeni kontrol et", "Check balance before resending");
            default -> PhoneSettingsStore.text("Transfer tamamlanamadı", "Transfer could not be completed");
        };
    }

    private String resultTitle() {
        return switch (bannerCode) {
            case "SENT" -> PhoneSettingsStore.text("Transfer tamamlandı", "Transfer complete");
            case "RECEIVED" -> PhoneSettingsStore.text("Para alındı", "Money received");
            default -> resultText();
        };
    }

    private String resultDetail() {
        String amount = bannerAmount == null ? "" : bannerAmount.trim();
        String peer = bannerPeer == null ? "" : bannerPeer.trim();
        if ("SENT".equals(bannerCode)) {
            if (amount.isEmpty()) return peer;
            if (peer.isEmpty()) return amount;
            return amount + " · " + peer;
        }
        if ("RECEIVED".equals(bannerCode)) {
            if (peer.isEmpty()) return amount;
            if (amount.isEmpty()) return peer;
            return peer + " · " + amount;
        }
        return "";
    }

    private void clearRetry() {
        retryRequestId = "";
        retryTargetUuid = "";
        retryCurrencyId = "";
        retryCoreAmount = 0L;
        retryAttempts = 0;
        PhoneBankClientData.abandonTransfer();
    }

    private void restoreTransferOperation() {
        PhoneBankClientData.TransferOperation operation = PhoneBankClientData.operation();
        if (operation == null) return;
        if (operation.completed()) {
            showBanner(operation.resultCode(), operation.resultPeerName(), operation.resultAmount());
            if ("SENT".equals(operation.resultCode())) quantity.setLength(0);
            PhoneBankClientData.acknowledgeTransfer(operation.requestId());
            pendingRequestId = "";
            pendingSince = 0L;
            confirmation = false;
            clearRetry();
            return;
        }
        if (retryRequestId.isBlank()) {
            retryRequestId = operation.requestId();
            retryTargetUuid = operation.targetUuid();
            retryCurrencyId = operation.currencyId();
            retryCoreAmount = operation.coreAmount();
            retryAttempts = operation.retryAttempts();
            selectedBalanceId = operation.currencyId();
            selectedUnitCore = operation.unitCoreValue();
            quantity.setLength(0);
            quantity.append(operation.quantity());
            selectedContact = PhoneClientData.contactByUuid(operation.targetUuid());
            if (operation.awaitingResponse()) tab = Tab.TRANSFER;
        }
        if (operation.awaitingResponse() && pendingRequestId.isBlank()) {
            pendingRequestId = operation.requestId();
            pendingSince = operation.lastSentAt();
            retryAttempts = operation.retryAttempts();
        }
        if (operation.awaitingResponse()
                && (selectedContact == null || !operation.targetUuid().equals(selectedContact.uuid))) {
            selectedContact = PhoneClientData.contactByUuid(operation.targetUuid());
        }
    }

    private void drawSelector(GuiGraphics graphics, int left, int right, String label, String value,
                              int optionCount, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && optionCount > 1
                && (inside(mouseX, mouseY, left, 201, left + 36, 259)
                || inside(mouseX, mouseY, right - 36, 201, right, 259));
        drawRoundedRect(graphics, x(left), y(201), x(right), y(259), size(8),
                hovered ? SURFACE_HOVER : enabled ? SURFACE : 0xFF141718);
        PhoneModernText.drawColored(graphics, fitToPixels(label, size(8), size(right - left - 24)),
                x(left + 12), y(210), size(8), TEXT_TERTIARY);
        int center = (left + right) / 2;
        int valueWidth = optionCount > 1 ? right - left - 58 : right - left - 24;
        drawCenteredFitted(graphics, value, center, 232, valueWidth, 9, TEXT);
        if (enabled && optionCount > 1) {
            drawChevron(graphics, left + 17, 235, false, TEXT_TERTIARY);
            drawChevron(graphics, right - 17, 235, true, TEXT_TERTIARY);
        }
    }

    private void drawCircleChevronButton(GuiGraphics graphics, int centerX, int centerY,
                                         boolean right, boolean hovered) {
        fillCircle(graphics, x(centerX), y(centerY), size(14), hovered ? SURFACE_HOVER : SURFACE_RAISED);
        drawChevron(graphics, centerX, centerY, right, hovered ? TEXT : TEXT_SECONDARY);
    }

    private void drawStepper(GuiGraphics graphics, int centerX, int centerY,
                             boolean plus, boolean hovered, boolean enabled) {
        fillCircle(graphics, x(centerX), y(centerY), size(17),
                hovered ? SURFACE_HOVER : enabled ? SURFACE_RAISED : SURFACE);
        int stroke = Math.max(1, size(2));
        graphics.fill(x(centerX - 6), y(centerY) - stroke / 2,
                x(centerX + 6), y(centerY) - stroke / 2 + stroke,
                enabled ? TEXT_SECONDARY : TEXT_TERTIARY);
        if (plus) {
            graphics.fill(x(centerX) - stroke / 2, y(centerY - 6),
                    x(centerX) - stroke / 2 + stroke, y(centerY + 6),
                    enabled ? TEXT_SECONDARY : TEXT_TERTIARY);
        }
    }

    private void drawPendingIndicator(GuiGraphics graphics, int centerX, int centerY) {
        int phase = (int) ((System.currentTimeMillis() / 180L) % 3L);
        for (int i = 0; i < 3; i++) {
            int height = i == phase ? 12 : 7;
            drawRoundedRect(graphics, x(centerX - 8 + i * 8), y(centerY - height / 2),
                    x(centerX - 4 + i * 8), y(centerY + height / 2), size(2),
                    i == phase ? TEXT : TEXT_SECONDARY);
        }
    }

    private void drawToastGlyph(GuiGraphics graphics, int centerX, int centerY,
                                boolean success, boolean waiting) {
        if (success) {
            drawCheck(graphics, centerX, centerY, BG);
        } else if (waiting) {
            for (int i = -1; i <= 1; i++) {
                fillCircle(graphics, x(centerX + i * 5), y(centerY), size(1), BG);
            }
        } else {
            drawCross(graphics, centerX, centerY, BG);
        }
    }

    private void drawPersonGlyph(GuiGraphics graphics, int centerX, int centerY, int color) {
        fillCircle(graphics, x(centerX), y(centerY - 6), size(5), color);
        drawRoundedRect(graphics, x(centerX - 9), y(centerY + 1), x(centerX + 9), y(centerY + 10),
                size(5), color);
    }

    private void drawSearchGlyph(GuiGraphics graphics, int centerX, int centerY, int color) {
        int dot = Math.max(1, size(2));
        for (int degrees = 0; degrees < 360; degrees += 24) {
            double angle = Math.toRadians(degrees);
            int px = x(centerX) + (int) Math.round(Math.cos(angle) * size(6));
            int py = y(centerY) + (int) Math.round(Math.sin(angle) * size(6));
            graphics.fill(px, py, px + dot, py + dot, color);
        }
        for (int step = 0; step <= 6; step++) {
            int px = x(centerX + 5 + step);
            int py = y(centerY + 5 + step);
            graphics.fill(px, py, px + dot, py + dot, color);
        }
    }

    private void drawRefreshGlyph(GuiGraphics graphics, int centerX, int centerY, int color) {
        int dot = Math.max(1, size(2));
        for (int degrees = 35; degrees <= 315; degrees += 16) {
            double angle = Math.toRadians(degrees);
            int px = x(centerX) + (int) Math.round(Math.cos(angle) * size(8));
            int py = y(centerY) + (int) Math.round(Math.sin(angle) * size(8));
            graphics.fill(px, py, px + dot, py + dot, color);
        }
        graphics.fill(x(centerX + 5), y(centerY - 9), x(centerX + 11), y(centerY - 7), color);
        graphics.fill(x(centerX + 9), y(centerY - 9), x(centerX + 11), y(centerY - 3), color);
    }

    private void drawSendGlyph(GuiGraphics graphics, int centerX, int centerY, int color) {
        int stroke = Math.max(1, size(2));
        for (int step = -6; step <= 6; step++) {
            int px = x(centerX + step);
            int py = y(centerY - step);
            graphics.fill(px, py, px + stroke, py + stroke, color);
        }
        graphics.fill(x(centerX + 1), y(centerY - 7), x(centerX + 8), y(centerY - 7) + stroke, color);
        graphics.fill(x(centerX + 6), y(centerY - 7), x(centerX + 8), y(centerY), color);
    }

    private void drawChevron(GuiGraphics graphics, int centerX, int centerY, boolean right, int color) {
        int stroke = Math.max(1, size(2));
        for (int step = 0; step <= 5; step++) {
            int px = x(centerX + (right ? step - 2 : 2 - step));
            int upper = y(centerY - 5 + step);
            int lower = y(centerY + 5 - step);
            graphics.fill(px, upper, px + stroke, upper + stroke, color);
            graphics.fill(px, lower, px + stroke, lower + stroke, color);
        }
    }

    private void drawCross(GuiGraphics graphics, int centerX, int centerY, int color) {
        int stroke = Math.max(1, size(2));
        for (int step = -5; step <= 5; step++) {
            int px = x(centerX + step);
            int pyA = y(centerY + step);
            int pyB = y(centerY - step);
            graphics.fill(px, pyA, px + stroke, pyA + stroke, color);
            graphics.fill(px, pyB, px + stroke, pyB + stroke, color);
        }
    }

    private void drawCheck(GuiGraphics graphics, int centerX, int centerY, int color) {
        int stroke = Math.max(1, size(2));
        for (int step = 0; step <= 4; step++) {
            int px = x(centerX - 6 + step);
            int py = y(centerY + step);
            graphics.fill(px, py, px + stroke, py + stroke, color);
        }
        for (int step = 0; step <= 8; step++) {
            int px = x(centerX - 2 + step);
            int py = y(centerY + 4 - step);
            graphics.fill(px, py, px + stroke, py + stroke, color);
        }
    }

    private void drawFitted(GuiGraphics graphics, String text, int designX, int designY,
                            int maxWidth, int preferredSize, int color) {
        int fontSize = preferredSize;
        while (fontSize > 9 && PhoneModernText.width(text, size(fontSize)) > size(maxWidth)) fontSize--;
        PhoneModernText.drawColored(graphics, fitToPixels(text, size(fontSize), size(maxWidth)),
                x(designX), y(designY), size(fontSize), color);
    }

    private void drawRightFitted(GuiGraphics graphics, String text, int rightX, int designY,
                                 int maxWidth, int fontSize, int color) {
        String fitted = fitToPixels(text, size(fontSize), size(maxWidth));
        int width = PhoneModernText.width(fitted, size(fontSize));
        PhoneModernText.drawColored(graphics, fitted, x(rightX) - width, y(designY), size(fontSize), color);
    }

    private void drawCenteredFitted(GuiGraphics graphics, String text, int centerX, int designY,
                                    int maxWidth, int preferredSize, int color) {
        int fontSize = preferredSize;
        while (fontSize > 8 && PhoneModernText.width(safe(text, ""), size(fontSize)) > size(maxWidth)) fontSize--;
        String fitted = fitToPixels(text, size(fontSize), size(maxWidth));
        PhoneModernText.drawCenteredColored(graphics, fitted, x(centerX), y(designY), size(fontSize), color);
    }

    private String fitToPixels(String text, int fontSize, int maxWidth) {
        String value = safe(text, "");
        if (PhoneModernText.width(value, fontSize) <= maxWidth) return value;
        while (value.length() > 1 && PhoneModernText.width(value + "…", fontSize) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "…";
    }

    private static String currencyInitial(PhoneBankStateData.BalanceEntry entry) {
        String name = safe(entry == null ? "" : entry.name, "C").trim();
        return name.substring(0, 1).toUpperCase();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private enum Tab {
        ACCOUNT,
        TRANSFER
    }

}

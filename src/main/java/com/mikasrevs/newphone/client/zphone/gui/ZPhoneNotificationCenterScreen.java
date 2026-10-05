package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneSocialClientData;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhonePlayerHeadRenderer;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.settings.PhonePrivacyStore;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.PhoneSocialStateData;
import com.mikasrevs.newphone.network.PhoneStateData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Forge 1.20.1 notification center aggregating Messages, LUMI, X and Tinder. */
public final class ZPhoneNotificationCenterScreen extends AbstractZPhoneScreen {
    private int scrollOffset;

    public ZPhoneNotificationCenterScreen() {
        super(Component.literal(PhoneSettingsStore.text("Bildirimler", "Notifications")));
        PhoneClientData.requestSync();
        PhoneSocialClientData.requestSync();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawWallpaper(graphics);
        drawRoundedRect(graphics, x(3), y(40), x(297), y(606), size(22), 0xE7090C12);
        drawStatusBar(graphics);
        drawHeader(graphics);
        drawCards(graphics, mouseX, mouseY);
        drawHomeIndicator(graphics);
        drawFrame(graphics);
    }

    private void drawHeader(GuiGraphics graphics) {
        int unread = totalAttentionCount();
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("Bildirimler", "Notifications"), x(22), y(58), size(18), 0xFFFFFFFF);
        if (unread > 0) {
            drawRoundedRect(graphics, x(169), y(59), x(204), y(79), size(10), PhoneSettingsStore.accentColor());
            PhoneModernText.drawCenteredColored(graphics, unread > 99 ? "99+" : Integer.toString(unread), x(186), y(64), size(7), 0xFFFFFFFF);
        }
        drawRoundedRect(graphics, x(211), y(57), x(279), y(83), size(12), 0xC91B222D);
        PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Tümünü oku", "Read all"), x(245), y(65), size(7), 0xFF8CC7FF);
        PhoneModernText.drawColored(graphics, PhoneSettingsStore.text("SON ETKİNLİK", "RECENT ACTIVITY"), x(23), y(101), size(7), 0xFF8793A2);
        if (PhoneSettingsStore.doNotDisturb()) {
            PhoneModernText.drawColored(graphics, "DND", x(252), y(101), size(6), 0xFFFFC35A);
        }
    }

    private void drawCards(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Card> cards = cards();
        if (cards.isEmpty()) {
            fillCircle(graphics, x(150), y(230), size(26), 0xFF19212C);
            PhoneModernText.drawCenteredColored(graphics, "✓", x(150), y(218), size(22), 0xFF74C99B);
            PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Hepsini gördün", "You're all caught up"), x(150), y(275), size(11), 0xFFFFFFFF);
            PhoneModernText.drawCenteredColored(graphics, PhoneSettingsStore.text("Yeni etkinlik burada görünür", "New activity will appear here"), x(150), y(296), size(7), 0xFF7D8793);
            return;
        }

        int maxOffset = Math.max(0, cards.size() - 5);
        scrollOffset = Math.min(scrollOffset, maxOffset);
        int count = Math.min(5, cards.size() - scrollOffset);
        for (int i = 0; i < count; i++) {
            Card card = cards.get(scrollOffset + i);
            int top = 116 + i * 86;
            drawCard(graphics, card, top, mouseX, mouseY);
        }
    }

    private void drawCard(GuiGraphics graphics, Card card, int top, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, 18, top, 282, top + 78);
        int bg = card.unread ? 0xE71A2635 : 0xC8131821;
        if (hover) bg = card.unread ? 0xF0243447 : 0xE41B222D;
        drawRoundedRect(graphics, x(19), y(top + 2), x(283), y(top + 80), size(14), 0x26000000);
        drawRoundedRect(graphics, x(18), y(top), x(282), y(top + 78), size(14), bg);

        int accent = switch (card.kind) {
            case "MESSAGES", "MARKETPLACE", "MARKETPLACE_TRADE" -> 0xFFFFD451;
            case "BLOCKGRAM", "BLOCKGRAM_DM" -> 0xFFFF4C83;
            case "BLOCKPOST" -> 0xFF59A9FF;
            case "TINDER", "TINDER_DM" -> 0xFFFF5369;
            default -> 0xFF8B95A3;
        };
        fillCircle(graphics, x(46), y(top + 31), size(19), 0xFF26303D);
        if (card.actorUuid != null && !card.actorUuid.isBlank()) {
            var group=com.mikasrevs.newphone.client.data.PhoneGroupClient.group(card.actorUuid);
            if(group!=null)PhoneGroupVisuals.avatar(this,graphics,group,46,top+31,38);else PhonePlayerHeadRenderer.draw(graphics, card.actorUuid, x(33), y(top + 18), size(26));
        }
        PhoneApp sourceApp = switch (card.kind) {
            case "MESSAGES" -> PhoneApp.MESSAGES;
            case "MARKETPLACE", "MARKETPLACE_TRADE" -> PhoneApp.MARKETPLACE;
            case "BLOCKGRAM", "BLOCKGRAM_DM" -> PhoneApp.BLOCKGRAM;
            case "BLOCKPOST" -> PhoneApp.BLOCKPOST;
            case "TINDER", "TINDER_DM" -> PhoneApp.BROWSER;
            default -> null;
        };
        if (sourceApp != null) {
            int appIconSize = size(16);
            MPhoneAppIconRenderer.draw(graphics, sourceApp,
                    x(53), y(top + 39), appIconSize, false);
        }

        PhoneModernText.drawColored(graphics, card.title, x(76), y(top + 11), size(9), 0xFFFFFFFF);
        PhoneModernText.drawColored(graphics, (!ZPhoneClient.isSessionUnlocked() && !PhonePrivacyStore.previews()) ? PhoneSettingsStore.text("Yeni bildirim", "New notification") : trim(card.body, 34), x(76), y(top + 31), size(7), 0xFFB3BBC6);
        PhoneModernText.drawColored(graphics, relativeTime(card.createdAt), x(76), y(top + 54), size(6), 0xFF727D89);
        PhoneModernText.drawColored(graphics, card.kindLabel, x(229), y(top + 12), size(6), accent);
        if (card.unread) fillCircle(graphics, x(268), y(top + 58), size(4), 0xFF0A84FF);
    }

    private List<Card> cards() {
        List<Card> result = new ArrayList<>();
        var trade=com.mikasrevs.newphone.marketplace.trade.TradeClient.state();
        if(com.mikasrevs.newphone.marketplace.trade.TradeClient.unread()>0)
            result.add(new Card("MARKETPLACE_TRADE","Marketplace",trade.other,
                    PhoneSettingsStore.text("Takas isteği","Trade request"),trade.otherName+" · "+trade.title,
                    trade.deadline-120_000,true,trade.id));
        for(var n:com.mikasrevs.newphone.marketplace.client.MarketplaceClient.state().notifications)
            result.add(new Card("MARKETPLACE","Marketplace",n.actorUuid(),n.title(),n.body(),n.time(),!n.read(),n.listingId()));
        for (PhoneStateData.PlayerEntry contact : PhoneClientData.sortedMessagePeers()) {
            int unread = PhoneClientData.unreadCount(contact.uuid);
            if (unread <= 0) continue;
            PhoneStateData.MessageEntry last = PhoneClientData.lastMessage(contact.uuid);
            long when = last == null ? System.currentTimeMillis() : last.timestamp;
            result.add(new Card("MESSAGES", "Messages", contact.uuid, contact.name,
                    PhoneClientData.messagePreview(last), when, true, contact.uuid));
        }
        for (PhoneStateData.PlayerEntry contact : PhoneClientData.sortedGramContacts()) {
            int unread = PhoneClientData.unreadGramCount(contact.uuid);
            if (unread <= 0) continue;
            PhoneStateData.MessageEntry last = PhoneClientData.lastGramMessage(contact.uuid);
            long when = last == null ? System.currentTimeMillis() : last.timestamp;
            result.add(new Card("BLOCKGRAM_DM", "Lumi DM", contact.uuid, contact.name,
                    PhoneClientData.messagePreview(last), when, true, contact.uuid));
        }
        for (PhoneSocialStateData.ProfileEntry match : PhoneSocialClientData.tinderMatches()) {
            int unread = PhoneClientData.unreadTinderCount(match.playerUuid);
            if (unread <= 0) continue;
            PhoneStateData.MessageEntry last = PhoneClientData.lastTinderMessage(match.playerUuid);
            long when = last == null ? System.currentTimeMillis() : last.timestamp;
            String name = match.playerName == null || match.playerName.isBlank() ? PhoneSettingsStore.text("Oyuncu", "Player") : match.playerName;
            result.add(new Card("TINDER_DM", "Minder DM", match.playerUuid, name,
                    PhoneClientData.messagePreview(last), when, true, match.playerUuid));
        }

        String self = PhoneClientData.selfUuid();
        for (PhoneBlockGramStore.NotificationEntry notification : PhoneBlockGramStore.notifications(self)) {
            result.add(new Card("BLOCKGRAM", "LUMI", notification.actorUuid(), notification.actorName(),
                    PhoneSettingsStore.localizeNetworkText(notification.text()), notification.createdAt(), !notification.read(), notification.postId()));
        }
        for (PhoneSocialStateData.NotificationEntry notification : PhoneSocialClientData.notifications("BLOCKPOST", self)) {
            result.add(new Card("BLOCKPOST", "X", notification.actorUuid, notification.actorName,
                    PhoneSettingsStore.localizeNetworkText(notification.text), notification.createdAt, !notification.read, notification.postId));
        }
        for (PhoneSocialStateData.NotificationEntry notification : PhoneSocialClientData.notifications("TINDER", self)) {
            result.add(new Card("TINDER", "Minder", notification.actorUuid, notification.actorName,
                    PhoneSettingsStore.localizeNetworkText(notification.text), notification.createdAt, !notification.read, notification.actorUuid));
        }
        result.removeIf(card -> PhonePrivacyStore.muted(card.actorUuid));
        result.sort(Comparator.comparingLong((Card card) -> card.createdAt).reversed());
        return result;
    }

    private String relativeTime(long createdAt) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - createdAt) / 1000L);
        if (seconds < 60L) return PhoneSettingsStore.text("şimdi", "now");
        long minutes = seconds / 60L;
        if (minutes < 60L) return minutes + "m";
        long hours = minutes / 60L;
        if (hours < 24L) return hours + "h";
        return (hours / 24L) + "d";
    }

    private String trim(String value, int max) {
        String safe = value == null ? "" : value.replace('\n', ' ').trim();
        if (safe.length() <= max) return safe;
        return safe.substring(0, Math.max(1, max - 1)) + "…";
    }

    private void clearAllNotifications() {
        com.mikasrevs.newphone.marketplace.client.MarketplaceClient.readNotifications();
        for (PhoneStateData.PlayerEntry contact : PhoneClientData.sortedMessagePeers()) {
            if (PhoneClientData.unreadCount(contact.uuid) > 0) PhoneClientData.markConversationOpened(contact.uuid);
        }
        for (PhoneStateData.PlayerEntry contact : PhoneClientData.sortedGramContacts()) {
            if (PhoneClientData.unreadGramCount(contact.uuid) > 0) {
                PhoneClientData.markGramConversationOpened(contact.uuid);
            }
        }
        for (PhoneSocialStateData.ProfileEntry match : PhoneSocialClientData.tinderMatches()) {
            if (PhoneClientData.unreadTinderCount(match.playerUuid) > 0) {
                PhoneClientData.markTinderConversationRead(match.playerUuid);
            }
        }
        PhoneBlockGramStore.clearNotifications(PhoneClientData.selfUuid());
        PhoneSocialClientData.clearNotifications("BLOCKPOST");
        PhoneSocialClientData.clearNotifications("TINDER");
        scrollOffset = 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!ZPhoneClient.isSessionUnlocked()) {
            if (com.mikasrevs.newphone.client.security.PhoneSecurityStore.hasPin()) ZPhoneClient.openPinEntryScreen();
            else ZPhoneClient.unlockToHome();
            return true;
        }
        if (inside(mouseX, mouseY, 211, 57, 279, 83)) {
            clearAllNotifications();
            return true;
        }

        List<Card> cards = cards();
        int count = Math.min(5, Math.max(0, cards.size() - scrollOffset));
        for (int i = 0; i < count; i++) {
            int top = 116 + i * 86;
            if (!inside(mouseX, mouseY, 18, top, 282, top + 78)) continue;
            Card card = cards.get(scrollOffset + i);
            if ("MARKETPLACE_TRADE".equals(card.kind)) {
                PhoneNavigationState.open(minecraft,this,new com.mikasrevs.newphone.marketplace.trade.TradeScreen());
            } else if ("MARKETPLACE".equals(card.kind)) {
                com.mikasrevs.newphone.marketplace.client.MarketplaceClient.readNotifications();
                PhoneNavigationState.open(minecraft,this,new com.mikasrevs.newphone.marketplace.client.MarketplaceListingDetailsScreen(card.target));
            } else if ("MESSAGES".equals(card.kind)) {
                PhoneClientData.markConversationOpened(card.target);
                PhoneNavigationState.open(minecraft, this, com.mikasrevs.newphone.client.data.PhoneGroupClient.group(card.target)!=null?new PhoneGroupThreadScreen(card.target):new ZPhoneMessageThreadScreen(card.target, card.title));
            } else if ("BLOCKGRAM_DM".equals(card.kind)) {
                PhoneClientData.markGramConversationOpened(card.target);
                PhoneNavigationState.open(minecraft, this,
                        new ZPhoneBlockGramThreadScreen(card.target, card.title));
            } else if ("BLOCKGRAM".equals(card.kind)) {
                PhoneBlockGramStore.markNotificationsRead(PhoneClientData.selfUuid());
                PhoneNavigationState.open(minecraft, this, new ZPhoneBlockGramScreen());
            } else if ("BLOCKPOST".equals(card.kind)) {
                PhoneSocialClientData.markNotificationsRead("BLOCKPOST");
                PhoneNavigationState.open(minecraft, this, new com.mikasrevs.newphone.client.gui.PhoneBlockPostScreen());
            } else if ("TINDER_DM".equals(card.kind)) {
                PhoneClientData.markTinderConversationRead(card.actorUuid);
                PhoneNavigationState.open(minecraft, this, new ZPhoneTinderChatScreen(card.actorUuid, card.title));
            } else if ("TINDER".equals(card.kind)) {
                PhoneSocialClientData.markNotificationsRead("TINDER");
                if (card.actorUuid != null && PhoneSocialClientData.isTinderMatch(card.actorUuid)) {
                    PhoneNavigationState.open(minecraft, this, new ZPhoneTinderChatScreen(card.actorUuid, card.title));
                } else {
                    PhoneNavigationState.open(minecraft, this, new ZPhoneBrowserScreen());
                }
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inside(mouseX, mouseY, 12, 104, 288, 565) && delta != 0.0D) {
            int max = Math.max(0, cards().size() - 5);
            if (delta < 0) scrollOffset = Math.min(max, scrollOffset + 1);
            else scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private record Card(String kind, String kindLabel, String actorUuid, String title,
                        String body, long createdAt, boolean unread, String target) {
    }
}

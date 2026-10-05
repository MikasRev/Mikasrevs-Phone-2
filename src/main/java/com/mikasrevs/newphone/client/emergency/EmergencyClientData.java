package com.mikasrevs.newphone.client.emergency;

import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;

import com.google.gson.Gson;
import com.mikasrevs.newphone.client.sound.PhoneSoundManager;
import com.mikasrevs.newphone.emergency.EmergencyCategory;
import com.mikasrevs.newphone.emergency.EmergencyService;
import com.mikasrevs.newphone.emergency.EmergencyStatus;
import com.mikasrevs.newphone.network.EmergencyStateData;
import com.mikasrevs.newphone.network.PhoneNetwork;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client cache and action facade for both the civilian app and dedicated tablets. */
public final class EmergencyClientData {
    private static final Gson GSON = new Gson();
    private static final Set<String> SEEN_RESPONDER_ALERTS = new HashSet<>();
    private static final Deque<IncomingDispatch> INCOMING = new ArrayDeque<>();
    private static volatile EmergencyStateData state = new EmergencyStateData();
    private static String noticeText = "";
    private static String phoneNoticeKey = "", phoneNoticeArgument = "";
    private static long noticeUntil;

    private EmergencyClientData() {
    }

    public static void applyStateJson(String json) {
        if (json == null || json.isBlank()) return;
        try {
            EmergencyStateData previous = state;
            EmergencyStateData decoded = GSON.fromJson(json, EmergencyStateData.class);
            if (decoded == null) return;
            if (decoded.authorizedServices == null) decoded.authorizedServices = new ArrayList<>();
            if (decoded.dispatches == null) decoded.dispatches = new ArrayList<>();
            if (decoded.chatMessages == null) decoded.chatMessages = new ArrayList<>();
            state = decoded;
            detectIncoming(decoded);
            detectLifecycleTransitions(previous, decoded);
            dropIncomingForAssignedServices(decoded);
            if (decoded.noticeKey != null && !decoded.noticeKey.isBlank()) {
                showNotice(decoded.noticeKey, decoded.noticeArgument);
            }
        } catch (RuntimeException exception) {
            System.err.println("[MPhone] Could not decode emergency state: " + exception.getMessage());
        }
    }

    public static void requestSync() {
        PhoneNetwork.sendAction("EMERGENCY_SYNC", "", "");
    }

    public static void create(EmergencyCategory category, String description) {
        if (category == null) return;
        PhoneNetwork.sendAction("EMERGENCY_CREATE", category.id(), description == null ? "" : description);
    }

    public static void cancel(long dispatchId) {
        PhoneNetwork.sendAction("EMERGENCY_CANCEL", Long.toString(dispatchId), "");
    }

    public static void accept(long dispatchId, EmergencyService service) {
        responderAction("EMERGENCY_ACCEPT", dispatchId, service);
    }

    public static void decline(long dispatchId, EmergencyService service) {
        responderAction("EMERGENCY_DECLINE", dispatchId, service);
        consumeIncoming(dispatchId, service);
    }

    public static void markOnScene(long dispatchId, EmergencyService service) {
        responderAction("EMERGENCY_ON_SCENE", dispatchId, service);
    }

    public static void close(long dispatchId, EmergencyService service) {
        responderAction("EMERGENCY_CLOSE", dispatchId, service);
    }

    public static void sendTabletChat(EmergencyService service, String message) {
        if (service == null || message == null || message.isBlank()) return;
        PhoneNetwork.sendAction("EMERGENCY_CHAT_SEND", service.name(), message);
    }

    public static List<EmergencyStateData.ChatEntry> chatFor(EmergencyService service) {
        if (service == null || state.chatMessages == null) return List.of();
        return state.chatMessages.stream()
                .filter(entry -> entry != null && service.name().equalsIgnoreCase(entry.service))
                .sorted(Comparator.comparingLong(entry -> entry.timestamp))
                .toList();
    }

    public static boolean isAuthorized(EmergencyService service) {
        return service != null && state.authorizedServices != null
                && state.authorizedServices.stream().anyMatch(service.name()::equalsIgnoreCase);
    }

    public static List<EmergencyStateData.DispatchEntry> activeFor(EmergencyService service) {
        if (service == null || state.dispatches == null) return List.of();
        return state.dispatches.stream()
                .filter(entry -> entry != null && service.name().equalsIgnoreCase(entry.service))
                .filter(entry -> !entry.callerOwned)
                .filter(EmergencyClientData::isActive)
                .sorted(Comparator
                        .comparingInt((EmergencyStateData.DispatchEntry entry) -> entry.priority).reversed()
                        .thenComparingLong(entry -> entry.createdAt))
                .toList();
    }

    /** The one active dispatch currently owned by this responder for the selected service. */
    public static EmergencyStateData.DispatchEntry activeAssignment(EmergencyService service) {
        if (service == null || state.dispatches == null) return null;
        for (EmergencyStateData.DispatchEntry entry : state.dispatches) {
            if (entry == null || entry.callerOwned || !entry.acceptedBySelf || !isActive(entry)) continue;
            if (service.name().equalsIgnoreCase(entry.service)) return entry;
        }
        return null;
    }

    public static EmergencyStateData.DispatchEntry find(EmergencyService service, long dispatchId) {
        if (service == null || state.dispatches == null) return null;
        for (EmergencyStateData.DispatchEntry entry : state.dispatches) {
            if (entry != null && entry.id == dispatchId && service.name().equalsIgnoreCase(entry.service)) {
                return entry;
            }
        }
        return null;
    }

    public static EmergencyStateData.DispatchEntry ownDispatch() {
        if (state.dispatches == null) return null;
        EmergencyStateData.DispatchEntry newestFinished = null;
        for (EmergencyStateData.DispatchEntry entry : state.dispatches) {
            if (entry == null || !entry.callerOwned) continue;
            if (isActive(entry)) return entry;
            if (newestFinished == null || entry.createdAt > newestFinished.createdAt) newestFinished = entry;
        }
        return newestFinished;
    }

    public static EmergencyCategory category(EmergencyStateData.DispatchEntry entry) {
        return entry == null ? null : EmergencyCategory.fromId(entry.categoryId);
    }

    public static boolean isActive(EmergencyStateData.DispatchEntry entry) {
        if (entry == null) return false;
        try {
            return EmergencyStatus.valueOf(entry.status).active();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static String noticeText() {
        return System.currentTimeMillis() <= noticeUntil ? noticeText : "";
    }

    public static String phoneNoticeText() {
        if (System.currentTimeMillis() > noticeUntil || phoneNoticeKey.isBlank()) return "";
        return phoneNoticeArgument.isBlank()
                ? PhoneSettingsStore.translation(phoneNoticeKey)
                : PhoneSettingsStore.translation(phoneNoticeKey, phoneNoticeArgument);
    }

    public static IncomingDispatch peekIncoming() {
        discardInvalidIncoming();
        return INCOMING.peekFirst();
    }

    public static IncomingDispatch pollIncoming() {
        discardInvalidIncoming();
        return INCOMING.pollFirst();
    }

    public static void consumeIncoming(long dispatchId, EmergencyService service) {
        INCOMING.removeIf(incoming -> incoming.dispatchId == dispatchId && incoming.service == service);
    }

    public static void clear() {
        state = new EmergencyStateData();
        SEEN_RESPONDER_ALERTS.clear();
        INCOMING.clear();
        noticeText = "";
        phoneNoticeKey = "";
        phoneNoticeArgument = "";
        noticeUntil = 0L;
    }

    private static void responderAction(String action, long dispatchId, EmergencyService service) {
        if (service == null || dispatchId <= 0L) return;
        PhoneNetwork.sendAction(action, Long.toString(dispatchId), service.name());
    }

    private static void detectIncoming(EmergencyStateData decoded) {
        for (EmergencyStateData.DispatchEntry entry : decoded.dispatches) {
            if (entry == null || entry.callerOwned || !isActive(entry)
                    || entry.acceptedBySelf || entry.declinedBySelf) continue;
            EmergencyService service = EmergencyService.fromId(entry.service);
            if (service == null || !isAuthorizedIn(decoded, service)
                    || hasAcceptedAssignmentIn(decoded, service)) continue;
            String key = service.name() + ":" + entry.id;
            if (!SEEN_RESPONDER_ALERTS.add(key)) continue;
            INCOMING.addLast(new IncomingDispatch(entry.id, service));
            PhoneSoundManager.playEmergencyNotification();
        }
    }

    private static void discardInvalidIncoming() {
        while (!INCOMING.isEmpty()) {
            IncomingDispatch incoming = INCOMING.peekFirst();
            EmergencyStateData.DispatchEntry entry = find(incoming.service, incoming.dispatchId);
            if (entry != null && isActive(entry) && !entry.acceptedBySelf && !entry.declinedBySelf
                    && activeAssignment(incoming.service) == null) return;
            INCOMING.removeFirst();
        }
    }

    private static void detectLifecycleTransitions(EmergencyStateData previous, EmergencyStateData current) {
        if (previous == null || previous.dispatches == null) return;
        for (EmergencyStateData.DispatchEntry oldEntry : previous.dispatches) {
            if (oldEntry == null || !oldEntry.callerOwned || !isActive(oldEntry)) continue;
            EmergencyService service = EmergencyService.fromId(oldEntry.service);
            EmergencyStateData.DispatchEntry updated = findIn(current, service, oldEntry.id);
            if (updated != null && "CLOSED".equalsIgnoreCase(updated.status)) {
                showNotice("emergency.newphone.notice.dispatch_resolved", "#" + oldEntry.id);
                PhoneSoundManager.playEmergencyNotification();
                return;
            }
        }
        for (EmergencyStateData.DispatchEntry oldEntry : previous.dispatches) {
            if (oldEntry == null || !oldEntry.acceptedBySelf || !isActive(oldEntry)) continue;
            EmergencyService service = EmergencyService.fromId(oldEntry.service);
            EmergencyStateData.DispatchEntry updated = findIn(current, service, oldEntry.id);
            if (updated == null || "CANCELLED".equalsIgnoreCase(updated.status)) {
                showNotice("emergency.newphone.notice.dispatch_cancelled", "#" + oldEntry.id);
                PhoneSoundManager.playEmergencyNotification();
                return;
            }
            if ("CLOSED".equalsIgnoreCase(updated.status)) {
                showNotice("emergency.newphone.notice.dispatch_resolved", "#" + oldEntry.id);
                PhoneSoundManager.playEmergencyNotification();
                return;
            }
            if (!updated.acceptedBySelf) {
                showNotice("emergency.newphone.notice.assignment_removed", "#" + oldEntry.id);
                return;
            }
        }
    }

    private static void dropIncomingForAssignedServices(EmergencyStateData decoded) {
        if (decoded == null) return;
        INCOMING.removeIf(incoming -> hasAcceptedAssignmentIn(decoded, incoming.service));
    }

    private static boolean hasAcceptedAssignmentIn(EmergencyStateData source, EmergencyService service) {
        if (source == null || source.dispatches == null || service == null) return false;
        for (EmergencyStateData.DispatchEntry entry : source.dispatches) {
            if (entry == null || entry.callerOwned || !entry.acceptedBySelf || !isActive(entry)) continue;
            if (service.name().equalsIgnoreCase(entry.service)) return true;
        }
        return false;
    }

    private static EmergencyStateData.DispatchEntry findIn(
            EmergencyStateData source,
            EmergencyService service,
            long dispatchId
    ) {
        if (source.dispatches == null || service == null) return null;
        for (EmergencyStateData.DispatchEntry entry : source.dispatches) {
            if (entry != null && entry.id == dispatchId && service.name().equalsIgnoreCase(entry.service)) return entry;
        }
        return null;
    }


    private static boolean isAuthorizedIn(EmergencyStateData source, EmergencyService service) {
        return source.authorizedServices != null
                && source.authorizedServices.stream().anyMatch(service.name()::equalsIgnoreCase);
    }

    private static void showNotice(String key, String argument) {
        phoneNoticeKey = key;
        phoneNoticeArgument = argument == null ? "" : argument;
        Component notice = argument == null || argument.isBlank()
                ? Component.translatable(key)
                : Component.translatable(key, argument);
        noticeText = notice.getString();
        noticeUntil = System.currentTimeMillis() + 3_200L;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(notice, true);
        }
    }

    public record IncomingDispatch(long dispatchId, EmergencyService service) {
    }
}

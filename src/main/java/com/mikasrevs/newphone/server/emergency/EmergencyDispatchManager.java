package com.mikasrevs.newphone.server.emergency;

import com.google.gson.Gson;
import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.ResponderTabletItem;
import com.mikasrevs.newphone.emergency.EmergencyCategory;
import com.mikasrevs.newphone.emergency.EmergencyService;
import com.mikasrevs.newphone.emergency.EmergencyStatus;
import com.mikasrevs.newphone.network.EmergencyStateData;
import com.mikasrevs.newphone.network.PhoneNetwork;
import com.mikasrevs.newphone.server.data.EmergencyRoleSavedData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/** Server-authoritative lifecycle and synchronization for active emergency dispatches. */
public final class EmergencyDispatchManager {
    public static final int MAX_DESCRIPTION_LENGTH = 160;

    private static final Gson GSON = new Gson();
    private static final long CREATE_ATTEMPT_INTERVAL_MS = 1_000L;
    private static final long CREATE_COOLDOWN_MS = 45_000L;
    private static final long CALLER_DISCONNECT_GRACE_MS = 10_000L;
    private static final long FINISHED_RETENTION_MS = 10_000L;
    private static final long CALLER_DISTANCE_SYNC_MS = 1_000L;
    private static final int MAX_TABLET_CHAT_LENGTH = 180;
    private static final int MAX_TABLET_CHAT_HISTORY = 80;
    private static final long TABLET_CHAT_RATE_LIMIT_MS = 650L;
    private static final double AUTO_ON_SCENE_RADIUS_BLOCKS = 32.0D;
    private static final double AUTO_ON_SCENE_MAX_VERTICAL_BLOCKS = 24.0D;
    private static final Map<MinecraftServer, EmergencyDispatchManager> INSTANCES = new WeakHashMap<>();
    private static final List<UUID> POLICE_DEMO_CALLERS = List.of(
            demoCallerId(EmergencyService.POLICE, 0), demoCallerId(EmergencyService.POLICE, 1),
            demoCallerId(EmergencyService.POLICE, 2), demoCallerId(EmergencyService.POLICE, 3)
    );
    private static final List<UUID> EMS_DEMO_CALLERS = List.of(
            demoCallerId(EmergencyService.EMS, 0), demoCallerId(EmergencyService.EMS, 1),
            demoCallerId(EmergencyService.EMS, 2), demoCallerId(EmergencyService.EMS, 3)
    );

    private final MinecraftServer server;
    private final Map<Long, EmergencyDispatch> dispatches = new LinkedHashMap<>();
    private final Map<UUID, Long> lastCreateAttemptAt = new HashMap<>();
    private final Map<UUID, Long> lastFinishedAt = new HashMap<>();
    private final Map<UUID, Long> callerDisconnectDeadlines = new HashMap<>();
    private final Map<UUID, Long> lastTabletChatAt = new HashMap<>();
    private final EnumMap<EmergencyService, Deque<TabletChatMessage>> tabletChat = new EnumMap<>(EmergencyService.class);
    private long nextDispatchId = 1042L;
    private long lastCallerDistanceSyncAt;

    private EmergencyDispatchManager(MinecraftServer server) {
        this.server = server;
        for (EmergencyService service : EmergencyService.values()) {
            tabletChat.put(service, new ArrayDeque<>());
        }
    }

    public static synchronized EmergencyDispatchManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, EmergencyDispatchManager::new);
    }

    public static synchronized void discard(MinecraftServer server) {
        if (server != null) INSTANCES.remove(server);
    }

    public static void handleAction(ServerPlayer sender, String action, String target, String body) {
        if (sender == null || sender.server == null) return;
        EmergencyDispatchManager manager = get(sender.server);
        switch (action == null ? "" : action) {
            case "EMERGENCY_SYNC" -> manager.syncPlayer(sender, "", "");
            case "EMERGENCY_CREATE" -> manager.create(sender, target, body);
            case "EMERGENCY_CANCEL" -> manager.cancelByCaller(sender, target);
            case "EMERGENCY_ACCEPT" -> manager.accept(sender, target, body);
            case "EMERGENCY_DECLINE" -> manager.decline(sender, target, body);
            case "EMERGENCY_ON_SCENE" -> manager.markOnScene(sender, target, body);
            case "EMERGENCY_CLOSE" -> manager.close(sender, target, body);
            case "EMERGENCY_CHAT_SEND" -> manager.sendTabletChat(sender, target, body);
            default -> {
            }
        }
    }

    public synchronized void sendTabletChat(ServerPlayer sender, String serviceId, String rawMessage) {
        EmergencyService service = EmergencyService.fromId(serviceId);
        if (service == null
                || !EmergencyRoleSavedData.get(server).canServe(sender.getUUID(), service)
                || !ResponderTabletItem.hasTablet(sender, service)) {
            syncPlayer(sender, "emergency.newphone.notice.unauthorized", "");
            return;
        }

        long now = System.currentTimeMillis();
        long previous = lastTabletChatAt.getOrDefault(sender.getUUID(), 0L);
        if (now - previous < TABLET_CHAT_RATE_LIMIT_MS) return;
        lastTabletChatAt.put(sender.getUUID(), now);

        String message = sanitizeTabletChat(rawMessage);
        if (message.isBlank()) return;
        Deque<TabletChatMessage> history = tabletChat.computeIfAbsent(service, ignored -> new ArrayDeque<>());
        history.addLast(new TabletChatMessage(
                service, sender.getUUID(), sender.getGameProfile().getName(), message, now
        ));
        while (history.size() > MAX_TABLET_CHAT_HISTORY) history.removeFirst();
        syncService(service);
    }

    public synchronized void create(ServerPlayer caller, String categoryId, String rawDescription) {
        long now = System.currentTimeMillis();
        long previousAttempt = lastCreateAttemptAt.getOrDefault(caller.getUUID(), 0L);
        lastCreateAttemptAt.put(caller.getUUID(), now);
        if (now - previousAttempt < CREATE_ATTEMPT_INTERVAL_MS) {
            syncPlayer(caller, "emergency.newphone.notice.too_fast", "");
            return;
        }
        if (!PhoneInventoryUtil.hasPhone(caller)) {
            syncPlayer(caller, "emergency.newphone.notice.phone_required", "");
            return;
        }
        EmergencyCategory category = EmergencyCategory.fromId(categoryId);
        if (category == null) {
            syncPlayer(caller, "emergency.newphone.notice.invalid_category", "");
            return;
        }
        if (activeOwnedBy(caller.getUUID()) != null) {
            syncPlayer(caller, "emergency.newphone.notice.active_exists", "");
            return;
        }
        long lastFinished = lastFinishedAt.getOrDefault(caller.getUUID(), 0L);
        if (now - lastFinished < CREATE_COOLDOWN_MS) {
            long seconds = Math.max(1L, (CREATE_COOLDOWN_MS - (now - lastFinished) + 999L) / 1_000L);
            syncPlayer(caller, "emergency.newphone.notice.cooldown", Long.toString(seconds));
            return;
        }
        String description = sanitizeDescription(rawDescription);
        if (rawDescription != null && rawDescription.length() > MAX_DESCRIPTION_LENGTH) {
            syncPlayer(caller, "emergency.newphone.notice.description_too_long",
                    Integer.toString(MAX_DESCRIPTION_LENGTH));
            return;
        }

        long id = nextDispatchId++;
        EmergencyDispatch dispatch = new EmergencyDispatch(
                id,
                category,
                caller.getUUID(),
                caller.getGameProfile().getName(),
                caller.level().dimension().location().toString(),
                Mth.floor(caller.getX()),
                Mth.floor(caller.getY()),
                Mth.floor(caller.getZ()),
                description,
                now
        );
        dispatch.addService(category.service(), category.maximumResponders());
        dispatches.put(id, dispatch);
        callerDisconnectDeadlines.remove(caller.getUUID());
        syncAll();
        syncPlayer(caller, "emergency.newphone.notice.created", "#" + id);
    }

    /** Replaces the previous demo set with four fully interactive calls near the command source. */
    public synchronized int createPoliceDemo(ServerPlayer operator) {
        return createResponderDemo(operator, EmergencyService.POLICE, POLICE_DEMO_CALLERS, List.of(
                new DemoCall(EmergencyCategory.ARMED_THREAT, "Apartment Resident",
                        "Armed suspect reported inside the apartment complex.", 54, -42),
                new DemoCall(EmergencyCategory.ROBBERY, "Bank Teller",
                        "Silent alarm activated. Suspect last seen near the main entrance.", -76, 38),
                new DemoCall(EmergencyCategory.POLICE_OTHER, "Store Security",
                        "Aggressive individual refusing to leave the market.", 92, 64),
                new DemoCall(EmergencyCategory.VEHICLE_INCIDENT, "Roadside Witness",
                        "Two vehicles blocking the road; traffic is building up.", -108, -70)
        ));
    }

    /** Medical counterpart to the police sample set, using real EMS dispatch lifecycle actions. */
    public synchronized int createEmsDemo(ServerPlayer operator) {
        return createResponderDemo(operator, EmergencyService.EMS, EMS_DEMO_CALLERS, List.of(
                new DemoCall(EmergencyCategory.SEVERE_BLEEDING, "Construction Foreman",
                        "Worker has a deep arm wound with uncontrolled bleeding.", 46, 52),
                new DemoCall(EmergencyCategory.UNCONSCIOUS_PERSON, "Park Visitor",
                        "Unresponsive person found near the park entrance.", -62, -48),
                new DemoCall(EmergencyCategory.TRAFFIC_ACCIDENT, "Passing Driver",
                        "Vehicle collision with one patient reporting chest pain.", 88, -66),
                new DemoCall(EmergencyCategory.GUNSHOT_WOUND, "Station Attendant",
                        "Patient conscious with a suspected gunshot wound to the leg.", -104, 72)
        ));
    }

    private int createResponderDemo(
            ServerPlayer operator,
            EmergencyService service,
            List<UUID> demoCallers,
            List<DemoCall> calls
    ) {
        if (operator == null) return 0;
        dispatches.values().removeIf(dispatch -> demoCallers.contains(dispatch.callerId));

        long now = System.currentTimeMillis();
        int baseX = Mth.floor(operator.getX());
        int baseY = Mth.floor(operator.getY());
        int baseZ = Mth.floor(operator.getZ());
        String dimension = operator.level().dimension().location().toString();

        for (int index = 0; index < calls.size(); index++) {
            DemoCall sample = calls.get(index);
            EmergencyDispatch dispatch = new EmergencyDispatch(
                    nextDispatchId++,
                    sample.category(),
                    demoCallers.get(index),
                    sample.callerName(),
                    dimension,
                    baseX + sample.offsetX(),
                    baseY,
                    baseZ + sample.offsetZ(),
                    sample.description(),
                    now - index * 90_000L
            );
            dispatch.addService(service, sample.category().maximumResponders());
            dispatches.put(dispatch.id, dispatch);
        }
        syncAll();
        return calls.size();
    }

    public synchronized void accept(ServerPlayer responder, String dispatchId, String serviceId) {
        EmergencyDispatch dispatch = activeDispatch(dispatchId, responder);
        EmergencyService service = authorizedService(responder, dispatch, serviceId);
        if (dispatch == null || service == null) return;
        ResponseGroup group = dispatch.groups.get(service);
        UUID responderId = responder.getUUID();
        EmergencyDispatch existingAssignment = activeAssignment(responderId, service);
        if (existingAssignment != null && existingAssignment.id != dispatch.id) {
            syncPlayer(responder, "emergency.newphone.notice.already_accepted", "#" + existingAssignment.id);
            return;
        }
        if (group.acceptedResponders.contains(responderId)) {
            syncPlayer(responder, "emergency.newphone.notice.already_accepted", "#" + dispatch.id);
            return;
        }
        if (group.acceptedResponders.size() >= group.maximumResponders) {
            syncPlayer(responder, "emergency.newphone.notice.call_full", "#" + dispatch.id);
            return;
        }
        group.declinedResponders.remove(responderId);
        group.acceptedResponders.add(responderId);
        dispatch.recomputeStatus();
        syncAll();
        syncPlayer(responder, "emergency.newphone.notice.accepted", "#" + dispatch.id);
    }

    public synchronized void decline(ServerPlayer responder, String dispatchId, String serviceId) {
        EmergencyDispatch dispatch = activeDispatch(dispatchId, responder);
        EmergencyService service = authorizedService(responder, dispatch, serviceId);
        if (dispatch == null || service == null) return;
        ResponseGroup group = dispatch.groups.get(service);
        UUID responderId = responder.getUUID();
        if (group.acceptedResponders.contains(responderId)) {
            syncPlayer(responder, "emergency.newphone.notice.already_accepted", "#" + dispatch.id);
            return;
        }
        group.declinedResponders.add(responderId);
        syncPlayer(responder, "emergency.newphone.notice.declined", "#" + dispatch.id);
    }

    public synchronized void markOnScene(ServerPlayer responder, String dispatchId, String serviceId) {
        EmergencyDispatch dispatch = activeDispatch(dispatchId, responder);
        EmergencyService service = authorizedService(responder, dispatch, serviceId);
        if (dispatch == null || service == null) return;
        ResponseGroup group = dispatch.groups.get(service);
        if (!group.acceptedResponders.contains(responder.getUUID())) {
            syncPlayer(responder, "emergency.newphone.notice.accept_required", "#" + dispatch.id);
            return;
        }
        if (!isResponderAtScene(responder, dispatch)) {
            syncPlayer(responder, "", "");
            return;
        }
        // Arrival can be detected by both the client HUD and the server proximity sweep.
        // The set keeps this idempotent and avoids duplicate ON_SCENE notices/sync storms.
        if (!group.onSceneResponders.add(responder.getUUID())) return;
        dispatch.recomputeStatus();
        syncAll();
        syncPlayer(responder, "emergency.newphone.notice.on_scene", "#" + dispatch.id);
    }

    public synchronized void close(ServerPlayer responder, String dispatchId, String serviceId) {
        EmergencyDispatch dispatch = activeDispatch(dispatchId, responder);
        EmergencyService service = authorizedService(responder, dispatch, serviceId);
        if (dispatch == null || service == null) return;
        ResponseGroup group = dispatch.groups.get(service);
        boolean administrator = responder.server.getPlayerList().isOp(responder.getGameProfile());
        UUID responderId = responder.getUUID();
        if (!administrator && !group.acceptedResponders.contains(responderId)) {
            syncPlayer(responder, "emergency.newphone.notice.accept_required", "#" + dispatch.id);
            return;
        }
        // An accepted responder must physically reach/mark the scene before resolving the incident.
        // This protects against accidental or forged remote-close packets.
        if (!administrator && !group.onSceneResponders.contains(responderId)) {
            syncPlayer(responder, "", "");
            return;
        }
        finish(dispatch, EmergencyStatus.CLOSED);
        syncAll();
        syncPlayer(responder, "emergency.newphone.notice.closed", "#" + dispatch.id);
    }

    public synchronized void cancelByCaller(ServerPlayer caller, String dispatchId) {
        EmergencyDispatch dispatch = parseDispatch(dispatchId);
        if (dispatch == null || !dispatch.status.active()) {
            syncPlayer(caller, "emergency.newphone.notice.not_found", "");
            return;
        }
        if (!dispatch.callerId.equals(caller.getUUID())) {
            syncPlayer(caller, "emergency.newphone.notice.unauthorized", "");
            return;
        }
        finish(dispatch, EmergencyStatus.CANCELLED);
        syncAll();
        syncPlayer(caller, "emergency.newphone.notice.cancelled", "#" + dispatch.id);
    }

    public synchronized void handlePlayerLogin(ServerPlayer player) {
        if (player == null) return;
        callerDisconnectDeadlines.remove(player.getUUID());
        syncPlayer(player, "", "");
    }

    public synchronized void handlePlayerLogout(ServerPlayer player) {
        if (player == null) return;
        boolean changed = removeResponder(player.getUUID());
        EmergencyDispatch owned = activeOwnedBy(player.getUUID());
        if (owned != null) {
            callerDisconnectDeadlines.put(player.getUUID(),
                    System.currentTimeMillis() + CALLER_DISCONNECT_GRACE_MS);
        }
        if (changed) syncAll();
    }

    public synchronized void handlePlayerDeath(ServerPlayer player) {
        if (player != null && removeResponder(player.getUUID())) syncAll();
    }

    public synchronized void revalidateRespondersAndSync() {
        removeUnavailableResponders();
        syncAll();
    }

    public synchronized void tick() {
        long now = System.currentTimeMillis();
        boolean changed = removeUnavailableResponders();
        changed |= markArrivedRespondersOnScene();
        lastCreateAttemptAt.entrySet().removeIf(entry -> now - entry.getValue() >= CREATE_ATTEMPT_INTERVAL_MS);
        lastFinishedAt.entrySet().removeIf(entry -> now - entry.getValue() >= CREATE_COOLDOWN_MS);

        for (Map.Entry<UUID, Long> entry : new ArrayList<>(callerDisconnectDeadlines.entrySet())) {
            if (now < entry.getValue()) continue;
            UUID callerId = entry.getKey();
            callerDisconnectDeadlines.remove(callerId);
            if (server.getPlayerList().getPlayer(callerId) != null) continue;
            EmergencyDispatch owned = activeOwnedBy(callerId);
            if (owned != null) {
                finish(owned, EmergencyStatus.CANCELLED);
                changed = true;
            }
        }

        for (EmergencyDispatch dispatch : new ArrayList<>(dispatches.values())) {
            if (dispatch.status.active() || dispatch.finishedAt <= 0L) continue;
            if (now - dispatch.finishedAt >= FINISHED_RETENTION_MS) {
                dispatches.remove(dispatch.id);
                changed = true;
            }
        }
        if (changed) syncAll();

        if (now - lastCallerDistanceSyncAt >= CALLER_DISTANCE_SYNC_MS) {
            lastCallerDistanceSyncAt = now;
            Set<UUID> synced = new HashSet<>();
            for (EmergencyDispatch dispatch : dispatches.values()) {
                if (!synced.add(dispatch.callerId)) continue;
                ServerPlayer caller = server.getPlayerList().getPlayer(dispatch.callerId);
                if (caller != null) syncPlayer(caller, "", "");
            }
        }
    }

    public synchronized void syncAll() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player, "", "");
        }
    }

    public synchronized void syncPlayer(ServerPlayer player, String noticeKey, String noticeArgument) {
        if (player == null) return;
        EmergencyStateData state = buildState(player);
        state.noticeKey = noticeKey == null ? "" : noticeKey;
        state.noticeArgument = noticeArgument == null ? "" : noticeArgument;
        PhoneNetwork.sendEmergencyState(player, GSON.toJson(state));
    }

    private void syncService(EmergencyService service) {
        EmergencyRoleSavedData roles = EmergencyRoleSavedData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (roles.canServe(player.getUUID(), service)) syncPlayer(player, "", "");
        }
    }

    private EmergencyStateData buildState(ServerPlayer viewer) {
        EmergencyStateData state = new EmergencyStateData();
        state.serverTime = System.currentTimeMillis();
        EnumSet<EmergencyService> authorized = EmergencyRoleSavedData.get(server).services(viewer.getUUID());
        authorized.stream().map(Enum::name).forEach(state.authorizedServices::add);

        for (EmergencyDispatch dispatch : dispatches.values()) {
            boolean callerOwned = dispatch.callerId.equals(viewer.getUUID());
            for (Map.Entry<EmergencyService, ResponseGroup> groupEntry : dispatch.groups.entrySet()) {
                EmergencyService service = groupEntry.getKey();
                if (!callerOwned && !authorized.contains(service)) continue;
                ResponseGroup group = groupEntry.getValue();
                EmergencyStateData.DispatchEntry entry = new EmergencyStateData.DispatchEntry();
                entry.id = dispatch.id;
                entry.service = service.name();
                entry.categoryId = dispatch.category.id();
                entry.risk = dispatch.category.risk().name();
                entry.priority = dispatch.category.priority();
                entry.callerName = dispatch.callerName;
                entry.description = dispatch.description;
                entry.dimension = dispatch.dimension;
                entry.x = dispatch.x;
                entry.y = dispatch.y;
                entry.z = dispatch.z;
                entry.createdAt = dispatch.createdAt;
                entry.maximumResponders = group.maximumResponders;
                entry.responderCount = group.acceptedResponders.size();
                entry.onSceneResponderCount = group.onSceneResponders.size();
                entry.status = dispatch.status.name();
                entry.callerOwned = callerOwned;
                entry.acceptedBySelf = group.acceptedResponders.contains(viewer.getUUID());
                entry.declinedBySelf = group.declinedResponders.contains(viewer.getUUID());
                entry.onSceneBySelf = group.onSceneResponders.contains(viewer.getUUID());
                if (callerOwned) fillNearestResponder(entry, dispatch, group);
                state.dispatches.add(entry);
            }
        }
        for (EmergencyService service : authorized) {
            Deque<TabletChatMessage> history = tabletChat.get(service);
            if (history == null) continue;
            for (TabletChatMessage chat : history) {
                EmergencyStateData.ChatEntry entry = new EmergencyStateData.ChatEntry();
                entry.service = chat.service().name();
                entry.senderUuid = chat.senderId().toString();
                entry.senderName = chat.senderName();
                entry.message = chat.message();
                entry.timestamp = chat.timestamp();
                state.chatMessages.add(entry);
            }
        }
        state.chatMessages.sort(Comparator.comparingLong(entry -> entry.timestamp));
        state.dispatches.sort(Comparator
                .comparingInt((EmergencyStateData.DispatchEntry entry) -> entry.priority).reversed()
                .thenComparingLong(entry -> entry.createdAt));
        return state;
    }

    private void fillNearestResponder(
            EmergencyStateData.DispatchEntry entry,
            EmergencyDispatch dispatch,
            ResponseGroup group
    ) {
        double nearest = Double.POSITIVE_INFINITY;
        boolean hasAcceptedInAnotherDimension = false;
        for (UUID responderId : group.acceptedResponders) {
            ServerPlayer responder = server.getPlayerList().getPlayer(responderId);
            if (responder == null) continue;
            String dimension = responder.level().dimension().location().toString();
            if (!dispatch.dimension.equals(dimension)) {
                hasAcceptedInAnotherDimension = true;
                continue;
            }
            double dx = responder.getX() - dispatch.x;
            double dy = responder.getY() - dispatch.y;
            double dz = responder.getZ() - dispatch.z;
            nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        if (Double.isFinite(nearest)) entry.nearestResponderDistance = Mth.floor(nearest);
        else entry.nearestResponderDifferentDimension = hasAcceptedInAnotherDimension;
    }

    private EmergencyDispatch activeDispatch(String rawId, ServerPlayer requester) {
        EmergencyDispatch dispatch = parseDispatch(rawId);
        if (dispatch == null) {
            syncPlayer(requester, "emergency.newphone.notice.not_found", "");
            return null;
        }
        if (!dispatch.status.active()) {
            syncPlayer(requester, "emergency.newphone.notice.inactive", "#" + dispatch.id);
            return null;
        }
        return dispatch;
    }

    private EmergencyService authorizedService(
            ServerPlayer responder,
            EmergencyDispatch dispatch,
            String serviceId
    ) {
        if (dispatch == null) return null;
        EmergencyService service = EmergencyService.fromId(serviceId);
        if (service == null || !dispatch.groups.containsKey(service)
                || dispatch.callerId.equals(responder.getUUID())
                || !EmergencyRoleSavedData.get(server).canServe(responder.getUUID(), service)
                || !ResponderTabletItem.hasTablet(responder, service)) {
            syncPlayer(responder, "emergency.newphone.notice.unauthorized", "");
            return null;
        }
        return service;
    }

    private EmergencyDispatch parseDispatch(String rawId) {
        if (rawId == null || rawId.isBlank()) return null;
        String value = rawId.trim();
        if (value.startsWith("#")) value = value.substring(1);
        try {
            return dispatches.get(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private EmergencyDispatch activeAssignment(UUID responderId, EmergencyService service) {
        if (responderId == null || service == null) return null;
        for (EmergencyDispatch dispatch : dispatches.values()) {
            if (!dispatch.status.active()) continue;
            ResponseGroup group = dispatch.groups.get(service);
            if (group != null && group.acceptedResponders.contains(responderId)) return dispatch;
        }
        return null;
    }

    private boolean markArrivedRespondersOnScene() {
        boolean changed = false;
        for (EmergencyDispatch dispatch : dispatches.values()) {
            if (!dispatch.status.active()) continue;
            boolean dispatchChanged = false;
            for (ResponseGroup group : dispatch.groups.values()) {
                for (UUID responderId : new ArrayList<>(group.acceptedResponders)) {
                    if (group.onSceneResponders.contains(responderId)) continue;
                    ServerPlayer responder = server.getPlayerList().getPlayer(responderId);
                    if (!isResponderAtScene(responder, dispatch)) continue;
                    group.onSceneResponders.add(responderId);
                    dispatchChanged = true;
                }
            }
            if (dispatchChanged) {
                dispatch.recomputeStatus();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isResponderAtScene(ServerPlayer responder, EmergencyDispatch dispatch) {
        if (responder == null || dispatch == null) return false;
        String dimension = responder.level().dimension().location().toString();
        if (!dispatch.dimension.equals(dimension)) return false;
        if (Math.abs(responder.getY() - dispatch.y) > AUTO_ON_SCENE_MAX_VERTICAL_BLOCKS) return false;
        double dx = responder.getX() - dispatch.x;
        double dz = responder.getZ() - dispatch.z;
        return dx * dx + dz * dz <= AUTO_ON_SCENE_RADIUS_BLOCKS * AUTO_ON_SCENE_RADIUS_BLOCKS;
    }

    private EmergencyDispatch activeOwnedBy(UUID callerId) {
        for (EmergencyDispatch dispatch : dispatches.values()) {
            if (dispatch.status.active() && dispatch.callerId.equals(callerId)) return dispatch;
        }
        return null;
    }

    private boolean removeResponder(UUID responderId) {
        boolean changed = false;
        for (EmergencyDispatch dispatch : dispatches.values()) {
            if (!dispatch.status.active()) continue;
            boolean dispatchChanged = false;
            for (ResponseGroup group : dispatch.groups.values()) {
                dispatchChanged |= group.acceptedResponders.remove(responderId);
                dispatchChanged |= group.onSceneResponders.remove(responderId);
            }
            if (dispatchChanged) {
                dispatch.recomputeStatus();
                changed = true;
            }
        }
        return changed;
    }

    private boolean removeUnavailableResponders() {
        boolean changed = false;
        EmergencyRoleSavedData roles = EmergencyRoleSavedData.get(server);
        for (EmergencyDispatch dispatch : dispatches.values()) {
            if (!dispatch.status.active()) continue;
            boolean dispatchChanged = false;
            for (Map.Entry<EmergencyService, ResponseGroup> entry : dispatch.groups.entrySet()) {
                EmergencyService service = entry.getKey();
                ResponseGroup group = entry.getValue();
                for (UUID responderId : new ArrayList<>(group.acceptedResponders)) {
                    ServerPlayer responder = server.getPlayerList().getPlayer(responderId);
                    if (responder != null && roles.canServe(responderId, service)
                            && ResponderTabletItem.hasTablet(responder, service)) continue;
                    group.acceptedResponders.remove(responderId);
                    group.onSceneResponders.remove(responderId);
                    dispatchChanged = true;
                }
            }
            if (dispatchChanged) {
                dispatch.recomputeStatus();
                changed = true;
            }
        }
        return changed;
    }

    private void finish(EmergencyDispatch dispatch, EmergencyStatus status) {
        dispatch.status = status;
        dispatch.finishedAt = System.currentTimeMillis();
        lastFinishedAt.put(dispatch.callerId, dispatch.finishedAt);
        callerDisconnectDeadlines.remove(dispatch.callerId);
    }

    private static String sanitizeDescription(String value) {
        if (value == null || value.isBlank()) return "";
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_DESCRIPTION_LENGTH));
        for (int index = 0; index < value.length() && result.length() < MAX_DESCRIPTION_LENGTH; index++) {
            char character = value.charAt(index);
            result.append(Character.isISOControl(character) ? ' ' : character);
        }
        return result.toString().trim().replaceAll("\\s{2,}", " ");
    }

    private static String sanitizeTabletChat(String value) {
        if (value == null || value.isBlank()) return "";
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_TABLET_CHAT_LENGTH));
        for (int index = 0; index < value.length() && result.length() < MAX_TABLET_CHAT_LENGTH; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)) {
                if (Character.isWhitespace(character)) result.append(' ');
                continue;
            }
            result.append(character);
        }
        return result.toString().trim().replaceAll("\\s{2,}", " ");
    }

    private static UUID demoCallerId(EmergencyService service, int index) {
        return UUID.nameUUIDFromBytes(("newphone:" + service.name().toLowerCase(java.util.Locale.ROOT)
                + "_demo:" + index).getBytes(StandardCharsets.UTF_8));
    }

    private record TabletChatMessage(
            EmergencyService service,
            UUID senderId,
            String senderName,
            String message,
            long timestamp
    ) { }

    private record DemoCall(
            EmergencyCategory category,
            String callerName,
            String description,
            int offsetX,
            int offsetZ
    ) { }

    private static final class EmergencyDispatch {
        final long id;
        final EmergencyCategory category;
        final UUID callerId;
        final String callerName;
        final String dimension;
        final int x;
        final int y;
        final int z;
        final String description;
        final long createdAt;
        final EnumMap<EmergencyService, ResponseGroup> groups = new EnumMap<>(EmergencyService.class);
        EmergencyStatus status = EmergencyStatus.WAITING;
        long finishedAt;
        boolean sceneSafe;

        EmergencyDispatch(
                long id,
                EmergencyCategory category,
                UUID callerId,
                String callerName,
                String dimension,
                int x,
                int y,
                int z,
                String description,
                long createdAt
        ) {
            this.id = id;
            this.category = category;
            this.callerId = callerId;
            this.callerName = callerName;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.description = description;
            this.createdAt = createdAt;
        }

        void addService(EmergencyService service, int maximumResponders) {
            groups.putIfAbsent(service, new ResponseGroup(Math.max(1, maximumResponders)));
        }

        void recomputeStatus() {
            if (!status.active()) return;
            boolean accepted = groups.values().stream().anyMatch(group -> !group.acceptedResponders.isEmpty());
            boolean onScene = groups.values().stream().anyMatch(group -> !group.onSceneResponders.isEmpty());
            status = onScene ? EmergencyStatus.ON_SCENE
                    : accepted ? EmergencyStatus.RESPONDING
                    : EmergencyStatus.WAITING;
        }
    }

    private static final class ResponseGroup {
        final int maximumResponders;
        final Set<UUID> acceptedResponders = new LinkedHashSet<>();
        final Set<UUID> onSceneResponders = new LinkedHashSet<>();
        final Set<UUID> declinedResponders = new LinkedHashSet<>();

        ResponseGroup(int maximumResponders) {
            this.maximumResponders = maximumResponders;
        }
    }
}

package com.mikasrevs.newphone.client.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.mikasrevs.newphone.network.PhoneBankStateData;
import com.mikasrevs.newphone.network.PhoneBankTransferRequest;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Client cache and requests for the dedicated Bank app channel. */
public final class PhoneBankClientData {
    private static final Gson GSON = new Gson();
    private static volatile PhoneBankStateData state = new PhoneBankStateData();
    private static volatile TransferOperation transferOperation;
    private static long lastSyncAt;
    private static final long OPERATION_TTL_MS = 300_000L;

    private PhoneBankClientData() {
    }

    public static PhoneBankStateData state() {
        return state;
    }

    public static void applyStateJson(String json) {
        if (json == null || json.isBlank()) return;
        try {
            PhoneBankStateData decoded = GSON.fromJson(json, PhoneBankStateData.class);
            if (decoded != null) {
                state = decoded;
                TransferOperation operation = transferOperation;
                if (operation != null && operation.requestId.equals(decoded.responseRequestId)) {
                    transferOperation = new TransferOperation(
                            operation.requestId, operation.targetUuid, operation.currencyId, operation.coreAmount,
                            operation.unitCoreValue, operation.quantity, operation.serverKey, operation.createdAt,
                            operation.lastSentAt, operation.retryAttempts, false, !"PENDING".equals(decoded.resultCode),
                            safe(decoded.resultCode), safe(decoded.resultPeerName), safe(decoded.resultAmount)
                    );
                }
            }
        } catch (JsonParseException ignored) {
        }
    }

    public static void requestSync() {
        long now = System.currentTimeMillis();
        if (lastSyncAt > 0L && now - lastSyncAt < 400L) return;
        lastSyncAt = now;
        PhoneClientData.sendAction("BANK_SYNC", "", "");
    }

    public static String transfer(String targetUuid, String currencyId, long coreAmount) {
        return transfer(targetUuid, currencyId, coreAmount, "", 1L, "");
    }

    public static String transfer(String targetUuid, String currencyId, long coreAmount, String requestId) {
        return transfer(targetUuid, currencyId, coreAmount, requestId, 1L, "");
    }

    public static String transfer(String targetUuid, String currencyId, long coreAmount, String requestId,
                                  long unitCoreValue, String quantity) {
        if (targetUuid == null || targetUuid.isBlank() || currencyId == null || currencyId.isBlank()
                || coreAmount <= 0L) return "";
        String id = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        PhoneBankTransferRequest request = new PhoneBankTransferRequest(id, currencyId, coreAmount);
        PhoneClientData.sendAction("BANK_TRANSFER", targetUuid, request.encode());
        TransferOperation previous = operation();
        int retries = previous != null && id.equals(previous.requestId) ? previous.retryAttempts : 0;
        long createdAt = previous != null && id.equals(previous.requestId)
                ? previous.createdAt : System.currentTimeMillis();
        transferOperation = new TransferOperation(
                id, targetUuid, currencyId, coreAmount, Math.max(1L, unitCoreValue),
                quantity == null ? "" : quantity, currentServerKey(), createdAt,
                System.currentTimeMillis(), retries, true, false, "", "", ""
        );
        return id;
    }

    public static String retryTransfer() {
        TransferOperation operation = operation();
        if (operation == null || operation.completed) return "";
        PhoneBankTransferRequest request = new PhoneBankTransferRequest(
                operation.requestId, operation.currencyId, operation.coreAmount
        );
        PhoneClientData.sendAction("BANK_TRANSFER", operation.targetUuid, request.encode());
        transferOperation = new TransferOperation(
                operation.requestId, operation.targetUuid, operation.currencyId, operation.coreAmount,
                operation.unitCoreValue, operation.quantity, operation.serverKey, operation.createdAt,
                System.currentTimeMillis(), operation.retryAttempts + 1, true, false, "", "", ""
        );
        return operation.requestId;
    }

    public static TransferOperation operation() {
        TransferOperation operation = transferOperation;
        if (operation == null) return null;
        long now = System.currentTimeMillis();
        String serverKey = currentServerKey();
        if (now - operation.createdAt > OPERATION_TTL_MS
                || (!serverKey.isBlank() && !operation.serverKey.isBlank()
                && !serverKey.equals(operation.serverKey))) {
            transferOperation = null;
            return null;
        }
        return operation;
    }

    public static void markTransferTimedOut() {
        TransferOperation operation = operation();
        if (operation == null) return;
        transferOperation = new TransferOperation(
                operation.requestId, operation.targetUuid, operation.currencyId, operation.coreAmount,
                operation.unitCoreValue, operation.quantity, operation.serverKey, operation.createdAt,
                operation.lastSentAt, operation.retryAttempts, false, operation.completed,
                operation.resultCode, operation.resultPeerName, operation.resultAmount
        );
    }

    public static void acknowledgeTransfer(String requestId) {
        TransferOperation operation = transferOperation;
        if (operation != null && operation.completed && operation.requestId.equals(requestId)) {
            transferOperation = null;
        }
    }

    public static void abandonTransfer() {
        transferOperation = null;
    }

    public static boolean available() {
        return state.available;
    }

    public static String status() {
        return safe(state.status);
    }

    public static String provider() {
        return safe(state.provider);
    }

    public static String accountName() {
        return safe(state.accountName);
    }

    public static List<PhoneBankStateData.BalanceEntry> balances() {
        return state.balances == null ? List.of() : List.copyOf(state.balances);
    }

    public static void clear() {
        state = new PhoneBankStateData();
        lastSyncAt = 0L;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String currentServerKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return "";
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) return "remote:" + server.ip;
        if (minecraft.getSingleplayerServer() != null) {
            return com.mikasrevs.newphone.integration.currency.PhoneBankScope.local(
                    minecraft.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        }
        return "";
    }

    public record TransferOperation(String requestId, String targetUuid, String currencyId, long coreAmount,
                                    long unitCoreValue, String quantity, String serverKey, long createdAt,
                                    long lastSentAt, int retryAttempts, boolean awaitingResponse, boolean completed,
                                    String resultCode, String resultPeerName, String resultAmount) {
    }
}

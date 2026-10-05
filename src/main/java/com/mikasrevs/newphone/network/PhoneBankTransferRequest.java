package com.mikasrevs.newphone.network;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.UUID;

/** JSON body carried by the generic phone action packet for an exact bank transfer. */
public final class PhoneBankTransferRequest {
    private static final Gson GSON = new Gson();

    public String requestId = "";
    public String currencyId = "";
    public long coreAmount;

    public PhoneBankTransferRequest() {
    }

    public PhoneBankTransferRequest(String requestId, String currencyId, long coreAmount) {
        this.requestId = safe(requestId);
        this.currencyId = safe(currencyId);
        this.coreAmount = coreAmount;
    }

    public String encode() {
        return GSON.toJson(this);
    }

    public static PhoneBankTransferRequest decode(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            PhoneBankTransferRequest request = GSON.fromJson(json, PhoneBankTransferRequest.class);
            if (request == null || request.currencyId == null || request.requestId == null) return null;
            request.currencyId = request.currencyId.trim();
            request.requestId = request.requestId.trim();
            if (request.currencyId.isEmpty() || request.currencyId.length() > 128
                    || request.requestId.length() > 64 || request.coreAmount <= 0L) return null;
            UUID.fromString(request.requestId);
            return request;
        } catch (JsonParseException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

package com.mikasrevs.newphone.integration.currency;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;

/** Server-only bridge for optional economy integrations. */
public interface PhoneCurrencyIntegration {
    Snapshot snapshot(ServerPlayer player);

    TransferResult transfer(ServerPlayer sender, UUID targetId, String currencyId, long coreAmount);

    default TransferResult transfer(ServerPlayer sender, UUID targetId, String currencyId, long coreAmount,
                                    PhoneBankJournal journal, PhoneBankJournal.Entry entry, boolean fresh) throws IOException {
        return fresh ? transfer(sender, targetId, currencyId, coreAmount)
                : new TransferResult(TransferStatus.PENDING, "");
    }

    default void recover(MinecraftServer server, PhoneBankJournal journal) throws IOException { }

    default PriceQuote quote(ServerPlayer player, String currencyId, long amount) {
        return new PriceQuote(false, "", "");
    }

    record PriceQuote(boolean valid, String currencyName, String formatted) { }

    record Snapshot(boolean available, String status, String provider, String accountName,
                    List<Balance> balances, long minimumTransfer, boolean onlineRecipientRequired) {
        public Snapshot(boolean available, String status, String provider, String accountName, List<Balance> balances) {
            this(available, status, provider, accountName, balances, 1L, false);
        }
        public Snapshot {
            status = safe(status);
            provider = safe(provider);
            accountName = safe(accountName);
            balances = balances == null ? List.of() : List.copyOf(balances);
        }
    }

    record Balance(String id, String name, String formatted, long coreValue, List<Unit> units) {
        public Balance {
            id = safe(id);
            name = safe(name);
            formatted = safe(formatted);
            coreValue = Math.max(0L, coreValue);
            units = units == null ? List.of() : List.copyOf(units);
        }
    }

    record Unit(String name, long coreValue) {
        public Unit {
            name = safe(name);
            coreValue = Math.max(1L, coreValue);
        }
    }

    record TransferResult(TransferStatus status, String amountText) {
        public TransferResult {
            status = status == null ? TransferStatus.FAILED : status;
            amountText = safe(amountText);
        }

        public boolean success() {
            return status == TransferStatus.SUCCESS;
        }
    }

    enum TransferStatus {
        SUCCESS,
        UNAVAILABLE,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        ACCOUNT_UNAVAILABLE,
        TARGET_OFFLINE,
        PENDING,
        FAILED
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

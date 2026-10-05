package com.mikasrevs.newphone.integration.currency;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.UUID;
import java.io.IOException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/** Resolves only the selected optional provider; a failed provider never switches ledgers. */
public final class PhoneCurrencyManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PhoneCurrencyProvider PROVIDER = PhoneCurrencyProvider.select(ModList.get()::isLoaded);
    private static final PhoneCurrencyIntegration INTEGRATION = loadIntegration();

    private PhoneCurrencyManager() {
    }

    public static PhoneCurrencyIntegration.Snapshot snapshot(ServerPlayer player) {
        try {
            if (player != null && player.server != null) INTEGRATION.recover(player.server, PhoneBankJournal.get(player.server));
            return INTEGRATION.snapshot(player);
        } catch (IOException | LinkageError | RuntimeException exception) {
            if (player != null && player.server != null) PhoneBankJournal.stop(player.server);
            LOGGER.error("MPhone bank snapshot failed", exception);
            return unavailable("ERROR");
        }
    }

    public static PhoneCurrencyIntegration.TransferResult transfer(
            ServerPlayer sender, UUID targetId, String currencyId, long coreAmount
    ) {
        return transfer(sender, targetId, currencyId, coreAmount, UUID.randomUUID().toString());
    }

    public static PhoneCurrencyIntegration.TransferResult transfer(ServerPlayer sender, UUID targetId,
            String currencyId, long coreAmount, String requestId) {
        try {
            if (sender == null || sender.server == null || !sender.server.isSameThread() || targetId == null
                    || currencyId == null || currencyId.isBlank() || coreAmount <= 0)
                return new PhoneCurrencyIntegration.TransferResult(PhoneCurrencyIntegration.TransferStatus.INVALID_AMOUNT, "");
            PhoneBankJournal journal = PhoneBankJournal.get(sender.server);
            INTEGRATION.recover(sender.server, journal);
            PhoneBankJournal.Entry entry = journal.find(sender.getUUID(), requestId);
            boolean fresh = entry == null;
            if (fresh) entry = journal.begin(sender.getUUID(), targetId, requestId, PROVIDER.name(), currencyId, coreAmount);
            if (!entry.target.equals(targetId) || !entry.currency.equals(currencyId) || entry.amount != coreAmount
                    || !entry.provider.equals(PROVIDER.name()))
                return new PhoneCurrencyIntegration.TransferResult(PhoneCurrencyIntegration.TransferStatus.INVALID_AMOUNT, "");
            if (entry.result() != null) return entry.result();
            var result = INTEGRATION.transfer(sender, targetId, currencyId, coreAmount, journal, entry, fresh);
            if (result.status() != PhoneCurrencyIntegration.TransferStatus.PENDING) journal.complete(entry, result);
            return result;
        } catch (IOException | LinkageError | RuntimeException exception) {
            if (sender != null && sender.server != null) PhoneBankJournal.stop(sender.server);
            LOGGER.error("MPhone bank transfer failed", exception);
            return new PhoneCurrencyIntegration.TransferResult(
                    PhoneCurrencyIntegration.TransferStatus.PENDING, ""
            );
        }
    }

    public static void recover(MinecraftServer server) {
        try { INTEGRATION.recover(server, PhoneBankJournal.get(server)); }
        catch (IOException | RuntimeException | LinkageError error) { PhoneBankJournal.stop(server); LOGGER.error("Phone bank recovery deferred", error); }
    }

    private static PhoneCurrencyIntegration loadIntegration() {
        if (PROVIDER == PhoneCurrencyProvider.NONE) return new UnavailableIntegration("MOD_MISSING");
        try {
            Class<?> type = Class.forName(PROVIDER.adapter);
            LOGGER.info("MPhone bank provider: {}", PROVIDER.displayName);
            return (PhoneCurrencyIntegration) type.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                 | IllegalAccessException | InvocationTargetException | LinkageError exception) {
            LOGGER.error("{} is installed, but MPhone could not load its bank bridge", PROVIDER.displayName, exception);
            return new UnavailableIntegration("ERROR");
        }
    }

    public static PhoneCurrencyIntegration.PriceQuote quote(ServerPlayer player, String currencyId, long amount) {
        try { return INTEGRATION.quote(player, currencyId, amount); }
        catch (RuntimeException | LinkageError error) { return new PhoneCurrencyIntegration.PriceQuote(false, "", ""); }
    }

    private static PhoneCurrencyIntegration.Snapshot unavailable(String status) {
        return new PhoneCurrencyIntegration.Snapshot(false, status, PROVIDER.displayName, "", List.of());
    }

    private record UnavailableIntegration(String status) implements PhoneCurrencyIntegration {
        @Override
        public Snapshot snapshot(ServerPlayer player) {
            return unavailable(status);
        }

        @Override
        public TransferResult transfer(ServerPlayer sender, UUID targetId, String currencyId, long coreAmount) {
            return new TransferResult(TransferStatus.UNAVAILABLE, "");
        }
    }
}

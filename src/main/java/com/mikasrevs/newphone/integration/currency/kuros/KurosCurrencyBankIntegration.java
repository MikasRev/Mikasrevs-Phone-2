package com.mikasrevs.newphone.integration.currency.kuros;

import com.mikasrevs.newphone.integration.currency.PhoneCurrencyAmounts;
import com.mikasrevs.newphone.integration.currency.PhoneCurrencyIntegration;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.*;
import com.mojang.authlib.GameProfile;
import com.mikasrevs.newphone.integration.currency.PhoneBankJournal;
import com.mikasrevs.newphone.server.data.PhonePlayerStorage;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.util.List;
import java.util.UUID;
import kuronomy.network.KuronomyModVariables;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/** Loaded reflectively only when Kuro's Economy is the selected server provider. */
public final class KurosCurrencyBankIntegration implements PhoneCurrencyIntegration {
    public static final String CURRENCY_ID = "kuronomy:balance";
    private static final String PROVIDER = "Kuro's Economy";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MINIMUM = 500L;
    public static final String MARKER = "newphone_bank_operation";
    private static final List<Unit> UNITS = List.of(new Unit("$0.01", 1), new Unit("$1", 100),
            new Unit("$5", 500), new Unit("$10", 1000), new Unit("$50", 5000),
            new Unit("$100", 10000), new Unit("$500", 50000));

    @Override
    public Snapshot snapshot(ServerPlayer player) {
        var account = account(player);
        long balance = account == null ? -1 : PhoneCurrencyAmounts.cents(account.bal);
        if (account == null || account.sus || balance < 0)
            return new Snapshot(false, "ACCOUNT_UNAVAILABLE", PROVIDER, "", List.of());
        return new Snapshot(true, "READY", PROVIDER, player.getGameProfile().getName(),
                List.of(new Balance(CURRENCY_ID, "$", PhoneCurrencyAmounts.dollars(balance), balance, UNITS)),
                MINIMUM, true);
    }

    @Override
    public PriceQuote quote(ServerPlayer player, String currencyId, long amount) {
        return CURRENCY_ID.equals(currencyId) && amount >= 0
                ? new PriceQuote(true, "$", PhoneCurrencyAmounts.dollars(amount))
                : new PriceQuote(false, "", "");
    }

    @Override
    public TransferResult transfer(ServerPlayer sender, UUID targetId, String currencyId, long coreAmount) {
        // Direct callers still go through the shared durable transaction boundary.
        return com.mikasrevs.newphone.integration.currency.PhoneCurrencyManager.transfer(sender, targetId, currencyId, coreAmount);
    }

    @Override
    public TransferResult transfer(ServerPlayer sender, UUID targetId, String currencyId, long coreAmount,
            PhoneBankJournal journal, PhoneBankJournal.Entry entry, boolean fresh) throws IOException {
        if (sender == null || sender.getServer() == null || !sender.getServer().isSameThread())
            return result(TransferStatus.ACCOUNT_UNAVAILABLE);
        if (targetId == null || targetId.equals(sender.getUUID()) || !CURRENCY_ID.equals(currencyId)
                || coreAmount < MINIMUM) return result(TransferStatus.INVALID_AMOUNT);
        var target = sender.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) return result(TransferStatus.TARGET_OFFLINE);
        var source = account(sender);
        var destination = account(target);
        if (source == null || destination == null || source == destination || source.sus || destination.sus
                || PhoneCurrencyAmounts.cents(source.bal) < 0 || PhoneCurrencyAmounts.cents(destination.bal) < 0)
            return result(TransferStatus.ACCOUNT_UNAVAILABLE);

        BigDecimal decimalAmount = BigDecimal.valueOf(coreAmount, 2);
        double amount = decimalAmount.doubleValue();
        double beforeSource = source.bal;
        double beforeTarget = destination.bal;
        BigDecimal sourceAmount = PhoneCurrencyAmounts.decimal(beforeSource);
        if (sourceAmount.compareTo(decimalAmount) < 0) return result(TransferStatus.INSUFFICIENT_FUNDS);
        double afterSource = sourceAmount.subtract(decimalAmount).doubleValue();
        double afterTarget = PhoneCurrencyAmounts.decimal(beforeTarget).add(decimalAmount).doubleValue();
        // Reject values where double precision would lose even a fraction of one cent before paying.
        if (PhoneCurrencyAmounts.cents(afterTarget) < 0 || Math.ulp(beforeSource) > 0.000001
                || Math.ulp(afterTarget) > 0.000001 || Math.abs((afterTarget - beforeTarget) - amount) > 0.000001
                || Math.abs((beforeSource - afterSource) - amount) > 0.000001)
            return result(TransferStatus.INVALID_AMOUNT);

        CompoundTag plan = new CompoundTag();
        plan.putDouble("SourceAfter", afterSource); plan.putDouble("TargetAfter", afterTarget);
        plan.putString("SourceName", sender.getGameProfile().getName()); plan.putString("TargetName", target.getGameProfile().getName());
        plan.put("SourceBackup", sender.saveWithoutId(new CompoundTag()));
        plan.put("TargetBackup", target.saveWithoutId(new CompoundTag()));
        journal.prepare(entry, plan); // Durable decision before either native balance is touched.
        finish(sender.server, journal, entry);
        return entry.result();
    }

    @Override
    public void recover(MinecraftServer server, PhoneBankJournal journal) throws IOException {
        for (var entry : journal.pending()) {
            if (entry.provider.equals("KUROS") && !entry.plan().isEmpty()) finish(server, journal, entry);
        }
    }

    private static void finish(MinecraftServer server, PhoneBankJournal journal, PhoneBankJournal.Entry entry) throws IOException {
        CompoundTag plan = entry.plan();
        ServerPlayer source = restore(server, entry.sender, plan.getString("SourceName"), plan.getCompound("SourceBackup"));
        ServerPlayer target = restore(server, entry.target, plan.getString("TargetName"), plan.getCompound("TargetBackup"));
        apply(source, entry.key(), plan.getDouble("SourceAfter"));
        apply(target, entry.key(), plan.getDouble("TargetAfter"));
        PhonePlayerStorage.save(source, MARKER, entry.key());
        PhonePlayerStorage.save(target, MARKER, entry.key());
        if (source.connection != null) sync(account(source), source);
        if (target.connection != null) sync(account(target), target);
        journal.complete(entry, new TransferResult(TransferStatus.SUCCESS, PhoneCurrencyAmounts.dollars(entry.amount)));
    }

    private static ServerPlayer restore(MinecraftServer server, UUID id, String name, CompoundTag backup) throws IOException {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online;
        Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
        CompoundTag saved = Files.exists(file) ? NbtIo.readCompressed(file.toFile()) : backup.copy();
        if (server.getSingleplayerProfile() != null && id.equals(server.getSingleplayerProfile().getId())) {
            Path level = server.getWorldPath(LevelResource.ROOT).resolve("level.dat");
            CompoundTag host = NbtIo.readCompressed(level.toFile()).getCompound("Data").getCompound("Player");
            if (!host.isEmpty()) saved = host;
        }
        if (saved.isEmpty()) throw new IOException("Missing player backup for bank recovery");
        ServerPlayer player = new ServerPlayer(server, server.overworld(), new GameProfile(id, name));
        player.load(saved);
        return player;
    }

    private static void apply(ServerPlayer player, String operation, double balance) throws IOException {
        var account = account(player);
        if (account == null || !Double.isFinite(balance) || balance < 0)
            throw new IOException("Native Kuro account unavailable during recovery");
        if (!operation.equals(player.getPersistentData().getString(MARKER))) {
            account.bal = balance;
            player.getPersistentData().putString(MARKER, operation);
        }
    }

    private static KuronomyModVariables.PlayerVariables account(ServerPlayer player) {
        return player == null ? null : player.getCapability(KuronomyModVariables.PLAYER_VARIABLES_CAPABILITY)
                .resolve().orElse(null);
    }

    private static void sync(KuronomyModVariables.PlayerVariables account, ServerPlayer player) {
        try { account.syncPlayerVariables(player); }
        catch (RuntimeException | LinkageError exception) {
            // A notification failure cannot turn an already committed payment into a failed/retryable payment.
            LOGGER.warn("Kuro account sync failed after a completed MPhone transfer for {}", player.getUUID(), exception);
        }
    }

    private static TransferResult result(TransferStatus status) { return new TransferResult(status, ""); }
}

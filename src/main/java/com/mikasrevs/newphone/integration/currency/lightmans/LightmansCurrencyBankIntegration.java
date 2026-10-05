package com.mikasrevs.newphone.integration.currency.lightmans;

import com.mikasrevs.newphone.integration.currency.PhoneCurrencyIntegration;
import com.mikasrevs.newphone.integration.currency.PhoneBankJournal;
import com.mikasrevs.newphone.server.data.PhoneAtomicStorage;
import io.github.lightman314.lightmanscurrency.common.data.types.BankDataCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.storage.LevelResource;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.file.*;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import io.github.lightman314.lightmanscurrency.api.money.bank.BankAPI;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.ChainData;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.coin.CoinEntry;
import io.github.lightman314.lightmanscurrency.api.money.types.builtin.CoinCurrencyType;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyStorage;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

/** Direct API adapter. This class is loaded reflectively only when Lightman's Currency is present. */
public final class LightmansCurrencyBankIntegration implements PhoneCurrencyIntegration {
    private static final int MAX_BALANCES = 12;
    private static final int MAX_UNITS = 12;

    @Override
    public Snapshot snapshot(ServerPlayer player) {
        if (player == null) return unavailable("ACCOUNT_UNAVAILABLE");
        BankReference reference = PlayerBankReference.of(player);
        IBankAccount account = reference == null ? null : reference.get();
        if (account == null) return unavailable("ACCOUNT_UNAVAILABLE");

        List<MoneyValue> values = new ArrayList<>(account.getMoneyStorage().allValues());
        values.removeIf(value -> value == null || value.isEmpty() || value.isFree() || value.isInvalid());
        boolean hasMainBalance = values.stream().anyMatch(LightmansCurrencyBankIntegration::isMainChain);
        values.sort(Comparator
                .comparing((MoneyValue value) -> !isMainChain(value))
                .thenComparing(value -> balanceName(value).toLowerCase()));

        List<Balance> balances = new ArrayList<>();
        if (!hasMainBalance) balances.add(emptyCoinBalance(CoinAPI.MAIN_CHAIN));
        for (MoneyValue value : values) {
            if (balances.size() >= MAX_BALANCES) break;
            balances.add(new Balance(
                    value.getUniqueName(),
                    balanceName(value),
                    formatValue(value),
                    Math.max(0L, value.getCoreValue()),
                    unitsFor(value)
            ));
        }
        return new Snapshot(true, "READY", "Lightman's Currency",
                plain(account.getName()), balances);
    }

    @Override
    public TransferResult transfer(ServerPlayer sender, UUID targetId, String currencyId, long coreAmount) {
        return com.mikasrevs.newphone.integration.currency.PhoneCurrencyManager.transfer(sender,targetId,currencyId,coreAmount);
    }

    @Override
    public TransferResult transfer(ServerPlayer sender, UUID targetId, String currencyId, long coreAmount,
            PhoneBankJournal journal, PhoneBankJournal.Entry entry, boolean fresh) throws IOException {
        if (!fresh) return new TransferResult(TransferStatus.PENDING, "");
        return transferNative(sender,targetId,currencyId,coreAmount,entry.key());
    }

    @Override
    public void recover(MinecraftServer server, PhoneBankJournal journal) throws IOException {
        if (journal.pending().stream().noneMatch(e -> e.provider.equals("LIGHTMANS"))) return;
        Path file = bankFile(server);
        if (!Files.exists(file)) return;
        CompoundTag root = NbtIo.readCompressed(file.toFile());
        for (var entry : journal.pending()) {
            if (entry.provider.equals("LIGHTMANS") && entry.key().equals(root.getString("MPhoneLastTransfer")))
                journal.complete(entry,new TransferResult(TransferStatus.SUCCESS,root.getString("MPhoneLastTransferText")));
        }
    }

    private static Path bankFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(BankDataCache.TYPE.fileName + ".dat");
    }

    private TransferResult transferNative(ServerPlayer sender, UUID targetId, String currencyId, long coreAmount, String operation) {
        if (sender == null || targetId == null || currencyId == null || currencyId.isBlank() || coreAmount <= 0L) {
            return new TransferResult(TransferStatus.INVALID_AMOUNT, "");
        }

        BankReference sourceReference = PlayerBankReference.of(sender);
        IBankAccount source = sourceReference == null ? null : sourceReference.get();
        IBankAccount destination = PlayerBankReference.of(targetId).get();
        if (source == null || destination == null) {
            return new TransferResult(TransferStatus.ACCOUNT_UNAVAILABLE, "");
        }

        MoneyStorage storage = source.getMoneyStorage();
        MoneyValue available = storage.valueOf(currencyId);
        if (available == null || available.isEmpty() || available.isInvalid()) {
            return new TransferResult(TransferStatus.INSUFFICIENT_FUNDS, "");
        }

        MoneyValue requested = available.fromCoreValue(coreAmount);
        if (requested == null || requested.isEmpty() || requested.isInvalid()
                || requested.getCoreValue() != coreAmount) {
            return new TransferResult(TransferStatus.INVALID_AMOUNT, "");
        }
        String amountText = plain(requested.getText());
        if (!storage.containsValue(requested)) {
            return new TransferResult(TransferStatus.INSUFFICIENT_FUNDS, amountText);
        }

        long before = available.getCoreValue();
        MoneyValue destinationValue = destination.getMoneyStorage().valueOf(currencyId);
        long destinationBefore = destinationValue == null || destinationValue.isEmpty() ? 0L : destinationValue.getCoreValue();
        if (destinationBefore < 0L || destinationBefore > Long.MAX_VALUE - coreAmount)
            return new TransferResult(TransferStatus.INVALID_AMOUNT, amountText);
        BankAPI.getApi().BankTransfer(sender, sourceReference, requested, destination);
        MoneyValue remaining = source.getMoneyStorage().valueOf(currencyId);
        long after = remaining == null || remaining.isEmpty() ? 0L : Math.max(0L, remaining.getCoreValue());
        MoneyValue destinationAfterValue = destination.getMoneyStorage().valueOf(currencyId);
        long destinationAfter = destinationAfterValue == null || destinationAfterValue.isEmpty() ? 0L : destinationAfterValue.getCoreValue();
        if (before - after != coreAmount || destinationAfter - destinationBefore != coreAmount) {
            return new TransferResult(TransferStatus.FAILED, amountText);
        }
        // Persist the native shared bank file before reporting a durable successful receipt.
        CompoundTag nativeData = new CompoundTag();
        BankDataCache.TYPE.get(false).save(nativeData);
        CompoundTag root = new CompoundTag(); root.put("data", nativeData);
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.putString("MPhoneLastTransfer",operation); root.putString("MPhoneLastTransferText",amountText);
        try { PhoneAtomicStorage.write(bankFile(sender.server), root); }
        catch (IOException error) { throw new UncheckedIOException(error); }
        return new TransferResult(TransferStatus.SUCCESS, amountText);
    }

    @Override
    public PriceQuote quote(ServerPlayer player, String currencyId, long amount) {
        if (player == null || currencyId == null || amount < 0) return new PriceQuote(false, "", "");
        IBankAccount account = PlayerBankReference.of(player).get();
        if (account == null) return new PriceQuote(false, "", "");
        MoneyValue prototype = account.getMoneyStorage().valueOf(currencyId);
        if (prototype == null || prototype.isEmpty() || prototype.isInvalid()) {
            prototype = null;
            for (ChainData chain : CoinAPI.getApi().AllChainData()) {
                MoneyValue candidate = CoinValue.fromNumber(chain, 1L);
                if (candidate != null && !candidate.isInvalid() && candidate.getUniqueName().equals(currencyId)) {
                    prototype = candidate; break;
                }
            }
        }
        if (prototype == null) return new PriceQuote(false, "", "");
        String name = balanceName(prototype);
        if (amount == 0L) return new PriceQuote(true, name, "0 " + name);
        MoneyValue value = prototype.fromCoreValue(amount);
        if (value == null || value.isInvalid() || value.getCoreValue() != amount) return new PriceQuote(false, "", "");
        return new PriceQuote(true, name, formatValue(value));
    }

    private static Balance emptyCoinBalance(String chainId) {
        ChainData chain = CoinAPI.getApi().ChainData(chainId);
        String name = chain == null
                ? readableLabel("", chainId, "Coins")
                : chainName(chain, chainId);
        List<Unit> units = chain == null
                ? List.of(new Unit("Coin", 1L))
                : unitsFor(chain);
        String id = MoneyValue.generateCustomUniqueName(CoinCurrencyType.TYPE, chainId);
        return new Balance(id, name, "0", 0L, units);
    }

    private static boolean isMainChain(MoneyValue value) {
        return value instanceof CoinValue coinValue && CoinAPI.MAIN_CHAIN.equals(coinValue.getChain());
    }

    private static String balanceName(MoneyValue value) {
        if (value instanceof CoinValue coinValue) {
            ChainData chain = CoinAPI.getApi().ChainData(coinValue.getChain());
            if (chain == null) return "Coins";
            return chainName(chain, coinValue.getChain());
        }
        String type = value.getCurrency() == null ? "" : value.getCurrency().getType().toString();
        return readableLabel(type, type, "Currency");
    }

    private static String formatValue(MoneyValue value) {
        String formatted = plain(value.getText(Component.literal("0")));
        return formatted.isEmpty() || formatted.equalsIgnoreCase("null")
                ? Long.toString(Math.max(0L, value.getCoreValue()))
                : formatted;
    }

    private static List<Unit> unitsFor(MoneyValue value) {
        if (!(value instanceof CoinValue coinValue)) return List.of(new Unit("Unit", 1L));
        ChainData chain = CoinAPI.getApi().ChainData(coinValue.getChain());
        if (chain == null) return List.of(new Unit("Coin", 1L));
        return unitsFor(chain);
    }

    private static List<Unit> unitsFor(ChainData chain) {
        List<CoinEntry> entries = new ArrayList<>(chain.getCoreChain());
        entries.sort(Comparator.comparingLong(CoinEntry::getCoreValue));
        List<Unit> units = new ArrayList<>();
        for (CoinEntry entry : entries) {
            if (entry == null || entry.getCoreValue() <= 0L || units.size() >= MAX_UNITS) continue;
            String itemId = entry.getCoin() == null || ForgeRegistries.ITEMS.getKey(entry.getCoin()) == null
                    ? "" : ForgeRegistries.ITEMS.getKey(entry.getCoin()).getPath();
            units.add(new Unit(readableLabel(plain(entry.getName()), itemId, "Unit"), entry.getCoreValue()));
        }
        return units.isEmpty() ? List.of(new Unit("Coin", 1L)) : List.copyOf(units);
    }

    private static String chainName(ChainData chain, String chainId) {
        return readableLabel(plain(chain.getDisplayName()), chainId, "Coins");
    }

    private static String readableLabel(String display, String identifier, String fallback) {
        String shown = display == null ? "" : display.trim();
        if (!shown.isEmpty() && !looksTechnical(shown)) return shown;
        String id = identifier == null ? "" : identifier.trim();
        if (id.isEmpty() || id.equalsIgnoreCase("null")) return fallback;
        int namespace = id.indexOf(':');
        if (namespace >= 0 && namespace + 1 < id.length()) id = id.substring(namespace + 1);
        int slash = id.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < id.length()) id = id.substring(slash + 1);
        int dot = id.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < id.length()) id = id.substring(dot + 1);
        if (id.isBlank() || id.equalsIgnoreCase("null") || id.equalsIgnoreCase("empty")) return fallback;
        if (id.equalsIgnoreCase(CoinAPI.MAIN_CHAIN) || id.equalsIgnoreCase("main")) return "Coins";
        if (id.startsWith("coin_") && id.length() > 5) id = id.substring(5) + "_coin";
        String[] words = id.replace('-', '_').split("_+");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (label.length() > 0) label.append(' ');
            label.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) label.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return label.length() == 0 ? fallback : label.toString();
    }

    private static boolean looksTechnical(String value) {
        return value.equalsIgnoreCase("null") || value.indexOf(':') >= 0
                || (value.indexOf(' ') < 0 && value.indexOf('.') >= 0);
    }

    private static String plain(Component component) {
        return component == null ? "" : component.getString().replace('\n', ' ').trim();
    }

    private static Snapshot unavailable(String status) {
        return new Snapshot(false, status, "Lightman's Currency", "", List.of());
    }
}

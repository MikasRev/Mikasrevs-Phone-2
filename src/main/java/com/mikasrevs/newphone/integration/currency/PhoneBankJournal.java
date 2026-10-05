package com.mikasrevs.newphone.integration.currency;

import com.mikasrevs.newphone.server.data.PhoneAtomicStorage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Transaction receipts only; all spendable balances stay in the selected economy mod. */
public final class PhoneBankJournal {
    private static final Map<MinecraftServer, PhoneBankJournal> STORES = new WeakHashMap<>();
    private final Path directory;
    private final Map<String, Entry> pending = new LinkedHashMap<>();
    private long nextPrune;
    private static final long RECEIPT_TTL_MS = 86_400_000L;

    private PhoneBankJournal(MinecraftServer server) throws IOException {
        directory = server.getWorldPath(LevelResource.ROOT).resolve("data/newphone_bank_operations");
        Files.createDirectories(directory);
        try (var files = Files.newDirectoryStream(directory, "*.dat")) {
            for (Path file : files) {
                Entry entry = read(file);
                if (entry.result == null) pending.put(entry.key(), entry);
            }
        }
        prune();
    }

    public static PhoneBankJournal get(MinecraftServer server) throws IOException {
        PhoneBankJournal store = STORES.get(server);
        if (store == null) { store = new PhoneBankJournal(server); STORES.put(server, store); }
        return store;
    }
    public static void stop(MinecraftServer server) { STORES.remove(server); }

    public Entry find(UUID sender, String request) throws IOException {
        String key = key(sender, request);
        Entry entry = pending.get(key);
        if (entry != null) return entry;
        Path file = directory.resolve(key + ".dat");
        if (!Files.exists(file)) return null;
        entry = read(file);
        if (entry.result == null) pending.put(key, entry);
        return entry;
    }

    public Entry begin(UUID sender, UUID target, String request, String provider, String currency, long amount) throws IOException {
        if (System.currentTimeMillis() >= nextPrune) prune();
        Entry entry = new Entry(sender, target, UUID.fromString(request).toString(), provider, currency, amount);
        persist(entry); pending.put(entry.key(), entry); return entry;
    }

    public void prepare(Entry entry, CompoundTag plan) throws IOException {
        CompoundTag data = entry.save(); data.put("Plan", plan.copy());
        PhoneAtomicStorage.write(directory.resolve(entry.key() + ".dat"), data);
        entry.plan = plan.copy();
    }

    public void complete(Entry entry, PhoneCurrencyIntegration.TransferResult result) throws IOException {
        CompoundTag data = entry.save();
        data.putString("Status", result.status().name()); data.putString("Text", result.amountText());
        data.remove("Plan");
        PhoneAtomicStorage.write(directory.resolve(entry.key() + ".dat"), data);
        entry.result = result; entry.plan = new CompoundTag(); pending.remove(entry.key());
    }

    public List<Entry> pending() { return List.copyOf(pending.values()); }
    private void persist(Entry entry) throws IOException { PhoneAtomicStorage.write(directory.resolve(entry.key() + ".dat"), entry.save()); }

    private void prune() throws IOException {
        long now = System.currentTimeMillis();
        try (var files = Files.newDirectoryStream(directory, "*.dat")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!pending.containsKey(name.substring(0,name.length()-4))
                        && now - Files.getLastModifiedTime(file).toMillis() > RECEIPT_TTL_MS) Files.delete(file);
            }
        }
        nextPrune = now + 3_600_000L;
    }

    private static String key(UUID sender, String request) { return sender + "_" + UUID.fromString(request); }
    private static Entry read(Path file) throws IOException {
        try {
            CompoundTag data = NbtIo.readCompressed(file.toFile());
            if (data.getInt("Version") != 1) throw new IllegalArgumentException("Unknown bank receipt version");
            Entry entry = new Entry(data.getUUID("Sender"), data.getUUID("Target"),
                    UUID.fromString(data.getString("Request")).toString(), data.getString("Provider"), data.getString("Currency"), data.getLong("Amount"));
            if (entry.amount <= 0 || entry.currency.isBlank() || !file.getFileName().toString().equals(entry.key() + ".dat"))
                throw new IllegalArgumentException("Invalid bank receipt");
            entry.plan = data.getCompound("Plan");
            if (data.contains("Status")) entry.result = new PhoneCurrencyIntegration.TransferResult(
                    PhoneCurrencyIntegration.TransferStatus.valueOf(data.getString("Status")), data.getString("Text"));
            return entry;
        } catch (RuntimeException error) { throw new IOException("Invalid bank receipt: " + file, error); }
    }

    public static final class Entry {
        public final UUID sender, target;
        public final String request, provider, currency;
        public final long amount;
        private CompoundTag plan = new CompoundTag();
        private PhoneCurrencyIntegration.TransferResult result;
        private Entry(UUID sender, UUID target, String request, String provider, String currency, long amount) {
            this.sender=sender; this.target=target; this.request=request; this.provider=provider; this.currency=currency; this.amount=amount;
        }
        public String key() { return PhoneBankJournal.key(sender, request); }
        public CompoundTag plan() { return plan.copy(); }
        public PhoneCurrencyIntegration.TransferResult result() { return result; }
        private CompoundTag save() {
            CompoundTag data = new CompoundTag(); data.putInt("Version", 1);
            data.putUUID("Sender", sender); data.putUUID("Target", target); data.putString("Request", request);
            data.putString("Provider", provider); data.putString("Currency", currency); data.putLong("Amount", amount);
            if (!plan.isEmpty()) data.put("Plan", plan.copy());
            if (result != null) { data.putString("Status", result.status().name()); data.putString("Text", result.amountText()); }
            return data;
        }
    }
}

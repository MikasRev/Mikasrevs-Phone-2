package com.mikasrevs.newphone;

import com.mikasrevs.newphone.integration.currency.*;
import com.mikasrevs.newphone.integration.currency.kuros.KurosCurrencyBankIntegration;
import com.mikasrevs.newphone.server.data.*;
import java.util.*;
import kuronomy.network.KuronomyModVariables;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class KuroDurabilityChecks {
    private static final String MARKER = KurosCurrencyBankIntegration.MARKER;
    public static void run(GameTestHelper h, ServerPlayer a, ServerPlayer b) throws Exception {
        account(a).bal = 14; account(b).bal = 0;
        var paid = PhoneCurrencyManager.transfer(a, b.getUUID(), KurosCurrencyBankIntegration.CURRENCY_ID, 503);
        h.assertTrue(paid.success() && PhoneCurrencyManager.snapshot(a).balances().get(0).coreValue() == 897,
                "14 - 5.03 still loses a displayed cent");
        h.assertTrue(PhoneCurrencyManager.transfer(a, b.getUUID(), KurosCurrencyBankIntegration.CURRENCY_ID, 897).success()
                && account(a).bal == 0, "Exact fractional remainder cannot be transferred");
        for (boolean offline : List.of(false, true)) for (int phase = 0; phase < 4; phase++) {
            account(a).bal = 100; account(b).bal = 0;
            a.getPersistentData().putString(MARKER, "baseline-a"); b.getPersistentData().putString(MARKER, "baseline-b");
            PhonePlayerStorage.save(a, MARKER, "baseline-a"); PhonePlayerStorage.save(b, MARKER, "baseline-b");
            String request = UUID.randomUUID().toString();
            var journal = PhoneBankJournal.get(a.server);
            var entry = journal.begin(a.getUUID(), b.getUUID(), request, "KUROS", KurosCurrencyBankIntegration.CURRENCY_ID, 500);
            CompoundTag plan = new CompoundTag(); plan.putDouble("SourceAfter",95); plan.putDouble("TargetAfter",5);
            plan.putString("SourceName",a.getGameProfile().getName()); plan.putString("TargetName",b.getGameProfile().getName());
            plan.put("SourceBackup",a.saveWithoutId(new CompoundTag())); plan.put("TargetBackup",b.saveWithoutId(new CompoundTag()));
            journal.prepare(entry, plan);
            if ((phase & 1) != 0) { account(a).bal=95; a.getPersistentData().putString(MARKER,entry.key()); PhonePlayerStorage.save(a,MARKER,entry.key()); }
            if ((phase & 2) != 0) { account(b).bal=5; b.getPersistentData().putString(MARKER,entry.key()); PhonePlayerStorage.save(b,MARKER,entry.key()); }
            reload(a); reload(b); PhoneBankJournal.stop(a.server);
            @SuppressWarnings("unchecked") var ids = (Map<UUID,ServerPlayer>)PhoneBankGameTests.field(a.server.getPlayerList().getClass(),"playersByUUID").get(a.server.getPlayerList());
            if (offline) { ids.remove(a.getUUID()); ids.remove(b.getUUID()); }
            try { PhoneCurrencyManager.recover(a.server); }
            finally { if (offline) { ids.put(a.getUUID(),a); ids.put(b.getUUID(),b); } }
            reload(a); reload(b);
            h.assertTrue(account(a).bal==95 && account(b).bal==5, "Partial native saves not repaired: "+phase+"/"+offline);
            h.assertTrue(PhoneCurrencyManager.transfer(a,b.getUUID(),KurosCurrencyBankIntegration.CURRENCY_ID,500,request).success()
                    && account(a).bal==95 && account(b).bal==5, "Recovered request replay paid again");
        }
        // A durable player write must preserve unrelated world metadata and the host load source.
        var level = a.server.getWorldPath(LevelResource.ROOT).resolve("level.dat");
        CompoundTag originalLevel = NbtIo.readCompressed(level.toFile());
        var previousHost = a.server.getSingleplayerProfile();
        try {
            a.server.setSingleplayerProfile(a.getGameProfile());
            CompoundTag withSentinel = originalLevel.copy(); withSentinel.getCompound("Data").putString("AuditMetadata","keep");
            PhoneAtomicStorage.write(level,withSentinel);
            a.getPersistentData().putString(MARKER,"host-test"); PhonePlayerStorage.save(a,MARKER,"host-test");
            CompoundTag hostLevel = NbtIo.readCompressed(level.toFile()).getCompound("Data");
            h.assertTrue(hostLevel.getString("AuditMetadata").equals("keep")
                    && hostLevel.getCompound("Player").getCompound("ForgeData").getString(MARKER).equals("host-test"),
                    "Host player update lost world metadata or operation marker");
        } finally { a.server.setSingleplayerProfile(previousHost); PhoneAtomicStorage.write(level,originalLevel); }
        System.out.println("Kuro durability checks: exact cents/full remainder, all four player-save boundaries online/offline, replay and host level.dat passed.");
    }
    private static KuronomyModVariables.PlayerVariables account(ServerPlayer p) {
        return p.getCapability(KuronomyModVariables.PLAYER_VARIABLES_CAPABILITY).resolve().orElseThrow();
    }
    private static void reload(ServerPlayer p) throws Exception {
        p.load(NbtIo.readCompressed(p.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat").toFile()));
    }
}

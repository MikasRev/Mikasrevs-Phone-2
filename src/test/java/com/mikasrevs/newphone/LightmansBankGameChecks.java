package com.mikasrevs.newphone;

import com.mikasrevs.newphone.integration.currency.*;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.LevelResource;
import io.github.lightman314.lightmanscurrency.common.data.types.BankDataCache;
import com.mikasrevs.newphone.server.data.PhoneAtomicStorage;

/** Isolated existing provider regression; also executes when both economy mods are installed. */
public final class LightmansBankGameChecks {
    public static void run(GameTestHelper h, ServerPlayer a, ServerPlayer b) throws Exception {
        var source = PlayerBankReference.of(a).get().getMoneyStorage();
        var destination = PlayerBankReference.of(b).get().getMoneyStorage();
        var funds = CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, 10000);
        source.addValue(funds);
        String currency = funds.getUniqueName();
        long beforeSource = source.valueOf(currency).getCoreValue();
        long beforeTarget = destination.valueOf(currency).getCoreValue();
        String request = UUID.randomUUID().toString();
        var result = PhoneCurrencyManager.transfer(a, b.getUUID(), currency, 500, request);
        h.assertTrue(result.success(), "Existing LC transfer failed: " + result.status());
        h.assertTrue(source.valueOf(currency).getCoreValue() == beforeSource - 500
                && destination.valueOf(currency).getCoreValue() == beforeTarget + 500, "LC transfer changed existing ledger semantics");
        var file = a.server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(BankDataCache.TYPE.fileName+".dat");
        var persisted = NbtIo.readCompressed(file.toFile());
        var nativeState = new CompoundTag(); BankDataCache.TYPE.get(false).save(nativeState);
        h.assertTrue(persisted.getCompound("data").equals(nativeState), "Successful LC receipt preceded native bank persistence");
        var receipt = PhoneBankJournal.get(a.server).find(a.getUUID(), request);
        var receiptFile = a.server.getWorldPath(LevelResource.ROOT).resolve("data/newphone_bank_operations").resolve(receipt.key()+".dat");
        var uncertain = NbtIo.readCompressed(receiptFile.toFile()); uncertain.remove("Status"); uncertain.remove("Text");
        PhoneAtomicStorage.write(receiptFile,uncertain); PhoneBankJournal.stop(a.server);
        PhoneCurrencyManager.recover(a.server);
        h.assertTrue(PhoneBankJournal.get(a.server).find(a.getUUID(),request).result().success()
                && source.valueOf(currency).getCoreValue()==beforeSource-500, "LC native commit marker did not recover its receipt");
        String unknown = UUID.randomUUID().toString();
        PhoneBankJournal.get(a.server).begin(a.getUUID(),b.getUUID(),unknown,"LIGHTMANS",currency,500);
        PhoneBankJournal.stop(a.server);
        h.assertTrue(PhoneCurrencyManager.transfer(a,b.getUUID(),currency,500,unknown).status()==PhoneCurrencyIntegration.TransferStatus.PENDING
                && source.valueOf(currency).getCoreValue()==beforeSource-500, "Uncertain LC payment was blindly retried");
        var state = PhoneCurrencyManager.snapshot(a);
        h.assertTrue(state.minimumTransfer() == 1
                && !state.onlineRecipientRequired(), "Kuro rules leaked into LC");
        h.assertTrue(!PhoneCurrencyManager.quote(a, "kuronomy:balance", 500).valid(), "LC selected a second currency ledger");
        var rejected = PhoneCurrencyManager.transfer(a, b.getUUID(), currency, Long.MAX_VALUE);
        h.assertTrue(!rejected.success() && source.valueOf(currency).getCoreValue() == beforeSource - 500,
                "LC rejected transfer changed balance");
        System.out.println("Lightman's native checks passed: real balance, transfer, rejected amount and unchanged transfer rules.");
    }
}

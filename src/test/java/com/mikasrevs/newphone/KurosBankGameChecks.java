package com.mikasrevs.newphone;

import com.google.gson.Gson;
import com.mikasrevs.newphone.integration.currency.*;
import com.mikasrevs.newphone.integration.currency.kuros.KurosCurrencyBankIntegration;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.network.message.PhoneActionMessage;
import com.mikasrevs.newphone.server.PhoneServerService;
import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import java.util.*;
import kuronomy.network.KuronomyModVariables;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Isolated optional API references: this class is never loaded without Kuro's Economy. */
public final class KurosBankGameChecks {
    public static void run(GameTestHelper h, ServerPlayer a, ServerPlayer b) throws Exception {
        var x = a.getCapability(KuronomyModVariables.PLAYER_VARIABLES_CAPABILITY).resolve().orElseThrow();
        var y = b.getCapability(KuronomyModVariables.PLAYER_VARIABLES_CAPABILITY).resolve().orElseThrow();
        var world = KuronomyModVariables.MapVariables.get(a.level());
        double previousFee = world.fee;
        String currency = KurosCurrencyBankIntegration.CURRENCY_ID;
        try {
            x.bal = 100; y.bal = 10; x.sus = false; y.sus = false; world.fee = 10;
            var initial = PhoneCurrencyManager.snapshot(a);
            h.assertTrue(initial.balances().get(0).coreValue() == 10000
                    && initial.minimumTransfer() == 500 && initial.onlineRecipientRequired(), "Native Kuro account/rules not shared");
            h.assertTrue(PhoneCurrencyManager.quote(a, currency, 501).formatted().equals("$5.01"), "Kuro price quote changed cents");
            check(h, a, b.getUUID(), currency, 1000, PhoneCurrencyIntegration.TransferStatus.SUCCESS);
            exact(h, x.bal, 90, "Fee-free debit with native fee enabled"); exact(h, y.bal, 20, "Recipient amount");
            exact(h, world.fee, 10, "Phone changed global Kuro fee");
            double beforeX = x.bal, beforeY = y.bal;
            check(h, a, b.getUUID(), currency, 499, PhoneCurrencyIntegration.TransferStatus.INVALID_AMOUNT);
            check(h, a, a.getUUID(), currency, 500, PhoneCurrencyIntegration.TransferStatus.INVALID_AMOUNT);
            check(h, a, UUID.randomUUID(), currency, 500, PhoneCurrencyIntegration.TransferStatus.TARGET_OFFLINE);
            check(h, a, b.getUUID(), "lightmans:coins", 500, PhoneCurrencyIntegration.TransferStatus.INVALID_AMOUNT);
            y.sus = true;
            check(h, a, b.getUUID(), currency, 500, PhoneCurrencyIntegration.TransferStatus.ACCOUNT_UNAVAILABLE);
            y.sus = false; x.sus = true;
            h.assertTrue(!PhoneCurrencyManager.snapshot(a).available(), "Suspended Kuro account still visible as active");
            check(h, a, b.getUUID(), currency, 500, PhoneCurrencyIntegration.TransferStatus.ACCOUNT_UNAVAILABLE);
            x.sus = false;
            exact(h, x.bal, beforeX, "Rejected payment changed source"); exact(h, y.bal, beforeY, "Rejected payment changed recipient");
            x.bal = 5;
            check(h, a, b.getUUID(), currency, 500, PhoneCurrencyIntegration.TransferStatus.SUCCESS);
            exact(h, x.bal, 0, "Exact balance transfer charged a fee"); exact(h, y.bal, 25, "Exact balance recipient amount");
            x.bal = 4;
            check(h, a, b.getUUID(), currency, 500, PhoneCurrencyIntegration.TransferStatus.INSUFFICIENT_FUNDS);
            exact(h, x.bal, 4, "Rejected transfer mutated funds");
            x.bal = 50; y.bal = 0; world.fee = 0.1;
            check(h, a, b.getUUID(), currency, 501, PhoneCurrencyIntegration.TransferStatus.SUCCESS);
            exact(h, x.bal, 44.99, "Cent transfer charged fractional fee"); exact(h, y.bal, 5.01, "Native cent transfer");
            exact(h, world.fee, 0.1, "Phone changed fractional Kuro fee");
            world.fee = Double.NaN;
            h.assertTrue(PhoneCurrencyManager.snapshot(a).available(), "Unused native fee broke phone account");
            check(h, a, b.getUUID(), currency, 500, PhoneCurrencyIntegration.TransferStatus.SUCCESS);
            exact(h, x.bal, 39.99, "Native fee config affected phone transfer");
            h.assertTrue(Double.isNaN(world.fee), "Phone overwrote native fee config");
            world.fee = 10; x.bal = 100; y.bal = 0;
            // Exercise the shared network handler's existing replay protection, not just the adapter.
            a.getInventory().setItem(0, new ItemStack(NewPhone.Z_PHONE.get()));
            b.getInventory().setItem(0, new ItemStack(NewPhone.Z_PHONE.get()));
            var data = PhoneCommunicationSavedData.get(a.getServer());
            var contacts = data.copySnapshot();
            contacts.contacts.put(a.getUUID().toString(), new ArrayList<>(List.of(b.getUUID().toString())));
            data.replace(contacts);
            var request = new PhoneBankTransferRequest();
            request.requestId = UUID.randomUUID().toString();
            request.currencyId = currency; request.coreAmount = 500;
            var message = new PhoneActionMessage("BANK_TRANSFER", b.getUUID().toString(), new Gson().toJson(request));
            PhoneServerService.handleAction(a, message);
            exact(h, x.bal, 95, "Shared bank handler debit"); exact(h, y.bal, 5, "Shared bank handler credit");
            @SuppressWarnings("unchecked") var rate = (Map<String,Long>) PhoneBankGameTests.field(PhoneServerService.class, "LAST_ACTION_AT").get(null);
            rate.keySet().removeIf(key -> key.startsWith(a.getUUID().toString()));
            PhoneServerService.handleAction(a, message);
            exact(h, x.bal, 95, "Replay charged twice"); exact(h, y.bal, 5, "Replay credited twice");
            var saved = a.saveWithoutId(new CompoundTag());
            var restored = new ServerPlayer(a.getServer(), a.serverLevel(), a.getGameProfile());
            restored.load(saved);
            var reloaded = restored.getCapability(KuronomyModVariables.PLAYER_VARIABLES_CAPABILITY).resolve().orElseThrow();
            exact(h, reloaded.bal, 95, "Vanilla player save/load lost Kuro balance");
            exact(h, world.fee, 10, "Network handler changed global Kuro fee");
            KuroDurabilityChecks.run(h, a, b);
            System.out.println("Kuro native checks: fee-free payments with native fee enabled, exact funds, cents, minimum, offline, suspension, insufficient funds, server replay and player NBT passed.");
        } finally { world.fee = previousFee; }
    }

    private static void check(GameTestHelper h, ServerPlayer a, UUID target, String currency, long amount,
                              PhoneCurrencyIntegration.TransferStatus expected) {
        var actual = PhoneCurrencyManager.transfer(a, target, currency, amount);
        h.assertTrue(actual.status() == expected, "Kuro transfer expected " + expected + ", got " + actual.status());
    }
    private static void exact(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 0.000001, message + ": " + actual + " != " + expected);
    }
}

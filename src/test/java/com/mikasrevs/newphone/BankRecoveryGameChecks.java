package com.mikasrevs.newphone;

import com.google.gson.Gson;
import com.mikasrevs.newphone.integration.currency.*;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.network.message.PhoneActionMessage;
import com.mikasrevs.newphone.server.PhoneServerService;
import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import java.util.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class BankRecoveryGameChecks {
    static void run(GameTestHelper h, ServerPlayer a, ServerPlayer b) throws Exception {
        var balance = PhoneCurrencyManager.snapshot(a).balances().stream().filter(v -> v.coreValue() >= 500).findFirst().orElseThrow();
        a.getInventory().setItem(0, new ItemStack(NewPhone.Z_PHONE.get()));
        var data = PhoneCommunicationSavedData.get(a.server);
        var contacts = data.copySnapshot();
        contacts.contacts.put(a.getUUID().toString(), new ArrayList<>(List.of(b.getUUID().toString()))); data.replace(contacts);
        long before = balance.coreValue();
        var request = new PhoneBankTransferRequest(UUID.randomUUID().toString(), balance.id(), 500);
        var message = new PhoneActionMessage("BANK_TRANSFER", b.getUUID().toString(), new Gson().toJson(request));
        PhoneServerService.handleAction(a, message);
        h.assertTrue(value(a, balance.id()) == before - 500, "Shared payment setup failed");
        var removedContact = data.copySnapshot(); removedContact.contacts.get(a.getUUID().toString()).clear(); data.replace(removedContact);
        // Lose all in-memory receipt state, then use the same real network handler and request ID.
        @SuppressWarnings("unchecked") var memory = (Map<Object,?>) PhoneBankGameTests.field(PhoneServerService.class,"BANK_REQUEST_RESULTS").get(null);
        memory.remove(a.server); PhoneBankJournal.stop(a.server);
        @SuppressWarnings("unchecked") var rate = (Map<String,Long>) PhoneBankGameTests.field(PhoneServerService.class,"LAST_ACTION_AT").get(null);
        rate.keySet().removeIf(key -> key.startsWith(a.getUUID().toString()));
        PhoneServerService.handleAction(a, message);
        h.assertTrue(value(a, balance.id()) == before - 500, "Disk receipt replay charged twice");
        var replies = (Map<?,?>)memory.get(a.server);
        h.assertTrue(replies != null && String.valueOf(replies.get(a.getUUID()+"|"+request.requestId)).contains("resultCode=SENT"),
                "Completed disk receipt was revalidated as a new payment after contact removal");
        var receipt = PhoneBankJournal.get(a.server).find(a.getUUID(), request.requestId);
        h.assertTrue(receipt.result() != null && receipt.result().success(), "Terminal receipt did not survive journal reload");
        h.assertTrue(PhoneCurrencyManager.transfer(a, b.getUUID(), balance.id(), 501, request.requestId).status()
                == PhoneCurrencyIntegration.TransferStatus.INVALID_AMOUNT, "Changed payload reused an existing payment ID");
        System.out.println("Bank recovery checks: shared handler replay after RAM/journal reload and request binding passed.");
    }
    private static long value(ServerPlayer a, String id) {
        return PhoneCurrencyManager.snapshot(a).balances().stream().filter(v -> v.id().equals(id)).mapToLong(v -> v.coreValue()).findFirst().orElse(0);
    }
}

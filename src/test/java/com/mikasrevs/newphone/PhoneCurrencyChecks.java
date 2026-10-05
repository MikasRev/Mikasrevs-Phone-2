package com.mikasrevs.newphone;

import com.mikasrevs.newphone.integration.currency.PhoneCurrencyAmounts;
import com.mikasrevs.newphone.integration.currency.PhoneCurrencyProvider;
import com.mikasrevs.newphone.integration.currency.PhoneCurrencyIntegration;
import java.util.List;

final class PhoneCurrencyChecks {
    static void run() {
        for (int mask = 0; mask < 4; mask++) {
            final int installed = mask;
            var selected = PhoneCurrencyProvider.select(id -> id.equals("lightmanscurrency")
                    ? (installed & 1) != 0 : id.equals("kuronomy") && (installed & 2) != 0);
            var expected = (mask & 1) != 0 ? PhoneCurrencyProvider.LIGHTMANS
                    : (mask & 2) != 0 ? PhoneCurrencyProvider.KUROS : PhoneCurrencyProvider.NONE;
            require(selected == expected, "Optional provider matrix " + mask);
        }
        require(PhoneCurrencyAmounts.cents(25.999) == 2599, "Fractional balance must not invent funds");
        require(PhoneCurrencyAmounts.cents(14 - 5.03) == 897, "Binary subtraction lost one cent");
        require(PhoneCurrencyAmounts.decimal(25.999).subtract(java.math.BigDecimal.valueOf(500, 2))
                .compareTo(new java.math.BigDecimal("20.999")) == 0, "Real sub-cent funds were rounded away");
        for (int dollars = 14; dollars < 70; dollars++) for (int cents = 500; cents <= dollars * 100; cents++)
            require(PhoneCurrencyAmounts.cents(dollars - java.math.BigDecimal.valueOf(cents, 2).doubleValue())
                    == dollars * 100 - cents, "Cent arithmetic mismatch: " + dollars + "/" + cents);
        var firstWorld = java.nio.file.Path.of("build/saves/world-one");
        var secondWorld = java.nio.file.Path.of("build/saves/world-two");
        require(!com.mikasrevs.newphone.integration.currency.PhoneBankScope.local(firstWorld)
                .equals(com.mikasrevs.newphone.integration.currency.PhoneBankScope.local(secondWorld)), "Different worlds share bank scope");
        require(com.mikasrevs.newphone.integration.currency.PhoneBankScope.local(firstWorld)
                .equals(com.mikasrevs.newphone.integration.currency.PhoneBankScope.local(firstWorld.resolve("nested/.."))), "Same world path changes bank scope");
        require(PhoneCurrencyAmounts.cents(Double.POSITIVE_INFINITY) == -1, "Infinite balance");
        require(PhoneCurrencyAmounts.cents(-0.01) == -1, "Negative balance");
        require(PhoneCurrencyAmounts.dollars(501).equals("$5.01"), "Decimal currency formatting");
        var legacy = new PhoneCurrencyIntegration.Snapshot(true, "READY", "Lightman's Currency", "Test", List.of());
        require(legacy.minimumTransfer() == 1
                && !legacy.onlineRecipientRequired(), "Lightman's transfer rules changed");
        System.out.println("Phone currency checks passed (optional providers and minor units).");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

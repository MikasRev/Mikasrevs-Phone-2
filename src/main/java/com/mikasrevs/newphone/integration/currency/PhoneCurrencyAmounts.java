package com.mikasrevs.newphone.integration.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Integer minor units on the wire; the provider remains responsible for its actual ledger. */
public final class PhoneCurrencyAmounts {
    private PhoneCurrencyAmounts() { }

    public static long cents(double dollars) {
        if (!Double.isFinite(dollars) || dollars < 0) return -1;
        try {
            return decimal(dollars).movePointRight(2)
                    .setScale(0, RoundingMode.DOWN).longValueExact();
        } catch (ArithmeticException exception) { return -1; }
    }

    public static BigDecimal decimal(double dollars) {
        BigDecimal value = BigDecimal.valueOf(dollars);
        BigDecimal cents = value.setScale(2, RoundingMode.HALF_UP);
        // Correct binary arithmetic noise without rounding genuine sub-cent native balances.
        return value.subtract(cents).abs().doubleValue() <= Math.max(Math.ulp(dollars) * 2, 1e-12)
                ? cents : value;
    }

    public static String dollars(long cents) {
        return "$" + BigDecimal.valueOf(cents, 2).toPlainString();
    }

}

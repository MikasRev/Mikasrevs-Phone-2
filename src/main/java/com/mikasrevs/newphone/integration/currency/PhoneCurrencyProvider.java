package com.mikasrevs.newphone.integration.currency;

import java.util.function.Predicate;

/** Selection affects the bank adapter only; existing ledgers are never migrated or merged. */
public enum PhoneCurrencyProvider {
    LIGHTMANS("lightmanscurrency", "Lightman's Currency", "lightmans.LightmansCurrencyBankIntegration"),
    KUROS("kuronomy", "Kuro's Economy", "kuros.KurosCurrencyBankIntegration"),
    NONE("", "", "");

    public final String modId;
    public final String displayName;
    public final String adapter;

    PhoneCurrencyProvider(String modId, String displayName, String adapter) {
        this.modId = modId;
        this.displayName = displayName;
        this.adapter = adapter.isEmpty() ? "" : "com.mikasrevs.newphone.integration.currency." + adapter;
    }

    public static PhoneCurrencyProvider select(Predicate<String> loaded) {
        if (loaded.test(LIGHTMANS.modId)) return LIGHTMANS;
        if (loaded.test(KUROS.modId)) return KUROS;
        return NONE;
    }
}

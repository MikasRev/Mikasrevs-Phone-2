package com.mikasrevs.newphone.marketplace.economy;

import com.mikasrevs.newphone.integration.currency.*;
import com.mikasrevs.newphone.marketplace.data.MarketplaceListing;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Shares the Bank's actual provider; never owns or persists an account/balance. */
public final class MarketplaceEconomyBridge {
    private MarketplaceEconomyBridge() { }
    public static PhoneCurrencyIntegration.Snapshot balance(ServerPlayer player) { return PhoneCurrencyManager.snapshot(player); }
    public static PhoneCurrencyIntegration.PriceQuote quote(ServerPlayer player,String currency,long amount) {
        return PhoneCurrencyManager.quote(player,currency,amount);
    }
    public static PhoneCurrencyIntegration.TransferResult transfer(ServerPlayer buyer,MarketplaceListing listing) {
        var state=balance(buyer);
        if(!state.available())return result(PhoneCurrencyIntegration.TransferStatus.UNAVAILABLE);
        if(listing.ownerUuid.equals(buyer.getUUID().toString()))return result(PhoneCurrencyIntegration.TransferStatus.INVALID_AMOUNT);
        if(!quote(buyer,listing.currencyId,listing.price).valid())return result(PhoneCurrencyIntegration.TransferStatus.INVALID_AMOUNT);
        if(listing.price==0)return new PhoneCurrencyIntegration.TransferResult(PhoneCurrencyIntegration.TransferStatus.SUCCESS,listing.priceText);
        long funds=state.balances().stream().filter(b->b.id().equals(listing.currencyId)).mapToLong(PhoneCurrencyIntegration.Balance::coreValue).findFirst().orElse(0);
        if(listing.price<state.minimumTransfer())return result(PhoneCurrencyIntegration.TransferStatus.INVALID_AMOUNT);
        if(funds<listing.price)return result(PhoneCurrencyIntegration.TransferStatus.INSUFFICIENT_FUNDS);
        return PhoneCurrencyManager.transfer(buyer,UUID.fromString(listing.ownerUuid),listing.currencyId,listing.price);
    }
    private static PhoneCurrencyIntegration.TransferResult result(PhoneCurrencyIntegration.TransferStatus status) {
        return new PhoneCurrencyIntegration.TransferResult(status,"");
    }
}

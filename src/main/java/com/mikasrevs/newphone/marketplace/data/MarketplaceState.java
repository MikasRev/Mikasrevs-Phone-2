package com.mikasrevs.newphone.marketplace.data;

import com.mikasrevs.newphone.integration.currency.PhoneCurrencyIntegration;
import java.util.ArrayList;
import java.util.List;

/** Personalized, paged projection. No private balance or favorite list of another player is sent. */
public final class MarketplaceState {
    public String requestId = "", code = "", listingId = "", uploadedId = "", contactUuid = "", contactName = "";
    public String selfUuid = "";
    public long revision;
    public int page, total, activeCount, soldCount, purchaseCount, favoriteCount;
    public boolean hasMore;
    public MarketplaceListing detail;
    public List<MarketplaceListing> listings = new ArrayList<>();
    public List<Notice> notifications = new ArrayList<>();
    public List<Receipt> purchases = new ArrayList<>();
    public PhoneCurrencyIntegration.Snapshot bank;
    public record Notice(String id, String listingId, String actorUuid, String title, String body, long time, boolean read) { }
    public record Receipt(String listingId, String title, String buyerUuid, String buyerName, String sellerUuid,
                          String sellerName, String currencyId, long amount, String amountText, long time, String status) { }
}

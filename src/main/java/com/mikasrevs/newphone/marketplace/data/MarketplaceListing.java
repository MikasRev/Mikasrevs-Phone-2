package com.mikasrevs.newphone.marketplace.data;

import java.util.ArrayList;
import java.util.List;

/** Persistent server record. Price is expressed in the selected LC currency's core units. */
public final class MarketplaceListing {
    public String id = "", ownerUuid = "", ownerName = "", title = "", description = "";
    public String category = "ITEMS", location = "", currencyId = "", currencyName = "", priceText = "";
    public List<String> images = new ArrayList<>();
    public long price, createdAt, updatedAt, expiresAt, views, revision = 1;
    public boolean directPurchase;
    public String status = "ACTIVE", buyerUuid = "", buyerName = "", purchaseRequestId = "";
    public long soldAt;
    public String createRequestId = "";
    public String reviewedBy = "", reviewResolution = "";
    public long reviewedAt;
    // Viewer-specific projection only; never read from a client mutation.
    public boolean favorite;
}

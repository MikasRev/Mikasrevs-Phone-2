package com.mikasrevs.newphone.marketplace.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Bounded command DTO; it deliberately has no owner, balance, payout or sold fields. */
public final class MarketplaceRequest {
    public static final Set<String> CATEGORIES = Set.of("CARS", "HOUSES", "ITEMS", "OTHERS");
    public static final int MAX_PHOTOS = 4, MAX_TITLE = 64, MAX_DESCRIPTION = 1800, MAX_LOCATION = 64;
    public static final long MAX_PRICE = 1_000_000_000_000L;
    public String action = "BROWSE", requestId = "", id = "", title = "", description = "";
    public String category = "", location = "", currencyId = "", price = "0";
    public String search = "", sort = "NEWEST", section = "MARKET", status = "ACTIVE", minPrice = "", maxPrice = "";
    public List<String> images = new ArrayList<>();
    public boolean directPurchase, favoritesOnly, favorite;
    public long expectedRevision, expectedPrice;
    public int page;

    public static long amount(String text, boolean optional) {
        if (optional && (text == null || text.isBlank())) return -1;
        if (text == null || !text.matches("[0-9]{1,13}")) throw new IllegalArgumentException("INVALID_PRICE");
        long value = Long.parseLong(text);
        if (value > MAX_PRICE) throw new IllegalArgumentException("INVALID_PRICE");
        return value;
    }

    public void validateDraft() {
        if (title == null || title.isBlank() || title.length() > MAX_TITLE) throw new IllegalArgumentException("INVALID_TITLE");
        if (description == null || description.length() > MAX_DESCRIPTION || location == null || location.length() > MAX_LOCATION)
            throw new IllegalArgumentException("INVALID_DESCRIPTION");
        if (category == null || !CATEGORIES.contains(category)) throw new IllegalArgumentException("INVALID_CATEGORY");
        if (currencyId == null || currencyId.isBlank() || currencyId.length() > 160) throw new IllegalArgumentException("INVALID_CURRENCY");
        if (images == null || images.size() > MAX_PHOTOS || images.stream().anyMatch(i -> i == null || !i.matches("market-[a-f0-9-]{36}")))
            throw new IllegalArgumentException("INVALID_PHOTO");
        if (title.indexOf('\u00a7') >= 0 || description.indexOf('\u00a7') >= 0 || location.indexOf('\u00a7') >= 0)
            throw new IllegalArgumentException("INVALID_TEXT");
        if(title.chars().anyMatch(Character::isISOControl)||location.chars().anyMatch(Character::isISOControl)
                ||description.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new IllegalArgumentException("INVALID_TEXT");
        amount(price, false);
    }
}

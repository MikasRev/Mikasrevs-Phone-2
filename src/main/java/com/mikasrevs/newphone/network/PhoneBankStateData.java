package com.mikasrevs.newphone.network;

import java.util.ArrayList;
import java.util.List;

/** Compact, server-authoritative state used only by the Bank app. */
public final class PhoneBankStateData {
    public boolean available;
    public String status = "LOADING";
    public String provider = "";
    public long minimumTransfer = 1L;
    public boolean onlineRecipientRequired;
    public String accountName = "";
    public long updatedAt;
    public String resultCode = "";
    public String resultPeerName = "";
    public String resultAmount = "";
    public String responseRequestId = "";
    public List<BalanceEntry> balances = new ArrayList<>();

    public static final class BalanceEntry {
        public String id = "";
        public String name = "";
        public String formatted = "";
        public long coreValue;
        public List<UnitEntry> units = new ArrayList<>();
    }

    public static final class UnitEntry {
        public String name = "";
        public long coreValue = 1L;

        public UnitEntry() {
        }

        public UnitEntry(String name, long coreValue) {
            this.name = name == null ? "" : name;
            this.coreValue = Math.max(1L, coreValue);
        }
    }
}

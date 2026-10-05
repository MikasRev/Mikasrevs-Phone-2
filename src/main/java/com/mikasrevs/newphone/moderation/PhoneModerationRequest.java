package com.mikasrevs.newphone.moderation;

import java.util.Set;

/** One correlated panel request; mutations include the state shown in the confirmation. */
public final class PhoneModerationRequest {
    public String id="", action="", target="", query="", platform="";
    public int page;
    public boolean enabled, expectedOperator, expectedModerator;
    public Set<String> expectedBans;
}

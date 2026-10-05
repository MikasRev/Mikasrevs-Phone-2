package com.mikasrevs.newphone.network;

/** Per-packet bounds, independent of the number of messages retained in a conversation. */
public final class PhoneMessageLimits {
    public static final int TEXT_LENGTH = 4096;
    public static final int ACTION_BODY_LENGTH = 28_000;
    private PhoneMessageLimits() { }
}

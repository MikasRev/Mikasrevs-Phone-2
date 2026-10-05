package com.mikasrevs.newphone.network;

/** The stable client id makes retries idempotent. Attachment data travels separately. */
public final class PhoneSendRequest {
    public String id = "";
    public String peer = "";
    public String type = "TEXT";
    public String body = "";
    public String replyTo = "";
    public String caption = "";
}

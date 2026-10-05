package com.mikasrevs.newphone.marketplace.trade;

import java.util.Set;
import java.util.UUID;

/** Only slot coordinates and server-issued fingerprints; never an item or owner supplied by the client. */
public final class TradeRequest {
    public static final int CHAT_LENGTH=240;
    public String action="SYNC",requestId="",id="",listing="",parcel="",fingerprint="",text="";
    public long revision;
    public int slot=-1,count=1,page;
    public void validate(){
        if(action==null||!Set.of("SYNC","INVITE","ACCEPT","CANCEL","ADD","REMOVE","CONFIRM","UNREADY","CLAIM","CHAT").contains(action))bad();
        uuid(requestId,false);uuid(id,true);uuid(parcel,true);
        if(listing==null||(!listing.isEmpty()&&!listing.matches("MP-[0-9]{1,19}"))||fingerprint==null||fingerprint.length()>64||page<0||page>1000)bad();
        if(action.equals("ADD")&&(slot<0||slot>=36||count<1||count>64||!fingerprint.matches("[a-f0-9]{64}")))bad();
        if(text==null||text.length()>CHAT_LENGTH)bad();
        if(action.equals("CHAT")&&(id.isEmpty()||text.isBlank()||text.chars().anyMatch(c->Character.isISOControl(c)||c==0x00a7)))bad();
    }
    private static void uuid(String text,boolean optional){if(text==null||(!optional||!text.isEmpty())&&(text.length()!=36||!UUID.fromString(text).toString().equals(text)))bad();}
    private static void bad(){throw new IllegalArgumentException("INVALID_REQUEST");}
}

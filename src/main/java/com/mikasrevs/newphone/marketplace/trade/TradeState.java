package com.mikasrevs.newphone.marketplace.trade;

import java.util.*;

/** Personalized view. Inventory and mailbox are visible only to their owner. */
public final class TradeState {
    public String requestId="",code="",self="",id="",status="",listing="",title="",other="",otherName="";
    public long revision,deadline;
    public boolean incoming,ready,otherReady;
    public int mailCount,page;
    public List<Row> mine=new ArrayList<>(),theirs=new ArrayList<>(),inventory=new ArrayList<>(),mail=new ArrayList<>();
    public List<Message> chat=new ArrayList<>();
    public static final class Message {
        public String id="",actor="",text="";
        public long time;
        public Message(){}
        public Message(String id,String actor,String text,long time){this.id=id;this.actor=actor;this.text=text;this.time=time;}
    }
    public static final class Row {
        public String id="",fingerprint="",item="",tag="",name="";
        public int slot=-1,count;
        public boolean protectedPhone;
    }
}

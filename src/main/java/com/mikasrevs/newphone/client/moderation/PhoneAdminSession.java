package com.mikasrevs.newphone.client.moderation;

import com.mikasrevs.newphone.moderation.*;
import java.util.*;

/** Per-screen state survives resize and ignores replies to old screens or timed-out requests. */
public final class PhoneAdminSession {
    public String query="", notice="";
    public int page, pages=1, total, online, restricted;
    public List<PhoneModerationState.Player> players=List.of();
    public PhoneModerationState.Player selected;
    public boolean loaded, fresh;
    private PhoneModerationRequest pending;
    private long started;
    public boolean busy() { return pending!=null; }

    public PhoneModerationRequest begin(String action,long now) {
        if(busy()) return null;
        var request=new PhoneModerationRequest(); request.id=UUID.randomUUID().toString(); request.action=action;
        request.query=query; request.page=page;
        if(selected!=null) {
            request.target=selected.uuid; request.expectedOperator=selected.operator;
            request.expectedModerator=selected.moderator; request.expectedBans=Set.copyOf(selected.bans);
        }
        pending=request; started=now; return request;
    }
    public boolean accept(PhoneModerationState response) {
        if(pending==null||response==null||!pending.id.equals(response.requestId)) return false;
        String action=pending.action; pending=null;
        if(!response.canManage()) { clear(); notice="DENIED"; return true; }
        if("LIST".equals(action)&&response.players!=null) {
            players=List.copyOf(response.players);page=response.page;pages=response.pages;
            total=response.total;online=response.online;restricted=response.restricted;loaded=true;
        }
        if(!"LIST".equals(action)) selected=response.selected;
        fresh=response.notice.isEmpty()||response.notice.equals("UPDATED")||response.notice.equals("CHANGED")||response.notice.equals("DENIED");
        if(!response.notice.isEmpty()) notice=response.notice;
        else if(notice.equals("TIMEOUT")) notice="";
        return true;
    }
    public boolean expire(long now) {
        if(pending==null||now-started<10_000_000_000L) return false;
        pending=null;fresh=false;notice="TIMEOUT";return true;
    }
    public void clear() {
        pending=null;players=List.of();selected=null;loaded=false;fresh=false;notice="";
        page=0;pages=1;total=online=restricted=0;
    }
}

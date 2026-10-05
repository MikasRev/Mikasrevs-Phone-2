package com.mikasrevs.newphone;

import com.google.gson.Gson;
import com.mikasrevs.newphone.client.moderation.PhoneAdminSession;
import com.mikasrevs.newphone.client.moderation.PhoneModerationClient;
import com.mikasrevs.newphone.moderation.*;
import java.util.*;

/** Exercises the actual panel service and asynchronous UI state without a graphical client. */
final class PhoneAdminPanelChecks {
    private static final UUID ADMIN=UUID.randomUUID(), MOD=UUID.randomUUID(), PLAYER=UUID.randomUUID();
    static void run() {
        pagination(); permissions(); staleConfirmation(); malformed(); asynchronous(); clientResponses();
        System.out.println("Admin panel checks passed: search/page limits, protected staff, malformed requests, stale confirmations, correlated replies, timeouts and notice retention.");
    }
    private static PhoneModerationData data() {
        var data=new PhoneModerationData();data.remember(ADMIN,"Admin");data.remember(MOD,"Moderator");data.remember(PLAYER,"Player");
        data.setModerator(true,MOD,true);return data;
    }
    private static PhoneModerationRequest request(String action) {
        var request=new PhoneModerationRequest();request.action=action;request.id=UUID.randomUUID().toString();request.target=PLAYER.toString();return request;
    }
    private static PhoneModerationState response(PhoneModerationData data,UUID actor,boolean op,PhoneModerationRequest request) {
        return PhoneModerationPanel.respond(data,actor,op,request,id->id.equals(ADMIN),id->id.equals(PLAYER));
    }
    private static void expected(PhoneModerationRequest request,PhoneModerationState.Player player) {
        request.target=player.uuid;request.expectedBans=player.bans;request.expectedModerator=player.moderator;request.expectedOperator=player.operator;
    }
    private static void pagination() {
        var data=data();
        for(int i=0;i<31;i++)data.remember(new UUID(0,i+1),String.format(Locale.ROOT,"Player%02d",i));
        var request=request("LIST");request.page=Integer.MAX_VALUE;
        var result=response(data,ADMIN,true,request);
        require(result.page==4&&result.pages==5&&result.players.size()==2&&result.total==34,"last page was not bounded");
        require(result.online==1&&result.restricted==0,"summary counts wrong");
        request.page=Integer.MIN_VALUE;result=response(data,ADMIN,true,request);
        require(result.page==0&&result.players.size()==8,"negative page allowed");
        Set<String> ids=new HashSet<>();
        for(int page=0;page<5;page++) {
            request.page=page;result=response(data,ADMIN,true,request);
            for(var player:result.players)require(ids.add(player.uuid),"player repeated across pages");
        }
        require(ids.size()==34,"pagination skipped a player");
        request.query="  pLaYeR30  ";result=response(data,ADMIN,true,request);
        require(result.total==1&&result.page==0&&result.players.get(0).name.equals("Player30"),"search did not reset bounded page");
        request.query=PLAYER.toString().toUpperCase(Locale.ROOT);result=response(data,ADMIN,true,request);
        require(result.total==1&&result.online==1&&result.players.get(0).uuid.equals(PLAYER.toString()),"UUID search failed");
        request.query="missing";result=response(data,ADMIN,true,request);
        require(result.total==0&&result.pages==1&&result.page==0&&result.players.isEmpty(),"empty query results unsafe");
        request.query=null;require(response(data,ADMIN,true,request).total==34,"null query did not use all players");
        request.query="x".repeat(5000);require(response(data,ADMIN,true,request).total==0,"long query unsafe");
        data.setBan(ADMIN,true,PLAYER,false,"BLOCKGRAM",true);
        request.query="Player";result=response(data,ADMIN,true,request);
        require(result.total==32&&result.restricted==1,"search summary did not use matching players");
        require(result.requestId.equals(request.id),"list response lost correlation");
        data.remember(UUID.randomUUID(),"PLAYER");request.query="player";request.page=0;
        var first=response(data,ADMIN,true,request);var second=response(data,ADMIN,true,request);
        require(first.players.stream().map(p->p.uuid).toList().equals(second.players.stream().map(p->p.uuid).toList()),"equal names sorted unstably");
    }
    private static void permissions() {
        var data=data();var list=request("LIST");
        var denied=response(data,PLAYER,false,list);
        require(denied.notice.equals("DENIED")&&denied.players==null&&denied.selected==null,"normal player received panel data");
        require(response(data,MOD,false,list).players!=null,"moderator cannot list players");
        var detail=response(data,ADMIN,true,request("DETAIL")).selected;
        var role=request("ROLE");expected(role,detail);role.enabled=true;
        require(response(data,MOD,false,role).notice.equals("DENIED")&&!data.moderator(PLAYER),"moderator can appoint staff");
        require(response(data,ADMIN,true,role).notice.equals("UPDATED")&&data.moderator(PLAYER),"OP cannot appoint staff");
        for(UUID target:List.of(ADMIN,MOD,PLAYER)) {
            var query=request("DETAIL");query.target=target.toString();
            var ban=request("BAN");expected(ban,response(data,ADMIN,true,query).selected);ban.enabled=true;ban.platform="TINDER";
            require(response(data,ADMIN,true,ban).notice.equals("DENIED")&&!data.banned(target,"TINDER"),"staff protection bypassed");
        }
        var query=request("DETAIL");query.target=PLAYER.toString();expected(role,response(data,ADMIN,true,query).selected);role.enabled=false;
        require(response(data,ADMIN,true,role).notice.equals("UPDATED")&&!data.moderator(PLAYER),"moderator removal failed");
        data.setModerator(true,MOD,false);
        require(response(data,MOD,false,list).players==null,"revoked moderator retained roster access");
        require(response(data,ADMIN,false,list).notice.equals("DENIED"),"deopped player kept panel access");
    }
    private static void staleConfirmation() {
        var data=data();var detail=response(data,ADMIN,true,request("DETAIL")).selected;
        var ban=request("BAN");expected(ban,detail);ban.platform="BLOCKPOST";ban.enabled=true;
        data.setBan(ADMIN,true,PLAYER,false,"BLOCKGRAM",true);
        var result=response(data,ADMIN,true,ban);
        require(result.notice.equals("CHANGED")&&!data.banned(PLAYER,"BLOCKPOST")&&result.selected.bans.contains("BLOCKGRAM"),"old ban confirmation overwrote updated state");
        expected(ban,result.selected);result=response(data,ADMIN,true,ban);
        require(result.notice.equals("UPDATED")&&data.banned(PLAYER,"BLOCKPOST"),"fresh confirmation rejected");
        require(response(data,ADMIN,true,ban).notice.equals("CHANGED"),"replayed confirmation mutated state");
        expected(ban,result.selected);ban.platform="TINDER";data.setModerator(true,PLAYER,true);
        require(response(data,ADMIN,true,ban).notice.equals("CHANGED")&&!data.banned(PLAYER,"TINDER"),"role change bypassed stale check");
        expected(ban,response(data,ADMIN,true,request("DETAIL")).selected);
        var operatorChange=PhoneModerationPanel.respond(data,ADMIN,true,ban,id->id.equals(ADMIN)||id.equals(PLAYER),id->true);
        require(operatorChange.notice.equals("CHANGED")&&!data.banned(PLAYER,"TINDER"),"OP change bypassed stale check");
    }
    private static void malformed() {
        var data=data();
        require(response(data,ADMIN,true,null).notice.equals("INVALID"),"null request unsafe");
        var request=request("DETAIL");request.id="x".repeat(65);
        require(response(data,ADMIN,true,request).notice.equals("INVALID"),"oversized correlation accepted");
        request=request("DETAIL");
        for(String target:Arrays.asList(null,"broken",UUID.randomUUID().toString())) {
            request.target=target;require(response(data,ADMIN,true,request).notice.equals("NOT_FOUND"),"unknown target unsafe");
        }
        request=request("BAN");request.enabled=true;expected(request,response(data,ADMIN,true,request("DETAIL")).selected);
        for(String platform:Arrays.asList(null,"UNKNOWN","blockgram")) {
            request.platform=platform;require(response(data,ADMIN,true,request).notice.equals("DENIED"),"invalid platform caused mutation");
        }
        request.platform="BLOCKGRAM";request.expectedBans=null;
        require(response(data,ADMIN,true,request).notice.equals("CHANGED"),"missing expected state accepted");
        request=request("UNKNOWN");require(response(data,ADMIN,true,request).notice.equals("INVALID"),"unknown operation accepted");
        require(data.bans(PLAYER).isEmpty(),"malformed request altered bans");
    }
    private static void asynchronous() {
        var data=data();var session=new PhoneAdminSession();session.query="Player";
        var pending=session.begin("LIST",100);
        require(session.begin("BAN",101)==null,"double click started concurrent mutation");
        var unrelated=response(data,ADMIN,true,request("LIST"));
        require(!session.accept(unrelated)&&session.busy()&&!session.loaded,"old screen response accepted");
        var result=response(data,ADMIN,true,pending);
        require(session.accept(result)&&session.loaded&&!session.busy()&&session.query.equals("Player"),"correct response not accepted");
        require(!session.accept(result),"duplicate reply accepted twice");
        session.selected=result.players.get(0);
        pending=session.begin("DETAIL",1000);require(session.accept(response(data,ADMIN,true,pending))&&session.selected!=null,"detail not updated");
        pending=session.begin("BAN",2000);pending.enabled=true;pending.platform="BLOCKGRAM";
        require(session.accept(response(data,ADMIN,true,pending))&&session.selected.bans.contains("BLOCKGRAM")&&session.notice.equals("UPDATED"),"action result lost selection/notice");
        pending=session.begin("DETAIL",3000);session.accept(response(data,ADMIN,true,pending));
        require(session.notice.equals("UPDATED"),"background refresh erased action notice");
        pending=session.begin("DETAIL",4000);
        require(!session.expire(4000+9_999_999_999L)&&session.busy(),"early timeout");
        require(session.expire(4000+10_000_000_000L)&&!session.busy()&&!session.fresh&&session.notice.equals("TIMEOUT"),"timeout did not unlock refresh");
        var late=response(data,ADMIN,true,pending);
        require(!session.accept(late)&&session.notice.equals("TIMEOUT"),"timed-out response accepted");
        var fresh=session.begin("DETAIL",20_000_000_000L);
        require(!session.accept(late)&&session.busy(),"late response completed another request");
        require(session.accept(response(data,ADMIN,true,fresh))&&session.fresh&&session.notice.isEmpty(),"refresh did not recover after timeout");
        session.selected=null;session.page=3;session.query="Player";
        pending=session.begin("LIST",30_000_000_000L);session.accept(response(data,ADMIN,true,pending));
        require(session.page==0&&session.query.equals("Player"),"server-clamped page or search lost");
        pending=session.begin("LIST",40_000_000_000L);
        var revoked=response(data,ADMIN,false,pending);session.accept(revoked);
        require(!session.busy()&&session.selected==null&&session.players.isEmpty()&&!session.loaded,"revocation retained UI data");
    }
    private static void clientResponses() {
        var gson=new Gson();PhoneModerationClient.logout(null);var data=data();
        var result=response(data,ADMIN,true,request("LIST"));PhoneModerationClient.receive(gson.toJson(result));
        require(PhoneModerationClient.response().requestId.equals(result.requestId),"client discarded correlated reply");
        var access=new PhoneModerationState();access.operator=true;PhoneModerationClient.receive(gson.toJson(access));
        require(PhoneModerationClient.response().requestId.equals(result.requestId),"access-only update erased operation reply");
        access.operator=false;PhoneModerationClient.receive(gson.toJson(access));
        require(PhoneModerationClient.response()==null&&PhoneModerationClient.state().players==null,"role removal retained private reply");
        PhoneModerationClient.receive(gson.toJson(result));PhoneModerationClient.logout(null);
        require(PhoneModerationClient.response()==null,"previous server response leaked into new session");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}

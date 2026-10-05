package com.mikasrevs.newphone.moderation;

import java.util.*;
import java.util.function.Predicate;

/** The panel's server-side permissions, pagination and stale-confirmation checks. */
public final class PhoneModerationPanel {
    public static final int PAGE_SIZE=8;
    private PhoneModerationPanel() {}

    public static PhoneModerationState respond(PhoneModerationData data, UUID actor, boolean operator,
            PhoneModerationRequest request, Predicate<UUID> operators, Predicate<UUID> online) {
        var result=new PhoneModerationState();
        result.operator=operator; result.moderator=data.moderator(actor); result.bans=data.bans(actor);
        if(request==null || request.id==null || request.id.length()>64) { result.notice="INVALID"; return result; }
        result.requestId=request.id;
        if(!result.canManage()) { result.notice="DENIED"; return result; }
        if("LIST".equals(request.action)) {
            String query=request.query==null?"":request.query;
            String filter=query.substring(0,Math.min(64,query.length())).trim().toLowerCase(Locale.ROOT);
            var rows=data.players().entrySet().stream()
                    .filter(e->e.getValue().toLowerCase(Locale.ROOT).contains(filter)||e.getKey().toString().equals(filter))
                    .sorted(Map.Entry.<UUID,String>comparingByValue(String.CASE_INSENSITIVE_ORDER).thenComparing(Map.Entry.comparingByKey()))
                    .toList();
            result.total=rows.size(); result.pages=Math.max(1,(rows.size()+PAGE_SIZE-1)/PAGE_SIZE);
            result.page=Math.max(0,Math.min(request.page,result.pages-1)); result.players=new ArrayList<>();
            for(var entry:rows) {
                if(online.test(entry.getKey())) result.online++;
                if(!data.bans(entry.getKey()).isEmpty()) result.restricted++;
            }
            for(var entry:rows.subList(Math.min(rows.size(),result.page*PAGE_SIZE),Math.min(rows.size(),(result.page+1)*PAGE_SIZE)))
                result.players.add(player(data,entry.getKey(),operators,online));
            return result;
        }
        UUID target;
        try { target=UUID.fromString(request.target); }
        catch(IllegalArgumentException|NullPointerException invalid) { result.notice="NOT_FOUND"; return result; }
        if(!data.players().containsKey(target)) { result.notice="NOT_FOUND"; return result; }
        result.selected=player(data,target,operators,online);
        if("DETAIL".equals(request.action)) return result;
        if(!"BAN".equals(request.action)&&!"ROLE".equals(request.action)) { result.notice="INVALID"; return result; }
        if(request.expectedBans==null || request.expectedOperator!=result.selected.operator
                || request.expectedModerator!=result.selected.moderator || !request.expectedBans.equals(result.selected.bans)) {
            result.notice="CHANGED"; return result;
        }
        boolean success="ROLE".equals(request.action)
                ? !result.selected.operator && data.setModerator(operator,target,request.enabled)
                : data.setBan(actor,operator,target,result.selected.operator,request.platform,request.enabled);
        result.notice=success?"UPDATED":"DENIED";
        result.selected=player(data,target,operators,online);
        // Changing one's own appointed role must also update the response access flags.
        result.moderator=data.moderator(actor);
        return result;
    }

    private static PhoneModerationState.Player player(PhoneModerationData data,UUID id,
            Predicate<UUID> operators,Predicate<UUID> online) {
        var row=new PhoneModerationState.Player(); row.uuid=id.toString(); row.name=data.players().get(id);
        row.online=online.test(id); row.operator=operators.test(id); row.moderator=data.moderator(id); row.bans=data.bans(id);
        return row;
    }
}

package com.mikasrevs.newphone.moderation;

import java.util.*;

/** Public access flags plus a bounded staff-only roster page. */
public final class PhoneModerationState {
    public boolean operator, moderator;
    public Set<String> bans=new HashSet<>();
    public List<Player> players; // Null means an access-only update; no staff data is sent to normal players.
    public int page, pages=1;
    public String notice="";
    public String requestId="";
    public Player selected;
    public int total, online, restricted;
    public boolean canManage(){return operator||moderator;}
    public static final class Player {
        public String uuid, name;
        public boolean online, operator, moderator;
        public Set<String> bans=new HashSet<>();
    }
}

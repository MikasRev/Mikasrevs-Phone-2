package com.mikasrevs.newphone.server.data;

import com.mikasrevs.newphone.network.PhoneStateData;
import java.util.*;

/** Network windows never remove messages from the persistent archive. */
public final class PhoneHistory {
    public static final int PAGE_SIZE = 50;
    private PhoneHistory() { }
    public static String channel(String type) {
        return type != null && type.startsWith("GROUP_") ? "GROUP" : type != null && type.startsWith("GRAM_") ? "GRAM" : type != null && type.startsWith("TINDER_") ? "TINDER" : "MESSAGE";
    }
    public static String key(String channel, String peer) { return channel + "|" + peer; }
    public static PhoneStateData.MessageEntry entry(PhoneCommunicationSavedData.StoredMessage m) {
        var e = new PhoneStateData.MessageEntry(m.id,m.fromUuid,m.fromName,m.toUuid,m.toName,m.type,m.body,m.timestamp,m.read,m.replyToId,m.replyPreview);
        e.delivered = m.delivered || m.read; e.caption = m.caption == null ? "" : m.caption;
        return e;
    }
    public static void recent(List<PhoneCommunicationSavedData.StoredMessage> messages, String self, PhoneStateData state) {
        Map<String,Integer> counts = new HashMap<>();
        for (int i=messages.size()-1;i>=0;i--) {
            var m=messages.get(i);
            if (PhoneGroups.isGroup(m)) continue;
            if (!self.equals(m.fromUuid) && !self.equals(m.toUuid)) continue;
            String peer=self.equals(m.fromUuid)?m.toUuid:m.fromUuid, key=key(channel(m.type),peer);
            if (self.equals(m.toUuid) && !m.read) state.unreadCounts.merge(key,1,Integer::sum);
            int count=counts.merge(key,1,Integer::sum);
            if (count<=PAGE_SIZE) state.messages.add(entry(m));
            state.historyMore.put(key,count>PAGE_SIZE);
        }
        Collections.reverse(state.messages);
    }
    public static PhoneStateData page(List<PhoneCommunicationSavedData.StoredMessage> messages,String self,String peer,String channel,String beforeId) {
        PhoneStateData result=new PhoneStateData(); result.historyOnly=true; result.historyKey=key(channel,peer);
        int before=messages.size();
        if (beforeId!=null && !beforeId.isBlank()) {
            before=-1;
            for(int i=0;i<messages.size();i++) {
                var m=messages.get(i);
                if(m.id.equals(beforeId)&&belongs(m,self,peer,channel)){before=i;break;}
            }
        }
        boolean more=false;
        for(int i=before-1;i>=0;i--) {
            var m=messages.get(i); if(!belongs(m,self,peer,channel))continue;
            if(result.messages.size()==PAGE_SIZE){more=true;break;}
            result.messages.add(entry(m));
        }
        Collections.reverse(result.messages); result.historyMore.put(result.historyKey,more);
        return result;
    }
    private static boolean belongs(PhoneCommunicationSavedData.StoredMessage m,String self,String peer,String channel) {
        return channel(m.type).equals(channel)&&((self.equals(m.fromUuid)&&peer.equals(m.toUuid))||(peer.equals(m.fromUuid)&&self.equals(m.toUuid)));
    }
}

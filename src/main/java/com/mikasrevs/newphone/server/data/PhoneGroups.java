package com.mikasrevs.newphone.server.data;

import com.mikasrevs.newphone.network.PhoneStateData;
import java.util.*;

/** Membership boundaries also govern attachments and rejoining a group. */
public final class PhoneGroups {
    private PhoneGroups(){}
    public static final class Group {
        public String id="",name="",owner="",photoId="";
        public long sequence;
        public LinkedHashMap<String,Long> members=new LinkedHashMap<>();
        public Group copy(){var g=new Group();g.id=id;g.name=name;g.owner=owner;g.photoId=photoId;g.sequence=sequence;g.members.putAll(members);return g;}
    }
    public static boolean isGroup(PhoneCommunicationSavedData.StoredMessage m){return m.type!=null&&m.type.startsWith("GROUP_");}
    public static boolean member(PhoneCommunicationSavedData.Snapshot s,String id,String self){var g=s.groups.get(id);return g!=null&&g.members.containsKey(self);}
    public static boolean visible(PhoneCommunicationSavedData.Snapshot s,PhoneCommunicationSavedData.StoredMessage m,String self){
        var g=s.groups.get(m.toUuid);
        return isGroup(m)&&g!=null&&g.members.containsKey(self)&&m.groupSequence>=g.members.get(self)&&m.recipients.contains(self);
    }
    public static PhoneStateData.MessageEntry entry(PhoneCommunicationSavedData.StoredMessage m,String self){
        var e=PhoneHistory.entry(m);e.groupSequence=m.groupSequence;
        if(self.equals(m.fromUuid)){
            var others=m.recipients.stream().filter(id->!id.equals(self)).toList();
            e.read=!others.isEmpty()&&m.readBy.containsAll(others);
            e.delivered=!others.isEmpty()&&m.deliveredTo.containsAll(others);
        }else{e.read=m.readBy.contains(self);e.delivered=m.deliveredTo.contains(self);}
        return e;
    }
    public static void recent(PhoneCommunicationSavedData.Snapshot s,String self,PhoneStateData state){
        for(var g:s.groups.values())if(g.members.containsKey(self)){
            var e=new PhoneStateData.GroupEntry();e.id=g.id;e.name=g.name;e.owner=g.owner;e.photoId=g.photoId;e.joinedSequence=g.members.get(self);
            for(String id:g.members.keySet())e.members.add(new PhoneStateData.PlayerEntry(id,s.knownNames.getOrDefault(id,"Player"),"",false));
            state.groups.add(e);
        }
        Map<String,Integer> counts=new HashMap<>();
        for(int i=s.messages.size()-1;i>=0;i--){var m=s.messages.get(i);if(!visible(s,m,self))continue;
            String key=PhoneHistory.key("GROUP",m.toUuid);
            if(!m.fromUuid.equals(self)&&!m.readBy.contains(self))state.unreadCounts.merge(key,1,Integer::sum);
            int count=counts.merge(key,1,Integer::sum);if(count<=PhoneHistory.PAGE_SIZE)state.messages.add(entry(m,self));
            state.historyMore.put(key,count>PhoneHistory.PAGE_SIZE);
        }
        state.messages.sort(Comparator.comparingLong(m->m.timestamp));
    }
    public static PhoneStateData page(PhoneCommunicationSavedData.Snapshot s,String self,String group,String beforeId){
        var result=new PhoneStateData();result.historyOnly=true;result.historyKey=PhoneHistory.key("GROUP",group);
        if(!member(s,group,self))return result;
        result.historyJoinedSequence=s.groups.get(group).members.get(self);
        int before=s.messages.size();
        if(!beforeId.isBlank()){before=-1;for(int i=0;i<s.messages.size();i++){var m=s.messages.get(i);if(m.id.equals(beforeId)&&m.toUuid.equals(group)&&visible(s,m,self)){before=i;break;}}}
        boolean more=false;
        for(int i=before-1;i>=0;i--){var m=s.messages.get(i);if(!m.toUuid.equals(group)||!visible(s,m,self))continue;
            if(result.messages.size()==PhoneHistory.PAGE_SIZE){more=true;break;}result.messages.add(entry(m,self));
        }
        Collections.reverse(result.messages);result.historyMore.put(result.historyKey,more);return result;
    }
    public static List<PhoneCommunicationSavedData.StoredMessage> markRead(PhoneCommunicationSavedData.Snapshot s,String group,String self){
        var changed=new ArrayList<PhoneCommunicationSavedData.StoredMessage>();
        for(var m:s.messages)if(m.toUuid.equals(group)&&visible(s,m,self)&&!m.readBy.contains(self)){
            m.readBy.add(self);
            if(!m.deliveredTo.contains(self))m.deliveredTo.add(self);
            changed.add(m);
        }
        return changed;
    }
    public static List<PhoneCommunicationSavedData.StoredMessage> acknowledge(PhoneCommunicationSavedData.Snapshot s,Set<String> ids,String self){
        var changed=new ArrayList<PhoneCommunicationSavedData.StoredMessage>();
        for(var m:s.messages)if(ids.contains(m.id)&&visible(s,m,self)&&!m.deliveredTo.contains(self)){
            m.deliveredTo.add(self);changed.add(m);
        }
        return changed;
    }
    public static PhoneStateData receipts(PhoneCommunicationSavedData.Snapshot s,List<PhoneCommunicationSavedData.StoredMessage> changed,String self){
        var packet=new PhoneStateData();packet.historyOnly=true;
        for(var m:changed)if(visible(s,m,self)){
            var entry=entry(m,self);var receipt=new PhoneStateData.MessageReceipt();
            receipt.id=m.id;receipt.read=entry.read;receipt.delivered=entry.delivered;
            packet.messageReceipts.add(receipt);
        }
        return packet;
    }
    public static boolean canAdd(PhoneCommunicationSavedData.Snapshot s,Group g,String actor,String candidate){
        if(!g.owner.equals(actor)||g.members.containsKey(candidate)||!s.contacts.getOrDefault(actor,List.of()).contains(candidate))return false;
        for(String member:g.members.keySet())if(s.blocked.getOrDefault(member,List.of()).contains(candidate)||s.blocked.getOrDefault(candidate,List.of()).contains(member))return false;
        return true;
    }
    public static void remove(Group g,String member){
        g.members.remove(member);
        if(g.owner.equals(member))g.owner=g.members.keySet().stream().findFirst().orElse("");
    }
}

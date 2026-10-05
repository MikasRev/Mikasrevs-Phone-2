package com.mikasrevs.newphone;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneGroupClient;
import com.mikasrevs.newphone.client.data.PhoneMessageClient;
import com.mikasrevs.newphone.network.PhoneStateData;
import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import com.mikasrevs.newphone.server.data.PhoneGroups;
import com.mikasrevs.newphone.server.data.PhoneHistory;
import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Exercises group state and client history without a running game or player files. */
final class PhoneGroupChecks {
    private static final String SELF=UUID.randomUUID().toString();
    private static final String BOB=UUID.randomUUID().toString();
    private static final String CAROL=UUID.randomUUID().toString();
    private static final String OUTSIDER=UUID.randomUUID().toString();
    private static final String GROUP=UUID.randomUUID().toString();

    static void run() throws Exception {
        membership();history();receipts();persistence();clientCache();
        System.out.println("Phone group checks passed (membership, admin succession, history privacy, receipts, persistence, stale pages).");
    }

    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}

    private static PhoneCommunicationSavedData.Snapshot fixture(int count){
        var s=new PhoneCommunicationSavedData.Snapshot();
        var g=new PhoneGroups.Group();g.id=GROUP;g.owner=SELF;g.name="Arkadaşlar";
        for(String member:List.of(SELF,BOB,CAROL))g.members.put(member,++g.sequence);
        s.groups.put(g.id,g);s.contacts.put(SELF,new ArrayList<>(List.of(BOB,CAROL,OUTSIDER)));
        for(int i=0;i<count;i++){
            var m=new PhoneCommunicationSavedData.StoredMessage();
            m.id="group-message-"+i;m.fromUuid=SELF;m.toUuid=GROUP;m.type="GROUP_TEXT";
            m.body="message "+i;m.timestamp=1234;m.groupSequence=++g.sequence;
            m.recipients.addAll(g.members.keySet());m.readBy.add(SELF);m.deliveredTo.add(SELF);
            s.messages.add(m);
        }
        return s;
    }

    private static PhoneStateData state(PhoneCommunicationSavedData.Snapshot s,String self){
        var state=new PhoneStateData();state.selfUuid=self;PhoneGroups.recent(s,self,state);return state;
    }

    private static void membership(){
        var s=fixture(1);var g=s.groups.get(GROUP);var m=s.messages.get(0);
        require(PhoneGroups.canAdd(s,g,SELF,OUTSIDER),"owner cannot add a contact");
        require(!PhoneGroups.canAdd(s,g,BOB,OUTSIDER),"ordinary member can add contacts");
        require(!PhoneGroups.canAdd(s,g,SELF,BOB),"existing member can be added twice");
        require(!PhoneGroups.canAdd(s,g,SELF,UUID.randomUUID().toString()),"non-contact can be added");
        s.blocked.put(CAROL,List.of(OUTSIDER));
        require(!PhoneGroups.canAdd(s,g,SELF,OUTSIDER),"another member's block was ignored");
        s.blocked.clear();s.blocked.put(OUTSIDER,List.of(BOB));
        require(!PhoneGroups.canAdd(s,g,SELF,OUTSIDER),"candidate's block was ignored");
        require(!PhoneGroups.visible(s,m,OUTSIDER),"outsider can read group text");
        PhoneGroups.remove(g,BOB);
        require(!PhoneGroups.visible(s,m,BOB)&&state(s,BOB).groups.isEmpty(),"removed member retains group access");
        g.members.put(BOB,++g.sequence);
        require(!PhoneGroups.visible(s,m,BOB),"rejoining exposes an earlier membership's messages");
        m.type="GROUP_VOICE";m.body=UUID.randomUUID().toString();
        var data=new PhoneCommunicationSavedData();data.replace(s);
        require(data.canReadVoice(SELF,m.body)&&!data.canReadVoice(BOB,m.body)&&!data.canReadVoice(OUTSIDER,m.body),"voice ignores membership boundaries");
        PhoneGroups.remove(g,SELF);
        require(g.owner.equals(CAROL),"admin succession did not follow remaining join order");
        PhoneGroups.remove(g,CAROL);PhoneGroups.remove(g,BOB);
        require(g.owner.isEmpty()&&g.members.isEmpty()&&s.groups.containsKey(GROUP),"empty group tombstone lost");
    }

    private static void history(){
        var s=fixture(137);var recent=state(s,BOB);
        require(recent.messages.size()==50&&recent.unreadCounts.get("GROUP|"+GROUP)==137,"group history/unread window is incorrect");
        var page=PhoneGroups.page(s,BOB,GROUP,"group-message-87");
        require(page.messages.size()==50&&page.messages.get(0).id.equals("group-message-37")&&page.messages.get(49).id.equals("group-message-86"),"same-timestamp group page overlaps");
        require(page.historyJoinedSequence==s.groups.get(GROUP).members.get(BOB),"page lacks membership boundary");
        page=PhoneGroups.page(s,BOB,GROUP,"group-message-37");
        require(page.messages.size()==37&&!page.historyMore.get("GROUP|"+GROUP),"oldest group page is incorrect");
        require(PhoneGroups.page(s,OUTSIDER,GROUP,"").messages.isEmpty(),"outsider can request history");
        require(PhoneGroups.page(s,BOB,GROUP,"foreign-cursor").messages.isEmpty(),"unknown history cursor was accepted");
        var direct=new PhoneStateData();PhoneHistory.recent(s.messages,SELF,direct);
        require(direct.messages.isEmpty(),"group messages appeared in direct history");
        var g=s.groups.get(GROUP);PhoneGroups.remove(g,BOB);g.members.put(BOB,++g.sequence);
        require(PhoneGroups.page(s,BOB,GROUP,"group-message-87").messages.isEmpty(),"previous membership cursor exposes history");
        require(s.messages.size()==137,"membership changes deleted shared message history");
    }

    private static void receipts(){
        var s=fixture(137);var m=s.messages.get(0);
        require(PhoneGroups.acknowledge(s,Set.of(m.id),OUTSIDER).isEmpty(),"outsider delivered a group message");
        PhoneGroups.acknowledge(s,Set.of(m.id),BOB);
        require(!PhoneGroups.entry(m,SELF).delivered,"partial delivery displayed as delivered to everyone");
        PhoneGroups.acknowledge(s,Set.of(m.id),CAROL);
        require(PhoneGroups.entry(m,SELF).delivered&&!PhoneGroups.entry(m,SELF).read,"delivery/read states were confused");
        require(PhoneGroups.markRead(s,GROUP,OUTSIDER).isEmpty(),"outsider marked history read");
        require(PhoneGroups.markRead(s,GROUP,BOB).size()==137,"read did not cover unloaded group history");
        require(!PhoneGroups.entry(m,SELF).read,"partial read displayed as read by everyone");
        var changed=PhoneGroups.markRead(s,GROUP,CAROL);
        require(PhoneGroups.entry(m,SELF).read&&PhoneGroups.entry(m,SELF).delivered,"complete group receipt missing");
        require(PhoneGroups.markRead(s,GROUP,CAROL).isEmpty(),"duplicate read mutated state");
        var packet=PhoneGroups.receipts(s,changed,SELF);
        require(packet.messages.isEmpty()&&packet.messageReceipts.size()==137,"receipt loaded message bodies");
        require(PhoneGroups.receipts(s,changed,OUTSIDER).messageReceipts.isEmpty(),"receipt leaked to outsider");
        var g=s.groups.get(GROUP);PhoneGroups.remove(g,BOB);g.members.put(BOB,++g.sequence);
        require(PhoneGroups.receipts(s,changed,BOB).messageReceipts.isEmpty(),"old membership receipt leaked after rejoin");
    }

    private static void persistence(){
        var s=fixture(2);var g=s.groups.get(GROUP);g.photoId="avatar-"+UUID.randomUUID();
        PhoneGroups.markRead(s,GROUP,BOB);
        var data=new PhoneCommunicationSavedData();data.replace(s);
        var loaded=PhoneCommunicationSavedData.load(data.save(new CompoundTag())).copySnapshot();
        var restored=loaded.groups.get(GROUP);
        require(restored.name.equals(g.name)&&restored.photoId.equals(g.photoId)&&restored.owner.equals(SELF)&&restored.members.equals(g.members)&&restored.sequence==g.sequence,"group metadata lost after reload");
        require(loaded.messages.get(0).readBy.contains(BOB)&&loaded.messages.get(0).recipients.equals(s.messages.get(0).recipients),"group receipts/recipients lost after reload");
        restored.members.clear();loaded.messages.get(0).readBy.clear();
        require(data.copySnapshot().groups.get(GROUP).members.size()==3&&data.copySnapshot().messages.get(0).readBy.contains(BOB),"snapshot copy shares mutable group collections");
    }

    private static void clientCache() throws Exception {
        PhoneClientData.clear();var s=fixture(137);
        PhoneClientData.setState(state(s,SELF));
        var older=PhoneGroups.page(s,SELF,GROUP,"group-message-87");PhoneMessageClient.update(older);
        require(PhoneGroupClient.messages(GROUP).size()==100&&PhoneGroupClient.messages(GROUP).get(0).id.equals("group-message-37"),"client group page order is incorrect");
        PhoneGroups.markRead(s,GROUP,BOB);var changed=PhoneGroups.markRead(s,GROUP,CAROL);
        PhoneMessageClient.update(PhoneGroups.receipts(s,changed,SELF));
        require(PhoneGroupClient.messages(GROUP).size()==100&&PhoneGroupClient.messages(GROUP).stream().allMatch(m->m.read&&m.delivered),"receipts failed for cached old pages or loaded extra history");
        PhoneMessageClient.update(PhoneGroups.page(s,SELF,GROUP,"group-message-37"));
        var g=s.groups.get(GROUP);PhoneGroups.remove(g,SELF);g.members.put(SELF,++g.sequence);
        PhoneClientData.setState(state(s,SELF));
        require(PhoneGroupClient.messages(GROUP).isEmpty(),"rejoin retained previous membership messages");
        PhoneMessageClient.update(older);
        require(PhoneGroupClient.messages(GROUP).isEmpty(),"late old page restored inaccessible messages");
        var moreField=PhoneMessageClient.class.getDeclaredField("MORE");moreField.setAccessible(true);
        var more=(Map<?,?>)moreField.get(null);
        require(!more.containsKey("GROUP|"+GROUP),"late old page corrupted new membership paging");
        var denied=new PhoneStateData();denied.selfUuid=SELF;PhoneClientData.setState(denied);
        require(PhoneGroupClient.group(GROUP)==null&&PhoneGroupClient.messages(GROUP).isEmpty(),"removed group retained client data");
        PhoneClientData.clear();
    }
}

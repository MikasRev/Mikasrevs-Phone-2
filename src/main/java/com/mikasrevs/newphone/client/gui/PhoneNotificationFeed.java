package com.mikasrevs.newphone.client.gui;

import com.mikasrevs.newphone.client.blockgram.PhoneBlockGramStore;
import com.mikasrevs.newphone.client.data.*;
import com.mikasrevs.newphone.client.settings.*;
import java.util.*;

/** Notification data projection shared by device shells; never owns notification state. */
public final class PhoneNotificationFeed {
    public record Notice(String kind,String title,String body,long when){}
    private PhoneNotificationFeed(){}
    public static List<Notice> entries(boolean extras){
        if(PhoneSettingsStore.doNotDisturb()||!PhoneSettingsStore.messageNotifications())return List.of();
        List<Notice> result=new ArrayList<>();
        for(var contact:PhoneClientData.sortedMessagePeers()){
            if(PhoneClientData.unreadCount(contact.uuid)<=0||PhonePrivacyStore.muted(contact.uuid))continue;
            var last=PhoneClientData.lastMessage(contact.uuid);
            result.add(new Notice("MESSAGES",contact.name,PhoneClientData.messagePreview(last),last==null?0:last.timestamp));
        }
        String self=PhoneClientData.selfUuid();
        for(var n:PhoneBlockGramStore.notifications(self))if(!n.read()&&!PhonePrivacyStore.muted(n.actorUuid()))result.add(new Notice("BLOCKGRAM",n.actorName(),PhoneSettingsStore.localizeNetworkText(n.text()),n.createdAt()));
        for(var n:PhoneSocialClientData.notifications("BLOCKPOST",self))if(!n.read&&!PhonePrivacyStore.muted(n.actorUuid))result.add(new Notice("BLOCKPOST",n.actorName,PhoneSettingsStore.localizeNetworkText(n.text),n.createdAt));
        if(extras){
            for(var n:PhoneSocialClientData.notifications("TINDER",self))if(!n.read&&!PhonePrivacyStore.muted(n.actorUuid))result.add(new Notice("TINDER",n.actorName,PhoneSettingsStore.localizeNetworkText(n.text),n.createdAt));
            for(var n:com.mikasrevs.newphone.marketplace.client.MarketplaceClient.state().notifications)if(!n.read()&&!PhonePrivacyStore.muted(n.actorUuid()))result.add(new Notice("MARKETPLACE",n.title(),n.body(),n.time()));
            if(com.mikasrevs.newphone.marketplace.trade.TradeClient.unread()>0)result.add(new Notice("MARKETPLACE",PhoneSettingsStore.text("Takas isteği","Trade request"),com.mikasrevs.newphone.marketplace.trade.TradeClient.state().otherName,Math.max(0,com.mikasrevs.newphone.marketplace.trade.TradeClient.state().deadline-120_000)));
        }
        result.sort(Comparator.comparingLong(Notice::when).reversed());return List.copyOf(result);
    }
}

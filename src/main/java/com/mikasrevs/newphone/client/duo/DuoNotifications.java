package com.mikasrevs.newphone.client.duo;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.gui.PhoneNotificationFeed;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;

/** A small visual banner observing the same unread feed; never acknowledges or resends an event. */
public final class DuoNotifications {
    private static String account="";
    private static long latest,until;
    private static PhoneNotificationFeed.Notice shown;
    private DuoNotifications(){}
    public static void reset(){account="";latest=0;until=0;shown=null;}
    public static PhoneNotificationFeed.Notice current(){
        var notices=PhoneNotificationFeed.entries(true);String self=PhoneClientData.selfUuid();
        long newest=notices.isEmpty()?0:notices.get(0).when();
        if(!account.equals(self)){account=self;latest=newest;shown=null;until=0;}
        if(newest>latest){latest=newest;shown=notices.get(0);until=System.currentTimeMillis()+2800;}
        return !PhoneSettingsStore.doNotDisturb()&&PhoneSettingsStore.messageNotifications()&&System.currentTimeMillis()<until?shown:null;
    }
}

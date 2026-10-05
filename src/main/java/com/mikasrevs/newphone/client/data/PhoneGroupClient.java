package com.mikasrevs.newphone.client.data;

import com.google.gson.Gson;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.network.message.PhonePhotoUploadMessage;
import java.nio.file.Path;
import java.util.*;

public final class PhoneGroupClient {
    private static final Gson GSON=new Gson();
    private PhoneGroupClient(){}
    public static PhoneStateData.GroupEntry group(String id){return PhoneClientData.state().groups.stream().filter(g->g.id.equals(id)).findFirst().orElse(null);}
    public static List<PhoneStateData.MessageEntry> messages(String id){return PhoneClientData.messagesSnapshot().stream().filter(m->m.type.startsWith("GROUP_")&&m.toUuid.equals(id)).toList();}
    public static int unread(String id){return PhoneClientData.state().unreadCounts.getOrDefault("GROUP|"+id,0);}
    public static PhoneStateData.MessageEntry last(String id){var all=messages(id);return all.isEmpty()?null:all.get(all.size()-1);}
    public static void action(String action,String id,String name,String member,Collection<String> members){
        Map<String,Object> edit=new LinkedHashMap<>();edit.put("name",name);edit.put("member",member);edit.put("members",members);
        PhoneNetwork.sendAction(action,id,GSON.toJson(edit));
    }
    public static boolean photo(String id,Path file,String caption,boolean avatar){
        if(group(id)==null)return false;
        byte[] bytes=PhonePhotoStore.encodeForMessage(file);if(bytes==null||bytes.length==0)return false;
        if(avatar)PhoneNetwork.CHANNEL.sendToServer(new PhonePhotoUploadMessage(id,"avatar-"+UUID.randomUUID(),bytes,""));
        else PhoneMessageClient.send(id,"GROUP_PHOTO","","",caption,bytes);
        return true;
    }
    public static void delete(PhoneStateData.MessageEntry message){
        if(!PhoneMessageClient.discard(message.id))PhoneNetwork.sendAction("GROUP_DELETE",message.id,"");
    }
}

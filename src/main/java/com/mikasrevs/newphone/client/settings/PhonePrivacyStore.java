package com.mikasrevs.newphone.client.settings;

import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;

public final class PhonePrivacyStore {
    private static boolean loaded, previews=true;
    private static final Set<String> MUTED=new HashSet<>();
    private PhonePrivacyStore() { }
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/newphone-privacy.properties");}
    private static synchronized void load(){
        if(loaded)return;loaded=true;
        try(var in=Files.newInputStream(file())){var p=new Properties();p.load(in);previews=Boolean.parseBoolean(p.getProperty("lockPreviews","true"));
            for(String id:p.getProperty("muted","").split(","))if(!id.isBlank())MUTED.add(id);
        }catch(java.io.IOException ignored){}
    }
    public static boolean previews(){load();return previews;}
    public static boolean muted(String peer){load();return MUTED.contains(peer);}
    public static void toggleMute(String peer){load();if(!MUTED.remove(peer))MUTED.add(peer);save();}
    public static void togglePreviews(){load();previews=!previews;save();}
    public static boolean alerts(String peer){return PhoneSettingsStore.messageNotifications()&&!PhoneSettingsStore.doNotDisturb()&&!muted(peer);}
    private static synchronized void save(){
        try{Path target=file();Files.createDirectories(target.getParent());Path temp=target.resolveSibling(target.getFileName()+".tmp");
            var p=new Properties();p.setProperty("lockPreviews",Boolean.toString(previews));p.setProperty("muted",String.join(",",MUTED));
            try(var out=Files.newOutputStream(temp)){p.store(out,"Phone notification privacy");}
            Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
        }catch(java.io.IOException e){System.err.println("[MPhone] Could not save privacy settings: "+e.getMessage());}
    }
}

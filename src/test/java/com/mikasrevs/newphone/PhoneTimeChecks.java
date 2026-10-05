package com.mikasrevs.newphone;

import com.mikasrevs.newphone.client.gui.PhoneTimeDisplay;
import java.nio.file.*;
import java.io.*;

final class PhoneTimeChecks {
    static void run() throws Exception {
        require(PhoneTimeDisplay.minecraftTime(0).toString().equals("06:00"),"sunrise conversion");
        require(PhoneTimeDisplay.minecraftTime(6000).toString().equals("12:00"),"noon conversion");
        require(PhoneTimeDisplay.minecraftTime(18000).toString().equals("00:00"),"midnight conversion");
        require(PhoneTimeDisplay.minecraftTime(24000).equals(PhoneTimeDisplay.minecraftTime(0)),"day wrap");
        require(PhoneTimeDisplay.minecraftTime(-1).equals(PhoneTimeDisplay.minecraftTime(23999)),"negative time wrap");
        require(PhoneTimeDisplay.minecraftDay(0)==1&&PhoneTimeDisplay.minecraftDay(23999)==1&&PhoneTimeDisplay.minecraftDay(24000)==2,"day label boundaries");
        require(PhoneTimeDisplay.minecraftTime(Long.MAX_VALUE)!=null,"large world age overflow");
        Path directory=Files.createTempDirectory("phone-time-checks-");String previous=System.getProperty("user.dir");
        try {
            System.setProperty("user.dir",directory.toString());Class<?> first=settings();
            require(!(Boolean)first.getMethod("useMinecraftTime").invoke(null),"existing users lost real-time default");
            first.getMethod("setUseMinecraftTime",boolean.class).invoke(null,true);
            first.getMethod("setUse24HourClock",boolean.class).invoke(null,false);
            Class<?> restored=settings();
            require((Boolean)restored.getMethod("useMinecraftTime").invoke(null),"time source not persisted");
            require(!(Boolean)restored.getMethod("use24HourClock").invoke(null),"12h format not independent");
            restored.getMethod("setUseMinecraftTime",boolean.class).invoke(null,false);
            require(!(Boolean)settings().getMethod("useMinecraftTime").invoke(null),"real-time selection not restored");
        }finally{
            System.setProperty("user.dir",previous);
            try(var paths=Files.walk(directory)){for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(path);}
        }
        System.out.println("Clock checks passed: Minecraft time/day boundaries and independent persisted source/12h settings.");
    }
    private static Class<?> settings() throws Exception {
        String store="com.mikasrevs.newphone.client.settings.PhoneSettingsStore";
        ClassLoader loader=new ClassLoader(PhoneTimeChecks.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.equals(store)&&!name.startsWith(store+"$"))return super.loadClass(name,resolve);
                Class<?> type=findLoadedClass(name);
                if(type==null)try(InputStream input=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                    if(input==null)throw new ClassNotFoundException(name);byte[] bytes=input.readAllBytes();type=defineClass(name,bytes,0,bytes.length);
                }catch(IOException e){throw new ClassNotFoundException(name,e);}
                if(resolve)resolveClass(type);return type;
            }
        };
        return Class.forName(store,true,loader);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

package com.mikasrevs.newphone.phone.device;

/** Hardware/presentation identity only. Never an account or storage namespace. */
public enum PhoneDeviceType {
    MPHONE("mphone", "MPhone", true), IPHONE_DUO("iphone_duo", "iPhone Duo", false);
    private final String id,name;
    private final boolean cases;
    PhoneDeviceType(String id,String name,boolean cases){this.id=id;this.name=name;this.cases=cases;}
    public String id(){return id;}
    public String displayName(){return name;}
    public boolean supportsCases(){return cases;}
}

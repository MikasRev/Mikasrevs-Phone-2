package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.security.PhoneSecurityStore;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import net.minecraft.network.chat.Component;

public final class ZPhonePinEntryScreen extends AbstractZPhonePinScreen {
    private boolean unlocked;private long openHomeAt;
    public ZPhonePinEntryScreen(){super(Component.literal("MPhone PIN"));}
    @Override protected String heading(){return unlocked?PhoneSettingsStore.text("Kilit Açıldı","Unlocked"):PhoneSettingsStore.text("PIN'i Gir","Enter your PIN");}
    @Override protected String subtitle(){String name=com.mikasrevs.newphone.client.device.PhoneDeviceSession.current().displayName();return unlocked?PhoneSettingsStore.text(name+" açılıyor...","Opening "+name+"..."):PhoneSettingsStore.text(name+" kilidini aç","Unlock "+name);}
    @Override protected void onPinComplete(String pin){if(PhoneSecurityStore.verifyPin(pin)){unlocked=true;openHomeAt=System.currentTimeMillis()+320L;showStatus(PhoneSettingsStore.text("PIN doğru","PIN accepted"),650L);}else{clearPin();showStatus(PhoneSettingsStore.text("Yanlış PIN","Incorrect PIN"),1200L);}}
    @Override public void tick(){super.tick();if(unlocked&&openHomeAt>0&&System.currentTimeMillis()>=openHomeAt)ZPhoneClient.unlockToHome();}
}

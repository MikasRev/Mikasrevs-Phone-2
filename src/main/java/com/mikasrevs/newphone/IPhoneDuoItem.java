package com.mikasrevs.newphone;

import com.mikasrevs.newphone.phone.device.PhoneDeviceType;
import java.util.function.Consumer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** A second handset for the existing MPhone account, network and use/call behavior. */
public final class IPhoneDuoItem extends ZPhoneItem {
    public IPhoneDuoItem(Properties properties){super(properties);}
    @Override public PhoneDeviceType deviceType(){return PhoneDeviceType.IPHONE_DUO;}
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){
        consumer.accept(com.mikasrevs.newphone.client.duo.DuoItemExtensions.INSTANCE);
    }
}

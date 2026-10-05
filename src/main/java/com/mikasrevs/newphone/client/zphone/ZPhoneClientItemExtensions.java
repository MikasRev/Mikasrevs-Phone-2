package com.mikasrevs.newphone.client.zphone;

import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Player call arms are rendered only by PhoneCallPlayerLayer; no straight-arm fallback. */
public final class ZPhoneClientItemExtensions implements IClientItemExtensions {
    public static final ZPhoneClientItemExtensions INSTANCE = new ZPhoneClientItemExtensions();

    private ZPhoneClientItemExtensions() {}
}

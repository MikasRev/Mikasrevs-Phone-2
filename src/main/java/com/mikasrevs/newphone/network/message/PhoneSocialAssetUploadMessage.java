package com.mikasrevs.newphone.network.message;

import com.mikasrevs.newphone.server.PhoneServerService;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C2S profile/avatar/cover image upload for social apps. */
public final class PhoneSocialAssetUploadMessage {
    public static final int MAX_IMAGE_BYTES = 30_000;
    private final String kind; private final byte[] imageData;
    public PhoneSocialAssetUploadMessage(String kind,byte[] imageData){this.kind=kind==null?"":kind;this.imageData=imageData==null?new byte[0]:imageData;}
    public String kind(){return kind;} public byte[] imageData(){return imageData;}
    public static void encode(PhoneSocialAssetUploadMessage m,FriendlyByteBuf b){b.writeUtf(m.kind,32);b.writeByteArray(m.imageData);}
    public static PhoneSocialAssetUploadMessage decode(FriendlyByteBuf b){return new PhoneSocialAssetUploadMessage(b.readUtf(32),b.readByteArray(MAX_IMAGE_BYTES));}
    public static void handle(PhoneSocialAssetUploadMessage m,Supplier<NetworkEvent.Context> supplier){NetworkEvent.Context c=supplier.get();ServerPlayer s=c.getSender();if(s!=null&&com.mikasrevs.newphone.network.PhoneRequestLimits.allow(s,com.mikasrevs.newphone.network.PhoneRequestLimits.Kind.PHOTO))c.enqueueWork(() -> PhoneServerService.handleSocialAssetUpload(s,m));c.setPacketHandled(true);}
}

package com.mikasrevs.newphone.network.message;

import java.util.function.Supplier;
import com.mikasrevs.newphone.network.PhoneMessageLimits;
import com.mikasrevs.newphone.server.PhoneServerService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Generic C2S action packet used by the phone apps. */
public final class PhoneActionMessage {
    private final String action;
    private final String targetUuid;
    private final String body;

    public PhoneActionMessage(String action, String targetUuid, String body) {
        this.action = action == null ? "" : action;
        this.targetUuid = targetUuid == null ? "" : targetUuid;
        this.body = body == null ? "" : body;
    }

    public String action() {
        return action;
    }

    public String targetUuid() {
        return targetUuid;
    }

    public String body() {
        return body;
    }

    public static void encode(PhoneActionMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.action, 32);
        buffer.writeUtf(message.targetUuid, 64);
        buffer.writeUtf(message.body, PhoneMessageLimits.ACTION_BODY_LENGTH);
    }

    public static PhoneActionMessage decode(FriendlyByteBuf buffer) {
        return new PhoneActionMessage(
                buffer.readUtf(32),
                buffer.readUtf(64),
                buffer.readUtf(PhoneMessageLimits.ACTION_BODY_LENGTH)
        );
    }

    public static void handle(PhoneActionMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null && com.mikasrevs.newphone.network.PhoneRequestLimits.allow(sender,message.action())) {
            context.enqueueWork(() -> PhoneServerService.handleAction(sender, message));
        }
        context.setPacketHandled(true);
    }
}

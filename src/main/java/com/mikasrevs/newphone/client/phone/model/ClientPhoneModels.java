package com.mikasrevs.newphone.client.phone.model;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.PhoneInventoryUtil;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.phone.model.PhoneModelDefinition;
import com.mikasrevs.newphone.phone.model.PhoneModelRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NewPhone.MOD_ID, value = Dist.CLIENT)
public final class ClientPhoneModels {
    private static final Map<UUID, String> SELECTED = new HashMap<>();
    private ClientPhoneModels() {}
    public static void receive(UUID player, String id) {
        if (id.isEmpty()) SELECTED.remove(player);
        else SELECTED.put(player, PhoneModelRegistry.get(id).id());
    }
    public static PhoneModelDefinition selected(LivingEntity entity, ItemStack stack) {
        String id = entity == null ? null : SELECTED.get(entity.getUUID());
        return id == null ? PhoneModelRegistry.fromStack(stack) : PhoneModelRegistry.get(id);
    }
    public static PhoneModelDefinition local() {
        var player = Minecraft.getInstance().player;
        return selected(player, PhoneInventoryUtil.findPhone(player));
    }
    public static String label(String value) {
        return PhoneSettingsStore.isChinese() ? PhoneSettingsStore.text("", value) : value;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { SELECTED.clear(); }
}

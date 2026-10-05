package com.mikasrevs.newphone.client.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** Lightweight 1.20.1 player-head renderer used by phone communication screens. */
public final class PhonePlayerHeadRenderer {
    private static final Map<UUID, ResourceLocation> SKIN_CACHE = new ConcurrentHashMap<>();

    private PhonePlayerHeadRenderer() {
    }

    public static void draw(GuiGraphics graphics, String uuidRaw, int x, int y, int size) {
        ResourceLocation skin = findSkin(uuidRaw);
        if (skin == null) return;
        graphics.blit(skin, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        graphics.blit(skin, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    private static ResourceLocation findSkin(String uuidRaw) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidRaw == null ? "" : uuidRaw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return SKIN_CACHE.getOrDefault(uuid, DefaultPlayerSkin.getDefaultSkin(uuid));
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                ResourceLocation skin = info.getSkinLocation();
                SKIN_CACHE.put(uuid, skin);
                return skin;
            }
        }
        if (minecraft.level != null) {
            for (AbstractClientPlayer player : minecraft.level.players()) {
                if (uuid.equals(player.getUUID())) {
                    ResourceLocation skin = player.getSkinTextureLocation();
                    SKIN_CACHE.put(uuid, skin);
                    return skin;
                }
            }
        }
        return SKIN_CACHE.getOrDefault(uuid, DefaultPlayerSkin.getDefaultSkin(uuid));
    }
}

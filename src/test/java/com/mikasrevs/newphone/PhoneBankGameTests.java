package com.mikasrevs.newphone;

import com.mikasrevs.newphone.integration.currency.*;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;

/** Optional dependency checks execute inside a real Forge server, including a no-provider classpath. */
@GameTestHolder("newphone")
@PrefixGameTestTemplate(false)
public final class PhoneBankGameTests {
    @GameTest(templateNamespace="forge", template="empty3x3", timeoutTicks=200)
    public static void selectedEconomyAndNativeTransfers(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer();
        // Kuro deliberately does not attach its bank capability to Forge FakePlayer instances.
        var a = new ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), "BankSender"));
        var b = new ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), "BankRecipient"));
        a.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), a);
        b.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), b);
        @SuppressWarnings("unchecked") var ids = (Map<UUID,ServerPlayer>) field(server.getPlayerList().getClass(), "playersByUUID").get(server.getPlayerList());
        @SuppressWarnings("unchecked") var roster = (List<ServerPlayer>) field(server.getPlayerList().getClass(), "players").get(server.getPlayerList());
        ids.put(a.getUUID(), a); ids.put(b.getUUID(), b); roster.add(a); roster.add(b);
        try {
            var selected = PhoneCurrencyProvider.select(ModList.get()::isLoaded);
            var state = PhoneCurrencyManager.snapshot(a);
            h.assertTrue(state.provider().equals(selected.displayName), "Wrong selected economy: " + state.provider());
            if (selected == PhoneCurrencyProvider.NONE) {
                h.assertTrue(!state.available() && state.status().equals("MOD_MISSING"), "Absent optional providers broke bank state");
                h.assertTrue(PhoneCurrencyManager.transfer(a, b.getUUID(), "unknown", 500).status()
                        == PhoneCurrencyIntegration.TransferStatus.UNAVAILABLE, "Missing provider transfer was not rejected");
            } else {
                h.assertTrue(state.available(), "Installed bank account unavailable: " + state.status());
                String test = selected == PhoneCurrencyProvider.KUROS ? "KurosBankGameChecks" : "LightmansBankGameChecks";
                Class.forName("com.mikasrevs.newphone." + test).getMethod("run", GameTestHelper.class,
                        ServerPlayer.class, ServerPlayer.class).invoke(null, h, a, b);
                BankRecoveryGameChecks.run(h, a, b);
            }
            System.out.println("Phone bank native GameTest passed: selected=" + selected + ", Lightmans="
                    + ModList.get().isLoaded("lightmanscurrency") + ", Kuros=" + ModList.get().isLoaded("kuronomy"));
            h.succeed();
        } finally {
            ids.remove(a.getUUID()); ids.remove(b.getUUID()); roster.remove(a); roster.remove(b);
        }
    }

    static Field field(Class<?> type, String name) throws NoSuchFieldException {
        while (type != null) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException exception) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}

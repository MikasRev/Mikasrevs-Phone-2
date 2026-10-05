package com.mikasrevs.newphone;

import com.mikasrevs.newphone.network.PhoneNetwork;
import com.mikasrevs.newphone.emergency.EmergencyService;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** MPhone Forge 1.20.1 bootstrap. */
@Mod(NewPhone.MOD_ID)
public final class NewPhone {
    public static final String MOD_ID = "newphone";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<Item> Z_PHONE = ITEMS.register(
            "z_phone",
            () -> new ZPhoneItem(new Item.Properties().stacksTo(1))
    );
    // Keep existing stacks valid while Duo is temporarily hidden from Creative.
    public static final RegistryObject<Item> IPHONE_DUO = ITEMS.register(
            "iphone_duo", () -> new IPhoneDuoItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> POLICE_TABLET = ITEMS.register(
            "police_tablet",
            () -> new ResponderTabletItem(EmergencyService.POLICE, new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> EMS_TABLET = ITEMS.register(
            "ems_tablet",
            () -> new ResponderTabletItem(EmergencyService.EMS, new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> BUNNY_BLUSH_PHONE_CASE = registerPhoneCase(
            "bunny_blush_phone_case", PhoneCaseStyle.BUNNY_BLUSH
    );
    public static final RegistryObject<Item> CARBON_GUARD_PHONE_CASE = registerPhoneCase(
            "carbon_guard_phone_case", PhoneCaseStyle.CARBON_GUARD
    );
    public static final RegistryObject<Item> FOREST_LEATHER_PHONE_CASE = registerPhoneCase(
            "forest_leather_phone_case", PhoneCaseStyle.FOREST_LEATHER
    );
    public static final RegistryObject<Item> MIDNIGHT_CYBER_PHONE_CASE = registerPhoneCase(
            "midnight_cyber_phone_case", PhoneCaseStyle.MIDNIGHT_CYBER
    );
    public static final RegistryObject<Item> BUNNY_BLOCK_PHONE_CASE = registerPhoneCase(
            "bunny_block_phone_case", PhoneCaseStyle.BUNNY_BLOCK
    );

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.newphone"))
                    .icon(() -> new ItemStack(Z_PHONE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(Z_PHONE.get());
                        output.accept(POLICE_TABLET.get());
                        output.accept(EMS_TABLET.get());
                        output.accept(BUNNY_BLUSH_PHONE_CASE.get());
                        output.accept(CARBON_GUARD_PHONE_CASE.get());
                        output.accept(FOREST_LEATHER_PHONE_CASE.get());
                        output.accept(MIDNIGHT_CYBER_PHONE_CASE.get());
                        output.accept(BUNNY_BLOCK_PHONE_CASE.get());
                    })
                    .build()
    );

    private static RegistryObject<Item> registerPhoneCase(String id, PhoneCaseStyle style) {
        return ITEMS.register(id, () -> new PhoneCaseItem(style, new Item.Properties().stacksTo(16)));
    }

    public static ItemStack phoneCaseStack(PhoneCaseStyle style) {
        Item item = switch (style) {
            case BUNNY_BLUSH -> BUNNY_BLUSH_PHONE_CASE.get();
            case CARBON_GUARD -> CARBON_GUARD_PHONE_CASE.get();
            case FOREST_LEATHER -> FOREST_LEATHER_PHONE_CASE.get();
            case MIDNIGHT_CYBER -> MIDNIGHT_CYBER_PHONE_CASE.get();
            case BUNNY_BLOCK -> BUNNY_BLOCK_PHONE_CASE.get();
            case NONE -> throw new IllegalArgumentException("No item exists for the empty case style");
        };
        return new ItemStack(item);
    }

    public NewPhone() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        PhoneSounds.SOUND_EVENTS.register(modEventBus);
        PhoneNetwork.register();
    }
}

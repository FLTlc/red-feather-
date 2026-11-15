package com.example.redfeather;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(RedFeatherMod.MOD_ID)
public class RedFeatherMod {
    public static final String MOD_ID = "redfeather";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredItem<Item> RED_FEATHER = ITEMS.registerSimpleItem(
            "red_feather",
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
    );

    public static final DeferredItem<DivineStickItem> DIVINE_STICK = ITEMS.registerItem(
            "divine_stick",
            DivineStickItem::new,
            new Item.Properties()
    );

    public static final DeferredItem<PocketScepterItem> POCKET_SCEPTER = ITEMS.registerItem(
            "pocket_scepter",
            PocketScepterItem::new,
            new Item.Properties()
    );

    public RedFeatherMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        NeoForge.EVENT_BUS.addListener(PocketScepterItem::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PocketScepterItem::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(PocketScepterItem::onExplosion);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(RED_FEATHER);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(DIVINE_STICK);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(POCKET_SCEPTER);
        }
    }
}

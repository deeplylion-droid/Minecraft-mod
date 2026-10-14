package com.jumpscares.registry;

import com.jumpscares.Jumpscares;
import com.jumpscares.item.ScarePearlItem;
import com.jumpscares.scare.ScareKind;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.DispenserBlock;

/** One scare pearl per monster. */
public final class ModItems {
    private static final ScarePearlItem[] PEARLS = new ScarePearlItem[ScareKind.values().length];

    static {
        for (ScareKind kind : ScareKind.values()) {
            PEARLS[kind.ordinal()] = Registry.register(BuiltInRegistries.ITEM, Jumpscares.id(kind.id + "_pearl"),
                    new ScarePearlItem(kind, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
        }
    }

    private ModItems() {
    }

    public static ScarePearlItem pearl(ScareKind kind) {
        return PEARLS[kind.ordinal()];
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.addAfter(Items.ENDER_EYE, PEARLS));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> entries.addAfter(Items.WIND_CHARGE, PEARLS));
        // dispensers shoot them too: a trap behind a pressure plate
        for (ScarePearlItem pearl : PEARLS) {
            DispenserBlock.registerProjectileBehavior(pearl);
        }
    }
}

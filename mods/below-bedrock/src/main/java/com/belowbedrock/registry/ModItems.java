package com.belowbedrock.registry;

import com.belowbedrock.BelowBedrock;
import com.belowbedrock.item.GlitchedPickaxeItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.Tiers;

public final class ModItems {
    public static final Item GLITCHED_PICKAXE = Registry.register(BuiltInRegistries.ITEM, BelowBedrock.id("glitched_pickaxe"),
            new GlitchedPickaxeItem(new Item.Properties().attributes(PickaxeItem.createAttributes(Tiers.DIAMOND, 1.0F, -2.8F))));
    public static final Item INHABITANT_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, BelowBedrock.id("inhabitant_spawn_egg"),
            new SpawnEggItem(ModEntities.INHABITANT, 0xD6D3CB, 0x121212, new Item.Properties()));

    private ModItems() {
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.addAfter(Items.DIAMOND_PICKAXE, GLITCHED_PICKAXE));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(INHABITANT_SPAWN_EGG));
    }
}

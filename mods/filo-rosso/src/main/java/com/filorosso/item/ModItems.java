package com.filorosso.item;

import com.filorosso.FiloRosso;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.function.Function;

public final class ModItems {
    private ModItems() {
    }

    /** Dropped by Recisione's servants. */
    public static final Item HEART_SHARD = register("heart_shard", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    /** Eight shards around a gold block: use it at night to call Recisione. */
    public static final Item BROKEN_HEART = register("broken_heart", BrokenHeartItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    /** The trophy of whoever defeats it. */
    public static final Item GOLDEN_HEART = register("golden_heart", GoldenHeartItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
    /** The trophy of whoever brings down the Burattinaio. */
    public static final Item PUPPETEER_MASK = register("puppeteer_mask", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
    /** The servants' scissors. */
    public static final Item SCISSORS = register("scissors", Item::new, new Item.Properties().stacksTo(1));

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, FiloRosso.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
            output.accept(HEART_SHARD);
            output.accept(BROKEN_HEART);
            output.accept(GOLDEN_HEART);
            output.accept(PUPPETEER_MASK);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
        });
    }
}

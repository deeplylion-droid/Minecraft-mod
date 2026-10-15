package com.disabilities.item;

import com.disabilities.Disabilities;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.function.Function;

/** The aids: white cane (blind), inhaler (asthma), bandage (hemophilia). */
public final class ModItems {
    private ModItems() {
    }

    public static final Item WHITE_CANE = register("white_cane", WhiteCaneItem::new, new Item.Properties().stacksTo(1));
    public static final Item INHALER = register("inhaler", InhalerItem::new, new Item.Properties().durability(16));
    public static final Item BANDAGE = register("bandage", BandageItem::new, new Item.Properties().stacksTo(16));

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Disabilities.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(WHITE_CANE);
            output.accept(INHALER);
            output.accept(BANDAGE);
        });
    }
}

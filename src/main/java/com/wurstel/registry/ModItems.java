package com.wurstel.registry;

import com.wurstel.WurstelMod;
import com.wurstel.item.WurstelBeltItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, WurstelMod.MODID);

    /** An ordinary (edible) würstel, used to craft the belt. */
    public static final RegistryObject<Item> WURSTEL = ITEMS.register("wurstel",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4)
                    .saturationMod(0.6F)
                    .meat()
                    .build())));

    /** The belt carrying the giant würstel, worn in the leggings slot. */
    public static final RegistryObject<Item> WURSTEL_BELT = ITEMS.register("wurstel_belt",
            () -> new WurstelBeltItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}

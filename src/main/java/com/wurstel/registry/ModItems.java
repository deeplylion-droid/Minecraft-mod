package com.wurstel.registry;

import com.wurstel.WurstelMod;
import com.wurstel.item.WurstelArmorMaterial;
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
            () -> new WurstelBeltItem(WurstelArmorMaterial.WURSTEL, new Item.Properties().stacksTo(1)));

    /** The belt upgraded with diamonds at the crafting table. */
    public static final RegistryObject<Item> DIAMOND_WURSTEL_BELT = ITEMS.register("diamond_wurstel_belt",
            () -> new WurstelBeltItem(WurstelArmorMaterial.DIAMOND, new Item.Properties().stacksTo(1)));

    /** The diamond belt upgraded with netherite at the smithing table. */
    public static final RegistryObject<Item> NETHERITE_WURSTEL_BELT = ITEMS.register("netherite_wurstel_belt",
            () -> new WurstelBeltItem(WurstelArmorMaterial.NETHERITE, new Item.Properties().stacksTo(1).fireResistant()));

    private ModItems() {
    }
}

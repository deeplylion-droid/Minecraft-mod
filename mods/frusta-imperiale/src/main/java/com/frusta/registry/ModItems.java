package com.frusta.registry;

import com.frusta.FrustaMod;
import com.frusta.item.WhipItem;
import com.frusta.item.WhipMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FrustaMod.MODID);

    /** The black leather whip. */
    public static final RegistryObject<Item> WHIP = ITEMS.register("whip",
            () -> new WhipItem(WhipMaterial.LEATHER, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** The whip upgraded with diamonds at the crafting table. */
    public static final RegistryObject<Item> DIAMOND_WHIP = ITEMS.register("diamond_whip",
            () -> new WhipItem(WhipMaterial.DIAMOND, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** The diamond whip upgraded with netherite at the smithing table. */
    public static final RegistryObject<Item> NETHERITE_WHIP = ITEMS.register("netherite_whip",
            () -> new WhipItem(WhipMaterial.NETHERITE, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    private ModItems() {
    }
}

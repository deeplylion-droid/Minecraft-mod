package com.rainbowyoyo.registry;

import com.rainbowyoyo.RainbowYoyoMod;
import com.rainbowyoyo.item.YoyoItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RainbowYoyoMod.MODID);

    /** The giant rainbow yo-yo. */
    public static final RegistryObject<Item> RAINBOW_YOYO = ITEMS.register("rainbow_yoyo",
            () -> new YoyoItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    private ModItems() {
    }
}

package com.infezione.registry;

import com.infezione.InfezioneMod;
import com.infezione.item.AntidoteItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, InfezioneMod.MODID);

    /** The antidote: a syringe that cures the infection up to stage 4. */
    public static final RegistryObject<Item> ANTIDOTE = ITEMS.register("antidoto",
            () -> new AntidoteItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    private ModItems() {
    }
}

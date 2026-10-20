package com.frusta.registry;

import com.frusta.FrustaMod;
import com.frusta.enchantment.SottomissioneEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, FrustaMod.MODID);

    /** Doubles the whip's damage at every level and makes whipped targets kneel. */
    public static final RegistryObject<Enchantment> SOTTOMISSIONE =
            ENCHANTMENTS.register("sottomissione", SottomissioneEnchantment::new);

    private ModEnchantments() {
    }
}

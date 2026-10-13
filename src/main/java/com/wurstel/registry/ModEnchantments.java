package com.wurstel.registry;

import com.wurstel.WurstelMod;
import com.wurstel.enchantment.TurgidoEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, WurstelMod.MODID);

    /** Multiplies the damage of würstel slaps. */
    public static final RegistryObject<Enchantment> TURGIDO = ENCHANTMENTS.register("turgido", TurgidoEnchantment::new);

    private ModEnchantments() {
    }
}

package com.rainbowyoyo.registry;

import com.rainbowyoyo.RainbowYoyoMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/** Damage types of the mod, defined in {@code data/rainbowyoyo/damage_type/}. */
public final class ModDamageTypes {
    /** A hit of the yo-yo, with its own death messages. */
    public static final ResourceKey<DamageType> YOYO =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(RainbowYoyoMod.MODID, "yoyo"));

    private ModDamageTypes() {
    }
}

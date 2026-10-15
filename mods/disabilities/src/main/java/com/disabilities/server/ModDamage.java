package com.disabilities.server;

import com.disabilities.Disabilities;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** Damage types (data/disabilities/damage_type): they give their own death messages. */
public final class ModDamage {
    private ModDamage() {
    }

    public static final ResourceKey<DamageType> BLEEDING = ResourceKey.create(Registries.DAMAGE_TYPE, Disabilities.id("bleeding"));
    public static final ResourceKey<DamageType> SUNBURN = ResourceKey.create(Registries.DAMAGE_TYPE, Disabilities.id("sunburn"));
}

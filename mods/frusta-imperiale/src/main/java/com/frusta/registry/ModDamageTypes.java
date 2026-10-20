package com.frusta.registry;

import com.frusta.FrustaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** Damage types of the mod, defined in {@code data/frusta/damage_type/} (never scaled by difficulty). */
public final class ModDamageTypes {
    /** A lash or a grab. */
    public static final ResourceKey<DamageType> WHIP = ResourceKey.create(Registries.DAMAGE_TYPE, FrustaMod.id("whip"));
    /** The Imperial Crack. */
    public static final ResourceKey<DamageType> IMPERIAL = ResourceKey.create(Registries.DAMAGE_TYPE, FrustaMod.id("imperial"));

    private ModDamageTypes() {
    }
}

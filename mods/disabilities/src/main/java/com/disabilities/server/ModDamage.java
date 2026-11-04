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
    /** Alien hand: slapped by your own hand. */
    public static final ResourceKey<DamageType> ALIEN_HAND = ResourceKey.create(Registries.DAMAGE_TYPE, Disabilities.id("alien_hand"));
    /** Gigantism (and blind people without the cane): hitting your head or a wall. */
    public static final ResourceKey<DamageType> BONK = ResourceKey.create(Registries.DAMAGE_TYPE, Disabilities.id("bonk"));
    /** Weak heart: nobody did the CPR in time. */
    public static final ResourceKey<DamageType> HEART_ATTACK = ResourceKey.create(Registries.DAMAGE_TYPE, Disabilities.id("heart_attack"));
}

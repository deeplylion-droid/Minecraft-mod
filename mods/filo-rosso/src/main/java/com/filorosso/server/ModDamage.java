package com.filorosso.server;

import com.filorosso.FiloRosso;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** Damage types (data/filorosso/damage_type): they give their own death messages. */
public final class ModDamage {
    private ModDamage() {
    }

    /** Hit by a taut thread. */
    public static final ResourceKey<DamageType> THREAD = ResourceKey.create(Registries.DAMAGE_TYPE, FiloRosso.id("thread"));
    /** Nobody came to revive you in time. */
    public static final ResourceKey<DamageType> ABANDONED = ResourceKey.create(Registries.DAMAGE_TYPE, FiloRosso.id("abandoned"));
    /** Lightning running along the thread (storm event). */
    public static final ResourceKey<DamageType> STORM = ResourceKey.create(Registries.DAMAGE_TYPE, FiloRosso.id("storm"));
    /** Lo Spezzacuori's scissors. */
    public static final ResourceKey<DamageType> SCISSORS = ResourceKey.create(Registries.DAMAGE_TYPE, FiloRosso.id("scissors"));
}

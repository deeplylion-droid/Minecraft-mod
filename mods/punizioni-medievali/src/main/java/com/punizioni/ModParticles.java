package com.punizioni;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModParticles {
    private ModParticles() {
    }

    /** A drop of blood: falls, and leaves a small stain where it lands. */
    public static final SimpleParticleType BLOOD = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Punizioni.id("blood"),
            FabricParticleTypes.simple(true));
    /** A glowing ember that rises from the stake and fades. */
    public static final SimpleParticleType EMBER = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Punizioni.id("ember"),
            FabricParticleTypes.simple(true));
    /** Grey ash falling around the burnt stake. */
    public static final SimpleParticleType ASH = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Punizioni.id("ash"),
            FabricParticleTypes.simple(true));

    public static void init() {
    }
}

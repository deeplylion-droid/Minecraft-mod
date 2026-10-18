package com.chiappe;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** A green-brown puff of gas (assets/chiappe/particles/gas.json). */
public final class ModParticles {
    private ModParticles() {
    }

    public static final SimpleParticleType GAS = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Chiappe.id("gas"),
            FabricParticleTypes.simple(true));

    public static void init() {
    }
}

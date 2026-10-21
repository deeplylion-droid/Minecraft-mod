package com.infezione.registry;

import com.infezione.InfezioneMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, InfezioneMod.MODID);

    /** A fly buzzing around a dying or dead player. */
    public static final RegistryObject<SimpleParticleType> FLY = PARTICLES.register("fly", () -> new SimpleParticleType(false));
    /** A small cloud coughed out. */
    public static final RegistryObject<SimpleParticleType> COUGH = PARTICLES.register("cough", () -> new SimpleParticleType(false));
    /** A small cloud coughed out with blood. */
    public static final RegistryObject<SimpleParticleType> COUGH_BLOOD = PARTICLES.register("cough_blood", () -> new SimpleParticleType(false));
    /** A drop of pus out of a burst boil. */
    public static final RegistryObject<SimpleParticleType> PUS = PARTICLES.register("pus", () -> new SimpleParticleType(false));
    /** A drop of blood that falls and stains the ground for a moment. */
    public static final RegistryObject<SimpleParticleType> BLOOD = PARTICLES.register("blood", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}

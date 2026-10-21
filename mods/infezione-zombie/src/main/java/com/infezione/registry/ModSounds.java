package com.infezione.registry;

import com.infezione.InfezioneMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, InfezioneMod.MODID);

    /** A dry cough. */
    public static final RegistryObject<SoundEvent> COUGH = register("cough");
    /** A wet cough that brings up blood. */
    public static final RegistryObject<SoundEvent> COUGH_BLOOD = register("cough_blood");
    /** Bones cracking in a convulsion. */
    public static final RegistryObject<SoundEvent> BONE_CRACK = register("bone_crack");
    /** The infected player's own heartbeat (only they hear it). */
    public static final RegistryObject<SoundEvent> HEARTBEAT = register("heartbeat");
    /** The infection gets worse: a sting for the infected player. */
    public static final RegistryObject<SoundEvent> STAGE_UP = register("stage_up");
    /** The transformation into a zombie, heard by everyone around. */
    public static final RegistryObject<SoundEvent> TRANSFORM = register("transform");
    /** The needle of the antidote. */
    public static final RegistryObject<SoundEvent> INJECT = register("inject");
    /** The infection goes away. */
    public static final RegistryObject<SoundEvent> CURE = register("cure");
    /** Flies buzzing around the dying and the dead. */
    public static final RegistryObject<SoundEvent> FLIES = register("flies");
    /** A boil bursting. */
    public static final RegistryObject<SoundEvent> BOIL_POP = register("boil_pop");
    /** A raspy breath. */
    public static final RegistryObject<SoundEvent> BREATH = register("breath");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(InfezioneMod.id(name)));
    }

    private ModSounds() {
    }
}

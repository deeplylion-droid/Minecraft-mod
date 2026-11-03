package com.punizioni;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** The sounds of the punishments, all synthesized (see the mod's notes in CLAUDE.md). */
public final class ModSounds {
    private ModSounds() {
    }

    public static final SoundEvent LASH = register("lash");
    public static final SoundEvent WHOOSH = register("whoosh");
    public static final SoundEvent SCREAM = register("scream");
    public static final SoundEvent SCREAM_MUFFLED = register("scream_muffled");
    public static final SoundEvent MAIDEN_CREAK = register("maiden_creak");
    public static final SoundEvent MAIDEN_SLAM = register("maiden_slam");
    public static final SoundEvent SPIKES = register("spikes");
    public static final SoundEvent ROPE_CREAK = register("rope_creak");
    public static final SoundEvent BONE_CRACK = register("bone_crack");
    public static final SoundEvent TEAR = register("tear");
    public static final SoundEvent CHOP = register("chop");
    public static final SoundEvent RATS = register("rats");
    public static final SoundEvent GNAW = register("gnaw");
    public static final SoundEvent FIRE_IGNITE = register("fire_ignite");
    public static final SoundEvent FIRE_ROAR = register("fire_roar");
    public static final SoundEvent SIZZLE = register("sizzle");
    public static final SoundEvent CHAINS = register("chains");
    public static final SoundEvent DRUMS = register("drums");
    public static final SoundEvent TOLL = register("toll");
    public static final SoundEvent BELLOW = register("bellow");
    public static final SoundEvent CROW = register("crow");
    public static final SoundEvent MALLET = register("mallet");
    public static final SoundEvent CRACK = register("crack");
    public static final SoundEvent FANFARE = register("fanfare");
    public static final SoundEvent JINGLE = register("jingle");
    public static final SoundEvent SPLAT = register("splat");
    public static final SoundEvent PLOP = register("plop");
    public static final SoundEvent FLIES = register("flies");

    private static SoundEvent register(String name) {
        Identifier id = Punizioni.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
    }
}

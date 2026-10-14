package com.jumpscares.registry;

import com.jumpscares.Jumpscares;
import com.jumpscares.scare.ScareKind;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Synthesized screams, the orchestral sting and the whoosh of the lunge. */
public final class ModSounds {
    public static final SoundEvent STING = register("sting");
    public static final SoundEvent WHOOSH = register("whoosh");
    private static final SoundEvent[] INTRO = new SoundEvent[ScareKind.values().length];
    private static final SoundEvent[] SCREAM = new SoundEvent[ScareKind.values().length];

    static {
        for (ScareKind kind : ScareKind.values()) {
            INTRO[kind.ordinal()] = register(kind.id + ".intro");
            SCREAM[kind.ordinal()] = register(kind.id + ".scream");
        }
    }

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        ResourceLocation id = Jumpscares.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Played as the monster appears. */
    public static SoundEvent intro(ScareKind kind) {
        return INTRO[kind.ordinal()];
    }

    /** Played when it reaches your face. */
    public static SoundEvent scream(ScareKind kind) {
        return SCREAM[kind.ordinal()];
    }

    public static void register() {
    }
}

package com.chiappe;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** The farts (assets/chiappe/sounds.json): bigger cheeks, longer and deeper. */
public final class ModSounds {
    private ModSounds() {
    }

    public static final SoundEvent FART_SMALL = register("fart_small");
    public static final SoundEvent FART_MEDIUM = register("fart_medium");
    public static final SoundEvent FART_BIG = register("fart_big");
    public static final SoundEvent FART_ATOMIC = register("fart_atomic");

    private static SoundEvent register(String name) {
        Identifier id = Chiappe.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** The fart for this level. */
    public static SoundEvent fart(int level) {
        return level >= Butt.MAX_LEVEL ? FART_ATOMIC : level >= 17 ? FART_BIG : level >= 8 ? FART_MEDIUM : FART_SMALL;
    }

    public static void init() {
    }
}

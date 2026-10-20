package com.disabilities.sound;

import com.disabilities.Disabilities;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** The mod's sounds (assets/disabilities/sounds.json). */
public final class ModSounds {
    private ModSounds() {
    }

    public static final SoundEvent CANE_TAP = register("cane_tap");
    public static final SoundEvent SONAR = register("sonar");
    public static final SoundEvent SNEEZE = register("sneeze");
    public static final SoundEvent SNORE = register("snore");
    public static final SoundEvent YAWN = register("yawn");
    public static final SoundEvent GASP = register("gasp");
    public static final SoundEvent WHEEZE = register("wheeze");
    public static final SoundEvent INHALER = register("inhaler");
    public static final SoundEvent PANT = register("pant");
    public static final SoundEvent TIC_HM = register("tic_hm");
    public static final SoundEvent TIC_WHISTLE = register("tic_whistle");
    public static final SoundEvent TIC_HEY = register("tic_hey");
    public static final SoundEvent TIC_CLICK = register("tic_click");
    public static final SoundEvent TIC_SNIFF = register("tic_sniff");
    public static final SoundEvent TIC_GRUNT = register("tic_grunt");

    public static final SoundEvent[] VOCAL_TICS = {TIC_HM, TIC_WHISTLE, TIC_HEY, TIC_CLICK, TIC_SNIFF, TIC_GRUNT};

    private static SoundEvent register(String name) {
        Identifier id = Disabilities.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
    }
}

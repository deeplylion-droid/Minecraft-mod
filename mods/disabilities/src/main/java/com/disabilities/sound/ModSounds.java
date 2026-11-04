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

    // 1.1.0
    public static final SoundEvent HEARTBEAT = register("heartbeat");
    public static final SoundEvent FLATLINE = register("flatline");
    public static final SoundEvent ECG_BEEP = register("ecg_beep");
    public static final SoundEvent CPR_PUSH = register("cpr_push");
    public static final SoundEvent BONE_CRACK = register("bone_crack");
    public static final SoundEvent SLAP = register("slap");
    public static final SoundEvent BONK = register("bonk");
    public static final SoundEvent GIANT_STEP = register("giant_step");
    public static final SoundEvent SEIZURE = register("seizure");
    public static final SoundEvent HYPERVENTILATE = register("hyperventilate");
    public static final SoundEvent COUGH = register("cough");
    public static final SoundEvent CRUTCH = register("crutch");
    public static final SoundEvent SQUEAK = register("squeak");
    public static final SoundEvent MMH = register("mmh");
    public static final SoundEvent THUD = register("thud");

    public static final SoundEvent[] VOCAL_TICS = {TIC_HM, TIC_WHISTLE, TIC_HEY, TIC_CLICK, TIC_SNIFF, TIC_GRUNT};

    private static SoundEvent register(String name) {
        Identifier id = Disabilities.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
    }
}

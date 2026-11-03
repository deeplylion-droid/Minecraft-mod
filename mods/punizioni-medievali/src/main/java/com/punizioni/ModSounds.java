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
    public static final SoundEvent ROAST_TEAR = register("roast_tear");
    public static final SoundEvent GRUEL_BUBBLE = register("gruel_bubble");
    public static final SoundEvent GRUEL_LADLE = register("gruel_ladle");
    public static final SoundEvent WITCH_CACKLE = register("witch_cackle");
    public static final SoundEvent CAULDRON_BUBBLE = register("cauldron_bubble");
    public static final SoundEvent CAULDRON_SPLASH = register("cauldron_splash");
    public static final SoundEvent CAULDRON_READY = register("cauldron_ready");
    public static final SoundEvent CAULDRON_FAIL = register("cauldron_fail");
    public static final SoundEvent CAULDRON_SPIT = register("cauldron_spit");
    public static final SoundEvent CURSE_CAST = register("curse_cast");
    public static final SoundEvent TOAD_CROAK = register("toad_croak");
    public static final SoundEvent TOAD_TRANSFORM = register("toad_transform");
    public static final SoundEvent TONGUE = register("tongue");
    public static final SoundEvent BUTT_SLURP = register("butt_slurp");
    public static final SoundEvent MUFFLED = register("muffled");
    public static final SoundEvent BUTT_FART = register("butt_fart");
    public static final SoundEvent BALLOON_INFLATE = register("balloon_inflate");
    public static final SoundEvent BALLOON_SQUEAK = register("balloon_squeak");
    public static final SoundEvent BALLOON_POP = register("balloon_pop");
    public static final SoundEvent BALLOON_DEFLATE = register("balloon_deflate");

    private static SoundEvent register(String name) {
        Identifier id = Punizioni.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
    }
}

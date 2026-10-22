package com.filorosso.sound;

import com.filorosso.FiloRosso;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** The mod's sounds (assets/filorosso/sounds.json). */
public final class ModSounds {
    private ModSounds() {
    }

    public static final SoundEvent PROPOSAL = register("proposal");
    public static final SoundEvent BOND = register("bond");
    public static final SoundEvent REFUSE = register("refuse");
    public static final SoundEvent SNAP = register("snap");
    public static final SoundEvent CUT = register("cut");
    public static final SoundEvent LEVEL_UP = register("level_up");
    public static final SoundEvent PULL = register("pull");
    public static final SoundEvent TELEPORT = register("teleport");
    public static final SoundEvent GIFT = register("gift");
    public static final SoundEvent COMBO = register("combo");
    public static final SoundEvent HEARTBEAT = register("heartbeat");
    public static final SoundEvent DOWNED = register("downed");
    public static final SoundEvent REVIVE = register("revive");
    public static final SoundEvent EVENT = register("event");
    public static final SoundEvent SWAP = register("swap");
    public static final SoundEvent CHALLENGE = register("challenge");
    public static final SoundEvent TAUT = register("taut");
    public static final SoundEvent BOSS_ROAR = register("boss_roar");
    public static final SoundEvent BOSS_SLAM = register("boss_slam");
    public static final SoundEvent BOSS_SCREAM = register("boss_scream");
    public static final SoundEvent BOSS_HURT = register("boss_hurt");
    public static final SoundEvent BOSS_DEATH = register("boss_death");
    public static final SoundEvent BOSS_BELL = register("boss_bell");
    public static final SoundEvent BOSS_GLITCH = register("boss_glitch");
    public static final SoundEvent MISSILE = register("missile");
    public static final SoundEvent MISSILE_HIT = register("missile_hit");
    public static final SoundEvent GRASP = register("grasp");
    public static final SoundEvent SCISSORS = register("scissors");
    public static final SoundEvent MINION_AMBIENT = register("minion_ambient");
    public static final SoundEvent MINION_HURT = register("minion_hurt");
    public static final SoundEvent MINION_DEATH = register("minion_death");
    public static final SoundEvent VICTORY = register("victory");
    // the powers of levels 6-10
    public static final SoundEvent SLING = register("sling");
    public static final SoundEvent LANDING = register("landing");
    public static final SoundEvent LASSO = register("lasso");
    public static final SoundEvent DOME = register("dome");
    public static final SoundEvent DOME_DEFLECT = register("dome_deflect");
    public static final SoundEvent KISS_ASK = register("kiss_ask");
    public static final SoundEvent KISS = register("kiss");
    public static final SoundEvent ETERNAL = register("eternal");
    public static final SoundEvent ETERNAL_END = register("eternal_end");
    // the Theatre and the Burattinaio
    public static final SoundEvent THEATRE_CALL = register("theatre_call");
    public static final SoundEvent THEATRE_MUSIC = register("theatre_music");
    public static final SoundEvent GHOST_OFF = register("ghost_off");
    public static final SoundEvent CURTAIN = register("curtain");
    public static final SoundEvent APPLAUSE = register("applause");
    public static final SoundEvent PUPPETEER_AMBIENT = register("puppeteer_ambient");
    public static final SoundEvent PUPPETEER_HURT = register("puppeteer_hurt");
    public static final SoundEvent PUPPETEER_DEATH = register("puppeteer_death");
    public static final SoundEvent MASK_FALL = register("mask_fall");
    public static final SoundEvent PUPPET_STRINGS = register("puppet_strings");
    public static final SoundEvent PUPPET_HIT = register("puppet_hit");
    public static final SoundEvent KNOT_HIT = register("knot_hit");
    public static final SoundEvent SPEAR_FALL = register("spear_fall");
    public static final SoundEvent SPEAR_HIT = register("spear_hit");
    public static final SoundEvent SPECTATOR_RISE = register("spectator_rise");
    public static final SoundEvent SPECTATOR_AMBIENT = register("spectator_ambient");

    private static SoundEvent register(String name) {
        Identifier id = FiloRosso.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
    }
}

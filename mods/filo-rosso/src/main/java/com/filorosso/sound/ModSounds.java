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
    public static final SoundEvent SCISSORS = register("scissors");
    public static final SoundEvent MINION_AMBIENT = register("minion_ambient");
    public static final SoundEvent MINION_HURT = register("minion_hurt");
    public static final SoundEvent MINION_DEATH = register("minion_death");
    public static final SoundEvent VICTORY = register("victory");

    private static SoundEvent register(String name) {
        Identifier id = FiloRosso.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
    }
}

package com.filorosso.server;

import com.filorosso.FiloRosso;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/** /gamerule filorosso:... */
public final class ModRules {
    private ModRules() {
    }

    /** Length of the thread in blocks. */
    public static final GameRule<Integer> LENGTH = GameRuleBuilder.forInteger(6).range(2, 32).category(GameRuleCategory.PLAYER)
            .buildAndRegister(FiloRosso.id("length"));
    /** A random event every few minutes. */
    public static final GameRule<Boolean> EVENTS = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER)
            .buildAndRegister(FiloRosso.id("events"));
    /** Minutes between two events. */
    public static final GameRule<Integer> EVENT_MINUTES = GameRuleBuilder.forInteger(20).range(1, 240).category(GameRuleCategory.PLAYER)
            .buildAndRegister(FiloRosso.id("event_minutes"));
    /** Seconds a downed player waits for the partner (0 = no downed state, you just die). */
    public static final GameRule<Integer> REVIVE_SECONDS = GameRuleBuilder.forInteger(30).range(0, 300).category(GameRuleCategory.PLAYER)
            .buildAndRegister(FiloRosso.id("revive_seconds"));
    /** Recisione's servants come at night. */
    public static final GameRule<Boolean> SERVANTS = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.SPAWNING)
            .buildAndRegister(FiloRosso.id("servants"));
    /** The rope hits mobs. */
    public static final GameRule<Boolean> THREAD_HITS = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER)
            .buildAndRegister(FiloRosso.id("thread_hits"));

    public static void init() {
    }
}

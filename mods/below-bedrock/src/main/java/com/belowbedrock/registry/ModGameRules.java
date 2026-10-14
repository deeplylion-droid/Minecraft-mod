package com.belowbedrock.registry;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;

/** Vanilla-style switches: {@code /gamerule fadedHauntings false} and {@code /gamerule fadedSightings false}. */
public final class ModGameRules {
    /** Strange events and Him in the faded world. */
    public static final GameRules.Key<GameRules.BooleanValue> HAUNTINGS =
            GameRuleRegistry.register("fadedHauntings", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));
    /** After you come back, sometimes you see Him far away in your own world. */
    public static final GameRules.Key<GameRules.BooleanValue> SIGHTINGS =
            GameRuleRegistry.register("fadedSightings", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

    private ModGameRules() {
    }

    public static void register() {
    }
}

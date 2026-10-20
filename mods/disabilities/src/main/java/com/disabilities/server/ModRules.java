package com.disabilities.server;

import com.disabilities.Disabilities;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/** /gamerule disabilities:unique, disabilities:reroll_on_death, disabilities:episodes. */
public final class ModRules {
    private ModRules() {
    }

    /** Two players cannot have the same condition (default on: with 8 players, 16 of the 21 are used). */
    public static final GameRule<Boolean> UNIQUE = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER)
            .buildAndRegister(Disabilities.id("unique"));
    /** When you die you choose again (default off). */
    public static final GameRule<Boolean> REROLL_ON_DEATH = GameRuleBuilder.forBoolean(false).category(GameRuleCategory.PLAYER)
            .buildAndRegister(Disabilities.id("reroll_on_death"));
    /** Random episodes (sleep attacks, tics, sneezes, stumbles...): turn off to pause them. */
    public static final GameRule<Boolean> EPISODES = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER)
            .buildAndRegister(Disabilities.id("episodes"));

    public static void init() {
    }
}

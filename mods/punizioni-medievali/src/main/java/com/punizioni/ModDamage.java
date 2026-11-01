package com.punizioni;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The punishments' damage types (data/punizioni/damage_type): they never scale with the difficulty
 * and go through armour. The executioner gets the kill credit, so the death message names them.
 */
public final class ModDamage {
    private ModDamage() {
    }

    public static final ResourceKey<DamageType> MAIDEN = key("maiden");
    public static final ResourceKey<DamageType> QUARTERING = key("quartering");
    public static final ResourceKey<DamageType> RACK = key("rack");
    public static final ResourceKey<DamageType> CHOP = key("chop");
    public static final ResourceKey<DamageType> BLEEDING = key("bleeding");
    public static final ResourceKey<DamageType> RATS = key("rats");
    public static final ResourceKey<DamageType> PYRE = key("pyre");
    public static final ResourceKey<DamageType> GUILLOTINE = key("guillotine");
    public static final ResourceKey<DamageType> WHEEL = key("wheel");
    public static final ResourceKey<DamageType> BULL = key("bull");
    public static final ResourceKey<DamageType> HANGING = key("hanging");
    public static final ResourceKey<DamageType> CROWS = key("crows");
    /** Every damage type above: dying of one keeps the inventory. */
    public static final TagKey<DamageType> PUNISHMENT = TagKey.create(Registries.DAMAGE_TYPE, Punizioni.id("punishment"));

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Punizioni.id(name));
    }

    /** Hurts the victim with a punishment, the executioner (if any) taking the credit. */
    public static boolean hurt(ServerLevel level, LivingEntity victim, ResourceKey<DamageType> type, float amount, @Nullable Player executioner) {
        if (executioner != null && executioner != victim) {
            victim.setLastHurtByPlayer(executioner, 100);
        }
        DamageSource source = level.damageSources().source(type);
        // punishments hit again and again: no invulnerability window between two blows
        victim.damageCooldownTime = 0;
        return victim.hurtServer(level, source, amount);
    }
}

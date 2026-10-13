package com.wurstel.event;

import com.wurstel.WurstelMod;
import com.wurstel.config.WurstelConfig;
import com.wurstel.item.WurstelBeltItem;
import java.lang.ref.WeakReference;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gameplay of a würstel slap: a melee hit with an empty main hand while wearing the belt deals more
 * damage (more with better belts and Turgido) and pushes the target further. Works on mobs and, with
 * PvP on, players. Slapping blocks breaks them faster with diamond and netherite belts, which also get
 * the drops a pickaxe, axe or shovel of their tier would.
 */
@Mod.EventBusSubscriber(modid = WurstelMod.MODID)
public final class SlapEvents {
    /** Target of the slap being processed, so the knockback that follows can be boosted. */
    private static WeakReference<LivingEntity> slapTarget = new WeakReference<>(null);
    private static long slapTime;

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker) {
            return;
        }
        if (!source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypes.MOB_ATTACK)) {
            return;
        }
        ItemStack belt = WurstelBeltItem.getSlappingBelt(attacker);
        if (belt.isEmpty()) {
            return;
        }
        float amount = event.getAmount();
        if (attacker instanceof Player player) {
            // The würstel, not the arm, does the hit: slaps always land at full strength, even when
            // clicking before the attack cooldown ends. Forge resets the cooldown only after the hit.
            float charge = player.getAttackStrengthScale(0.5F);
            amount /= 0.2F + charge * charge * 0.8F;
        }
        // The Ender Dragon gets here with the damage already cut for hits away from its head:
        // 1.25 for a punch on its body, 1 on its head.
        event.setAmount(amount * WurstelBeltItem.getDamageMultiplier(belt));

        LivingEntity target = event.getEntity();
        slapTarget = new WeakReference<>(target);
        slapTime = target.level().getGameTime();
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (slapTarget.get() != target || target.level().getGameTime() != slapTime) {
            return;
        }
        slapTarget = new WeakReference<>(null);
        float extra = WurstelConfig.SERVER.extraKnockback.get().floatValue();
        event.setStrength(event.getStrength() + extra);
    }

    /** Runs on both sides: the client predicts the mining progress, the server checks it. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack belt = WurstelBeltItem.getSlappingBelt(event.getEntity());
        if (!belt.isEmpty()) {
            float multiplier = ((WurstelBeltItem) belt.getItem()).getWurstelMaterial().miningSpeedMultiplier();
            event.setNewSpeed(event.getNewSpeed() * multiplier);
        }
    }

    /** Diamond and netherite würstels mine like the right tool: stone, ores and the like drop their items. */
    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.canHarvest()) {
            return;
        }
        ItemStack belt = WurstelBeltItem.getSlappingBelt(event.getEntity());
        if (!belt.isEmpty() && ((WurstelBeltItem) belt.getItem()).getWurstelMaterial().canHarvest(event.getTargetBlock())) {
            event.setCanHarvest(true);
        }
    }

    private SlapEvents() {
    }
}

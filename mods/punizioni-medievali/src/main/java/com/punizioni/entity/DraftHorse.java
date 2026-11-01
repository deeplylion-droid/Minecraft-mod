package com.punizioni.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Variant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * One of the four black horses of the quartering rack: it comes when the rack calls it, pulls, and
 * goes away. It has no will of its own (the rack moves it every tick), cannot be hurt, ridden or
 * led, and is never saved.
 */
public class DraftHorse extends Horse {
    public DraftHorse(EntityType<? extends DraftHorse> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPermanentlyInvulnerable(true);
        this.setComponent(DataComponents.HORSE_VARIANT, Variant.BLACK);
        this.setPersistenceRequired();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, amount);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}

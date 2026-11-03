package com.punizioni.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Something set down on a table or the floor: the roast pig, the pot of gruel. Placed like the
 * machines (facing whoever sets it down), falls if what is under it goes, is not pushed around.
 * Right click uses it; crouch and hit it twice to take it away.
 */
public abstract class Prop extends Entity {
    private int breakHits;
    private long lastBreakHit;

    protected Prop(EntityType<? extends Prop> type, Level level) {
        super(type, level);
    }

    /** The item that sets it down (and that it gives back when picked up whole). */
    public abstract ItemStack item();

    /** A right click, on both sides (the client only answers for the swing): {@code location} is where it was clicked, from its feet. */
    protected abstract InteractionResult use(Player player, InteractionHand hand, ItemStack stack, Vec3 location);

    /** What it leaves when taken away (by default: itself). */
    protected void drops(ServerLevel level) {
        this.spawnAtLocation(level, this.item());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        return this.use(player, hand, player.getItemInHand(hand), location);
    }

    /** Which way it faces. */
    public Vec3 forward() {
        float yaw = this.getYRot() * ((float) Math.PI / 180.0F);
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    /** Its right hand side, looking the way it faces. */
    public Vec3 right() {
        float yaw = this.getYRot() * ((float) Math.PI / 180.0F);
        return new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
    }

    /** A point in its own frame: blocks to its right, up, forward. */
    public Vec3 local(double right, double up, double forward) {
        Vec3 f = this.forward();
        Vec3 r = this.right();
        return new Vec3(this.getX() + r.x * right + f.x * forward, this.getY() + up, this.getZ() + r.z * right + f.z * forward);
    }

    // ------------------------------------------------------------------ taking it away

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isRemoved() || this.isInvulnerableToBase(source)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            this.takeAway(level, false);
            return true;
        }
        if (!(source.getEntity() instanceof Player p) || source.getDirectEntity() != p) {
            return false;
        }
        if (!p.isShiftKeyDown()) {
            p.sendOverlayMessage(Component.translatable("punizioni.msg.prop_break_hint"));
            return false;
        }
        if (p.getAbilities().instabuild) {
            this.takeAway(level, true);
            return true;
        }
        long now = level.getGameTime();
        if (now - this.lastBreakHit > 10) {
            this.breakHits = 0;
        }
        this.lastBreakHit = now;
        if (++this.breakHits >= 2) {
            this.takeAway(level, false);
        } else {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_HIT, SoundSource.BLOCKS, 1.0F, 0.9F);
        }
        return true;
    }

    protected void takeAway(ServerLevel level, boolean creative) {
        if (!creative) {
            this.drops(level);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), this.getX(),
                this.getY() + this.getBbHeight() * 0.5, this.getZ(), 20, this.getBbWidth() * 0.3, this.getBbHeight() * 0.3, this.getBbWidth() * 0.3,
                0.05);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
        this.discard();
    }

    @Override
    public ItemStack getPickResult() {
        return this.item();
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (!this.onGround() || this.getDeltaMovement().lengthSqr() > 1.0E-6) {
            this.applyGravity();
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.98, 0.5));
        }
        if (this.level() instanceof ServerLevel level) {
            this.serverTick(level);
        } else {
            this.clientTick();
        }
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    protected void serverTick(ServerLevel level) {
    }

    protected void clientTick() {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0 * 64.0;
    }
}

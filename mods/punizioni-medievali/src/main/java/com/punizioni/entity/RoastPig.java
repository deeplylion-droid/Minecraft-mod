package com.punizioni.entity;

import com.punizioni.ModSounds;
import com.punizioni.item.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A whole roast pig on a wooden platter, golden and crackling, an apple in its mouth: set it on
 * the table and feast. A right click tears off the piece you are looking at (a leg, a ham, a side
 * of ribs, the loin, the apple) and puts it in your hand to eat when you like; the pig loses that
 * piece for everyone. When only the bones are left, a click clears them away.
 */
public class RoastPig extends Prop {
    /** A piece: its bone in the model, where it sits (blocks: right, up, forward from the platter's middle). */
    public record Piece(String bone, double right, double up, double forward) {
    }

    public static final Piece[] PIECES = {
            new Piece("leg_front_right", 0.155, 0.105, 0.32), new Piece("leg_front_left", -0.155, 0.105, 0.32),
            new Piece("leg_back_right", 0.135, 0.105, -0.54), new Piece("leg_back_left", -0.135, 0.105, -0.54),
            new Piece("ham_right", 0.2, 0.28, -0.27), new Piece("ham_left", -0.2, 0.28, -0.27),
            new Piece("ribs_right", 0.19, 0.27, 0.1), new Piece("ribs_left", -0.19, 0.27, 0.1),
            new Piece("loin", 0.0, 0.5, -0.06), new Piece("apple", 0.0, 0.23, 0.64)};
    public static final int ALL = (1 << PIECES.length) - 1;

    private static final EntityDataAccessor<Integer> DATA_TAKEN = SynchedEntityData.defineId(RoastPig.class, EntityDataSerializers.INT);

    public RoastPig(EntityType<? extends RoastPig> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TAKEN, 0);
    }

    /** The pieces already taken (one bit each). */
    public int taken() {
        return this.entityData.get(DATA_TAKEN);
    }

    public boolean has(int piece) {
        return (this.taken() & 1 << piece) == 0;
    }

    @Override
    public ItemStack item() {
        return new ItemStack(ModItems.ROAST_PIG);
    }

    /** What each piece is in the hand. */
    public static ItemStack piece(int i) {
        return switch (i) {
            case 0, 1, 2, 3 -> new ItemStack(ModItems.ROAST_LEG);
            case 4, 5, 8 -> new ItemStack(ModItems.ROAST_HAM);
            case 6, 7 -> new ItemStack(ModItems.ROAST_RIBS);
            default -> new ItemStack(Items.APPLE);
        };
    }

    @Override
    protected InteractionResult use(Player player, InteractionHand hand, ItemStack stack, Vec3 location) {
        int taken = this.taken();
        if (taken == ALL) {
            // only the bones are left
            if (this.level() instanceof ServerLevel level) {
                give(level, player, hand, new ItemStack(Items.BONE, 3));
                level.playSound(null, this.getX(), this.getY() + 0.3, this.getZ(), SoundEvents.SKELETON_STEP, SoundSource.PLAYERS, 1.0F, 1.1F);
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.BONE))),
                        this.getX(), this.getY() + 0.3, this.getZ(), 10, 0.25, 0.1, 0.25, 0.05);
                this.discard();
            }
            return InteractionResult.SUCCESS;
        }
        int i = this.aimed(player, location);
        if (i < 0) {
            return InteractionResult.PASS;
        }
        if (this.level() instanceof ServerLevel level) {
            this.entityData.set(DATA_TAKEN, taken | 1 << i);
            ItemStack piece = piece(i);
            Piece p = PIECES[i];
            Vec3 at = this.local(p.right(), p.up(), p.forward());
            give(level, player, hand, piece.copy());
            level.playSound(null, at.x, at.y, at.z, ModSounds.ROAST_TEAR, SoundSource.PLAYERS, 1.0F, 0.9F + this.random.nextFloat() * 0.25F);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(piece)), at.x, at.y, at.z, 12,
                    0.08, 0.06, 0.08, 0.06);
            level.sendParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y + 0.1, at.z, 4, 0.05, 0.02, 0.05, 0.01);
        }
        return InteractionResult.SUCCESS;
    }

    /** Into the empty hand, else into the inventory, else at the feet. */
    private static void give(ServerLevel level, Player player, InteractionHand hand, ItemStack stack) {
        if (player.getItemInHand(hand).isEmpty()) {
            player.setItemInHand(hand, stack);
        } else if (!player.getInventory().add(stack)) {
            player.spawnAtLocation(level, stack);
        }
    }

    /** The piece still on the pig nearest to the line the player looks along (through where the click landed), or -1. */
    private int aimed(Player player, Vec3 location) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = this.position().add(location).subtract(eye);
        if (dir.lengthSqr() < 1.0E-6) {
            dir = player.getViewVector(1.0F);
        }
        dir = dir.normalize();
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < PIECES.length; i++) {
            if (!this.has(i)) {
                continue;
            }
            Piece p = PIECES[i];
            Vec3 at = this.local(p.right(), p.up(), p.forward()).subtract(eye);
            double along = at.dot(dir);
            double off = at.subtract(dir.scale(along)).length();
            // nearest to the line; between two equally near, the one in front
            double score = off + 0.08 * Math.max(0.0, along);
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Taken away: whatever is left of it comes with it. */
    @Override
    protected void drops(ServerLevel level) {
        int taken = this.taken();
        if (taken == 0) {
            super.drops(level);
            return;
        }
        for (int i = 0; i < PIECES.length; i++) {
            if ((taken & 1 << i) == 0) {
                this.spawnAtLocation(level, piece(i));
            }
        }
        this.spawnAtLocation(level, new ItemStack(Items.BONE, 3));
    }

    @Override
    protected void clientTick() {
        // still hot: steam rises off the crackling
        if (this.taken() != ALL && this.random.nextInt(6) == 0) {
            Vec3 at = this.local((this.random.nextDouble() - 0.5) * 0.4, 0.55, (this.random.nextDouble() - 0.5) * 0.8);
            this.level().addParticle(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, 0.0, 0.015, 0.0);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.entityData.set(DATA_TAKEN, input.getIntOr("taken", 0) & ALL);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("taken", this.taken());
    }
}

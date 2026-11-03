package com.punizioni.entity;

import com.punizioni.ModSounds;
import com.punizioni.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * A big iron pot of gruel on a tripod over a fire: grey-green slop that bubbles and steams, bits
 * floating in it. An empty bowl fills with a ladle of it; a water bucket and whatever scraps there
 * are (rotten flesh, bones, peelings, a spider eye, dung...) fill the pot back up.
 */
public class GruelPot extends Prop {
    public static final int MAX = 16;
    /** A new pot comes half full. */
    public static final int START = 8;
    public static final int PER_BUCKET = 6;

    private static Set<Item> scraps;

    /** Thrown in, they make one more ladle each. */
    private static Set<Item> scraps() {
        if (scraps == null) {
            scraps = Set.of(Items.ROTTEN_FLESH, Items.BONE, Items.POTATO, Items.POISONOUS_POTATO, Items.CARROT, Items.BEETROOT, Items.WHEAT,
                    Items.SPIDER_EYE, Items.BREAD, Items.BROWN_MUSHROOM, Items.RED_MUSHROOM, Items.KELP, Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS,
                    ModItems.POOP);
        }
        return scraps;
    }

    private static final EntityDataAccessor<Integer> DATA_PORTIONS = SynchedEntityData.defineId(GruelPot.class, EntityDataSerializers.INT);

    public GruelPot(EntityType<? extends GruelPot> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PORTIONS, START);
    }

    public int portions() {
        return this.entityData.get(DATA_PORTIONS);
    }

    private void setPortions(int n) {
        this.entityData.set(DATA_PORTIONS, Math.max(0, Math.min(MAX, n)));
    }

    @Override
    public ItemStack item() {
        return new ItemStack(ModItems.GRUEL_POT);
    }

    /** The middle of the slop's surface (world), for sounds and splashes. */
    public Vec3 surface() {
        return this.local(0.0, 0.56 + 0.28 * this.portions() / (float) MAX, 0.0);
    }

    @Override
    protected InteractionResult use(Player player, InteractionHand hand, ItemStack stack, Vec3 location) {
        ServerLevel level = this.level() instanceof ServerLevel l ? l : null;
        if (stack.is(Items.BOWL)) {
            if (this.portions() <= 0) {
                if (level != null) {
                    player.sendOverlayMessage(Component.translatable("punizioni.msg.pot_empty").withStyle(ChatFormatting.GRAY));
                }
                return InteractionResult.SUCCESS;
            }
            if (level != null) {
                this.setPortions(this.portions() - 1);
                ItemStack gruel = new ItemStack(ModItems.GRUEL);
                stack.consume(1, player);
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, gruel);
                } else if (!player.getInventory().add(gruel)) {
                    player.spawnAtLocation(level, gruel);
                }
                Vec3 s = this.surface();
                level.playSound(null, s.x, s.y, s.z, ModSounds.GRUEL_LADLE, SoundSource.PLAYERS, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
                level.sendParticles(ParticleTypes.SPLASH, s.x, s.y, s.z, 8, 0.15, 0.02, 0.15, 0.05);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.WATER_BUCKET) || scraps().contains(stack.getItem())) {
            if (this.portions() >= MAX) {
                if (level != null) {
                    player.sendOverlayMessage(Component.translatable("punizioni.msg.pot_full").withStyle(ChatFormatting.GRAY));
                }
                return InteractionResult.SUCCESS;
            }
            if (level != null) {
                Vec3 s = this.surface();
                if (stack.is(Items.WATER_BUCKET)) {
                    this.setPortions(this.portions() + PER_BUCKET);
                    if (!player.getAbilities().instabuild) {
                        player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                    }
                    level.playSound(null, s.x, s.y, s.z, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 0.9F);
                } else {
                    this.setPortions(this.portions() + 1);
                    stack.consume(1, player);
                    level.playSound(null, s.x, s.y, s.z, ModSounds.PLOP, SoundSource.PLAYERS, 0.9F, 0.7F + this.random.nextFloat() * 0.2F);
                }
                level.sendParticles(ParticleTypes.SPLASH, s.x, s.y, s.z, 14, 0.18, 0.02, 0.18, 0.08);
            }
            return InteractionResult.SUCCESS;
        }
        if (level != null) {
            player.sendOverlayMessage(Component.translatable("punizioni.msg.pot_level", this.portions(), MAX).withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (this.portions() > 0 && this.random.nextInt(70) == 0) {
            Vec3 s = this.surface();
            level.playSound(null, s.x, s.y, s.z, ModSounds.GRUEL_BUBBLE, SoundSource.BLOCKS, 0.6F, 0.8F + this.random.nextFloat() * 0.3F);
        }
    }

    @Override
    protected void clientTick() {
        Vec3 s = this.surface();
        if (this.portions() > 0) {
            if (this.random.nextInt(3) == 0) {
                // bubbles burst on the slop, steam rises
                this.level().addParticle(ParticleTypes.BUBBLE_POP, s.x + (this.random.nextDouble() - 0.5) * 0.5, s.y + 0.02,
                        s.z + (this.random.nextDouble() - 0.5) * 0.5, 0.0, 0.02, 0.0);
            }
            if (this.random.nextInt(5) == 0) {
                this.level().addParticle(ParticleTypes.WHITE_SMOKE, s.x + (this.random.nextDouble() - 0.5) * 0.4, s.y + 0.1,
                        s.z + (this.random.nextDouble() - 0.5) * 0.4, 0.0, 0.02, 0.0);
            }
        }
        // the fire under the pot
        if (this.random.nextInt(4) == 0) {
            Vec3 f = this.local((this.random.nextDouble() - 0.5) * 0.3, 0.2, (this.random.nextDouble() - 0.5) * 0.3);
            this.level().addParticle(this.random.nextInt(3) == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME, f.x, f.y, f.z, 0.0, 0.02, 0.0);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setPortions(input.getIntOr("portions", START));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("portions", this.portions());
    }
}

package com.punizioni.machine;

import com.punizioni.ModDamage;
import com.punizioni.ModParticles;
import com.punizioni.ModSounds;
import com.punizioni.data.ModData;
import com.punizioni.item.ModItems;
import com.punizioni.net.Net;
import com.punizioni.server.Gore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The brazen bull: a hollow bronze bull on a brick firebox, with a hatch in its flank. Whoever stands
 * by the open hatch is shut inside; light the fire under it and the bronze heats from dark brown to
 * glowing red. The screams come out of the bull's nostrils as bellows, with the smoke. A bucket of
 * water cools it in time; otherwise, when the hatch opens again, only charred bones are inside.
 */
public class BrazenBull extends Machine {
    public static final int SEALED = 1;
    public static final int HEATING = 2;
    public static final int DONE = 3;
    public static final int COOLED = 4;

    public static final int HEAT_TICKS = 200;
    public static final int DONE_TICKS = 20 * 30;
    public static final int COOL_TICKS = 100;
    /** Where the hatch is (the right flank) and where the victim waits by it. */
    public static final Vec3 HATCH = new Vec3(0.62, 1.15, 0.0);
    /** The nostrils, at the front of the head. */
    public static final Vec3 NOSE = new Vec3(0.0, 1.95, 1.75);

    private @Nullable BlockPos light;

    public BrazenBull(EntityType<? extends BrazenBull> type, Level level) {
        super(type, level);
    }

    @Override
    public MachineKind kind() {
        return MachineKind.BRAZEN_BULL;
    }

    /** Waiting by the hatch while it is open; curled up in the belly once shut in. */
    @Override
    protected Vec3 slot() {
        return this.state() == IDLE ? new Vec3(1.35, 0.0, 0.0) : new Vec3(0.0, 0.95, -0.1);
    }

    @Override
    protected Vec3 catchSize() {
        return new Vec3(0.8, 2.0, 0.8);
    }

    @Override
    protected float headFreedom() {
        return 180.0F;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected double dismountDistance() {
        return 0.0;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return this.local(1.6, 0.05, 0.0);
    }

    /** The hatch: 1 open, 0 shut. */
    public float hatch(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case IDLE -> 1.0F;
            case SEALED, HEATING -> Math.max(0.0F, 1.0F - t / 6.0F);
            case DONE -> t > 220 ? Math.min(1.0F, (t - 220) / 20.0F) : 0.0F;
            case COOLED -> t > 40 ? Math.min(1.0F, (t - 40) / 15.0F) : 0.0F;
            default -> 1.0F;
        };
    }

    /** How hot the bronze is: 0 dark, 1 glowing red. */
    public float heat(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case HEATING -> Mth.clamp(t / HEAT_TICKS, 0.0F, 1.0F);
            case DONE -> Mth.clamp(this.value() - t / 500.0F, 0.0F, 1.0F);
            case COOLED -> Mth.clamp(this.value() - t / 40.0F, 0.0F, 1.0F);
            default -> 0.0F;
        };
    }

    /** The fire in the firebox: 0 to 1. */
    public float fire(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case HEATING -> Mth.clamp(t / 40.0F, 0.0F, 1.0F);
            case DONE -> Mth.clamp(1.0F - t / 200.0F, 0.0F, 1.0F);
            default -> 0.0F;
        };
    }

    private static boolean lights(ItemStack stack) {
        return stack.is(ModItems.EXECUTIONER_TORCH) || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)
                || stack.is(Items.SOUL_TORCH) || stack.is(Items.LAVA_BUCKET);
    }

    @Override
    protected InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        switch (this.state()) {
            case IDLE -> {
                LivingEntity victim = this.findVictim(player);
                if (victim == null) {
                    tell(player, "punizioni.msg.nobody_hatch");
                } else {
                    this.startWith(level, victim, player);
                }
            }
            case SEALED -> {
                if (lights(stack)) {
                    this.setOperator(player);
                    this.setState(HEATING);
                    this.sound(ModSounds.FIRE_IGNITE, 2.2F, 0.8F);
                    this.sound(ModSounds.DRUMS, 2.2F, 0.75F);
                    if (stack.is(Items.FLINT_AND_STEEL)) {
                        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                    } else if (stack.is(Items.FIRE_CHARGE)) {
                        stack.consume(1, player);
                    }
                } else if (player.isShiftKeyDown()) {
                    this.release();
                    this.setState(IDLE);
                } else {
                    tell(player, "punizioni.msg.bull_hint");
                }
            }
            case HEATING -> {
                if (stack.is(Items.WATER_BUCKET)) {
                    if (!player.getAbilities().instabuild) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    }
                    this.setValue(this.heat(0.0F));
                    this.setState(COOLED);
                    this.sound(ModSounds.SIZZLE, 2.6F, 0.8F);
                    level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1.5, this.getZ(), 80, 1.2, 0.8, 1.2, 0.06);
                }
            }
            default -> {
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean startWith(ServerLevel level, LivingEntity victim, ServerPlayer player) {
        if (!this.capture(victim)) {
            return false;
        }
        this.setOperator(player);
        this.setState(SEALED);
        this.sound(ModSounds.MAIDEN_SLAM, 2.0F, 1.3F);
        tell(player, "punizioni.msg.bull_hint");
        return true;
    }

    /** /punizione avanza: lights the fire under it. */
    @Override
    public void commandAdvance(ServerLevel level, ServerPlayer player) {
        this.use(level, player, this.state() == SEALED ? new ItemStack(ModItems.EXECUTIONER_TORCH) : ItemStack.EMPTY);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        int t = this.stateTicks();
        LivingEntity v = this.victim();
        Player executioner = this.operator();
        float heat = this.heat(0.0F);
        float fire = this.fire(0.0F);
        switch (this.state()) {
            case SEALED -> {
                if (v == null) {
                    this.setState(IDLE);
                } else if (t % 50 == 25) {
                    // banging on the bronze from inside
                    this.sound(SoundEvents.ANVIL_LAND, 0.5F, 0.6F + this.random.nextFloat() * 0.2F);
                }
            }
            case HEATING -> {
                if (v == null || !v.isAlive()) {
                    this.setValue(heat);
                    this.setState(DONE);
                    break;
                }
                if (t > 30 && t % 20 == 0) {
                    ModDamage.hurt(level, v, ModDamage.BULL, 0.5F + 2.0F * heat, executioner);
                    ModData.setBody(v, ModData.body(v).burnt(heat));
                    Gore.hurtScreen(v, Net.VictimFx.BURN, 0.4F + 0.6F * heat);
                }
                if (t > 40 && t % 30 == 0) {
                    // the screams come out as a bull's bellow, with smoke
                    Vec3 nose = this.local(NOSE.x, NOSE.y, NOSE.z);
                    level.playSound(null, nose.x, nose.y, nose.z, ModSounds.BELLOW, SoundSource.HOSTILE, 2.5F, 0.9F + this.random.nextFloat() * 0.2F);
                    this.snort(level, 14);
                }
                if (t % 50 == 0) {
                    this.sound(ModSounds.FIRE_ROAR, 1.2F + fire, 0.85F);
                }
            }
            case DONE -> {
                if (t == 220) {
                    this.sound(SoundEvents.IRON_DOOR_OPEN, 1.6F, 0.6F);
                }
                if (t >= DONE_TICKS) {
                    this.setState(IDLE);
                }
            }
            case COOLED -> {
                if (t == 40) {
                    this.release();
                    this.sound(SoundEvents.IRON_DOOR_OPEN, 1.6F, 0.7F);
                }
                if (t % 6 == 0) {
                    level.sendParticles(ParticleTypes.WHITE_SMOKE, this.getX(), this.getY() + 2.0, this.getZ(), 6, 1.0, 0.4, 1.0, 0.03);
                }
                if (t >= COOL_TICKS) {
                    this.setState(IDLE);
                }
            }
            default -> {
            }
        }
        if (fire > 0.05F) {
            this.firebox(level, fire, t);
        }
        if (heat > 0.4F && t % 4 == 0) {
            // the bronze is so hot the air shimmers with smoke
            level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 2.3, this.getZ(), 3, 1.0, 0.2, 1.2, 0.01);
        }
        this.updateLight(level, Math.max(fire, heat));
    }

    /** Smoke out of the nostrils. */
    private void snort(ServerLevel level, int count) {
        Vec3 nose = this.local(NOSE.x, NOSE.y, NOSE.z);
        Vec3 f = this.forward();
        for (int i = 0; i < count; i++) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, nose.x, nose.y, nose.z, 0, f.x + (this.random.nextDouble() - 0.5) * 0.3,
                    0.15 + this.random.nextDouble() * 0.2, f.z + (this.random.nextDouble() - 0.5) * 0.3, 0.12 + this.random.nextDouble() * 0.08);
        }
    }

    private void firebox(ServerLevel level, float fire, int t) {
        Vec3 c = this.local(0.0, 0.35, 0.0);
        if (this.random.nextInt(2) == 0) {
            level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, (int) (2 + fire * 5), 0.8, 0.15, 1.1, 0.01);
        }
        level.sendParticles(ModParticles.EMBER, true, true, c.x, c.y + 0.3, c.z, (int) (1 + fire * 3), 0.9, 0.2, 1.1, 0.02);
        if (t % 4 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, c.x, c.y + 0.6, c.z, 4, 1.2, 0.1, 1.4, 0.02);
        }
        if (this.random.nextInt(12) == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.6F, 0.8F + this.random.nextFloat() * 0.4F);
        }
    }

    private void updateLight(ServerLevel level, float k) {
        int wanted = k > 0.05F ? Mth.clamp(Math.round(5 + k * 8), 5, 13) : 0;
        BlockPos at = BlockPos.containing(this.getX(), this.getY() + 2.8, this.getZ());
        if (wanted == 0) {
            this.clearLight(level);
            return;
        }
        if (this.light != null && !this.light.equals(at)) {
            this.clearLight(level);
        }
        if (level.getBlockState(at).isAir() || level.getBlockState(at).is(Blocks.LIGHT)) {
            if (!level.getBlockState(at).is(Blocks.LIGHT) || level.getBlockState(at).getValue(LightBlock.LEVEL) != wanted) {
                level.setBlock(at, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, wanted), 3);
            }
            this.light = at;
        }
    }

    private void clearLight(Level level) {
        if (level.isClientSide() || this.light == null) {
            return;
        }
        if (level.getBlockState(this.light).is(Blocks.LIGHT)) {
            level.setBlock(this.light, Blocks.AIR.defaultBlockState(), 3);
        }
        this.light = null;
    }

    @Override
    protected void onRemovedMachine(ServerLevel level) {
        this.clearLight(level);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.clearLight(this.level());
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        if (this.light != null) {
            output.putLong("light", this.light.asLong());
        }
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        long l = input.getLongOr("light", Long.MIN_VALUE);
        this.light = l == Long.MIN_VALUE ? null : BlockPos.of(l);
    }
}

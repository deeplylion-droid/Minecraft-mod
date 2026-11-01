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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
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
 * The stake: a tall post on a heap of faggots. Whoever stands against it gets tied, hands behind the
 * post. Set it alight (the executioner's torch, flint and steel, a fire charge, a torch): the flames
 * creep up the wood and then roar higher than the post, black smoke, embers; the skin blackens bit
 * by bit (Body.burn). A bucket of water puts it out in time. At the end a charred skeleton stays tied
 * to the post, smouldering.
 */
public class Pyre extends Machine {
    public static final int TIED = 1;
    public static final int BURNING = 2;
    public static final int REMAINS = 3;
    public static final int QUENCHED = 4;

    public static final int GROW_TICKS = 100;
    /** How long it takes the skin to char completely. */
    public static final int CHAR_TICKS = 260;
    public static final int REMAINS_TICKS = 20 * 30;
    public static final int QUENCH_TICKS = 100;

    private @Nullable BlockPos light;

    public Pyre(EntityType<? extends Pyre> type, Level level) {
        super(type, level);
    }

    @Override
    public MachineKind kind() {
        return MachineKind.PYRE;
    }

    /** Standing on the faggots, back against the post. */
    @Override
    protected Vec3 slot() {
        return new Vec3(0.0, 0.72, 0.28);
    }

    @Override
    protected float headFreedom() {
        return 60.0F;
    }

    @Override
    protected Vec3 catchSize() {
        return new Vec3(0.9, 2.5, 0.9);
    }

    @Override
    protected double dismountDistance() {
        return 1.8;
    }

    /** How big the fire is (0 to 1), for both sides. */
    public float fire(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case BURNING -> {
                float k = Mth.clamp(t / GROW_TICKS, 0.0F, 1.0F);
                yield k * k * (3.0F - 2.0F * k);
            }
            // dying down after the end: still burning a while, then embers
            case REMAINS -> Math.max(0.0F, 1.0F - t / 160.0F) * 0.9F + 0.08F;
            case QUENCHED -> Math.max(0.0F, 0.6F - t / 20.0F);
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
            case IDLE -> this.catchSomeone(level, player);
            case TIED -> {
                if (lights(stack)) {
                    this.setOperator(player);
                    this.setState(BURNING);
                    this.sound(ModSounds.FIRE_IGNITE, 2.2F, 0.9F);
                    this.sound(ModSounds.DRUMS, 2.0F, 0.8F);
                    if (stack.is(Items.FLINT_AND_STEEL)) {
                        stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    } else if (stack.is(Items.FIRE_CHARGE)) {
                        stack.consume(1, player);
                    }
                } else if (player.isShiftKeyDown()) {
                    this.release();
                    this.setState(IDLE);
                } else {
                    tell(player, "punizioni.msg.pyre_hint");
                }
            }
            case BURNING -> {
                if (stack.is(Items.WATER_BUCKET)) {
                    if (!player.getAbilities().instabuild) {
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    }
                    this.quench(level);
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
        this.setState(TIED);
        tell(player, "punizioni.msg.pyre_hint");
        return true;
    }

    /** /punizione avanza: lights it (as with the executioner's torch). */
    @Override
    public void commandAdvance(ServerLevel level, ServerPlayer player) {
        this.use(level, player, this.state() == TIED ? new ItemStack(ModItems.EXECUTIONER_TORCH) : ItemStack.EMPTY);
    }

    private void quench(ServerLevel level) {
        this.setState(QUENCHED);
        this.sound(ModSounds.SIZZLE, 2.4F, 1.0F);
        level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1.2, this.getZ(), 60, 0.8, 0.8, 0.8, 0.05);
        this.release();
    }

    @Override
    protected void serverTick(ServerLevel level) {
        int state = this.state();
        int t = this.stateTicks();
        LivingEntity v = this.victim();
        Player executioner = this.operator();
        float fire = this.fire(0.0F);
        switch (state) {
            case TIED -> {
                if (v == null) {
                    this.setState(IDLE);
                }
            }
            case BURNING -> {
                if (v == null || !v.isAlive()) {
                    this.setState(REMAINS);
                    break;
                }
                int every = fire > 0.7F ? 10 : 20;
                if (t > 20 && t % every == 0) {
                    ModDamage.hurt(level, v, ModDamage.PYRE, 1.0F + fire, executioner);
                    Gore.hurtScreen(v, Net.VictimFx.BURN, fire);
                }
                ModData.setBody(v, ModData.body(v).burnt(Math.min(1.0F, t / (float) CHAR_TICKS)));
                if (t % 22 == 0 && t > 15) {
                    this.scream(v, false, 2.0F);
                }
                if (t % 40 == 0) {
                    this.sound(ModSounds.FIRE_ROAR, 1.2F + fire * 1.5F, 0.9F);
                }
            }
            case REMAINS -> {
                if (t % 60 == 0 && fire > 0.2F) {
                    this.sound(ModSounds.FIRE_ROAR, fire * 1.6F, 0.8F);
                }
                if (t >= REMAINS_TICKS) {
                    this.setState(IDLE);
                }
            }
            case QUENCHED -> {
                if (t % 10 == 0) {
                    level.sendParticles(ParticleTypes.WHITE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 6, 0.6, 0.4, 0.6, 0.02);
                }
                if (t >= QUENCH_TICKS) {
                    this.setState(IDLE);
                }
            }
            default -> {
            }
        }
        if (fire > 0.05F) {
            this.flames(level, fire, t);
        }
        this.updateLight(level, fire);
    }

    /** Smoke, embers and the crackle (the flames themselves are drawn by the client). */
    private void flames(ServerLevel level, float fire, int t) {
        if (t % 3 == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.5 + fire * 2.0, this.getZ(), (int) (2 + fire * 5),
                    0.5, 0.6, 0.5, 0.04);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 2.5 + fire * 2.0, this.getZ(), 1, 0.4, 0.4, 0.4,
                    0.01);
        }
        level.sendParticles(ModParticles.EMBER, true, true, this.getX(), this.getY() + 0.8 + fire, this.getZ(), (int) (1 + fire * 4),
                0.8, 0.8, 0.8, 0.03);
        if (this.random.nextInt(4) == 0) {
            level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.5, this.getZ(), (int) (2 + fire * 6), 0.9, 0.3, 0.9, 0.02);
        }
        if (this.random.nextInt(10) == 0) {
            level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), SoundEvents.FIRE_AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS,
                    1.5F + fire, 0.8F + this.random.nextFloat() * 0.4F);
        }
    }

    /** The fire lights up the place: an invisible light block over the faggots while it burns. */
    private void updateLight(ServerLevel level, float fire) {
        int wanted = fire > 0.05F ? Mth.clamp(Math.round(6 + fire * 9), 6, 15) : 0;
        BlockPos at = BlockPos.containing(this.getX(), this.getY() + 2.6, this.getZ());
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
        if (level.isClientSide()) {
            return;
        }
        if (this.light != null) {
            if (level.getBlockState(this.light).is(Blocks.LIGHT)) {
                level.setBlock(this.light, Blocks.AIR.defaultBlockState(), 3);
            }
            this.light = null;
        }
    }

    @Override
    protected void onRemovedMachine(ServerLevel level) {
        this.clearLight(level);
    }

    /** The light block is remembered, so a restart in the middle of a fire does not leave it behind. */
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

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.clearLight(this.level());
        super.remove(reason);
    }
}

package com.punizioni.machine;

import com.punizioni.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The whipping post: a thick post with a crossbeam and iron manacles. Whoever stands in front of it
 * gets their wrists chained up high, back to the executioner; then the cat-o'-nine-tails does the
 * rest (see CatOfNineTailsItem: every lash leaves a mark on the back of the skin).
 */
public class WhippingPost extends Machine {
    public static final int BOUND = 1;

    public WhippingPost(EntityType<? extends WhippingPost> type, Level level) {
        super(type, level);
    }

    @Override
    public MachineKind kind() {
        return MachineKind.WHIPPING_POST;
    }

    /** Face against the post, back to the one holding the whip. */
    @Override
    protected Vec3 slot() {
        return new Vec3(0.0, 0.0, 0.62);
    }

    @Override
    protected float victimYaw() {
        return 180.0F;
    }

    @Override
    protected float headFreedom() {
        return 60.0F;
    }

    @Override
    protected InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (this.state() == IDLE) {
            this.catchSomeone(level, player);
            return InteractionResult.SUCCESS;
        }
        this.release();
        this.setState(IDLE);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean startWith(ServerLevel level, LivingEntity victim, ServerPlayer player) {
        if (!this.capture(victim)) {
            return false;
        }
        this.setOperator(player);
        this.setState(BOUND);
        tell(player, "punizioni.msg.whip_hint");
        return true;
    }

    /** /punizione avanza on the post: a lash, as if from the executioner's whip. */
    @Override
    public void commandAdvance(ServerLevel level, ServerPlayer player) {
        LivingEntity v = this.victim();
        if (this.state() == BOUND && v != null) {
            v.damageCooldownTime = 0;
            if (v.hurtServer(level, level.damageSources().playerAttack(player), 3.0F)) {
                ItemStack whip = new ItemStack(com.punizioni.item.ModItems.CAT_O_NINE_TAILS);
                whip.getItem().hurtEnemy(whip, v, player);
            }
        } else {
            super.commandAdvance(level, player);
        }
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (this.state() == BOUND) {
            LivingEntity v = this.victim();
            if (v == null) {
                this.setState(IDLE);
            } else if (this.random.nextInt(120) == 0 && v.isAlive()) {
                // pulling at the chains
                this.sound(ModSounds.CHAINS, 0.6F, 0.8F + this.random.nextFloat() * 0.3F);
            }
        }
    }

    /** A lash landed on the one chained here: more blood, and a scream now and then. */
    public void lashed(ServerLevel level, LivingEntity victim, int lashes) {
        if (lashes % 2 == 1 || this.random.nextInt(3) == 0) {
            this.scream(victim, false, 1.3F);
        }
        if (this.random.nextInt(4) == 0) {
            this.sound(ModSounds.CHAINS, 0.8F, 1.0F);
        }
    }
}

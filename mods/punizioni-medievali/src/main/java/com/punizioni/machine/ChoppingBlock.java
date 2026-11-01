package com.punizioni.machine;

import com.punizioni.ModDamage;
import com.punizioni.ModSounds;
import com.punizioni.data.Body;
import com.punizioni.data.Limb;
import com.punizioni.data.ModData;
import com.punizioni.item.ModItems;
import com.punizioni.net.Net;
import com.punizioni.server.Amputations;
import com.punizioni.server.Gore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The executioner's chopping block: a stump of oak, scarred and stained, with leather straps. Whoever
 * kneels in front of it gets a hand (or a foot) strapped on top. Right click with the executioner's
 * axe to choose another hand or foot, hit the victim with the axe to take it. It stays gone until
 * death.
 */
public class ChoppingBlock extends Machine {
    public static final int HELD = 1;

    public ChoppingBlock(EntityType<? extends ChoppingBlock> type, Level level) {
        super(type, level);
    }

    @Override
    public MachineKind kind() {
        return MachineKind.CHOPPING_BLOCK;
    }

    /** Kneeling in front of the block, facing it. */
    @Override
    protected Vec3 slot() {
        // kneeling with a hand on the block, or sitting closer with a foot on it
        return new Vec3(0.0, 0.0, this.limb().hand() ? 0.95 : 0.8);
    }

    @Override
    protected float victimYaw() {
        return 180.0F;
    }

    @Override
    protected float headFreedom() {
        return 70.0F;
    }

    /** The hand or foot on the block now. */
    public Limb limb() {
        return Limb.ALL[Math.floorMod(this.choice(), Limb.ALL.length)];
    }

    @Override
    protected InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (this.state() == IDLE) {
            this.catchSomeone(level, player);
            return InteractionResult.SUCCESS;
        }
        LivingEntity victim = this.victim();
        if (stack.is(ModItems.EXECUTIONER_AXE) && victim != null && !player.isShiftKeyDown()) {
            Limb next = this.nextLimb(victim, this.limb());
            if (next != null) {
                this.setChoice(next.ordinal());
                this.sound(ModSounds.CHAINS, 0.8F, 1.2F);
                tell(player, "punizioni.msg.chop_hint", Component.translatable(next.key()));
            }
            return InteractionResult.SUCCESS;
        }
        this.release();
        this.setState(IDLE);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean startWith(ServerLevel level, LivingEntity victim, ServerPlayer player) {
        Limb first = this.nextLimb(victim, Limb.LEFT_FOOT);
        if (first == null) {
            tell(player, "punizioni.msg.nothing_to_chop");
            return false;
        }
        if (!this.capture(victim)) {
            return false;
        }
        this.setOperator(player);
        this.setChoice(first.ordinal());
        this.setState(HELD);
        tell(player, "punizioni.msg.chop_hint", Component.translatable(first.key()));
        return true;
    }

    /** /punizione avanza on the block: the axe comes down. */
    @Override
    public void commandAdvance(ServerLevel level, ServerPlayer player) {
        if (this.state() == HELD) {
            this.chop(level, player);
        } else {
            super.commandAdvance(level, player);
        }
    }

    /** The next hand or foot after this one that is still there, or null. */
    private Limb nextLimb(LivingEntity victim, Limb after) {
        Body body = ModData.body(victim);
        Limb l = after;
        for (int i = 0; i < Limb.ALL.length; i++) {
            l = l.next();
            if (!body.lacks(l)) {
                return l;
            }
        }
        return null;
    }

    /** The axe comes down: the hand or foot on the block is gone. */
    public boolean chop(ServerLevel level, Player executioner) {
        LivingEntity victim = this.victim();
        if (this.state() != HELD || victim == null || !victim.isAlive()) {
            return false;
        }
        Limb limb = this.limb();
        Body body = ModData.body(victim);
        if (body.lacks(limb)) {
            return false;
        }
        ModData.setBody(victim, body.without(limb));
        this.sound(ModSounds.CHOP, 2.4F, 0.85F + this.random.nextFloat() * 0.2F);
        this.sound(ModSounds.BONE_CRACK, 1.8F, 0.9F);
        this.scream(victim, false, 2.0F);
        // the victim faces the block: their right is the block's left
        Vec3 cut = this.local(cutRight(limb), TOP + 0.08, cutForward(limb));
        Gore.jet(level, cut, new Vec3(0.0, 1.0, 0.0).add(this.forward().scale(0.3)), 10, 0.55);
        Gore.burst(level, cut, 50, 0.25, 0.2);
        Gore.toAll(this, new Net.ChopFx(this.getId(), victim.getId(), limb.ordinal()));
        Gore.hurtScreen(victim, Net.VictimFx.CHOP, 1.0F);
        ModDamage.hurt(level, victim, ModDamage.CHOP, 5.0F, executioner);
        Amputations.bleed(victim, executioner, 140);
        Limb next = this.nextLimb(victim, limb);
        if (next == null) {
            // nothing left to take
            this.release();
            this.setState(IDLE);
        } else {
            this.setChoice(next.ordinal());
        }
        return true;
    }

    /** The top of the block. */
    public static final double TOP = 0.45;

    /** Where the axe cuts, in the block's frame (the client throws the hand or foot from there too). */
    public static double cutRight(Limb limb) {
        return limb.right() ? -0.16 : 0.16;
    }

    public static double cutForward(Limb limb) {
        return limb.hand() ? 0.22 : 0.3;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (this.state() == HELD && this.victim() == null) {
            this.setState(IDLE);
        }
    }
}

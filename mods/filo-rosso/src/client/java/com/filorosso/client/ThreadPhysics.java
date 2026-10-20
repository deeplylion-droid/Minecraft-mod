package com.filorosso.client;

import com.filorosso.data.BondType;
import com.filorosso.data.ModData;
import com.filorosso.server.Events;
import com.filorosso.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The thread on this client: keeps the local player within reach of the partner. Every client moves
 * only its own player (movement is the client's in Minecraft), by its share of the correction: the
 * one who holds on (crouching on the ground) does not move, the one in the air swings from the
 * other, the one walking away drags the other along.
 */
public final class ThreadPhysics {
    private ThreadPhysics() {
    }

    /** Beyond the length by this much, the correction is immediate. */
    private static final double HARD = 0.8;
    /** Stretched further than this (a pearl, lag): no yanking, the server brings the two together. */
    private static final double GIVE_UP = 4.0;

    /** Most a correction or a drag may move a player in one tick (blocks). */
    private static final double MAX_STEP = 0.9;
    /** After a teleport (yours or your partner's) the thread waits a moment: positions are still arriving. */
    private static final int SETTLE_TICKS = 10;

    private static long lastTautSound;
    private static net.minecraft.world.phys.Vec3 lastMine;
    private static final java.util.Map<Integer, net.minecraft.world.phys.Vec3> LAST_OTHER = new java.util.HashMap<>();
    private static int settle;
    /** True while the local player is hanging from the thread (HUD hint, sounds). */
    public static boolean hanging;
    /** True while the partner hangs from the local player (HUD hint: crouch to hold them). */
    public static boolean holding;
    /** Another player is too close to the partner (jealous bond). */
    public static boolean jealous;

    public static void tick(Minecraft mc) {
        hanging = false;
        holding = false;
        jealous = false;
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null || me.isSpectator() || !me.isAlive() || me.isRemoved()) {
            return;
        }
        Entity body = me;
        if (me.isPassenger()) {
            body = me.getControlledVehicle();
            if (body == null) {
                // a minecart, someone else's horse...: it moves us, the server keeps the couple together
                return;
            }
        }
        float length = ClientState.length();
        // teleports (commands, pearls, the server bringing partners together) are not movement
        Vec3 here = me.position();
        if (lastMine != null && lastMine.distanceToSqr(here) > 4.5 * 4.5) {
            settle = SETTLE_TICKS;
        }
        lastMine = here;
        java.util.List<ThreadLinks.Tie> ties = ThreadLinks.of(me);
        for (ThreadLinks.Tie tie : ties) {
            Player other = tie.other(me);
            Vec3 there = other.position();
            Vec3 before = LAST_OTHER.put(other.getId(), there);
            if (before != null && before.distanceToSqr(there) > 4.5 * 4.5) {
                settle = SETTLE_TICKS;
            }
        }
        if (settle > 0) {
            settle--;
            return;
        }
        for (ThreadLinks.Tie tie : ties) {
            Player other = tie.other(me);
            apply(mc, me, body, other, tie, length);
        }
        if (me.level().getGameTime() % 200 == 0) {
            LAST_OTHER.keySet().removeIf(id -> me.level().getEntity(id) == null);
        }
    }

    public static void reset() {
        lastMine = null;
        LAST_OTHER.clear();
        settle = 0;
    }

    private static boolean airborne(Player p) {
        return !p.onGround() && !p.isInWater() && !p.isInLava() && !p.onClimbable();
    }

    /** Holding on: crouching on solid ground (or riding something). */
    private static boolean anchored(Player p) {
        return p.isPassenger() || p.onGround() && p.isShiftKeyDown() && !ModData.downed(p);
    }

    /**
     * How much of the stretch this player takes: 0 = holds still, 1 = takes it all.
     */
    private static double share(Player me, Player other) {
        if (ModData.downed(me)) {
            return 1.0;
        }
        if (ModData.downed(other)) {
            return 0.0;
        }
        boolean mine = anchored(me);
        boolean theirs = anchored(other);
        if (mine != theirs) {
            return mine ? 0.0 : 1.0;
        }
        boolean meAir = airborne(me) || me.getAbilities().flying;
        boolean otherAir = airborne(other) || other.getAbilities().flying;
        if (meAir && !otherAir) {
            return 1.0;
        }
        if (!meAir && otherAir) {
            return 0.35;
        }
        return 0.5;
    }

    private static Vec3 center(Entity e) {
        return e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
    }

    private static void apply(Minecraft mc, LocalPlayer me, Entity body, Player other, ThreadLinks.Tie tie, float length) {
        Vec3 a = center(me);
        Vec3 b = center(other);
        Vec3 delta = b.subtract(a);
        double d = delta.length();
        if (d < 1.0E-4) {
            return;
        }
        Vec3 n = delta.scale(1.0 / d);
        Vec3 v = body.getDeltaMovement();
        Vec3 otherVel = other.position().subtract(other.xo, other.yo, other.zo);
        double share = share(me, other);
        boolean elastic = !tie.extra() && tie.type() == BondType.ELASTIC;
        double limit = elastic ? length * 1.5 : length;
        if (d > limit + GIVE_UP) {
            return;
        }
        Events.Kind event = ClientState.event();

        // the fates of the bond
        if (!tie.extra()) {
            if (tie.type() == BondType.MAGNET && d > 3.0) {
                v = v.add(n.scale(me.onGround() ? 0.008 : 0.003));
            }
            if (tie.type() == BondType.JEALOUS) {
                for (Player p : me.level().players()) {
                    if (p != me && p != other && !p.isSpectator() && p.distanceToSqr(other) < 3.5 * 3.5) {
                        jealous = true;
                        break;
                    }
                }
                if (jealous && d > 1.5) {
                    v = v.add(n.scale(me.onGround() ? 0.07 : 0.02));
                }
            }
            if (elastic && d > length) {
                // the elastic part: a bungee that wants to bring you back
                v = v.add(n.scale((d - length) * 0.025 * Math.min(1.0, share * 2.0)));
            }
        }
        if (event == Events.Kind.QUARREL && d < 3.0) {
            // a quarrel: the thread pushes them apart
            v = v.subtract(n.scale((3.0 - d) * 0.06));
        }

        boolean taut = d > limit;
        if (taut && share > 0.0) {
            double excess = d - limit;
            // pulled back within reach (with collisions: never through walls)
            double k = excess > HARD ? 1.0 : 0.5;
            body.move(MoverType.SELF, n.scale(Math.min(MAX_STEP, excess * share * k)));
            // no more moving away; dragged along by a partner who walks away
            double myOut = -v.dot(n);
            if (myOut > 0.0) {
                v = v.add(n.scale(myOut * Math.min(1.0, share * 2.0)));
            }
            double theirOut = otherVel.dot(n);
            double toward = v.dot(n);
            double follow = Math.min(MAX_STEP, theirOut * Math.min(1.0, share * 2.0));
            if (follow > toward) {
                v = v.add(n.scale(follow - toward));
            }
            if (airborne(me)) {
                // swinging: a little air drag so it does not go on forever
                v = new Vec3(v.x * 0.98, v.y, v.z * 0.98);
            }
            if ((excess > 0.3 || follow > 0.25) && me.level().getGameTime() - lastTautSound > 16) {
                lastTautSound = me.level().getGameTime();
                me.level().playLocalSound(me.getX(), me.getY(), me.getZ(), ModSounds.TAUT, SoundSource.PLAYERS, 0.35F,
                        0.9F + me.getRandom().nextFloat() * 0.3F, false);
            }
        }

        // hanging below the partner: hold jump to climb up the thread
        boolean above = other.getY() - me.getY() > 1.0;
        if (above && airborne(me) && !airborne(other) && d > limit - 0.6) {
            hanging = true;
        }
        if (above && airborne(me) && !airborne(other) && d > 1.2 && mc.options.keyJump.isDown()) {
            hanging = true;
            v = new Vec3(v.x * 0.7, Math.max(v.y, 0.0), v.z * 0.7);
            body.move(MoverType.SELF, n.scale(0.16));
            me.resetFallDistance();
        }
        if (other.getY() - me.getY() < -1.0 && airborne(other) && !airborne(me) && d > limit - 0.6) {
            holding = true;
        }
        body.setDeltaMovement(v);
    }
}

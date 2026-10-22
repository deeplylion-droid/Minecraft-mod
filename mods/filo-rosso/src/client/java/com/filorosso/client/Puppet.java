package com.filorosso.client;

import com.filorosso.data.ModData;
import com.filorosso.data.Powers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A puppet of the Burattinaio: your keys do nothing, the strings walk you (with jerks, like a
 * marionette) towards whoever it chose, usually your partner. Movement belongs to the client in
 * Minecraft, so the strings move you here; the server only says who pulls and where to.
 */
public final class Puppet {
    private Puppet() {
    }

    private static int ticks;

    /** Is the local player a puppet right now? (the keyboard mixin ignores the keys) */
    public static boolean active() {
        LocalPlayer me = Minecraft.getInstance().player;
        return me != null && ModData.powers(me).puppet(me.level().getGameTime());
    }

    public static void tick(Minecraft mc) {
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) {
            return;
        }
        Powers powers = ModData.powers(me);
        if (!powers.puppet(me.level().getGameTime()) || me.isSpectator()) {
            ticks = 0;
            return;
        }
        ticks++;
        Entity target = powers.puppetTarget() >= 0 ? mc.level.getEntity(powers.puppetTarget()) : null;
        Vec3 v = me.getDeltaMovement();
        if (target != null) {
            Vec3 to = target.position().subtract(me.position());
            Vec3 flat = new Vec3(to.x, 0.0, to.z);
            double d = flat.length();
            // it turns your head to where it drags you
            float want = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
            me.setYRot(Mth.approachDegrees(me.getYRot(), want, 12.0F));
            me.setYHeadRot(me.getYRot());
            if (d > 1.4) {
                // a marionette's walk: jerks of the strings, then a stop
                float jerk = Math.abs(Mth.sin(ticks * 0.45F));
                double speed = 0.06 + 0.2 * jerk * jerk;
                Vec3 step = flat.normalize().scale(speed);
                v = new Vec3(step.x, v.y, step.z);
                if (me.horizontalCollision && me.onGround()) {
                    v = new Vec3(v.x, 0.42, v.z);
                }
            } else {
                v = new Vec3(v.x * 0.5, v.y, v.z * 0.5);
            }
        } else if (me.onGround()) {
            // nowhere to go: it lifts you a little and drops you, like a puppet shaken by its strings
            if (ticks % 24 == 0) {
                v = new Vec3(v.x, 0.36, v.z);
            }
        }
        me.setDeltaMovement(v);
    }
}

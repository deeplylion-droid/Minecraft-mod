// Example of mod-specific test commands (Frusta Imperiale): copy into <test folder>/ctest/src/main/java/ctest/Extra.java
// and run setup.sh again. The driver calls Extra.run(mc, words) for every command it does not know.
package ctest;

import com.frusta.client.WhipAnim;
import com.frusta.client.WhipAnims;
import com.frusta.motion.V3;
import com.frusta.motion.Whip;
import com.frusta.server.Kneel;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Frusta Imperiale test commands (test only). */
public final class Extra {
    private Extra() {
    }

    public static boolean run(Minecraft mc, String[] w) {
        switch (w[0]) {
            // whip [player]: where each whip is, how long it is along its points, how far the tip is
            case "whip" -> {
                for (Player p : mc.level.players()) {
                    if (w.length > 1 && !p.getGameProfile().getName().equals(w[1])) {
                        continue;
                    }
                    WhipAnim anim = WhipAnims.find(p);
                    if (anim == null || !anim.hasNodes()) {
                        Driver.log("whip " + p.getGameProfile().getName() + " none");
                        continue;
                    }
                    double along = 0;
                    for (int i = 1; i < Whip.NODES; i++) {
                        along += anim.node(i).sub(anim.node(i - 1)).length();
                    }
                    V3 hand = anim.node(0);
                    V3 tip = anim.node(Whip.NODES - 1);
                    double minY = Double.MAX_VALUE;
                    for (int i = 0; i < Whip.NODES; i++) {
                        minY = Math.min(minY, anim.node(i).y());
                    }
                    Driver.log(String.format(Locale.ROOT, "whip %s %s hand %.2f %.2f %.2f tip %.2f %.2f %.2f along %.2f span %.2f lowest %.3f feet %.3f",
                            p.getGameProfile().getName(), anim.material(), hand.x(), hand.y(), hand.z(), tip.x(), tip.y(), tip.z(),
                            along, tip.sub(hand).length(), minY, p.getY()));
                }
                return true;
            }
            // use: the use key as this client sees it (spin charge)
            case "use" -> {
                Driver.log(String.format(Locale.ROOT, "use using %s ticks %d item %s cooldown %.2f", mc.player.isUsingItem(),
                        mc.player.getTicksUsingItem(), mc.player.getUseItem().getItem(),
                        mc.player.getCooldowns().getCooldownPercent(mc.player.getMainHandItem().getItem(), 0)));
                return true;
            }
            // kneel: who is kneeling, with their box and eyes
            case "kneel" -> {
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (e instanceof LivingEntity le && (Kneel.isKneeling(e) || e instanceof Player)) {
                        Driver.log(String.format(Locale.ROOT, "kneel %s %s height %.2f eyes %.2f pose %s speed %.3f",
                                e.getName().getString(), Kneel.isKneeling(e), e.getBbHeight(), e.getEyeHeight(), e.getPose(),
                                le.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
                    }
                }
                return true;
            }
            // dragonhead: look at the head of the nearest Ender Dragon (its hitbox, as this client has it)
            case "dragonhead" -> {
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (e instanceof EnderDragon dragon) {
                        Vec3 c = dragon.head.getBoundingBox().getCenter();
                        float[] a = Driver.angles(mc.player.getEyePosition(), c);
                        Driver.look(mc, a[0], a[1]);
                        Driver.log(String.format(Locale.ROOT, "dragonhead at %.2f %.2f %.2f dist %.2f", c.x, c.y, c.z,
                                c.distanceTo(mc.player.getEyePosition())));
                    }
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}

package com.punizioni.client.fx;

import com.punizioni.ModParticles;
import com.punizioni.ModSounds;
import com.punizioni.data.Filth;
import com.punizioni.data.ModData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Flies round whoever is covered in dung (from Filth.FLIES stains on): more stains, more flies,
 * and a buzz that follows them. Not in front of your own eyes: in first person the flies keep away
 * from the camera.
 */
public final class Flies {
    private Flies() {
    }

    private static final Map<Integer, Integer> NEXT_BUZZ = new HashMap<>();

    public static void reset() {
        NEXT_BUZZ.clear();
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        for (Player p : level.players()) {
            Filth f = ModData.filth(p);
            int stains = f.stains();
            if (stains < Filth.FLIES || p.isInvisible() || p.distanceToSqr(mc.player) > 40.0 * 40.0) {
                NEXT_BUZZ.remove(p.getId());
                continue;
            }
            // a fly now and then; with a lot of dung, a cloud
            float chance = 0.08F + 0.03F * (stains - Filth.FLIES);
            if (level.getRandom().nextFloat() < chance) {
                double x = p.getX() + (level.getRandom().nextDouble() - 0.5) * 0.9;
                double y = p.getY() + 0.3 + level.getRandom().nextDouble() * (p.getBbHeight() - 0.1);
                double z = p.getZ() + (level.getRandom().nextDouble() - 0.5) * 0.9;
                if (camera.distanceToSqr(x, y, z) > 1.4 * 1.4) {
                    level.addParticle(ModParticles.FLY, x, y, z, 0.0, 0.0, 0.0);
                }
            }
            int next = NEXT_BUZZ.getOrDefault(p.getId(), 0);
            if (next <= 0) {
                float volume = Math.min(1.0F, 0.25F + 0.04F * stains);
                mc.getSoundManager().play(new EntityBoundSoundInstance(ModSounds.FLIES, SoundSource.PLAYERS, volume,
                        0.9F + level.getRandom().nextFloat() * 0.2F, p, level.getRandom().nextLong()));
                next = 70 + level.getRandom().nextInt(40);
            }
            NEXT_BUZZ.put(p.getId(), next - 1);
        }
    }
}

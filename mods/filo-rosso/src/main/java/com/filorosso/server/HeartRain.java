package com.filorosso.server;

import com.filorosso.entity.FallingHeart;
import com.filorosso.entity.ModEntities;
import com.filorosso.world.Theatre;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** "Pioggia di cuori": hearts float down around every couple; they take them together. */
public final class HeartRain {
    private HeartRain() {
    }

    /** A new heart near each couple this often (ticks), at most this many around them. */
    private static final int EVERY = 30;
    private static final int MAX_AROUND = 4;

    public static void tick(MinecraftServer server, long now) {
        if (now % EVERY != 0) {
            return;
        }
        for (Links.Pair pair : Links.active(server)) {
            if (pair.couple() == null || pair.a().level() != pair.b().level() || !(pair.a().level() instanceof ServerLevel level)
                    || level.dimension() == Theatre.LEVEL) {
                continue;
            }
            Vec3 mid = pair.a().position().add(pair.b().position()).scale(0.5);
            if (level.getEntitiesOfClass(FallingHeart.class, new AABB(mid, mid).inflate(16.0)).size() >= MAX_AROUND) {
                continue;
            }
            RandomSource r = level.getRandom();
            double angle = r.nextDouble() * Math.PI * 2.0;
            double dist = 3.0 + r.nextDouble() * 7.0;
            double x = mid.x + Math.cos(angle) * dist;
            double z = mid.z + Math.sin(angle) * dist;
            // from up high, but under the roof if there is one (caves, houses)
            double y = Math.floor(mid.y) + 1.0;
            for (int i = 0; i < 14 && level.getBlockState(BlockPos.containing(x, y + 1.0, z)).isAir(); i++) {
                y += 1.0;
            }
            if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir()) {
                continue;
            }
            FallingHeart heart = ModEntities.FALLING_HEART.create(level, EntitySpawnReason.EVENT);
            if (heart != null) {
                heart.setPos(x, y, z);
                level.addFreshEntity(heart);
            }
        }
    }
}

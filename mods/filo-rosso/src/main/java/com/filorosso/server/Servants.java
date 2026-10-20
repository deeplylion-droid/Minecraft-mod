package com.filorosso.server;

import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import com.filorosso.entity.ModEntities;
import com.filorosso.entity.Servant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;

/** At night Recisione sends its servants after the couples (a few at a time). */
public final class Servants {
    private Servants() {
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        if (now % 200 != 0 || !level.getGameRules().get(ModRules.SERVANTS) || level.isBrightOutside()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (Couple c : CoupleBook.get(server).couples) {
            ServerPlayer a = Bonds.player(server, c.a);
            if (!Links.usable(a) || a.level() != level || a.isCreative() || random.nextFloat() > 0.3F) {
                continue;
            }
            int near = level.getEntitiesOfClass(Servant.class, a.getBoundingBox().inflate(48.0)).size();
            if (near < 2) {
                spawnNear(level, a, false);
            }
        }
    }

    /** One servant 16-26 blocks away, on the surface, in the dark (anywhere with {@code force}). */
    public static boolean spawnNear(ServerLevel level, ServerPlayer player, boolean force) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = force ? 6.0 + random.nextDouble() * 8.0 : 16.0 + random.nextDouble() * 10.0;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (Math.abs(y - player.getY()) > 12 && !force) {
                continue;
            }
            if (!force && (level.getBrightness(LightLayer.BLOCK, pos) > 7 || level.dimension() != Level.OVERWORLD)) {
                continue;
            }
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir() || level.getBlockState(pos.below()).isAir()) {
                continue;
            }
            Servant servant = ModEntities.SERVANT.create(level, EntitySpawnReason.EVENT);
            if (servant == null) {
                return false;
            }
            servant.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
            servant.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
            servant.setTarget(player);
            level.addFreshEntity(servant);
            return true;
        }
        return false;
    }
}

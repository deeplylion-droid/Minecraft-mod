package com.belowbedrock.fall;

import com.belowbedrock.haunt.Director;
import com.belowbedrock.net.ScreenFxPayload;
import com.belowbedrock.net.ScreenFxPayload.Fx;
import com.belowbedrock.registry.ModAdvancements;
import com.belowbedrock.registry.ModAttachments;
import com.belowbedrock.util.SoundFx;
import com.belowbedrock.world.Faded;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Falling out of the world. Below the overworld (and below the faded world) the void does not kill:
 * you keep falling for 30 to 40 seconds, and then you wake up on the other side.
 */
public final class VoidFall {
    private static final int MIN_TICKS = 600;
    private static final int MAX_TICKS = 800;
    /** Players who log back in while already deep in the void only fall for a few more seconds. */
    private static final int RESUMED_TICKS = 120;
    private static final Map<UUID, Fall> FALLS = new HashMap<>();

    private VoidFall() {
    }

    private static final class Fall {
        final ResourceKey<Level> from;
        final int total;
        final int x;
        final int z;
        int age;

        Fall(ResourceKey<Level> from, int total, int x, int z) {
            this.from = from;
            this.total = total;
            this.x = x;
            this.z = z;
        }

        float progress() {
            return this.age / (float) this.total;
        }

        boolean at(float fraction) {
            return this.age == (int) (this.total * fraction);
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(VoidFall::tick);
        // The void's damage simply does not reach someone who is falling through it.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player && source.is(DamageTypes.FELL_OUT_OF_WORLD) && FALLS.containsKey(player.getUUID())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FALLS.remove(handler.player.getUUID()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (FALLS.remove(newPlayer.getUUID()) != null) {
                ScreenFxPayload.send(newPlayer, Fx.FALL_END, 0, 0.0F);
            }
        });
    }

    public static boolean isFalling(ServerPlayer player) {
        return FALLS.containsKey(player.getUUID());
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            Fall fall = FALLS.get(player.getUUID());
            if (fall == null) {
                if (Faded.hasDeepVoid(level) && !player.isSpectator() && player.isAlive()
                        && player.getY() < level.getMinBuildHeight() - 2) {
                    start(player, level);
                }
                continue;
            }
            if (!player.isAlive() || player.isSpectator() || level.dimension() != fall.from
                    || player.getY() > level.getMinBuildHeight() + 1) {
                cancel(player);
                continue;
            }
            fall.age++;
            step(player, level, fall);
            if (fall.age >= fall.total) {
                FALLS.remove(player.getUUID());
                arrive(player, fall);
            }
        }
    }

    private static void start(ServerPlayer player, ServerLevel level) {
        RandomSource random = player.getRandom();
        boolean resumed = player.getY() < level.getMinBuildHeight() - 200;
        int total = resumed ? RESUMED_TICKS : MIN_TICKS + random.nextInt(MAX_TICKS - MIN_TICKS + 1);
        FALLS.put(player.getUUID(), new Fall(level.dimension(), total, player.getBlockX(), player.getBlockZ()));
        ScreenFxPayload.send(player, Fx.FALLING, total, 0.0F);
    }

    private static void cancel(ServerPlayer player) {
        FALLS.remove(player.getUUID());
        ScreenFxPayload.send(player, Fx.FALL_END, 0, 0.0F);
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.CONFUSION);
        SoundFx.stopAll(player);
    }

    /** What happens while you fall: wind, then a record far too slow, a heartbeat, voices, darkness. */
    private static void step(ServerPlayer player, ServerLevel level, Fall fall) {
        RandomSource random = player.getRandom();
        int age = fall.age;
        int total = fall.total;
        float progress = fall.progress();
        boolean down = fall.from == Level.OVERWORLD;

        if (age % 140 == 1 && age < total - 80) {
            SoundFx.onPlayer(player, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.0F - 0.65F * progress, 0.75F);
        }
        if (age == 30) {
            SoundFx.onPlayer(player, down ? SoundEvents.MUSIC_DISC_13 : SoundEvents.MUSIC_DISC_11, SoundSource.RECORDS, 0.55F, 0.7F);
        }
        if (random.nextInt(200) == 0) {
            SoundFx.onPlayer(player, SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 1.0F, 0.5F + random.nextFloat() * 0.4F);
        }
        // Faint sparks rushing past: left still in the air while you fall through them (the void has no light,
        // so only a particle that glows by itself can be seen down here).
        if (age % 2 == 0) {
            level.sendParticles(player, ParticleTypes.END_ROD, true, player.getX(), player.getY() - 14.0, player.getZ(),
                    1, 5.0, 6.0, 5.0, 0.0);
        }
        if (progress > 0.55F) {
            int interval = (int) Mth.lerp((progress - 0.55F) / 0.45F, 28.0F, 9.0F);
            if (age % interval == 0) {
                SoundFx.onPlayer(player, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.9F);
            }
        }
        if (random.nextFloat() < 0.004F + 0.03F * progress * progress && age < total - 60) {
            ScreenFxPayload.send(player, Fx.GLITCH, 4 + random.nextInt(8), 0.25F + 0.6F * progress);
        }

        String key = down ? "fall.belowbedrock.down." : "fall.belowbedrock.up.";
        if (fall.at(0.22F)) {
            whisper(player, Component.translatable(key + "1"));
        } else if (fall.at(0.45F)) {
            whisper(player, Component.translatable(key + "2", Component.translatable(key + "2.glitch").withStyle(ChatFormatting.OBFUSCATED)));
        } else if (fall.at(0.7F)) {
            whisper(player, Component.translatable(key + "3", player.getName()));
        } else if (fall.at(0.86F)) {
            whisper(player, Component.translatable(key + "4"));
        }
        if (fall.at(0.35F)) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, total - age + 80, 0, false, false, false));
        }
        if (fall.at(0.58F)) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0, false, false, false));
        }
        if (age == total - 100 && down) {
            ServerLevel faded = player.server.getLevel(Faded.LEVEL);
            if (faded != null) {
                faded.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(fall.x >> 4, fall.z >> 4), 3,
                        new BlockPos(fall.x, 0, fall.z));
            }
        }
        if (age == total - 60) {
            ScreenFxPayload.send(player, Fx.BLACKOUT, 110, 1.0F);
        }
        if (age == total - 25) {
            SoundFx.stopAll(player);
        }
    }

    private static void whisper(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }

    private static void arrive(ServerPlayer player, Fall fall) {
        player.stopRiding();
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.CONFUSION);
        if (fall.from == Level.OVERWORLD) {
            ServerLevel faded = player.server.getLevel(Faded.LEVEL);
            if (faded == null) {
                return;
            }
            BlockPos landing = landing(faded, fall.x, fall.z);
            player.teleportTo(faded, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, player.getYRot(), 10.0F);
            player.setAttached(ModAttachments.VISITED, true);
        } else {
            DimensionTransition home = player.findRespawnPositionAndUseSpawnBlock(false, DimensionTransition.DO_NOTHING);
            player.changeDimension(home);
            player.setAttached(ModAttachments.RETURNED, true);
            ModAdvancements.award(player, ModAdvancements.ESCAPE);
        }
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        // Waking up is slow.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, false, false));
        ScreenFxPayload.send(player, Fx.FALL_END, 0, 0.0F);
        Director.onArrive(player);
    }

    /** The ground at the same x/z on the other side, or the closest free spot around it. */
    private static BlockPos landing(ServerLevel level, int x, int z) {
        for (int radius = 0; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                    BlockPos feet = new BlockPos(x + dx, y, z + dz);
                    if (y > level.getMinBuildHeight() && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                        return feet;
                    }
                }
            }
        }
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
    }
}

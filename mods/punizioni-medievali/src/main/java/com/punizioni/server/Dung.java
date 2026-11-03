package com.punizioni.server;

import com.punizioni.ModParticles;
import com.punizioni.ModSounds;
import com.punizioni.data.Filth;
import com.punizioni.data.ModData;
import com.punizioni.entity.PoopProjectile;
import com.punizioni.item.ModItems;
import com.punizioni.machine.Pillory;
import com.punizioni.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Dung: where it comes from (cows, pigs, sheep, goats and horses drop it now and then, like hens lay
 * eggs) and what it does when thrown. On a player it splats exactly where it hits: a stain on that
 * spot of their skin (Filth, painted by MarkedSkins), a brown splatter on their screen; from a few
 * stains on, flies buzz round them. Water washes it off at once, the rain slowly.
 */
public final class Dung {
    private Dung() {
    }

    /** Every this many ticks the animals near players may drop some. */
    private static final int ANIMAL_EVERY = 100;
    /** One in this many each time: about once every five minutes per animal. */
    private static final int ANIMAL_CHANCE = 60;
    private static final double ANIMAL_RANGE = 24.0;
    /** Washing: every this many ticks in water three stains go, in the rain one every three times. */
    private static final int WASH_EVERY = 10;

    // ------------------------------------------------------------------ hits

    /** The dung hit an entity's bounding box (not the pillory: see hitPillory). Always ends the flight. */
    public static boolean hitEntity(ServerLevel level, PoopProjectile poop, Entity target, Vec3 at) {
        Vec3 dir = direction(poop);
        if (target instanceof Player player) {
            BodyHit.Hit hit = BodyHit.standing(player, at.subtract(dir), dir, 3.0, true);
            if (hit != null) {
                splat(level, poop, player, hit, at);
                return true;
            }
        }
        mess(level, at, 10);
        level.playSound(null, at.x, at.y, at.z, ModSounds.SPLAT, SoundSource.PLAYERS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.3F);
        if (target instanceof LivingEntity living && !(target instanceof Player)) {
            // a mob only flinches
            living.push(dir.x * 0.12, 0.04, dir.z * 0.12);
        }
        return true;
    }

    /** The dung met the pillory: the one locked in it, or its wood. */
    public static void hitPillory(ServerLevel level, PoopProjectile poop, Pillory pillory, BodyHit.Hit hit, Vec3 at) {
        LivingEntity victim = pillory.victim();
        if (hit.part() != BodyHit.WOOD && victim instanceof Player player) {
            splat(level, poop, player, hit, at);
            return;
        }
        mess(level, at, 10);
        level.playSound(null, at.x, at.y, at.z, ModSounds.SPLAT, SoundSource.PLAYERS, hit.part() == BodyHit.WOOD ? 0.7F : 0.9F,
                0.8F + level.getRandom().nextFloat() * 0.3F);
    }

    public static void hitBlock(ServerLevel level, PoopProjectile poop, BlockHitResult hit) {
        Vec3 at = hit.getLocation();
        mess(level, at, 8);
        level.playSound(null, at.x, at.y, at.z, ModSounds.SPLAT, SoundSource.PLAYERS, 0.55F, 0.8F + level.getRandom().nextFloat() * 0.3F);
    }

    private static Vec3 direction(PoopProjectile poop) {
        Vec3 d = poop.getDeltaMovement();
        return d.lengthSqr() > 1.0E-6 ? d.normalize() : new Vec3(0.0, -1.0, 0.0);
    }

    /** A splat on a player: the stain where it hit, the splatter on their screen, the sound. */
    private static void splat(ServerLevel level, PoopProjectile poop, Player target, BodyHit.Hit hit, Vec3 at) {
        RandomSource r = level.getRandom();
        boolean head = hit.part() == Filth.HEAD;
        boolean face = head && hit.face() == Filth.FRONT;
        int size = head ? 1 + r.nextInt(3) : r.nextInt(4);
        int packed = Filth.pack(hit.part(), hit.face(), hit.u(), hit.v(), size, r.nextInt(256));
        ModData.setFilth(target, ModData.filth(target).splat(packed, level.getGameTime()));
        Gore.hurtScreen(target, Net.VictimFx.POOP, face ? 1.0F : head ? 0.6F : 0.3F);
        level.playSound(null, at.x, at.y, at.z, ModSounds.SPLAT, SoundSource.PLAYERS, 1.0F, 0.85F + r.nextFloat() * 0.3F);
        mess(level, at, 14);
        if (poop.getOwner() instanceof ServerPlayer thrower && thrower != target) {
            thrower.sendOverlayMessage(Component.translatable(face ? "punizioni.msg.poop_face" : "punizioni.msg.poop_hit", target.getDisplayName())
                    .withStyle(face ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
        }
    }

    private static void mess(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(ModParticles.DUNG, true, false, at.x, at.y, at.z, count, 0.08, 0.08, 0.08, 0.14);
    }

    // ------------------------------------------------------------------ every tick

    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Filth f = ModData.filth(p);
            if (f.clean() || p.tickCount % WASH_EVERY != 0 || !p.isInWaterOrRain()) {
                continue;
            }
            boolean water = p.isInWater();
            if (!water && p.tickCount % (WASH_EVERY * 3) != 0) {
                continue;
            }
            Filth washed = f.washed(water ? 3 : 1);
            ModData.setFilth(p, washed);
            ServerLevel level = p.level();
            level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.3, 0.5, 0.3, 0.1);
            if (washed.clean()) {
                p.sendOverlayMessage(Component.translatable("punizioni.msg.washed").withStyle(ChatFormatting.AQUA));
            }
        }
        if (server.getTickCount() % ANIMAL_EVERY == 0) {
            animals(server);
        }
    }

    private static void animals(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            Set<Animal> seen = new HashSet<>();
            for (ServerPlayer p : level.players()) {
                for (Animal a : level.getEntitiesOfClass(Animal.class, p.getBoundingBox().inflate(ANIMAL_RANGE), Dung::poops)) {
                    if (seen.add(a) && level.getRandom().nextInt(ANIMAL_CHANCE) == 0) {
                        drop(level, a);
                    }
                }
            }
        }
    }

    private static boolean poops(Animal a) {
        return a.isAlive() && !a.isBaby() && (a instanceof AbstractCow || a instanceof Pig || a instanceof Sheep || a instanceof Goat
                || a instanceof AbstractHorse);
    }

    /** Plop: a lump behind the animal. */
    public static void drop(ServerLevel level, Animal a) {
        float yaw = a.yBodyRot * Mth.DEG_TO_RAD;
        double back = a.getBbWidth() * 0.5 + 0.12;
        double bx = Mth.sin(yaw);
        double bz = -Mth.cos(yaw);
        Vec3 at = new Vec3(a.getX() + bx * back, a.getY() + a.getBbHeight() * 0.4, a.getZ() + bz * back);
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(ModItems.POOP));
        item.setDeltaMovement(bx * 0.06, 0.0, bz * 0.06);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        level.playSound(null, at.x, at.y, at.z, ModSounds.PLOP, SoundSource.NEUTRAL, 0.9F, 0.8F + level.getRandom().nextFloat() * 0.3F);
    }
}

package com.frusta.server;

import com.frusta.FrustaMod;
import com.frusta.config.FrustaConfig;
import com.frusta.item.WhipItem;
import com.frusta.item.WhipMaterial;
import com.frusta.motion.Whip;
import com.frusta.motion.WhipShape;
import com.frusta.network.FrustaNetwork;
import com.frusta.network.MovePacket;
import com.frusta.registry.ModDamageTypes;
import com.frusta.registry.ModSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/**
 * Server side of the whip: starts the moves, tells the other players about them and, on the crack
 * tick, decides what is hit. The whipper's own client already shows the move when it asks for it.
 *
 * <p>The Ender Dragon always takes the whip on its head, where hits count in full (on the other parts
 * vanilla cuts them to a quarter): with the default config a netherite whip with Sottomissione V hits
 * for 256 and kills it (200 health) in one lash.</p>
 */
public final class WhipMoves {
    /** A move under way: everything is decided when it starts. */
    private record Active(WhipShape.Move move, long start, Vec3 aim, double length, float damage, int kneel,
                          float charge, int target, WhipMaterial material) {
    }

    /** Something found along the whip: what and how far from the eyes. */
    private record Hit(@Nullable Entity entity, double distance, boolean blocked) {
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private WhipMoves() {
    }

    /** The attack key, with the style the client picked. */
    public static void lash(ServerPlayer player, int style) {
        ItemStack whip = WhipItem.held(player);
        if (!canMove(player, whip)) {
            return;
        }
        start(player, whip, WhipShape.Move.LASH, Math.floorMod(style, WhipShape.STYLES), 0.0F);
    }

    /** The use key let go after spinning the whip for {@code held} ticks: the Imperial Crack, or a plain lash. */
    public static void release(ServerPlayer player, int held) {
        ItemStack whip = WhipItem.held(player);
        if (whip.isEmpty() || player.isSpectator() || !player.isAlive()
                || player.getCooldowns().isOnCooldown(whip.getItem())) {
            return;
        }
        if (held < Whip.TWIRL_MIN) {
            start(player, whip, WhipShape.Move.LASH, player.getRandom().nextInt(WhipShape.STYLES), 0.0F);
        } else {
            start(player, whip, WhipShape.Move.IMPERIAL, 0, (float) Whip.charge(held));
        }
    }

    /** Sneak + use: the whip wraps around whoever is in front of you, then pulls them in. */
    public static void grab(ServerPlayer player) {
        ItemStack whip = WhipItem.held(player);
        if (canMove(player, whip)) {
            start(player, whip, WhipShape.Move.GRAB, 0, 0.0F);
        }
    }

    private static boolean canMove(ServerPlayer player, ItemStack whip) {
        return !whip.isEmpty() && !player.isSpectator() && player.isAlive() && !player.isUsingItem()
                && !player.getCooldowns().isOnCooldown(whip.getItem()) && !ACTIVE.containsKey(player.getUUID());
    }

    private static void start(ServerPlayer player, ItemStack whip, WhipShape.Move move, int style, float charge) {
        WhipMaterial material = ((WhipItem) whip.getItem()).getMaterial();
        double length = material.length();
        Vec3 aim = player.getLookAngle();
        // Where it will crack: a block or (except for the Imperial Crack, which sweeps) someone in the way.
        Hit hit = trace(player, aim, length, move != WhipShape.Move.IMPERIAL);
        int target = -1;
        if (move == WhipShape.Move.GRAB && hit.entity() != null && canPull(hit.entity())) {
            target = hit.entity().getId();
        }
        int kneel = FrustaConfig.SERVER.kneel.get() ? Whip.kneelTicks(WhipItem.sottomissione(whip)) : 0;
        long now = player.server.getTickCount();
        ACTIVE.put(player.getUUID(), new Active(move, now, aim, length, WhipItem.damage(whip), kneel, charge, target,
                material));
        int cooldown = switch (move) {
            case LASH -> Whip.LASH_COOLDOWN;
            case IMPERIAL -> Whip.IMPERIAL_COOLDOWN;
            case GRAB -> Whip.GRAB_COOLDOWN;
        };
        player.getCooldowns().addCooldown(whip.getItem(), cooldown);
        boolean contact = hit.entity() != null || hit.blocked();
        FrustaNetwork.sendToTracking(player, new MovePacket(player.getId(), (byte) move.ordinal(), (byte) style,
                (float) aim.x, (float) aim.y, (float) aim.z, (float) hit.distance(), contact, target, charge));
    }

    /** Every server tick: what cracks now hits. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> entry = it.next();
            Active a = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            long t = now - a.start();
            if (player == null || !player.isAlive() || t > Whip.GRAB_LANDED + 2) {
                it.remove();
                continue;
            }
            try {
                switch (a.move()) {
                    case LASH -> {
                        if (t == Whip.LASH_CRACK) {
                            lashHit(player, a);
                            it.remove();
                        }
                    }
                    case IMPERIAL -> {
                        if (t == Whip.IMPERIAL_CRACK) {
                            imperialHit(player, a);
                            it.remove();
                        }
                    }
                    case GRAB -> {
                        if (a.target() < 0 && t >= Whip.GRAB_YANK) {
                            it.remove();
                        } else if (t == Whip.GRAB_YANK) {
                            yank(player, a);
                        } else if (t >= Whip.GRAB_LANDED) {
                            landed(player, a);
                            it.remove();
                        }
                    }
                }
            } catch (RuntimeException e) {
                FrustaMod.LOGGER.error("Whip move of {} failed", player.getGameProfile().getName(), e);
                it.remove();
            }
        }
    }

    public static void forget(UUID player) {
        ACTIVE.remove(player);
    }

    // =====================================================================================
    // Hits
    // =====================================================================================

    private static void lashHit(ServerPlayer player, Active a) {
        Hit hit = trace(player, a.aim(), a.length(), true);
        if (hit.entity() != null) {
            strike(player, hit.entity(), a, ModDamageTypes.WHIP, a.aim(), FrustaConfig.SERVER.knockback.get());
        }
    }

    /** The Imperial Crack sweeps everything in a cone in front of you, through nothing but air. */
    private static void imperialHit(ServerPlayer player, Active a) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        double range = a.length() + 0.5;
        double halfAngle = Math.toRadians(FrustaConfig.SERVER.imperialCone.get()) * (0.5 + 0.5 * a.charge());
        double cos = Math.cos(halfAngle);
        double knockback = FrustaConfig.SERVER.imperialKnockback.get() * (0.6 + 0.4 * a.charge());
        boolean dragonHit = false;
        for (Entity e : level.getEntities(player, new AABB(eye, eye).inflate(range), e -> canHit(player, e))) {
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 to = center.subtract(eye);
            double dist = to.length();
            if (dist > range + e.getBbWidth() * 0.5) {
                continue;
            }
            boolean close = dist < 1.2 + e.getBbWidth() * 0.5;
            if (!close && to.normalize().dot(a.aim()) < cos) {
                continue;
            }
            BlockHitResult wall = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (wall.getType() != HitResult.Type.MISS && !close) {
                continue;
            }
            if (dragonOf(e) != null) {
                if (dragonHit) {
                    continue; // one crack, one hit on the dragon, however many of its parts are in the cone
                }
                dragonHit = true;
            }
            Vec3 push = new Vec3(to.x, 0, to.z);
            if (strike(player, e, a, ModDamageTypes.IMPERIAL, push.lengthSqr() < 1e-6 ? a.aim() : push.normalize(), knockback)
                    && e instanceof LivingEntity living && dragonOf(e) == null) {
                living.setDeltaMovement(living.getDeltaMovement().add(0, 0.3 + 0.15 * a.charge(), 0));
                living.hurtMarked = true;
            }
        }
    }

    /** The grab pulls its target in, landing it a couple of blocks in front of you. */
    private static void yank(ServerPlayer player, Active a) {
        Entity e = player.serverLevel().getEntity(a.target());
        if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(player) > a.length() + 3) {
            return;
        }
        DamageSource source = source(player, ModDamageTypes.WHIP);
        // a sting: half a heart, mostly so mobs know who did it
        target.hurt(source, 1.0F);
        Vec3 to = player.position().subtract(target.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        double distance = flat.length();
        double strength = FrustaConfig.SERVER.grabStrength.get();
        // Airborne, horizontal speed shrinks by 0.91 a tick: in ~11 ticks of flight it covers about 7.3 times it.
        double speed = Math.min(1.6, Math.max(0, distance - 1.8) / 7.3 * strength);
        Vec3 velocity = (flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize().scale(speed))
                .add(0, Math.min(0.6, 0.38 + 0.02 * distance) * Math.min(1.0, strength), 0);
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        target.resetFallDistance();
        if (target instanceof ServerPlayer pulled) {
            pulled.connection.send(new ClientboundSetEntityMotionPacket(pulled));
        }
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.YANK.get(),
                SoundSource.PLAYERS, 1.6F, a.material().pitch());
    }

    /** ...and once it has landed at your feet, it kneels (with Sottomissione). */
    private static void landed(ServerPlayer player, Active a) {
        Entity e = player.serverLevel().getEntity(a.target());
        if (e instanceof LivingEntity target && target.isAlive() && a.kneel() > 0 && canHurtPlayer(player, target)) {
            Kneel.start(target, a.kneel(), player);
        }
    }

    /**
     * One hit of the whip: damage (the dragon on its head), knockback, the sound of leather on skin,
     * sparks, and with Sottomissione the target kneels. Returns true if it was hurt.
     */
    private static boolean strike(ServerPlayer player, Entity e, Active a, ResourceKey<DamageType> type, Vec3 push,
                                  double knockback) {
        ServerLevel level = player.serverLevel();
        DamageSource source = source(player, type);
        EnderDragon dragon = dragonOf(e);
        boolean hurt;
        if (dragon != null) {
            hurt = dragon.hurt(dragon.head, source, a.damage());
        } else {
            hurt = e.hurt(source, a.damage());
            if (hurt && e instanceof LivingEntity living && knockback > 0) {
                living.knockback(knockback, -push.x, -push.z);
            }
        }
        Vec3 at = dragon != null ? dragon.head.getBoundingBox().getCenter() : e.getBoundingBox().getCenter();
        if (hurt) {
            level.playSound(null, at.x, at.y, at.z, ModSounds.HIT.get(), SoundSource.PLAYERS, 1.5F,
                    a.material().pitch() * (0.9F + player.getRandom().nextFloat() * 0.2F));
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.25, 0.25, 0.25, 0.45);
            level.sendParticles(sparks(a.material()), at.x, at.y, at.z, 8, 0.2, 0.25, 0.2, 0.08);
            if (a.kneel() > 0 && e instanceof LivingEntity living && dragon == null && canHurtPlayer(player, living)) {
                Kneel.start(living, a.kneel(), player);
            }
        }
        return hurt;
    }

    private static ParticleOptions sparks(WhipMaterial material) {
        return switch (material) {
            case LEATHER -> ParticleTypes.ENCHANTED_HIT;
            case DIAMOND -> ParticleTypes.ELECTRIC_SPARK;
            case NETHERITE -> ParticleTypes.FLAME;
        };
    }

    private static DamageSource source(ServerPlayer player, ResourceKey<DamageType> key) {
        Optional<Holder.Reference<DamageType>> type =
                player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key);
        return type.<DamageSource>map(holder -> new DamageSource(holder, player, player))
                .orElseGet(() -> player.damageSources().playerAttack(player));
    }

    // =====================================================================================
    // Finding things along the whip
    // =====================================================================================

    /**
     * The first thing along the aim within the whip's length: a block (it cracks against it) or, if
     * {@code entities}, someone who can be hit.
     */
    private static Hit trace(ServerPlayer player, Vec3 aim, double length, boolean entities) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(aim.scale(length));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = length;
        boolean blocked = false;
        if (block.getType() != HitResult.Type.MISS) {
            reach = block.getLocation().distanceTo(eye);
            blocked = true;
        }
        if (!entities) {
            return new Hit(null, reach, blocked);
        }
        Vec3 to = eye.add(aim.scale(reach));
        Entity best = null;
        double bestDistance = reach;
        AABB area = new AABB(eye, to).inflate(1.0);
        for (Entity e : level.getEntities(player, area, e -> canHit(player, e))) {
            AABB box = e.getBoundingBox().inflate(0.35 + e.getPickRadius());
            if (box.contains(eye)) {
                return new Hit(e, 0.3, false);
            }
            Optional<Vec3> clip = box.clip(eye, to);
            if (clip.isPresent()) {
                double d = clip.get().distanceTo(eye);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = e;
                }
            }
        }
        return best != null ? new Hit(best, bestDistance, false) : new Hit(null, reach, blocked);
    }

    /** Mobs, players (if PvP allows), the dragon's parts and end crystals; not your pets, not your mount. */
    private static boolean canHit(ServerPlayer owner, Entity entity) {
        if (entity == owner || !entity.isAlive() || entity.isSpectator() || !entity.isPickable()
                || entity == owner.getVehicle() || entity.isPassengerOfSameVehicle(owner)) {
            return false;
        }
        if (entity instanceof PartEntity<?> part) {
            return part.getParent() != owner;
        }
        if (!(entity instanceof LivingEntity) && !(entity instanceof EndCrystal)) {
            return false;
        }
        if (entity instanceof TamableAnimal pet && pet.isOwnedBy(owner)) {
            return false;
        }
        return !(entity instanceof Player other) || owner.canHarmPlayer(other);
    }

    private static boolean canHurtPlayer(ServerPlayer owner, LivingEntity target) {
        return !(target instanceof Player other) || owner.canHarmPlayer(other);
    }

    /** The grab pulls mobs and players, not bosses. */
    private static boolean canPull(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof EnderDragon) && !(entity instanceof WitherBoss)
                && !(entity instanceof PartEntity<?>) && entity.getBbWidth() < 3.0F;
    }

    @Nullable
    private static EnderDragon dragonOf(Entity e) {
        if (e instanceof EnderDragon dragon) {
            return dragon;
        }
        if (e instanceof EnderDragonPart part) {
            return part.parentMob;
        }
        return null;
    }
}

package com.punizioni.server;

import com.punizioni.ModDamage;
import com.punizioni.ModParticles;
import com.punizioni.ModSounds;
import com.punizioni.Punizioni;
import com.punizioni.data.Curse;
import com.punizioni.data.ModData;
import com.punizioni.item.ModItems;
import com.punizioni.net.Net;
import com.punizioni.witch.CurseClock;
import com.punizioni.witch.CurseKind;
import com.punizioni.witch.Talk;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The witch's curses, until death:
 * <ul>
 * <li>the toad: a big warty toad, hops, croaks, has no hands (cannot hit with things, dig, build,
 * open, use anything but food) but a sticky tongue that hits from five blocks; croaks in the chat;</li>
 * <li>the head in the butt: the face sticks out of the backside, so the body walks on backwards
 * (the eyes are down there: lower view); mumbles in the chat, now and then farts in its own face;</li>
 * <li>the balloon: swells up and floats a few blocks over the ground, drifting with the wind (its own
 * client steers it); anything sharp (a blade, an arrow, a cactus, fire) pops it, and so does holding
 * crouch for three seconds: it whizzes round deflating and the player dies (keeping the inventory).</li>
 * </ul>
 * Whoever is splashed is cursed; the thrower is spared unless nobody else was hit. Animals hit by
 * the toad potion become frogs, by the balloon they float off for a while.
 */
public final class Curses {
    private Curses() {
    }

    /** Who the bottle reaches (blocks from where it shatters). */
    public static final double SPLASH = 3.0;
    /** The popped balloon whizzes round this long before the player dies. */
    public static final int ZIP_TICKS = 36;
    /** Crouching this long pops your own balloon. */
    private static final int SELF_POP = 60;
    private static final float TONGUE_DAMAGE = 2.5F;

    private static final Identifier MOD = Punizioni.id("curse");

    private static final Map<UUID, Integer> CROUCHED = new HashMap<>();
    private static final Map<UUID, UUID> POPPERS = new HashMap<>();
    private static final Map<UUID, Long> TONGUES = new HashMap<>();

    public static boolean toad(Player player) {
        return ModData.cursed(player, CurseKind.TOAD);
    }

    public static boolean balloon(Player player) {
        return ModData.cursed(player, CurseKind.BALLOON);
    }

    public static void init() {
        // a toad has no hands: no digging, building, opening or using things (food goes down its throat)
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> toad(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> toad(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) ->
                toad(player) && !player.getItemInHand(hand).has(DataComponents.FOOD) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> toad(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !toad(player));
        // ...but a tongue: every attack is a lash of it, whatever is in the "hand"
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!toad(player)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer toad && level instanceof ServerLevel server) {
                tongue(server, toad, entity);
            }
            return InteractionResult.SUCCESS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(Curses::allowDamage);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!(entity instanceof ServerPlayer p) || taken <= 0.0F) {
                return;
            }
            Curse c = ModData.curse(p);
            if (c == null) {
                return;
            }
            ServerLevel level = p.level();
            if (c.is(CurseKind.TOAD)) {
                level.playSound(null, p.getX(), p.getY() + 0.4, p.getZ(), ModSounds.TOAD_CROAK, SoundSource.PLAYERS, 1.0F, 1.5F);
            } else if (c.is(CurseKind.BUTT)) {
                level.playSound(null, p.getX(), p.getY() + 0.8, p.getZ(), ModSounds.MUFFLED, SoundSource.PLAYERS, 1.0F, 1.3F);
            } else if (c.is(CurseKind.BALLOON) && !c.popping()) {
                // a balloon bounces off whatever hits it
                level.playSound(null, p.getX(), p.getY() + 1.2, p.getZ(), ModSounds.BALLOON_SQUEAK, SoundSource.PLAYERS, 1.2F,
                        0.9F + p.getRandom().nextFloat() * 0.3F);
                Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
                if (from != null) {
                    Vec3 away = p.position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
                    away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
                    p.push(away.x * 1.1, 0.35, away.z * 1.1);
                    p.syncVelocity = true;
                }
            }
        });
        ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.CONTENT_PHASE, (sender, message) -> {
            if (sender == null) {
                return message;
            }
            Curse c = ModData.curse(sender);
            if (c != null && c.is(CurseKind.TOAD)) {
                return Component.literal(Talk.croaks(message.getString(), sender.getRandom()));
            }
            if (c != null && c.is(CurseKind.BUTT)) {
                return Component.literal(Talk.mumbles(message.getString(), sender.getRandom()));
            }
            return message;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            handler.player.refreshDimensions();
            modifiers(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CROUCHED.remove(handler.player.getUUID());
            TONGUES.remove(handler.player.getUUID());
        });
    }

    // ------------------------------------------------------------------ the bottle

    /** The bottle shatters at {@code at}: everyone in reach is cursed. */
    public static void splash(ServerLevel level, Entity bottle, CurseKind kind, Vec3 at, @Nullable Entity direct, @Nullable Entity thrower) {
        AABB box = new AABB(at, at).inflate(SPLASH, 2.0, SPLASH);
        List<ServerPlayer> hit = new ArrayList<>();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator())) {
            if (p == direct || p.position().add(0.0, p.getBbHeight() * 0.5, 0.0).distanceToSqr(at) < SPLASH * SPLASH) {
                hit.add(p);
            }
        }
        boolean others = hit.stream().anyMatch(p -> p != thrower);
        for (ServerPlayer p : hit) {
            // the witch is spared, unless she threw it at her own feet with nobody else around
            if (p != thrower || !others) {
                curse(p, kind, thrower);
            }
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof Player) && e.isAlive())) {
            if (e != direct && e.distanceToSqr(at) > SPLASH * SPLASH) {
                continue;
            }
            switch (kind) {
                case TOAD -> toFrog(level, e);
                case BALLOON -> e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200, 1));
                case BUTT -> e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
            }
        }
        level.playSound(null, at.x, at.y, at.z, ModSounds.CURSE_CAST, SoundSource.PLAYERS, 1.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | kind.color()), at.x, at.y + 0.4, at.z, 70,
                SPLASH * 0.4, 0.6, SPLASH * 0.4, 0.4);
        level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 0.5, at.z, 30, SPLASH * 0.35, 0.5, SPLASH * 0.35, 0.05);
    }

    /** A mob in the splash of the toad potion: a frog (not bosses, pets or named animals). */
    private static void toFrog(ServerLevel level, LivingEntity e) {
        if (!(e instanceof Mob mob) || e instanceof Frog || e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden
                || mob.hasCustomName() || e instanceof OwnableEntity owned && owned.getOwnerReference() != null) {
            return;
        }
        Vec3 at = e.position();
        Frog frog = mob.convertTo(EntityTypes.FROG, ConversionParams.single(mob, false, false), f -> {
        });
        if (frog != null) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y + 0.5, at.z, 20, 0.4, 0.4, 0.4, 0.1);
            level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | CurseKind.TOAD.color()), at.x, at.y + 0.5,
                    at.z, 25, 0.5, 0.5, 0.5, 0.2);
            level.playSound(null, at.x, at.y, at.z, ModSounds.TOAD_CROAK, SoundSource.NEUTRAL, 1.0F, 1.3F);
        }
    }

    /** Curses a player (any older curse gives way). */
    public static void curse(ServerPlayer p, CurseKind kind, @Nullable Entity witch) {
        Curse old = ModData.curse(p);
        if (old != null && old.popping()) {
            return;
        }
        ServerLevel level = p.level();
        p.setAttached(ModData.CURSE, new Curse(kind.ordinal(), level.getGameTime(), 0L));
        p.refreshDimensions();
        modifiers(p);
        SoundEvent sound = switch (kind) {
            case TOAD -> ModSounds.TOAD_TRANSFORM;
            case BUTT -> ModSounds.BUTT_SLURP;
            case BALLOON -> ModSounds.BALLOON_INFLATE;
        };
        level.playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), sound, SoundSource.PLAYERS, 1.5F, 1.0F);
        Vec3 mid = p.position().add(0.0, 0.9, 0.0);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | kind.color()), mid.x, mid.y, mid.z, 50, 0.5,
                0.7, 0.5, 0.3);
        switch (kind) {
            case TOAD -> level.sendParticles(ParticleTypes.ITEM_SLIME, mid.x, mid.y - 0.4, mid.z, 30, 0.5, 0.3, 0.5, 0.1);
            case BUTT -> level.sendParticles(ModParticles.DUNG, mid.x, mid.y - 0.1, mid.z, 12, 0.3, 0.3, 0.3, 0.05);
            case BALLOON -> level.sendParticles(ParticleTypes.POOF, mid.x, mid.y, mid.z, 20, 0.5, 0.5, 0.5, 0.05);
        }
        Gore.hurtScreen(p, Net.VictimFx.CURSE, kind.ordinal());
        String key = "punizioni.curse." + kind.commandName();
        Component line = witch instanceof Player w && w != p
                ? Component.translatable(key + ".by", p.getDisplayName(), w.getDisplayName())
                : Component.translatable(key, p.getDisplayName());
        line = line.copy().withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer o : level.players()) {
            if (o.distanceToSqr(p) < 64.0 * 64.0) {
                o.sendSystemMessage(line);
            }
        }
        p.sendOverlayMessage(Component.translatable(key + ".you").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Lifts the curse (the command; otherwise only death does it). */
    public static void lift(ServerPlayer p) {
        if (p.hasAttached(ModData.CURSE)) {
            p.removeAttached(ModData.CURSE);
            p.refreshDimensions();
            modifiers(p);
            POPPERS.remove(p.getUUID());
        }
    }

    // ------------------------------------------------------------------ every tick

    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Curse c = ModData.curse(p);
            if (c == null) {
                if (p.tickCount % 20 == 0) {
                    modifiers(p);
                }
                CROUCHED.remove(p.getUUID());
                continue;
            }
            if (p.tickCount % 20 == 0) {
                modifiers(p);
            }
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            ServerLevel level = p.level();
            long now = level.getGameTime();
            UUID id = p.getUUID();
            CurseKind kind = c.type();
            if (kind == CurseKind.TOAD) {
                if (CurseClock.at(id, now, CurseClock.CROAK)) {
                    level.playSound(null, p.getX(), p.getY() + 0.4, p.getZ(), ModSounds.TOAD_CROAK, SoundSource.PLAYERS, 1.3F,
                            0.85F + p.getRandom().nextFloat() * 0.2F);
                }
            } else if (kind == CurseKind.BUTT) {
                Vec3 face = buttFace(p);
                if (CurseClock.at(id, now, CurseClock.GRUNT)) {
                    level.playSound(null, face.x, face.y, face.z, ModSounds.MUFFLED, SoundSource.PLAYERS, 1.0F, 0.9F + p.getRandom().nextFloat() * 0.25F);
                }
                if (CurseClock.at(id, now, CurseClock.FART) && now - c.since() > 60L) {
                    // straight in its own face
                    level.playSound(null, face.x, face.y, face.z, ModSounds.BUTT_FART, SoundSource.PLAYERS, 1.2F, 0.85F + p.getRandom().nextFloat() * 0.3F);
                    level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF6E7A2A), face.x, face.y, face.z, 18, 0.25, 0.2,
                            0.25, 0.05);
                    level.sendParticles(ParticleTypes.SMOKE, face.x, face.y, face.z, 6, 0.15, 0.1, 0.15, 0.01);
                    p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, false, false, true));
                    Gore.hurtScreen(p, Net.VictimFx.FART, 1.0F);
                }
            } else if (kind == CurseKind.BALLOON) {
                p.resetFallDistance();
                if (c.popping()) {
                    if (now - c.popAt() >= ZIP_TICKS) {
                        burst(level, p);
                    }
                } else if (p.isShiftKeyDown() && !p.isPassenger()) {
                    int n = CROUCHED.merge(id, 1, Integer::sum);
                    if (n % 10 == 0) {
                        level.playSound(null, p.getX(), p.getY() + 1.2, p.getZ(), ModSounds.BALLOON_SQUEAK, SoundSource.PLAYERS, 0.8F, 0.7F + n * 0.012F);
                    }
                    if (n == 20) {
                        p.sendOverlayMessage(Component.translatable("punizioni.curse.pallone.hold").withStyle(ChatFormatting.LIGHT_PURPLE));
                    }
                    if (n >= SELF_POP) {
                        pop(p, null);
                    }
                } else {
                    CROUCHED.remove(id);
                }
            }
        }
    }

    /** Where the face is, stuck in the backside (the body faces away from where the player looks). */
    public static Vec3 buttFace(Player p) {
        Vec3 look = Vec3.directionFromRotation(0.0F, p.getYRot());
        return p.position().add(look.x * 0.28, 0.82, look.z * 0.28);
    }

    // ------------------------------------------------------------------ the balloon

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer p)) {
            return true;
        }
        Curse c = ModData.curse(p);
        if (c == null || !c.is(CurseKind.BALLOON)) {
            return true;
        }
        if (c.popping()) {
            // whizzing round: only the end of it
            return source.is(ModDamage.POPPED) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        if (sharp(source)) {
            pop(p, source.getEntity() instanceof Player popper ? popper : null);
            return false;
        }
        return true;
    }

    /** What pops a balloon: blades, points, arrows and tridents, thorns, cacti, fire, explosions. */
    private static boolean sharp(DamageSource source) {
        if (source.getDirectEntity() instanceof AbstractArrow) {
            return true;
        }
        if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.STALAGMITE)
                || source.is(DamageTypes.FALLING_STALACTITE) || source.is(DamageTypes.THORNS) || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_LIGHTNING)) {
            return true;
        }
        if (source.getDirectEntity() instanceof LivingEntity attacker && source.getEntity() == attacker) {
            ItemStack held = attacker.getWeaponItem();
            return held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES) || held.is(ItemTags.SPEARS) || held.is(Items.TRIDENT) || held.is(Items.SHEARS)
                    || held.is(ModItems.EXECUTIONER_AXE);
        }
        return false;
    }

    /** BANG: the balloon whizzes round deflating (every client draws it), then the player dies. */
    public static void pop(ServerPlayer p, @Nullable Player popper) {
        Curse c = ModData.curse(p);
        if (c == null || !c.is(CurseKind.BALLOON) || c.popping()) {
            return;
        }
        ServerLevel level = p.level();
        long now = level.getGameTime();
        p.setAttached(ModData.CURSE, new Curse(c.kind(), c.since(), now));
        if (popper != null && popper != p) {
            POPPERS.put(p.getUUID(), popper.getUUID());
        } else {
            POPPERS.remove(p.getUUID());
        }
        CROUCHED.remove(p.getUUID());
        Vec3 mid = p.position().add(0.0, 1.2, 0.0);
        level.playSound(null, mid.x, mid.y, mid.z, ModSounds.BALLOON_POP, SoundSource.PLAYERS, 2.5F, 0.9F + p.getRandom().nextFloat() * 0.2F);
        level.playSound(null, mid.x, mid.y, mid.z, ModSounds.BALLOON_DEFLATE, SoundSource.PLAYERS, 1.6F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION, mid.x, mid.y, mid.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | CurseKind.BALLOON.color()), mid.x, mid.y, mid.z,
                40, 0.6, 0.6, 0.6, 0.4);
        Component line = popper != null && popper != p
                ? Component.translatable("punizioni.curse.pallone.popped.by", p.getDisplayName(), popper.getDisplayName())
                : Component.translatable("punizioni.curse.pallone.popped", p.getDisplayName());
        line = line.copy().withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer o : level.players()) {
            if (o.distanceToSqr(p) < 64.0 * 64.0) {
                o.sendSystemMessage(line);
            }
        }
    }

    /** The end of the whizzing: what is left of the balloon falls dead. */
    private static void burst(ServerLevel level, ServerPlayer p) {
        UUID by = POPPERS.remove(p.getUUID());
        Player popper = by != null ? level.getServer().getPlayerList().getPlayer(by) : null;
        ModDamage.hurt(level, p, ModDamage.POPPED, 1000.0F, popper);
        if (p.isAlive()) {
            // a totem: the balloon is gone, the player stays
            lift(p);
        }
    }

    // ------------------------------------------------------------------ the toad's tongue

    private static void tongue(ServerLevel level, ServerPlayer toad, Entity target) {
        long now = level.getGameTime();
        Long last = TONGUES.get(toad.getUUID());
        if (last != null && now - last < 8L) {
            return;
        }
        TONGUES.put(toad.getUUID(), now);
        level.playSound(null, toad.getX(), toad.getY() + 0.3, toad.getZ(), ModSounds.TONGUE, SoundSource.PLAYERS, 1.0F,
                0.9F + toad.getRandom().nextFloat() * 0.25F);
        if (target instanceof LivingEntity living && living.isAlive() && target != toad) {
            living.hurtServer(level, level.damageSources().playerAttack(toad), TONGUE_DAMAGE);
            // the tongue drags whatever it catches a little closer
            Vec3 pull = toad.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
            if (pull.lengthSqr() > 1.0E-4) {
                pull = pull.normalize().scale(0.35);
                living.push(pull.x, 0.12, pull.z);
                living.syncVelocity = true;
            }
            Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
            level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.05);
        }
    }

    // ------------------------------------------------------------------ attributes

    private static final Identifier JUMP = Punizioni.id("curse_jump");
    private static final Identifier SPEED = Punizioni.id("curse_speed");
    private static final Identifier FALL = Punizioni.id("curse_fall");
    private static final Identifier SWIM = Punizioni.id("curse_swim");
    private static final Identifier AIR = Punizioni.id("curse_air");
    private static final Identifier REACH = Punizioni.id("curse_reach");
    private static final Identifier GRAVITY = Punizioni.id("curse_gravity");

    /** The body the curse gives: a toad hops high, swims and holds its breath; a balloon weighs nothing. */
    public static void modifiers(ServerPlayer p) {
        Curse c = ModData.curse(p);
        CurseKind kind = c != null ? c.type() : null;
        boolean toad = kind == CurseKind.TOAD;
        boolean balloon = kind == CurseKind.BALLOON;
        set(p, Attributes.JUMP_STRENGTH, JUMP, toad ? 0.22 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.MOVEMENT_SPEED, SPEED, toad ? -0.3 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(p, Attributes.SAFE_FALL_DISTANCE, FALL, toad ? 6.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM, toad ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.OXYGEN_BONUS, AIR, toad ? 8.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.ENTITY_INTERACTION_RANGE, REACH, toad ? 2.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.GRAVITY, GRAVITY, balloon ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void set(ServerPlayer p, Holder<Attribute> attribute, Identifier id, double value, AttributeModifier.Operation op) {
        AttributeInstance a = p.getAttribute(attribute);
        if (a == null) {
            return;
        }
        if (value == 0.0) {
            if (a.getModifier(id) != null) {
                a.removeModifier(id);
            }
        } else {
            AttributeModifier m = a.getModifier(id);
            if (m == null || m.amount() != value) {
                a.addOrUpdateTransientModifier(new AttributeModifier(id, value, op));
            }
        }
    }

    /** Whether a balloon can be held on the ground (the server's "flying" check leaves it alone). */
    public static boolean floats(Player p) {
        return balloon(p);
    }
}

package com.punizioni.server;

import com.punizioni.ModSounds;
import com.punizioni.Punizioni;
import com.punizioni.data.Bound;
import com.punizioni.data.ModData;
import com.punizioni.item.ModItems;
import com.punizioni.machine.Machine;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The prisoner's rope. Right click a player holding it: they are tied up like a salami (arms to the
 * body, rope from the shoulders to the ankles: they can only hop) and follow at the end of a 4-block rope (their own client pulls them along: see the client's Captives). Only the
 * captor unties them (right click), or anyone cuts the rope with shears. Right click a fence while
 * leading prisoners: they are knotted to it; the captor (or anyone with a rope) takes them back by
 * clicking them or the fence. Tied hands do nothing: no hitting, mining, building, using, opening.
 */
public final class Bonds {
    private Bonds() {
    }

    /** The rope's length (blocks). */
    public static final double LENGTH = 4.0;
    /** Further than this (a teleport, a portal, lag): the prisoner is brought back to the rope's end. */
    public static final double FAR = 12.0;
    /** Prisoners knotted to a post or led by a captor further than this do not get tied by a click on a fence. */
    private static final double LEAD_RANGE = 10.0;
    private static final Identifier SLOW = Punizioni.id("bound_slow");
    /** A captor who is gone this long (ticks) lets the rope go. */
    private static final long GRACE = 100L;

    private static final Map<UUID, Long> MISSING = new HashMap<>();

    public static boolean bound(Player player) {
        return ModData.isBound(player);
    }

    public static void init() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (bound(player)) {
                return InteractionResult.FAIL;
            }
            if (!(entity instanceof Player target) || target == player || target.isSpectator()) {
                return InteractionResult.PASS;
            }
            return interact(player, target, hand);
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (bound(player)) {
                return InteractionResult.FAIL;
            }
            if (hand != InteractionHand.MAIN_HAND || !level.getBlockState(hit.getBlockPos()).is(BlockTags.FENCES)) {
                return InteractionResult.PASS;
            }
            return fence(player, hit.getBlockPos());
        });
        UseItemCallback.EVENT.register((player, level, hand) -> bound(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> bound(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> bound(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !bound(player));
        // a captor who dies lets go of the rope
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer dead) {
                for (ServerPlayer p : dead.level().getServer().getPlayerList().getPlayers()) {
                    Bound b = ModData.bound(p);
                    if (b != null && b.knot().isEmpty() && b.captor().equals(dead.getUUID())) {
                        free(p, true);
                        p.sendOverlayMessage(Component.translatable("punizioni.bonds.loose").withStyle(ChatFormatting.GREEN));
                    }
                }
            }
        });
    }

    /** Both sides decide the same way (the bonds are known by every client): the server also acts. */
    private static InteractionResult interact(Player player, Player target, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Bound b = ModData.bound(target);
        boolean server = player instanceof ServerPlayer;
        if (b != null) {
            if (held.is(ModItems.GRUEL)) {
                // feeding the prisoner: the bowl does it
                return InteractionResult.PASS;
            }
            if (held.is(Items.SHEARS)) {
                if (server) {
                    cut((ServerPlayer) target, (ServerPlayer) player, held, hand);
                }
                return InteractionResult.SUCCESS;
            }
            if (b.knot().isPresent() && (b.captor().equals(player.getUUID()) || held.is(ModItems.PRISONER_ROPE))) {
                if (server) {
                    take((ServerPlayer) target, (ServerPlayer) player);
                }
                return InteractionResult.SUCCESS;
            }
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (b.knot().isEmpty() && b.captor().equals(player.getUUID())) {
                if (server) {
                    untie((ServerPlayer) target, (ServerPlayer) player);
                }
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("punizioni.bonds.someones", target.getName(), captorName(sp, b))
                        .withStyle(ChatFormatting.GOLD));
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(ModItems.PRISONER_ROPE) && !(target.getVehicle() instanceof Machine)) {
            if (server) {
                tie((ServerPlayer) target, (ServerPlayer) player, held);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static Component captorName(ServerPlayer anyone, Bound b) {
        ServerPlayer captor = anyone.level().getServer().getPlayerList().getPlayer(b.captor());
        return captor != null ? captor.getName() : Component.literal("?");
    }

    // ------------------------------------------------------------------ what can be done with a prisoner

    public static void tie(ServerPlayer target, ServerPlayer captor, ItemStack rope) {
        ServerLevel level = target.level();
        target.setAttached(ModData.BOUND, new Bound(captor.getUUID(), Optional.empty(), level.getGameTime()));
        rope.consume(1, captor);
        target.stopRiding();
        target.closeContainer();
        level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), ModSounds.ROPE_CREAK, SoundSource.PLAYERS, 0.7F, 1.3F);
        target.sendSystemMessage(Component.translatable("punizioni.bonds.tied_you", captor.getName()).withStyle(ChatFormatting.RED));
        target.sendOverlayMessage(Component.translatable("punizioni.bonds.hint").withStyle(ChatFormatting.GOLD));
        captor.sendOverlayMessage(Component.translatable("punizioni.bonds.tied", target.getName()).withStyle(ChatFormatting.GOLD));
        slow(target, true);
    }

    public static void untie(ServerPlayer target, ServerPlayer by) {
        free(target, false);
        ItemStack rope = new ItemStack(ModItems.PRISONER_ROPE);
        if (!by.hasInfiniteMaterials() && !by.getInventory().add(rope)) {
            by.spawnAtLocation(by.level(), rope);
        }
        target.level().playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.LEAD_UNTIED, SoundSource.PLAYERS, 1.0F, 1.0F);
        target.sendSystemMessage(Component.translatable("punizioni.bonds.untied_you", by.getName()).withStyle(ChatFormatting.GREEN));
        by.sendOverlayMessage(Component.translatable("punizioni.bonds.untied", target.getName()).withStyle(ChatFormatting.GOLD));
    }

    public static void cut(ServerPlayer target, ServerPlayer by, ItemStack shears, InteractionHand hand) {
        Bound b = ModData.bound(target);
        free(target, false);
        shears.hurtAndBreak(1, by, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        ServerLevel level = target.level();
        level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.SHEARS_SNIP, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.9, target.getZ(), 8, 0.2, 0.1, 0.2, 0.05);
        target.sendSystemMessage(Component.translatable("punizioni.bonds.cut_you", by.getName()).withStyle(ChatFormatting.GREEN));
        by.sendOverlayMessage(Component.translatable("punizioni.bonds.cut", target.getName()).withStyle(ChatFormatting.GOLD));
        if (b != null && !b.captor().equals(by.getUUID())) {
            ServerPlayer captor = level.getServer().getPlayerList().getPlayer(b.captor());
            if (captor != null) {
                captor.sendSystemMessage(Component.translatable("punizioni.bonds.cut_captor", by.getName(), target.getName()).withStyle(ChatFormatting.RED));
            }
        }
    }

    /** From a post (or another guard) into this one's hand. */
    public static void take(ServerPlayer target, ServerPlayer by) {
        Bound b = ModData.bound(target);
        if (b == null) {
            return;
        }
        target.setAttached(ModData.BOUND, b.led(by.getUUID()));
        target.level().playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.1F);
        by.sendOverlayMessage(Component.translatable("punizioni.bonds.taken", target.getName()).withStyle(ChatFormatting.GOLD));
        target.sendOverlayMessage(Component.translatable("punizioni.bonds.taken_you", by.getName()).withStyle(ChatFormatting.RED));
    }

    /** A click on a fence: the prisoners on this one's rope get knotted to it, or the ones knotted to it come back to the hand. */
    private static InteractionResult fence(Player player, BlockPos post) {
        List<Player> led = new ArrayList<>();
        List<Player> knotted = new ArrayList<>();
        for (Player p : player.level().players()) {
            Bound b = ModData.bound(p);
            if (b == null || p.distanceToSqr(player) > LEAD_RANGE * LEAD_RANGE) {
                continue;
            }
            if (b.knot().isEmpty() && b.captor().equals(player.getUUID())) {
                led.add(p);
            } else if (b.knot().isPresent() && b.knot().get().equals(post)
                    && (b.captor().equals(player.getUUID()) || player.getMainHandItem().is(ModItems.PRISONER_ROPE))) {
                knotted.add(p);
            }
        }
        if (led.isEmpty() && knotted.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer captor) {
            ServerLevel level = captor.level();
            if (!led.isEmpty()) {
                for (Player p : led) {
                    Bound b = ModData.bound(p);
                    p.setAttached(ModData.BOUND, b.knotted(post));
                    captor.sendOverlayMessage(Component.translatable("punizioni.bonds.knotted", p.getName()).withStyle(ChatFormatting.GOLD));
                    if (p instanceof ServerPlayer sp) {
                        sp.sendOverlayMessage(Component.translatable("punizioni.bonds.knotted_you").withStyle(ChatFormatting.RED));
                    }
                }
                level.playSound(null, post, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.9F);
            } else {
                for (Player p : knotted) {
                    if (p instanceof ServerPlayer sp) {
                        take(sp, captor);
                    }
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Untied: the bonds are gone; dropRope leaves the rope on the ground where they stand. */
    public static void free(ServerPlayer target, boolean dropRope) {
        target.removeAttached(ModData.BOUND);
        MISSING.remove(target.getUUID());
        slow(target, false);
        if (dropRope) {
            target.spawnAtLocation(target.level(), new ItemStack(ModItems.PRISONER_ROPE));
        }
    }

    private static void slow(ServerPlayer target, boolean on) {
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (on && !speed.hasModifier(SLOW)) {
            speed.addTransientModifier(new AttributeModifier(SLOW, -0.45, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!on) {
            speed.removeModifier(SLOW);
        }
    }

    // ------------------------------------------------------------------ every tick

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Bound b = ModData.bound(p);
            if (b == null) {
                continue;
            }
            slow(p, true);
            if (p.getVehicle() instanceof Machine || !p.isAlive()) {
                // in a machine the rope waits
                continue;
            }
            ServerLevel level = p.level();
            if (b.knot().isPresent()) {
                BlockPos post = b.knot().get();
                if (!level.isLoaded(post)) {
                    continue;
                }
                if (!level.getBlockState(post).is(BlockTags.FENCES)) {
                    free(p, true);
                    p.sendOverlayMessage(Component.translatable("punizioni.bonds.loose").withStyle(ChatFormatting.GREEN));
                    continue;
                }
                Vec3 knot = Vec3.atBottomCenterOf(post);
                if (p.position().distanceToSqr(knot) > FAR * FAR) {
                    bringBack(p, level, knot, p.getYRot());
                }
                continue;
            }
            ServerPlayer captor = server.getPlayerList().getPlayer(b.captor());
            if (captor == null || !captor.isAlive() || captor.isSpectator()) {
                long since = MISSING.computeIfAbsent(p.getUUID(), id -> now);
                if (now - since > GRACE) {
                    free(p, true);
                    p.sendOverlayMessage(Component.translatable("punizioni.bonds.loose").withStyle(ChatFormatting.GREEN));
                }
                continue;
            }
            MISSING.remove(p.getUUID());
            if (captor.level() != level || p.distanceToSqr(captor) > FAR * FAR) {
                // behind the captor, at the rope's length
                float yaw = captor.getYRot() * Mth.DEG_TO_RAD;
                Vec3 at = captor.position().add(Mth.sin(yaw) * 1.5, 0.0, -Mth.cos(yaw) * 1.5);
                bringBack(p, captor.level(), at, captor.getYRot());
            }
        }
    }

    private static void bringBack(ServerPlayer p, ServerLevel level, Vec3 at, float yaw) {
        p.teleportTo(level, at.x, at.y, at.z, Set.of(), yaw, p.getXRot(), true);
        p.resetFallDistance();
    }
}

package com.filorosso.server;

import com.filorosso.data.Couple;
import com.filorosso.data.ModData;
import com.filorosso.data.Status;
import com.filorosso.sound.ModSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "A terra": a player who would die while the partner is alive nearby falls to the ground instead,
 * and has some seconds to be revived (the partner crouches next to them, or holds the use key on
 * them). Nobody comes in time: real death. After respawning you are brought back to your partner.
 */
public final class Revive {
    private Revive() {
    }

    private static final net.minecraft.resources.Identifier SLOW = com.filorosso.FiloRosso.id("downed");
    /** Last time the partner held the use key on a downed player. */
    private static final Map<UUID, Long> HOLDING = new HashMap<>();

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && Abilities.eternal(player)) {
                // "Eterni": you cannot fall
                player.setHealth(1.0F);
                return false;
            }
            if (entity instanceof ServerPlayer player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return !down(player);
            }
            return true;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player instanceof ServerPlayer sp && entity instanceof ServerPlayer target && ModData.downed(target)) {
                HOLDING.put(target.getUUID(), sp.level().getServer().overworld().getGameTime());
                return InteractionResult.SUCCESS;
            }
            return ModData.downed(player) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> ModData.downed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) -> ModData.downed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> ModData.downed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            newPlayer.setAttached(ModData.STATUS, Status.NONE);
            slow(newPlayer, false);
            if (!alive) {
                backToPartner(newPlayer);
            }
        });
    }

    private static int seconds(MinecraftServer server) {
        return server.overworld().getGameRules().get(ModRules.REVIVE_SECONDS);
    }

    /** Would-be death: downed instead, if the partner can come. Returns true if downed. */
    private static boolean down(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (ModData.downed(player) || seconds(server) <= 0) {
            return false;
        }
        ServerPlayer partner = Bonds.partner(player);
        if (partner == null || !partner.isAlive() || ModData.downed(partner) || partner.isSpectator() || partner.level() != player.level()
                || partner.distanceTo(player) > 64.0) {
            return false;
        }
        long now = server.overworld().getGameTime();
        player.setHealth(1.0F);
        SharedLife.onDowned(partner);
        player.clearFire();
        player.setAttached(ModData.STATUS, new Status(now + seconds(server) * 20L, 0));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, seconds(server) * 20 + 40, 0, false, false));
        slow(player, true);
        Bonds.sound(player, ModSounds.DOWNED, 1.0F);
        Bonds.title(player, Component.translatable("filorosso.title.downed").withStyle(ChatFormatting.RED),
                Component.translatable("filorosso.title.downed.sub", partner.getGameProfile().name(), seconds(server)), 5, 60, 10);
        Bonds.title(partner, Component.translatable("filorosso.title.partner_downed", player.getGameProfile().name()).withStyle(ChatFormatting.RED),
                Component.translatable("filorosso.title.partner_downed.sub"), 5, 60, 10);
        Bonds.sound(partner, ModSounds.HEARTBEAT, 1.0F);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("filorosso.msg.downed", player.getGameProfile().name(),
                partner.getGameProfile().name(), seconds(server)).withStyle(ChatFormatting.RED), false);
        return true;
    }

    private static void slow(ServerPlayer player, boolean on) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(SLOW);
        if (on) {
            speed.addTransientModifier(new AttributeModifier(SLOW, -0.75, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Status status = ModData.status(player);
            if (!status.downed()) {
                continue;
            }
            ServerPlayer partner = Bonds.partner(player);
            boolean partnerOk = partner != null && partner.isAlive() && !ModData.downed(partner) && partner.level() == player.level();
            if (now >= status.downedUntil() || !partnerOk || !player.isAlive()) {
                abandon(player, partner);
                continue;
            }
            Long held = HOLDING.get(player.getUUID());
            boolean helping = partner.distanceTo(player) < 2.2 && (partner.isShiftKeyDown() || held != null && now - held <= 6);
            int revive = status.revive();
            revive = helping ? revive + 1 : Math.max(0, revive - 2);
            if (now % 20 == 0) {
                Bonds.sound(player, ModSounds.HEARTBEAT, 1.0F);
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 0.4, player.getZ(), 1,
                        0.2, 0.1, 0.2, 0.0);
            }
            if (helping && now % 4 == 0) {
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 0.6, player.getZ(), 1, 0.3, 0.2, 0.3, 0.0);
                partner.sendSystemMessage(Component.translatable("filorosso.msg.reviving", player.getGameProfile().name(),
                        revive * 100 / Status.REVIVE_TICKS).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            if (revive >= Status.REVIVE_TICKS) {
                revived(player, partner);
            } else if (revive != status.revive()) {
                player.setAttached(ModData.STATUS, new Status(status.downedUntil(), revive));
            }
        }
    }

    private static void revived(ServerPlayer player, ServerPlayer partner) {
        player.setAttached(ModData.STATUS, Status.NONE);
        HOLDING.remove(player.getUUID());
        slow(player, false);
        player.setHealth(Math.min(player.getMaxHealth(), 8.0F));
        SharedLife.onRevived(player, partner);
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 2, false, true));
        Bonds.sound(player, ModSounds.REVIVE, 1.0F);
        Bonds.sound(partner, ModSounds.REVIVE, 1.0F);
        Bonds.hearts(player, 30);
        MinecraftServer server = player.level().getServer();
        server.getPlayerList().broadcastSystemMessage(Component.translatable("filorosso.msg.revived", partner.getGameProfile().name(),
                player.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        Couple couple = Bonds.coupleOf(player);
        if (couple != null) {
            Affinity.add(server, couple, 25, false);
            Challenges.add(server, couple, Challenges.REVIVES, 1);
        }
    }

    private static void abandon(ServerPlayer player, ServerPlayer partner) {
        player.setAttached(ModData.STATUS, Status.NONE);
        HOLDING.remove(player.getUUID());
        slow(player, false);
        ServerLevel level = (ServerLevel) player.level();
        player.hurtServer(level, partner != null ? level.damageSources().source(ModDamage.ABANDONED, partner, partner)
                : level.damageSources().source(ModDamage.ABANDONED), Float.MAX_VALUE);
    }

    /** After respawning: back to the partner (if they are alive), so the thread holds. */
    private static void backToPartner(ServerPlayer player) {
        ServerPlayer partner = Bonds.partner(player);
        if (partner == null || !partner.isAlive() || partner.isSpectator()) {
            return;
        }
        Vec3 to = partner.position();
        player.teleportTo((ServerLevel) partner.level(), to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), true);
        player.resetFallDistance();
        player.sendSystemMessage(Component.translatable("filorosso.msg.respawn_partner", partner.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE),
                true);
    }

    /** For mixins on both sides: downed players lie on the ground. */
    public static boolean downed(Player player) {
        return ModData.downed(player);
    }
}

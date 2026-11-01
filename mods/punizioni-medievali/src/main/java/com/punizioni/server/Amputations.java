package com.punizioni.server;

import com.punizioni.ModDamage;
import com.punizioni.Punizioni;
import com.punizioni.data.Body;
import com.punizioni.data.Limb;
import com.punizioni.data.ModData;
import com.punizioni.item.ModItems;
import com.punizioni.machine.ChoppingBlock;
import com.punizioni.machine.Machine;
import com.punizioni.machine.Wheel;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * What the punishments take away, until death: no right hand, nothing in it (no hitting, no digging);
 * no left hand, nothing in that one; a foot less and you limp, both and you crawl; broken legs slow
 * you down, broken arms hit and dig weakly. Fresh stumps bleed for a while. Also the clicks: someone
 * held in a machine cannot do anything, a click on them reaches the machine, and the axe and the
 * mallet do their work on the chopping block and on the wheel.
 */
public final class Amputations {
    private Amputations() {
    }

    private static final Identifier SPEED = Punizioni.id("maimed_speed");
    private static final Identifier JUMP = Punizioni.id("maimed_jump");
    private static final Identifier DAMAGE = Punizioni.id("broken_arms_damage");
    private static final Identifier DIG = Punizioni.id("broken_arms_dig");

    private record Bleed(int ticksLeft, @Nullable UUID by) {
    }

    private static final Map<UUID, Bleed> BLEEDING = new HashMap<>();

    /** A fresh stump: it bleeds for this many ticks (a heart of damage every 1.5 seconds). */
    public static void bleed(LivingEntity victim, @Nullable Player by, int ticks) {
        BLEEDING.put(victim.getUUID(), new Bleed(ticks, by != null ? by.getUUID() : null));
    }

    public static boolean cannotUse(Player player, InteractionHand hand) {
        Body body = ModData.body(player);
        return hand == InteractionHand.MAIN_HAND ? body.lacks(Limb.RIGHT_HAND) : body.lacks(Limb.LEFT_HAND);
    }

    public static void init() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player.getVehicle() instanceof Machine || ModData.body(player).lacks(Limb.RIGHT_HAND)) {
                return InteractionResult.FAIL;
            }
            if (entity.getVehicle() instanceof ChoppingBlock block && player.getMainHandItem().is(ModItems.EXECUTIONER_AXE)) {
                if (level instanceof ServerLevel server) {
                    block.chop(server, player);
                    player.getMainHandItem().hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                }
                return InteractionResult.SUCCESS;
            }
            if (entity.getVehicle() instanceof Wheel wheel && player.getMainHandItem().is(ModItems.EXECUTIONER_MALLET)
                    && wheel.state() == Wheel.TIED) {
                if (level instanceof ServerLevel server) {
                    wheel.strike(server, player);
                    player.getMainHandItem().hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player.getVehicle() instanceof Machine || cannotUse(player, hand)) {
                return InteractionResult.FAIL;
            }
            if (entity.getVehicle() instanceof Machine machine && hand == InteractionHand.MAIN_HAND) {
                return machine.interact(player, hand, entity.position());
            }
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                player.getVehicle() instanceof Machine || ModData.body(player).lacks(Limb.RIGHT_HAND) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                player.getVehicle() instanceof Machine || cannotUse(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) ->
                player.getVehicle() instanceof Machine || cannotUse(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Bleed>> it = BLEEDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Bleed> e = it.next();
            LivingEntity victim = find(server, e.getKey());
            Bleed b = e.getValue();
            if (victim == null || !victim.isAlive() || b.ticksLeft <= 0) {
                it.remove();
                continue;
            }
            e.setValue(new Bleed(b.ticksLeft - 1, b.by));
            ServerLevel level = (ServerLevel) victim.level();
            if (b.ticksLeft % 4 == 0) {
                Gore.burst(level, victim.position().add(0.0, 0.6 + level.getRandom().nextDouble() * 0.6, 0.0), 2, 0.2, 0.0);
            }
            if (b.ticksLeft % 30 == 0) {
                Player by = b.by != null ? server.getPlayerList().getPlayer(b.by) : null;
                ModDamage.hurt(level, victim, ModDamage.BLEEDING, 1.0F, by);
            }
        }
        if (now % 10 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                apply(p, ModData.body(p));
            }
        }
    }

    private static LivingEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof LivingEntity l) {
                return l;
            }
        }
        return null;
    }

    /** Limping, crawling, weak arms: attribute modifiers rebuilt from the body. */
    private static void apply(ServerPlayer p, Body body) {
        int feet = body.feetMissing();
        int legs = body.legsBroken();
        int arms = (body.brokenBone(Body.RIGHT_ARM) ? 1 : 0) + (body.brokenBone(Body.LEFT_ARM) ? 1 : 0);
        double speed = -Math.min(0.85, (feet == 2 ? 0.7 : feet * 0.4) + legs * 0.25);
        double jump = -Math.min(1.0, (feet == 2 ? 1.0 : feet * 0.5) + legs * 0.35);
        set(p, Attributes.MOVEMENT_SPEED, SPEED, speed);
        set(p, Attributes.JUMP_STRENGTH, JUMP, jump);
        set(p, Attributes.ATTACK_DAMAGE, DAMAGE, -0.35 * arms);
        set(p, Attributes.BLOCK_BREAK_SPEED, DIG, -0.4 * arms);
        if (feet == 2 && p.isSprinting()) {
            p.setSprinting(false);
        }
    }

    private static void set(ServerPlayer p, Holder<Attribute> attribute, Identifier id, double value) {
        AttributeInstance a = p.getAttribute(attribute);
        if (a == null) {
            return;
        }
        if (value == 0.0) {
            a.removeModifier(id);
        } else {
            a.addOrUpdateTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Somewhere a stump bleeds from (for effects): the body's middle. */
    public static Vec3 middle(LivingEntity e) {
        return e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
    }
}

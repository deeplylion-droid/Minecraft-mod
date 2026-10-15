package com.disabilities.server;

import com.disabilities.Disabilities;
import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.condition.Picks;
import com.disabilities.condition.Status;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Attribute changes: speed, jump, size, reach... from the conditions and from the episodes in
 * progress. Recomputed from scratch on every change, as two modifiers per attribute (one added, one
 * multiplied), so nothing can pile up or get stuck.
 */
public final class Body {
    private Body() {
    }

    private static final Identifier ADD = Disabilities.id("add");
    private static final Identifier MUL = Disabilities.id("multiply");

    private static final List<Holder<Attribute>> TOUCHED = List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH,
            Attributes.SCALE, Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE, Attributes.BLOCK_BREAK_SPEED,
            Attributes.ATTACK_SPEED, Attributes.FALL_DAMAGE_MULTIPLIER, Attributes.SAFE_FALL_DISTANCE, Attributes.KNOCKBACK_RESISTANCE,
            Attributes.WAYPOINT_RECEIVE_RANGE);

    private static final class Sum {
        double add;
        double mul = 1.0;
    }

    public static void refresh(ServerPlayer player) {
        Picks picks = ModData.picks(player);
        Status status = ModData.status(player);
        long now = player.level().getGameTime();
        Map<Holder<Attribute>, Sum> sums = new LinkedHashMap<>();
        for (Holder<Attribute> attribute : TOUCHED) {
            sums.put(attribute, new Sum());
        }
        for (Condition condition : picks.list()) {
            switch (condition) {
                case BLIND, AMNESIA -> mul(sums, Attributes.WAYPOINT_RECEIVE_RANGE, 0.0);
                case WHEELCHAIR -> mul(sums, Attributes.JUMP_STRENGTH, 0.0);
                case ONE_LEG -> {
                    mul(sums, Attributes.MOVEMENT_SPEED, 0.72);
                    mul(sums, Attributes.JUMP_STRENGTH, 0.9);
                }
                case ONE_ARM -> mul(sums, Attributes.BLOCK_BREAK_SPEED, 0.6);
                case DWARFISM -> {
                    mul(sums, Attributes.SCALE, 0.55);
                    mul(sums, Attributes.MOVEMENT_SPEED, 0.9);
                    add(sums, Attributes.BLOCK_INTERACTION_RANGE, -1.25);
                    add(sums, Attributes.ENTITY_INTERACTION_RANGE, -0.75);
                }
                case OBESITY -> {
                    mul(sums, Attributes.MOVEMENT_SPEED, 0.82);
                    mul(sums, Attributes.JUMP_STRENGTH, 0.92);
                    add(sums, Attributes.FALL_DAMAGE_MULTIPLIER, 0.5);
                    add(sums, Attributes.SAFE_FALL_DISTANCE, -1.0);
                    add(sums, Attributes.KNOCKBACK_RESISTANCE, 0.4);
                }
                default -> {
                }
            }
        }
        // episodes
        if (status.asleep(now) || status.frozen(now)) {
            mul(sums, Attributes.MOVEMENT_SPEED, 0.0);
            mul(sums, Attributes.JUMP_STRENGTH, 0.0);
        }
        if (status.winded(now)) {
            mul(sums, Attributes.MOVEMENT_SPEED, 0.85);
        }
        if (Ailments.isBreathless(player)) {
            mul(sums, Attributes.MOVEMENT_SPEED, 0.65);
            mul(sums, Attributes.BLOCK_BREAK_SPEED, 0.7);
            mul(sums, Attributes.ATTACK_SPEED, 0.8);
        }
        for (Map.Entry<Holder<Attribute>, Sum> e : sums.entrySet()) {
            AttributeInstance instance = player.getAttribute(e.getKey());
            if (instance == null) {
                continue;
            }
            Sum sum = e.getValue();
            set(instance, ADD, sum.add, AttributeModifier.Operation.ADD_VALUE);
            set(instance, MUL, sum.mul - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        player.refreshDimensions();
    }

    private static void set(AttributeInstance instance, Identifier id, double amount, AttributeModifier.Operation operation) {
        if (Math.abs(amount) < 1.0E-6) {
            instance.removeModifier(id);
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current == null || current.amount() != amount || current.operation() != operation) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static void mul(Map<Holder<Attribute>, Sum> sums, Holder<Attribute> attribute, double factor) {
        sums.get(attribute).mul *= factor;
    }

    private static void add(Map<Holder<Attribute>, Sum> sums, Holder<Attribute> attribute, double amount) {
        sums.get(attribute).add += amount;
    }
}

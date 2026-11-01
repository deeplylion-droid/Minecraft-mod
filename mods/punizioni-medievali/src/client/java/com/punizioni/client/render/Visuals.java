package com.punizioni.client.render;

import com.punizioni.data.Body;
import com.punizioni.data.ModData;
import com.punizioni.item.ModItems;
import com.punizioni.machine.ChoppingBlock;
import com.punizioni.machine.Gallows;
import com.punizioni.machine.Guillotine;
import com.punizioni.machine.Machine;
import com.punizioni.machine.MachineKind;
import com.punizioni.machine.QuarteringRack;
import com.punizioni.machine.Wheel;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.item.ItemStack;
import com.punizioni.data.Limb;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

/** What a player's drawing needs from the punishments, copied into the render state each frame. */
public final class Visuals {
    public static final RenderStateDataKey<Visuals> KEY = RenderStateDataKey.create(() -> "punizioni:visuals");

    /** The machine holding them, or null. */
    public @Nullable MachineKind kind;
    public int state;
    public float age;
    public float time;
    /** Rack: stretch; wheel: hub and spin; gallows: drop. */
    public float a;
    public float b;
    public int choice;
    public Body body = Body.NONE;
    /** How stretched the body is now: on the rack, or still, after it. */
    public float stretch;
    /** Ticks since the last lash (the body flinches). */
    public float sinceLash = 1000.0F;
    public boolean alive = true;
    public boolean hood, apron, breeches, boots;
    public int seed;

    /** What the mod draws on this player (or mannequin) now, or null: nothing. */
    public static @Nullable Visuals of(Avatar entity, float partial) {
        Body body = ModData.body(entity);
        boolean hood = entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.EXECUTIONER_HOOD);
        boolean apron = entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.EXECUTIONER_APRON);
        boolean breeches = entity.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.EXECUTIONER_BREECHES);
        boolean boots = entity.getItemBySlot(EquipmentSlot.FEET).is(ModItems.EXECUTIONER_BOOTS);
        long now = entity.level().getGameTime();
        boolean stretched = body.stretchUntil() > now;
        if (!(entity.getVehicle() instanceof Machine) && body == Body.NONE && !hood && !apron && !breeches && !boots && !stretched) {
            return null;
        }
        Visuals v = new Visuals();
        v.body = body;
        v.hood = hood;
        v.apron = apron;
        v.breeches = breeches;
        v.boots = boots;
        v.time = now + partial;
        v.seed = entity.getUUID().hashCode();
        v.alive = entity.isAlive();
        v.sinceLash = body.lastLash() > 0 ? now - body.lastLash() + partial : 1000.0F;
        if (stretched) {
            // a minute of rubber, then it snaps back in the last two seconds, wobbling
            float left = body.stretchUntil() - now - partial;
            float k = Mth.clamp(left / 40.0F, 0.0F, 1.0F);
            v.stretch = body.stretch() * k + (k < 1.0F ? Mth.sin(left * 1.3F) * 0.08F * k : 0.0F);
        }
        if (entity.getVehicle() instanceof Machine m) {
            v.kind = m.kind();
            v.state = m.state();
            v.age = m.stateAge(partial);
            switch (m) {
                case QuarteringRack r -> {
                    v.a = r.stretch(partial);
                    v.stretch = v.a;
                }
                case Wheel w -> {
                    v.a = (float) w.hub(partial);
                    v.b = w.spin(partial);
                }
                case Gallows g -> v.a = g.drop(partial);
                case Guillotine g -> v.a = g.blade(partial);
                case ChoppingBlock c -> v.choice = c.limb().ordinal();
                default -> {
                }
            }
        }
        return v;
    }

    /** Fills the render state of a player (or mannequin) with what the mod draws on them. */
    public static void extract(Avatar entity, AvatarRenderState state, float partial) {
        Visuals v = of(entity, partial);
        state.setData(KEY, v);
        if (v == null) {
            return;
        }
        if (v.kind != null) {
            // the machine holds the body: no falling over when they die, the machine shows what is left
            state.deathTime = 0.0F;
            if (v.kind == MachineKind.BRAZEN_BULL && v.state != Machine.IDLE) {
                // shut inside the bronze: nothing of them shows
                state.isInvisible = true;
                state.isInvisibleToPlayer = true;
                state.nameTag = null;
            }
        }
        // a hand that is gone holds nothing
        if (v.body.lacks(Limb.RIGHT_HAND) || v.body.torn()) {
            state.rightHandItemState.clear();
            state.rightHandItemStack = ItemStack.EMPTY;
            state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        }
        if (v.body.lacks(Limb.LEFT_HAND) || v.body.torn()) {
            state.leftHandItemState.clear();
            state.leftHandItemStack = ItemStack.EMPTY;
            state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        }
        if (v.body.marked()) {
            state.skin = MarkedSkins.marked(state.skin, entity.getUUID(), v.body, entity.level().getGameTime());
        }
        if (v.hood) {
            // the executioner has no name
            state.nameTag = null;
        }
    }
}

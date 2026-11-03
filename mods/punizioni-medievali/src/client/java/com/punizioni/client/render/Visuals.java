package com.punizioni.client.render;

import com.punizioni.data.Body;
import com.punizioni.data.Curse;
import com.punizioni.witch.CurseClock;
import com.punizioni.witch.CurseKind;
import java.util.UUID;
import com.punizioni.data.Filth;
import com.punizioni.data.ModData;
import com.punizioni.item.ModItems;
import com.punizioni.machine.ChoppingBlock;
import com.punizioni.machine.Gallows;
import com.punizioni.machine.Guillotine;
import com.punizioni.machine.Machine;
import com.punizioni.machine.MachineKind;
import com.punizioni.machine.Pillory;
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
import com.punizioni.client.bonds.Captives;
import com.punizioni.client.royal.Guilty;
import com.punizioni.client.royal.Royal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
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
    /** The king's regalia. */
    public boolean crown, cape;
    /** Hands tied with the prisoner's rope; leading someone on one. */
    public boolean bound, captor;
    /** The jester's motley and the prisoner's rags: bits for hat/hood (1), tunic (2), hose/breeches (4), shoes/wraps (8). */
    public int jester, peasant;
    /** Dung on them, and ticks since the last splat. */
    public Filth filth = Filth.CLEAN;
    public float sinceSplat = 1000.0F;
    /** Ticks since they ate gruel (the grimace). */
    public float sinceGruel = 1000.0F;
    /** The witch's clothes: hat (1), dress (2), stockings (4), boots (8). */
    public int witch;
    /** A witch's curse on them, or null; ticks since it took hold; since the balloon popped (-1: not popped). */
    public @Nullable CurseKind curse;
    public float curseAge = 1000.0F;
    public float popAge = -1.0F;
    /** The toad: ticks since its last croak (-1: none lately); in the air; how fast it goes up or down. */
    public float croak = -1.0F;
    public boolean airborne;
    public float vy;
    /** The head in the butt: ticks since the last grunt and the last fart (-1: none lately). */
    public float grunt = -1.0F;
    public float fart = -1.0F;
    /** Ticks of the red flash left after a hit (0: none); how fast they go forward and sideways (blocks a tick, the body's frame). */
    public float hurt;
    public float forward, side;
    /** The head in the butt: how far the body has turned round (0..1; it turns in the first ticks). */
    public float turn;
    /** How far through a swing they are (0..1; -1: not swinging): the toad's tongue. */
    public float swing = -1.0F;
    /** The hand holding the overseer's whip (its lash is drawn from the handle), or null. */
    public @Nullable HumanoidArm whipArm;
    /** Drawn in the inventory's little window, not in the world: no cloth or lash simulation there. */
    public boolean gui;
    public int entityId;
    /** Where they look (degrees), for the whip's crack. */
    public float viewYaw, viewPitch;
    /**
     * The pose the entity is drawn from in this pass, before the renderer's own rotations (the
     * camera's or a shadow pass' transform, then the move to the entity): set by LivingEntityRendererMixin.
     * Cloth and lash live in world space and are drawn through it.
     */
    public final Matrix4f entityPose = new Matrix4f();
    public boolean hasEntityPose;

    /** What the mod draws on this player (or mannequin) now, or null: nothing. */
    public static @Nullable Visuals of(Avatar entity, float partial) {
        Body body = ModData.body(entity);
        boolean hood = entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.EXECUTIONER_HOOD);
        boolean apron = entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.EXECUTIONER_APRON);
        boolean breeches = entity.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.EXECUTIONER_BREECHES);
        boolean boots = entity.getItemBySlot(EquipmentSlot.FEET).is(ModItems.EXECUTIONER_BOOTS);
        boolean crown = entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.ROYAL_CROWN);
        boolean cape = entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.ROYAL_CAPE);
        HumanoidArm whipArm = entity.getMainHandItem().is(ModItems.OVERSEER_WHIP) ? entity.getMainArm()
                : entity.getOffhandItem().is(ModItems.OVERSEER_WHIP) ? entity.getMainArm().getOpposite() : null;
        long now = entity.level().getGameTime();
        boolean stretched = body.stretchUntil() > now;
        boolean bound = ModData.isBound(entity);
        boolean captor = Captives.isCaptor(entity.getUUID());
        int jester = outfit(entity, ModItems.JESTER_HAT, ModItems.JESTER_TUNIC, ModItems.JESTER_HOSE, ModItems.JESTER_SHOES);
        int peasant = outfit(entity, ModItems.PEASANT_HOOD, ModItems.PEASANT_TUNIC, ModItems.PEASANT_BREECHES, ModItems.PEASANT_WRAPS);
        Filth filth = ModData.filth(entity);
        float sinceGruel = com.punizioni.client.fx.Disgusts.since(entity.getId(), now, partial);
        int witch = outfit(entity, ModItems.WITCH_HAT, ModItems.WITCH_DRESS, ModItems.WITCH_STOCKINGS, ModItems.WITCH_BOOTS);
        Curse curse = ModData.curse(entity);
        if (!(entity.getVehicle() instanceof Machine) && body == Body.NONE && !hood && !apron && !breeches && !boots && !stretched && !crown && !cape
                && whipArm == null && !bound && !captor && jester == 0 && peasant == 0 && filth.clean()
                && sinceGruel > com.punizioni.client.fx.Disgusts.LENGTH && witch == 0 && curse == null) {
            return null;
        }
        Visuals v = new Visuals();
        v.witch = witch;
        if (curse != null && curse.type() != null) {
            UUID id = entity.getUUID();
            v.curse = curse.type();
            v.curseAge = now - curse.since() + partial;
            v.popAge = curse.popping() ? now - curse.popAt() + partial : -1.0F;
            int c = CurseClock.since(id, now, CurseClock.CROAK, 40);
            v.croak = c < 0 ? -1.0F : c + partial;
            int g = CurseClock.since(id, now, CurseClock.GRUNT, 40);
            v.grunt = g < 0 ? -1.0F : g + partial;
            int f = CurseClock.since(id, now, CurseClock.FART, 60);
            v.fart = f < 0 || now - curse.since() <= 60L ? -1.0F : f + partial;
            v.airborne = !entity.onGround() && !entity.isInWater();
            v.vy = (float) (entity.getY() - entity.yo);
            v.swing = entity.isSwinging() ? entity.getSwingAnimation(partial) : -1.0F;
            v.hurt = entity.hurtTime > 0 ? entity.hurtTime - partial : 0.0F;
            double dx = entity.getX() - entity.xo;
            double dz = entity.getZ() - entity.zo;
            float yaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
            v.forward = (float) (-Mth.sin(yaw) * dx + Mth.cos(yaw) * dz);
            v.side = (float) (Mth.cos(yaw) * dx + Mth.sin(yaw) * dz);
            float k = Mth.clamp(v.curseAge / 8.0F, 0.0F, 1.0F);
            v.turn = v.curse == CurseKind.BUTT ? k * k * (3.0F - 2.0F * k) : 0.0F;
        }
        v.sinceGruel = sinceGruel;
        v.jester = jester;
        v.peasant = peasant;
        v.filth = filth;
        v.sinceSplat = filth.lastHit() > 0L ? now - filth.lastHit() + partial : 1000.0F;
        v.crown = crown;
        v.cape = cape;
        v.bound = bound;
        v.captor = captor;
        v.whipArm = whipArm;
        v.gui = Royal.inGui;
        v.entityId = entity.getId();
        v.viewYaw = entity.getViewYRot(partial);
        v.viewPitch = entity.getViewXRot(partial);
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
                case Pillory p -> v.a = p.board(partial);
                case ChoppingBlock c -> v.choice = c.limb().ordinal();
                default -> {
                }
            }
        }
        if (v.hidesBody()) {
            // a toad, a balloon: none of the body's gear or marks shows (a balloon keeps what is on its head)
            boolean balloon = v.curse == CurseKind.BALLOON;
            v.body = Body.NONE;
            v.hood = v.hood && balloon;
            v.apron = false;
            v.breeches = false;
            v.boots = false;
            v.crown = v.crown && balloon;
            v.cape = false;
            v.stretch = 0.0F;
            v.whipArm = null;
            v.jester = balloon ? v.jester & 1 : 0;
            v.peasant = balloon ? v.peasant & 1 : 0;
            v.witch = balloon ? v.witch & 1 : 0;
        }
        return v;
    }

    /** A toad, or a balloon: the player's own body is not drawn (CurseLayer draws what it has become). */
    public boolean hidesBody() {
        return this.curse == CurseKind.TOAD || this.curse == CurseKind.BALLOON;
    }

    /** Which pieces of an outfit are worn: hat (1), tunic (2), legs (4), feet (8). */
    private static int outfit(Avatar entity, net.minecraft.world.item.Item head, net.minecraft.world.item.Item chest, net.minecraft.world.item.Item legs,
                              net.minecraft.world.item.Item feet) {
        int bits = 0;
        bits |= entity.getItemBySlot(EquipmentSlot.HEAD).is(head) ? 1 : 0;
        bits |= entity.getItemBySlot(EquipmentSlot.CHEST).is(chest) ? 2 : 0;
        bits |= entity.getItemBySlot(EquipmentSlot.LEGS).is(legs) ? 4 : 0;
        bits |= entity.getItemBySlot(EquipmentSlot.FEET).is(feet) ? 8 : 0;
        return bits;
    }

    /** Fills the render state of a player (or mannequin) with what the mod draws on them. */
    public static void extract(Avatar entity, AvatarRenderState state, float partial) {
        Visuals v = of(entity, partial);
        state.setData(KEY, v);
        if (Guilty.shows(entity)) {
            // seen through the king's lens: fresh misdeeds glow red, through walls
            state.outlineColor = Guilty.COLOR;
            if (state.nameTag != null) {
                state.nameTag = Component.literal("\u26a0 ").withStyle(ChatFormatting.RED).append(state.nameTag.copy().withStyle(ChatFormatting.RED));
            }
        }
        if (v == null) {
            return;
        }
        if (v.cape) {
            // the royal mantle replaces any cape
            state.showCape = false;
        }
        if (v.hidesBody()) {
            // nothing worn or held shows on a toad or a balloon (vanilla's armour, items, capes, parrots, arrows)
            boolean balloon = v.curse == CurseKind.BALLOON;
            if (!balloon) {
                state.headEquipment = ItemStack.EMPTY;
                state.headItem.clear();
                state.wornHeadType = null;
            }
            state.chestEquipment = ItemStack.EMPTY;
            state.legsEquipment = ItemStack.EMPTY;
            state.feetEquipment = ItemStack.EMPTY;
            state.showCape = false;
            state.parrotOnLeftShoulder = null;
            state.parrotOnRightShoulder = null;
            state.arrowCount = 0;
            state.stingerCount = 0;
            if (!balloon) {
                state.rightHandItemState.clear();
                state.rightHandItemStack = ItemStack.EMPTY;
                state.leftHandItemState.clear();
                state.leftHandItemStack = ItemStack.EMPTY;
                state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
                state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            }
        } else if (v.curse == CurseKind.BUTT) {
            // the head is down in the backside, facing the way the player looks: the body walks on backwards
            state.headEquipment = ItemStack.EMPTY;
            state.headItem.clear();
            state.wornHeadType = null;
            state.bodyRot += 180.0F * v.turn;
        }
        if (v.crown && state.nameTag != null && !Guilty.shows(entity)) {
            state.nameTag = Component.literal("\u2654 ").withStyle(ChatFormatting.GOLD).append(state.nameTag.copy().withStyle(ChatFormatting.GOLD));
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
        // a hand that is gone holds nothing; tied hands neither
        if (v.bound) {
            state.rightHandItemState.clear();
            state.rightHandItemStack = ItemStack.EMPTY;
            state.leftHandItemState.clear();
            state.leftHandItemStack = ItemStack.EMPTY;
            state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        }
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
        if (v.body.marked() || !v.filth.clean()) {
            state.skin = MarkedSkins.marked(state.skin, entity.getUUID(), v.body, v.filth, entity.level().getGameTime());
        }
        if (v.hood) {
            // the executioner has no name
            state.nameTag = null;
        }
    }
}

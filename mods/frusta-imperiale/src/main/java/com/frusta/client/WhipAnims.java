package com.frusta.client;

import com.frusta.client.fx.ScreenShake;
import com.frusta.client.fx.WhipFx;
import com.frusta.client.sound.SoundDucker;
import com.frusta.client.sound.TwirlSound;
import com.frusta.item.WhipItem;
import com.frusta.motion.ArmMath;
import com.frusta.motion.V3;
import com.frusta.motion.Whip;
import com.frusta.motion.WhipRope;
import com.frusta.motion.WhipShape;
import com.frusta.network.FrustaNetwork;
import com.frusta.network.LashPacket;
import com.frusta.network.MovePacket;
import com.frusta.registry.ModSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * All the whips this client knows about, one {@link WhipAnim} per player holding one. Starts the
 * moves (your own at once, the others' when the server says so), plays their sounds and effects on
 * the right tick, and brings every whip up to date before it is drawn.
 */
public final class WhipAnims {
    private static final Map<Integer, WhipAnim> ANIMS = new HashMap<>();
    private static final Random RANDOM = new Random();
    /** Client ticks, counted only while the game runs. */
    private static long ticks;
    /** Counts world renders, so anchors found while drawing a player are known to be fresh. */
    private static long frame;

    private WhipAnims() {
    }

    public static long ticks() {
        return ticks;
    }

    public static long frame() {
        return frame;
    }

    public static void newFrame() {
        frame++;
    }

    /** Client ticks plus how far into the next one we are. */
    public static double clock(float partialTick) {
        return ticks + partialTick;
    }

    @Nullable
    public static WhipAnim find(Entity entity) {
        return ANIMS.get(entity.getId());
    }

    private static WhipAnim get(Player player) {
        return ANIMS.computeIfAbsent(player.getId(), id -> new WhipAnim());
    }

    public static void clear() {
        ANIMS.clear();
        TwirlSound.stopAll();
    }

    /** The whip's handle end, found while drawing the player (third person) or its first-person arm. */
    public static void anchor(Player player, V3 hand, V3 handleDir) {
        WhipAnim anim = get(player);
        anim.hand = hand;
        anim.handleDir = handleDir;
        anim.handFrame = frame;
    }

    // =====================================================================================
    // Starting moves
    // =====================================================================================

    /** The attack key with a whip in hand: lash at once, and tell the server. */
    public static void lashLocal() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int style = RANDOM.nextInt(WhipShape.STYLES);
        startLocal(player, WhipShape.Move.LASH, style, 0.0F, 0);
        FrustaNetwork.sendToServer(new LashPacket((byte) style));
    }

    /** Your own move, shown at once (the server does the same checks and the hitting). */
    static void startLocal(LocalPlayer player, WhipShape.Move move, int style, float charge, double spunFor) {
        ItemStack whip = WhipItem.held(player);
        if (whip.isEmpty()) {
            return;
        }
        double length = ((WhipItem) whip.getItem()).getMaterial().length();
        Vec3 look = player.getLookAngle();
        V3 aim = new V3(look.x, look.y, look.z);
        Entity hitEntity = null;
        double reach = length;
        boolean contact = false;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(look.scale(length));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            reach = block.getLocation().distanceTo(eye);
            contact = true;
        }
        if (move != WhipShape.Move.IMPERIAL) {
            Vec3 to = eye.add(look.scale(reach));
            for (Entity e : player.level().getEntities(player, new AABB(eye, to).inflate(1.0),
                    e -> e.isPickable() && e.isAlive() && !e.isSpectator() && (e instanceof LivingEntity || e instanceof net.minecraftforge.entity.PartEntity<?>))) {
                if (e == player.getVehicle()) {
                    continue;
                }
                Optional<Vec3> clip = e.getBoundingBox().inflate(0.35 + e.getPickRadius()).clip(eye, to);
                if (clip.isPresent() && clip.get().distanceTo(eye) < reach) {
                    reach = clip.get().distanceTo(eye);
                    hitEntity = e;
                    contact = true;
                }
            }
        }
        int target = move == WhipShape.Move.GRAB && hitEntity instanceof LivingEntity ? hitEntity.getId() : -1;
        WhipShape.Strike strike = new WhipShape.Strike(move, style, length, aim, reach, contact);
        start(player, strike, target, charge, spunFor);
    }

    /** Someone else's move, as the server tells it. */
    public static void onMove(MovePacket p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(p.player()) instanceof Player player) || player == Minecraft.getInstance().player) {
            return;
        }
        ItemStack whip = WhipItem.held(player);
        double length = whip.isEmpty() ? 5.0 : ((WhipItem) whip.getItem()).getMaterial().length();
        WhipShape.Move move = WhipShape.Move.values()[Mth.clamp(p.move(), 0, WhipShape.Move.values().length - 1)];
        V3 aim = new V3(p.aimX(), p.aimY(), p.aimZ()).normalize(new V3(0, 0, 1));
        WhipShape.Strike strike = new WhipShape.Strike(move, p.style(), length, aim, p.reach(), p.contact());
        WhipAnim anim = get(player);
        double spunFor = anim.twirling ? ticks - anim.twirlStart : p.charge() * Whip.TWIRL_FULL;
        start(player, strike, p.target(), p.charge(), spunFor);
    }

    private static void start(Player player, WhipShape.Strike strike, int target, float charge, double spunFor) {
        WhipAnim anim = get(player);
        TwirlSound.stop(player);
        anim.startMove(strike, ticks, target, charge, spunFor);
        WhipItem item = WhipItem.held(player).getItem() instanceof WhipItem w ? w : null;
        float pitch = item != null ? item.getMaterial().pitch() : 1.0F;
        Vec3 at = player.getEyePosition();
        float volume = strike.move() == WhipShape.Move.IMPERIAL ? 1.6F : 1.0F;
        play(ModSounds.WHOOSH.get(), at.x, at.y, at.z, volume, pitch * (0.9F + RANDOM.nextFloat() * 0.2F));
    }

    // =====================================================================================
    // Every tick: twirls, cracks, sounds
    // =====================================================================================

    public static void tick(ClientLevel level) {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        Iterator<Map.Entry<Integer, WhipAnim>> it = ANIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, WhipAnim> entry = it.next();
            WhipAnim anim = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player player) || !player.isAlive() || WhipItem.held(player).isEmpty()) {
                if (entity instanceof Player p) {
                    TwirlSound.stop(p);
                }
                it.remove();
                continue;
            }
            ItemStack whip = WhipItem.held(player);
            anim.material = ((WhipItem) whip.getItem()).getMaterial();
            anim.foil = whip.hasFoil();

            boolean spinning = player.isUsingItem() && player.getUseItem().getItem() instanceof WhipItem;
            if (spinning && !anim.twirling && (anim.strike == null || anim.released)) {
                anim.startTwirl(ticks - player.getTicksUsingItem());
                TwirlSound.start(player);
            } else if (!spinning && anim.twirling && anim.hand != null) {
                anim.stopTwirl(ticks, anim.hand);
                TwirlSound.stop(player);
            }

            if (anim.strike != null && ticks - anim.moveStart > endOf(anim.strike.move())) {
                anim.strike = null;
            }
            if (anim.strike != null && anim.hand != null && anim.eye != null) {
                double t = ticks - anim.moveStart;
                WhipShape.Move move = anim.strike.move();
                if (move != WhipShape.Move.GRAB && !anim.cracked && t >= WhipShape.crackAt(move)) {
                    anim.cracked = true;
                    crack(player, anim, WhipShape.crackPoint(anim.strike, anim.hand, anim.eye));
                }
                if (move == WhipShape.Move.GRAB && !anim.reached && t >= Whip.GRAB_REACH) {
                    anim.reached = true;
                    Entity target = level.getEntity(anim.target);
                    Vec3 at = target != null ? target.getBoundingBox().getCenter()
                            : toVec(WhipShape.crackPoint(anim.strike, anim.hand, anim.eye));
                    play(ModSounds.WRAP.get(), at.x, at.y, at.z, 1.3F, anim.material.pitch());
                }
            }
        }
        // Players who just took a whip in hand get one too.
        for (Player player : level.players()) {
            if (!WhipItem.held(player).isEmpty()) {
                get(player);
            }
        }
        WhipFx.tick();
        SoundDucker.tick(mc);
        ScreenShake.tick();
    }

    /** Ticks after the start when the arm is back at rest and the move is forgotten. */
    private static int endOf(WhipShape.Move move) {
        return switch (move) {
            case LASH -> Whip.LASH_END;
            case IMPERIAL -> Whip.IMPERIAL_END;
            case GRAB -> Whip.GRAB_END;
        };
    }

    /** The crack: a sound over everything, a shockwave, sparks, and a shake for whoever is close. */
    private static void crack(Player player, WhipAnim anim, V3 point) {
        Minecraft mc = Minecraft.getInstance();
        boolean imperial = anim.strike.move() == WhipShape.Move.IMPERIAL;
        SoundEvent sound = imperial ? ModSounds.CRACK_IMPERIAL.get() : ModSounds.CRACK.get();
        // Volume above 1 only makes it carry further: 4 = 64 blocks, 8 = 128.
        float volume = imperial ? 8.0F : 4.0F;
        play(sound, point.x(), point.y(), point.z(), volume, anim.material.pitch() * (0.95F + RANDOM.nextFloat() * 0.1F));
        WhipFx.crack(point, anim, imperial);
        if (mc.player != null) {
            double distance = mc.player.getEyePosition().distanceTo(toVec(point));
            double reach = imperial ? 40 : 24;
            if (distance < reach) {
                double k = 1 - distance / reach;
                SoundDucker.duck(k, imperial ? 14 : 7, imperial ? 40 : 22);
                ScreenShake.add((float) ((imperial ? 2.6 : 1.0) * k * k), imperial ? 16 : 7);
            }
        }
    }

    private static void play(SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.PLAYERS, volume, pitch,
                    net.minecraft.util.RandomSource.create(RANDOM.nextLong()), x, y, z));
        }
    }

    // =====================================================================================
    // Every frame: bring every whip up to date (drawing is done by WhipRenderer)
    // =====================================================================================

    public static Iterable<Map.Entry<Integer, WhipAnim>> all() {
        return ANIMS.entrySet();
    }

    /** Updates every whip for this frame; returns the ones to draw. */
    public static Iterable<Map.Entry<Integer, WhipAnim>> update(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return Map.<Integer, WhipAnim>of().entrySet();
        }
        double clock = clock(partialTick);
        WhipRope.Ground ground = (x, y, z) -> floor(level, x, y, z);
        for (Map.Entry<Integer, WhipAnim> entry : ANIMS.entrySet()) {
            WhipAnim anim = entry.getValue();
            if (!(level.getEntity(entry.getKey()) instanceof Player player) || WhipItem.held(player).isEmpty()) {
                anim.hasNodes = false;
                continue;
            }
            if (anim.handFrame != frame || anim.hand == null) {
                guessHand(player, partialTick, anim);
            }
            Vec3 eye = player.getEyePosition(partialTick);
            anim.eye = new V3(eye.x, eye.y, eye.z);
            float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            V3 back = V3.forward(bodyYaw).scale(-1);
            WhipShape.Target target = null;
            if (anim.strike != null && anim.target >= 0 && level.getEntity(anim.target) instanceof LivingEntity t) {
                Vec3 c = t.getPosition(partialTick);
                target = new WhipShape.Target(new V3(c.x, c.y + t.getBbHeight() * 0.55, c.z), t.getBbWidth() * 0.5, t.getBbHeight());
            }
            double floorY = floor(level, anim.hand.x(), anim.hand.y(), anim.hand.z());
            if (Double.isNaN(floorY)) {
                floorY = player.getY();
            }
            anim.update(clock, anim.hand, anim.handleDir, anim.eye, ground, back, floorY, target);
            anim.recordTrail(clock);
        }
        return ANIMS.entrySet();
    }

    /** A player not drawn this frame (out of view): where the hand should be, from the arm maths. */
    private static void guessHand(Player player, float partialTick, WhipAnim anim) {
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float[] a = WhipPoses.angles(player, partialTick, WhipPoses.vanillaRest(0.0F), false);
        double side = right ? 1 : -1;
        V3 pivot = new V3(side * ArmMath.SHOULDER_X, player.isCrouching() ? ArmMath.SHOULDER_Y_CROUCHING : ArmMath.SHOULDER_Y, 0);
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        Vec3 feet = player.getPosition(partialTick);
        V3 base = new V3(feet.x, feet.y + (player.isCrouching() ? -0.125 : 0), feet.z);
        double x = a[0];
        double y = right ? a[1] : -a[1];
        double z = right ? a[2] : -a[2];
        V3 tip = base.add(ArmMath.armPoint(pivot, x, y, z, new V3(-side, 10, -9), bodyYaw, ArmMath.PLAYER_SCALE));
        V3 grip = base.add(ArmMath.armPoint(pivot, x, y, z, new V3(-side, 10, -2), bodyYaw, ArmMath.PLAYER_SCALE));
        anim.hand = tip;
        anim.handleDir = tip.sub(grip).normalize(new V3(0, -1, 0));
    }

    /** Top of the solid ground at or just under a point, NaN if there is none (air, or deep inside a wall). */
    private static double floor(ClientLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x);
        int by = Mth.floor(y);
        int bz = Mth.floor(z);
        for (int dy = 0; dy >= -1; dy--) {
            pos.set(bx, by + dy, bz);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double top = pos.getY() + shape.max(Direction.Axis.Y);
            return top <= y + 0.35 ? top : Double.NaN;
        }
        return Double.NaN;
    }

    static Vec3 toVec(V3 v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}

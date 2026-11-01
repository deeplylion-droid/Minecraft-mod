package com.punizioni.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the punishments left on a body, until death: missing hands and feet, bones broken on the
 * wheel, lash marks on the back, rat bites, burns, a stretch that has not settled yet. Saved (a relog
 * does not heal anything), not copied on death (dying is the only cure), known by every client (they
 * draw it).
 *
 * @param missing      bits of the missing {@link Limb}s
 * @param broken       bits of the arms and legs broken on the wheel ({@link #RIGHT_ARM}...)
 * @param lashes       lash marks on the back (their places come from the player's UUID)
 * @param bites        rat bites all over the body
 * @param burn         0 = untouched, 1 = charred black
 * @param lastLash     game time of the last lash (fresh marks bleed)
 * @param stretchUntil game time until which the body is still stretched by the horses
 * @param stretch      how far the horses had stretched it (0 = not at all, 1 = as far as it goes)
 * @param flags        {@link #TORN} (arms and legs torn off), {@link #HEADLESS}: only while dying
 */
public record Body(int missing, int broken, int lashes, int bites, float burn, long lastLash, long stretchUntil, float stretch, int flags) {
    public static final Body NONE = new Body(0, 0, 0, 0, 0.0F, 0L, 0L, 0.0F, 0);
    public static final int MAX_LASHES = 40;
    public static final int MAX_BITES = 60;
    /** How long the body stays stretched after the horses stop (1 minute). */
    public static final int STRETCH_TICKS = 20 * 60;

    public static final int RIGHT_ARM = 1;
    public static final int LEFT_ARM = 2;
    public static final int RIGHT_LEG = 4;
    public static final int LEFT_LEG = 8;
    public static final int ALL_BONES = 15;

    public static final int TORN = 1;
    public static final int HEADLESS = 2;

    public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("missing", 0).forGetter(Body::missing),
            Codec.INT.optionalFieldOf("broken", 0).forGetter(Body::broken),
            Codec.INT.optionalFieldOf("lashes", 0).forGetter(Body::lashes),
            Codec.INT.optionalFieldOf("bites", 0).forGetter(Body::bites),
            Codec.FLOAT.optionalFieldOf("burn", 0.0F).forGetter(Body::burn),
            Codec.LONG.optionalFieldOf("last_lash", 0L).forGetter(Body::lastLash),
            Codec.LONG.optionalFieldOf("stretch_until", 0L).forGetter(Body::stretchUntil),
            Codec.FLOAT.optionalFieldOf("stretch", 0.0F).forGetter(Body::stretch),
            Codec.INT.optionalFieldOf("flags", 0).forGetter(Body::flags)
    ).apply(i, Body::new));

    public static final StreamCodec<ByteBuf, Body> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Body::missing,
            ByteBufCodecs.VAR_INT, Body::broken,
            ByteBufCodecs.VAR_INT, Body::lashes,
            ByteBufCodecs.VAR_INT, Body::bites,
            ByteBufCodecs.FLOAT, Body::burn,
            ByteBufCodecs.VAR_LONG, Body::lastLash,
            ByteBufCodecs.VAR_LONG, Body::stretchUntil,
            ByteBufCodecs.FLOAT, Body::stretch,
            ByteBufCodecs.VAR_INT, Body::flags,
            Body::new);

    public boolean lacks(Limb limb) {
        return (this.missing & limb.bit()) != 0;
    }

    public boolean brokenBone(int bone) {
        return (this.broken & bone) != 0;
    }

    public boolean torn() {
        return (this.flags & TORN) != 0;
    }

    public boolean headless() {
        return (this.flags & HEADLESS) != 0;
    }

    public Body without(Limb limb) {
        return new Body(this.missing | limb.bit(), this.broken, this.lashes, this.bites, this.burn, this.lastLash, this.stretchUntil, this.stretch,
                this.flags);
    }

    public Body withBroken(int bone) {
        return new Body(this.missing, this.broken | bone, this.lashes, this.bites, this.burn, this.lastLash, this.stretchUntil, this.stretch,
                this.flags);
    }

    public Body lashed(long now) {
        return new Body(this.missing, this.broken, Math.min(MAX_LASHES, this.lashes + 1), this.bites, this.burn, now, this.stretchUntil,
                this.stretch, this.flags);
    }

    public Body bitten(int n) {
        return new Body(this.missing, this.broken, this.lashes, Math.min(MAX_BITES, this.bites + n), this.burn, this.lastLash, this.stretchUntil,
                this.stretch, this.flags);
    }

    public Body burnt(float b) {
        return new Body(this.missing, this.broken, this.lashes, this.bites, Math.max(this.burn, Math.min(1.0F, b)), this.lastLash,
                this.stretchUntil, this.stretch, this.flags);
    }

    public Body stretched(long until, float amount) {
        return new Body(this.missing, this.broken, this.lashes, this.bites, this.burn, this.lastLash, until, amount, this.flags);
    }

    public Body withFlag(int flag) {
        return new Body(this.missing, this.broken, this.lashes, this.bites, this.burn, this.lastLash, 0L, 0.0F, this.flags | flag);
    }

    public boolean marked() {
        return this.lashes > 0 || this.bites > 0 || this.burn > 0.0F;
    }

    public int feetMissing() {
        return (this.lacks(Limb.RIGHT_FOOT) ? 1 : 0) + (this.lacks(Limb.LEFT_FOOT) ? 1 : 0);
    }

    public int legsBroken() {
        return (this.brokenBone(RIGHT_LEG) ? 1 : 0) + (this.brokenBone(LEFT_LEG) ? 1 : 0);
    }
}

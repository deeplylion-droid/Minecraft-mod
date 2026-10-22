package com.filorosso.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Powers of the bond working on a player right now, known by every client (not saved): they draw
 * the trail of the slingshot, the wings of "Eterni", the heart of a kiss waiting for an answer and
 * the strings of the Puppeteer.
 *
 * @param slingUntil   flying from the slingshot until then (no fall damage, a crash on landing)
 * @param eternalUntil "Eterni": flight, strength and wings until then
 * @param kissUntil    a kiss waiting for the partner's answer until then
 * @param puppetUntil  a puppet of the Burattinaio until then
 * @param puppeteer    entity id of the Burattinaio pulling the strings (-1: none)
 * @param puppetTarget entity id the puppet is walked towards (-1: wherever it wants)
 */
public record Powers(long slingUntil, long eternalUntil, long kissUntil, long puppetUntil, int puppeteer, int puppetTarget) {
    public static final Powers NONE = new Powers(0L, 0L, 0L, 0L, -1, -1);

    public static final StreamCodec<ByteBuf, Powers> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Powers::slingUntil,
            ByteBufCodecs.VAR_LONG, Powers::eternalUntil,
            ByteBufCodecs.VAR_LONG, Powers::kissUntil,
            ByteBufCodecs.VAR_LONG, Powers::puppetUntil,
            ByteBufCodecs.VAR_INT, Powers::puppeteer,
            ByteBufCodecs.VAR_INT, Powers::puppetTarget,
            Powers::new);

    public boolean slinging(long now) {
        return now < this.slingUntil;
    }

    public boolean eternal(long now) {
        return now < this.eternalUntil;
    }

    public boolean kissing(long now) {
        return now < this.kissUntil;
    }

    public boolean puppet(long now) {
        return now < this.puppetUntil;
    }

    public Powers withSling(long until) {
        return new Powers(until, this.eternalUntil, this.kissUntil, this.puppetUntil, this.puppeteer, this.puppetTarget);
    }

    public Powers withEternal(long until) {
        return new Powers(this.slingUntil, until, this.kissUntil, this.puppetUntil, this.puppeteer, this.puppetTarget);
    }

    public Powers withKiss(long until) {
        return new Powers(this.slingUntil, this.eternalUntil, until, this.puppetUntil, this.puppeteer, this.puppetTarget);
    }

    public Powers withPuppet(long until, int puppeteer, int target) {
        return new Powers(this.slingUntil, this.eternalUntil, this.kissUntil, until, puppeteer, target);
    }

    public boolean idle(long now) {
        return !this.slinging(now) && !this.eternal(now) && !this.kissing(now) && !this.puppet(now);
    }
}

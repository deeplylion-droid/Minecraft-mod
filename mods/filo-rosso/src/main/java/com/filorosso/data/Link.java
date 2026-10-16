package com.filorosso.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * What every client needs to know about a player's bond (to draw the thread and run its physics):
 * a copy of their couple, kept on both partners. The couple book on the overworld is the truth.
 *
 * @param color     the couple's colour (index into {@link Palette})
 * @param xp        affinity
 * @param type      {@link BondType} ordinal for this session
 * @param cutUntil  game time until which the thread is cut (Spezzacuori, his servants)
 * @param bondedAt  game time of the bond (the thread grows from one pinky to the other)
 */
public record Link(Optional<UUID> partner, String partnerName, int color, int xp, int type, long cutUntil, long bondedAt) {
    public static final Link NONE = new Link(Optional.empty(), "", 0, 0, 0, 0L, 0L);

    public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("partner").forGetter(Link::partner),
            Codec.STRING.optionalFieldOf("partner_name", "").forGetter(Link::partnerName),
            Codec.INT.optionalFieldOf("color", 0).forGetter(Link::color),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(Link::xp),
            Codec.INT.optionalFieldOf("type", 0).forGetter(Link::type),
            Codec.LONG.optionalFieldOf("cut_until", 0L).forGetter(Link::cutUntil),
            Codec.LONG.optionalFieldOf("bonded_at", 0L).forGetter(Link::bondedAt)
    ).apply(i, Link::new));

    public static final StreamCodec<ByteBuf, Link> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public Link decode(ByteBuf buf) {
            Optional<UUID> partner = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf);
            String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            int color = ByteBufCodecs.VAR_INT.decode(buf);
            int xp = ByteBufCodecs.VAR_INT.decode(buf);
            int type = ByteBufCodecs.VAR_INT.decode(buf);
            long cut = ByteBufCodecs.VAR_LONG.decode(buf);
            long bonded = ByteBufCodecs.VAR_LONG.decode(buf);
            return new Link(partner, name, color, xp, type, cut, bonded);
        }

        @Override
        public void encode(ByteBuf buf, Link link) {
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buf, link.partner);
            ByteBufCodecs.STRING_UTF8.encode(buf, link.partnerName);
            ByteBufCodecs.VAR_INT.encode(buf, link.color);
            ByteBufCodecs.VAR_INT.encode(buf, link.xp);
            ByteBufCodecs.VAR_INT.encode(buf, link.type);
            ByteBufCodecs.VAR_LONG.encode(buf, link.cutUntil);
            ByteBufCodecs.VAR_LONG.encode(buf, link.bondedAt);
        }
    };

    public boolean bound() {
        return this.partner.isPresent();
    }

    public BondLevel level() {
        return BondLevel.forXp(this.xp);
    }

    public BondType bondType() {
        return BondType.byOrdinal(this.type);
    }

    public boolean cut(long now) {
        return now < this.cutUntil;
    }

    public boolean isPartner(UUID id) {
        return this.partner.isPresent() && this.partner.get().equals(id);
    }
}

package com.filorosso.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Where a player was before going up into the Theatre: they go back there. */
public record Home(String dimension, double x, double y, double z, float yaw) {
    public static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("dimension").forGetter(Home::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(Home::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Home::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Home::z),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(Home::yaw)
    ).apply(i, Home::new));
}

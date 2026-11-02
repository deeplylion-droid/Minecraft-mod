package com.punizioni.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** A twinkle on a gem of the crown: a little four-pointed star that flares and fades in place. */
public class SparkleParticle extends SingleQuadParticle {
    private final float peak;

    SparkleParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.xd = 0.0;
        this.yd = 0.002;
        this.zd = 0.0;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.lifetime = 10 + this.random.nextInt(8);
        this.peak = 0.05F + this.random.nextFloat() * 0.05F;
        this.quadSize = 0.0F;
        this.hasPhysics = false;
        this.setColor(1.0F, 0.93F, 0.6F);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public float getQuadSize(float partial) {
        float t = (this.age + partial) / this.lifetime;
        return this.peak * Mth.sin(Mth.clamp(t, 0.0F, 1.0F) * Mth.PI);
    }

    @Override
    protected int getLightCoords(float partial) {
        return LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                                       RandomSource random) {
            return new SparkleParticle(level, x, y, z, this.sprites);
        }
    }
}

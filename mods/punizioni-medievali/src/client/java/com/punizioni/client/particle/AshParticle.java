package com.punizioni.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** A flake of grey ash: lifted by the heat, then drifting down slowly, turning as it falls. */
public class AshParticle extends SingleQuadParticle {
    private final float spin;

    AshParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.xd = xd + (this.random.nextFloat() - 0.5F) * 0.03F;
        this.yd = yd + 0.02F + this.random.nextFloat() * 0.03F;
        this.zd = zd + (this.random.nextFloat() - 0.5F) * 0.03F;
        this.gravity = 0.05F;
        this.friction = 0.96F;
        this.lifetime = 80 + this.random.nextInt(80);
        this.quadSize = 0.04F + this.random.nextFloat() * 0.05F;
        float g = 0.25F + this.random.nextFloat() * 0.3F;
        this.setColor(g, g * 0.97F, g * 0.94F);
        this.spin = (this.random.nextFloat() - 0.5F) * 0.2F;
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += this.spin;
        this.xd += Mth.sin(this.age * 0.1F + this.spin * 30.0F) * 0.002F;
        float t = (float) this.age / this.lifetime;
        this.setAlpha(t < 0.8F ? 0.9F : 0.9F * (1.0F - (t - 0.8F) / 0.2F));
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
            return new AshParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}

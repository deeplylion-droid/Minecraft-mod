package com.punizioni.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Where the whip cracks: a quick white burst (the snapped air), then puffs of dust that spread and fade. */
public class CrackParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final float start;

    CrackParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = -0.01F;
        this.friction = 0.82F;
        this.lifetime = 9 + this.random.nextInt(7);
        this.start = 0.07F + this.random.nextFloat() * 0.08F;
        this.quadSize = this.start;
        this.hasPhysics = false;
        float grey = 0.85F + this.random.nextFloat() * 0.15F;
        this.setColor(grey, grey * 0.97F, grey * 0.9F);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        float t = (float) this.age / this.lifetime;
        this.quadSize = this.start * (1.0F + t * 1.6F);
        this.setAlpha(Mth.clamp(1.0F - t, 0.0F, 1.0F) * 0.8F);
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
            return new CrackParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}

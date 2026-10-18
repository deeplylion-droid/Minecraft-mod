package com.chiappe.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** A puff of green-brown gas: shot out fast, it slows down, swells, rises a little and fades. */
public class GasParticle extends SingleQuadParticle {
    private final float startAlpha;

    GasParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.friction = 0.88F;
        this.gravity = -0.03F;
        this.lifetime = 35 + this.random.nextInt(30);
        this.quadSize = 0.35F + this.random.nextFloat() * 0.25F;
        float green = this.random.nextFloat();
        this.setColor(Mth.lerp(green, 0.55F, 0.42F), Mth.lerp(green, 0.45F, 0.55F), Mth.lerp(green, 0.20F, 0.16F));
        this.startAlpha = 0.7F + this.random.nextFloat() * 0.2F;
        this.setAlpha(this.startAlpha);
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
    }

    /** Bigger puffs for bigger cheeks: they last longer, too. */
    void grow(float size) {
        this.quadSize *= size;
        this.lifetime = (int) (this.lifetime * Mth.clamp(0.8F + size * 0.25F, 1.0F, 2.5F));
    }

    @Override
    public float getQuadSize(float partial) {
        float t = Mth.clamp((this.age + partial) / this.lifetime, 0.0F, 1.0F);
        // swells fast, then slowly
        return this.quadSize * (1.0F + 1.6F * (1.0F - (1.0F - t) * (1.0F - t)));
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += 0.02F * (this.random.nextFloat() - 0.5F) + (float) (this.xd + this.zd) * 0.05F;
        float t = (float) this.age / this.lifetime;
        this.setAlpha(this.startAlpha * (t < 0.6F ? 1.0F : 1.0F - (t - 0.6F) / 0.4F));
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
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new GasParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}

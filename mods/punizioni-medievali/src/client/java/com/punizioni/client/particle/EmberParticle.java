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

/** A glowing ember: rises in the heat, wanders, cools from yellow to dark red and goes out. */
public class EmberParticle extends SingleQuadParticle {
    private final float wobble;

    EmberParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.xd = xd + (this.random.nextFloat() - 0.5F) * 0.04F;
        this.yd = yd + 0.04F + this.random.nextFloat() * 0.05F;
        this.zd = zd + (this.random.nextFloat() - 0.5F) * 0.04F;
        this.gravity = -0.25F;
        this.friction = 0.95F;
        this.lifetime = 30 + this.random.nextInt(40);
        this.quadSize = 0.03F + this.random.nextFloat() * 0.04F;
        this.wobble = this.random.nextFloat() * 10.0F;
        this.hasPhysics = false;
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) this.age / this.lifetime;
        this.xd += Mth.sin(this.age * 0.3F + this.wobble) * 0.004F;
        this.zd += Mth.cos(this.age * 0.27F + this.wobble) * 0.004F;
        this.setColor(1.0F, Mth.lerp(t, 0.85F, 0.18F), Mth.lerp(t, 0.35F, 0.02F));
        this.setAlpha(t < 0.75F ? 1.0F : 1.0F - (t - 0.75F) / 0.25F);
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
            return new EmberParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}

package com.infezione.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A small cloud coughed out: it goes forward, slows down, spreads a little and fades (it does not rise like smoke). */
public class CoughParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float startSize;

    protected CoughParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                            SpriteSet sprites, float r, float g, float b) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.0F;
        this.friction = 0.82F;
        this.hasPhysics = false;
        this.lifetime = 14 + this.random.nextInt(10);
        this.startSize = 0.07F + this.random.nextFloat() * 0.05F;
        this.quadSize = this.startSize;
        float shade = 0.85F + this.random.nextFloat() * 0.15F;
        this.setColor(r * shade, g * shade, b * shade);
        this.alpha = 0.75F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            float t = (float) this.age / this.lifetime;
            this.quadSize = this.startSize * (1.0F + 1.5F * t);
            this.alpha = 0.75F * (1.0F - t);
            this.setSpriteFromAge(this.sprites);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Pale grey: a dry cough. */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new CoughParticle(level, x, y, z, xd, yd, zd, this.sprites, 0.86F, 0.86F, 0.84F);
        }
    }

    /** Pink with blood. */
    public static class BloodyProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public BloodyProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new CoughParticle(level, x, y, z, xd, yd, zd, this.sprites, 0.75F, 0.32F, 0.32F);
        }
    }
}

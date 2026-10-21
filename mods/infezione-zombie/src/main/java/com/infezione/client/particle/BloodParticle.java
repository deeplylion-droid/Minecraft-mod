package com.infezione.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A drop of blood: it falls, lies on the ground for a moment, and fades. */
public class BloodParticle extends TextureSheetParticle {
    protected BloodParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.9F;
        this.friction = 0.96F;
        this.hasPhysics = true;
        this.lifetime = 40 + this.random.nextInt(30);
        this.quadSize = 0.045F + this.random.nextFloat() * 0.035F;
        float dark = 0.75F + this.random.nextFloat() * 0.25F;
        this.setColor(dark, dark, dark);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.onGround) {
            this.xd = 0.0;
            this.zd = 0.0;
        }
        int left = this.lifetime - this.age;
        if (left < 10) {
            this.alpha = Math.max(0.0F, left / 10.0F);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new BloodParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}

package com.infezione.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A fly: it darts around where it was born, wings flickering, then goes. */
public class FlyParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final double homeX;
    private final double homeY;
    private final double homeZ;

    protected FlyParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.lifetime = 40 + this.random.nextInt(50);
        this.quadSize = 0.03F + this.random.nextFloat() * 0.015F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = (this.random.nextDouble() - 0.5) * 0.1;
        this.yd = (this.random.nextDouble() - 0.5) * 0.05;
        this.zd = (this.random.nextDouble() - 0.5) * 0.1;
        this.setSprite(sprites.get(0, 1));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        // darting about, pulled back towards home
        this.xd += (this.random.nextDouble() - 0.5) * 0.06 + (this.homeX - this.x) * 0.02;
        this.yd += (this.random.nextDouble() - 0.5) * 0.04 + (this.homeY - this.y) * 0.02;
        this.zd += (this.random.nextDouble() - 0.5) * 0.06 + (this.homeZ - this.z) * 0.02;
        this.xd *= 0.82;
        this.yd *= 0.82;
        this.zd *= 0.82;
        this.move(this.xd, this.yd, this.zd);
        this.setSprite(this.sprites.get(this.age % 2, 1));
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
            return new FlyParticle(level, x, y, z, this.sprites);
        }
    }
}

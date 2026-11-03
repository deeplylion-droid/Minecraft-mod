package com.punizioni.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A fly round someone covered in dung: it circles a point near them in quick, jerky loops, the wings
 * flicker (two sprites), and after a few seconds it is gone.
 */
public class FlyParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final double cx, cy, cz;
    private final float radius;
    private final float speed;
    private float angle;

    FlyParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        this.sprites = sprites;
        this.cx = x;
        this.cy = y;
        this.cz = z;
        this.radius = 0.12F + this.random.nextFloat() * 0.25F;
        this.speed = (0.35F + this.random.nextFloat() * 0.3F) * (this.random.nextBoolean() ? 1.0F : -1.0F);
        this.angle = this.random.nextFloat() * Mth.TWO_PI;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.lifetime = 40 + this.random.nextInt(50);
        this.quadSize = 0.065F + this.random.nextFloat() * 0.02F;
        this.setColor(1.0F, 1.0F, 1.0F);
        this.xd = this.yd = this.zd = 0.0;
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
        // loops round the point, with sudden darts
        this.angle += this.speed + (this.random.nextFloat() - 0.5F) * 0.5F;
        float r = this.radius * (0.8F + 0.4F * Mth.sin(this.age * 0.7F));
        double x = this.cx + Mth.cos(this.angle) * r + (this.random.nextFloat() - 0.5F) * 0.05F;
        double y = this.cy + Mth.sin(this.angle * 1.7F) * r * 0.45F + (this.random.nextFloat() - 0.5F) * 0.04F;
        double z = this.cz + Mth.sin(this.angle) * r + (this.random.nextFloat() - 0.5F) * 0.05F;
        this.setPos(x, y, z);
        // never right in front of someone's eyes: a fly that close goes away
        if (Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(x, y, z) < 0.7 * 0.7) {
            this.remove();
            return;
        }
        this.setSprite(this.sprites.get(this.age % 2, 1));
        float fade = Math.min(1.0F, Math.min(this.age, this.lifetime - this.age) / 5.0F);
        this.setAlpha(fade);
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
            return new FlyParticle(level, x, y, z, this.sprites);
        }
    }
}

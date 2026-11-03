package com.punizioni.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

/** A lump of dung off a splat: heavy, it falls and stays a while as a flat brown smear where it lands. */
public class DungParticle extends SingleQuadParticle {
    private static final FacingCameraMode FLAT = (target, camera, partial) -> target.set(new Quaternionf().rotationX(-Mth.HALF_PI));

    private final SpriteSet sprites;
    private boolean smear;
    private int smearAge;
    private final float smearSize;

    DungParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 1.2F;
        this.friction = 0.97F;
        this.lifetime = 50 + this.random.nextInt(30);
        this.quadSize = 0.06F + this.random.nextFloat() * 0.07F;
        this.smearSize = 0.14F + this.random.nextFloat() * 0.2F;
        float k = 0.8F + this.random.nextFloat() * 0.2F;
        this.setColor(k, k, k);
        this.setSize(0.06F, 0.06F);
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return this.smear ? FLAT : FacingCameraMode.LOOKAT_XYZ;
    }

    @Override
    public void tick() {
        if (this.smear) {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (++this.smearAge > 200) {
                this.remove();
            }
            this.setAlpha(this.smearAge < 150 ? 0.95F : 0.95F * (1.0F - (this.smearAge - 150) / 50.0F));
            return;
        }
        super.tick();
        if (this.onGround && !this.removed) {
            this.smear = true;
            this.y += 0.012;
            this.yo = this.y;
            this.quadSize = this.smearSize;
            this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
            this.setSprite(this.sprites.get(this.random));
            this.setColor(this.rCol * 0.85F, this.gCol * 0.85F, this.bCol * 0.85F);
        }
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
            return new DungParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}

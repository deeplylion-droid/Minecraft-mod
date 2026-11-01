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

/**
 * A drop of blood: thrown out (or just falling), heavy, dark red; where it lands it flattens into a
 * stain on the ground that stays a few seconds and fades.
 */
public class BloodParticle extends SingleQuadParticle {
    private static final FacingCameraMode FLAT = (target, camera, partial) -> target.set(new Quaternionf().rotationX(-Mth.HALF_PI));

    private final SpriteSet sprites;
    private boolean stain;
    private int stainAge;
    private final float stainSize;

    BloodParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 1.1F;
        this.friction = 0.985F;
        this.lifetime = 60 + this.random.nextInt(40);
        this.quadSize = 0.05F + this.random.nextFloat() * 0.06F;
        this.stainSize = 0.12F + this.random.nextFloat() * 0.18F;
        float dark = this.random.nextFloat() * 0.25F;
        this.setColor(0.55F - dark, 0.02F, 0.03F);
        this.setSize(0.05F, 0.05F);
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return this.stain ? FLAT : FacingCameraMode.LOOKAT_XYZ;
    }

    @Override
    public void tick() {
        if (this.stain) {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (++this.stainAge > 120) {
                this.remove();
            }
            this.setAlpha(this.stainAge < 80 ? 0.9F : 0.9F * (1.0F - (this.stainAge - 80) / 40.0F));
            return;
        }
        super.tick();
        if (this.onGround && !this.removed) {
            // splat
            this.stain = true;
            this.y += 0.01;
            this.yo = this.y;
            this.quadSize = this.stainSize;
            this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
            this.setSprite(this.sprites.get(this.random));
            this.setColor(this.rCol * 0.8F, 0.01F, 0.02F);
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
            return new BloodParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}

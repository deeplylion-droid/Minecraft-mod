package com.rainbowyoyo.client.sound;

import com.rainbowyoyo.entity.YoyoEntity;
import com.rainbowyoyo.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** The whirr of the spinning yo-yo: follows it and gets higher and louder with its speed. */
public class YoyoSpinSound extends AbstractTickableSoundInstance {
    private final YoyoEntity yoyo;

    public YoyoSpinSound(YoyoEntity yoyo) {
        super(ModSounds.SPIN.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.yoyo = yoyo;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.x = yoyo.getX();
        this.y = yoyo.getY();
        this.z = yoyo.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (yoyo.isRemoved()) {
            stop();
            return;
        }
        this.x = yoyo.getX();
        this.y = yoyo.getY();
        this.z = yoyo.getZ();
        float intensity = yoyo.soundIntensity();
        this.volume = 0.2F + 0.6F * intensity;
        this.pitch = 0.75F + 0.75F * intensity;
    }
}

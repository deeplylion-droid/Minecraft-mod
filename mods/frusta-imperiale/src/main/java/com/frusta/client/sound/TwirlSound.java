package com.frusta.client.sound;

import com.frusta.item.WhipItem;
import com.frusta.motion.Whip;
import com.frusta.registry.ModSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** The whoop-whoop of a whip spinning over someone's head: faster and louder as it charges. */
public class TwirlSound extends AbstractTickableSoundInstance {
    private static final Map<Integer, TwirlSound> PLAYING = new HashMap<>();
    private final Player player;

    private TwirlSound(Player player) {
        super(ModSounds.TWIRL.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.2F;
        this.x = player.getX();
        this.y = player.getEyeY();
        this.z = player.getZ();
    }

    public static void start(Player player) {
        stop(player);
        TwirlSound sound = new TwirlSound(player);
        PLAYING.put(player.getId(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    public static void stop(Player player) {
        TwirlSound sound = PLAYING.remove(player.getId());
        if (sound != null) {
            sound.stopNow();
        }
    }

    public static void stopAll() {
        PLAYING.values().forEach(TwirlSound::stopNow);
        PLAYING.clear();
    }

    private void stopNow() {
        stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (player.isRemoved() || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof WhipItem)) {
            stop();
            PLAYING.remove(player.getId(), this);
            return;
        }
        this.x = player.getX();
        this.y = player.getEyeY() + 0.5;
        this.z = player.getZ();
        float charge = (float) Whip.charge(player.getTicksUsingItem());
        this.volume = 0.35F + 0.9F * charge;
        this.pitch = 0.7F + 0.75F * charge;
    }
}

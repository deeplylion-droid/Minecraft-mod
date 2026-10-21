package com.infezione.infection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * A player's infection, kept in the player's own saved data (so it survives logging out): the stage, how
 * long they have been in it, whether the clock is stopped, and when the next episode comes.
 */
public final class Infection {
    private static final String TAG = "infezione";
    /** What the client knows of every player's stage (filled by the packets, read only on the client side). */
    public static final Map<UUID, Integer> CLIENT_STAGES = new ConcurrentHashMap<>();

    private final CompoundTag tag;

    private Infection(CompoundTag tag) {
        this.tag = tag;
    }

    /** The infection of a player on the server. */
    public static Infection of(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG, Tag.TAG_COMPOUND)) {
            data.put(TAG, new CompoundTag());
        }
        return new Infection(data.getCompound(TAG));
    }

    /** The stage of a player, on either side (on the client, as the server last said). */
    public static int stageOf(Player player) {
        if (player.level().isClientSide) {
            return CLIENT_STAGES.getOrDefault(player.getUUID(), Stages.HEALTHY);
        }
        return of(player).stage();
    }

    /** The infection goes on in the new player entity (back from the End, or respawning when death does not cure). */
    public static void copy(Player from, Player to) {
        CompoundTag data = from.getPersistentData();
        if (data.contains(TAG, Tag.TAG_COMPOUND)) {
            to.getPersistentData().put(TAG, data.getCompound(TAG).copy());
        }
    }

    public int stage() {
        return Stages.clamp(tag.getInt("stage"));
    }

    /** A new stage starts from its beginning. */
    void setStage(int stage) {
        tag.putInt("stage", Stages.clamp(stage));
        tag.putInt("ticks", 0);
    }

    /** Ticks spent in the current stage. */
    public int ticks() {
        return tag.getInt("ticks");
    }

    void setTicks(int ticks) {
        tag.putInt("ticks", ticks);
    }

    /** The clock is stopped (/infezione pausa): the stage does not change by itself. */
    public boolean paused() {
        return tag.getBoolean("paused");
    }

    void setPaused(boolean paused) {
        tag.putBoolean("paused", paused);
    }

    /** Until when (game time) the sun leaves a new zombie alone: the transformation is not cut short by fire. */
    long sunSafeUntil() {
        return tag.getLong("sun");
    }

    void setSunSafeUntil(long gameTime) {
        tag.putLong("sun", gameTime);
    }

    /** Ticks until the next episode. */
    int nextEpisode() {
        return tag.getInt("episode");
    }

    void setNextEpisode(int ticks) {
        tag.putInt("episode", ticks);
    }
}

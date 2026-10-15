package com.disabilities.client;

import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.condition.Picks;
import com.disabilities.condition.Status;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** The local player's conditions and episodes. */
public final class Me {
    private Me() {
    }

    public static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    public static Picks picks() {
        LocalPlayer player = player();
        return player == null ? Picks.NONE : ModData.picks(player);
    }

    public static boolean has(Condition condition) {
        LocalPlayer player = player();
        return player != null && !player.isSpectator() && ModData.has(player, condition);
    }

    public static Status status() {
        LocalPlayer player = player();
        return player == null ? Status.NONE : ModData.status(player);
    }

    public static long now() {
        LocalPlayer player = player();
        return player == null ? 0 : player.level().getGameTime();
    }

    public static boolean asleep() {
        LocalPlayer player = player();
        return player != null && ModData.asleep(player);
    }

    /** Asleep or frozen (Parkinson): no walking, no hands. */
    public static boolean frozen() {
        LocalPlayer player = player();
        return player != null && ModData.frozen(player);
    }
}

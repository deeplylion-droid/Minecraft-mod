package com.filorosso.client;

import com.filorosso.data.Link;
import com.filorosso.data.ModData;
import com.filorosso.data.Status;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/** The local player's bond. */
public final class Me {
    private Me() {
    }

    public static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    public static Link link() {
        LocalPlayer p = player();
        return p == null ? Link.NONE : ModData.link(p);
    }

    public static Status status() {
        LocalPlayer p = player();
        return p == null ? Status.NONE : ModData.status(p);
    }

    public static boolean downed() {
        LocalPlayer p = player();
        return p != null && ModData.downed(p);
    }

    public static long now() {
        LocalPlayer p = player();
        return p == null ? 0L : p.level().getGameTime();
    }

    /** The partner, if their player is loaded on this client. */
    public static Player partner() {
        LocalPlayer p = player();
        Link link = link();
        if (p == null || link.partner().isEmpty()) {
            return null;
        }
        return p.level().getPlayerByUUID(link.partner().get());
    }
}

package com.belowbedrock.registry;

import com.belowbedrock.BelowBedrock;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/** Grants the hidden advancements that have no vanilla trigger (their only criterion is called "done"). */
public final class ModAdvancements {
    public static final String BREAK_BEDROCK = "break_bedrock";
    public static final String SEEN_HIM = "seen_him";
    public static final String ESCAPE = "escape";

    private ModAdvancements() {
    }

    public static void award(ServerPlayer player, String path) {
        AdvancementHolder holder = player.server.getAdvancements().get(BelowBedrock.id(path));
        if (holder != null) {
            player.getAdvancements().award(holder, "done");
        }
    }
}

package com.belowbedrock.registry;

import com.belowbedrock.BelowBedrock;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Per-player memory, saved with the player and kept on death. */
public final class ModAttachments {
    /** Set once the player has woken up in the faded world. */
    public static final AttachmentType<Boolean> VISITED = AttachmentRegistry.<Boolean>builder()
            .persistent(Codec.BOOL)
            .copyOnDeath()
            .buildAndRegister(BelowBedrock.id("visited"));
    /** Set once the player has fallen back out of the faded world: from then on, He sometimes follows. */
    public static final AttachmentType<Boolean> RETURNED = AttachmentRegistry.<Boolean>builder()
            .persistent(Codec.BOOL)
            .copyOnDeath()
            .buildAndRegister(BelowBedrock.id("returned"));

    private ModAttachments() {
    }

    public static void register() {
    }

    public static boolean flag(net.minecraft.world.entity.player.Player player, AttachmentType<Boolean> type) {
        return Boolean.TRUE.equals(player.getAttached(type));
    }
}

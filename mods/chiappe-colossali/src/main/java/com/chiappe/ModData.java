package com.chiappe;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/** Each player's cheek level, sent to everyone who can see them (other players' XP is not, in vanilla). */
public final class ModData {
    private ModData() {
    }

    /** The XP level, up to {@link Butt#MAX_LEVEL}; set by the server every time it changes. */
    public static final AttachmentType<Integer> LEVEL = AttachmentRegistry.create(Chiappe.id("level"),
            b -> b.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    public static int level(Player player) {
        Integer v = player.getAttached(LEVEL);
        return v != null ? v : 0;
    }

    public static void init() {
    }
}

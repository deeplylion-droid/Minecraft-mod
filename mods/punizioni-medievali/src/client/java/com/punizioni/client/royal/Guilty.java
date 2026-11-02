package com.punizioni.client.royal;

import com.punizioni.item.ModItems;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/** Who the king's lens shows as guilty (the server sends the list every second to whoever holds the lens). */
public final class Guilty {
    private Guilty() {
    }

    /** The outline: a deep red. */
    public static final int COLOR = 0xFFE0201A;

    private static final IntSet IDS = new IntOpenHashSet();
    private static long received = Long.MIN_VALUE;

    public static void receive(List<Integer> ids) {
        IDS.clear();
        IDS.addAll(ids);
        Minecraft mc = Minecraft.getInstance();
        received = mc.level != null ? mc.level.getGameTime() : 0L;
    }

    public static void reset() {
        IDS.clear();
        received = Long.MIN_VALUE;
    }

    public static boolean holdsLens(Player player) {
        return player.getMainHandItem().is(ModItems.ROYAL_LENS) || player.getOffhandItem().is(ModItems.ROYAL_LENS);
    }

    /** Whether this entity glows red for the local player now. */
    public static boolean shows(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || entity == mc.player || IDS.isEmpty() || !holdsLens(mc.player)) {
            return false;
        }
        return mc.level.getGameTime() - received < 50L && IDS.contains(entity.getId());
    }
}

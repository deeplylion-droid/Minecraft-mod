package com.chiappe.client;

import com.chiappe.Butt;
import com.chiappe.ModData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Every visible player's cheeks, moved once per client tick. */
public final class Cheeks {
    private Cheeks() {
    }

    private static final Map<Integer, ButtState> STATES = new HashMap<>();
    /** True while the inventory draws the player in its little window. */
    public static boolean inGui;

    public static ButtState get(int entityId) {
        return STATES.computeIfAbsent(entityId, k -> new ButtState());
    }

    static void tick(Minecraft mc) {
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (AbstractClientPlayer p : mc.level.players()) {
            seen.add(p.getId());
            get(p.getId()).tick(p, ModData.level(p));
        }
        STATES.keySet().retainAll(seen);
    }

    /** The biggest a player's cheeks are now or are going to be (blocks), for culling. */
    public static double radius(Player player) {
        ButtState s = STATES.get(player.getId());
        double target = Butt.diameter(ModData.level(player));
        return Math.max(target, s != null ? s.size : 0.0) / 2.0;
    }

    /** Called when a player's render state is filled. */
    public static void extract(Avatar entity, AvatarRenderState state, float partial) {
        ButtState s = entity instanceof Player ? STATES.get(entity.getId()) : null;
        if (s == null) {
            state.setData(ButtVisual.KEY, null);
            return;
        }
        ButtVisual v = new ButtVisual();
        s.fill(v, partial);
        if (inGui && v.diameter > 0.6F) {
            v.diameter = 0.6F + 0.06F * (v.diameter - 0.6F);
        }
        v.skin = state.skin.body().texturePath();
        state.setData(ButtVisual.KEY, v);
    }
}

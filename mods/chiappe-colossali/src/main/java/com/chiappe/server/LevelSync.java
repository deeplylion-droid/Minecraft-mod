package com.chiappe.server;

import com.chiappe.Butt;
import com.chiappe.Chiappe;
import com.chiappe.ModData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Objects;

/**
 * Keeps every player's cheek level in step with their XP level (it goes down when they die or spend
 * levels on enchanting, too), and moves their third person camera back so it is not inside them.
 */
public final class LevelSync {
    private LevelSync() {
    }

    private static final Identifier CAMERA = Chiappe.id("cheeks_camera");

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(LevelSync::tick);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            int level = Butt.clamp(p.experienceLevel);
            Integer had = p.getAttached(ModData.LEVEL);
            AttributeInstance camera = p.getAttribute(Attributes.CAMERA_DISTANCE);
            boolean cameraSet = camera == null || camera.getModifier(CAMERA) != null || Butt.cameraExtra(level) <= 0.0;
            if (Objects.equals(had, level) && cameraSet) {
                continue;
            }
            p.setAttached(ModData.LEVEL, level);
            if (camera != null) {
                camera.removeModifier(CAMERA);
                double extra = Butt.cameraExtra(level);
                if (extra > 0.0) {
                    camera.addTransientModifier(new AttributeModifier(CAMERA, extra, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }
    }
}

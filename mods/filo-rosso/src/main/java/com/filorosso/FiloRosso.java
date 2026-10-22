package com.filorosso;

import com.filorosso.data.ModData;
import com.filorosso.entity.ModEntities;
import com.filorosso.item.ModItems;
import com.filorosso.net.Net;
import com.filorosso.server.Abilities;
import com.filorosso.server.Affinity;
import com.filorosso.server.Bonds;
import com.filorosso.server.Challenges;
import com.filorosso.server.Combat;
import com.filorosso.server.Destiny;
import com.filorosso.server.Events;
import com.filorosso.server.FiloCommand;
import com.filorosso.server.Joining;
import com.filorosso.server.ModRules;
import com.filorosso.server.Revive;
import com.filorosso.server.Scores;
import com.filorosso.server.Servants;
import com.filorosso.server.SharedLife;
import com.filorosso.server.Tether;
import com.filorosso.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FiloRosso implements ModInitializer {
    public static final String MODID = "filorosso";
    public static final Logger LOGGER = LoggerFactory.getLogger("Filo Rosso");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    @Override
    public void onInitialize() {
        ModData.init();
        ModSounds.init();
        ModItems.init();
        ModEntities.init();
        ModRules.init();
        Net.init();
        Bonds.init();
        Tether.init();
        Affinity.init();
        Combat.init();
        Abilities.init();
        Revive.init();
        SharedLife.init();
        Joining.init();
        FiloCommand.init();
        com.filorosso.world.Stage.init();

        ServerLifecycleEvents.SERVER_STOPPING.register(Abilities::stop);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Destiny.newSession(server);
            Scores.refresh(server);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                Joining.tick(server);
                Tether.tick(server);
                Tether.tickCuts(server);
                Events.tick(server);
                Affinity.tick(server);
                Destiny.tick(server);
                Revive.tick(server);
                SharedLife.tick(server);
                Challenges.tick(server);
                Servants.tick(server);
                Abilities.tick(server);
                com.filorosso.world.Theatre.tick(server);
                com.filorosso.world.Stage.tick(server);
                com.filorosso.world.Puppets.tick(server);
            } catch (RuntimeException e) {
                LOGGER.error("Filo Rosso: error in the server tick", e);
            }
        });
    }
}

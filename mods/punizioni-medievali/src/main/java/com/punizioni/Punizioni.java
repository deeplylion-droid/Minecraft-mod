package com.punizioni;

import com.punizioni.data.ModData;
import com.punizioni.entity.ModEntities;
import com.punizioni.item.ModItems;
import com.punizioni.net.Net;
import com.punizioni.server.Amputations;
import com.punizioni.server.Deaths;
import com.punizioni.server.PunizioneCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punizioni Medievali: the executioner's gear and six machines for cruel, scenic punishments
 * (whipping post, iron maiden, quartering by horses, chopping block, rats, the stake). Whoever stands
 * in a machine when the executioner works it is caught; the deadly finales do not cost the inventory.
 */
public class Punizioni implements ModInitializer {
    public static final String MOD_ID = "punizioni";
    public static final Logger LOGGER = LoggerFactory.getLogger("Punizioni Medievali");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModSounds.init();
        ModParticles.init();
        ModData.init();
        ModEntities.init();
        ModItems.init();
        Net.init();
        Deaths.init();
        Amputations.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> PunizioneCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                Amputations.tick(server);
            } catch (RuntimeException e) {
                LOGGER.error("Punizioni tick failed", e);
            }
        });
    }
}

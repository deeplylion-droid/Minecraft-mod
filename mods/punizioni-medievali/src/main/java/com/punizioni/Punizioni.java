package com.punizioni;

import com.punizioni.data.ModData;
import com.punizioni.entity.ModEntities;
import com.punizioni.inspect.Inspection;
import com.punizioni.item.ModItems;
import com.punizioni.misdeed.Misdeeds;
import com.punizioni.net.Net;
import com.punizioni.server.Amputations;
import com.punizioni.server.Bonds;
import com.punizioni.server.Deaths;
import com.punizioni.server.Dung;
import com.punizioni.server.Jester;
import com.punizioni.server.PunizioneCommand;
import com.punizioni.server.Royalty;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punizioni Medievali: the executioner's gear and machines for cruel, scenic punishments
 * (whipping post, iron maiden, quartering by horses, chopping block, rats, the stake, guillotine,
 * wheel, brazen bull, gallows, pillory). Whoever stands in a machine when the executioner works it is caught;
 * the deadly finales do not cost the inventory. For the king: crown, mantle, the lens that sees
 * pockets and misdeeds, and the overseer's whip. The prisoner's rope; the jester's motley and the
 * prisoner's rags; the pillory, and dung to throw at whoever is in it.
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
        Inspection.init();
        Misdeeds.init();
        Royalty.init();
        Bonds.init();
        Jester.init();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Misdeeds.forget(handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> PunizioneCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                Amputations.tick(server);
                Royalty.tick(server);
                Bonds.tick(server);
                Dung.tick(server);
                Jester.tick(server);
            } catch (RuntimeException e) {
                LOGGER.error("Punizioni tick failed", e);
            }
        });
    }
}

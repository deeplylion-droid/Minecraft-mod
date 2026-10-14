package com.jumpscares;

import com.jumpscares.net.ScareNet;
import com.jumpscares.registry.ModEntities;
import com.jumpscares.registry.ModItems;
import com.jumpscares.registry.ModSounds;
import com.jumpscares.scare.ScareCommand;
import com.jumpscares.scare.Scares;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jumpscare!: five 3D monsters that leap at a player's face, screaming. Started with
 * /jumpscare or with a scare pearl thrown at someone; every client draws its own scare.
 */
public class Jumpscares implements ModInitializer {
    public static final String MODID = "jumpscares";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    @Override
    public void onInitialize() {
        ModSounds.register();
        ModEntities.register();
        ModItems.register();
        ScareNet.register();
        Scares.register();
        ScareCommand.register();
    }
}

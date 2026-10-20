package com.chiappe;

import com.chiappe.net.Net;
import com.chiappe.server.FartAttack;
import com.chiappe.server.LevelSync;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chiappe Colossali: every player gets a pair of big cheeks that grow with every XP level (ten blocks
 * each at level 25), and a fart (key G) that hits everything around harder with every level.
 */
public class Chiappe implements ModInitializer {
    public static final String MOD_ID = "chiappe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModSounds.init();
        ModParticles.init();
        ModData.init();
        Net.init();
        LevelSync.init();
        FartAttack.init();
    }
}

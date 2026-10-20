package com.disabilities;

import com.disabilities.condition.ModData;
import com.disabilities.item.ModItems;
import com.disabilities.net.Net;
import com.disabilities.server.Ailments;
import com.disabilities.server.Chooser;
import com.disabilities.server.DisabilityCommand;
import com.disabilities.server.ModRules;
import com.disabilities.server.Speech;
import com.disabilities.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Disabilità: on your first spawn you must pick two disabilities out of 21. Each one changes how
 * you play, and the others can see it on you.
 */
public class Disabilities implements ModInitializer {
    public static final String MODID = "disabilities";
    public static final Logger LOGGER = LoggerFactory.getLogger("Disabilità");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    @Override
    public void onInitialize() {
        ModData.init();
        ModSounds.init();
        ModItems.init();
        ModRules.init();
        Net.init();
        Chooser.init();
        Ailments.init();
        Speech.init();
        DisabilityCommand.init();
    }
}

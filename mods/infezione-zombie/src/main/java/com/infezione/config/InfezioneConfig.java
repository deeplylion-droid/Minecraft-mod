package com.infezione.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class InfezioneConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    /** Gameplay, per world (saves/<world>/serverconfig/infezione-server.toml). */
    public static final class Server {
        public final ForgeConfigSpec.DoubleValue minutesPerStage;
        public final ForgeConfigSpec.BooleanValue deathCures;
        public final ForgeConfigSpec.BooleanValue gameplayEffects;
        public final ForgeConfigSpec.BooleanValue burnsInSun;
        public final ForgeConfigSpec.DoubleValue zombieDamage;
        public final ForgeConfigSpec.BooleanValue nightVision;
        public final ForgeConfigSpec.BooleanValue zombiesAreFriends;

        Server(ForgeConfigSpec.Builder builder) {
            builder.push("infection");
            minutesPerStage = builder
                    .comment("Minutes from one stage to the next (stage 1 to 2, 2 to 3, 3 to 4, 4 to zombie).",
                            "Also changed in game with /infezione durata <minutes>.")
                    .defineInRange("minutesPerStage", 5.0, 0.05, 1440.0);
            deathCures = builder
                    .comment("Dying cures the infection (a zombie comes back human).")
                    .define("deathCures", true);
            gameplayEffects = builder
                    .comment("The infection weighs on the game: hunger, fewer hearts (stages 3 and 4), slower at stage 4,",
                            "Nausea, darkness and convulsions. Off: only what you see and hear.")
                    .define("gameplayEffects", true);
            builder.pop();

            builder.push("zombie");
            burnsInSun = builder
                    .comment("A zombie player burns in the sun like a zombie (a helmet protects them).")
                    .define("burnsInSun", true);
            zombieDamage = builder
                    .comment("Damage a zombie player adds to their hits (2 = a heart).")
                    .defineInRange("extraDamage", 2.0, 0.0, 100.0);
            nightVision = builder
                    .comment("A zombie player sees in the dark.")
                    .define("nightVision", true);
            zombiesAreFriends = builder
                    .comment("Zombies (the entity type tag infezione:zombie_friends) leave zombie players alone,",
                            "unless a zombie player hits them first.")
                    .define("zombiesAreFriends", true);
            builder.pop();
        }
    }

    /** Looks and sounds, per player (config/infezione-client.toml). */
    public static final class Client {
        public final ForgeConfigSpec.BooleanValue panel;
        public final ForgeConfigSpec.BooleanValue timer;
        public final ForgeConfigSpec.BooleanValue hearts;
        public final ForgeConfigSpec.BooleanValue vignette;
        public final ForgeConfigSpec.BooleanValue screenShake;
        public final ForgeConfigSpec.BooleanValue heartbeat;
        public final ForgeConfigSpec.BooleanValue hallucinations;
        public final ForgeConfigSpec.BooleanValue skins;
        public final ForgeConfigSpec.BooleanValue boils;

        Client(ForgeConfigSpec.Builder builder) {
            builder.push("infezione");
            panel = builder
                    .comment("The genome at the top left: how far the infection has gone, the stage, the time left.")
                    .define("panel", true);
            timer = builder
                    .comment("Show in the panel how long until the next stage.")
                    .define("timer", true);
            hearts = builder
                    .comment("Infected hearts in the health bar.")
                    .define("hearts", true);
            vignette = builder
                    .comment("Veins and darkness at the edges of the screen, beating with the heart.")
                    .define("vignette", true);
            screenShake = builder
                    .comment("The screen shakes with twitches and convulsions.")
                    .define("screenShake", true);
            heartbeat = builder
                    .comment("Hear your own heartbeat while infected.")
                    .define("heartbeat", true);
            hallucinations = builder
                    .comment("From stage 3, sounds that are not there (groans behind you, footsteps).")
                    .define("hallucinations", true);
            skins = builder
                    .comment("Infected players get the infected look (grey skin, veins, eyes, blood).")
                    .define("skins", true);
            boils = builder
                    .comment("Boils grow on infected players from stage 3, and burst.")
                    .define("boils", true);
            builder.pop();
        }
    }

    private InfezioneConfig() {
    }
}

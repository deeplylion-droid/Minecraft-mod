package com.frusta.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class FrustaConfig {
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

    /**
     * Gameplay, per world (saves/<world>/serverconfig/frusta-server.toml). With the defaults a netherite
     * whip with Sottomissione V hits for 8 * 2^5 = 256: the Ender Dragon (200 health) dies in one lash,
     * while Sottomissione IV (128) and a diamond whip with V (192) leave it alive.
     */
    public static final class Server {
        public final ForgeConfigSpec.DoubleValue leatherDamage;
        public final ForgeConfigSpec.DoubleValue diamondDamage;
        public final ForgeConfigSpec.DoubleValue netheriteDamage;
        public final ForgeConfigSpec.DoubleValue knockback;
        public final ForgeConfigSpec.DoubleValue multiplierPerLevel;
        public final ForgeConfigSpec.BooleanValue kneel;
        public final ForgeConfigSpec.DoubleValue imperialCone;
        public final ForgeConfigSpec.DoubleValue imperialKnockback;
        public final ForgeConfigSpec.BooleanValue grab;
        public final ForgeConfigSpec.DoubleValue grabStrength;

        Server(ForgeConfigSpec.Builder builder) {
            builder.push("whip");
            leatherDamage = builder
                    .comment("Damage of a lash with the black leather whip, before Sottomissione (4 = 2 hearts).")
                    .defineInRange("leatherDamage", 4.0, 0.0, 1000.0);
            diamondDamage = builder
                    .comment("Damage of a lash with the diamond whip, before Sottomissione.")
                    .defineInRange("diamondDamage", 6.0, 0.0, 1000.0);
            netheriteDamage = builder
                    .comment("Damage of a lash with the netherite whip, before Sottomissione.")
                    .defineInRange("netheriteDamage", 8.0, 0.0, 1000.0);
            knockback = builder
                    .comment("Knockback added to a lash (0 = like a punch).")
                    .defineInRange("knockback", 0.5, 0.0, 10.0);
            builder.pop();

            builder.push("sottomissione");
            multiplierPerLevel = builder
                    .comment("Every level of Sottomissione multiplies the damage by this",
                            "(2 = Sottomissione I doubles it, Sottomissione V multiplies it by 32).")
                    .defineInRange("damageMultiplierPerLevel", 2.0, 1.0, 10.0);
            kneel = builder
                    .comment("Whoever is whipped with Sottomissione falls to their knees (1 s at level I, 3 s at V).")
                    .define("kneel", true);
            builder.pop();

            builder.push("imperial");
            imperialCone = builder
                    .comment("Width of the Imperial Crack, in degrees either side of where you look (at full charge).")
                    .defineInRange("coneDegrees", 40.0, 5.0, 90.0);
            imperialKnockback = builder
                    .comment("Knockback of the Imperial Crack.")
                    .defineInRange("knockback", 1.4, 0.0, 10.0);
            builder.pop();

            builder.push("grab");
            grab = builder
                    .comment("Sneak + use: the whip wraps around someone and pulls them to you.")
                    .define("enabled", true);
            grabStrength = builder
                    .comment("How hard the grab pulls (1 = lands them at your feet, a block or two in front of you).")
                    .defineInRange("strength", 1.0, 0.0, 5.0);
            builder.pop();
        }
    }

    /** Looks and sounds, per player (config/frusta-client.toml). */
    public static final class Client {
        public final ForgeConfigSpec.BooleanValue firstPerson;
        public final ForgeConfigSpec.BooleanValue trails;
        public final ForgeConfigSpec.BooleanValue particles;
        public final ForgeConfigSpec.BooleanValue screenShake;
        public final ForgeConfigSpec.BooleanValue duckOtherSounds;
        public final ForgeConfigSpec.DoubleValue duckVolume;

        Client(ForgeConfigSpec.Builder builder) {
            builder.push("frusta");
            firstPerson = builder
                    .comment("In first person the handle swings with the whip's moves (off: it stays still like any held item).")
                    .define("firstPerson", true);
            trails = builder
                    .comment("Glowing trail behind the tip and shockwave rings when it cracks.")
                    .define("trails", true);
            particles = builder
                    .comment("Sparks, smoke and dust around the cracks.")
                    .define("particles", true);
            screenShake = builder
                    .comment("Shake the screen when a crack goes off close to you.")
                    .define("screenShake", true);
            duckOtherSounds = builder
                    .comment("Turn every other sound of the game down for a moment when a whip cracks near you,",
                            "so the crack towers over everything.")
                    .define("duckOtherSounds", true);
            duckVolume = builder
                    .comment("How loud the other sounds stay during a crack (0.25 = a quarter).")
                    .defineInRange("duckVolume", 0.25, 0.0, 1.0);
            builder.pop();
        }
    }

    private FrustaConfig() {
    }
}

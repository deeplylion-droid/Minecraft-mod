package com.wurstel.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class WurstelConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
    }

    /** Per-player visual settings (config/wurstel-client.toml). */
    public static final class Client {
        public final ForgeConfigSpec.BooleanValue showInFirstPerson;
        public final ForgeConfigSpec.BooleanValue sounds;
        public final ForgeConfigSpec.BooleanValue particles;

        Client(ForgeConfigSpec.Builder builder) {
            builder.push("wurstel");
            showInFirstPerson = builder
                    .comment("Show your own wurstel in first person view.")
                    .define("showInFirstPerson", true);
            sounds = builder
                    .comment("Play the slap and whoosh sounds.")
                    .define("sounds", true);
            particles = builder
                    .comment("Spawn particles when the wurstel hits something.")
                    .define("particles", true);
            builder.pop();
        }
    }

    /**
     * Gameplay settings, per world (saves/<world>/serverconfig/wurstel-server.toml). With the defaults a
     * netherite belt with Turgido V slaps 8 * 2^5 = 256 times harder than a punch: enough to kill the
     * Ender Dragon (200 health) in one hit, while Turgido IV is not.
     */
    public static final class Server {
        public final ForgeConfigSpec.DoubleValue damageMultiplier;
        public final ForgeConfigSpec.DoubleValue extraKnockback;
        public final ForgeConfigSpec.DoubleValue miningSpeedMultiplier;
        public final ForgeConfigSpec.DoubleValue diamondDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue diamondMiningSpeedMultiplier;
        public final ForgeConfigSpec.DoubleValue netheriteDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue netheriteMiningSpeedMultiplier;
        public final ForgeConfigSpec.DoubleValue turgidoMultiplierPerLevel;

        Server(ForgeConfigSpec.Builder builder) {
            builder.push("slap");
            damageMultiplier = builder
                    .comment("Multiplier applied to bare-hand damage when slapping with the wurstel belt (1 = like a punch).")
                    .defineInRange("damageMultiplier", 2.0, 0.0, 100.0);
            extraKnockback = builder
                    .comment("Knockback added to wurstel slaps (0 = like a punch).")
                    .defineInRange("extraKnockback", 0.6, 0.0, 10.0);
            miningSpeedMultiplier = builder
                    .comment("Mining speed multiplier of slaps with the wurstel belt (1 = like a bare hand).")
                    .defineInRange("miningSpeedMultiplier", 1.0, 0.0, 100.0);
            builder.pop();

            builder.comment("Diamond wurstel belt. Its slaps also get the drops a diamond tool would.").push("diamond");
            diamondDamageMultiplier = builder
                    .comment("Multiplier applied to bare-hand damage (1 = like a punch).")
                    .defineInRange("damageMultiplier", 4.0, 0.0, 100.0);
            diamondMiningSpeedMultiplier = builder
                    .comment("Mining speed multiplier (1 = like a bare hand, 8 = like a diamond pickaxe).")
                    .defineInRange("miningSpeedMultiplier", 8.0, 0.0, 100.0);
            builder.pop();

            builder.comment("Netherite wurstel belt. Its slaps also get the drops a netherite tool would.").push("netherite");
            netheriteDamageMultiplier = builder
                    .comment("Multiplier applied to bare-hand damage (1 = like a punch).")
                    .defineInRange("damageMultiplier", 8.0, 0.0, 100.0);
            netheriteMiningSpeedMultiplier = builder
                    .comment("Mining speed multiplier (1 = like a bare hand, 9 = like a netherite pickaxe).")
                    .defineInRange("miningSpeedMultiplier", 9.0, 0.0, 100.0);
            builder.pop();

            builder.push("turgido");
            turgidoMultiplierPerLevel = builder
                    .comment("Every level of the Turgido enchantment multiplies the slap damage by this",
                            "(2 = Turgido I doubles it, Turgido V multiplies it by 32).")
                    .defineInRange("damageMultiplierPerLevel", 2.0, 1.0, 10.0);
            builder.pop();
        }
    }

    private WurstelConfig() {
    }
}

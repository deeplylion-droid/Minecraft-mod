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

    /** Gameplay settings, per world (saves/<world>/serverconfig/wurstel-server.toml). */
    public static final class Server {
        public final ForgeConfigSpec.DoubleValue damageMultiplier;
        public final ForgeConfigSpec.DoubleValue extraKnockback;

        Server(ForgeConfigSpec.Builder builder) {
            builder.push("slap");
            damageMultiplier = builder
                    .comment("Multiplier applied to bare-hand damage when slapping with the wurstel (1 = like a punch).")
                    .defineInRange("damageMultiplier", 2.0, 0.0, 100.0);
            extraKnockback = builder
                    .comment("Knockback added to wurstel slaps (0 = like a punch).")
                    .defineInRange("extraKnockback", 0.6, 0.0, 10.0);
            builder.pop();
        }
    }

    private WurstelConfig() {
    }
}

package com.rainbowyoyo.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class YoyoConfig {
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

    /** Gameplay, per world (saves/<world>/serverconfig/rainbowyoyo-server.toml). */
    public static final class Server {
        public final ForgeConfigSpec.DoubleValue damage;
        public final ForgeConfigSpec.DoubleValue stringLength;

        Server(ForgeConfigSpec.Builder builder) {
            builder.push("yoyo");
            damage = builder
                    .comment("Damage of every hit of the yo-yo (80 = 40 hearts).")
                    .defineInRange("damage", 80.0, 0.0, 1000.0);
            stringLength = builder
                    .comment("Length of the string in blocks: how far the yo-yo goes when thrown.")
                    .defineInRange("stringLength", 8.0, 3.0, 16.0);
            builder.pop();
        }
    }

    /** Looks, per player (config/rainbowyoyo-client.toml). */
    public static final class Client {
        public final ForgeConfigSpec.BooleanValue trail;
        public final ForgeConfigSpec.BooleanValue particles;

        Client(ForgeConfigSpec.Builder builder) {
            builder.push("yoyo");
            trail = builder
                    .comment("Draw the glowing rainbow trail behind the yo-yo.")
                    .define("trail", true);
            particles = builder
                    .comment("Spawn rainbow sparkles around the yo-yo.")
                    .define("particles", true);
            builder.pop();
        }
    }

    private YoyoConfig() {
    }
}

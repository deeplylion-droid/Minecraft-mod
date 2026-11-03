package com.punizioni.machine;

import com.punizioni.entity.ModEntities;
import com.punizioni.item.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.Locale;

/** The machines, with their names in the commands. */
public enum MachineKind {
    WHIPPING_POST("fustigazione"),
    IRON_MAIDEN("vergine"),
    QUARTERING_RACK("squartamento"),
    CHOPPING_BLOCK("ceppo"),
    RAT_TABLE("ratti"),
    PYRE("rogo"),
    GUILLOTINE("ghigliottina"),
    WHEEL("ruota"),
    BRAZEN_BULL("toro"),
    GALLOWS("forca"),
    PILLORY("gogna");

    public static final MachineKind[] ALL = values();

    public final String command;

    MachineKind(String command) {
        this.command = command;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public Item item() {
        return ModItems.MACHINES.get(this);
    }

    public EntityType<? extends Machine> type() {
        return ModEntities.MACHINES.get(this);
    }

    public static MachineKind byCommand(String name) {
        for (MachineKind k : ALL) {
            if (k.command.equals(name)) {
                return k;
            }
        }
        return null;
    }
}

package com.punizioni.misdeed;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.punizioni.Punizioni;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who placed each chest, barrel, furnace, shulker box... (anything that holds items), per dimension:
 * so the lens can tell "opened a chest" from "opened Luigi's chest" and "stole from Luigi". Only
 * containers placed while the mod is installed have an owner.
 */
public final class Owners extends SavedData {
    public record Owner(UUID id, String name) {
    }

    private record Entry(long pos, UUID id, String name) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.fieldOf("name").forGetter(Entry::name)
        ).apply(i, Entry::new));
    }

    public static final Codec<Owners> CODEC = Entry.CODEC.listOf().xmap(Owners::new, Owners::entries);
    public static final SavedDataType<Owners> TYPE = new SavedDataType<>(Punizioni.id("owners"), Owners::new, CODEC, null);

    private final Map<Long, Owner> owners = new HashMap<>();

    public Owners() {
    }

    private Owners(List<Entry> entries) {
        for (Entry e : entries) {
            this.owners.put(e.pos, new Owner(e.id, e.name));
        }
    }

    private List<Entry> entries() {
        List<Entry> list = new ArrayList<>(this.owners.size());
        this.owners.forEach((pos, o) -> list.add(new Entry(pos, o.id(), o.name())));
        return list;
    }

    public static Owners of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Owner get(BlockPos pos) {
        return this.owners.get(pos.asLong());
    }

    public void put(BlockPos pos, UUID id, String name) {
        this.owners.put(pos.asLong(), new Owner(id, name));
        this.setDirty();
    }

    public void remove(BlockPos pos) {
        if (this.owners.remove(pos.asLong()) != null) {
            this.setDirty();
        }
    }
}

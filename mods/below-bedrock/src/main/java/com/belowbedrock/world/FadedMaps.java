package com.belowbedrock.world;

import com.belowbedrock.BelowBedrock;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The creepy maps: nine pictures of Him, drawn with the vanilla map palette. Each picture gets one
 * locked map id per world, shared by every copy, so they never update and never fill the map list.
 */
public final class FadedMaps {
    /** Custom data key that marks a creepy map (and, in worldgen, a frame waiting for one). */
    public static final String TAG = "belowbedrock_picture";
    public static final List<String> PICTURES = List.of("field", "trees", "face", "corridor", "behind", "window", "hole",
            "drawing", "watching");
    private static final Map<String, byte[]> COLORS = new HashMap<>();

    private FadedMaps() {
    }

    public static String random(RandomSource random) {
        return PICTURES.get(random.nextInt(PICTURES.size()));
    }

    /** A map item showing {@code picture}. Must run on the server thread. */
    public static ItemStack create(ServerLevel anyLevel, String requested) {
        String picture = PICTURES.contains(requested) ? requested : PICTURES.get(0);
        ServerLevel overworld = anyLevel.getServer().overworld();
        Ids ids = overworld.getDataStorage().computeIfAbsent(Ids.FACTORY, "belowbedrock_maps");
        MapId id = ids.byPicture.get(picture);
        if (id == null || overworld.getMapData(id) == null) {
            id = overworld.getFreeMapId();
            MapItemSavedData data = MapItemSavedData.createFresh(0.0, 0.0, (byte) 0, false, false, Faded.LEVEL);
            System.arraycopy(colors(picture), 0, data.colors, 0, data.colors.length);
            overworld.setMapData(id, data.locked());
            ids.byPicture.put(picture, id);
            ids.setDirty();
        }
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, id);
        stack.set(DataComponents.ITEM_NAME, Component.translatable("map.belowbedrock." + picture));
        stack.set(DataComponents.MAP_COLOR, new MapItemColor(0x4A1414));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG, picture));
        return stack;
    }

    /** A placeholder map for worldgen (no map id yet); it becomes a real map when the chunk loads. */
    public static ItemStack placeholder(String picture) {
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG, picture));
        return stack;
    }

    /** The picture of a placeholder map, or null if {@code stack} is anything else. */
    public static String placeholderPicture(ItemStack stack) {
        if (!stack.is(Items.FILLED_MAP) || stack.has(DataComponents.MAP_ID)) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(TAG) ? data.copyTag().getString(TAG) : null;
    }

    private static synchronized byte[] colors(String picture) {
        return COLORS.computeIfAbsent(picture, name -> {
            byte[] bytes = new byte[128 * 128];
            try (InputStream in = FadedMaps.class.getResourceAsStream("/belowbedrock_maps/" + name + ".bin")) {
                if (in != null) {
                    in.readNBytes(bytes, 0, bytes.length);
                }
            } catch (IOException e) {
                BelowBedrock.LOGGER.error("Could not read map picture {}", name, e);
            }
            return bytes;
        });
    }

    /** Which map id shows which picture, saved with the world. */
    private static final class Ids extends SavedData {
        static final SavedData.Factory<Ids> FACTORY = new SavedData.Factory<>(Ids::new, Ids::load, null);
        final Map<String, MapId> byPicture = new HashMap<>();

        static Ids load(CompoundTag tag, HolderLookup.Provider registries) {
            Ids ids = new Ids();
            for (String key : tag.getAllKeys()) {
                ids.byPicture.put(key, new MapId(tag.getInt(key)));
            }
            return ids;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            this.byPicture.forEach((picture, id) -> tag.putInt(picture, id.id()));
            return tag;
        }
    }
}

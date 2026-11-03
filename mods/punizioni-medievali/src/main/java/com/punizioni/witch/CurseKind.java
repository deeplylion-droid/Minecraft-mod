package com.punizioni.witch;

import com.punizioni.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * The witch's three curses, each brewed from three ingredients in the giant cauldron and thrown in
 * a bottle: the toad (a warty, horrible toad until death), the head stuck in the butt (until death),
 * the balloon (swells up and floats away until something sharp pops it).
 */
public enum CurseKind {
    TOAD("rospo", 0x6E9A2C, () -> List.of(Items.LILY_PAD, Items.SLIME_BALL, Items.FERMENTED_SPIDER_EYE), () -> ModItems.TOAD_POTION),
    BUTT("sedere", 0x8A5A2E, () -> List.of(ModItems.POOP, Items.FEATHER, Items.ROTTEN_FLESH), () -> ModItems.BUTT_POTION),
    BALLOON("pallone", 0xF06CA8, () -> List.of(Items.PUFFERFISH, Items.GUNPOWDER, Items.STRING), () -> ModItems.BALLOON_POTION);

    public static final CurseKind[] ALL = values();

    /** The name in the /punizione command. */
    private final String name;
    /** The brew and the splash. */
    private final int color;
    private final Supplier<List<Item>> ingredients;
    private final Supplier<Item> potion;

    CurseKind(String name, int color, Supplier<List<Item>> ingredients, Supplier<Item> potion) {
        this.name = name;
        this.color = color;
        this.ingredients = ingredients;
        this.potion = potion;
    }

    public String commandName() {
        return this.name;
    }

    public int color() {
        return this.color;
    }

    public List<Item> ingredients() {
        return this.ingredients.get();
    }

    public Item potion() {
        return this.potion.get();
    }

    public static @Nullable CurseKind byIndex(int index) {
        return index >= 0 && index < ALL.length ? ALL[index] : null;
    }

    public static @Nullable CurseKind byName(String name) {
        for (CurseKind k : ALL) {
            if (k.name.equals(name)) {
                return k;
            }
        }
        return null;
    }

    /** The potion for this item, or null. */
    public static @Nullable CurseKind ofPotion(Item item) {
        for (CurseKind k : ALL) {
            if (k.potion() == item) {
                return k;
            }
        }
        return null;
    }
}

package com.belowbedrock.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/** The diary of someone who fell before you, and the book He writes for you. */
public final class Diaries {
    public static final int VOLUMES = 3;
    private static final int[] PAGES = {4, 4, 4};

    private Diaries() {
    }

    public static int randomVolume(RandomSource random) {
        return 1 + random.nextInt(VOLUMES);
    }

    /** Pages are translation keys, so the diary is in the reader's language. */
    public static ItemStack diary(int volume) {
        int v = Math.max(1, Math.min(VOLUMES, volume));
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int page = 1; page <= PAGES[v - 1]; page++) {
            pages.add(Filterable.passThrough(Component.translatable("diary.belowbedrock." + v + "." + page)));
        }
        return book(Component.translatable("diary.belowbedrock." + v), "???", pages);
    }

    /** His gift: a book that is only your name, over and over, and one last page. */
    public static ItemStack nameBook(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        StringBuilder page = new StringBuilder();
        while (page.length() + name.length() + 1 < 220) {
            page.append(name).append(' ');
        }
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pages.add(Filterable.passThrough(Component.literal(page.toString().trim())));
        }
        pages.add(Filterable.passThrough(Component.translatable("book.belowbedrock.name.last")));
        return book(Component.translatable("book.belowbedrock.name", name), name, pages);
    }

    private static ItemStack book(Component name, String author, List<Filterable<Component>> pages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("?"), author, 3, pages, true));
        stack.set(DataComponents.ITEM_NAME, name);
        return stack;
    }
}

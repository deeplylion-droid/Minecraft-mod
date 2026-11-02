package com.punizioni.inspect;

import com.punizioni.misdeed.Misdeed;
import com.punizioni.misdeed.Misdeeds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.TooltipDisplay;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The server side of the lens' view: slots 0-40 are the subject's real inventory (live: whatever
 * they do shows, whatever the king takes is gone), 41-53 are pictures made when the view opened.
 */
public class InspectContainer implements Container {
    private final ServerPlayer king;
    private final ServerPlayer subject;
    private final ItemStack[] fixed = new ItemStack[InspectMenu.SIZE];
    /** What the king took this time, for the summary the subject gets at the end. */
    private final Map<Item, Integer> taken = new LinkedHashMap<>();

    public InspectContainer(ServerPlayer king, ServerPlayer subject) {
        this.king = king;
        this.subject = subject;
        this.refresh();
    }

    /** Where container slot i is in the subject's inventory: -1 for the pictures. */
    public static int inventorySlot(int i) {
        if (i < 27) {
            return 9 + i;
        }
        if (i < 36) {
            return i - 27;
        }
        return switch (i) {
            case 36 -> 39;
            case 37 -> 38;
            case 38 -> 37;
            case 39 -> 36;
            case 40 -> 40;
            default -> -1;
        };
    }

    private void refresh() {
        long now = this.subject.level().getGameTime();
        ItemStack filler = picture(Items.STAINED_GLASS_PANE.pick(DyeColor.BLACK), Component.literal(" "), List.of());
        filler.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new ReferenceLinkedOpenHashSet<>()));
        for (int i = InspectMenu.OWN; i < InspectMenu.SIZE; i++) {
            this.fixed[i] = filler.copy();
        }
        // their head: who it is, how they are
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(this.subject.getGameProfile()));
        head.set(DataComponents.CUSTOM_NAME, plain(this.subject.getName().copy(), ChatFormatting.GOLD));
        head.set(DataComponents.LORE, new ItemLore(List.of(
                plain(Component.translatable("punizioni.inspect.health", Mth.ceil(this.subject.getHealth()), Mth.ceil(this.subject.getMaxHealth())),
                        ChatFormatting.RED),
                plain(Component.translatable("punizioni.inspect.food", this.subject.getFoodData().getFoodLevel()), ChatFormatting.GOLD),
                plain(Component.translatable("punizioni.inspect.level", this.subject.experienceLevel), ChatFormatting.GREEN),
                plain(Component.translatable("punizioni.misdeed.where", this.subject.getBlockX(), this.subject.getBlockY(), this.subject.getBlockZ()),
                        ChatFormatting.GRAY))));
        this.fixed[InspectMenu.HEAD] = head;

        List<Misdeed> deeds = Misdeeds.of(this.subject.getUUID(), now);
        this.fixed[InspectMenu.PARDON] = picture(Items.WRITABLE_BOOK, plain(Component.translatable("punizioni.inspect.pardon"), ChatFormatting.AQUA),
                List.of(plain(Component.translatable("punizioni.inspect.pardon.desc", this.subject.getName()), ChatFormatting.GRAY)));
        if (deeds.isEmpty()) {
            this.fixed[InspectMenu.MISDEEDS + 4] = picture(Items.LILY_OF_THE_VALLEY,
                    plain(Component.translatable("punizioni.inspect.innocent"), ChatFormatting.GREEN), List.of());
        }
        for (int i = 0; i < 9 && i < deeds.size(); i++) {
            this.fixed[InspectMenu.MISDEEDS + i] = paper(deeds.get(i), now);
        }
    }

    /** A misdeed as a picture: its item, how many, what and when. */
    private static ItemStack paper(Misdeed m, long now) {
        ItemStack stack = new ItemStack(Items.PAPER, Mth.clamp(m.count, 1, 64));
        stack.set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(m.icon));
        Component count = Component.literal(m.count + "×");
        Component whose = m.whose != null ? m.whose : Component.empty();
        ChatFormatting color = m.kind.severity >= 2 ? ChatFormatting.RED : m.kind.severity == 1 ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
        stack.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("punizioni.misdeed." + m.kind.key(), count, m.what, whose), color));
        List<Component> lore = new ArrayList<>();
        long ago = Math.max(0L, now - m.time) / 20L;
        lore.add(plain(ago < 60 ? Component.translatable("punizioni.time.seconds", ago) : Component.translatable("punizioni.time.minutes", ago / 60L),
                ChatFormatting.GRAY));
        lore.add(plain(Component.translatable("punizioni.misdeed.where", m.pos.getX(), m.pos.getY(), m.pos.getZ()), ChatFormatting.DARK_GRAY));
        if (m.count > 1 && (m.kind == Misdeed.Kind.HIT || m.kind == Misdeed.Kind.OPENED || m.kind == Misdeed.Kind.OPENED_OWNED
                || m.kind == Misdeed.Kind.FIRE || m.kind == Misdeed.Kind.LAVA || m.kind == Misdeed.Kind.TNT)) {
            lore.add(plain(Component.translatable("punizioni.misdeed.times", m.count), ChatFormatting.GRAY));
        }
        if (m.kind.severity >= 2) {
            lore.add(plain(Component.translatable("punizioni.misdeed.guilty"), ChatFormatting.DARK_RED));
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else if (m.kind.severity == 1) {
            lore.add(plain(Component.translatable("punizioni.misdeed.suspicious"), ChatFormatting.GOLD));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack picture(Item item, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    private static Component plain(MutableComponent c, ChatFormatting color) {
        return c.withStyle(s -> s.withItalic(false).withColor(color));
    }

    // ------------------------------------------------------------------ what the king does

    void confiscated(ServerPlayer king, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        this.taken.merge(stack.getItem(), stack.getCount(), Integer::sum);
        this.subject.sendOverlayMessage(Component.translatable("punizioni.inspect.confiscated", king.getName(), stack.getCount(), stack.getHoverName())
                .withStyle(ChatFormatting.GOLD));
        this.subject.level().playSound(null, this.subject.getX(), this.subject.getY(), this.subject.getZ(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    void pardon(ServerPlayer king) {
        Misdeeds.pardon(this.subject.getUUID());
        this.refresh();
        this.subject.sendSystemMessage(Component.translatable("punizioni.inspect.pardoned", king.getName()).withStyle(ChatFormatting.AQUA));
        king.sendOverlayMessage(Component.translatable("punizioni.inspect.pardon.done", this.subject.getName()).withStyle(ChatFormatting.AQUA));
        king.level().playSound(null, king.getX(), king.getY(), king.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        king.containerMenu.broadcastChanges();
    }

    @Override
    public void startOpen(ContainerUser user) {
        this.subject.sendOverlayMessage(Component.translatable("punizioni.inspect.searched", this.king.getName()).withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (this.taken.isEmpty()) {
            return;
        }
        MutableComponent list = Component.empty();
        boolean first = true;
        for (Map.Entry<Item, Integer> e : this.taken.entrySet()) {
            if (!first) {
                list.append(", ");
            }
            first = false;
            list.append(e.getValue() + "× ").append(Component.translatable(e.getKey().getDescriptionId()));
        }
        this.subject.sendSystemMessage(Component.translatable("punizioni.inspect.confiscated.summary", this.king.getName(), list)
                .withStyle(ChatFormatting.GOLD));
        this.taken.clear();
    }

    // ------------------------------------------------------------------ Container

    @Override
    public int getContainerSize() {
        return InspectMenu.SIZE;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public ItemStack getItem(int slot) {
        int inv = inventorySlot(slot);
        return inv >= 0 ? this.subject.getInventory().getItem(inv) : this.fixed[slot];
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        int inv = inventorySlot(slot);
        return inv >= 0 ? this.subject.getInventory().removeItem(inv, count) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        int inv = inventorySlot(slot);
        return inv >= 0 ? this.subject.getInventory().removeItemNoUpdate(inv) : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        int inv = inventorySlot(slot);
        if (inv >= 0) {
            this.subject.getInventory().setItem(inv, stack);
        }
    }

    @Override
    public void setChanged() {
        this.subject.getInventory().setChanged();
    }

    /** The subject must still be there, alive and near. */
    @Override
    public boolean stillValid(Player player) {
        return !this.subject.isRemoved() && this.subject.isAlive() && this.subject.level() == player.level()
                && this.subject.distanceToSqr(player) < Inspection.KEEP_DISTANCE * Inspection.KEEP_DISTANCE;
    }

    @Override
    public void clearContent() {
    }
}

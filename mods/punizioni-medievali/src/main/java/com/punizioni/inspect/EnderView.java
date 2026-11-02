package com.punizioni.inspect;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The subject's Ender chest seen through the lens (crouch + right click): a plain three-row chest
 * over their real one. What the king takes is listed to the subject when he closes it.
 */
public class EnderView implements Container {
    private final ServerPlayer king;
    private final ServerPlayer subject;
    private final PlayerEnderChestContainer chest;
    private final Map<Item, Integer> before = new LinkedHashMap<>();

    public EnderView(ServerPlayer king, ServerPlayer subject) {
        this.king = king;
        this.subject = subject;
        this.chest = subject.getEnderChestInventory();
    }

    private Map<Item, Integer> count() {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < this.chest.getContainerSize(); i++) {
            ItemStack s = this.chest.getItem(i);
            if (!s.isEmpty()) {
                counts.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    @Override
    public void startOpen(ContainerUser user) {
        this.before.clear();
        this.before.putAll(this.count());
        this.subject.sendOverlayMessage(Component.translatable("punizioni.inspect.searched.ender", this.king.getName()).withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void stopOpen(ContainerUser user) {
        Map<Item, Integer> now = this.count();
        MutableComponent list = Component.empty();
        boolean any = false;
        for (Map.Entry<Item, Integer> e : this.before.entrySet()) {
            int gone = e.getValue() - now.getOrDefault(e.getKey(), 0);
            if (gone > 0) {
                if (any) {
                    list.append(", ");
                }
                any = true;
                list.append(gone + "× ").append(Component.translatable(e.getKey().getDescriptionId()));
            }
        }
        if (any) {
            this.subject.sendSystemMessage(Component.translatable("punizioni.inspect.confiscated.ender", this.king.getName(), list)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public int getContainerSize() {
        return this.chest.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return this.chest.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.chest.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return this.chest.removeItem(slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return this.chest.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.chest.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        this.chest.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return !this.subject.isRemoved() && this.subject.isAlive() && this.subject.level() == player.level()
                && this.subject.distanceToSqr(player) < Inspection.KEEP_DISTANCE * Inspection.KEEP_DISTANCE;
    }

    @Override
    public void clearContent() {
    }
}

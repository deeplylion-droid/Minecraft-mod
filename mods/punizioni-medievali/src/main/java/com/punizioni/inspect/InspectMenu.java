package com.punizioni.inspect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The lens' view of a subject, a six-row chest: their inventory (rows 1-3), hotbar (row 4), armour
 * and off hand (row 5, with their head and the king's pardon), the recent misdeeds (row 6). The king
 * takes (confiscates) and puts as in a chest; the misdeeds and the decorations cannot be moved. The
 * same slots exist on the client (the menu has its own type), so nothing flickers.
 */
public class InspectMenu extends ChestMenu {
    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;
    /** Container slots that are the subject's own: 0-26 inventory, 27-35 hotbar, 36-39 armour, 40 off hand. */
    public static final int OWN = 41;
    public static final int HEAD = 42;
    public static final int PARDON = 44;
    public static final int MISDEEDS = 45;

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Identifier[] ARMOR_ICONS = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};

    public InspectMenu(int containerId, Inventory inventory, Container container) {
        super(Inspection.MENU, containerId, inventory, container, ROWS);
        for (int i = 0; i < SIZE; i++) {
            Slot old = this.slots.get(i);
            Slot slot;
            if (i < 36) {
                slot = new Own(container, i, old.x, old.y);
            } else if (i < 40) {
                slot = new Armor(container, i, old.x, old.y, ARMOR[i - 36], ARMOR_ICONS[i - 36]);
            } else if (i == 40) {
                slot = new OffHand(container, i, old.x, old.y);
            } else {
                slot = new Locked(container, i, old.x, old.y);
            }
            slot.index = i;
            this.slots.set(i, slot);
        }
    }

    /** The client's copy: the server fills it. */
    public static InspectMenu client(int containerId, Inventory inventory) {
        return new InspectMenu(containerId, inventory, new SimpleContainer(SIZE));
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
        if (slotIndex == PARDON && player instanceof ServerPlayer king && this.getContainer() instanceof InspectContainer c) {
            c.pardon(king);
            return;
        }
        super.clicked(slotIndex, buttonNum, containerInput, player);
    }

    /** Shift-click: the subject's things to the king, the king's things into the subject's inventory (never into the locked rows). */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot instanceof Locked || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (slotIndex < SIZE) {
            if (!this.moveItemStackTo(stack, SIZE, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            ItemStack moved = before.copyWithCount(before.getCount() - stack.getCount());
            if (!moved.isEmpty() && slot instanceof Own own) {
                own.confiscated(player, moved);
            }
        } else if (!this.moveItemStackTo(stack, 0, 36, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return !(target instanceof Locked) && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof Locked);
    }

    /** One of the subject's own slots: what the king takes from it is confiscated (the subject is told). */
    public static class Own extends Slot {
        Own(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            super.onTake(player, carried);
            this.confiscated(player, carried);
        }

        void confiscated(Player player, ItemStack stack) {
            if (player instanceof ServerPlayer king && this.container instanceof InspectContainer c) {
                c.confiscated(king, stack);
            }
        }
    }

    /** An armour slot: only what can be worn there. */
    public static class Armor extends Own {
        private final EquipmentSlot equipment;
        private final Identifier icon;

        Armor(Container container, int slot, int x, int y, EquipmentSlot equipment, Identifier icon) {
            super(container, slot, x, y);
            this.equipment = equipment;
            this.icon = icon;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            return equippable != null && equippable.slot() == this.equipment;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public @Nullable Identifier getNoItemIcon() {
            return this.icon;
        }
    }

    public static class OffHand extends Own {
        OffHand(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public @Nullable Identifier getNoItemIcon() {
            return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
        }
    }

    /** Decorations and misdeeds: look, don't touch. */
    public static class Locked extends Slot {
        Locked(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}

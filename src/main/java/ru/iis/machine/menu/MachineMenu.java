package ru.iis.machine.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Общая часть меню машин: слоты машины, инвентарь игрока (8,84) и данные для шкал. */
public abstract class MachineMenu extends AbstractContainerMenu {
    protected final Container container;
    protected final ContainerData data;
    protected final int machineSlots;

    protected MachineMenu(@Nullable MenuType<?> type, int id, Container container, ContainerData data) {
        super(type, id);
        this.container = container;
        this.data = data;
        this.machineSlots = container.getContainerSize();
        checkContainerDataCount(data, data.getCount());
    }

    protected void finish(Inventory inventory) {
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    public int data(int i) {
        return data.get(i);
    }

    /** Можно ли положить предмет в слот машины shift-кликом. */
    protected abstract boolean acceptsShiftClick(int machineSlot, ItemStack stack);

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerStart = machineSlots, end = slots.size();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, playerStart, end, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int s = 0; s < machineSlots && !moved; s++) {
                if (acceptsShiftClick(s, stack) && moveItemStackTo(stack, s, s + 1, false)) moved = true;
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    /** Слот только для выдачи. */
    public static class OutputSlot extends Slot {
        public OutputSlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}

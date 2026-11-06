package ru.iis.reactor;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Сетка реактора 9 × 6; столбцы без камеры закрыты. */
public class ReactorMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 240, GRID_Y = 18, INV_Y = 158;
    private final ContainerData data;
    private final Container container;

    public ReactorMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(ReactorContent.REACTOR_MENU.get(), id);
        this.container = container;
        this.data = data;
        addDataSlots(data);
        for (int y = 0; y < ReactorCore.HEIGHT; y++) {
            for (int x = 0; x < ReactorCore.WIDTH; x++) {
                final int column = x;
                addSlot(new Slot(container, x + y * ReactorCore.WIDTH, 8 + x * 18, GRID_Y + y * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return column < columns() && stack.getItem() instanceof ReactorPartItem;
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }

                    @Override
                    public boolean isActive() {
                        return column < columns();
                    }
                });
            }
        }
        addStandardInventorySlots(inventory, 8, INV_Y);
    }

    public static ReactorMenu client(int id, Inventory inventory) {
        return new ReactorMenu(id, inventory, new SimpleContainer(NuclearReactorBlockEntity.SLOTS), new SimpleContainerData(6));
    }

    public int heat() {
        return data.get(0);
    }

    public int maxHeat() {
        return Math.max(1, data.get(1));
    }

    public int euPerTick() {
        return data.get(2);
    }

    public int columns() {
        return data.get(3);
    }

    public boolean active() {
        return data.get(4) != 0;
    }

    /** Изменение нагрева за последнюю секунду. */
    public int heatDelta() {
        return data.get(5);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int grid = NuclearReactorBlockEntity.SLOTS;
        if (index < grid) {
            if (!moveItemStackTo(stack, grid, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < grid && !stack.isEmpty(); i++) {
                Slot s = slots.get(i);
                if (!s.hasItem() && s.mayPlace(stack)) {
                    s.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}

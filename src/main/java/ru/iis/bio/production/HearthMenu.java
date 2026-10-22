package ru.iis.bio.production;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.iis.bio.BioContent;

/** Окно очага: слот топлива (80, 53) под пламенем, как у печи; инвентарь игрока (8, 84). */
public class HearthMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    public HearthMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(BioContent.HEARTH_MENU.get(), id);
        this.container = container;
        this.data = data;
        addSlot(new Slot(container, 0, 80, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(0, stack);
            }
        });
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    public static HearthMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return new HearthMenu(id, inventory, new SimpleContainer(1), new SimpleContainerData(2));
    }

    /** Доля оставшегося жара (0–1) для пламени. */
    public float flame() {
        int total = Math.max(1, data.get(1));
        return Math.min(1f, data.get(0) / (float) total);
    }

    public int heatSeconds() {
        return data.get(0) / 20;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!container.canPlaceItem(0, stack) || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}

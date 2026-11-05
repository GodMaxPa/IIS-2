package ru.iis.auto;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.iis.machine.menu.MachineMenu;

/** GT_Container_QuantumChest: вход (80,17), выход (80,53). */
public class QuantumChestMenu extends MachineMenu {
    public static final int DATA = 5;

    public QuantumChestMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(AutoContent.QUANTUM_CHEST_MENU.get(), id, container, data);
        addSlot(new Slot(container, 0, 80, 17));
        addSlot(new OutputSlot(container, 1, 80, 53));
        finish(inventory);
    }

    public static QuantumChestMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return new QuantumChestMenu(id, inventory, new SimpleContainer(2), new SimpleContainerData(DATA));
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return slot == 0;
    }

    public int count() {
        return (data(0) & 0xFFFF) | ((data(1) & 0xFFFF) << 16);
    }

    public int capacity() {
        return (data(2) & 0xFFFF) | ((data(3) & 0xFFFF) << 16);
    }

    public int itemId() {
        return data(4) & 0xFFFF;
    }
}

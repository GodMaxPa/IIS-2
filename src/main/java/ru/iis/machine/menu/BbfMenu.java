package ru.iis.machine.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.iis.machine.IISMachines;
import ru.iis.machine.block.BbfBlockEntity;

/** GT_Container_PrimitiveBlastFurnace: входы (34,16+18i), выходы (86+18i,25). */
public class BbfMenu extends MachineMenu {
    public BbfMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(IISMachines.BBF_MENU.get(), id, container, data);
        for (int i = 0; i < BbfBlockEntity.INPUTS; i++) addSlot(new Slot(container, i, 34, 16 + 18 * i));
        for (int i = 0; i < BbfBlockEntity.OUTPUTS; i++) addSlot(new OutputSlot(container, BbfBlockEntity.INPUTS + i, 86 + 18 * i, 25));
        finish(inventory);
    }

    public static BbfMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return new BbfMenu(id, inventory, new SimpleContainer(6), new SimpleContainerData(3));
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return slot < BbfBlockEntity.INPUTS;
    }

    /** Прогресс в тысячных. */
    public int progress() { return data(0); }
    public boolean formed() { return data(1) != 0; }
    public int secondsLeft() { return data(2); }
}

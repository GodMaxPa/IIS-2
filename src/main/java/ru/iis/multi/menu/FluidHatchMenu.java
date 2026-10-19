package ru.iis.multi.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.iis.machine.menu.MachineMenu;
import ru.iis.multi.MultiContent;

/** GT_Container_BasicTank: ведро сверху (80,17), результат снизу (80,53), жидкость (80,35). */
public class FluidHatchMenu extends MachineMenu {
    public FluidHatchMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(MultiContent.FLUID_HATCH_MENU.get(), id, container, data);
        addSlot(new Slot(container, 0, 80, 17));
        addSlot(new OutputSlot(container, 1, 80, 53));
        finish(inventory);
    }

    public static FluidHatchMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return new FluidHatchMenu(id, inventory, new SimpleContainer(2), new SimpleContainerData(2));
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return slot == 0;
    }

    public int amount() { return data(0); }
    public int fluidId() { return data(1); }
}

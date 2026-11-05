package ru.iis.auto;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.iis.lv.item.BatteryItem;
import ru.iis.machine.menu.MachineMenu;
import ru.iis.multi.MultiContent;

/** GT_Container_BasicMachine для бура и помпы: входы (35,25), (53,25), выходы (107,25), (125,25), батарейка (80,63). */
public class BasicDrillerMenu extends MachineMenu {
    public BasicDrillerMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(AutoContent.BASIC_DRILLER_MENU.get(), id, container, data);
        addSlot(new Slot(container, 0, 35, 25));
        addSlot(new Slot(container, 1, 53, 25));
        addSlot(new OutputSlot(container, 2, 107, 25));
        addSlot(new OutputSlot(container, 3, 125, 25));
        addSlot(new Slot(container, 4, 80, 63));
        finish(inventory);
    }

    public static BasicDrillerMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return new BasicDrillerMenu(id, inventory, new SimpleContainer(BasicDrillerBlockEntity.SLOTS), new SimpleContainerData(BasicDrillerBlockEntity.DATA));
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        if (slot < 2) return stack.is(MultiContent.MINING_PIPE.asItem());
        return slot == BasicDrillerBlockEntity.BATTERY && stack.getItem() instanceof BatteryItem;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 && id != 1) return false;
        if (container instanceof BasicDrillerBlockEntity be) be.toggleTransfer(id);
        return true;
    }

    public int progress() { return data(0); }
    public int maxProgress() { return data(1); }
    public boolean itemTransfer() { return data(2) != 0; }
    public boolean fluidTransfer() { return data(3) != 0; }
    public int fluidAmount() { return (data(4) & 0xFFFF) | ((data(5) & 0xFFFF) << 16); }
    public int fluidId() { return data(6); }
}

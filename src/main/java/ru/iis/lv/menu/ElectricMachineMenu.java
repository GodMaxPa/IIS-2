package ru.iis.lv.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlock;
import ru.iis.lv.item.BatteryItem;
import ru.iis.machine.menu.MachineMenu;

/** GT_Container_BasicMachine: входы, выходы, слот батарейки (80,63). */
public class ElectricMachineMenu extends MachineMenu {
    public final LvMachineType type;

    public ElectricMachineMenu(int id, Inventory inventory, Container container, ContainerData data, LvMachineType type) {
        super(LvContent.ELECTRIC_MACHINE_MENU.get(), id, container, data);
        this.type = type;
        int[][] in = type.inputSlots(), out = type.outputSlots();
        for (int i = 0; i < in.length; i++) addSlot(new Slot(container, i, in[i][0], in[i][1]));
        for (int i = 0; i < out.length; i++) addSlot(new OutputSlot(container, type.inputs + i, out[i][0], out[i][1]));
        addSlot(new Slot(container, type.inputs + type.outputs, 80, 63) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BatteryItem;
            }
        });
        finish(inventory);
    }

    public static ElectricMachineMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        LvMachineType type = inventory.player.level().getBlockState(pos).getBlock() instanceof ElectricMachineBlock b ? b.type : LvMachineType.MACERATOR;
        return new ElectricMachineMenu(id, inventory, new SimpleContainer(type.inputs + type.outputs + 1), new SimpleContainerData(10), type);
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        if (stack.getItem() instanceof BatteryItem) return slot == type.inputs + type.outputs;
        return slot < type.inputs;
    }

    public int progressPermille() { return data(0); }
    public boolean working() { return data(1) != 0; }
    public int energy() { return data(2); }
    public int capacity() { return data(3); }
    public boolean stuttering() { return data(4) != 0; }
    public int fluidAmount() { return data(5); }
    public int fluidId() { return data(6); }
    public int eut() { return data(7); }
    public boolean itemTransfer() { return data(8) != 0; }
    public boolean fluidTransfer() { return data(9) != 0; }

    /** Кнопки автовыгрузки (GT_Container_BasicMachine.slotClick 0/1). */
    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player player, int id) {
        if (id != 0 && id != 1) return false;
        if (container instanceof ru.iis.lv.block.ElectricMachineBlockEntity be) be.toggleTransfer(id);
        return true;
    }
}

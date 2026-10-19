package ru.iis.multi.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.iis.machine.menu.MachineMenu;
import ru.iis.multi.MultiContent;

/** GT_Container_2by2: шина LV, слоты (71..89, 26..44). Выходная шина — только забирать. */
public class ItemBusMenu extends MachineMenu {
    public final boolean input;

    public ItemBusMenu(int id, Inventory inventory, Container container, boolean input) {
        super(MultiContent.ITEM_BUS_MENU.get(), id, container, new SimpleContainerData(0));
        this.input = input;
        int[][] pos = {{71, 26}, {89, 26}, {71, 44}, {89, 44}};
        for (int i = 0; i < 4; i++) {
            addSlot(input ? new Slot(container, i, pos[i][0], pos[i][1]) : new OutputSlot(container, i, pos[i][0], pos[i][1]));
        }
        finish(inventory);
    }

    public static ItemBusMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        boolean input = inventory.player.level().getBlockState(pos).getBlock() instanceof ru.iis.multi.block.HatchBlock h
                && h.type == ru.iis.multi.block.HatchType.INPUT_BUS;
        return new ItemBusMenu(id, inventory, new SimpleContainer(4), input);
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return input;
    }
}

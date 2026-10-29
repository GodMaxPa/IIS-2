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

/**
 * GT_Container_2by2 / 3by3 / 4by4: шина LV — 2×2 (71, 26), MV — 3×3 (62, 17), HV и выше — 4×4 (53, 8); у EV и IV слотов больше
 * (25, 36), но, как в GT, окно показывает первые 16 (D103). Выходная шина — только забирать.
 */
public class ItemBusMenu extends MachineMenu {
    public final boolean input;

    public ItemBusMenu(int id, Inventory inventory, Container container, boolean input) {
        super(MultiContent.ITEM_BUS_MENU.get(), id, container, new SimpleContainerData(0));
        this.input = input;
        int n = Math.min(16, container.getContainerSize()), side = n <= 4 ? 2 : n <= 9 ? 3 : 4;
        int x0 = side == 2 ? 71 : side == 3 ? 62 : 53, y0 = side == 2 ? 26 : side == 3 ? 17 : 8;
        for (int i = 0; i < side * side; i++) {
            int x = x0 + 18 * (i % side), y = y0 + 18 * (i / side);
            addSlot(input ? new Slot(container, i, x, y) : new OutputSlot(container, i, x, y));
        }
        finish(inventory);
    }

    public static ItemBusMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        var state = inventory.player.level().getBlockState(pos);
        boolean input = state.getBlock() instanceof ru.iis.multi.block.HatchBlock h && h.type.kind() == ru.iis.multi.block.HatchType.INPUT_BUS;
        return new ItemBusMenu(id, inventory, new SimpleContainer(ru.iis.multi.block.ItemBusBlockEntity.slots(state)), input);
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return input;
    }
}

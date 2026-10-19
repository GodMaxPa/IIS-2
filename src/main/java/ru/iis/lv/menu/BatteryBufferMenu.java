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
import ru.iis.lv.block.BatteryBufferBlock;
import ru.iis.lv.item.BatteryItem;
import ru.iis.machine.menu.MachineMenu;

/** GT_Container_1by1 (80,35) и GT_Container_2by2 (71..89, 26..44). */
public class BatteryBufferMenu extends MachineMenu {
    public final int slots;

    public BatteryBufferMenu(int id, Inventory inventory, Container container, ContainerData data, int slots) {
        super(LvContent.BATTERY_BUFFER_MENU.get(), id, container, data);
        this.slots = slots;
        int[][] pos = slots == 1 ? new int[][]{{80, 35}} : new int[][]{{71, 26}, {89, 26}, {71, 44}, {89, 44}};
        for (int i = 0; i < slots; i++) {
            addSlot(new Slot(container, i, pos[i][0], pos[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof BatteryItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        finish(inventory);
    }

    public static BatteryBufferMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int n = inventory.player.level().getBlockState(pos).getBlock() instanceof BatteryBufferBlock b ? b.slots : 1;
        return new BatteryBufferMenu(id, inventory, new SimpleContainer(n), new SimpleContainerData(2), n);
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return stack.getItem() instanceof BatteryItem;
    }

    public int energy() { return data(0); }
    public int capacity() { return data(1); }
}

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

/** GT_Container_1by1 (80,35), 2by2 (71..89, 26..44), 3by3 (62..98, 17..53), 4by4 (53..107, 8..62). */
public class BatteryBufferMenu extends MachineMenu {
    public final int slots;

    public BatteryBufferMenu(int id, Inventory inventory, Container container, ContainerData data, int slots) {
        super(LvContent.BATTERY_BUFFER_MENU.get(), id, container, data);
        this.slots = slots;
        int side = (int) Math.round(Math.sqrt(slots));
        int x0 = side == 1 ? 80 : side == 2 ? 71 : side == 3 ? 62 : 53, y0 = side == 1 ? 35 : side == 2 ? 26 : side == 3 ? 17 : 8;
        for (int i = 0; i < slots; i++) {
            addSlot(new Slot(container, i, x0 + 18 * (i % side), y0 + 18 * (i / side)) {
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
        return new BatteryBufferMenu(id, inventory, new SimpleContainer(n), new SimpleContainerData(4), n);
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return stack.getItem() instanceof BatteryItem;
    }

    public int energy() { return (data(0) & 0xFFFF) | ((data(1) & 0xFFFF) << 16); }
    public int capacity() { return (data(2) & 0xFFFF) | ((data(3) & 0xFFFF) << 16); }
}

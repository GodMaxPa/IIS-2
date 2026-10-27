package ru.iis.multi.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.iis.lv.item.ProgrammedCircuitItem;
import ru.iis.machine.menu.MachineMenu;
import ru.iis.multi.MultiContent;

/**
 * Экран контроллера со слотом (GT_Container_MultiMachine: слот 1 в (152, 5)): программная схема большого котла (D88).
 * Сведения 0–9 — общие (как у {@link EbfMenu}), дальше — свои у каждой машины.
 */
public class ControllerMenu extends MachineMenu {
    /** Котёл: 10 — пар л/т, 11 — запас горения, 12 — настройка схемы, 13 — кислород. */
    public static final int BOILER_DATA = 14;

    public ControllerMenu(MenuType<?> type, int id, Inventory inventory, Container container, ContainerData data) {
        super(type, id, container, data);
        addSlot(new Slot(container, 0, 152, 5));
        finish(inventory);
    }

    public static ControllerMenu boiler(int id, Inventory inventory, Container container, ContainerData data) {
        return new ControllerMenu(MultiContent.LARGE_BOILER_MENU.get(), id, inventory, container, data);
    }

    public static ControllerMenu boilerClient(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return boiler(id, inventory, new SimpleContainer(1), new SimpleContainerData(BOILER_DATA));
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return stack.getItem() instanceof ProgrammedCircuitItem;
    }

    public boolean formed() { return data(0) != 0; }
    public int okMask() { return data(1); }
    public int efficiency() { return data(2); }
    public boolean works() { return data(8) != 0; }
    public boolean mufflerBlocked() { return data(9) != 0; }
    public int extra(int i) { return data(10 + i); }
}

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
 * Экран контроллера со слотом (GT_Container_MultiMachine: слот 1 в (152, 5)): программная схема большого котла (D88),
 * ротор большой турбины (D89), программная схема теплообменника (D105).
 * Сведения 0–9 — общие (как у {@link EbfMenu}), дальше — свои у каждой машины.
 */
public class ControllerMenu extends MachineMenu {
    /** Котёл: 10 — пар л/т, 11 — запас горения, 12 — настройка схемы, 13 — кислород. */
    public static final int BOILER_DATA = 14;
    /** Турбина: 10 — отдача EU/t, 11 — оптимальный поток, 12 — пар в люке, 13 — износ ротора %, 14 — ротор на месте. */
    public static final int TURBINE_DATA = 15;
    /** Теплообменник (D105): 10 — пар л/т, 11 — перегретый, 12 — порог перегрева л/с, 13 — настройка схемы. */
    public static final int HEAT_EXCHANGER_DATA = 14;
    /** Обрабатывающий массив (D105): 10 — сколько машин сработало, 11 — машина в слоте годится. */
    public static final int ARRAY_DATA = 12;
    /** Буровые (D106): 10 — глубина, 11 — этап (спуск, дно, подъём), 12 — труб в контроллере. */
    public static final int DRILL_DATA = 13;

    public ControllerMenu(MenuType<?> type, int id, Inventory inventory, Container container, ContainerData data) {
        super(type, id, container, data);
        // у термоядерного компьютера слота нет
        if (container.getContainerSize() > 0) addSlot(new Slot(container, 0, 152, 5));
        finish(inventory);
    }

    public static ControllerMenu boiler(int id, Inventory inventory, Container container, ContainerData data) {
        return new ControllerMenu(MultiContent.LARGE_BOILER_MENU.get(), id, inventory, container, data);
    }

    public static ControllerMenu boilerClient(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return boiler(id, inventory, new SimpleContainer(1), new SimpleContainerData(BOILER_DATA));
    }

    public static ControllerMenu turbine(int id, Inventory inventory, Container container, ContainerData data) {
        return new ControllerMenu(MultiContent.LARGE_TURBINE_MENU.get(), id, inventory, container, data);
    }

    public static ControllerMenu turbineClient(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return turbine(id, inventory, new SimpleContainer(1), new SimpleContainerData(TURBINE_DATA));
    }

    public static ControllerMenu processingArray(int id, Inventory inventory, Container container, ContainerData data) {
        return new ControllerMenu(MultiContent.PROCESSING_ARRAY_MENU.get(), id, inventory, container, data);
    }

    public static ControllerMenu processingArrayClient(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return processingArray(id, inventory, new SimpleContainer(1), new SimpleContainerData(ARRAY_DATA));
    }

    /** Сборочная линия (D110): слот флешки, общие данные контроллера. */
    public static ControllerMenu fusion(int id, Inventory inventory, Container container, ContainerData data) {
        return new ControllerMenu(MultiContent.FUSION_MENU.get(), id, inventory, container, data);
    }

    public static ControllerMenu fusionClient(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return fusion(id, inventory, new SimpleContainer(0), new SimpleContainerData(10));
    }

    public static ControllerMenu assemblyLine(int id, Inventory inventory, Container container, ContainerData data) {
        return new ControllerMenu(MultiContent.ASSEMBLY_LINE_MENU.get(), id, inventory, container, data);
    }

    public static ControllerMenu assemblyLineClient(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return assemblyLine(id, inventory, new SimpleContainer(1), new SimpleContainerData(10));
    }

    public static ControllerMenu drill(int id, Inventory inventory, Container container, ContainerData data) {
        return new ControllerMenu(MultiContent.DRILL_MENU.get(), id, inventory, container, data);
    }

    public static ControllerMenu drillClient(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return drill(id, inventory, new SimpleContainer(1), new SimpleContainerData(DRILL_DATA));
    }

    public static ControllerMenu heatExchanger(int id, Inventory inventory, Container container, ContainerData data) {
        return new ControllerMenu(MultiContent.HEAT_EXCHANGER_MENU.get(), id, inventory, container, data);
    }

    public static ControllerMenu heatExchangerClient(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return heatExchanger(id, inventory, new SimpleContainer(1), new SimpleContainerData(HEAT_EXCHANGER_DATA));
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return stack.getItem() instanceof ProgrammedCircuitItem || stack.getItem() instanceof ru.iis.multi.TurbineRotorItem
                || ru.iis.multi.block.ProcessingArrayBlockEntity.machine(stack) != null || stack.is(MultiContent.MINING_PIPE.asItem())
                || stack.getItem() instanceof ru.iis.lv.item.DataStickItem;
    }

    public boolean formed() { return data(0) != 0; }
    public int okMask() { return data(1); }
    public int efficiency() { return data(2); }
    public int eut() { return data(5); }
    public boolean works() { return data(8) != 0; }
    public boolean mufflerBlocked() { return data(9) != 0; }
    public int extra(int i) { return data(10 + i); }
}

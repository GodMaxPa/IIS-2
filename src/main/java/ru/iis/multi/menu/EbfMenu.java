package ru.iis.multi.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import ru.iis.machine.menu.MachineMenu;
import ru.iis.multi.MultiContent;

/** Экран контроллера: только сведения (как GT_GUIContainer_MultiMachine). */
public class EbfMenu extends MachineMenu {
    public EbfMenu(int id, Inventory inventory, ContainerData data) {
        super(MultiContent.EBF_MENU.get(), id, new SimpleContainer(0), data);
        finish(inventory);
    }

    public static EbfMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return new EbfMenu(id, inventory, new SimpleContainerData(10));
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return false;
    }

    public boolean formed() { return data(0) != 0; }
    public int okMask() { return data(1); }
    public int efficiency() { return data(2); }
    public int progress() { return data(3); }
    public int maxProgress() { return data(4); }
    public int eut() { return data(5); }
    public int heat() { return data(6); }
    public int voltage() { return data(7); }
    public boolean works() { return data(8) != 0; }
    public boolean mufflerBlocked() { return data(9) != 0; }
}

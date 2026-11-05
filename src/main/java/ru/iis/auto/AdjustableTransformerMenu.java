package ru.iis.auto;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import ru.iis.machine.menu.MachineMenu;
import ru.iis.multi.logic.BlastLogic;

/** GT_Container_DigitalTransformer: кнопка id = (столбец 0/1) × 3 + ряд, с Shift + 6. */
public class AdjustableTransformerMenu extends MachineMenu {
    public AdjustableTransformerMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(AutoContent.ADJUSTABLE_TRANSFORMER_MENU.get(), id, container, data);
        finish(inventory);
    }

    public static AdjustableTransformerMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return new AdjustableTransformerMenu(id, inventory, new SimpleContainer(0), new SimpleContainerData(AdjustableTransformerBlockEntity.DATA));
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= 12) return false;
        boolean shift = id >= 6;
        int b = id % 6;
        if (container instanceof AdjustableTransformerBlockEntity be) be.adjust(b % 3, b >= 3, shift);
        return true;
    }

    public int iAmp() { return data(0); }
    public long oVoltage() { return BlastLogic.V[data(1)]; }
    public int oAmp() { return data(2); }
    public long voltage() { return BlastLogic.V[data(3)]; }
}

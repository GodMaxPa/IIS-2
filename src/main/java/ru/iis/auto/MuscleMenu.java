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
import ru.iis.machine.menu.MachineMenu;

/** GT_Container_PrimitiveMiner: 4 входа в столбик (8,8), 6 × 4 выхода с (62,8), между ними кнопка «Работать». */
public class MuscleMenu extends MachineMenu {
    public MuscleMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(AutoContent.MUSCLE_MENU.get(), id, container, data);
        for (int i = 0; i < MuscleBlockEntity.INPUTS; i++) addSlot(new Slot(container, i, 8, 8 + i * 18));
        for (int y = 0; y < 4; y++) for (int x = 0; x < 6; x++) addSlot(new OutputSlot(container, MuscleBlockEntity.INPUTS + y * 6 + x, 62 + x * 18, 8 + y * 18));
        finish(inventory);
    }

    public static MuscleMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return new MuscleMenu(id, inventory, new SimpleContainer(MuscleBlockEntity.SLOTS), new SimpleContainerData(MuscleBlockEntity.DATA));
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return slot < MuscleBlockEntity.INPUTS;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0) return false;
        if (container instanceof MuscleBlockEntity be) be.work(player);
        return true;
    }

    public int power() { return data(0); }
    public int progress() { return data(1); }
    public int maxProgress() { return data(2); }
    public int depth() { return data(3); }
    public int state() { return data(4); }
    public boolean digger() { return data(5) != 0; }
}

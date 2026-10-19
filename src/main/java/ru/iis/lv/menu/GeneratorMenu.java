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
import ru.iis.lv.GeneratorType;
import ru.iis.lv.LvContent;
import ru.iis.lv.block.GeneratorBlock;
import ru.iis.machine.menu.MachineMenu;

/** GT_Container_BasicTank: ведро с топливом (80,17), пустое ведро (80,53). */
public class GeneratorMenu extends MachineMenu {
    public final GeneratorType type;

    public GeneratorMenu(int id, Inventory inventory, Container container, ContainerData data, GeneratorType type) {
        super(LvContent.GENERATOR_MENU.get(), id, container, data);
        this.type = type;
        addSlot(new Slot(container, 0, 80, 17));
        addSlot(new OutputSlot(container, 1, 80, 53));
        finish(inventory);
    }

    public static GeneratorMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        GeneratorType type = inventory.player.level().getBlockState(pos).getBlock() instanceof GeneratorBlock b ? b.type : GeneratorType.STEAM_TURBINE;
        return new GeneratorMenu(id, inventory, new SimpleContainer(2), new SimpleContainerData(4), type);
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return slot == 0;
    }

    public int energy() { return data(0); }
    public int capacity() { return data(1); }
    public int fluidAmount() { return data(2); }
    public int fluidId() { return data(3); }
}

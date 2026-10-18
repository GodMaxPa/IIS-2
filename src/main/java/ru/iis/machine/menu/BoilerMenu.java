package ru.iis.machine.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.iis.machine.BoilerType;
import ru.iis.machine.IISMachines;
import ru.iis.machine.block.BoilerBlock;
import ru.iis.machine.block.BoilerBlockEntity;

/** GT_Container_Boiler: топливо (116,62), вода (44,26), пустая ёмкость (44,62), зола (116,26). */
public class BoilerMenu extends MachineMenu {
    public final BoilerType type;

    public BoilerMenu(int id, Inventory inventory, Container container, ContainerData data, BoilerType type) {
        super(IISMachines.BOILER_MENU.get(), id, container, data);
        this.type = type;
        addSlot(new Slot(container, BoilerBlockEntity.SLOT_WATER_IN, 44, 26));
        addSlot(new OutputSlot(container, BoilerBlockEntity.SLOT_WATER_OUT, 44, 62));
        addSlot(new Slot(container, BoilerBlockEntity.SLOT_FUEL, 116, 62));
        addSlot(new OutputSlot(container, BoilerBlockEntity.SLOT_ASH, 116, 26));
        finish(inventory);
    }

    public static BoilerMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BoilerType type = inventory.player.level().getBlockState(pos).getBlock() instanceof BoilerBlock b ? b.type : BoilerType.COAL_BRONZE;
        return new BoilerMenu(id, inventory, new SimpleContainer(4), new SimpleContainerData(4), type);
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        if (slot == BoilerBlockEntity.SLOT_WATER_IN) return stack.is(Items.WATER_BUCKET);
        if (slot == BoilerBlockEntity.SLOT_FUEL) return BoilerBlockEntity.isFuel(type, stack);
        return false;
    }

    public int temperature() { return data(0); }
    public int water() { return data(1); }
    public int steam() { return data(2); }
    public int energy() { return data(3); }
}

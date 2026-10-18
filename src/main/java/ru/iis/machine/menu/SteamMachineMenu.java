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
import ru.iis.machine.IISMachines;
import ru.iis.machine.SteamMachineType;
import ru.iis.machine.block.SteamMachineBlock;

/** GT_Container_BasicMachine: вход (53,25) или (35,25)+(53,25), выход (107,25). */
public class SteamMachineMenu extends MachineMenu {
    public final SteamMachineType type;
    public final SteamMachineType.Tier tier;

    public SteamMachineMenu(int id, Inventory inventory, Container container, ContainerData data, SteamMachineType type, SteamMachineType.Tier tier) {
        super(IISMachines.STEAM_MACHINE_MENU.get(), id, container, data);
        this.type = type;
        this.tier = tier;
        if (type.inputs == 1) {
            addSlot(new Slot(container, 0, 53, 25));
        } else {
            addSlot(new Slot(container, 0, 35, 25));
            addSlot(new Slot(container, 1, 53, 25));
        }
        addSlot(new OutputSlot(container, type.inputs, 107, 25));
        finish(inventory);
    }

    public static SteamMachineMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        SteamMachineType type = SteamMachineType.MACERATOR;
        SteamMachineType.Tier tier = SteamMachineType.Tier.BRONZE;
        if (inventory.player.level().getBlockState(pos).getBlock() instanceof SteamMachineBlock b) {
            type = b.type;
            tier = b.tier;
        }
        return new SteamMachineMenu(id, inventory, new SimpleContainer(type.inputs + 1), new SimpleContainerData(4), type, tier);
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return slot < type.inputs;
    }

    public int progress() { return data(0); }
    public int maxProgress() { return data(1); }
    public int steam() { return data(2); }
    public boolean stuttering() { return data(3) != 0; }
}

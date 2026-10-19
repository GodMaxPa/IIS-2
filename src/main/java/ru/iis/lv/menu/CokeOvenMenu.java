package ru.iis.lv.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.iis.lv.LvContent;
import ru.iis.machine.menu.MachineMenu;

/** Коксовая печь: вход (44,35), выход (98,35), ведро (152,17) → (152,53), бак креозота (120..136, 15..72). */
public class CokeOvenMenu extends MachineMenu {
    public CokeOvenMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(LvContent.COKE_OVEN_MENU.get(), id, container, data);
        addSlot(new Slot(container, 0, 44, 35));
        addSlot(new OutputSlot(container, 1, 98, 35));
        addSlot(new Slot(container, 2, 152, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.BUCKET);
            }
        });
        addSlot(new OutputSlot(container, 3, 152, 53));
        finish(inventory);
    }

    public static CokeOvenMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return new CokeOvenMenu(id, inventory, new SimpleContainer(4), new SimpleContainerData(3));
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return stack.is(Items.BUCKET) ? slot == 2 : slot == 0;
    }

    public int progressPermille() { return data(0); }
    public int creosote() { return data(1) * 2; }
    public boolean formed() { return data(2) != 0; }
}

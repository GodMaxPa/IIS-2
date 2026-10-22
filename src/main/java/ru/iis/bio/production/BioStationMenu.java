package ru.iis.bio.production;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.iis.bio.BioContent;
import ru.iis.bio.item.BioItems;

/**
 * Интерфейс места Био. Раскладка: входы (8 + 18·i, 17), инструмент (8, 44), выходы (30 + 18·i, 44), баки — столбцы справа
 * (118 + 11·k, 16..62), инвентарь игрока (8, 84). Кнопка 0 — сменить режим.
 */
public class BioStationMenu extends AbstractContainerMenu {
    public final Station station;
    public final BlockPos pos;
    private final Container container;
    private final ContainerData data;

    public BioStationMenu(int id, Inventory inventory, Container container, ContainerData data, Station station, BlockPos pos) {
        super(BioContent.STATION_MENU.get(), id);
        this.station = station;
        this.pos = pos;
        this.container = container;
        this.data = data;
        for (int i = 0; i < station.inputs; i++) addSlot(new Slot(container, i, 8 + 18 * i, 17));
        for (int i = 0; i < station.outputs; i++) {
            addSlot(new Slot(container, station.inputs + i, 30 + 18 * i, 44) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        if (station.toolSlot) {
            addSlot(new Slot(container, station.toolIndex(), 8, 44) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return StationLogic.isTool(stack);
                }
            });
        }
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    public static BioStationMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        Station st = Station.values()[buf.readVarInt()];
        BlockPos pos = buf.readBlockPos();
        return new BioStationMenu(id, inventory, new SimpleContainer(st.slots()), new SimpleContainerData(StationLogic.dataCount(st)), st, pos);
    }

    public int progressPermille() {
        return data.get(0);
    }

    public StationLogic.Status status() {
        int s = data.get(1);
        StationLogic.Status[] v = StationLogic.Status.values();
        return s >= 0 && s < v.length ? v[s] : StationLogic.Status.IDLE;
    }

    public int mode() {
        return data.get(2);
    }

    public int tankFluid(int k) {
        return data.get(3 + 2 * k);
    }

    public int tankAmount(int k) {
        return data.get(4 + 2 * k);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && container instanceof StationLogic logic) {
            logic.cycleMode();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int own = station.slots();
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean tool = StationLogic.isTool(stack);
            boolean moved = false;
            if (tool && station.toolSlot) moved = moveItemStackTo(stack, own - 1, own, false);
            if (!moved) moved = moveItemStackTo(stack, 0, station.inputs, false);
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}

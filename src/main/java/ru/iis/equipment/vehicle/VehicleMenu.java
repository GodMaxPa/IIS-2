package ru.iis.equipment.vehicle;

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
import org.jspecify.annotations.Nullable;
import ru.iis.equipment.EquipmentContent;

/** Окно машины: слот топлива, запас и прочность, груз, если есть. */
public class VehicleMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, TOP = 58;
    public final int rows;
    private final Container container;
    private final @Nullable Driveable vehicle;
    private final ContainerData data;

    public VehicleMenu(int id, Inventory inv, Container container, Driveable vehicle) {
        this(id, inv, container, vehicle, vehicle.kind.cargoRows, new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> (int) (1000 * vehicle.fuel() / vehicle.kind.tank);
                    case 1 -> (int) (1000 * vehicle.vehicleHealth() / vehicle.kind.health);
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() {
                return 2;
            }
        });
    }

    private VehicleMenu(int id, Inventory inv, Container container, @Nullable Driveable vehicle, int rows, ContainerData data) {
        super(EquipmentContent.VEHICLE_MENU.get(), id);
        this.container = container;
        this.vehicle = vehicle;
        this.rows = rows;
        this.data = data;
        addDataSlots(data);
        addSlot(new Slot(container, 0, 26, 26));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(container, 1 + c + r * 9, 8 + c * 18, TOP + r * 18));
        }
        addStandardInventorySlots(inv, 8, invY(rows));
    }

    public static int invY(int rows) {
        return TOP + rows * 18 + 14;
    }

    public static VehicleMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        int entity = buf.readVarInt();
        int rows = buf.readVarInt();
        return new VehicleMenu(id, inv, new SimpleContainer(1 + rows * 9), inv.player.level().getEntity(entity) instanceof Driveable d ? d : null, rows, new SimpleContainerData(2));
    }

    public double fuel() {
        return data.get(0) / 1000.0;
    }

    public double health() {
        return data.get(1) / 1000.0;
    }

    public @Nullable Driveable vehicle() {
        return vehicle;
    }

    @Override
    public boolean stillValid(Player player) {
        return vehicle != null && !vehicle.isRemoved() && player.distanceToSqr(vehicle) < 12 * 12 + vehicle.getBbWidth() * vehicle.getBbWidth();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int own = 1 + rows * 9;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean fuel = vehicle != null && vehicle.canFuel(stack);
            if (fuel && moveItemStackTo(stack, 0, 1, false)) {
                // ушло в слот топлива
            } else if (rows == 0 || !moveItemStackTo(stack, 1, own, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}

package ru.iis.equipment.vehicle;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import ru.iis.equipment.EquipmentContent;

/** Окно машины: слот топлива, запас и прочность, багажник. Большой багажник идёт страницами по шесть рядов. */
public class VehicleMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, TOP = 58, PAGE_ROWS = 6;
    public final int cargoSlots;
    public final int rowsOnPage;
    private final Container container;
    private final @Nullable Driveable vehicle;
    private final ContainerData data;
    private final DataSlot page = DataSlot.standalone();

    public VehicleMenu(int id, Inventory inv, Container container, Driveable vehicle) {
        this(id, inv, container, vehicle, vehicle.cargoSlots(), new ContainerData() {
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

    private VehicleMenu(int id, Inventory inv, Container container, @Nullable Driveable vehicle, int cargoSlots, ContainerData data) {
        super(EquipmentContent.VEHICLE_MENU.get(), id);
        this.container = container;
        this.vehicle = vehicle;
        this.cargoSlots = cargoSlots;
        this.rowsOnPage = Math.min(PAGE_ROWS, (cargoSlots + 8) / 9);
        this.data = data;
        addDataSlots(data);
        addDataSlot(page);
        addSlot(new Slot(container, 0, 26, 26));
        for (int i = 0; i < cargoSlots; i++) {
            final int row = i / 9;
            addSlot(new Slot(container, 1 + i, 8 + i % 9 * 18, TOP + row % PAGE_ROWS * 18) {
                @Override
                public boolean isActive() {
                    return row / PAGE_ROWS == page();
                }
            });
        }
        addStandardInventorySlots(inv, 8, invY(rowsOnPage));
    }

    public static int invY(int rows) {
        return TOP + rows * 18 + 14;
    }

    public static VehicleMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        int entity = buf.readVarInt();
        int slots = buf.readVarInt();
        return new VehicleMenu(id, inv, new SimpleContainer(1 + slots), inv.player.level().getEntity(entity) instanceof Driveable d ? d : null, slots, new SimpleContainerData(2));
    }

    public double fuel() {
        return data.get(0) / 1000.0;
    }

    public double health() {
        return data.get(1) / 1000.0;
    }

    public int page() {
        return page.get();
    }

    public int pages() {
        return Math.max(1, ((cargoSlots + 8) / 9 + PAGE_ROWS - 1) / PAGE_ROWS);
    }

    public @Nullable Driveable vehicle() {
        return vehicle;
    }

    /** Кнопки листания: 0 назад, 1 вперёд. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        int p = Math.floorMod(page() + (id == 0 ? -1 : 1), pages());
        page.set(p);
        return true;
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
        int own = 1 + cargoSlots;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean fuel = vehicle != null && vehicle.canFuel(stack);
            if (fuel && moveItemStackTo(stack, 0, 1, false)) {
                // ушло в слот топлива
            } else if (cargoSlots == 0 || !moveItemStackTo(stack, 1, own, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}

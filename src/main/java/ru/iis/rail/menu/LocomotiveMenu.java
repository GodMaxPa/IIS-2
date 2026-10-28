package ru.iis.rail.menu;

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
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import ru.iis.fluid.CapsuleItem;
import ru.iis.rail.RailContent;
import ru.iis.rail.cart.Locomotive;
import ru.iis.rail.cart.SteamLocomotive;

/**
 * Окно локомотива (GuiLocomotive Railcraft): режим и скорость кнопками, у паровоза — вода, пар, нагрев, слоты воды,
 * горения и запаса топлива; у электровоза — заряд. Данные: 0 режим, 1 скорость, 2 вода, 3 пар, 4 нагрев °C,
 * 5 горит ли топка, 6 заряд.
 */
public class LocomotiveMenu extends AbstractContainerMenu {
    public static final int INV_Y = 102;

    private final @Nullable Locomotive loco;
    private final ContainerData data;
    public final boolean steam;
    private final int locoSlots;

    public LocomotiveMenu(int id, Inventory inventory, @Nullable Locomotive loco, ContainerData data) {
        this(id, inventory, loco, data, loco instanceof SteamLocomotive s ? s.inventory : null);
    }

    private LocomotiveMenu(int id, Inventory inventory, @Nullable Locomotive loco, ContainerData data, @Nullable Container inv) {
        super(RailContent.LOCO_MENU.get(), id);
        this.loco = loco;
        this.data = data;
        this.steam = inv != null;
        if (inv != null) {
            addSlot(new Slot(inv, SteamLocomotive.SLOT_WATER_IN, 62, 48) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    return isWater(s);
                }
            });
            addSlot(new Slot(inv, SteamLocomotive.SLOT_WATER_OUT, 62, 76) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    return false;
                }
            });
            addSlot(fuelSlot(inv, SteamLocomotive.SLOT_BURN, 92, 76, inventory.player));
            for (int i = 0; i < 3; i++) addSlot(fuelSlot(inv, SteamLocomotive.SLOT_STOCK + i, 116 + 18 * i, 76, inventory.player));
        }
        locoSlots = slots.size();
        addStandardInventorySlots(inventory, 8, INV_Y);
        addDataSlots(data);
    }

    private static Slot fuelSlot(Container inv, int index, int x, int y, Player player) {
        return new Slot(inv, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack s) {
                return player.level().fuelValues().burnDuration(s) > 0;
            }
        };
    }

    static boolean isWater(ItemStack s) {
        return s.is(Items.WATER_BUCKET) || s.getItem() instanceof CapsuleItem && CapsuleItem.content(s).fluidValue() == net.minecraft.world.level.material.Fluids.WATER;
    }

    public static LocomotiveMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean steam = buf.readBoolean();
        Locomotive loco = inventory.player.level().getEntity(entityId) instanceof Locomotive l ? l : null;
        return new LocomotiveMenu(id, inventory, loco, new SimpleContainerData(7), steam ? new SimpleContainer(SteamLocomotive.SLOTS) : null);
    }

    public Locomotive.Mode mode() {
        return Locomotive.Mode.values()[Math.floorMod(data.get(0), 3)];
    }

    public Locomotive.Speed speed() {
        return Locomotive.Speed.values()[Math.floorMod(data.get(1), 5)];
    }

    public int water() {
        return data.get(2);
    }

    public int steamAmount() {
        return data.get(3);
    }

    public int heat() {
        return data.get(4);
    }

    public boolean burning() {
        return data.get(5) != 0;
    }

    public int charge() {
        return data.get(6);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (loco == null || id < 0 || id >= 8) return false;
        loco.pressButton(id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < locoSlots) {
            if (!moveItemStackTo(stack, locoSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (locoSlots == 0) {
            return ItemStack.EMPTY;
        } else if (isWater(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (player.level().fuelValues().burnDuration(stack) > 0) {
            if (!moveItemStackTo(stack, 2, locoSlots, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return loco == null || loco.isAlive() && player.distanceToSqr(loco) < 64;
    }
}

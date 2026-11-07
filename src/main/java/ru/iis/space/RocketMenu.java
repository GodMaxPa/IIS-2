package ru.iis.space;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Груз ракеты: 0, 18, 36 или 54 ячейки. */
public class RocketMenu extends AbstractContainerMenu {
    public final int rows, tier;
    private final int cargoSlots;
    private final net.minecraft.world.inventory.DataSlot target;
    @Nullable private final Entity rocket;

    public RocketMenu(int id, Inventory inventory, Container cargo, @Nullable Entity rocket) {
        this(id, inventory, cargo, rocket, rocket instanceof RocketEntity r ? r.tier() : 1);
    }

    private RocketMenu(int id, Inventory inventory, Container cargo, @Nullable Entity rocket, int tier) {
        super(SpaceContent.ROCKET_MENU.get(), id);
        this.rocket = rocket;
        this.tier = tier;
        target = addDataSlot(rocket instanceof RocketEntity r ? new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return r.target();
            }

            @Override
            public void set(int value) {
                r.setTarget(value);
            }
        } : net.minecraft.world.inventory.DataSlot.standalone());
        cargoSlots = cargo.getContainerSize();
        rows = cargoSlots / 9;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(cargo, c + r * 9, 8 + c * 18, 18 + r * 18));
        }
        addStandardInventorySlots(inventory, 8, 32 + rows * 18);
    }

    public static RocketMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        int cargo = buf.readVarInt();
        return new RocketMenu(id, inventory, new SimpleContainer(cargo), null, buf.readVarInt());
    }

    public int target() {
        return target.get();
    }

    /** Кнопка окна: цель 2-го уровня с Земли. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (tier < 2) return false;
        // D134: у 3-го уровня третья цель — астероиды
        target.set((target.get() + 1) % (tier >= 3 ? 3 : 2));
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return rocket == null || (!rocket.isRemoved() && player.distanceToSqr(rocket) < 100);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        boolean moved = index < cargoSlots ? moveItemStackTo(stack, cargoSlots, slots.size(), true) : moveItemStackTo(stack, 0, cargoSlots, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}

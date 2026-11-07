package ru.iis.space;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Сборка ракет (GC SchematicRocketT1): обтекатель, 8 сверхпрочных покрытий (Т2 дают ракету 2-го уровня), 4 стабилизатора, двигатель;
 * до трёх сундуков дают по 18 ячеек груза. Детали берутся из слотов целиком при сборке.
 */
public class NasaWorkbenchMenu extends AbstractContainerMenu {
    public static final int NOSE = 0, PLATES = 1, FINS = 9, ENGINE = 13, CHESTS = 14, PARTS = 17, RESULT = 17;
    public static final int WIDTH = 176, HEIGHT = 200, INV_Y = 118;
    /** Положение слотов: корпус столбиком 2×4 посередине, стабилизаторы по бокам, двигатель снизу, сундуки справа. */
    private static final int[][] POS = {{53, 18},
            {44, 36}, {62, 36}, {44, 54}, {62, 54}, {44, 72}, {62, 72}, {44, 90}, {62, 90},
            {26, 72}, {80, 72}, {26, 90}, {80, 90}, {53, 108 - 18 + 0}, {116, 36}, {134, 36}, {152, 36}};

    private final Container parts = new SimpleContainer(PARTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            NasaWorkbenchMenu.this.refresh();
        }
    };
    private final Container result = new SimpleContainer(1);

    public NasaWorkbenchMenu(int id, Inventory inventory) {
        super(SpaceContent.NASA_MENU.get(), id);
        POS[ENGINE] = new int[]{98, 90};
        for (int i = 0; i < PARTS; i++) {
            final int slot = i;
            addSlot(new Slot(parts, i, POS[i][0], POS[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(required(slot)) || slot >= PLATES && slot < FINS && stack.is(SpaceContent.HEAVY_PLATING_T2.get());
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addSlot(new Slot(result, 0, 134, 72) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                for (int i = 0; i < PARTS; i++) parts.setItem(i, ItemStack.EMPTY);
                refresh();
                super.onTake(player, stack);
            }
        });
        addStandardInventorySlots(inventory, 8, INV_Y);
    }

    public static Item required(int slot) {
        if (slot == NOSE) return SpaceContent.NOSE_CONE.get();
        if (slot < FINS) return SpaceContent.HEAVY_PLATING.get();
        if (slot < ENGINE) return SpaceContent.ROCKET_FINS.get();
        if (slot == ENGINE) return SpaceContent.ROCKET_ENGINE_T1.get();
        return Items.CHEST;
    }

    /** Что собирается из этих деталей; пусто, если не хватает обязательных. */
    public static ItemStack assemble(Container parts) {
        int chests = 0;
        // D131: восемь покрытий Т2 вместо обычных дают ракету 2-го уровня; вперемешку не собирается
        boolean t2 = parts.getItem(PLATES).is(SpaceContent.HEAVY_PLATING_T2.get());
        for (int i = 0; i < PARTS; i++) {
            boolean ok = i >= PLATES && i < FINS && t2 ? parts.getItem(i).is(SpaceContent.HEAVY_PLATING_T2.get()) : parts.getItem(i).is(required(i));
            if (i >= CHESTS) {
                if (ok) chests++;
            } else if (!ok) {
                return ItemStack.EMPTY;
            }
        }
        ItemStack rocket = new ItemStack(t2 ? SpaceContent.ROCKET_T2.get() : SpaceContent.ROCKET_T1.get());
        rocket.set(SpaceContent.ROCKET_DATA.get(), new RocketItem.Stored(chests * 18, 0));
        return rocket;
    }

    private void refresh() {
        result.setItem(0, assemble(parts));
        broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, parts);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int first = PARTS + 1, last = slots.size();
        if (index <= RESULT) {
            if (!moveItemStackTo(stack, first, last, true)) return ItemStack.EMPTY;
            if (index == RESULT) slot.onTake(player, copy);
        } else {
            boolean moved = false;
            for (int i = 0; i < PARTS && !stack.isEmpty(); i++) {
                Slot part = slots.get(i);
                if (!part.hasItem() && part.mayPlace(stack)) {
                    part.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}

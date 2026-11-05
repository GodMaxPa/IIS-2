package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.iis.machine.menu.MachineMenu;

/**
 * Экраны автоматики (GT_Container_ChestBuffer, _SuperBuffer, _Filter, _TypeFilter, _Regulator, _ItemDistributor): раскладка слотов
 * как в GT. Образцы — без настоящих предметов: клик кладёт копию, у регулятора число в образце — размер порции.
 */
public class AutomationMenu extends MachineMenu {
    /** Кнопки по порядку: выдача энергии, сигнал «полон», инверсия сигнала, инверсия фильтра, игнор NBT; 5/6 — вид фильтра; 100+ и 200+ — слот регулятора. */
    public static final int TYPE_PREV = 5, TYPE_NEXT = 6, SLOT_DOWN = 100, SLOT_UP = 200;

    public final AutomationType kind;
    private final Container real;
    private final int ghostStart, ghostEnd;

    public AutomationMenu(int id, Inventory inventory, Container container, ContainerData data, AutomationType kind) {
        // у сверхбуфера в экране GT нет слотов: 256 ячеек видны только трубам
        super(AutoContent.AUTOMATION_MENU.get(), id, kind == AutomationType.SUPER_BUFFER ? new SimpleContainer(0) : container, data);
        this.kind = kind;
        this.real = container;
        switch (kind) {
            case CHEST_BUFFER, ITEM_DISTRIBUTOR -> {
                for (int y = 0; y < 3; y++) for (int x = 0; x < 9; x++) addSlot(new Slot(container, x + y * 9, 8 + x * 18, 5 + y * 18));
            }
            case FILTER, TYPE_FILTER -> {
                for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) addSlot(new Slot(container, x + y * 3, 98 + x * 18, 5 + y * 18));
            }
            case REGULATOR -> {
                for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) addSlot(new Slot(container, x + y * 3, 8 + x * 18, 6 + y * 18));
            }
            default -> {}
        }
        ghostStart = slots.size();
        if (kind == AutomationType.FILTER) {
            int[] xs = {18, 35, 52}, ys = {6, 23, 40};
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) addSlot(new Ghost(container, 9 + x + y * 3, xs[x], ys[y]));
        } else if (kind == AutomationType.REGULATOR) {
            int[] xs = {64, 81, 98}, ys = {7, 24, 41};
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) addSlot(new Ghost(container, 9 + x + y * 3, xs[x], ys[y]));
        }
        ghostEnd = slots.size();
        finish(inventory);
    }

    public static AutomationMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        AutomationType k = inventory.player.level().getBlockState(pos).getBlock() instanceof AutomationBlock b ? b.kind : AutomationType.CHEST_BUFFER;
        return new AutomationMenu(id, inventory, new SimpleContainer(k.slots()), new SimpleContainerData(AutomationBlockEntity.DATA), k);
    }

    /** Слот-образец: своими руками предмет не забрать. */
    private static final class Ghost extends Slot {
        Ghost(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    public boolean on(int i) {
        return data(i) != 0;
    }

    public int typeIndex() {
        return data(5);
    }

    public int targetSlot(int i) {
        return data(7 + i);
    }

    public long stored() {
        return (data(16) & 0xFFFFL) | ((data(17) & 0xFFFFL) << 16);
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
        if (slotIndex >= ghostStart && slotIndex < ghostEnd) {
            Slot s = slots.get(slotIndex);
            ItemStack carried = getCarried(), cur = s.getItem();
            boolean regulator = kind == AutomationType.REGULATOR;
            if (carried.isEmpty()) {
                if (regulator && !cur.isEmpty() && button == 1) {
                    ItemStack c = cur.copy();
                    c.setCount(Math.min(c.getMaxStackSize(), c.getCount() * 2));
                    s.set(c);
                } else if (regulator && !cur.isEmpty() && cur.getCount() > 1) {
                    ItemStack c = cur.copy();
                    c.shrink(cur.getCount() / 2);
                    s.set(c);
                } else {
                    s.set(ItemStack.EMPTY);
                }
            } else if (regulator && ItemStack.isSameItemSameComponents(carried, cur)) {
                ItemStack c = cur.copy();
                c.setCount(Math.max(1, Math.min(c.getMaxStackSize(), c.getCount() + (button == 0 ? carried.getCount() : 1))));
                s.set(c);
            } else {
                ItemStack c = carried.copy();
                c.setCount(regulator && button == 0 ? Math.min(c.getCount(), c.getMaxStackSize()) : 1);
                s.set(c);
            }
            real.setChanged();
            return;
        }
        super.clicked(slotIndex, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= ghostStart && index < ghostEnd) return ItemStack.EMPTY;
        return super.quickMoveStack(player, index);
    }

    @Override
    protected boolean acceptsShiftClick(int slot, ItemStack stack) {
        return slot < kind.storage;
    }

    @Override
    public boolean stillValid(Player player) {
        return real.stillValid(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(real instanceof AutomationBlockEntity be)) return id >= 0;
        if (id >= SLOT_UP && id < SLOT_UP + 18) {
            int i = (id - SLOT_UP) % 9, step = id - SLOT_UP >= 9 ? 16 : 1;
            be.targetSlots[i] = Math.min(99, be.targetSlots[i] + step);
        } else if (id >= SLOT_DOWN && id < SLOT_DOWN + 18) {
            int i = (id - SLOT_DOWN) % 9, step = id - SLOT_DOWN >= 9 ? 16 : 1;
            be.targetSlots[i] = Math.max(0, be.targetSlots[i] - step);
        } else if (id == TYPE_PREV || id == TYPE_NEXT) {
            be.cycleType(id == TYPE_NEXT ? 1 : -1);
        } else if (id >= 0 && id < 5) {
            be.toggle(id);
            player.sendOverlayMessage(Component.translatable("message.iis.auto.button." + id + (data(id) != 0 ? ".on" : ".off")));
        } else {
            return false;
        }
        be.setChanged();
        return true;
    }
}

package ru.iis.bio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ru.iis.bio.BioContent;
import ru.iis.bio.production.HearthMenu;

/**
 * Очаг W05 (§22.3, D72): слот топлива как у печи; когда сосуд сверху просит жар, а запаса нет, сгорает одна единица топлива
 * (время горения — печное: уголь 80 с). Остаток жара сохраняется.
 */
public class HearthBlockEntity extends BaseContainerBlockEntity {
    public static final int TICKS_PER_FUEL = 80 * 20;
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int heat, burnTotal = TICKS_PER_FUEL;

    public HearthBlockEntity(BlockPos pos, BlockState state) {
        super(BioContent.HEARTH_BE.get(), pos, state);
    }

    public int heat() {
        return heat;
    }

    /** Добавить единицу топлива напрямую (тесты): +80 с жара. */
    public void addFuel() {
        heat += TICKS_PER_FUEL;
        burnTotal = Math.max(heat, TICKS_PER_FUEL);
        sync(true);
        setChanged();
    }

    /** Взять тик жара; при пустом запасе подбрасывает топливо из слота. */
    public boolean takeHeat() {
        if (heat <= 0 && !burnFuel()) return false;
        heat--;
        if (heat == 0) sync(false);
        else if (heat % 200 == 0) setChanged();
        return true;
    }

    private boolean burnFuel() {
        if (level == null) return false;
        ItemStack fuel = items.get(0);
        int t = fuel.isEmpty() ? 0 : level.fuelValues().burnDuration(fuel);
        if (t <= 0) return false;
        var tpl = fuel.getCraftingRemainder();
        ItemStack rem = tpl == null ? ItemStack.EMPTY : tpl.create();
        fuel.shrink(1);
        if (fuel.isEmpty() && !rem.isEmpty()) items.set(0, rem);
        heat += t;
        burnTotal = t;
        sync(true);
        setChanged();
        return true;
    }

    private void sync(boolean lit) {
        if (level == null || level.isClientSide()) return;
        BlockState st = getBlockState();
        if (st.getValue(HearthBlock.LIT) != lit) level.setBlock(worldPosition, st.setValue(HearthBlock.LIT, lit), 3);
    }

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return i == 0 ? Math.min(heat, 32000) : Math.min(burnTotal, 32000);
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 2;
        }
    };

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return level == null || level.fuelValues().isFuel(stack);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new HearthMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("heat", heat);
        out.putInt("burnTotal", burnTotal);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        heat = Math.max(0, in.getIntOr("heat", 0));
        burnTotal = Math.max(1, in.getIntOr("burnTotal", TICKS_PER_FUEL));
    }
}

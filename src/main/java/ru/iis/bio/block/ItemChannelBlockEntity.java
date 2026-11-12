package ru.iis.bio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ru.iis.bio.BioContent;
import ru.iis.bio.production.BioRecipe;
import ru.iis.bio.production.StationLogic;

/**
 * Перистальтический канал: одна ячейка. Раз в {@link #PERIOD} тиков отдаёт до {@link #BATCH} предметов вперёд и, если пуст, берёт
 * столько же сзади. Органы Био: берёт из выходных ячеек, кладёт во входные только то, что входит в рецепты органа.
 * Сначала вставка, потом списание ровно вставленного — предметы не теряются и не удваиваются.
 */
public class ItemChannelBlockEntity extends BlockEntity implements Container {
    public static final int PERIOD = 10, BATCH = 4;
    private ItemStack held = ItemStack.EMPTY;

    public ItemChannelBlockEntity(BlockPos pos, BlockState state) {
        super(BioContent.ITEM_CHANNEL_BE.get(), pos, state);
    }

    public void serverTick() {
        if (level == null || Math.floorMod(level.getGameTime() + worldPosition.asLong(), PERIOD) != 0) return;
        step();
    }

    /** Один шаг переноса (отдельно — для тестов). */
    public void step() {
        Direction out = getBlockState().getValue(ItemChannelBlock.FACING);
        if (!held.isEmpty()) {
            int moved = insertInto(worldPosition.relative(out), out.getOpposite(), held.copyWithCount(Math.min(BATCH, held.getCount())));
            if (moved > 0) {
                held.shrink(moved);
                if (held.isEmpty()) held = ItemStack.EMPTY;
                setChanged();
            }
        }
        if (held.isEmpty()) {
            ItemStack got = takeFrom(worldPosition.relative(out.getOpposite()), out);
            if (!got.isEmpty()) {
                held = got;
                setChanged();
            }
        }
    }

    /** Сколько из stack принял блок at (со стороны face). */
    private int insertInto(BlockPos at, Direction face, ItemStack stack) {
        if (!level.isLoaded(at)) return 0;
        BlockEntity be = level.getBlockEntity(at);
        if (be instanceof ItemChannelBlockEntity c) {
            if (c.getBlockState().getValue(ItemChannelBlock.FACING) == face) return 0;   // встречный канал
            if (!c.held.isEmpty()) return 0;
            c.held = stack.copy();
            c.setChanged();
            return stack.getCount();
        }
        if (be instanceof StationBlockEntity st) {
            StationLogic l = st.logic;
            if (!wanted(l, stack)) return 0;
            int left = stack.getCount();
            for (int i = 0; i < l.station.inputs && left > 0; i++) {
                ItemStack t = l.getItem(i);
                if (t.isEmpty()) {
                    l.setItem(i, stack.copyWithCount(left));
                    left = 0;
                } else if (ItemStack.isSameItemSameComponents(t, stack) && t.getCount() < t.getMaxStackSize()) {
                    int n = Math.min(left, t.getMaxStackSize() - t.getCount());
                    t.grow(n);
                    left -= n;
                }
            }
            if (left != stack.getCount()) st.setChanged();
            return stack.getCount() - left;
        }
        Container c = HopperBlockEntity.getContainerAt(level, at);
        if (c == null) return 0;
        ItemStack rest = HopperBlockEntity.addItem(this, c, stack.copy(), face);
        return stack.getCount() - rest.getCount();
    }

    /** Нужен ли предмет хоть одному рецепту органа (иначе канал забил бы входы мусором). */
    private static boolean wanted(StationLogic l, ItemStack stack) {
        for (BioRecipe r : BioRecipe.all()) {
            if (!r.station().equals(l.station.id)) continue;
            for (BioRecipe.In in : r.items()) if (StationLogic.matches(stack, in.key())) return true;
        }
        return false;
    }

    /** До BATCH предметов из блока at (со стороны face); пусто, если брать нечего. */
    private ItemStack takeFrom(BlockPos at, Direction face) {
        if (!level.isLoaded(at)) return ItemStack.EMPTY;
        BlockEntity be = level.getBlockEntity(at);
        if (be instanceof ItemChannelBlockEntity) return ItemStack.EMPTY;   // каналы толкают сами
        if (be instanceof StationBlockEntity st) {
            StationLogic l = st.logic;
            for (int i = l.station.inputs; i < l.station.inputs + l.station.outputs; i++) {
                ItemStack s = l.getItem(i);
                if (s.isEmpty()) continue;
                ItemStack got = s.split(Math.min(BATCH, s.getCount()));
                st.setChanged();
                return got;
            }
            return ItemStack.EMPTY;
        }
        Container c = HopperBlockEntity.getContainerAt(level, at);
        if (c == null) return ItemStack.EMPTY;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            if (c instanceof WorldlyContainer w && !w.canTakeItemThroughFace(i, s, face)) continue;
            ItemStack got = c.removeItem(i, Math.min(BATCH, s.getCount()));
            c.setChanged();
            return got;
        }
        return ItemStack.EMPTY;
    }

    // --- Container: одна ячейка (воронки и трубы Кибера тоже могут работать с каналом) ----------------------------------------
    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return held.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return held;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack r = held.split(amount);
        if (held.isEmpty()) held = ItemStack.EMPTY;
        if (!r.isEmpty()) setChanged();
        return r;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack r = held;
        held = ItemStack.EMPTY;
        return r;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        held = stack;
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
        held = ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!held.isEmpty()) out.store("held", ItemStack.CODEC, held);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        held = in.read("held", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
}

package ru.iis.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.block.ElectricBlockEntity;
import ru.iis.machine.block.FluidTank;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Машины IC2 для грядок (D139), LV, область 9 × 3 × 9 вокруг, по одной клетке за тик (по памяти о TileEntityCropmatron и TileEntityCropHarvester):
 * Crop-Matron кормит удобрением, поливает из бака (2000 л воды; ведро в слотах сливается в бак) и пропалывает гербицидом;
 * сборщик урожая собирает спелые культуры в 27 слотов и убирает сорняки. Цены в EU — предложение ИИ: 1 EU за клетку, 10 EU за уход, 100 EU за сбор.
 */
public class CropMachineBlockEntity extends ElectricBlockEntity {
    public enum Kind { MATRON, HARVESTER }

    public static final int RADIUS = 4, CELLS = 9 * 3 * 9, TANK = 2000;
    public final Kind kind;
    public final FluidTank tank;
    private int cursor;
    private boolean allowed = true;

    public CropMachineBlockEntity(BlockPos pos, BlockState state) {
        super(CropContent.CROP_MACHINE_BE.get(), pos, state, kind(state) == Kind.MATRON ? 9 : 27, 10_000, 32);
        this.kind = kind(state);
        this.tank = new FluidTank(TANK, f -> kind == Kind.MATRON && f == Fluids.WATER, false, this::setChanged);
    }

    private static Kind kind(BlockState s) {
        return s.getBlock() instanceof CropMachineBlock b ? b.kind : Kind.MATRON;
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return true;
    }

    @Override
    public boolean isMachine() {
        return true;
    }

    @Override
    public boolean allowedToWork() {
        return allowed;
    }

    @Override
    public void setAllowedToWork(boolean a) {
        allowed = a;
        setChanged();
    }

    @Override
    protected void work(ServerLevel sl) {
        if (!allowed) return;
        if (kind == Kind.MATRON) fillFromBuckets();
        if (energy.stored < 1) return;
        energy.stored -= 1;
        int i = cursor++ % CELLS;
        cursor %= CELLS;
        BlockPos p = worldPosition.offset(i % 9 - RADIUS, i / 81 - 1, (i / 9) % 9 - RADIUS);
        if (!(sl.getBlockEntity(p) instanceof CropBlockEntity crop)) return;
        if (kind == Kind.MATRON) tend(crop);
        else harvest(sl, crop);
        setChanged();
    }

    /** Уход: удобрение (+90), гербицид (+50, тратит прочность), вода из бака. */
    void tend(CropBlockEntity crop) {
        if (energy.stored < 10) return;
        int f = find(CropContent.FERTILIZER.get());
        if (f >= 0 && crop.applyFertilizer(false)) {
            items.get(f).shrink(1);
            energy.stored -= 10;
        }
        int w = find(CropContent.WEED_EX.get());
        if (w >= 0 && energy.stored >= 10 && crop.applyWeedEx(false)) {
            ItemStack s = items.get(w);
            s.setDamageValue(s.getDamageValue() + 1);
            if (s.getDamageValue() >= s.getMaxDamage()) items.set(w, ItemStack.EMPTY);
            energy.stored -= 10;
        }
        if (tank.amount() > 0 && energy.stored >= 10) {
            int used = crop.applyHydration(tank.amount());
            if (used > 0) {
                tank.drainInternal(used);
                energy.stored -= 10;
            }
        }
    }

    private void fillFromBuckets() {
        if (tank.amount() + 1000 > TANK) return;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).is(Items.WATER_BUCKET)) {
                tank.setAmount(Fluids.WATER, tank.amount() + 1000);
                items.set(i, new ItemStack(Items.BUCKET));
                setChanged();
                return;
            }
        }
    }

    /** Сбор: спелая культура отдаёт урожай, если он помещается; сорняк убирается. */
    void harvest(ServerLevel sl, CropBlockEntity crop) {
        CropCard c = crop.card();
        if (c == null) return;
        if (c.isWeedCard()) {
            if (energy.stored < 10) return;
            crop.reset();
            energy.stored -= 10;
            return;
        }
        if (!c.canBeHarvested(crop.size()) || energy.stored < 100 || !hasFreeSlots(3)) return;
        List<ItemStack> drops = crop.harvest(sl.getRandom());
        energy.stored -= 100;
        for (ItemStack d : drops) insert(d);
    }

    private int find(net.minecraft.world.item.Item item) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).is(item)) return i;
        return -1;
    }

    private boolean hasFreeSlots(int n) {
        return items.stream().filter(ItemStack::isEmpty).count() >= n;
    }

    private void insert(ItemStack s) {
        for (int i = 0; i < items.size() && !s.isEmpty(); i++) {
            ItemStack t = items.get(i);
            if (!t.isEmpty() && ItemStack.isSameItemSameComponents(t, s) && t.getCount() < t.getMaxStackSize()) {
                int move = Math.min(s.getCount(), t.getMaxStackSize() - t.getCount());
                t.grow(move);
                s.shrink(move);
            }
        }
        for (int i = 0; i < items.size() && !s.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, s.copy());
                s.setCount(0);
            }
        }
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return IntStream.range(0, items.size()).toArray();
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return kind == Kind.MATRON && (stack.is(CropContent.FERTILIZER.get()) || stack.is(CropContent.WEED_EX.get()) || stack.is(Items.WATER_BUCKET));
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return kind == Kind.HARVESTER || stack.is(Items.BUCKET);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return kind == Kind.MATRON ? new DispenserMenu(id, inventory, this) : ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("water", tank.amount());
        out.putInt("cursor", cursor);
        out.putBoolean("allowed", allowed);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        int w = in.getIntOr("water", 0);
        if (w > 0) tank.setAmount(Fluids.WATER, w);
        cursor = in.getIntOr("cursor", 0);
        allowed = in.getBooleanOr("allowed", true);
    }
}

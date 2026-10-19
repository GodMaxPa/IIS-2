package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.LvContent;
import ru.iis.lv.menu.CokeOvenMenu;
import ru.iis.machine.block.FluidTank;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;

import java.util.List;

/**
 * Коксовая печь Railcraft (ИИС RailCraft.zs переносит её в эпоху LV). Без энергии: пока стоит целая печь 3×3×3
 * с пустой серединой, рецепт идёт по тику. Уголь → кокс + 500 л креозота за 1800 тиков (значения Railcraft по умолчанию).
 * Бак креозота 64000 л; креозот сливается трубами или в ведро (слоты ведра).
 */
public class CokeOvenBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, MachineBlock.TickingMachine {
    public static final int SLOT_IN = 0, SLOT_OUT = 1, SLOT_BUCKET_IN = 2, SLOT_BUCKET_OUT = 3;
    public static final int TANK = 64000;

    private NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    private final FluidTank tank = new FluidTank(TANK, f -> false, true, this::setChanged);
    private int progress, maxProgress;
    private ItemStack pending = ItemStack.EMPTY;
    private FluidStack pendingFluid = FluidStack.EMPTY;
    private boolean formed;

    public CokeOvenBlockEntity(BlockPos pos, BlockState state) {
        super(LvContent.COKE_OVEN_BE.get(), pos, state);
    }

    /** Проверка формы: середина пуста, остальные 25 клеток — кирпичи коксовой печи, контроллер — этот блок. */
    public boolean checkStructure(ServerLevel sl) {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        BlockPos center = worldPosition.relative(facing.getOpposite());
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (p.equals(worldPosition)) continue;
                    BlockState s = sl.getBlockState(p);
                    if (dx == 0 && dy == 0 && dz == 0) {
                        if (!s.isAir()) return false;
                    } else if (!s.is(LvContent.COKE_OVEN_BRICKS.get())) return false;
                }
        return true;
    }

    public boolean formed() {
        return formed;
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (sl.getGameTime() % 20 == 0) formed = checkStructure(sl);
        fillBucket();
        if (maxProgress > 0) {
            if (formed && ++progress >= maxProgress) finish();
        } else if (formed) {
            start(sl);
        }
        boolean active = formed && maxProgress > 0;
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != active) sl.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, active), 3);
    }

    private void start(ServerLevel sl) {
        if (items.get(SLOT_IN).isEmpty()) return;
        var match = GtRecipes.find(sl, GtRecipeMap.COKE_OVEN, List.of(items.get(SLOT_IN)), eut -> true);
        if (match.isEmpty()) return;
        var r = match.get().recipe();
        ItemStack out = r.outputs().isEmpty() ? ItemStack.EMPTY : r.outputs().getFirst().create();
        FluidStack fl = r.fluidOutputs().isEmpty() ? FluidStack.EMPTY : r.fluidOutputs().getFirst().create();
        ItemStack cur = items.get(SLOT_OUT);
        if (!out.isEmpty() && !cur.isEmpty() && (!ItemStack.isSameItemSameComponents(cur, out) || cur.getCount() + out.getCount() > cur.getMaxStackSize())) return;
        if (!fl.isEmpty() && (tank.amount() + fl.getAmount() > TANK || (tank.amount() > 0 && !tank.resource().getFluid().isSame(fl.getFluid())))) return;
        items.get(SLOT_IN).shrink(match.get().take()[0]);
        pending = out;
        pendingFluid = fl;
        maxProgress = r.duration();
        progress = 0;
        setChanged();
    }

    private void finish() {
        if (!pending.isEmpty()) {
            ItemStack cur = items.get(SLOT_OUT);
            if (cur.isEmpty()) items.set(SLOT_OUT, pending.copy());
            else cur.grow(pending.getCount());
        }
        if (!pendingFluid.isEmpty()) tank.setAmount(pendingFluid.getFluid(), tank.amount() + pendingFluid.getAmount());
        pending = ItemStack.EMPTY;
        pendingFluid = FluidStack.EMPTY;
        progress = 0;
        maxProgress = 0;
        setChanged();
    }

    private void fillBucket() {
        ItemStack in = items.get(SLOT_BUCKET_IN);
        if (!in.is(Items.BUCKET) || tank.amount() < 1000 || !tank.resource().getFluid().isSame(IISFluids.CREOSOTE.get())) return;
        if (!items.get(SLOT_BUCKET_OUT).isEmpty()) return;
        tank.drainInternal(1000);
        in.shrink(1);
        items.set(SLOT_BUCKET_OUT, new ItemStack(IISFluids.CREOSOTE_BUCKET.get()));
        setChanged();
    }

    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return tank;
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    public FluidTank tank() {
        return tank;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{SLOT_IN, SLOT_OUT, SLOT_BUCKET_IN, SLOT_BUCKET_OUT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUT || slot == SLOT_BUCKET_OUT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_IN || (slot == SLOT_BUCKET_IN && stack.is(Items.BUCKET));
    }

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> maxProgress > 0 ? progress * 1000 / maxProgress : 0;
                case 1 -> tank.amount() / 2; // до 32000, влезает в short
                case 2 -> formed ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 3;
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
        return items.size();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new CokeOvenMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        tank.serialize(out.child("tank"));
        out.putInt("progress", progress);
        out.putInt("maxProgress", maxProgress);
        if (!pending.isEmpty()) out.store("pending", ItemStack.CODEC, pending);
        if (!pendingFluid.isEmpty()) out.store("pendingFluid", FluidStack.CODEC, pendingFluid);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(4, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        tank.deserialize(in.childOrEmpty("tank"));
        progress = in.getIntOr("progress", 0);
        maxProgress = in.getIntOr("maxProgress", 0);
        pending = in.read("pending", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        pendingFluid = in.read("pendingFluid", FluidStack.CODEC).orElse(FluidStack.EMPTY);
    }
}

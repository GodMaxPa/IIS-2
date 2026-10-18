package ru.iis.machine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;
import ru.iis.machine.IISMachines;
import ru.iis.machine.logic.BbfStructure;
import ru.iis.machine.menu.BbfMenu;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;

import java.util.ArrayList;
import java.util.List;

/**
 * Примитивная кирпичная доменная печь GT5-II (GT_MetaTileEntity_PrimitiveBlastFurnace).
 * Три входа, три выхода; топливо (уголь/древесный уголь) входит в рецепт. Работает без пара и энергии.
 * Работающая печь заливает лаву в нижние две клетки центральной шахты и убирает её, когда останавливается.
 */
public class BbfBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, MachineBlock.TickingMachine {
    public static final int INPUTS = 3, OUTPUTS = 3;

    private NonNullList<ItemStack> items = NonNullList.withSize(INPUTS + OUTPUTS, ItemStack.EMPTY);
    private List<ItemStack> pending = List.of();
    private int progress, maxProgress;
    private boolean formed;
    private int recheck;

    public BbfBlockEntity(BlockPos pos, BlockState state) {
        super(IISMachines.BBF_BE.get(), pos, state);
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    public boolean formed() {
        return formed;
    }

    private BlockPos at(BbfStructure.Pos p) {
        Direction back = getBlockState().getValue(MachineBlock.FACING).getOpposite();
        Direction right = back.getClockWise();
        return worldPosition.relative(right, p.right()).relative(Direction.UP, p.up()).relative(back, p.back());
    }

    public boolean checkStructure() {
        if (level == null) return false;
        return BbfStructure.check(new BbfStructure.World() {
            @Override
            public boolean isCasing(BbfStructure.Pos p) {
                return level.getBlockState(at(p)).is(IISMachines.FIREBRICK_CASING.get());
            }

            @Override
            public boolean isAirOrLava(BbfStructure.Pos p) {
                BlockState s = level.getBlockState(at(p));
                return s.isAir() || s.is(Blocks.LAVA);
            }
        });
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (recheck-- <= 0) {
            formed = checkStructure();
            recheck = 20;
        }
        if (formed) {
            if (maxProgress > 0) {
                if (++progress >= maxProgress) {
                    addOutputs();
                    progress = 0;
                    maxProgress = 0;
                }
            } else {
                tryStart(sl);
            }
        }
        boolean active = formed && maxProgress > 0;
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != active) {
            sl.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, active), 3);
        }
        for (BbfStructure.Pos p : new BbfStructure.Pos[]{BbfStructure.LAVA_LOW, BbfStructure.LAVA_HIGH}) {
            BlockPos lp = at(p);
            BlockState s = sl.getBlockState(lp);
            if (active && s.isAir()) sl.setBlock(lp, Blocks.LAVA.defaultBlockState(), 2);
            else if (!active && s.is(Blocks.LAVA)) sl.setBlock(lp, Blocks.AIR.defaultBlockState(), 2);
        }
        if (active && sl.getGameTime() % 4 == 0) {
            BlockPos top = at(new BbfStructure.Pos(0, 2, 1));
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, top.getX() + 0.5, top.getY() + 0.8, top.getZ() + 0.5, 1, 0.25, 0.1, 0.25, 0.02);
        }
    }

    private void tryStart(ServerLevel sl) {
        List<ItemStack> inputs = new ArrayList<>(items.subList(0, INPUTS));
        var match = GtRecipes.find(sl, GtRecipeMap.PRIMITIVE_BLAST, inputs, eut -> true);
        if (match.isEmpty()) return;
        var m = match.get();
        List<ItemStack> outs = m.recipe().rollOutputs(sl.getRandom());
        for (int i = 0; i < OUTPUTS; i++) {
            ItemStack o = i < outs.size() ? outs.get(i) : ItemStack.EMPTY;
            ItemStack cur = items.get(INPUTS + i);
            if (o.isEmpty() || cur.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(cur, o) || cur.getCount() + o.getCount() > cur.getMaxStackSize()) return;
        }
        for (int s = 0; s < m.take().length; s++) {
            if (m.take()[s] > 0) items.get(s).shrink(m.take()[s]);
        }
        pending = outs;
        maxProgress = m.recipe().duration();
        progress = 0;
        setChanged();
    }

    private void addOutputs() {
        for (int i = 0; i < Math.min(OUTPUTS, pending.size()); i++) {
            ItemStack o = pending.get(i);
            if (o.isEmpty()) continue;
            ItemStack cur = items.get(INPUTS + i);
            if (cur.isEmpty()) items.set(INPUTS + i, o.copy());
            else if (ItemStack.isSameItemSameComponents(cur, o)) cur.setCount(Math.min(cur.getMaxStackSize(), cur.getCount() + o.getCount()));
        }
        pending = List.of();
        setChanged();
    }

    // --- WorldlyContainer ---------------------------------------------------------------------------------------
    private static final int[] ALL = {0, 1, 2, 3, 4, 5};

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot < INPUTS;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= INPUTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < INPUTS;
    }

    // --- GUI ------------------------------------------------------------------------------------------------------
    /** Прогресс передаётся в тысячных: длительность до 64800 тиков не влезает в short пакета. */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> maxProgress > 0 ? (int) ((long) progress * 1000 / maxProgress) : 0;
                case 1 -> formed ? 1 : 0;
                case 2 -> Math.min(Short.MAX_VALUE, Math.max(0, (maxProgress - progress) / 20));
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
        return INPUTS + OUTPUTS;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new BbfMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("progress", progress);
        out.putInt("maxProgress", maxProgress);
        out.store("pending", ItemStack.OPTIONAL_CODEC.listOf(), pending);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(INPUTS + OUTPUTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        progress = in.getIntOr("progress", 0);
        maxProgress = in.getIntOr("maxProgress", 0);
        pending = in.read("pending", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
    }
}

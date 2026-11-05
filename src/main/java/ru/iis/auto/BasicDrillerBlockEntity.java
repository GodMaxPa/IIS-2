package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.block.ElectricBlockEntity;
import ru.iis.lv.item.BatteryItem;
import ru.iis.machine.block.FluidTank;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.world.ore.OreBlocks;

import java.util.List;

/**
 * Автобур и помпа (GT_MetaTileEntity_BasicDrillerBase, _Miner, _Pump, D108). Шахтные трубы — во входных слотах. Машина опускает трубу
 * под собой по блоку, на каждом слое обходит квадрат: бур добывает руду, помпа откачивает жидкость. Внизу поднимает трубы
 * обратно и выключается. Каждая операция — полный цикл; запас энергии нужен на весь цикл.
 */
public class BasicDrillerBlockEntity extends ElectricBlockEntity {
    private static final int[] MINER_RADIUS = {8, 8, 16, 24}, MINER_SPEED = {160, 160, 80, 40}, MINER_ENERGY = {8, 8, 32, 128};
    private static final int[] PUMP_RADIUS = {8, 10, 20, 40, 80, 160}, PUMP_SPEED = {80, 80, 40, 20, 10, 5}, PUMP_ENERGY = {8, 4, 16, 64, 256, 1024};
    public static final int DATA = 8, SLOTS = 5, OUT = 2, BATTERY = 4;

    private final boolean pump;
    private final int tier;
    private final long voltage;
    private final FluidTank tank;
    private int drillX, drillY, drillZ, progress;
    private boolean pickingPipes, waitPipe = true;
    public boolean allowed = true, itemTransfer, fluidTransfer;
    private List<ItemStack> pending = List.of();

    public BasicDrillerBlockEntity(BlockPos pos, BlockState state) {
        super(AutoContent.BASIC_DRILLER_BE.get(), pos, state, SLOTS, capacity(state), block(state).tier.voltage());
        this.pump = block(state).pump;
        this.tier = block(state).tier.tier();
        this.voltage = block(state).tier.voltage();
        this.tank = new FluidTank(Math.max(1, 16000 * tier), f -> false, true, this::setChanged);
    }

    private static BasicDrillerBlock block(BlockState s) {
        return s.getBlock() instanceof BasicDrillerBlock b ? b : AutoContent.MINERS.get(0).get();
    }

    private static long capacity(BlockState s) {
        int t = block(s).tier.tier();
        return t == 1 ? 4096 : block(s).tier.voltage() * 64;
    }

    public int radius() {
        return pump ? PUMP_RADIUS[tier] : MINER_RADIUS[tier];
    }

    public int speed() {
        return pump ? PUMP_SPEED[tier] : MINER_SPEED[tier];
    }

    public int energyPerTick() {
        return pump ? PUMP_ENERGY[tier] : MINER_ENERGY[tier];
    }

    public FluidTank tank() {
        return tank;
    }

    public boolean picking() {
        return pickingPipes;
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return side != front();
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
        if (allowed != a) {
            allowed = a;
            setChanged();
        }
    }

    public void toggleTransfer(int b) {
        if (b == 0) fluidTransfer = !fluidTransfer;
        else itemTransfer = !itemTransfer;
        setChanged();
    }

    // --- работа -----------------------------------------------------------------------------------------------------
    private boolean freeSpace() {
        if (pump) return tank.amount() <= tank.capacity() - 1000;
        return items.get(OUT).isEmpty() && items.get(OUT + 1).isEmpty();
    }

    private boolean hasPipe() {
        return items.get(0).is(MultiContent.MINING_PIPE.asItem()) || items.get(1).is(MultiContent.MINING_PIPE.asItem());
    }

    /** GT onPostTick. */
    @Override
    protected void work(ServerLevel sl) {
        ItemStack bat = items.get(BATTERY);
        if (bat.getItem() instanceof BatteryItem b && b.isBattery() && energy.stored < energy.capacity) {
            energy.stored += b.discharge(bat, Math.min(voltage, energy.capacity - energy.stored));
        }
        if (itemTransfer) pushItems(sl);
        if (fluidTransfer) pushFluid(sl);
        boolean active = false;
        int speed = speed();
        if (allowed && energy.stored >= (long) energyPerTick() * (speed - progress) && freeSpace()) {
            if (waitPipe && hasPipe()) waitPipe = false;
            if (!waitPipe) {
                active = true;
                energy.stored -= energyPerTick();
                if (++progress >= speed) {
                    progress = 0;
                    operate(sl);
                }
                setChanged();
            }
        }
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != active) sl.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, active), 3);
    }

    private void operate(ServerLevel sl) {
        if (pickingPipes) {
            if (drillY == 0) {
                allowed = false;
                pickingPipes = false;
            } else {
                BlockPos p = worldPosition.above(drillY);
                if (sl.getBlockState(p).is(MultiContent.MINING_PIPE_TIP.get()) || sl.getBlockState(p).is(MultiContent.MINING_PIPE.get())) {
                    put(OUT, new ItemStack(MultiContent.MINING_PIPE.get()));
                    sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
                drillY++;
            }
            return;
        }
        if (drillY == 0 || drillZ > radius()) {
            moveOneDown(sl);
            return;
        }
        int r = radius();
        while (drillZ <= r) {
            while (drillX <= r) {
                if (workBlock(sl)) return;
                drillX++;
            }
            drillX = -r;
            drillZ++;
        }
    }

    private boolean takePipe() {
        for (int i = 0; i < 2; i++) {
            ItemStack s = items.get(i);
            if (s.is(MultiContent.MINING_PIPE.asItem())) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** GT moveOneDown. */
    private void moveOneDown(ServerLevel sl) {
        BlockPos next = worldPosition.above(drillY - 1);
        BlockState ns = sl.getBlockState(next);
        boolean blocked = next.getY() < sl.getMinY() || (pump ? !canPump(ns) : ns.getDestroySpeed(sl, next) < 0);
        if (blocked) {
            pickingPipes = true;
            return;
        }
        BlockPos cur = worldPosition.above(drillY);
        if (drillY != 0 && sl.getBlockState(cur).is(MultiContent.MINING_PIPE_TIP.get())) {
            sl.setBlock(cur, MultiContent.MINING_PIPE.get().defaultBlockState(), 3);
        }
        if (!takePipe()) {
            waitPipe = true;
            return;
        }
        if (pump) {
            pumpBlock(sl, next);
        } else if (!ns.isAir()) {
            mine(sl, next);
        }
        sl.setBlock(next, MultiContent.MINING_PIPE_TIP.get().defaultBlockState(), 3);
        drillY--;
        drillX = drillZ = -radius();
    }

    private boolean workBlock(ServerLevel sl) {
        BlockPos p = worldPosition.offset(drillX, drillY, drillZ);
        BlockState s = sl.getBlockState(p);
        if (pump) return pumpBlock(sl, p);
        if (OreBlocks.isOre(s.getBlock()) || s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) {
            mine(sl, p);
            return true;
        }
        return false;
    }

    private void mine(ServerLevel sl, BlockPos p) {
        List<ItemStack> drops = Block.getDrops(sl.getBlockState(p), sl, p, sl.getBlockEntity(p));
        if (!drops.isEmpty()) put(OUT, drops.get(0));
        if (drops.size() > 1) put(OUT + 1, drops.get(1));
        sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    }

    private static boolean canPump(BlockState s) {
        return s.isAir() || s.getBlock() instanceof LiquidBlock;
    }

    /** GT pumpBlock: источник даёт 1000 л, течение просто убирается. */
    private boolean pumpBlock(ServerLevel sl, BlockPos p) {
        BlockState s = sl.getBlockState(p);
        if (!(s.getBlock() instanceof LiquidBlock)) return false;
        FluidState f = s.getFluidState();
        if (!f.isSource()) {
            sl.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            return true;
        }
        Fluid fluid = f.getType();
        if (tank.amount() > 0 && !tank.resource().getFluid().isSame(fluid)) return false;
        sl.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        tank.setAmount(fluid, Math.min(tank.capacity(), tank.amount() + 1000));
        return true;
    }

    private void put(int slot, ItemStack s) {
        ItemStack cur = items.get(slot);
        if (cur.isEmpty()) items.set(slot, s.copy());
        else if (ItemStack.isSameItemSameComponents(cur, s)) cur.setCount(Math.min(cur.getMaxStackSize(), cur.getCount() + s.getCount()));
    }

    private void pushItems(ServerLevel sl) {
        Direction side = front().getOpposite();
        ResourceHandler<ItemResource> to = sl.getCapability(Capabilities.Item.BLOCK, worldPosition.relative(side), side.getOpposite());
        if (to == null || energy.stored < 128) return;
        for (int s = OUT; s < OUT + 2; s++) {
            ItemStack st = items.get(s);
            if (st.isEmpty()) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int moved = ResourceHandlerUtil.insertStacking(to, ItemResource.of(st), st.getCount(), tx);
                tx.commit();
                if (moved > 0) {
                    st.shrink(moved);
                    energy.stored = Math.max(0, energy.stored - moved);
                    setChanged();
                }
            }
        }
    }

    private void pushFluid(ServerLevel sl) {
        if (tank.amount() <= 0) return;
        Direction side = front().getOpposite();
        ResourceHandler<FluidResource> to = sl.getCapability(Capabilities.Fluid.BLOCK, worldPosition.relative(side), side.getOpposite());
        if (to == null) return;
        try (Transaction tx = Transaction.openRoot()) {
            int moved = to.insert(tank.resource(), Math.min(tank.amount(), 1000), tx);
            tx.commit();
            if (moved > 0) tank.drainInternal(moved);
        }
    }

    // --- предметы и жидкость снаружи -----------------------------------------------------------------------------------
    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return pump && side != front() ? tank : null;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{0, 1, 2, 3};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot < OUT && side != front() && stack.is(MultiContent.MINING_PIPE.asItem());
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUT || slot == OUT + 1;
    }

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> progress;
                case 1 -> speed();
                case 2 -> itemTransfer ? 1 : 0;
                case 3 -> fluidTransfer ? 1 : 0;
                case 4 -> tank.amount() & 0xFFFF;
                case 5 -> (tank.amount() >> 16) & 0xFFFF;
                case 6 -> tank.amount() > 0 ? BuiltInRegistries.FLUID.getId(tank.resource().getFluid()) : 0;
                case 7 -> -drillY;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA;
        }
    };

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new BasicDrillerMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("drillX", drillX);
        out.putInt("drillY", drillY);
        out.putInt("drillZ", drillZ);
        out.putInt("progress", progress);
        out.putBoolean("picking", pickingPipes);
        out.putBoolean("waitPipe", waitPipe);
        out.putBoolean("allowed", allowed);
        out.putBoolean("itemTransfer", itemTransfer);
        out.putBoolean("fluidTransfer", fluidTransfer);
        tank.serialize(out.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        drillX = in.getIntOr("drillX", 0);
        drillY = in.getIntOr("drillY", 0);
        drillZ = in.getIntOr("drillZ", 0);
        progress = in.getIntOr("progress", 0);
        pickingPipes = in.getBooleanOr("picking", false);
        waitPipe = in.getBooleanOr("waitPipe", true);
        allowed = in.getBooleanOr("allowed", true);
        itemTransfer = in.getBooleanOr("itemTransfer", false);
        fluidTransfer = in.getBooleanOr("fluidTransfer", false);
        tank.deserialize(in.childOrEmpty("tank"));
    }
}

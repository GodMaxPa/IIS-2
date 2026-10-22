package ru.iis.machine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import ru.iis.machine.IISMachines;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Бак трубы и раздача жидкости (GT_MetaPipeEntity_Fluid#onPostTick, #distributeFluid):
 * бак вмещает cap×20; раз в 5 тиков, если за этот период не было новых поступлений с других сторон,
 * до cap×10 раздаётся соседям пропорционально их свободному месту (кроме сторон, откуда жидкость пришла).
 */
public class PipeBlockEntity extends BlockEntity {
    private final FluidTank tank;
    private int receivedFrom, lastReceivedFrom;
    private final ReceivedJournal journal = new ReceivedJournal();
    private final Map<Direction, ResourceHandler<FluidResource>> sided = new EnumMap<>(Direction.class);

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(IISMachines.PIPE_BE.get(), pos, state);
        this.tank = new FluidTank(spec(state).capacity() * 20, f -> true, true, this::setChanged);
        for (Direction d : Direction.values()) sided.put(d, new Side(d));
    }

    private static PipeBlock.Spec spec(BlockState state) {
        return state.getBlock() instanceof PipeBlock p ? p.spec : PipeBlock.SPECS.getFirst();
    }

    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        if (side == null) return tank;
        return getBlockState().getValue(PipeBlock.PROPS.get(side)) ? sided.get(side) : null;
    }

    public FluidTank tank() {
        return tank;
    }

    public void serverTick() {
        if (level == null || level.getGameTime() % 5 != 0) return;
        receivedFrom &= 63;
        if (receivedFrom == 63) receivedFrom = 0;
        if (tank.amount() > 0 && checkEnvironment()) return;
        boolean distribute = receivedFrom == lastReceivedFrom;
        if (tank.amount() > 0 && distribute) {
            distributeFluid();
            receivedFrom = 0;
        }
        lastReceivedFrom = receivedFrom;
    }

    /**
     * GT_MetaPipeEntity_Fluid#checkEnvironment: жидкость горячее предела трубы поджигает её (1 шанс из 100 — труба сгорает),
     * газ уходит через негерметичную трубу (−5 л за проверку) и обжигает/морозит существ рядом. true — трубы больше нет.
     */
    private boolean checkEnvironment() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl) || !ru.iis.IISConfig.on(ru.iis.IISConfig.PIPE_HAZARDS)) return false;
        PipeBlock.Spec spec = spec(getBlockState());
        var type = tank.resource().getFluid().getFluidType();
        int temp = type.getTemperature();
        if (temp > spec.heatResistance()) {
            if (sl.getRandom().nextInt(100) == 0) {
                sl.setBlock(worldPosition, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
                return true;
            }
            Direction d = Direction.getRandom(sl.getRandom());
            BlockPos n = worldPosition.relative(d);
            if (sl.getBlockState(n).isAir()) sl.setBlock(n, net.minecraft.world.level.block.BaseFireBlock.getState(sl, n), 3);
        }
        if (!spec.gasProof() && type.isLighterThanAir()) {
            tank.drainInternal(5);
            sl.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 0.2f, 1.6f);
            if (temp > 320 || temp < 260) {
                float dmg = temp > 320 ? (temp - 300) / 25.0f : (270 - temp) / 12.5f;
                var box = new net.minecraft.world.phys.AABB(worldPosition).inflate(2);
                for (var living : sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
                    living.hurtServer(sl, temp > 320 ? sl.damageSources().inFire() : sl.damageSources().freeze(), dmg);
                }
            }
        }
        return false;
    }

    /** Существо касается трубы: горячая жидкость (&gt;320 K) обжигает ((T−300)/50), холодная (&lt;260 K) морозит ((270−T)/25). */
    void touch(net.minecraft.world.entity.LivingEntity living) {
        if (tank.amount() <= 0 || !(level instanceof net.minecraft.server.level.ServerLevel sl) || !ru.iis.IISConfig.on(ru.iis.IISConfig.PIPE_HAZARDS)) return;
        int temp = tank.resource().getFluid().getFluidType().getTemperature();
        if (temp > 320) living.hurtServer(sl, sl.damageSources().hotFloor(), (temp - 300) / 50.0f);
        else if (temp < 260) living.hurtServer(sl, sl.damageSources().freeze(), (270 - temp) / 25.0f);
    }

    private void distributeFluid() {
        FluidResource fluid = tank.resource();
        int cap = spec(getBlockState()).capacity();
        record Target(ResourceHandler<FluidResource> handler, int room) {}
        List<Target> targets = new ArrayList<>();
        Direction[] dirs = Direction.values();
        int start = level.getRandom().nextInt(6);
        long available = 0;
        for (int i = 0; i < 6; i++) {
            Direction d = dirs[(i + start) % 6];
            if (!getBlockState().getValue(PipeBlock.PROPS.get(d)) || (receivedFrom & (1 << d.get3DDataValue())) != 0) continue;
            ResourceHandler<FluidResource> h = level.getCapability(Capabilities.Fluid.BLOCK, worldPosition.relative(d), d.getOpposite());
            if (h == null) continue;
            int room;
            try (Transaction tx = Transaction.openRoot()) {
                room = h.insert(fluid, Integer.MAX_VALUE / 2, tx);
            }
            if (room > 0) {
                targets.add(new Target(h, room));
                available += room;
            }
        }
        double amount = Math.max(1, Math.min(cap * 10, tank.amount()));
        for (Target t : targets) {
            int share = available > amount ? (int) Math.floor(t.room * amount / available) : t.room;
            share = Math.min(share, tank.amount());
            if (share <= 0) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int moved = t.handler.insert(fluid, share, tx);
                if (moved > 0) {
                    tank.drainInternal(moved);
                    tx.commit();
                }
            }
        }
    }

    /** Отметка «откуда пришла жидкость», откатываемая вместе с транзакцией. */
    private final class ReceivedJournal extends SnapshotJournal<Integer> {
        @Override
        protected Integer createSnapshot() {
            return receivedFrom;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            receivedFrom = snapshot;
        }
    }

    /** Бак, видимый с одной стороны: запоминает сторону поступления. */
    private final class Side implements ResourceHandler<FluidResource> {
        private final Direction side;

        Side(Direction side) {
            this.side = side;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            return tank.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return tank.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return tank.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return tank.isValid(index, resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            int r = tank.insert(index, resource, amount, transaction);
            if (r > 0) {
                journal.updateSnapshots(transaction);
                receivedFrom |= 1 << side.get3DDataValue();
            }
            return r;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return tank.extract(index, resource, amount, transaction);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.serialize(out.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.deserialize(in.childOrEmpty("tank"));
    }
}

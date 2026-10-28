package ru.iis.rail.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ru.iis.energy.EuNode;
import ru.iis.energy.logic.EnergyStorage;
import ru.iis.lv.Deferred;
import ru.iis.rail.RailContent;

/** Запас фидера: 10 000 EU, вход до 512 В, 2 А за тик. */
public class ElectricFeederBlockEntity extends BlockEntity implements EuNode {
    public static final long CAPACITY = 10_000, INPUT_VOLTAGE = 512;

    public final EnergyStorage energy = new EnergyStorage(CAPACITY, INPUT_VOLTAGE);
    private boolean exploded;

    public ElectricFeederBlockEntity(BlockPos pos, BlockState state) {
        super(RailContent.FEEDER_BE.get(), pos, state);
        energy.maxAmperesIn = 2;
    }

    @Override
    public long injectEnergy(Direction from, long voltage, long amperes) {
        if (exploded || !(level instanceof ServerLevel sl)) return 0;
        EnergyStorage.Inject r = energy.inject(sl.getGameTime(), voltage, amperes);
        if (r.overvoltage()) {
            if (ru.iis.IISConfig.on(ru.iis.IISConfig.OVERVOLTAGE_EXPLOSIONS)) explode(sl);
            return 0;
        }
        if (r.amperes() > 0) setChanged();
        return r.amperes();
    }

    /** Отдать до {@code eu} EU локомотиву. */
    public long take(long eu) {
        long t = Math.min(eu, energy.stored);
        if (t > 0) {
            energy.stored -= t;
            setChanged();
        }
        return t;
    }

    private void explode(ServerLevel sl) {
        exploded = true;
        BlockPos pos = worldPosition;
        Deferred.run(sl, () -> {
            if (sl.getBlockEntity(pos) == this) sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            sl.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3f, Level.ExplosionInteraction.BLOCK);
        });
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("energy", energy.stored);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy.stored = in.getLongOr("energy", 0);
    }
}

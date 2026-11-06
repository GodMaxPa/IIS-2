package ru.iis.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import ru.iis.energy.logic.CableState;
import ru.iis.energy.logic.FeTransfer;
import ru.iis.lv.block.CableBlockEntity;

/** Провода EU питают блоки других модов с энергией FE: AE2 и Ender IO (D125). Обратного хода нет: IIS OverpoweredStuff.cfg, InputRF=false. */
public final class FeBridge {
    private FeBridge() {}

    private static EnergyHandler handler(Level level, BlockPos pos, BlockState state, Direction side) {
        return level.getCapability(Capabilities.Energy.BLOCK, pos, state, null, side);
    }

    /** Подключается ли провод к блоку {@code pos} с его стороны {@code side}. Свои блоки идут через {@link EnergyConnectable}. */
    public static boolean connects(LevelReader reader, BlockPos pos, BlockState state, Direction side) {
        if (!state.hasBlockEntity() || !(reader instanceof Level level) || state.getBlock() instanceof EnergyConnectable) return false;
        return handler(level, pos, state, side) != null;
    }

    static long insert(ServerLevel level, BlockPos pos, Direction side, long voltage, long amperes) {
        BlockState state = level.getBlockState(pos);
        EnergyHandler h = state.hasBlockEntity() ? handler(level, pos, state, side) : null;
        if (h == null) return 0;
        FeTransfer.Sink sink = (amount, simulate) -> {
            try (Transaction tx = Transaction.openRoot()) {
                int got = h.insert((int) Math.min(amount, Integer.MAX_VALUE), tx);
                if (!simulate) tx.commit();
                return got;
            }
        };
        if ("ae2".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) return FeTransfer.ae2(sink, voltage, amperes);
        CableState cable = level.getBlockEntity(pos.relative(side)) instanceof CableBlockEntity be ? be.state : new CableState();
        return FeTransfer.rf(sink, voltage, cable);
    }
}

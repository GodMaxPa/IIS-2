package ru.iis.gear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.block.BatteryBufferBlock;

/** Энергохранилище IC2: окно и подключение проводов как у аккумуляторного буфера на 4 слота. */
public class EnergyStorageBlock extends BatteryBufferBlock {
    public final long capacity;

    public EnergyStorageBlock(long capacity, VoltageTier tier, Properties properties) {
        super(4, tier, properties);
        this.capacity = capacity;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyStorageBlockEntity(pos, state);
    }
}

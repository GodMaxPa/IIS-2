package ru.iis.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import ru.iis.energy.EnergyConnectable;

/** Блок-панель (решение владельца D117, по памяти об IC2): 1 EU/t днём под открытым небом, отдаёт в провод. */
public class SolarPanelBlock extends Block implements EntityBlock, EnergyConnectable {
    public SolarPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (l, p, s, be) -> {
            if (be instanceof SolarPanelBlockEntity r) r.serverTick();
        };
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return side != Direction.UP;
    }
}

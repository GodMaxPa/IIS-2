package ru.iis.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Путь ИИС: тип (для предела скорости, {@link RailSpeed}) и действие на проходящую вагонетку — замена
 * {@code onMinecartPass} Forge 1.7.10, которого в NeoForge 26.1 нет (вызывает {@link RailEvents} каждый тик вагонетки).
 */
public interface IisRail {
    TrackKind kind();

    default void onCartPass(BlockState state, ServerLevel level, BlockPos pos, AbstractMinecart cart) {}
}

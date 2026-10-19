package ru.iis.energy;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Блок, к которому провода подключаются с указанной стороны (вход или выход EU). */
public interface EnergyConnectable {
    boolean connectsEnergy(BlockState state, Direction side);
}

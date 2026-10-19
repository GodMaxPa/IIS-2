package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.energy.logic.CableState;
import ru.iis.lv.LvContent;

/** Состояние провода (ток, нагрев). Не тикает и не сохраняется: нагрев за секунды остывает и после загрузки не нужен. */
public class CableBlockEntity extends BlockEntity {
    public final CableState state = new CableState();

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(LvContent.CABLE_BE.get(), pos, state);
    }
}

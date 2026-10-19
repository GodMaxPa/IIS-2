package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.energy.EnergyConnectable;
import ru.iis.machine.block.MachineBlock;

import java.util.function.BiConsumer;

/**
 * Электрический блок с лицевой стороной: машина, генератор, буфер.
 * GT5 explosions_on_nonwrenching: блок, разрушенный взрывом, сам взрывается, если в нём есть заряд (цепная реакция).
 */
public abstract class LvMachineBlock extends MachineBlock implements EnergyConnectable {
    protected LvMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        if (level.getBlockEntity(pos) instanceof ElectricBlockEntity be) be.doEnergyExplosion();
        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    /** Для подключения проводов: front — сторона блока, с которой смотрит FACING. */
    protected static Direction front(BlockState state) {
        return state.getValue(FACING);
    }
}

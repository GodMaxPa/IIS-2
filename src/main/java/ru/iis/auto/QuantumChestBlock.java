package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.block.MachineBlock;

/** Квантовый сундук (D108): энергии не требует, содержимое остаётся в предмете при поломке. */
public class QuantumChestBlock extends MachineBlock {
    /** GT CommonSizeCompute: LV 4 млн … IV 64 млн предметов. */
    public final int capacity;

    public QuantumChestBlock(int capacity, Properties properties) {
        super(properties);
        this.capacity = capacity;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumChestBlockEntity(pos, state);
    }
}

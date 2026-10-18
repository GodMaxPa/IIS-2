package ru.iis.machine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.SteamMachineType;

/** Паровая машина (бронза или сталь). */
public class SteamMachineBlock extends MachineBlock {
    public final SteamMachineType type;
    public final SteamMachineType.Tier tier;

    public SteamMachineBlock(SteamMachineType type, SteamMachineType.Tier tier, Properties properties) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamMachineBlockEntity(pos, state);
    }
}

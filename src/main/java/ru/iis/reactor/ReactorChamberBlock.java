package ru.iis.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import ru.iis.energy.EnergyConnectable;

/**
 * Реакторная камера: добавляет реактору столбец ячеек и служит его продолжением, как в IC2: открывает окно реактора, принимает и отдаёт детали, выдаёт энергию, слышит красный камень.
 * Своего содержимого у неё нет, всё лежит в реакторе рядом.
 */
public class ReactorChamberBlock extends Block implements EnergyConnectable {
    public ReactorChamberBlock(Properties properties) {
        super(properties);
    }

    /** Реактор, к которому примыкает камера. */
    public static @Nullable NuclearReactorBlockEntity reactor(BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (level.getBlockEntity(pos.relative(d)) instanceof NuclearReactorBlockEntity r) return r;
        }
        return null;
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        NuclearReactorBlockEntity r = reactor(level, pos);
        if (r != null) player.openMenu(r);
        else player.sendOverlayMessage(Component.translatable("gui.iis.reactor.chamber_alone"));
        return InteractionResult.SUCCESS;
    }
}

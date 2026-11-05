package ru.iis.space;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Верстак NASA. Отличие от GC: одна схема без предмета-схемы, блок 1×1 вместо 3×3×3. */
public class NasaWorkbenchBlock extends Block {
    public NasaWorkbenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new NasaWorkbenchMenu(id, inv), Component.translatable("block.iis.nasa_workbench")));
        }
        return InteractionResult.SUCCESS;
    }
}

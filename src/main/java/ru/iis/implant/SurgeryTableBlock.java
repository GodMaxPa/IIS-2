package ru.iis.implant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ru.iis.lv.block.LvMachineBlock;

/** Хирургический стол (D157): ПКМ имплантом — установка, ПКМ пустой рукой — состояние тела и снятие. */
public class SurgeryTableBlock extends LvMachineBlock {
    public SurgeryTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SurgeryTableBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (Implants.kindOf(stack) != null && level.getBlockEntity(pos) instanceof SurgeryTableBlockEntity be) {
            if (!level.isClientSide()) be.beginInstall(player, stack);
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof SurgeryTableBlockEntity be) be.report(sp);
        return InteractionResult.SUCCESS;
    }
}

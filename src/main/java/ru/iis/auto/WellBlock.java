package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ru.iis.machine.block.MachineBlock;

/** Колодец GT (GT_MetaTileEntity_Well, D133): без энергии, ПКМ ведром или капсулой забирает 1000 л. */
public class WellBlock extends MachineBlock {
    public WellBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WellBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof WellBlockEntity well && WellBlockEntity.isContainer(stack)) {
            if (!level.isClientSide()) well.fill(player, hand, stack);
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WellBlockEntity well) {
            player.sendOverlayMessage(well.fluid == null ? Component.translatable("message.iis.well.empty", well.amount, WellBlockEntity.CAPACITY)
                    : Component.translatable("message.iis.well.state", well.fluid.getFluidType().getDescription(), well.amount));
        }
        return InteractionResult.SUCCESS;
    }
}

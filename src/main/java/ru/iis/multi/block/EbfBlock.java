package ru.iis.multi.block;

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

/** Контроллер электрической доменной печи. Киянкой — включить/выключить (как в GT5). */
public class EbfBlock extends MachineBlock {
    public EbfBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (MaintenanceHatchBlockEntity.problemFor(stack) != ru.iis.multi.logic.Maintenance.Problem.SOFT_HAMMER) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EbfBlockEntity e) {
            e.allowedToWork = !e.allowedToWork;
            e.setChanged();
            player.sendOverlayMessage(Component.translatable(e.allowedToWork ? "message.iis.machine.enabled" : "message.iis.machine.disabled"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EbfBlockEntity(pos, state);
    }
}

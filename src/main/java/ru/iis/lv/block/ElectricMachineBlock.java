package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import ru.iis.lv.LvMachineType;
import ru.iis.machine.block.Wrench;
import ru.iis.net.IISNetwork;

public class ElectricMachineBlock extends LvMachineBlock {
    public final LvMachineType type;

    public ElectricMachineBlock(LvMachineType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return side != front(state);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricMachineBlockEntity(pos, state);
    }

    /**
     * GT BaseMetaTileEntity.setFrontFacingByWrench (D101): ключ ставит выход предметов на грань по сетке 3×3,
     * Ctrl + ключ — отдельный выход жидкостей. Лицевую сторону поворачивает Shift + ключ ({@link #onRightClickBlock}).
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!Wrench.is(stack) || player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof ElectricMachineBlockEntity be)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide()) {
            boolean fluid = IISNetwork.isCtrlPressed(player);
            Direction side = Wrench.wrenchingSide(pos, hit);
            if (be.setOutputSide(side, fluid)) {
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
                if (!player.getAbilities().instabuild) stack.hurtAndBreak(Wrench.DAMAGE, player, hand);
            }
            player.sendOverlayMessage(Component.translatable(fluid ? "message.iis.output.fluids" : "message.iis.output.items",
                    Component.translatable("direction.iis." + (fluid ? be.fluidOutputSide() : be.outputSide()).getName())));
        }
        return InteractionResult.SUCCESS;
    }

    /** GT: Shift + ключ по машине — setMainFacing (лицевая сторона на грань по сетке; только боковые грани). */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!player.isShiftKeyDown() || !Wrench.is(stack) || !(state.getBlock() instanceof ElectricMachineBlock)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide() || !(level.getBlockEntity(event.getPos()) instanceof ElectricMachineBlockEntity be)) return;
        Direction side = Wrench.wrenchingSide(event.getPos(), event.getHitVec());
        if (Wrench.rotate(level, event.getPos(), state, FACING, side, player, event.getHand(), stack)) be.mainFacingChanged();
    }
}

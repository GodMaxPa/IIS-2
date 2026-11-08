package ru.iis.defense;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.block.LvMachineBlock;

/** База турели OMT (D140), уровни 1–5. Ломает и открывает только владелец (OMT: «Are turrets/turret bases breakable by anyone?» = false). */
public class TurretBaseBlock extends LvMachineBlock {
    public final int tier;

    public TurretBaseBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurretBaseBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player p && level.getBlockEntity(pos) instanceof TurretBaseBlockEntity be) be.setOwner(p);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TurretBaseBlockEntity be && !be.isOwner(player) && !player.getAbilities().instabuild) return 0;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TurretBaseBlockEntity be)) return InteractionResult.PASS;
        if (!be.trusted(player)) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.iis.turret.not_owner", be.ownerName()));
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && be.isOwner(player)) be.cycleMode(player);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) player.openMenu(be);
        return InteractionResult.SUCCESS;
    }
}

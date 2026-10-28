package ru.iis.rail.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import ru.iis.energy.EnergyConnectable;

/**
 * Электрический фидер Railcraft ({@code TileElectricFeeder}, D102): принимает EU из проводов GT с любой стороны и питает
 * сеть электрических путей рядом. Входной уровень — до 512 В (sinkTier 3 у IC2-фидера), выше — взрыв, как у машин GT.
 */
public class ElectricFeederBlock extends BaseEntityBlock implements EnergyConnectable {
    public static final MapCodec<ElectricFeederBlock> CODEC = simpleCodec(ElectricFeederBlock::new);

    public ElectricFeederBlock(Properties p) {
        super(p);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ElectricFeederBlockEntity be) {
            player.sendOverlayMessage(Component.translatable("message.iis.electric_feeder", be.energy.stored, be.energy.capacity));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricFeederBlockEntity(pos, state);
    }
}

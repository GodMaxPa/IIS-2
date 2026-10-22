package ru.iis.fluid;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** Блок универсального резервуара; ёмкость — по исполнению. ПКМ — что внутри; капсулы и вёдра — через обработчик ПКМ. */
public class UniversalTankBlock extends BaseEntityBlock {
    public final int capacity;

    public UniversalTankBlock(int capacity, Properties properties) {
        super(properties);
        this.capacity = capacity;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new UniversalTankBlock(capacity, p));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UniversalTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != FluidContainers.TANK_BE.get()) return null;
        return (l, p, s, be) -> ((UniversalTankBlockEntity) be).serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof UniversalTankBlockEntity t) player.sendOverlayMessage(t.describe());
        return InteractionResult.SUCCESS;
    }

    /** Предмет резервуара: показывает содержимое. */
    public static class TankItem extends BlockItem {
        public TankItem(Block block, Item.Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
            int cap = getBlock() instanceof UniversalTankBlock b ? b.capacity : 0;
            FluidContent c = stack.get(FluidContainers.CONTENT.get());
            if (c != null && !c.isEmpty()) {
                builder.accept(Component.translatable("item.iis.tank.content", c.fluidValue().getFluidType().getDescription(), c.amount(), cap).withStyle(ChatFormatting.GRAY));
            } else {
                builder.accept(Component.translatable("item.iis.tank.empty", cap).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}

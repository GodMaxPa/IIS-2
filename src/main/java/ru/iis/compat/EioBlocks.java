package ru.iis.compat;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Вещи Ender IO 2.3 из ИИС 1, которых нет в новом Ender IO 9 (D135): предметный буфер, банк конденсаторов (базовый), электролампа и простая лампа.
 * Рецепты — EnderIO.zs ИИС 1. Энергия — FE, как у нового мода: провода EU питают их через FeBridge, как машины Ender IO.
 */
public final class EioBlocks {
    private EioBlocks() {}

    // --- предметный буфер: 9 ячеек, трубы и провода кладут и забирают с любой стороны (EIO BlockBuffer, item) ------------------------
    public static class BufferBlock extends BaseEntityBlock {
        public BufferBlock(Properties p) {
            super(p);
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(BufferBlock::new);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new BufferEntity(pos, state);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BufferEntity be) player.openMenu(be);
            return InteractionResult.SUCCESS;
        }
    }

    public static class BufferEntity extends BaseContainerBlockEntity {
        private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);

        public BufferEntity(BlockPos pos, BlockState state) {
            super(AeioContent.BUFFER_BE.get(), pos, state);
        }

        @Override
        protected Component getDefaultName() {
            return Component.translatable("block.iis.item_buffer");
        }

        @Override
        protected NonNullList<ItemStack> getItems() {
            return items;
        }

        @Override
        protected void setItems(NonNullList<ItemStack> list) {
            items = list;
        }

        @Override
        protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
            return new DispenserMenu(id, inventory, this);
        }

        @Override
        public int getContainerSize() {
            return 9;
        }

        @Override
        public void preRemoveSideEffects(BlockPos pos, BlockState state) {
            if (level != null) Containers.dropContents(level, pos, this);
        }

        @Override
        protected void saveAdditional(ValueOutput out) {
            super.saveAdditional(out);
            net.minecraft.world.ContainerHelper.saveAllItems(out, items);
        }

        @Override
        protected void loadAdditional(ValueInput in) {
            super.loadAdditional(in);
            items = NonNullList.withSize(9, ItemStack.EMPTY);
            net.minecraft.world.ContainerHelper.loadAllItems(in, items);
        }
    }

    // --- лампы: простая горит от красного сигнала, электрическая — пока есть энергия (EIO BlockElectricLight 0 и 2) -------------------
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public static class LightBlock extends BaseEntityBlock {
        public final boolean electric;

        public LightBlock(boolean electric, Properties p) {
            super(p.lightLevel(s -> s.getValue(LIT) ? 15 : 0));
            this.electric = electric;
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(p -> new LightBlock(electric, p));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(LIT);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(LIT, !electric && ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
            if (electric || level.isClientSide()) return;
            boolean on = level.hasNeighborSignal(pos);
            if (on != state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, on), 3);
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return electric ? new PoweredEntity(pos, state) : null;
        }

        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return !electric || level.isClientSide() ? null : createTickerHelper(type, AeioContent.POWERED_BE.get(), PoweredEntity::tick);
        }
    }

    // --- банк конденсаторов и электролампа: хранилище FE --------------------------------------------------------------------------
    public static class CapBankBlock extends BaseEntityBlock {
        public CapBankBlock(Properties p) {
            super(p);
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(CapBankBlock::new);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new PoweredEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide() ? null : createTickerHelper(type, AeioContent.POWERED_BE.get(), PoweredEntity::tick);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PoweredEntity be) {
                player.sendOverlayMessage(Component.translatable("message.iis.cap_bank", be.energy.getAmountAsLong(), be.energy.getCapacityAsLong()));
            }
            return InteractionResult.SUCCESS;
        }
    }

    /**
     * Базовый банк конденсаторов EIO 2.3: 1 000 000 RF, ввод и вывод до 1000 RF/т (1 FE = 1 RF, как в D125); отдаёт энергию соседям, которые её берут.
     * Электролампа: 1 FE/т, горит, пока в ней есть энергия.
     */
    public static class PoweredEntity extends BlockEntity {
        public static final int BANK = 1_000_000, BANK_IO = 1000, LAMP = 1000;
        public final SimpleEnergyHandler energy;

        public PoweredEntity(BlockPos pos, BlockState state) {
            super(AeioContent.POWERED_BE.get(), pos, state);
            boolean bank = state.getBlock() instanceof CapBankBlock;
            energy = bank ? new SimpleEnergyHandler(BANK, BANK_IO, BANK_IO) {
                @Override
                protected void onEnergyChanged(int previous) {
                    setChanged();
                }
            } : new SimpleEnergyHandler(LAMP, LAMP, 0) {
                @Override
                protected void onEnergyChanged(int previous) {
                    setChanged();
                }
            };
        }

        public static void tick(Level level, BlockPos pos, BlockState state, PoweredEntity be) {
            if (state.getBlock() instanceof CapBankBlock) {
                for (Direction d : Direction.values()) {
                    if (be.energy.getAmountAsLong() <= 0) break;
                    BlockEntity other = level.getBlockEntity(pos.relative(d));
                    if (other instanceof PoweredEntity p && p.getBlockState().getBlock() instanceof CapBankBlock) continue;
                    EnergyHandler to = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(d), d.getOpposite());
                    if (to != null) EnergyHandlerUtil.move(be.energy, to, BANK_IO, null);
                }
            } else {
                boolean lit = be.energy.getAmountAsLong() > 0;
                if (lit) be.energy.set((int) be.energy.getAmountAsLong() - 1);
                if (state.getValue(LIT) != lit) level.setBlock(pos, state.setValue(LIT, lit), 3);
            }
        }

        @Override
        protected void saveAdditional(ValueOutput out) {
            super.saveAdditional(out);
            energy.serialize(out);
        }

        @Override
        protected void loadAdditional(ValueInput in) {
            super.loadAdditional(in);
            energy.deserialize(in);
        }
    }
}

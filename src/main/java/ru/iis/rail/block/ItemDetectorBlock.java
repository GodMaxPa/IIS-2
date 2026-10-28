package ru.iis.rail.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Детектор предметов Railcraft ({@code DetectorItem}): сигнал 15, когда рядом (с любой стороны) вагонетка с
 * инвентарём, по режиму: что угодно (по умолчанию), пустая, полная, не пустая, аналоговый (по заполнению).
 * ПКМ меняет режим. Фильтр по предметам (режим FILTERED с окном) не перенесён.
 */
public class ItemDetectorBlock extends Block {
    public enum Mode implements StringRepresentable {
        ANYTHING, EMPTY, FULL, NOT_EMPTY, ANALOG;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final MapCodec<ItemDetectorBlock> CODEC = simpleCodec(ItemDetectorBlock::new);
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    /** TileDetector.SENSITIVITY: на сколько ужать клетку соседа при поиске вагонеток. */
    static final double SENSITIVITY = 0.2;
    static final int INTERVAL = 2;

    public ItemDetectorBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(MODE, Mode.ANYTHING).setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE, POWER);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, INTERVAL);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int power = test(state.getValue(MODE), cartsAround(level, pos));
        if (power != state.getValue(POWER)) level.setBlock(pos, state.setValue(POWER, power), 3);
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** CartTools.getMinecartsOnAllSides. */
    public static List<AbstractMinecart> cartsAround(Level level, BlockPos pos) {
        List<AbstractMinecart> out = new ArrayList<>();
        for (Direction d : Direction.values()) {
            out.addAll(level.getEntitiesOfClass(AbstractMinecart.class, new AABB(pos.relative(d)).deflate(SENSITIVITY), AbstractMinecart::isAlive));
        }
        return out;
    }

    static int test(Mode mode, List<AbstractMinecart> carts) {
        for (AbstractMinecart cart : carts) {
            if (!(cart instanceof Container inv) || inv.getContainerSize() <= 0) continue;
            switch (mode) {
                case ANYTHING -> {
                    return 15;
                }
                case EMPTY -> {
                    if (inv.isEmpty()) return 15;
                }
                case FULL -> {
                    if (isFull(inv)) return 15;
                }
                case NOT_EMPTY -> {
                    if (!inv.isEmpty()) return 15;
                }
                case ANALOG -> {
                    return AbstractContainerMenu.getRedstoneSignalFromContainer(inv);
                }
            }
        }
        return 0;
    }

    static boolean isFull(Container inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.isEmpty() || s.getCount() < Math.min(s.getMaxStackSize(), inv.getMaxStackSize())) return false;
        }
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockState next = state.cycle(MODE);
            level.setBlock(pos, next, 3);
            player.sendOverlayMessage(Component.translatable("message.iis.detector.mode",
                    Component.translatable("message.iis.detector.mode." + next.getValue(MODE).getSerializedName())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }
}

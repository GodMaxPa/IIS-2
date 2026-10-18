package ru.iis.machine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;
import ru.iis.IIS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Жидкостная труба GT5-II (GT_MetaPipeEntity_Fluid). Соединения — как в IIS (GT6StyledPipesConnection=true):
 * при установке труба соединяется с соседями, у которых есть бак (и соседние трубы — с ней),
 * дальше соединения переключаются гаечным ключом по стороне, на которую кликнули.
 */
public class PipeBlock extends Block implements EntityBlock {
    /** Размер и материал: пропускная способность mCapacity (л за 1/20 с — GT хранит cap×20, отдаёт до cap×10 раз в 5 тиков). */
    public record Spec(String id, String material, int capacity, int heatResistance, int thickness, int color) {}

    public static final List<Spec> SPECS = List.of(
            new Spec("pipe_small_bronze", "bronze", 40, 2000, 6, 0xFF8000),
            new Spec("pipe_medium_bronze", "bronze", 120, 2000, 8, 0xFF8000),
            new Spec("pipe_large_bronze", "bronze", 240, 2000, 12, 0xFF8000),
            new Spec("pipe_small_steel", "steel", 80, 2500, 6, 0x808080),
            new Spec("pipe_medium_steel", "steel", 240, 2500, 8, 0x808080),
            new Spec("pipe_large_steel", "steel", 480, 2500, 12, 0x808080));

    public static final Map<Direction, BooleanProperty> PROPS = net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION;
    public static final TagKey<Item> WRENCH = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IIS.MODID, "tools/wrench"));

    public final Spec spec;
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public PipeBlock(Spec spec, Properties properties) {
        super(properties);
        this.spec = spec;
        BlockState def = stateDefinition.any();
        for (BooleanProperty p : PROPS.values()) def = def.setValue(p, false);
        registerDefaultState(def);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PROPS.values().forEach(builder::add);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.computeIfAbsent(state, this::makeShape);
    }

    private VoxelShape makeShape(BlockState state) {
        double lo = (16 - spec.thickness()) / 2.0, hi = (16 + spec.thickness()) / 2.0;
        VoxelShape shape = Block.box(lo, lo, lo, hi, hi, hi);
        for (Map.Entry<Direction, BooleanProperty> e : PROPS.entrySet()) {
            if (!state.getValue(e.getValue())) continue;
            Direction d = e.getKey();
            double x0 = d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? hi : lo, x1 = d.getStepX() < 0 ? lo : d.getStepX() > 0 ? 16 : hi;
            double y0 = d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? hi : lo, y1 = d.getStepY() < 0 ? lo : d.getStepY() > 0 ? 16 : hi;
            double z0 = d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? hi : lo, z1 = d.getStepZ() < 0 ? lo : d.getStepZ() > 0 ? 16 : hi;
            shape = Shapes.or(shape, Block.box(x0, y0, z0, x1, y1, z1));
        }
        return shape;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState();
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        for (Direction d : Direction.values()) {
            if (canConnectTo(level, pos, d)) s = s.setValue(PROPS.get(d), true);
        }
        return s;
    }

    private static boolean canConnectTo(Level level, BlockPos pos, Direction d) {
        BlockPos n = pos.relative(d);
        if (level.getBlockState(n).getBlock() instanceof PipeBlock) return true;
        return level.getCapability(Capabilities.Fluid.BLOCK, n, d.getOpposite()) != null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) connectNeighbourPipes(level, pos);
    }

    /** Соседние трубы подключаются к только что поставленному блоку. */
    public static void connectNeighbourPipes(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState ns = level.getBlockState(n);
            if (ns.getBlock() instanceof PipeBlock) {
                BooleanProperty p = PROPS.get(d.getOpposite());
                if (!ns.getValue(p)) level.setBlock(n, ns.setValue(p, true), 3);
            }
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(WRENCH)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            Direction side = hit.getDirection();
            boolean on = !state.getValue(PROPS.get(side));
            level.setBlock(pos, state.setValue(PROPS.get(side), on), 3);
            BlockPos n = pos.relative(side);
            BlockState ns = level.getBlockState(n);
            if (ns.getBlock() instanceof PipeBlock) level.setBlock(n, ns.setValue(PROPS.get(side.getOpposite()), on), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.4f);
            player.sendOverlayMessage(Component.translatable(on ? "message.iis.pipe.connected" : "message.iis.pipe.disconnected"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (l, p, s, be) -> {
            if (be instanceof PipeBlockEntity pipe) pipe.serverTick();
        };
    }
}

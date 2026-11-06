package ru.iis.machine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Предметная труба GT5-II (GT_MetaPipeEntity_Item, D123). Соединения — как у жидкостных: при установке с соседями, у которых есть инвентарь, и с соседними предметными трубами,
 * дальше гаечным ключом по стороне. Ограничительная труба в 100 раз «дальше» обычной: предметы идут в неё в последнюю очередь.
 */
public class ItemPipeBlock extends Block implements EntityBlock {
    /** slots — порций за секунду и размер инвентаря; step — «длина» трубы для маршрута (GT mStepSize). */
    public record Spec(String id, String material, int slots, int step, boolean restrictive, int thickness) {}

    /** GT_Loader_MetaTileEntities:1620-1628 generateItemPipes: материал и базовое число слотов. */
    public static final Object[][] MATERIALS = {{"brass", 1}, {"electrum", 2}, {"platinum", 4}, {"osmium", 8}, {"polyvinyl_chloride", 4}, {"wrought_iron", 1}, {"nickel", 1}, {"cobalt", 2}, {"aluminium", 2}};
    public static final List<Spec> SPECS = buildSpecs();

    /** GT generateItemPipes: обычная 32768 / база, большая 16384 / база, огромная 8192 / база, ограничительная × 100; слоты база, × 2, × 4; толщина 0,5, 0,75, 0,875. */
    private static List<Spec> buildSpecs() {
        List<Spec> l = new ArrayList<>();
        String[] sizes = {"medium", "large", "huge"};
        int[] mul = {1, 2, 4}, thick = {8, 12, 14}, step = {32768, 16384, 8192};
        for (Object[] m : MATERIALS) {
            int base = (Integer) m[1];
            for (int r = 0; r < 2; r++) {
                for (int i = 0; i < 3; i++) {
                    String id = "item_pipe_" + (r == 1 ? "restrictive_" : "") + sizes[i] + "_" + m[0];
                    l.add(new Spec(id, (String) m[0], base * mul[i], step[i] / base * (r == 1 ? 100 : 1), r == 1, thick[i]));
                }
            }
        }
        return List.copyOf(l);
    }

    public static final Map<Direction, BooleanProperty> PROPS = PipeBlock.PROPS;

    public final Spec spec;
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public ItemPipeBlock(Spec spec, Properties properties) {
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
        for (Direction d : Direction.values()) {
            if (canConnectTo(ctx.getLevel(), ctx.getClickedPos(), d)) s = s.setValue(PROPS.get(d), true);
        }
        return s;
    }

    private static boolean canConnectTo(Level level, BlockPos pos, Direction d) {
        BlockPos n = pos.relative(d);
        if (level.getBlockState(n).getBlock() instanceof ItemPipeBlock) return true;
        return level.getCapability(Capabilities.Item.BLOCK, n, d.getOpposite()) != null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide() || oldState.is(this)) return;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState ns = level.getBlockState(n);
            BooleanProperty p = PROPS.get(d.getOpposite());
            if (ns.getBlock() instanceof ItemPipeBlock && !ns.getValue(p)) level.setBlock(n, ns.setValue(p, true), 3);
        }
    }

    /** Соседние предметные трубы подключаются к только что поставленной машине, если у неё есть инвентарь с этой стороны. */
    public static void connectNeighbourPipes(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState ns = level.getBlockState(n);
            BooleanProperty p = PROPS.get(d.getOpposite());
            if (ns.getBlock() instanceof ItemPipeBlock && !ns.getValue(p) && level.getCapability(Capabilities.Item.BLOCK, pos, d) != null) level.setBlock(n, ns.setValue(p, true), 3);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ru.iis.cover.CoverHost host) {
            InteractionResult r = ru.iis.cover.Covers.use(host, stack, player, hand, hit, null);
            if (r != null) return r;
        }
        if (!Wrench.is(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            Direction side = Wrench.wrenchingSide(pos, hit);
            boolean on = !state.getValue(PROPS.get(side));
            level.setBlock(pos, state.setValue(PROPS.get(side), on), 3);
            BlockPos n = pos.relative(side);
            BlockState ns = level.getBlockState(n);
            if (ns.getBlock() instanceof ItemPipeBlock) level.setBlock(n, ns.setValue(PROPS.get(side.getOpposite()), on), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.4f);
            player.sendOverlayMessage(Component.translatable(on ? "message.iis.pipe.connected" : "message.iis.pipe.disconnected"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ru.iis.cover.CoverHost host) {
            InteractionResult r = ru.iis.cover.Covers.use(host, ItemStack.EMPTY, player, InteractionHand.MAIN_HAND, hit, null);
            if (r != null) return r;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ru.iis.cover.CoverHost h ? h.covers().redstoneOut(direction.getOpposite()) : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (l, p, s, be) -> {
            if (be instanceof ItemPipeBlockEntity pipe) pipe.serverTick();
        };
    }
}

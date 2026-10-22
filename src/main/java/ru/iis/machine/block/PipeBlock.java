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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Жидкостная труба GT5-II (GT_MetaPipeEntity_Fluid). Соединения — как в IIS (GT6StyledPipesConnection=true):
 * при установке труба соединяется с соседями, у которых есть бак (и соседние трубы — с ней),
 * дальше соединения переключаются гаечным ключом по стороне, на которую кликнули.
 */
public class PipeBlock extends Block implements EntityBlock {
    /**
     * Размер и материал: пропускная способность mCapacity (л за 1/20 с — GT хранит cap×20, отдаёт до cap×10 раз в 5 тиков),
     * предел температуры (K) и газонепроницаемость (GT_Loader_MetaTileEntities#generateFluidPipes, D72).
     */
    public record Spec(String id, String material, int capacity, int heatResistance, int thickness, int color, boolean gasProof) {
        public Spec(String id, String material, int capacity, int heatResistance, int thickness, int color) {
            this(id, material, capacity, heatResistance, thickness, color, true);
        }
    }

    /** GT_Loader_MetaTileEntities: (материал, базовая способность, предел K, газонепроницаемость, цвет); tiny ÷6, small ÷3, large ×2, huge ×4. */
    private static final Object[][] GT_PIPES = {
            {"copper", 60, 1000, true, 0xFF6400}, {"bronze", 120, 2000, true, 0xFF8000}, {"steel", 240, 2500, true, 0x808080},
            {"stainless_steel", 360, 3000, true, 0xC8C8DC}, {"titanium", 480, 5000, true, 0xDCA0F0}, {"tungsten_steel", 600, 7500, true, 0x6464A0},
            {"plastic", 360, 350, true, 0xC8C8C8}, {"polytetrafluoroethylene", 480, 600, true, 0x646464}};

    public static final List<Spec> SPECS = buildSpecs();

    private static List<Spec> buildSpecs() {
        List<Spec> l = new java.util.ArrayList<>();
        // деревянные — отдельно (GT_Loader_MetaTileEntities:1529–1531): 10 / 30 / 60, 350 K, пропускают газ
        l.add(new Spec("pipe_small_wood", "wood", 10, 350, 6, 0x643200, false));
        l.add(new Spec("pipe_medium_wood", "wood", 30, 350, 8, 0x643200, false));
        l.add(new Spec("pipe_large_wood", "wood", 60, 350, 12, 0x643200, false));
        String[] sizes = {"tiny", "small", "medium", "large", "huge"};
        int[] thick = {4, 6, 8, 12, 14};
        for (Object[] m : GT_PIPES) {
            int base = (Integer) m[1];
            int[] cap = {base / 6, base / 3, base, base * 2, base * 4};
            for (int i = 0; i < sizes.length; i++) {
                l.add(new Spec("pipe_" + sizes[i] + "_" + m[0], (String) m[0], cap[i], (Integer) m[2], thick[i], (Integer) m[4], (Boolean) m[3]));
            }
        }
        return List.copyOf(l);
    }

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
            Direction side = Wrench.wrenchingSide(pos, hit); // GT: сетка на грани трубы
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

    /** GT_MetaPipeEntity_Fluid#onEntityCollidedWithBlock: горячая (&gt;320 K) или холодная (&lt;260 K) жидкость обжигает. */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (!level.isClientSide() && entity instanceof net.minecraft.world.entity.LivingEntity living
                && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) pipe.touch(living);
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

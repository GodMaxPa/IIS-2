package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import ru.iis.energy.EnergyConnectable;
import ru.iis.energy.IISDamage;
import ru.iis.energy.logic.EnergyNet;
import ru.iis.lv.CableSpecs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Провод или кабель GT5-II (GT_MetaPipeEntity_Cable). Соединения как в ИИС (GT6StyledWiresConnection=false):
 * провод сам подключается к соседним проводам и машинам, принимающим или отдающим EU с этой стороны.
 * Неизолированный провод бьёт током того, кто его касается (урон по напряжению и току за последнюю секунду).
 */
public class CableBlock extends Block implements EntityBlock, EnergyConnectable {
    /** Каталог проводов — {@link CableSpecs}. */
    public static final List<CableSpecs.Spec> SPECS = CableSpecs.ALL;

    public static final Map<Direction, BooleanProperty> PROPS = net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION;

    public final CableSpecs.Spec spec;
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public CableBlock(CableSpecs.Spec spec, Properties properties) {
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
    public boolean connectsEnergy(BlockState state, Direction side) {
        return true;
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

    public static boolean canConnect(BlockState neighbour, Direction sideOfNeighbour) {
        return neighbour.getBlock() instanceof EnergyConnectable c && c.connectsEnergy(neighbour, sideOfNeighbour);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState();
        for (Direction d : Direction.values()) {
            BlockState n = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(d));
            s = s.setValue(PROPS.get(d), canConnect(n, d.getOpposite()));
        }
        return s;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return state.setValue(PROPS.get(dir), canConnect(neighbour, dir.getOpposite()));
    }

    /** GT5 onEntityCollidedWithBlock + GT_Utility#applyElectricityDamage: только голые провода. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
        if (spec.insulated() || !ru.iis.IISConfig.on(ru.iis.IISConfig.SHOCK) || !(level instanceof ServerLevel sl) || !(entity instanceof LivingEntity living)) return;
        if (!(level.getBlockEntity(pos) instanceof CableBlockEntity be)) return;
        if (sl.getGameTime() - be.state.lastTick() > 20) return;
        float dmg = EnergyNet.shockDamage(be.state.voltageLast20, be.state.amperageLast20);
        if (be.state.voltageLast20 > 0 && dmg > 0) living.hurtServer(sl, sl.damageSources().source(IISDamage.ELECTRICITY), dmg);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }
}

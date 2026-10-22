package ru.iis.bio.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
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
import ru.iis.bio.BioContent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Живой сосуд Био (спецификация v0.7 §20.2, §22.7, D72–D73): участок гидравлической сети. Соединяется сам со всеми соседями,
 * у которых есть ёмкость. Течение считает решатель сети {@link ru.iis.bio.hydraulics.VesselNetwork}: напор насоса,
 * сопротивление участков, высота, пропускная способность, давление на стенку и разрыв. Клапан и зажим — его разновидности.
 */
public class VesselBlock extends BaseEntityBlock {
    /**
     * Исполнение участка: id, класс, Q_cap мБ/т, объём мБ, поддержание N0 мБ/т, толщина px, цвет, R атм·т/мБ (чистая N0,
     * 25 °C), безопасное и разрывное избыточное давление, атм; живой (тратит N0 и голодает).
     */
    public record Spec(String id, String tier, int flow, int volume, double upkeep, int thickness, int color, double r, double pSafe, double pBurst,
                       boolean living) {}

    public static final List<Spec> SPECS = List.of(
            new Spec("tissue_microchannel", "ULV", 4, 10, 0.0005, 4, 0xC0505A, 0.0025, 0.050, 0.080, true),
            new Spec("capillary_bundle", "ULV", 16, 40, 0.0010, 6, 0xB03848, 0.000625, 0.050, 0.080, true),
            new Spec("narrow_fibrous_vessel", "LV", 32, 100, 0.0015, 8, 0xA04050, 0.00025, 0.200, 0.320, true),
            new Spec("working_fibrous_vessel", "LV", 128, 400, 0.0030, 12, 0x903040, 0.0000625, 0.200, 0.320, true));
    /** Обратный клапан LV (§20.4): порог открытия 0,002 атм, открытый R=0,00003125, Q_cap 128. Поддержание — как узкого сосуда (не задано). */
    public static final Spec CHECK_VALVE = new Spec("check_valve", "LV", 128, 50, 0.0015, 10, 0x8A3048, 0.00003125, 0.200, 0.320, true);
    /** Ручной пережим ULV, неживой (§20.4, §22.7): добавочное сопротивление ступенями до полного закрытия. */
    public static final Spec CLAMP = new Spec("manual_clamp", "ULV", 16, 10, 0, 6, 0xB03848, 0.000625, 0.050, 0.080, false);

    public static final Map<Direction, BooleanProperty> PROPS = net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION;

    public final Spec spec;
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public VesselBlock(Spec spec, Properties properties) {
        super(properties);
        this.spec = spec;
        BlockState def = stateDefinition.any();
        for (BooleanProperty p : PROPS.values()) def = def.setValue(p, false);
        registerDefaultState(def);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new VesselBlock(spec, p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PROPS.values().forEach(builder::add);
    }

    // --- гидравлика участка ------------------------------------------------------------------------------------------
    /** Сопротивление участка при чистой N0, атм·т/мБ; бесконечность — закрыт. */
    public double resistance(BlockState state) {
        return spec.r();
    }

    /** Порог открытия (обратный клапан), атм. */
    public double threshold(BlockState state) {
        return 0;
    }

    /** Может ли жидкость войти в участок через эту сторону. */
    public boolean entry(BlockState state, Direction side) {
        return state.getValue(PROPS.get(side));
    }

    /** Может ли жидкость выйти из участка через эту сторону. */
    public boolean exit(BlockState state, Direction side) {
        return state.getValue(PROPS.get(side));
    }

    /** Соединяется ли участок с этой стороной вообще (клапан — только по своей оси). */
    protected boolean sideAllowed(BlockState state, Direction side) {
        return true;
    }

    // --- форма и соединения ------------------------------------------------------------------------------------------
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

    protected boolean connects(BlockState self, LevelReader level, BlockPos pos, Direction d) {
        if (!sideAllowed(self, d)) return false;
        BlockPos n = pos.relative(d);
        BlockState ns = level.getBlockState(n);
        if (ns.getBlock() instanceof VesselBlock v) return v.sideAllowed(ns, d.getOpposite());
        return level instanceof Level l && l.getCapability(Capabilities.Fluid.BLOCK, n, d.getOpposite()) != null;
    }

    protected BlockState placementBase(BlockPlaceContext ctx) {
        return defaultBlockState();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = placementBase(ctx);
        for (Direction d : Direction.values()) s = s.setValue(PROPS.get(d), connects(s, ctx.getLevel(), ctx.getClickedPos(), d));
        return s;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random) {
        return state.setValue(PROPS.get(dir), connects(state, level, pos, dir));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VesselBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != BioContent.VESSEL_BE.get()) return null;
        return (l, p, s, be) -> ((VesselBlockEntity) be).serverTick();
    }

    /** ПКМ: давление, поток, износ и состояние сети. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VesselBlockEntity v) v.describe().forEach(player::sendSystemMessage);
        return InteractionResult.SUCCESS;
    }

    /** Мягкая ткань — заплатка: −20 износа (при D ≥ 80 только замена), §20.11. */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ru.iis.bio.item.BioItems.SOFT_TISSUE.get()) || !spec.living()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VesselBlockEntity v) {
            if (v.repair()) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, ru.iis.bio.BioSounds.SQUELCH, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.2f);
            }
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(v.damage() >= 80 ? "message.iis.bio.vessel.replace"
                    : "message.iis.bio.vessel.repaired", String.format(java.util.Locale.ROOT, "%.1f", v.damage())));
        }
        return InteractionResult.SUCCESS;
    }
}

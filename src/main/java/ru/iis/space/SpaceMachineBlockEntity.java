package ru.iis.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.iis.fluid.GtFluids;
import ru.iis.machine.block.FluidTank;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Кислородный сборщик, распределитель, загрузчик и разгрузчик груза (D134).
 * Сборщик (GC TileEntityOxygenCollector): там, где есть воздух, 1 л в тик; на безвоздушных планетах от листвы и растений в кубе 11 × 11 × 11,
 * 1/20 л за блок в тик, не больше 1 л. Распределитель (GC TileEntityOxygenDistributor): 1 л в тик держит пузырь, он растёт на блок в секунду до 10.
 * Загрузчик и разгрузчик (GC TileEntityCargoLoader/Unloader): раз в 10 тиков по одному предмету между соседним сундуком и ракетой рядом.
 * Числа сборщика и распределителя — по памяти о GC, в сборке ИИС 1 их нет.
 */
public class SpaceMachineBlockEntity extends BlockEntity {
    public static final int TANK = 4000, MAX_BUBBLE = 10;
    /** Работающие распределители по измерениям: центр и радиус. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> BUBBLES = new HashMap<>();

    public final FluidTank tank;
    private int bubble, rateTenths;

    public SpaceMachineBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContent.SPACE_MACHINE_BE.get(), pos, state);
        boolean distributor = kind(state) == SpaceMachineBlock.Kind.DISTRIBUTOR;
        tank = new FluidTank(TANK, f -> distributor && f == oxygen(), !distributor, this::setChanged);
    }

    private static SpaceMachineBlock.Kind kind(BlockState s) {
        return s.getBlock() instanceof SpaceMachineBlock b ? b.kind : SpaceMachineBlock.Kind.LOADER;
    }

    public SpaceMachineBlock.Kind kind() {
        return kind(getBlockState());
    }

    static Fluid oxygen() {
        return GtFluids.get("oxygen").source().get();
    }

    public int bubble() {
        return bubble;
    }

    /** Литров в секунду. */
    public int rate() {
        return rateTenths * 2;
    }

    /** Внутри ли точка пузыря какого-нибудь распределителя этого измерения. */
    public static boolean inBubble(Level level, Vec3 at) {
        Map<BlockPos, Integer> m = BUBBLES.get(level.dimension());
        if (m == null) return false;
        for (Map.Entry<BlockPos, Integer> e : m.entrySet()) {
            if (Vec3.atCenterOf(e.getKey()).distanceToSqr(at) <= (double) e.getValue() * e.getValue()) return true;
        }
        return false;
    }

    static void forget(Level level, BlockPos pos) {
        Map<BlockPos, Integer> m = BUBBLES.get(level.dimension());
        if (m != null) m.remove(pos);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpaceMachineBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        switch (be.kind()) {
            case COLLECTOR -> be.collect(sl);
            case DISTRIBUTOR -> be.distribute(sl);
            default -> {
                if (sl.getGameTime() % 10 == 0) be.moveCargo(sl, be.kind() == SpaceMachineBlock.Kind.LOADER);
            }
        }
    }

    private int carry;

    private void collect(ServerLevel sl) {
        if (sl.getGameTime() % 20 == 0) {
            if (!OxygenSystem.airless(sl.dimension())) {
                rateTenths = 10;
            } else {
                int plants = 0;
                for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-5, -5, -5), worldPosition.offset(5, 5, 5))) {
                    BlockState s = sl.getBlockState(p);
                    if (s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.CROPS)) plants++;
                }
                rateTenths = Math.min(10, plants / 2);
            }
        }
        carry += rateTenths;
        if (carry >= 10 && tank.amount() < tank.capacity()) {
            tank.setAmount(oxygen(), Math.min(tank.capacity(), tank.amount() + carry / 10));
            carry %= 10;
        }
    }

    private void distribute(ServerLevel sl) {
        boolean fed = tank.amount() > 0;
        if (fed) tank.setAmount(oxygen(), tank.amount() - 1);
        if (sl.getGameTime() % 20 == 0) {
            bubble = fed ? Math.min(MAX_BUBBLE, bubble + 1) : Math.max(0, bubble - 1);
            Map<BlockPos, Integer> m = BUBBLES.computeIfAbsent(sl.dimension(), k -> new HashMap<>());
            if (bubble > 0) m.put(worldPosition.immutable(), bubble);
            else m.remove(worldPosition);
            setChanged();
        }
    }

    private void moveCargo(ServerLevel sl, boolean load) {
        List<RocketEntity> rockets = sl.getEntitiesOfClass(RocketEntity.class, new AABB(worldPosition).inflate(3, 2, 3), r -> r.state() == RocketEntity.IDLE && r.cargoSlots() > 0);
        if (rockets.isEmpty()) return;
        Container rocket = rockets.getFirst().cargo();
        for (Direction d : Direction.values()) {
            Container chest = HopperBlockEntity.getContainerAt(sl, worldPosition.relative(d));
            if (chest == null) continue;
            if (load ? moveOne(chest, rocket) : moveOne(rocket, chest)) return;
        }
    }

    /** Один предмет из первого непустого слота, если ему есть место. */
    static boolean moveOne(Container from, Container to) {
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack s = from.getItem(i);
            if (s.isEmpty()) continue;
            ItemStack one = s.copyWithCount(1);
            for (int j = 0; j < to.getContainerSize(); j++) {
                ItemStack t = to.getItem(j);
                if (t.isEmpty() || (ItemStack.isSameItemSameComponents(t, one) && t.getCount() < t.getMaxStackSize())) {
                    if (t.isEmpty()) to.setItem(j, one);
                    else t.grow(1);
                    s.shrink(1);
                    from.setChanged();
                    to.setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void setRemoved() {
        if (level != null) forget(level, worldPosition);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Oxygen", tank.amount());
        out.putInt("Bubble", bubble);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        int amount = in.getIntOr("Oxygen", 0);
        if (amount > 0) tank.setAmount(oxygen(), amount);
        bubble = in.getIntOr("Bubble", 0);
    }

    static @Nullable Set<BlockPos> debugBubbles(Level level) {
        Map<BlockPos, Integer> m = BUBBLES.get(level.dimension());
        return m == null ? null : new HashSet<>(m.keySet());
    }
}

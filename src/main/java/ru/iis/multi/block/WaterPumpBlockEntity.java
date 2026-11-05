package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.fluid.GtFluids;
import ru.iis.fluid.IISFluids;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.block.PipeBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.menu.EbfMenu;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Водяные насосы ИИС (GT_MetaTileEntity_WaterPumpPrimitive и _WaterPumpElectric LV–IV, D107). Форма 2×2×1: контроллер,
 * сбоку — входной люк пара (примитивный) или энергетический люк, над ним — выходной люк, на контроллере — жидкостная труба
 * материала уровня (бронза, сталь, нержавейка, титан, вольфрамосталь). Сзади контроллера уходит цепочка труб (не длиннее
 * 10 + 4 × T, пропускная способность не меньше выхода насоса) к заборнику своего уровня, стоящему в верхнем слое воды
 * реки (вода), океана (солёная вода) или болота (грязная вода). Насосу нужна водная гладь не меньше 128 + 32 × T блоков
 * в радиусе 16 + 4 × T, иначе выход падает пропорционально; другие насосы на той же глади делят выход (1 / (n + 1)),
 * а ближе 4 блоков друг к другу заборники не ставятся. Каждые 16800 циклов заборник забивается: −1 % выхода.
 */
public class WaterPumpBlockEntity extends MultiblockControllerBlockEntity {
    /** GT_MetaTileEntity_WaterPumpElectric.SPEED и ENERGY по уровню (0 — примитивный). */
    public static final int[] SPEED = {80, 80, 40, 20, 10, 5}, ENERGY = {8, 4, 16, 64, 256, 1024};
    private static final String[] PIPE_MATERIAL = {"bronze", "bronze", "steel", "stainless_steel", "titanium", "tungsten_steel"};
    public static final int CLOG_CYCLES = 16800, STEAM_PER_CYCLE = 40;

    public enum Kind { RIVER, OCEAN, SWAMP }

    /** Для тестов: считать воду у заборника такой, какой бы ни был биом. */
    public @Nullable Kind forcedKind;

    private static final Map<ResourceKey<Level>, Map<Long, WaterPumpBlockEntity>> PUMPS = new ConcurrentHashMap<>();

    private @Nullable BlockPos head;
    private @Nullable Kind kind;
    private int waterSurface, workCycles, connectedPumps;
    private double pumpingEfficiency, efficiencyRate = 1d, waterToOutput;

    public WaterPumpBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.WATER_PUMP_BE.get(), pos, state);
    }

    /** 0 — примитивный, 1…5 — LV…IV. */
    public int tier() {
        return Math.max(0, MultiContent.index(MultiContent.WATER_PUMPS, getBlockState().getBlock()));
    }

    public int radius() {
        return tier() == 0 ? 16 : 16 + 4 * tier();
    }

    public int surfaceBlocksNeeded() {
        return tier() == 0 ? 128 : 128 + 32 * tier();
    }

    public int pipeLength() {
        return tier() == 0 ? 10 : 10 + 4 * tier();
    }

    /** Литров за тик при полном КПД. */
    public double outputRate() {
        return tier() == 0 ? 6.25d : 1000d / SPEED[tier()];
    }

    public int waterSurface() {
        return waterSurface;
    }

    public double pumpingEfficiency() {
        return pumpingEfficiency;
    }

    public @Nullable Kind kind() {
        return kind;
    }

    // --- форма --------------------------------------------------------------------------------------------------------
    @Override
    boolean checkStructure(ServerLevel sl) {
        boolean ok = check(sl);
        if (!ok) unregister();
        return ok;
    }

    @Override
    public void setRemoved() {
        unregister();
        super.setRemoved();
    }

    private void unregister() {
        if (head != null && level != null) {
            Map<Long, WaterPumpBlockEntity> m = PUMPS.get(level.dimension());
            if (m != null) m.remove(key(head.getX(), head.getZ()), this);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private boolean check(ServerLevel sl) {
        parts.clear();
        maintenance.setMask(63);
        Direction front = getBlockState().getValue(MachineBlock.FACING), main = front.getOpposite();
        Direction side = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d == front || d == main) continue;
            if (sideParts(sl, d)) {
                side = d;
                break;
            }
        }
        if (side == null) return false;
        if (!(sl.getBlockState(worldPosition.above()).getBlock() instanceof PipeBlock top) || !top.spec.material().equals(PIPE_MATERIAL[tier()])) return false;
        // цепочка труб от задней стороны контроллера до заборника (GT checkHead)
        BlockPos p = worldPosition.relative(main);
        BlockState ps = sl.getBlockState(p);
        if (!(ps.getBlock() instanceof PipeBlock) || !ps.getValue(PipeBlock.PROPS.get(main.getOpposite()))) return false;
        BlockPos found = checkHead(sl, p, main, 0);
        if (found == null) return false;
        Holder<Biome> biome = sl.getBiome(found);
        Kind k = kindOf(biome);
        if (k == null) return false;
        unregister();
        head = found;
        kind = k;
        Set<WaterPumpBlockEntity> affected = new HashSet<>();
        waterSurface = fill(sl, affected);
        for (WaterPumpBlockEntity o : affected) {
            if (o.head != null && Math.abs(head.getX() - o.head.getX()) <= 4 && Math.abs(head.getZ() - o.head.getZ()) <= 4) return false;
        }
        PUMPS.computeIfAbsent(sl.dimension(), d -> new ConcurrentHashMap<>()).put(key(head.getX(), head.getZ()), this);
        connectedPumps = affected.size();
        recalculateEfficiency();
        return true;
    }

    /** GT constructStructure / addToStructure: сбоку — вход (пар или энергия), над ним — выходной люк. */
    private boolean sideParts(ServerLevel sl, Direction d) {
        BlockPos in = worldPosition.relative(d), out = in.above();
        HatchType ti = hatchAt(sl, in), to = hatchAt(sl, out);
        if (to != HatchType.OUTPUT_HATCH) return false;
        if (tier() == 0) {
            if (ti != HatchType.INPUT_HATCH) return false;
            parts.inputHatches.add(in);
        } else {
            if (ti == null || !ti.energy() || ti.dynamo()) return false;
            parts.energy.add(in);
        }
        parts.outputHatches.add(out);
        return true;
    }

    private @Nullable Kind kindOf(Holder<Biome> b) {
        if (forcedKind != null) return forcedKind;
        if (b.is(BiomeTags.IS_RIVER)) return Kind.RIVER;
        if (b.is(BiomeTags.IS_OCEAN)) return Kind.OCEAN;
        if (b.is(net.neoforged.neoforge.common.Tags.Biomes.IS_SWAMP)) return Kind.SWAMP;
        return null;
    }

    private Block intake() {
        return MultiContent.PUMP_INTAKES.get(tier()).get();
    }

    /**
     * GT checkHead: труба — не больше двух соединений, способность не меньше выхода; идём к следующей трубе, а в конце
     * цепочки рядом с последней трубой должен стоять ровно один заборник своего уровня. Возвращает его место.
     */
    private @Nullable BlockPos checkHead(ServerLevel sl, BlockPos pos, Direction from, int depth) {
        if (depth > pipeLength()) return null;
        BlockState s = sl.getBlockState(pos);
        if (!(s.getBlock() instanceof PipeBlock pipe) || pipe.spec.capacity() < outputRate()) return null;
        Direction back = from.getOpposite(), next = null;
        int connections = 0;
        for (Direction d : Direction.values()) {
            if (!s.getValue(PipeBlock.PROPS.get(d))) continue;
            if (d != back) next = d;
            connections++;
        }
        if (connections > 2) return null;
        if (next != null && sl.getBlockState(pos.relative(next)).getBlock() instanceof PipeBlock) {
            BlockPos r = checkHead(sl, pos.relative(next), next, depth + 1);
            if (r != null) return r;
        }
        BlockPos only = null;
        int count = 0;
        for (Direction d : Direction.values()) {
            if (sl.getBlockState(pos.relative(d)).is(intake())) {
                count++;
                only = pos.relative(d);
            }
        }
        return count == 1 ? only : null;
    }

    /** GT fillList: водная гладь на уровне заборника в радиусе, того же вида воды; другие насосы на ней. */
    private int fill(ServerLevel sl, Set<WaterPumpBlockEntity> affected) {
        Map<Long, WaterPumpBlockEntity> pumps = PUMPS.getOrDefault(sl.dimension(), Map.of());
        int y = head.getY(), r = radius(), count = 0;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{head.getX(), head.getZ(), 0});
        seen.add(key(head.getX(), head.getZ()));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            count++;
            WaterPumpBlockEntity o = pumps.get(key(c[0], c[1]));
            if (o != null && o != this && !o.isRemoved()) affected.add(o);
            if (c[2] >= 128) continue;
            for (int[] d : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
                int x = c[0] + d[0], z = c[1] + d[1];
                if (Math.abs(head.getX() - x) > r || Math.abs(head.getZ() - z) > r || !seen.add(key(x, z))) continue;
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = sl.getBlockState(p);
                if (!(s.is(Blocks.WATER) || isIntake(s)) || kindOf(sl.getBiome(p)) != kind) continue;
                queue.add(new int[]{x, z, c[2] + 1});
            }
        }
        return count;
    }

    private static boolean isIntake(BlockState s) {
        for (var d : MultiContent.PUMP_INTAKES) if (s.is(d.get())) return true;
        return false;
    }

    /** GT recalculateEfficiency. */
    private void recalculateEfficiency() {
        pumpingEfficiency = 10000d;
        if (connectedPumps > 0) pumpingEfficiency *= 1d / (connectedPumps + 1);
        if (waterSurface < surfaceBlocksNeeded()) pumpingEfficiency *= (double) waterSurface / surfaceBlocksNeeded();
    }

    // --- работа -------------------------------------------------------------------------------------------------------
    @Override
    protected int idleInterval() {
        return 100;
    }

    /** GT checkRecipe: цикл 10 тиков — 40 л пара (примитивный) или ENERGY[T] EU/t при запасе в люке не меньше 10 × ENERGY. */
    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        if (tier() == 0) {
            if (!depleteInput(sl, IISFluids.STEAM.get(), STEAM_PER_CYCLE)) return false;
            eut = 0;
        } else {
            long need = ENERGY[tier()] * 10L;
            if (parts.energy.isEmpty() || !(sl.getBlockEntity(parts.energy.getFirst()) instanceof EnergyHatchBlockEntity e) || e.energy.stored < need) return false;
            eut = ENERGY[tier()];
        }
        if (++workCycles >= CLOG_CYCLES) {
            workCycles = 0;
            efficiencyRate *= 0.99d;
        }
        maxProgress = 10;
        progress = 0;
        efficiencyIncrease = 10000;
        pendingItems = List.of();
        pendingFluids = List.of();
        setChanged();
        return true;
    }

    /** GT onRunningTick: выход = скорость × КПД насоса (+ остаток) × чистота заборника. */
    @Override
    protected boolean runningTick(ServerLevel sl) {
        if (tier() > 0 && !super.runningTick(sl)) return false;
        double out = outputRate() * (pumpingEfficiency / 10000) + waterToOutput;
        out *= efficiencyRate;
        int whole = (int) out;
        waterToOutput = out - whole;
        if (whole > 0 && kind != null) addFluidOutput(sl, fluid(kind), whole);
        return true;
    }

    private static Fluid fluid(Kind k) {
        return switch (k) {
            case RIVER -> Fluids.WATER;
            case OCEAN -> GtFluids.get("saltwater").source().get();
            case SWAMP -> GtFluids.get("dirtywater").source().get();
        };
    }

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return EbfMenu.freezer(id, inventory, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putDouble("effRate", efficiencyRate);
        out.putInt("cycles", workCycles);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        efficiencyRate = in.getDoubleOr("effRate", 1d);
        if (efficiencyRate < 0.0000001d) efficiencyRate = 1d;
        workCycles = in.getIntOr("cycles", 0);
    }
}

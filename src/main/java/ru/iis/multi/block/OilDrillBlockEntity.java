package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ru.iis.machine.recipe.FluidOutput;
import ru.iis.multi.MultiContent;
import ru.iis.world.UndergroundFluids;

import java.util.ArrayList;
import java.util.List;

/**
 * Нефтяная буровая установка I–III (GT_MetaTileEntity_OilDrill1–3, D106). Опускает трубу до бедрока, затем каждый цикл
 * качает подземную жидкость из поля чанков (1×1, 3×3, 6×6 чанков, выровненных по сетке) — той, что лежит под
 * контроллером. Коэффициент добычи из чанка — 1/2, 1/6 и 1/8. Нужны люк обслуживания, выходной люк и энергия не ниже
 * MV, HV и EV. Цикл: 80 тиков при спуске и 320 у дна, делённые на 2 за каждую ступень напряжения; 3 × 4^T EU/t.
 */
public class OilDrillBlockEntity extends DrillerBlockEntity {
    private static final int[] RANGE = {1, 3, 6}, MIN_TIER = {2, 3, 4};
    private final List<ChunkPos> field = new ArrayList<>();
    private String oilId = "";

    public OilDrillBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.OIL_DRILL_BE.get(), pos, state);
    }

    @Override
    protected int level() {
        return Math.max(0, MultiContent.index(MultiContent.OIL_DRILLS, getBlockState().getBlock()));
    }

    @Override
    protected Block casing() {
        return switch (level()) {
            case 0 -> MultiContent.SOLID_STEEL_CASING.get();
            case 1 -> MultiContent.STABLE_TITANIUM_CASING.get();
            default -> MultiContent.ROBUST_TUNGSTENSTEEL_CASING.get();
        };
    }

    @Override
    protected Block frame() {
        return MultiContent.FRAMES.get(switch (level()) {
            case 0 -> "steel";
            case 1 -> "titanium";
            default -> "tungsten_steel";
        }).get();
    }

    @Override
    protected int minTier() {
        return MIN_TIER[level()];
    }

    public int rangeInChunks() {
        return RANGE[level()];
    }

    @Override
    protected boolean checkHatches() {
        return !parts.maintenance.isEmpty() && !parts.outputHatches.isEmpty() && !parts.energy.isEmpty();
    }

    @Override
    protected boolean allowPutPipesToController() {
        return false;
    }

    @Override
    protected void setElectricityStats(ServerLevel sl) {
        int tier = voltageTier(sl);
        setCycle(3 * (1 << (tier << 1)), (workState == STATE_AT_BOTTOM ? 320 : 80) / (1 << tier));
    }

    @Override
    protected boolean workingUpward(ServerLevel sl, int oldYHead) {
        if (returnStoredPipes(sl)) return true;
        return super.workingUpward(sl, oldYHead);
    }

    /** GT workingAtBottom: у дна качать жидкость поля. */
    @Override
    protected boolean workingAtBottom(ServerLevel sl, int oldYHead) {
        if (tryLowerPipe(sl, true) == 0) {
            workState = STATE_DOWNWARD;
            setElectricityStats(sl);
            return true;
        }
        if (reachingVoidOrBedrock(sl) && tryFillChunkList(sl)) {
            int t = minTier();
            float speed = 1f / t / (t > 2 ? 2 : 1);
            UndergroundFluids.Sample s = pumpOil(sl, speed);
            if (s != null && s.amount() > 0) pendingFluids = List.of(new FluidOutput(s.fluid(), s.amount()));
        }
        return true;
    }

    private boolean tryFillChunkList(ServerLevel sl) {
        if (oilId.isEmpty()) {
            UndergroundFluids.Sample s = UndergroundFluids.read(sl, ChunkPos.containing(worldPosition));
            if (s == null) return false;
            oilId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(s.fluid()).toString();
        }
        if (field.isEmpty()) {
            ChunkPos here = ChunkPos.containing(worldPosition);
            int range = rangeInChunks(), x0 = Math.floorDiv(here.x(), range) * range, z0 = Math.floorDiv(here.z(), range) * range;
            for (int i = 0; i < range; i++) {
                for (int j = 0; j < range; j++) {
                    ChunkPos cp = new ChunkPos(x0 + i, z0 + j);
                    UndergroundFluids.Sample s = UndergroundFluids.read(sl, cp);
                    if (s != null && s.amount() > 0 && isOil(s.fluid())) field.add(cp);
                }
            }
        }
        return !field.isEmpty();
    }

    private boolean isOil(Fluid f) {
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f).toString().equals(oilId);
    }

    private UndergroundFluids.Sample pumpOil(ServerLevel sl, float speed) {
        Fluid oil = null;
        int total = 0;
        List<ChunkPos> empty = new ArrayList<>();
        for (ChunkPos cp : field) {
            UndergroundFluids.Sample s = UndergroundFluids.drain(sl, cp, speed);
            if (s == null || s.amount() < 1) empty.add(cp);
            if (s != null && isOil(s.fluid())) {
                oil = s.fluid();
                total += s.amount();
            }
        }
        field.removeAll(empty);
        return oil == null || total == 0 ? null : new UndergroundFluids.Sample(oil, total);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("oil", oilId);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        oilId = in.getStringOr("oil", "");
    }
}

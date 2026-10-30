package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.recipe.FluidOutput;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.BlastLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ректификационная колонна (GT_MetaTileEntity_DistillationTower, D104): столб 3×3 высотой 3–12, контроллер в середине
 * стороны нижнего этажа. Низ — чистые корпуса из нержавеющей стали или люки (входы, выходы, обслуживание, энергия);
 * выше — этажи с пустой серединой, по краю корпуса или выходные люки (обслуживание, энергия), верхний этаж сплошной.
 * Корпусов не меньше 7 × высота − 5. Жидкость № i рецепта уходит в выходные люки этажа i + 1 (считая от нижнего);
 * предмет — в выходную шину. Разгон GT: до 16 EU/t — сразу на уровень люков, иначе ×4/÷2, пока EU/t не больше V[T−1].
 */
public class DistillationTowerBlockEntity extends RecipeMultiblockBlockEntity {
    private static final Set<HatchType> BOTTOM = Set.of(HatchType.INPUT_HATCH, HatchType.INPUT_BUS, HatchType.OUTPUT_HATCH, HatchType.OUTPUT_BUS,
            HatchType.MAINTENANCE, HatchType.ENERGY);
    private static final Set<HatchType> UPPER = Set.of(HatchType.OUTPUT_HATCH, HatchType.MAINTENANCE, HatchType.ENERGY);

    private int height;

    public DistillationTowerBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.DISTILLATION_TOWER_BE.get(), pos, state);
    }

    public int height() {
        return height;
    }

    @Override
    protected GtRecipeMap recipeMap() {
        return GtRecipeMap.DISTILLATION_TOWER;
    }

    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        height = 0;
        BlockPos c = worldPosition.relative(back());
        Block casing = MultiContent.CLEAN_STAINLESS_CASING.get();
        int casings = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = c.offset(dx, 0, dz);
                if (p.equals(worldPosition) || addPart(sl, p, BOTTOM)) continue;
                if (!is(sl, p, casing)) return false;
                casings++;
            }
        }
        int y = 1;
        boolean top = false;
        while (y < 12 && !top) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = c.offset(dx, y, dz);
                    boolean middle = dx == 0 && dz == 0;
                    if (sl.getBlockState(p).isAir()) {
                        if (!middle) return false;
                        continue;
                    }
                    if (middle) top = true;
                    if (addPart(sl, p, UPPER)) continue;
                    if (!is(sl, p, casing)) return false;
                    casings++;
                }
            }
            y++;
        }
        height = y;
        return casings >= 7 * y - 5 && y >= 3 && top && basicParts();
    }

    @Override
    protected void start(ServerLevel sl, GtRecipe r, int tier) {
        efficiency = maintenance.maxEfficiency();
        efficiencyIncrease = 10000;
        long e = r.eut();
        int d = r.duration();
        if (e <= 16) {
            e = e * (1L << (tier - 1)) * (1L << (tier - 1));
            d = d / (1 << (tier - 1));
        } else {
            while (e <= BlastLogic.V[tier - 1]) {
                e *= 4;
                d /= 2;
            }
        }
        eut = (int) e;
        maxProgress = Math.max(1, d);
        progress = 0;
        pendingItems = r.rollOutputs(sl.getRandom());
        pendingFluids = new ArrayList<>(r.fluidOutputs());
        setChanged();
    }

    /** GT addFluidOutputs: жидкость i — только в люки этажа controllerY + 1 + i. */
    @Override
    protected void outputFluids(ServerLevel sl, List<FluidOutput> fluids) {
        for (int i = 0; i < fluids.size(); i++) {
            FluidOutput f = fluids.get(i);
            int left = f.amount();
            for (BlockPos p : parts.outputHatches) {
                if (left <= 0 || p.getY() != worldPosition.getY() + 1 + i) continue;
                if (sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h) left -= h.fillInternal(f.fluid(), left);
            }
        }
    }
}

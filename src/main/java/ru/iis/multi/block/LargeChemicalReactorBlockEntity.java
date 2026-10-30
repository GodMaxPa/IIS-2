package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.BlastLogic;

import java.util.ArrayList;

/**
 * Большой химический реактор (GT_MetaTileEntity_LargeChemicalReactor, D104): куб 3×3×3, контроллер — в середине стенки.
 * В центре — корпус с ПТФЭ-трубами, хотя бы в одной из середин граней — мельхиоровая катушка, остальное — химически
 * стойкие корпуса (не меньше 8) или люки (входы, выходы, обслуживание, энергия). Работает по рецептам химреактора
 * (в GT — sMultiblockChemicalRecipes) с идеальным разгоном: ×4 энергии и ÷4 времени, пока EU/t не больше V[T−1].
 */
public class LargeChemicalReactorBlockEntity extends RecipeMultiblockBlockEntity {
    public static final int MIN_CASINGS = 8;

    public LargeChemicalReactorBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.LARGE_CHEMICAL_REACTOR_BE.get(), pos, state);
    }

    @Override
    protected GtRecipeMap recipeMap() {
        return GtRecipeMap.CHEMICAL_REACTOR;
    }

    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        BlockPos c = worldPosition.relative(back());
        if (!is(sl, c, MultiContent.PTFE_PIPE_CASING.get())) return false;
        int casings = 0;
        boolean coil = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (p.equals(c) || p.equals(worldPosition)) continue;
                    int zeros = (dx == 0 ? 1 : 0) + (dy == 0 ? 1 : 0) + (dz == 0 ? 1 : 0);
                    if (zeros == 2 && is(sl, p, MultiContent.COIL_CUPRONICKEL.get())) {
                        coil = true;
                        continue;
                    }
                    if (addPart(sl, p, ALL_IO)) continue;
                    if (!is(sl, p, MultiContent.CHEMICALLY_INERT_CASING.get())) return false;
                    casings++;
                }
            }
        }
        return casings >= MIN_CASINGS && coil && basicParts();
    }

    @Override
    protected void start(ServerLevel sl, GtRecipe r, int tier) {
        efficiency = maintenance.maxEfficiency();
        efficiencyIncrease = 10000;
        long e = r.eut();
        int d = r.duration();
        while (e <= BlastLogic.V[tier - 1] && d > 2) {
            e *= 4;
            d /= 4;
        }
        if (d <= 0) {
            d = 1;
            e = (long) r.eut() * r.duration() / 2;
        }
        eut = (int) e;
        maxProgress = d;
        progress = 0;
        pendingItems = r.rollOutputs(sl.getRandom());
        pendingFluids = new ArrayList<>(r.fluidOutputs());
        setChanged();
    }

}

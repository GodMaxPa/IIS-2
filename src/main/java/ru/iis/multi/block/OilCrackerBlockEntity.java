package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.multi.MultiContent;

import java.util.Set;

/**
 * Крекер (GT_MetaTileEntity_OilCracker, D104): 5 в ширину × 3 в высоту × 3 в глубину, контроллер — в середине передней
 * стороны. Два слоя по ширине вокруг середины — мельхиоровые катушки (кроме центральной линии), середина — чистые корпуса
 * из нержавеющей стали или люки (обслуживание, вход, энергия), торцы — корпуса или люки; на одном торце должен быть вход,
 * на другом — выход. Корпусов не меньше 18. Водород (схема 1) или пар (схема 2) + углеводород → крекинг-фракция.
 */
public class OilCrackerBlockEntity extends RecipeMultiblockBlockEntity {
    public static final int MIN_CASINGS = 18;
    private static final Set<HatchType> MIDDLE = Set.of(HatchType.MAINTENANCE, HatchType.INPUT_HATCH, HatchType.ENERGY);
    private static final Set<HatchType> END = Set.of(HatchType.INPUT_HATCH, HatchType.OUTPUT_HATCH, HatchType.MAINTENANCE, HatchType.ENERGY);

    public OilCrackerBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.OIL_CRACKER_BE.get(), pos, state);
    }

    @Override
    protected GtRecipeMap recipeMap() {
        return GtRecipeMap.CRACKING;
    }

    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        Direction back = back(), side = back.getClockWise();
        Block coil = MultiContent.COIL_CUPRONICKEL.get(), casing = MultiContent.CLEAN_STAINLESS_CASING.get();
        int casings = 0;
        boolean[] in = new boolean[2], out = new boolean[2];
        for (int depth = 0; depth < 3; depth++) {
            for (int y = -1; y <= 1; y++) {
                for (int w = -2; w <= 2; w++) {
                    BlockPos p = worldPosition.relative(back, depth).above(y).relative(side, w);
                    if (depth == 1 && y == 0 && Math.abs(w) <= 1) continue;   // внутренняя линия
                    if (Math.abs(w) == 1) {
                        if (!is(sl, p, coil)) return false;
                    } else if (Math.abs(w) == 2) {
                        int end = w < 0 ? 0 : 1;
                        int ins = parts.inputHatches.size(), outs = parts.outputHatches.size();
                        if (addPart(sl, p, END)) {
                            if (parts.inputHatches.size() > ins) in[end] = true;
                            if (parts.outputHatches.size() > outs) out[end] = true;
                            continue;
                        }
                        if (!is(sl, p, casing)) return false;
                        casings++;
                    } else {
                        if (p.equals(worldPosition) || addPart(sl, p, MIDDLE)) continue;
                        if (!is(sl, p, casing)) return false;
                        casings++;
                    }
                }
            }
        }
        return casings >= MIN_CASINGS && (in[0] && out[1] || in[1] && out[0]) && basicParts();
    }
}

package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.BlastLogic;

import java.util.ArrayList;
import java.util.Set;

/**
 * Пиролизная печь (GT_MetaTileEntity_PyrolyseOven, D104): 5×5×4, контроллер — в середине передней стороны нижнего слоя.
 * Низ: внутри 3×3 одинаковых катушек, по краю — каркасы ULV или люки (обслуживание, выходы, энергия). Два средних слоя:
 * внутри пусто, по краю каркасы ULV. Верх: внутри каркасы ULV, входы или глушитель, по краю каркасы.
 * Разгон GT: рецепт до 16 EU/t — сразу на уровень люков (×4^(T−1) энергии, ÷2^(T−1) времени), иначе ×4/÷2, пока EU/t не
 * больше V[T−1]; затем время ×2 / (1 + номер катушки): мельхиор 0, кантал 1, нихром 2, вольфрамосталь 3. 10 загрязнения за тик.
 */
public class PyrolyseOvenBlockEntity extends RecipeMultiblockBlockEntity {
    public static final int POLLUTION = 10;
    private static final Set<HatchType> BOTTOM = Set.of(HatchType.MAINTENANCE, HatchType.OUTPUT_BUS, HatchType.OUTPUT_HATCH, HatchType.ENERGY);
    private static final Set<HatchType> TOP = Set.of(HatchType.INPUT_BUS, HatchType.INPUT_HATCH, HatchType.MUFFLER);

    private int coilMeta = -1;

    public PyrolyseOvenBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.PYROLYSE_OVEN_BE.get(), pos, state);
    }

    /** Номер катушки GT (GT_Block_Casings5 meta): мельхиор 0, кантал 1, нихром 2, вольфрамосталь 3; −1 — не катушка. */
    public static int coilMeta(Block b) {
        if (b == MultiContent.COIL_CUPRONICKEL.get()) return 0;
        if (b == MultiContent.COIL_KANTHAL.get()) return 1;
        if (b == MultiContent.COIL_NICHROME.get()) return 2;
        if (b == MultiContent.COIL_TUNGSTENSTEEL.get()) return 3;
        return -1;
    }

    @Override
    protected GtRecipeMap recipeMap() {
        return GtRecipeMap.PYROLYSE;
    }

    @Override
    protected int pollutionPerTick() {
        return POLLUTION;
    }

    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        coilMeta = -1;
        BlockPos c = worldPosition.relative(back(), 2);
        Block casing = MultiContent.CASING_ULV.get();
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                boolean inner = Math.abs(i) < 2 && Math.abs(j) < 2;
                for (int h = 0; h < 4; h++) {
                    BlockPos p = c.offset(i, h, j);
                    if (inner) {
                        if (h == 0) {
                            int m = coilMeta(sl.getBlockState(p).getBlock());
                            if (m < 0 || coilMeta >= 0 && m != coilMeta) return false;
                            coilMeta = m;
                        } else if (h == 3) {
                            if (!addPart(sl, p, TOP) && !is(sl, p, casing)) return false;
                        } else if (!sl.getBlockState(p).isAir()) {
                            return false;
                        }
                    } else if (h == 0) {
                        if (p.equals(worldPosition) || addPart(sl, p, BOTTOM)) continue;
                        if (!is(sl, p, casing)) return false;
                    } else if (!is(sl, p, casing)) {
                        return false;
                    }
                }
            }
        }
        return true;
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
        maxProgress = Math.max(1, d * 2 / (1 + Math.max(0, coilMeta)));
        progress = 0;
        pendingItems = r.rollOutputs(sl.getRandom());
        pendingFluids = new ArrayList<>(r.fluidOutputs());
        setChanged();
    }
}

package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.multi.MultiContent;

import java.util.Set;

/**
 * Имплозионный компрессор (GT_MetaTileEntity_ImplosionCompressor, D104): куб 3×3×3 с пустой серединой, контроллер в
 * середине передней грани, остальное — прочные стальные корпуса (не меньше 16) или люки: обслуживание, глушитель, входная
 * и выходная шины, энергия. Рецепт берётся как есть, без разгона; 100 загрязнения за тик.
 */
public class ImplosionCompressorBlockEntity extends RecipeMultiblockBlockEntity {
    public static final int MIN_CASINGS = 16, POLLUTION = 100;
    private static final Set<HatchType> HATCHES = Set.of(HatchType.MAINTENANCE, HatchType.MUFFLER, HatchType.INPUT_BUS, HatchType.OUTPUT_BUS,
            HatchType.ENERGY);

    public ImplosionCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.IMPLOSION_COMPRESSOR_BE.get(), pos, state);
    }

    @Override
    protected GtRecipeMap recipeMap() {
        return GtRecipeMap.IMPLOSION;
    }

    @Override
    protected boolean overclocks() {
        return false;
    }

    @Override
    protected int pollutionPerTick() {
        return POLLUTION;
    }

    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        BlockPos center = worldPosition.relative(back());
        if (!sl.getBlockState(center).isAir()) return false;
        int casings = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos b = center.offset(dx, dy, dz);
                    if (b.equals(center) || b.equals(worldPosition) || addPart(sl, b, HATCHES)) continue;
                    if (!is(sl, b, MultiContent.SOLID_STEEL_CASING.get())) return false;
                    casings++;
                }
            }
        }
        return casings >= MIN_CASINGS;
    }
}

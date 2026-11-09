package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.multi.MultiContent;

import java.util.Set;

/**
 * Автоферма дерева LV (D144, владелец: «для превращения в уголь… как новый многоблок ИИС 2, а не копия Forestry»).
 * Форма 5×5×5, контроллер — в середине передней стороны нижнего слоя. Низ: внутри поле 3×3 земли (любой блок тега
 * {@code minecraft:dirt}), по краю каркасы ULV или люки (обслуживание, входные и выходные шины, входной люк воды, энергия).
 * Три средних слоя: внутри пусто (на нижнем можно поставить саженцы для вида), по краю стекло или каркасы ULV.
 * Верх: каркасы ULV. Работает по карте {@link GtRecipeMap#TREE_FARM} с разгоном GT.
 */
public class TreeFarmBlockEntity extends RecipeMultiblockBlockEntity {
    private static final Set<HatchType> BOTTOM = Set.of(HatchType.MAINTENANCE, HatchType.INPUT_BUS, HatchType.OUTPUT_BUS, HatchType.INPUT_HATCH, HatchType.ENERGY);

    public TreeFarmBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.TREE_FARM_BE.get(), pos, state);
    }

    @Override
    protected GtRecipeMap recipeMap() {
        return GtRecipeMap.TREE_FARM;
    }

    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        BlockPos c = worldPosition.relative(back(), 2);
        var casing = MultiContent.CASING_ULV.get();
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                boolean inner = Math.abs(i) < 2 && Math.abs(j) < 2;
                for (int h = 0; h < 5; h++) {
                    BlockPos p = c.offset(i, h, j);
                    BlockState st = sl.getBlockState(p);
                    if (h == 0) {
                        if (inner) {
                            if (!st.is(BlockTags.DIRT)) return false;
                        } else if (!p.equals(worldPosition) && !addPart(sl, p, BOTTOM) && !st.is(casing)) {
                            return false;
                        }
                    } else if (h == 4) {
                        if (!st.is(casing)) return false;
                    } else if (inner) {
                        if (!st.isAir() && !(h == 1 && st.is(BlockTags.SAPLINGS))) return false;
                    } else if (!st.is(casing) && !st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)) {
                        return false;
                    }
                }
            }
        }
        return basicParts();
    }
}

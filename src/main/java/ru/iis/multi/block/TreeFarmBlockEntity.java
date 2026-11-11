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

    /** D145 (владелец: «по тирам, меняя части»): каркасы одного уровня — ULV 1, LV 2, MV 3, HV 4, EV 5, IV 6. */
    private int level = 1;

    public static int casingLevel(net.minecraft.world.level.block.Block b) {
        if (b == MultiContent.CASING_ULV.get()) return 1;
        if (b == ru.iis.lv.LvContent.CASING_LV.get()) return 2;
        if (b == ru.iis.lv.LvContent.CASING_MV.get()) return 3;
        if (b == ru.iis.lv.LvContent.CASING_HV.get()) return 4;
        if (b == ru.iis.lv.LvContent.CASING_EV.get()) return 5;
        if (b == ru.iis.lv.LvContent.CASING_IV.get()) return 6;
        return 0;
    }

    public int level() {
        return level;
    }

    /** Каждый уровень сверх первого: время ÷2 и энергия ×4 (как разгон GT), брёвен +50 %. */
    @Override
    protected void start(ServerLevel sl, ru.iis.machine.recipe.GtRecipe r, int tier) {
        super.start(sl, r, tier);
        int up = level - 1;
        if (up <= 0) return;
        maxProgress = Math.max(1, maxProgress >> up);
        eut = (int) Math.min(Integer.MAX_VALUE, (long) eut << (2 * up));
        if (!pendingItems.isEmpty() && !pendingItems.getFirst().isEmpty()) {
            net.minecraft.world.item.ItemStack logs = pendingItems.getFirst();
            logs.setCount(logs.getCount() * (2 + up) / 2);
        }
        setChanged();
    }

    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        level = 0;
        BlockPos c = worldPosition.relative(back(), 2);
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                boolean inner = Math.abs(i) < 2 && Math.abs(j) < 2;
                for (int h = 0; h < 5; h++) {
                    BlockPos p = c.offset(i, h, j);
                    BlockState st = sl.getBlockState(p);
                    if (h == 0) {
                        if (inner) {
                            if (!st.is(BlockTags.DIRT)) return false;
                        } else if (!p.equals(worldPosition) && !addPart(sl, p, BOTTOM) && !casing(st)) {
                            return false;
                        }
                    } else if (h == 4) {
                        if (!casing(st)) return false;
                    } else if (inner) {
                        if (!st.isAir() && !(h == 1 && st.is(BlockTags.SAPLINGS))) return false;
                    } else if (!st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) && !casing(st)) {
                        return false;
                    }
                }
            }
        }
        return level > 0 && basicParts();
    }

    /** Каркас; первый найденный задаёт уровень, остальные должны быть такими же. */
    private boolean casing(BlockState st) {
        int l = casingLevel(st.getBlock());
        if (l == 0 || (level != 0 && l != level)) return false;
        level = l;
        return true;
    }
}

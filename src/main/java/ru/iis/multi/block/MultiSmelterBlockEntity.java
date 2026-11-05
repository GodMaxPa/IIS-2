package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.BlastLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Мультиплавильня (GT_MetaTileEntity_MultiFurnace, D104). Нижний слой (с контроллером) — термостойкие корпуса или люки
 * (обслуживание, шины, энергия), средний — кольцо одинаковых катушек вокруг пустоты, верхний — термостойкие корпуса,
 * в центре глушитель. Плавит в печи до 8 × уровень предметов сразу (уровень: мельхиор 1, кантал 2, нихром 4,
 * вольфрамосталь 8), 4 × 4^(T−1) × уровень EU/t, 512 / 2^(T−1) тиков (T — уровень напряжения люков, не ниже LV);
 * 5 загрязнения за тик.
 */
public class MultiSmelterBlockEntity extends RecipeMultiblockBlockEntity {
    public static final int POLLUTION = 5;
    private static final Set<HatchType> BOTTOM = Set.of(HatchType.MAINTENANCE, HatchType.INPUT_BUS, HatchType.OUTPUT_BUS, HatchType.ENERGY);

    private int level;
    private int discount = 1;

    public MultiSmelterBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.MULTI_SMELTER_BE.get(), pos, state);
    }

    /** GT mLevel по катушке. */
    public static int coilLevel(Block b) {
        if (b == MultiContent.COIL_CUPRONICKEL.get()) return 1;
        if (b == MultiContent.COIL_KANTHAL.get()) return 2;
        if (b == MultiContent.COIL_NICHROME.get()) return 4;
        if (b == MultiContent.COIL_TUNGSTENSTEEL.get()) return 8;
        if (b == MultiContent.COIL_HSSG.get() || b == MultiContent.COIL_NAQUADAH.get() || b == MultiContent.COIL_NAQUADAH_ALLOY.get()) return 16;
        return 0;
    }

    /** D112: GT mCostDiscount: HSS-G 2, наквада 4, остальные 1. */
    public static int coilDiscount(Block b) {
        return b == MultiContent.COIL_NAQUADAH_ALLOY.get() ? 8 : b == MultiContent.COIL_NAQUADAH.get() ? 4 : b == MultiContent.COIL_HSSG.get() ? 2 : 1;
    }

    public int level() {
        return level;
    }

    @Override
    protected GtRecipeMap recipeMap() {
        return null;
    }

    @Override
    protected int pollutionPerTick() {
        return POLLUTION;
    }

    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        level = 0;
        BlockPos c0 = worldPosition.relative(back());
        if (!sl.getBlockState(c0.above()).isAir()) return false;
        if (addPart(sl, c0.above(2), Set.of(HatchType.MUFFLER))) {
            // глушитель в центре верха
        } else if (!is(sl, c0.above(2), MultiContent.HEAT_PROOF_CASING.get())) {
            return false;
        }
        Block coil = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos b0 = c0.offset(dx, 0, dz);
                if (dx != 0 || dz != 0) {
                    Block c = sl.getBlockState(b0.above()).getBlock();
                    if (coilLevel(c) == 0 || coil != null && c != coil) return false;
                    coil = c;
                    if (!is(sl, b0.above(2), MultiContent.HEAT_PROOF_CASING.get())) return false;
                }
                if (b0.equals(worldPosition) || addPart(sl, b0, BOTTOM)) continue;
                if (!is(sl, b0, MultiContent.HEAT_PROOF_CASING.get())) return false;
            }
        }
        level = coilLevel(coil);
        discount = coilDiscount(coil);
        return true;
    }

    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        List<ItemStack> slots = inputSlots(sl);
        ItemStack first = slots.stream().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        if (first.isEmpty() || level <= 0) return false;
        var smelt = sl.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(first), sl);
        if (smelt.isEmpty()) return false;
        ItemStack result = smelt.get().value().assemble(new SingleRecipeInput(first));
        if (result.isEmpty()) return false;
        int maxParallel = 8 * level, parallel = 0;
        ItemStack proto = first.copy();
        for (ItemStack s : slots) {
            if (parallel >= maxParallel) break;
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, proto)) continue;
            int t = Math.min(s.getCount(), maxParallel - parallel);
            s.shrink(t);
            parallel += t;
        }
        inputBusesChanged(sl);
        int total = parallel * result.getCount();
        List<ItemStack> out = new ArrayList<>();
        while (total > 0) {
            int n = Math.min(total, result.getMaxStackSize());
            out.add(result.copyWithCount(n));
            total -= n;
        }
        int tier = Math.max(1, BlastLogic.tier(maxInputVoltage(sl)));
        efficiency = maintenance.maxEfficiency();
        efficiencyIncrease = 10000;
        eut = 4 * (1 << (tier - 1)) * (1 << (tier - 1)) * level / discount;
        maxProgress = Math.max(1, 512 / (1 << (tier - 1)));
        progress = 0;
        pendingItems = out;
        pendingFluids = List.of();
        setChanged();
        return true;
    }
}

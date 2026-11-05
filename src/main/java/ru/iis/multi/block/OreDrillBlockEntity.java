package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.fluid.GtFluids;
import ru.iis.machine.recipe.ChanceOutput;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;
import ru.iis.multi.MultiContent;
import ru.iis.world.ore.OreBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Рудная буровая установка I–II (GT_MetaTileEntity_OreDrillingPlant1–2, D106). Спускает трубу и на каждом слое выбирает
 * руды в квадрате радиусом 48 и 64 блока (3 и 4 чанка) вокруг контроллера — по одной за цикл, каждый цикл тратя 2000 л
 * буровой жидкости. Руду сразу размалывает по рецептам измельчителя с напряжением люков, каждый выход — ×1…4 (дроблёная
 * руда и пыль идут без помола, тоже ×1…4). Дойдя до бедрока, поднимает трубы. Нужны люк обслуживания, входной люк
 * буровой жидкости, выходная шина и энергия не ниже MV и HV. Цикл 960 / 800 тиков, делённые на 2 за ступень; 3 × 4^T EU/t.
 * Добыча «из-под бедрока» по сфере данных ИИС не перенесена: у дна машина сразу поднимает трубы.
 */
public class OreDrillBlockEntity extends DrillerBlockEntity {
    /** MachineStats.cfg ИИС: OreDrillingPlant.dFluidPerOperation. */
    public static final int FLUID_PER_OPERATION = 2000;
    private static final int[] RADIUS_CHUNKS = {3, 4, 6, 9}, MIN_TIER = {2, 3, 4, 5}, BASE_TIME = {960, 800, 640, 480};
    private final List<BlockPos> ores = new ArrayList<>();

    public OreDrillBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.ORE_DRILL_BE.get(), pos, state);
    }

    @Override
    protected int level() {
        return Math.max(0, MultiContent.index(MultiContent.ORE_DRILLS, getBlockState().getBlock()));
    }

    @Override
    protected Block casing() {
        return switch (level()) {
            case 0 -> MultiContent.SOLID_STEEL_CASING.get();
            case 1 -> MultiContent.STABLE_TITANIUM_CASING.get();
            case 2 -> MultiContent.ROBUST_TUNGSTENSTEEL_CASING.get();
            default -> MultiContent.MINING_OSMIRIDIUM_CASING.get();
        };
    }

    @Override
    protected Block frame() {
        return MultiContent.FRAMES.get(switch (level()) {
            case 0 -> "steel";
            case 1 -> "titanium";
            case 2 -> "tungsten_steel";
            default -> "osmiridium";
        }).get();
    }

    @Override
    protected int minTier() {
        return MIN_TIER[level()];
    }

    public int radius() {
        return RADIUS_CHUNKS[level()] << 4;
    }

    @Override
    protected boolean checkHatches() {
        return !parts.maintenance.isEmpty() && !parts.inputHatches.isEmpty() && !parts.outputBuses.isEmpty() && !parts.energy.isEmpty();
    }

    @Override
    protected boolean allowPutPipesToController() {
        return false;
    }

    @Override
    protected void setElectricityStats(ServerLevel sl) {
        int tier = voltageTier(sl);
        int time = workState == STATE_DOWNWARD || workState == STATE_AT_BOTTOM ? BASE_TIME[level()] : 80;
        setCycle(3 * (1 << (tier << 1)), time / (1 << tier));
    }

    // --- спуск ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean workingDownward(ServerLevel sl, int oldYHead) {
        if (yHead != oldYHead) ores.clear();
        fillMineListIfEmpty(sl);
        if (ores.isEmpty()) {
            switch (tryLowerPipe(sl, false)) {
                case 2 -> {
                    maxProgress = 0;
                    return false;
                }
                case 1 -> {
                    workState = STATE_AT_BOTTOM;
                    return true;
                }
                default -> fillMineListIfEmpty(sl);
            }
        }
        if (!depleteInput(sl, GtFluids.get("liquid_drillingfluid").source().get(), FLUID_PER_OPERATION)) {
            maxProgress = 0;
            return false;
        }
        BlockPos pos = null;
        BlockState ore = null;
        while ((ore == null || ore.isAir()) && !ores.isEmpty()) {
            pos = ores.removeFirst();
            ore = sl.getBlockState(pos);
        }
        if (ore != null && !ore.isAir()) {
            List<ItemStack> drops = OreBlocks.isSmallOre(ore.getBlock()) ? Block.getDrops(ore, sl, pos, null) : List.of(new ItemStack(ore.getBlock()));
            sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            pendingItems = outputByDrops(sl, drops);
        }
        return true;
    }

    /** GT workingAtBottom без сферы данных: добывать под бедроком нечего — подъём. */
    @Override
    protected boolean workingAtBottom(ServerLevel sl, int oldYHead) {
        if (reachingVoidOrBedrock(sl)) workState = STATE_UPWARD;
        return true;
    }

    @Override
    protected boolean workingUpward(ServerLevel sl, int oldYHead) {
        if (returnStoredPipes(sl)) return true;
        return super.workingUpward(sl, oldYHead);
    }

    private void fillMineListIfEmpty(ServerLevel sl) {
        if (!ores.isEmpty()) return;
        BlockPos c = pipeColumn();
        tryAdd(sl, c.atY(yHead - 1));
        if (yHead == worldPosition.getY()) return;
        int r = radius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) tryAdd(sl, new BlockPos(worldPosition.getX() + dx, yHead, worldPosition.getZ() + dz));
        }
    }

    /** GT tryAddOreBlockToMineList: руды GT и всё с тегом руд. */
    private void tryAdd(ServerLevel sl, BlockPos p) {
        if (ores.contains(p)) return;
        BlockState s = sl.getBlockState(p);
        if (OreBlocks.isOre(s.getBlock()) || s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) ores.add(p.immutable());
    }

    /** GT getOutputByDrops: помол по рецептам измельчителя, каждый выход ×1…4. */
    private List<ItemStack> outputByDrops(ServerLevel sl, List<ItemStack> drops) {
        long voltage = maxInputVoltage(sl);
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack d : drops) {
            ItemStack cur = d.copy();
            if (!useMacerator(cur)) {
                cur.setCount(cur.getCount() * (sl.getRandom().nextInt(4) + 1));
                out.add(cur);
                continue;
            }
            var match = GtRecipes.find(sl, GtRecipeMap.MACERATOR, List.of(cur), eu -> eu <= voltage);
            if (match.isEmpty()) {
                out.add(cur);
                continue;
            }
            for (ChanceOutput o : match.get().recipe().outputs()) {
                ItemStack s = o.create();
                if (sl.getRandom().nextInt(ChanceOutput.ALWAYS) < o.chance()) s.setCount(s.getCount() * (sl.getRandom().nextInt(4) + 1));
                out.add(s);
            }
        }
        return out;
    }

    /** GT doUseMaceratorRecipe: дроблёную руду, грязную и обычную пыль не мелют. */
    private static boolean useMacerator(ItemStack s) {
        String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        return !(path.startsWith("crushed_") || path.startsWith("dust_"));
    }
}

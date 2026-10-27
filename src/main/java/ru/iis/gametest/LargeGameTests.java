package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.LvContent;
import ru.iis.lv.item.ProgrammedCircuitItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;
import ru.iis.multi.logic.LargeBoilerLogic;

/** Большие котлы (D88). */
final class LargeGameTests {
    private LargeGameTests() {}

    static final BlockPos CTRL = new BlockPos(4, 1, 2), BUS = new BlockPos(3, 1, 2), WATER = new BlockPos(5, 1, 2),
            FUEL_HATCH = new BlockPos(4, 1, 4), STEAM = new BlockPos(4, 2, 2), PIPE = new BlockPos(4, 3, 3);

    /**
     * Котёл 3×5×3: контроллер (4,1,2) лицом на север, середина (4,·,3). Низ: шина топлива, люк воды (каждый тик доливается),
     * люк жидкого топлива (4,1,4), обслуживание, глушитель лицом на восток, 3 топки. Над контроллером — выходной люк пара.
     */
    static LargeBoilerBlockEntity build(GameTestHelper h, LargeBoilerLogic.Tier tier) {
        LargeBoilerBlockEntity.Casings c = MultiContent.boilerCasings(tier);
        BlockPos center = CTRL.south();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos col = center.offset(dx, 0, dz);
                h.setBlock(col, c.firebox());
                for (int k = 1; k <= 4; k++) h.setBlock(col.above(k), dx == 0 && dz == 0 && k < 4 ? c.pipe() : c.casing());
            }
        }
        h.setBlock(BUS, MultiGameTests.hatch(HatchType.INPUT_BUS, Direction.NORTH));
        h.setBlock(WATER, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.NORTH));
        h.setBlock(FUEL_HATCH, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.SOUTH));
        h.setBlock(new BlockPos(3, 1, 4), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.SOUTH));
        h.setBlock(new BlockPos(5, 1, 4), MultiGameTests.hatch(HatchType.MUFFLER, Direction.EAST));
        h.setBlock(STEAM, MultiGameTests.hatch(HatchType.OUTPUT_HATCH, Direction.NORTH));
        h.setBlock(CTRL, MultiContent.LARGE_BOILERS.get(tier).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        LargeBoilerBlockEntity b = h.getBlockEntity(CTRL, LargeBoilerBlockEntity.class);
        b.maintenance().setMask(63);
        FluidHatchBlockEntity water = h.getBlockEntity(WATER, FluidHatchBlockEntity.class);
        h.onEachTick(() -> water.tank().setAmount(Fluids.WATER, HatchType.TANK));
        return b;
    }

    private static int steam(GameTestHelper h) {
        FluidHatchBlockEntity out = h.getBlockEntity(STEAM, FluidHatchBlockEntity.class);
        return out.tank().amount() > 0 && out.tank().resource().getFluid().isSame(IISFluids.STEAM.get()) ? out.tank().amount() : 0;
    }

    /** Уголь в шине → котёл разгорается (КПД растёт порциями), в выходной люк идёт пар, уголь убывает. */
    static void bronzeBoilerCoal(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.BRONZE);
        ItemBusBlockEntity bus = h.getBlockEntity(BUS, ItemBusBlockEntity.class);
        bus.setItem(0, new ItemStack(Items.COAL, 64));
        h.succeedWhen(() -> {
            h.assertTrue(b.formed(), "boiler not formed");
            h.assertTrue(h.getBlockState(CTRL).getValue(MachineBlock.ACTIVE), "boiler not burning");
            h.assertTrue(bus.getItem(0).getCount() < 64, "coal not used");
            h.assertTrue(b.efficiency() > 0, "efficiency did not grow");
            h.assertTrue(steam(h) > 0, "no steam in the output hatch");
        });
    }

    /** Полужидкое топливо: 8000 л креозота из люка → 128 тиков горения (8 × 16, D88 dense). */
    static void bronzeBoilerCreosote(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.BRONZE);
        FluidHatchBlockEntity fuel = h.getBlockEntity(FUEL_HATCH, FluidHatchBlockEntity.class);
        fuel.tank().setAmount(IISFluids.CREOSOTE.get(), 9000);
        h.succeedWhen(() -> {
            h.assertTrue(b.formed(), "boiler not formed");
            h.assertTrue(fuel.tank().amount() == 1000, "creosote left " + fuel.tank().amount());
            h.assertTrue(b.logic().progressTimeStash > 0 || b.maxProgress() > 0, "creosote did not burn");
        });
    }

    /** Схема 8 в контроллере горячего стального котла: 600 − 8 × 25 = 400 EU/t (800 л/т пара вместо 1200). */
    static void boilerCircuitThrottle(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.STEEL);
        ItemBusBlockEntity bus = h.getBlockEntity(BUS, ItemBusBlockEntity.class);
        bus.setItem(0, new ItemStack(Items.COAL, 64));
        b.setItem(0, ProgrammedCircuitItem.withConfig(new ItemStack(LvContent.PROGRAMMED_CIRCUIT.get()), 8));
        h.onEachTick(() -> {
            if (b.efficiency() < 10000 && b.maxProgress() > 0) setEfficiency(b, 10000);
        });
        h.succeedWhen(() -> {
            h.assertTrue(b.logic().circuitConfig == 8, "circuit not read: " + b.logic().circuitConfig);
            h.assertTrue(b.logic().eut == 400, "steel boiler at circuit 8 must give 400 EU/t, got " + b.logic().eut);
        });
    }

    private static void setEfficiency(MultiblockControllerBlockEntity b, int e) {
        try {
            var f = MultiblockControllerBlockEntity.class.getDeclaredField("efficiency");
            f.setAccessible(true);
            f.setInt(b, e);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Без трубчатого корпуса в середине, с двумя топками или с чужим блоком в стенке форма не собирается. */
    static void boilerStructureRules(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.BRONZE);
        BlockPos firebox = new BlockPos(3, 1, 3);
        h.runAtTickTime(25, () -> {
            h.assertTrue(b.formed(), "boiler not formed");
            h.setBlock(PIPE, MultiContent.BRONZE_PLATED_BRICKS.get());
        });
        h.runAtTickTime(50, () -> {
            h.assertFalse(b.formed(), "formed without a pipe casing");
            h.setBlock(PIPE, MultiContent.BRONZE_PIPE_CASING.get());
            h.setBlock(firebox, MultiGameTests.hatch(HatchType.INPUT_BUS, Direction.NORTH)); // топок осталось 2
        });
        h.runAtTickTime(75, () -> {
            h.assertFalse(b.formed(), "formed with two fireboxes");
            h.setBlock(firebox, MultiContent.BRONZE_FIREBOX_CASING.get());
        });
        h.runAtTickTime(100, () -> {
            h.assertTrue(b.formed(), "three fireboxes must be enough");
            h.setBlock(STEAM, Blocks.STONE);
        });
        h.runAtTickTime(125, () -> {
            h.assertFalse(b.formed(), "formed with stone in the casing");
            h.succeed();
        });
    }
}

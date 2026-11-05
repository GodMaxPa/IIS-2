package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.fluid.GtFluids;
import ru.iis.lv.LvContent;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;

/** Термоядерный реактор Mark I (D116): GT_MetaTileEntity_FusionComputer, _FusionComputer1. */
final class FusionGameTests {
    private FusionGameTests() {}

    private static final BlockPos CENTER = new BlockPos(8, 2, 8), CONTROLLER = CENTER.east(FusionReactorBlockEntity.RING);

    private static void hatch(GameTestHelper h, int[] d, HatchType t) {
        BlockPos p = CENTER.offset(d[0], d[1], d[2]);
        BlockState s = MultiGameTests.hatch(t, Direction.UP);
        h.setBlock(p, s);
    }

    /** Кольцо; энергетических люков LuV сколько задано, один выходной и два входных, остальное — корпус. */
    private static FusionReactorBlockEntity ring(GameTestHelper h, int energyHatches, HatchType energy) {
        for (int[] d : FusionShape.COILS) h.setBlock(CENTER.offset(d[0], d[1], d[2]), MultiContent.COIL_SUPERCONDUCTOR.get());
        for (int[] d : FusionShape.CASINGS) h.setBlock(CENTER.offset(d[0], d[1], d[2]), LvContent.CASING_LUV.get());
        for (int i = 0; i < FusionShape.ENERGY.length; i++) {
            if (i < energyHatches) hatch(h, FusionShape.ENERGY[i], energy);
            else h.setBlock(CENTER.offset(FusionShape.ENERGY[i][0], FusionShape.ENERGY[i][1], FusionShape.ENERGY[i][2]), LvContent.CASING_LUV.get());
        }
        for (int[] d : FusionShape.OUTPUT) h.setBlock(CENTER.offset(d[0], d[1], d[2]), LvContent.CASING_LUV.get());
        for (int[] d : FusionShape.INPUT) h.setBlock(CENTER.offset(d[0], d[1], d[2]), LvContent.CASING_LUV.get());
        hatch(h, FusionShape.OUTPUT[0], HatchType.OUTPUT_HATCH_LUV);
        hatch(h, FusionShape.INPUT[0], HatchType.INPUT_HATCH_LUV);
        hatch(h, FusionShape.INPUT[1], HatchType.INPUT_HATCH_LUV);
        h.setBlock(CONTROLLER, MultiContent.FUSION_COMPUTER_LUV.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        return h.getBlockEntity(CONTROLLER, FusionReactorBlockEntity.class);
    }

    private static FluidHatchBlockEntity at(GameTestHelper h, int[] d) {
        return h.getBlockEntity(CENTER.offset(d[0], d[1], d[2]), FluidHatchBlockEntity.class);
    }

    /** Марганец + водород → железо (GT_MachineRecipeLoader:1085): 120 млн EU зажигания, 16 л за 64 тика. */
    static void reactorFuses(GameTestHelper h) {
        FusionReactorBlockEntity e = ring(h, 16, HatchType.ENERGY_LUV);
        e.setStored(FusionReactorBlockEntity.STORE_MARK1 - 1000);
        at(h, FusionShape.INPUT[0]).tank().setAmount(GtFluids.get("molten_manganese").source().get(), 16);
        at(h, FusionShape.INPUT[1]).tank().setAmount(GtFluids.get("hydrogen").source().get(), 16);
        h.succeedWhen(() -> {
            h.assertTrue(e.formed(), "not formed");
            FluidHatchBlockEntity out = at(h, FusionShape.OUTPUT[0]);
            h.assertTrue(out.tank().amount() == 16 && out.tank().resource().getFluid() == GtFluids.get("molten_iron").source().get(), "output " + out.tank().amount());
            h.assertTrue(e.stored() < FusionReactorBlockEntity.STORE_MARK1 - 120000000L, "ignition not paid: " + e.stored());
            h.assertTrue(at(h, FusionShape.INPUT[0]).tank().amount() == 0, "input left");
        });
    }

    /** Вторая порция подряд не платит за зажигание: 120 млн снимаются один раз. */
    static void secondBatchKeepsBurning(GameTestHelper h) {
        FusionReactorBlockEntity e = ring(h, 16, HatchType.ENERGY_LUV);
        long start = FusionReactorBlockEntity.STORE_MARK1 - 1000;
        e.setStored(start);
        at(h, FusionShape.INPUT[0]).tank().setAmount(GtFluids.get("molten_manganese").source().get(), 32);
        at(h, FusionShape.INPUT[1]).tank().setAmount(GtFluids.get("hydrogen").source().get(), 32);
        h.succeedWhen(() -> {
            h.assertTrue(at(h, FusionShape.OUTPUT[0]).tank().amount() == 32, "output " + at(h, FusionShape.OUTPUT[0]).tank().amount());
            h.assertTrue(e.stored() == start - 120000000L - 2 * 64 * 8192L, "stored " + e.stored());
        });
    }

    /** С одним люком запас всего 10 млн EU: на зажигание 120 млн не хватит никогда, входы целы. */
    static void oneHatchIsTooSmall(GameTestHelper h) {
        FusionReactorBlockEntity e = ring(h, 1, HatchType.ENERGY_LUV);
        e.setStored(160000000L);
        at(h, FusionShape.INPUT[0]).tank().setAmount(GtFluids.get("molten_manganese").source().get(), 16);
        at(h, FusionShape.INPUT[1]).tank().setAmount(GtFluids.get("hydrogen").source().get(), 16);
        h.runAtTickTime(240, () -> {
            h.assertTrue(e.formed() && e.capacity() == FusionReactorBlockEntity.STORE_MARK1 / 16, "capacity " + e.capacity());
            h.assertTrue(at(h, FusionShape.INPUT[0]).tank().amount() == 16, "input burnt");
            h.succeed();
        });
    }

    /** Запаса мало: отличие от GT, входы не сгорают. */
    static void lowStoreKeepsInputs(GameTestHelper h) {
        FusionReactorBlockEntity e = ring(h, 16, HatchType.ENERGY_LUV);
        e.setStored(1000000L);
        at(h, FusionShape.INPUT[0]).tank().setAmount(GtFluids.get("molten_manganese").source().get(), 16);
        at(h, FusionShape.INPUT[1]).tank().setAmount(GtFluids.get("hydrogen").source().get(), 16);
        h.runAtTickTime(240, () -> {
            h.assertTrue(at(h, FusionShape.INPUT[0]).tank().amount() == 16, "input burnt");
            h.assertTrue(e.maxProgress() == 0, "running");
            h.succeed();
        });
    }

    /** Каждый люк отдаёт 2048 EU за тик. */
    static void hatchesFillTheStore(GameTestHelper h) {
        FusionReactorBlockEntity e = ring(h, 2, HatchType.ENERGY_LUV);
        h.runAtTickTime(40, () -> {
            for (int i = 0; i < 2; i++) {
                int[] d = FusionShape.ENERGY[i];
                h.getBlockEntity(CENTER.offset(d[0], d[1], d[2]), EnergyHatchBlockEntity.class).energy.stored = 2048 * 10;
            }
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(e.stored() == 2 * 2048 * 10, "stored " + e.stored());
            h.succeed();
        });
    }

    /** Люки ниже LuV и пропуск катушки: форма не собрана. */
    static void shapeNeedsLuvAndCoils(GameTestHelper h) {
        FusionReactorBlockEntity e = ring(h, 1, HatchType.ENERGY_IV);
        h.runAtTickTime(30, () -> {
            h.assertFalse(e.formed(), "IV energy hatch accepted");
            hatch(h, FusionShape.ENERGY[0], HatchType.ENERGY_LUV);
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(e.formed(), "not formed with a LuV hatch");
            int[] c = FusionShape.COILS[0];
            h.setBlock(CENTER.offset(c[0], c[1], c[2]), LvContent.CASING_LUV.get());
        });
        h.runAtTickTime(90, () -> {
            h.assertFalse(e.formed(), "formed without a coil");
            h.succeed();
        });
    }
}

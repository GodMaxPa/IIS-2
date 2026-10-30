package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import ru.iis.command.SelfTest;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;
import ru.iis.world.UndergroundFluids;
import ru.iis.world.ore.OreBlocks;
import ru.iis.world.ore.OreHost;

/** Буровые GT5-II (D106): нефтяная, рудная, бетонный засыпщик; подземные жидкости. */
final class DrillGameTests {
    private DrillGameTests() {}

    private static final BlockPos CTRL = new BlockPos(4, 2, 2), COLUMN = CTRL.south();

    /** Площадка 3×3 из корпусов, столб и рамки; контроллер смотрит на север, труба уходит из-под центра (бедрок на y = 0). */
    private static void build(GameTestHelper h, Block controller, Block casing, Block frame) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) h.setBlock(COLUMN.offset(dx, 0, dz), casing);
        for (int y = 1; y < 4; y++) {
            h.setBlock(COLUMN.above(y), casing);
            h.setBlock(COLUMN.above(y + 3), frame);
            for (Direction d : Direction.Plane.HORIZONTAL) h.setBlock(COLUMN.above(y).relative(d), frame);
        }
        h.setBlock(COLUMN.below(), Blocks.AIR);
        h.setBlock(COLUMN.below(2), Blocks.BEDROCK);
        h.setBlock(CTRL, controller.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        h.setBlock(COLUMN.west(), MultiGameTests.hatch(HatchType.ENERGY_MV, Direction.WEST));
        h.setBlock(COLUMN.east(), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.EAST));
        h.onEachTick(() -> {
            if (h.getLevel().getBlockEntity(h.absolutePos(COLUMN.west())) instanceof EnergyHatchBlockEntity e) e.energy.stored = e.energy.capacity;
        });
    }

    /** Нефтяная буровая I: труба до бедрока, потом жидкость поля (чанк под контроллером) — в выходной люк. */
    static void oilDrillPumpsField(GameTestHelper h) {
        build(h, MultiContent.OIL_DRILLS.get(0).get(), MultiContent.SOLID_STEEL_CASING.get(), MultiContent.FRAMES.get("steel").get());
        BlockPos out = COLUMN.south().west();
        h.setBlock(out, MultiGameTests.hatch(HatchType.OUTPUT_HATCH, Direction.WEST));
        Fluid fluid = UndergroundFluids.prime(h.getLevel(), ChunkPos.containing(h.absolutePos(CTRL)), 1_000_000);
        h.assertTrue(fluid != null, "overworld has no underground fluid");
        OilDrillBlockEntity e = h.getBlockEntity(CTRL, OilDrillBlockEntity.class);
        e.maintenance().setMask(63);
        e.setItem(0, new ItemStack(MultiContent.MINING_PIPE.get(), 4));
        FluidHatchBlockEntity hatch = h.getBlockEntity(out, FluidHatchBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(e.formed(), "rig not formed");
            h.assertBlockPresent(MultiContent.MINING_PIPE_TIP.get(), COLUMN.below());
            h.assertTrue(e.pipes().getCount() == 3, "pipes " + e.pipes().getCount());
            h.assertTrue(hatch.tank().amount() > 0 && hatch.tank().resource().getFluid().isSame(fluid), "pumped " + hatch.tank().amount());
        });
    }

    /** Рудная буровая I: на первом слое добывает руду халькопирита и сразу мелет её в дроблёную руду; буровая жидкость тратится. */
    static void oreDrillMinesAndGrinds(GameTestHelper h) {
        build(h, MultiContent.ORE_DRILLS.get(0).get(), MultiContent.SOLID_STEEL_CASING.get(), MultiContent.FRAMES.get("steel").get());
        BlockPos fluid = COLUMN.south().west(), bus = COLUMN.south().east(), ore = new BlockPos(8, 1, 8);
        h.setBlock(fluid, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.WEST));
        h.setBlock(bus, MultiGameTests.hatch(HatchType.OUTPUT_BUS, Direction.EAST));
        h.setBlock(ore, OreBlocks.get("chalcopyrite", OreHost.STONE));
        OreDrillBlockEntity e = h.getBlockEntity(CTRL, OreDrillBlockEntity.class);
        e.maintenance().setMask(63);
        e.setItem(0, new ItemStack(MultiContent.MINING_PIPE.get(), 4));
        FluidHatchBlockEntity in = h.getBlockEntity(fluid, FluidHatchBlockEntity.class);
        in.tank().setAmount(ru.iis.fluid.GtFluids.get("liquid_drillingfluid").source().get(), 8000);
        ItemBusBlockEntity out = h.getBlockEntity(bus, ItemBusBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(e.formed(), "plant not formed");
            h.assertBlockPresent(Blocks.AIR, ore);
            int crushed = 0;
            for (ItemStack s : out.stacks()) if (s.is(SelfTest.item("crushed_chalcopyrite"))) crushed += s.getCount();
            h.assertTrue(crushed >= 2, "crushed ore " + crushed);
            h.assertTrue(in.tank().amount() <= 4000, "drilling fluid left " + in.tank().amount());
        });
    }

    /** Без буровой жидкости рудная буровая не начинает (трубы на месте). */
    static void oreDrillNeedsFluid(GameTestHelper h) {
        build(h, MultiContent.ORE_DRILLS.get(0).get(), MultiContent.SOLID_STEEL_CASING.get(), MultiContent.FRAMES.get("steel").get());
        h.setBlock(COLUMN.south().west(), MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.WEST));
        h.setBlock(COLUMN.south().east(), MultiGameTests.hatch(HatchType.OUTPUT_BUS, Direction.EAST));
        OreDrillBlockEntity e = h.getBlockEntity(CTRL, OreDrillBlockEntity.class);
        e.maintenance().setMask(63);
        e.setItem(0, new ItemStack(MultiContent.MINING_PIPE.get(), 4));
        h.runAtTickTime(250, () -> {
            h.assertTrue(e.formed(), "plant not formed");
            h.assertTrue(e.maxProgress() == 0, "plant runs without drilling fluid");
            h.succeed();
        });
    }

    /** Засыпщик: опускает трубу до бедрока и начинает подъём; без бетона ничего не заливает. */
    static void backfillerGoesDownAndUp(GameTestHelper h) {
        build(h, MultiContent.BACKFILLERS.get(0).get(), MultiContent.SOLID_STEEL_CASING.get(), MultiContent.FRAMES.get("steel").get());
        h.setBlock(COLUMN.south().west(), MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.WEST));
        BackfillerBlockEntity e = h.getBlockEntity(CTRL, BackfillerBlockEntity.class);
        e.maintenance().setMask(63);
        e.setItem(0, new ItemStack(MultiContent.MINING_PIPE.get(), 4));
        h.succeedWhen(() -> {
            h.assertTrue(e.formed(), "backfiller not formed");
            h.assertTrue(e.workState() == DrillerBlockEntity.STATE_UPWARD, "state " + e.workState());
            h.assertBlockPresent(MultiContent.MINING_PIPE_TIP.get(), COLUMN.below());
            h.assertBlockNotPresent(MultiContent.LIGHT_CONCRETE.get(), COLUMN.below().west());
        });
    }

    /** Подземная жидкость: чтение — запас / 5000, добыча с коэффициентом 1/2 даёт запас / 10000 и уменьшает запас. */
    static void undergroundFluidDrains(GameTestHelper h) {
        ChunkPos cp = ChunkPos.containing(h.absolutePos(new BlockPos(1, 1, 1)));
        Fluid f = UndergroundFluids.prime(h.getLevel(), cp, 500_000);
        h.assertTrue(f != null, "no fluid");
        UndergroundFluids.Sample read = UndergroundFluids.read(h.getLevel(), cp);
        h.assertTrue(read != null && read.amount() == 100 && read.fluid() == f, "read " + read);
        UndergroundFluids.Sample got = UndergroundFluids.drain(h.getLevel(), cp, 0.5f);
        h.assertTrue(got != null && got.amount() == 50, "drained " + got);
        UndergroundFluids.Sample after = UndergroundFluids.read(h.getLevel(), cp);
        h.assertTrue(after != null && after.amount() <= 99, "after " + after);
        h.succeed();
    }
}

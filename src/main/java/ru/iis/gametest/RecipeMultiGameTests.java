package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import ru.iis.command.SelfTest;
import ru.iis.lv.LvContent;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;


/** Многоблоки по картам рецептов GT (D104): имплозионный компрессор, мультиплавильня, пиролизная печь. */
final class RecipeMultiGameTests {
    private RecipeMultiGameTests() {}

    private static void powered(GameTestHelper h, BlockPos... hatches) {
        h.onEachTick(() -> {
            for (BlockPos p : hatches) {
                if (h.getLevel().getBlockEntity(h.absolutePos(p)) instanceof EnergyHatchBlockEntity e) e.energy.stored = e.energy.capacity;
            }
        });
    }

    private static int count(ItemBusBlockEntity bus, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : bus.stacks()) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** Куб 3×3×3 вокруг (4,2,2): контроллер (4,2,1) на север; энергия снизу, шины по бокам, обслуживание сверху, глушитель сзади. */
    static void implosionMakesGems(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 2, 2);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            h.setBlock(c.offset(dx, dy, dz), MultiContent.SOLID_STEEL_CASING.get());
        h.setBlock(c, net.minecraft.world.level.block.Blocks.AIR);
        h.setBlock(c.below(), MultiGameTests.hatch(HatchType.ENERGY, Direction.DOWN));
        h.setBlock(c.west(), MultiGameTests.hatch(HatchType.INPUT_BUS, Direction.WEST));
        h.setBlock(c.east(), MultiGameTests.hatch(HatchType.OUTPUT_BUS, Direction.EAST));
        h.setBlock(c.above(), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.UP));
        h.setBlock(c.south(), MultiGameTests.hatch(HatchType.MUFFLER, Direction.SOUTH));
        h.setBlock(c.north(), MultiContent.IMPLOSION_COMPRESSOR.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        ImplosionCompressorBlockEntity e = h.getBlockEntity(c.north(), ImplosionCompressorBlockEntity.class);
        e.maintenance().setMask(63);
        powered(h, c.below());
        ItemBusBlockEntity in = h.getBlockEntity(c.west(), ItemBusBlockEntity.class);
        ItemBusBlockEntity out = h.getBlockEntity(c.east(), ItemBusBlockEntity.class);
        in.setItem(0, new ItemStack(SelfTest.item("dust_ruby"), 4));
        in.setItem(1, new ItemStack(Items.TNT, 12));
        h.succeedWhen(() -> {
            h.assertTrue(e.formed(), "not formed");
            h.assertTrue(count(out, SelfTest.item("gem_ruby")) == 3, "output " + out.stacks());
            h.assertTrue(count(out, SelfTest.item("tiny_dust_dark_ash")) == 12, "ash " + out.stacks());
        });
    }

    /** Мультиплавильня на мельхиоре (уровень 1): 8 булыжников за раз → 8 камня. */
    static void multiSmelterSmeltsEight(GameTestHelper h) {
        BlockPos c0 = new BlockPos(4, 1, 2);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos b = c0.offset(dx, 0, dz);
            h.setBlock(b, MultiContent.HEAT_PROOF_CASING.get());
            if (dx != 0 || dz != 0) h.setBlock(b.above(), MultiContent.COIL_CUPRONICKEL.get());
            h.setBlock(b.above(2), MultiContent.HEAT_PROOF_CASING.get());
        }
        h.setBlock(c0, MultiGameTests.hatch(HatchType.ENERGY, Direction.DOWN));
        h.setBlock(c0.west(), MultiGameTests.hatch(HatchType.INPUT_BUS, Direction.WEST));
        h.setBlock(c0.east(), MultiGameTests.hatch(HatchType.OUTPUT_BUS, Direction.EAST));
        h.setBlock(c0.south(), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.SOUTH));
        h.setBlock(c0.above(2), MultiGameTests.hatch(HatchType.MUFFLER, Direction.UP));
        h.setBlock(c0.north(), MultiContent.MULTI_SMELTER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        MultiSmelterBlockEntity e = h.getBlockEntity(c0.north(), MultiSmelterBlockEntity.class);
        e.maintenance().setMask(63);
        powered(h, c0);
        ItemBusBlockEntity in = h.getBlockEntity(c0.west(), ItemBusBlockEntity.class);
        ItemBusBlockEntity out = h.getBlockEntity(c0.east(), ItemBusBlockEntity.class);
        in.setItem(0, new ItemStack(Items.COBBLESTONE, 20));
        h.runAtTickTime(40, () -> {
            h.assertTrue(e.formed() && e.level() == 1, "formed " + e.formed() + " level " + e.level());
            h.assertTrue(in.stacks().get(0).getCount() == 12, "took " + (20 - in.stacks().get(0).getCount()) + ", must be 8");
        });
        h.succeedWhen(() -> h.assertTrue(count(out, Items.STONE) == 8, "output " + out.stacks()));
    }

    /** Пиролизная печь на вольфрамостальных катушках и HV: 16 дубовых брёвен + схема 1 → 20 древесного угля + 4000 л креозота. */
    static void pyrolyseMakesCharcoal(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 1, 3);
        Block coil = MultiContent.COIL_TUNGSTENSTEEL.get(), casing = MultiContent.CASING_ULV.get();
        for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) for (int y = 0; y < 4; y++) {
            boolean inner = Math.abs(i) < 2 && Math.abs(j) < 2;
            BlockPos p = c.offset(i, y, j);
            if (inner && y == 0) h.setBlock(p, coil);
            else if (inner && y < 3) h.setBlock(p, net.minecraft.world.level.block.Blocks.AIR);
            else h.setBlock(p, casing);
        }
        BlockPos energy = c.offset(-2, 0, 0), outBus = c.offset(2, 0, 0), outHatch = c.offset(0, 0, 2), maint = c.offset(-2, 0, 2);
        BlockPos inBus = c.offset(0, 3, 0), muffler = c.offset(-1, 3, 0);
        h.setBlock(energy, MultiGameTests.hatch(HatchType.ENERGY_HV, Direction.WEST));
        h.setBlock(outBus, MultiGameTests.hatch(HatchType.OUTPUT_BUS, Direction.EAST));
        h.setBlock(outHatch, MultiGameTests.hatch(HatchType.OUTPUT_HATCH, Direction.SOUTH));
        h.setBlock(maint, MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.SOUTH));
        h.setBlock(inBus, MultiGameTests.hatch(HatchType.INPUT_BUS, Direction.UP));
        h.setBlock(muffler, MultiGameTests.hatch(HatchType.MUFFLER, Direction.UP));
        BlockPos ctrl = c.offset(0, 0, -2);
        h.setBlock(ctrl, MultiContent.PYROLYSE_OVEN.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        PyrolyseOvenBlockEntity e = h.getBlockEntity(ctrl, PyrolyseOvenBlockEntity.class);
        e.maintenance().setMask(63);
        powered(h, energy);
        ItemBusBlockEntity in = h.getBlockEntity(inBus, ItemBusBlockEntity.class);
        in.setItem(0, new ItemStack(Items.OAK_LOG, 16));
        in.setItem(1, ru.iis.lv.item.ProgrammedCircuitItem.withConfig(new ItemStack(LvContent.PROGRAMMED_CIRCUIT.get()), 1));
        ItemBusBlockEntity out = h.getBlockEntity(outBus, ItemBusBlockEntity.class);
        FluidHatchBlockEntity fluid = h.getBlockEntity(outHatch, FluidHatchBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(e.formed(), "not formed");
            h.assertTrue(count(out, Items.CHARCOAL) == 20, "output " + out.stacks());
            h.assertTrue(fluid.tank().amount() == 4000, "creosote " + fluid.tank().amount());
        });
    }


    private static net.minecraft.world.level.material.Fluid fluid(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("iis", id));
    }

    /** Колонна в 3 этажа: гидрокрекинг этана 1000 л → 2000 л метана на первом этаже над низом (120 EU/t, MV). */
    static void towerSplitsCrackedEthane(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 1, 2);
        Block casing = MultiContent.CLEAN_STAINLESS_CASING.get();
        for (int y = 0; y < 3; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos p = c.offset(dx, y, dz);
            h.setBlock(p, y == 1 && dx == 0 && dz == 0 ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : casing.defaultBlockState());
        }
        h.setBlock(c.west(), MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.WEST));
        h.setBlock(c.east(), MultiGameTests.hatch(HatchType.ENERGY_MV, Direction.EAST));
        h.setBlock(c.south(), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.SOUTH));
        BlockPos out1 = c.above().west();
        h.setBlock(out1, MultiGameTests.hatch(HatchType.OUTPUT_HATCH, Direction.WEST));
        h.setBlock(c.north(), MultiContent.DISTILLATION_TOWER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        DistillationTowerBlockEntity e = h.getBlockEntity(c.north(), DistillationTowerBlockEntity.class);
        e.maintenance().setMask(63);
        powered(h, c.east());
        h.getBlockEntity(c.west(), FluidHatchBlockEntity.class).tank().setAmount(fluid("hydrocracked_ethane"), 1000);
        FluidHatchBlockEntity out = h.getBlockEntity(out1, FluidHatchBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(e.formed() && e.height() == 3, "formed " + e.formed() + " height " + e.height());
            h.assertTrue(out.tank().amount() == 2000 && out.tank().resource().getFluid() == fluid("methane"), "methane " + out.tank().amount());
        });
    }

    /** Крекер: пропан с торца + водород в середине → 1000 л гидрокрекинг-пропана в выходной люк другого торца. */
    static void crackerHydrocracksPropane(GameTestHelper h) {
        BlockPos ctrl = new BlockPos(4, 2, 1);
        Direction back = Direction.SOUTH, side = Direction.WEST;
        for (int depth = 0; depth < 3; depth++) for (int y = -1; y <= 1; y++) for (int w = -2; w <= 2; w++) {
            BlockPos p = ctrl.relative(back, depth).above(y).relative(side, w);
            if (depth == 1 && y == 0 && Math.abs(w) <= 1) continue;
            h.setBlock(p, Math.abs(w) == 1 ? MultiContent.COIL_CUPRONICKEL.get() : MultiContent.CLEAN_STAINLESS_CASING.get());
        }
        BlockPos in = ctrl.relative(back).relative(side, -2), out = ctrl.relative(back).relative(side, 2);
        BlockPos h2 = ctrl.relative(back, 2), energy = ctrl.relative(back).below(), maint = ctrl.relative(back).above();
        h.setBlock(in, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.EAST));
        h.setBlock(out, MultiGameTests.hatch(HatchType.OUTPUT_HATCH, Direction.WEST));
        h.setBlock(h2, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.SOUTH));
        h.setBlock(energy, MultiGameTests.hatch(HatchType.ENERGY_MV, Direction.DOWN));
        h.setBlock(maint, MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.UP));
        h.setBlock(ctrl, MultiContent.OIL_CRACKER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        OilCrackerBlockEntity e = h.getBlockEntity(ctrl, OilCrackerBlockEntity.class);
        e.maintenance().setMask(63);
        powered(h, energy);
        h.getBlockEntity(in, FluidHatchBlockEntity.class).tank().setAmount(fluid("propane"), 1000);
        h.getBlockEntity(h2, FluidHatchBlockEntity.class).tank().setAmount(fluid("hydrogen"), 1000);
        FluidHatchBlockEntity o = h.getBlockEntity(out, FluidHatchBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(e.formed(), "not formed");
            h.assertTrue(o.tank().amount() == 1000 && o.tank().resource().getFluid() == fluid("hydrocracked_propane"), "out " + o.tank().amount());
        });
    }

    /** Большой химреактор на MV: 9 ПДМС + сера → 1296 л силикона; идеальный разгон 30 EU/t × 600 → 120 EU/t × 150 тиков. */
    static void largeChemicalReactorSilicone(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 2, 2);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            h.setBlock(c.offset(dx, dy, dz), MultiContent.CHEMICALLY_INERT_CASING.get());
        h.setBlock(c, MultiContent.PTFE_PIPE_CASING.get());
        h.setBlock(c.above(), MultiContent.COIL_CUPRONICKEL.get());
        h.setBlock(c.below(), MultiGameTests.hatch(HatchType.ENERGY_MV, Direction.DOWN));
        h.setBlock(c.west(), MultiGameTests.hatch(HatchType.INPUT_BUS, Direction.WEST));
        h.setBlock(c.east(), MultiGameTests.hatch(HatchType.OUTPUT_HATCH, Direction.EAST));
        h.setBlock(c.south(), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.SOUTH));
        h.setBlock(c.north(), MultiContent.LARGE_CHEMICAL_REACTOR.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        LargeChemicalReactorBlockEntity e = h.getBlockEntity(c.north(), LargeChemicalReactorBlockEntity.class);
        e.maintenance().setMask(63);
        powered(h, c.below());
        ItemBusBlockEntity in = h.getBlockEntity(c.west(), ItemBusBlockEntity.class);
        in.setItem(0, new ItemStack(SelfTest.item("dust_polydimethylsiloxane"), 9));
        in.setItem(1, new ItemStack(SelfTest.item("dust_sulfur")));
        FluidHatchBlockEntity out = h.getBlockEntity(c.east(), FluidHatchBlockEntity.class);
        h.runAtTickTime(60, () -> h.assertTrue(e.formed() && e.maxProgress() == 150, "formed " + e.formed() + " time " + e.maxProgress()));
        h.succeedWhen(() -> h.assertTrue(out.tank().amount() == 1296 && out.tank().resource().getFluid() == fluid("molten_silicone"),
                "silicone " + out.tank().amount()));
    }

    /** Большая газовая турбина на метане (стальной малый ротор, люк MV): мощность растёт, энергия идёт в динамо-люк, метан расходуется. */
    static void gasTurbineBurnsMethane(GameTestHelper h) {
        for (int x = 3; x <= 5; x++) for (int y = 1; y <= 3; y++) for (int z = 1; z <= 4; z++)
            h.setBlock(new BlockPos(x, y, z), MultiContent.TURBINE_CASING_STAINLESS.get());
        h.setBlock(new BlockPos(4, 2, 2), net.minecraft.world.level.block.Blocks.AIR);
        h.setBlock(new BlockPos(4, 2, 3), net.minecraft.world.level.block.Blocks.AIR);
        BlockPos fuelPos = new BlockPos(4, 3, 2), muffler = new BlockPos(3, 2, 2), dynamo = new BlockPos(4, 2, 4), ctrl = new BlockPos(4, 2, 1);
        h.setBlock(fuelPos, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.UP));
        h.setBlock(muffler, MultiGameTests.hatch(HatchType.MUFFLER, Direction.WEST));
        h.setBlock(new BlockPos(4, 1, 3), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.DOWN));
        h.setBlock(dynamo, MultiGameTests.hatch(HatchType.DYNAMO_MV, Direction.SOUTH));
        h.setBlock(ctrl, MultiContent.LARGE_GAS_TURBINE.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        LargeGasTurbineBlockEntity t = h.getBlockEntity(ctrl, LargeGasTurbineBlockEntity.class);
        t.maintenance().setMask(63);
        t.setItem(0, MultiContent.ROTORS.get(ru.iis.multi.logic.LargeTurbineLogic.Size.SMALL).get().create("steel"));
        FluidHatchBlockEntity fuel = h.getBlockEntity(fuelPos, FluidHatchBlockEntity.class);
        fuel.tank().setAmount(fluid("methane"), 16000);
        DynamoHatchBlockEntity dyn = h.getBlockEntity(dynamo, DynamoHatchBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(t.formed(), "turbine not formed");
            h.assertTrue(t.eut() > 20, "turbine EU/t " + t.eut());
            h.assertTrue(dyn.energy.stored > DynamoHatchBlockEntity.MIN_STORED, "dynamo got " + dyn.energy.stored);
            h.assertTrue(fuel.tank().amount() < 16000, "no methane burnt");
        });
    }
}

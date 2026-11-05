package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.command.SelfTest;
import ru.iis.fluid.GtFluids;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.lv.item.DataStickItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;

/** Уровень LuV (D110): сканер, флешка, люк данных, сборочная линия. */
final class LuvGameTests {
    private LuvGameTests() {}

    /** Контроллер на север; столбцы по x: 8 (левый, глядя на контроллер), 7, 6, 5, 4; ряды z = 2, 3, 4; слои y = 1…4. */
    private static final BlockPos CONTROLLER = new BlockPos(6, 3, 2);

    private static RecipeHolder<GtRecipe> plan(GameTestHelper h, String id) {
        for (RecipeHolder<GtRecipe> r : GtRecipes.all(h.getLevel().recipeAccess().recipeMap(), GtRecipeMap.ASSEMBLY_LINE)) {
            if (r.id().identifier().getPath().endsWith("/" + id)) return r;
        }
        throw new AssertionError("no plan " + id);
    }

    private static void set(GameTestHelper h, BlockPos p, Block b) {
        h.setBlock(p, b);
    }

    private static void hatch(GameTestHelper h, BlockPos p, HatchType t) {
        BlockState s = MultiGameTests.hatch(t, Direction.DOWN);
        h.setBlock(p, s);
    }

    /** Форма из GT_MetaTileEntity_AssemblyLine#checkMachine: 5 столбцов, низ — стальные корпуса, люки ставит тест. */
    private static AssemblyLineBlockEntity line(GameTestHelper h) {
        frame(h, HatchType.ENERGY_IV);
        // порядок обхода GT: столбец за столбцом слева направо, в столбце — от контроллера назад
        int[][] buses = {{8, 2}, {8, 3}, {8, 4}, {7, 2}, {7, 3}, {7, 4}, {6, 2}};
        for (int[] b : buses) hatch(h, new BlockPos(b[0], 1, b[1]), HatchType.INPUT_BUS);
        hatch(h, new BlockPos(6, 1, 3), HatchType.INPUT_HATCH);
        hatch(h, new BlockPos(6, 1, 4), HatchType.INPUT_HATCH);
        hatch(h, new BlockPos(5, 1, 2), HatchType.MAINTENANCE);
        hatch(h, new BlockPos(5, 1, 3), HatchType.OUTPUT_BUS);
        AssemblyLineBlockEntity e = h.getBlockEntity(CONTROLLER, AssemblyLineBlockEntity.class);
        e.maintenance().setMask(63);
        return e;
    }

    /** Корпус линии без шин и люков. */
    private static void frame(GameTestHelper h, HatchType energyType) {
        for (int x = 4; x <= 8; x++) {
            for (int z = 2; z <= 4; z++) set(h, new BlockPos(x, 1, z), MultiContent.SOLID_STEEL_CASING.get());
            set(h, new BlockPos(x, 2, 2), MultiContent.REINFORCED_GLASS.get());
            set(h, new BlockPos(x, 2, 3), MultiContent.GEAR_BOX_TUNGSTEN_STEEL.get());
            set(h, new BlockPos(x, 2, 4), MultiContent.REINFORCED_GLASS.get());
            set(h, new BlockPos(x, 3, 2), MultiContent.GRATE_CASING.get());
            set(h, new BlockPos(x, 3, 3), MultiContent.ASSEMBLER_CASING.get());
            set(h, new BlockPos(x, 3, 4), MultiContent.GRATE_CASING.get());
            set(h, new BlockPos(x, 4, 3), MultiContent.SOLID_STEEL_CASING.get());
        }
        h.setBlock(CONTROLLER, MultiContent.ASSEMBLY_LINE.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        BlockPos energy = new BlockPos(6, 4, 3);
        hatch(h, energy, energyType);
        h.onEachTick(() -> {
            if (h.getLevel().getBlockEntity(h.absolutePos(energy)) instanceof EnergyHatchBlockEntity e) e.energy.stored = e.energy.capacity;
        });
    }

    /** GT_MachineRecipeLoader:2046: всё для двигателя LuV — ингредиенты по шинам, жидкости по люкам. */
    private static void motorInputs(GameTestHelper h, boolean swapped) {
        Object[][] in = {{"long_rod_neodymium_magnetic", 1}, {"long_rod_hssg", 2}, {"wire_fine_annealed_copper", 64}, {"wire_fine_annealed_copper", 64},
                {"wire_fine_annealed_copper", 64}, {"wire_fine_annealed_copper", 64}, {"cable_yttrium_barium_cuprate_1x", 2}};
        int[][] buses = {{8, 2}, {8, 3}, {8, 4}, {7, 2}, {7, 3}, {7, 4}, {6, 2}};
        for (int i = 0; i < in.length; i++) {
            int k = swapped && i < 2 ? 1 - i : i;
            h.getBlockEntity(new BlockPos(buses[k][0], 1, buses[k][1]), ItemBusBlockEntity.class)
                    .setItem(0, new ItemStack(SelfTest.item((String) in[i][0]), (Integer) in[i][1]));
        }
        h.getBlockEntity(new BlockPos(6, 1, 3), FluidHatchBlockEntity.class).tank().setAmount(GtFluids.get("molten_solderingalloy").source().get(), 1000);
        h.getBlockEntity(new BlockPos(6, 1, 4), FluidHatchBlockEntity.class).tank().setAmount(GtFluids.get("lubricant").source().get(), 1000);
    }

    /** Двигатель LuV: 600 тиков, 6000 EU/t; ингредиенты списаны, в люках убыло 144 и 250 л. */
    static void assemblyLineBuildsMotor(GameTestHelper h) {
        AssemblyLineBlockEntity e = line(h);
        e.setItem(0, DataStickItem.written(plan(h, "electric_motor_luv")));
        motorInputs(h, false);
        ItemBusBlockEntity out = h.getBlockEntity(new BlockPos(5, 1, 3), ItemBusBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(e.formed(), "not formed");
            h.assertTrue(out.stacks().stream().anyMatch(s -> s.is(LvContent.ELECTRIC_MOTOR_LUV.get())), "output " + out.stacks());
            h.assertTrue(h.getBlockEntity(new BlockPos(8, 1, 2), ItemBusBlockEntity.class).getItem(0).isEmpty(), "rod left");
            h.assertTrue(h.getBlockEntity(new BlockPos(6, 1, 3), FluidHatchBlockEntity.class).tank().amount() == 856, "solder");
            h.assertTrue(h.getBlockEntity(new BlockPos(6, 1, 4), FluidHatchBlockEntity.class).tank().amount() == 750, "lubricant");
        });
    }

    /** Ингредиенты не в тех шинах: рецепт не начинается, ничего не списано. */
    static void assemblyLineChecksOrder(GameTestHelper h) {
        AssemblyLineBlockEntity e = line(h);
        e.setItem(0, DataStickItem.written(plan(h, "electric_motor_luv")));
        motorInputs(h, true);
        h.runAtTickTime(120, () -> {
            h.assertTrue(e.formed(), "not formed");
            h.assertTrue(e.maxProgress() == 0, "started " + e.maxProgress());
            h.assertTrue(h.getBlockEntity(new BlockPos(8, 1, 2), ItemBusBlockEntity.class).getItem(0).getCount() == 2, "consumed");
            h.succeed();
        });
    }

    /** Без флешки линия стоит; флешка в люке данных вместо решётки её запускает. Люк принимает только флешки. */
    static void assemblyLineReadsDataHatch(GameTestHelper h) {
        AssemblyLineBlockEntity e = line(h);
        motorInputs(h, false);
        h.runAtTickTime(60, () -> {
            h.assertTrue(e.formed() && e.maxProgress() == 0, "ran without a stick");
            hatch(h, new BlockPos(8, 3, 2), HatchType.DATA_ACCESS);
            ItemBusBlockEntity d = h.getBlockEntity(new BlockPos(8, 3, 2), ItemBusBlockEntity.class);
            h.assertTrue(d.getContainerSize() == 4, "slots " + d.getContainerSize());
            h.assertFalse(d.canPlaceItem(0, new ItemStack(Items.DIRT)), "takes dirt");
            h.assertTrue(d.canPlaceItem(0, new ItemStack(LvContent.DATA_STICK.get())), "refuses a stick");
            d.setItem(0, DataStickItem.written(plan(h, "electric_motor_luv")));
        });
        h.succeedWhen(() -> h.assertTrue(e.formed() && e.maxProgress() == 600, "progress " + e.maxProgress()));
    }

    /** Форма: без армированного стекла и у́же 5 столбцов — не собрана. */
    static void assemblyLineShape(GameTestHelper h) {
        AssemblyLineBlockEntity e = line(h);
        h.runAtTickTime(40, () -> {
            h.assertTrue(e.formed(), "not formed");
            h.setBlock(new BlockPos(7, 2, 2), net.minecraft.world.level.block.Blocks.GLASS);
        });
        h.runAtTickTime(80, () -> {
            h.assertFalse(e.formed(), "formed with plain glass");
            h.succeed();
        });
    }

    /** Сканер: двигатель IV и пустая флешка → флешка с чертежом двигателя LuV; 144000 тиков на LV, у IV четыре разгона. */
    static void scannerWritesPlan(GameTestHelper h) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), LvMachineType.SCANNER_IV, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        m.setItem(0, new ItemStack(LvContent.ELECTRIC_MOTOR_IV.get()));
        m.setItem(2, new ItemStack(LvContent.DATA_STICK.get()));
        h.runAtTickTime(30, () -> {
            h.assertTrue(m.logic().maxProgress == 9000, "time " + m.logic().maxProgress);
            h.assertTrue(m.getItem(0).isEmpty() && m.getItem(2).isEmpty(), "inputs not consumed");
            m.logic().progress = m.logic().maxProgress - 2;
        });
        h.succeedWhen(() -> {
            ItemStack out = m.getItem(1);
            h.assertTrue(out.is(LvContent.DATA_STICK.get()), "output " + out);
            DataStickItem.Research r = out.get(LvContent.RESEARCH.get());
            h.assertTrue(r != null && r.recipe().getPath().endsWith("assembly_line/electric_motor_luv"), "plan " + r);
        });
    }

    /** Сканер не трогает предмет, которого нет в исследованиях. */
    static void scannerIgnoresOtherItems(GameTestHelper h) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), LvMachineType.SCANNER_IV, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        m.setItem(0, new ItemStack(Items.DIRT));
        m.setItem(2, new ItemStack(LvContent.DATA_STICK.get()));
        h.runAtTickTime(40, () -> {
            h.assertTrue(m.logic().maxProgress == 0, "started");
            h.assertTrue(m.getItem(0).getCount() == 1 && m.getItem(2).getCount() == 1, "consumed");
            h.succeed();
        });
    }

    private static ElectricMachineBlockEntity powered(GameTestHelper h, LvMachineType t) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), t, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        return m;
    }

    /** D111: стекловолокно (GT_MachineRecipeLoader:497): слиток боросиликатного стекла и насадка «провод» → 8 штук, насадка остаётся. */
    static void extruderMakesGlassFiber(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.EXTRUDER_MV);
        m.setItem(0, new ItemStack(SelfTest.item("ingot_borosilicate_glass")));
        m.setItem(1, new ItemStack(SelfTest.item("shape_extruder_wire")));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(2).is(LvContent.GLASS_FIBER.get()) && m.getItem(2).getCount() == 8, "output " + m.getItem(2));
            h.assertTrue(m.getItem(1).is(SelfTest.item("shape_extruder_wire")), "shape consumed");
        });
    }

    /** D111: фильтр воды (:2600): малая пластиковая труба, 8 углеродного и 32 стекловолокна → 4 фильтра. */
    static void assemblerMakesFluidFilter(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.ASSEMBLER_MV);
        m.setItem(0, new ItemStack(SelfTest.item("pipe_small_plastic")));
        m.setItem(1, new ItemStack(LvContent.CARBON_FIBER.get(), 8));
        m.setItem(2, new ItemStack(LvContent.GLASS_FIBER.get(), 32));
        h.succeedWhen(() -> h.assertTrue(m.getItem(6).is(SelfTest.item("fluid_filter")) && m.getItem(6).getCount() == 4, "output " + m.getItem(6)));
    }

    /** D111: радон (:1829): 6 слитков плутония → 6 пыли и 100 л радона; идёт 12000 тиков у LV, конец рецепта подводит тест. */
    static void plutoniumGivesRadon(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.CHEMICAL_REACTOR_IV);
        m.setItem(0, new ItemStack(SelfTest.item("ingot_plutonium"), 6));
        h.runAtTickTime(30, () -> {
            h.assertTrue(m.logic().maxProgress > 0, "not started");
            m.logic().progress = m.logic().maxProgress - 2;
        });
        h.succeedWhen(() -> {
            h.assertTrue(m.outTank().amount() == 100 && m.outTank().resource().getFluid() == GtFluids.get("radon").source().get(), "radon " + m.outTank().amount());
            h.assertTrue(m.getItem(3).is(SelfTest.item("dust_plutonium")) && m.getItem(3).getCount() == 6, "dust " + m.getItem(3));
        });
    }

    /** D111: квантовое око (:1190): око Края в 250 л радона. */
    static void radonMakesQuantumEye(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.CHEMICAL_BATH_IV);
        m.setItem(0, new ItemStack(Items.ENDER_EYE));
        m.tank().setAmount(GtFluids.get("radon").source().get(), 250);
        h.succeedWhen(() -> h.assertTrue(m.getItem(1).is(LvContent.QUANTUM_EYE.get()), "output " + m.getItem(1)));
    }

    // --- D112: уровень ZPM ---------------------------------------------------------------------------------------------

    /** Двигатель ZPM (GT_MachineRecipeLoader:2058): девять шин, 24000 EU/t от люка LuV; в люках убыло 288 и 750 л. */
    static void assemblyLineBuildsZpmMotor(GameTestHelper h) {
        frame(h, HatchType.ENERGY_LUV);
        int[][] buses = {{8, 2}, {8, 3}, {8, 4}, {7, 2}, {7, 3}, {7, 4}, {6, 2}, {6, 3}, {6, 4}};
        for (int[] b : buses) hatch(h, new BlockPos(b[0], 1, b[1]), HatchType.INPUT_BUS);
        hatch(h, new BlockPos(5, 1, 2), HatchType.INPUT_HATCH);
        hatch(h, new BlockPos(5, 1, 3), HatchType.INPUT_HATCH);
        hatch(h, new BlockPos(5, 1, 4), HatchType.MAINTENANCE);
        hatch(h, new BlockPos(4, 1, 3), HatchType.OUTPUT_BUS);
        AssemblyLineBlockEntity e = h.getBlockEntity(CONTROLLER, AssemblyLineBlockEntity.class);
        e.maintenance().setMask(63);
        e.setItem(0, DataStickItem.written(plan(h, "electric_motor_zpm")));
        Object[][] in = {{"long_rod_neodymium_magnetic", 2}, {"long_rod_hsse", 4}, {"ring_hsse", 4}, {"round_hsse", 16}, {"wire_fine_platinum", 64}, {"wire_fine_platinum", 64},
                {"wire_fine_platinum", 64}, {"wire_fine_platinum", 64}, {"cable_vanadium_gallium_4x", 2}};
        for (int i = 0; i < in.length; i++) {
            h.getBlockEntity(new BlockPos(buses[i][0], 1, buses[i][1]), ItemBusBlockEntity.class).setItem(0, new ItemStack(SelfTest.item((String) in[i][0]), (Integer) in[i][1]));
        }
        FluidHatchBlockEntity solder = h.getBlockEntity(new BlockPos(5, 1, 2), FluidHatchBlockEntity.class), lube = h.getBlockEntity(new BlockPos(5, 1, 3), FluidHatchBlockEntity.class);
        solder.tank().setAmount(GtFluids.get("molten_solderingalloy").source().get(), 1000);
        lube.tank().setAmount(GtFluids.get("lubricant").source().get(), 1000);
        ItemBusBlockEntity out = h.getBlockEntity(new BlockPos(4, 1, 3), ItemBusBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(out.stacks().stream().anyMatch(s -> s.is(LvContent.ELECTRIC_MOTOR_ZPM.get())), "output " + out.stacks());
            h.assertTrue(solder.tank().amount() == 712 && lube.tank().amount() == 250, "fluids " + solder.tank().amount() + " " + lube.tank().amount());
        });
    }

    /** Сканер ZPM: 144000 тиков на LV и шесть разгонов — 2250; из детали LuV пишет чертёж детали ZPM. */
    static void scannerZpmResearchesLuvPart(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.SCANNER_ZPM);
        m.setItem(0, new ItemStack(LvContent.ELECTRIC_MOTOR_LUV.get()));
        m.setItem(2, new ItemStack(LvContent.DATA_STICK.get()));
        h.runAtTickTime(30, () -> {
            h.assertTrue(m.logic().maxProgress == 2250, "time " + m.logic().maxProgress);
            m.logic().progress = m.logic().maxProgress - 2;
        });
        h.succeedWhen(() -> {
            DataStickItem.Research r = m.getItem(1).get(LvContent.RESEARCH.get());
            h.assertTrue(r != null && r.recipe().getPath().endsWith("assembly_line/electric_motor_zpm"), "plan " + r);
        });
    }

    /** Квантовый процессор (GT_MachineRecipeLoader:585) в сборщике схем LuV, который теперь можно собрать. */
    static void circuitAssemblerMakesQuantumProcessor(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.CIRCUIT_ASSEMBLER_LUV);
        Object[][] in = {{"circuit_board_fiberglass", 1}, {"chip_quantum_cpu", 1}, {"chip_nano_cpu", 1}, {"capacitor_smd", 2}, {"transistor_smd", 2}, {"wire_fine_platinum", 2}};
        for (int i = 0; i < in.length; i++) m.setItem(i, new ItemStack(SelfTest.item((String) in[i][0]), (Integer) in[i][1]));
        m.tank().setAmount(GtFluids.get("molten_solderingalloy").source().get(), 72);
        h.succeedWhen(() -> h.assertTrue(m.getItem(6).is(LvContent.CIRCUIT_QUANTUMPROCESSOR.get()), "output " + m.getItem(6)));
    }

    /** Кубитная пластина (:521): нанопластина, два квантовых ока, 288 л расплава арсенида галлия. */
    static void reactorMakesQbitWafer(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.CHEMICAL_REACTOR_IV);
        m.setItem(0, new ItemStack(SelfTest.item("wafer_nano_cpu")));
        m.setItem(1, new ItemStack(LvContent.QUANTUM_EYE.get(), 2));
        m.tank().setAmount(GtFluids.get("molten_galliumarsenide").source().get(), 288);
        h.succeedWhen(() -> h.assertTrue(m.getItem(3).is(LvContent.WAFER_QUANTUM_CPU.get()), "output " + m.getItem(3)));
    }

    /** HSS-E плавится при 5400 K: катушка HSS-G тянет. */
    static void ebfMeltsHsseOnHssgCoil(GameTestHelper h) {
        EbfBlockEntity e = MultiGameTests.build(h, 1, HatchType.ENERGY_EV, MultiContent.COIL_HSSG.get(), true);
        e.maintenance().setMask(63);
        ItemBusBlockEntity in = h.getBlockEntity(new BlockPos(5, 1, 2), ItemBusBlockEntity.class);
        in.setItem(0, new ItemStack(SelfTest.item("dust_hsse")));
        h.succeedWhen(() -> {
            h.assertTrue(e.heat() == EbfBlockEntity.HSSG_HEAT, "heat " + e.heat());
            h.assertTrue(in.getItem(0).isEmpty(), "dust not taken");
        });
    }

    /** Вольфрамосталь (4500 K) для HSS-E мала: пыль остаётся в шине. */
    static void ebfRefusesHsseOnTungstensteelCoil(GameTestHelper h) {
        EbfBlockEntity e = MultiGameTests.build(h, 1, HatchType.ENERGY_EV, MultiContent.COIL_TUNGSTENSTEEL.get(), true);
        e.maintenance().setMask(63);
        ItemBusBlockEntity in = h.getBlockEntity(new BlockPos(5, 1, 2), ItemBusBlockEntity.class);
        in.setItem(0, new ItemStack(SelfTest.item("dust_hsse")));
        h.runAtTickTime(120, () -> {
            h.assertTrue(e.heat() == EbfBlockEntity.TUNGSTENSTEEL_HEAT, "heat " + e.heat());
            h.assertTrue(in.getItem(0).getCount() == 1, "dust taken");
            h.succeed();
        });
    }

    /** D114: компрессор со схемой 1 набирает 2000 л воздуха за 30 тиков (GT_MachineRecipeLoader:1834), схема остаётся. */
    static void compressorDrawsAir(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.COMPRESSOR_MV);
        m.setItem(0, ru.iis.lv.item.ProgrammedCircuitItem.withConfig(new ItemStack(LvContent.PROGRAMMED_CIRCUIT.get()), 1));
        h.succeedWhen(() -> {
            h.assertTrue(m.outTank().resource().getFluid() == GtFluids.get("air").source().get() && m.outTank().amount() >= 2000, "air " + m.outTank().amount());
            h.assertTrue(!m.getItem(0).isEmpty(), "circuit consumed");
        });
    }
}

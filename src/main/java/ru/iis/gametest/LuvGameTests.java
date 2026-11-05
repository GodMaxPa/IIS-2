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
        hatch(h, energy, HatchType.ENERGY_IV);
        h.onEachTick(() -> {
            if (h.getLevel().getBlockEntity(h.absolutePos(energy)) instanceof EnergyHatchBlockEntity e) e.energy.stored = e.energy.capacity;
        });
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
}

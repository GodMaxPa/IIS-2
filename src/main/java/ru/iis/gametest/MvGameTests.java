package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import ru.iis.command.SelfTest;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.TransformerBlock;
import ru.iis.lv.block.TransformerBlockEntity;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.HotIngotHeat;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;

/** Эпоха MV (D81, D82): MV-машины, трансформатор и люк MV, термоцентрифуга LV/MV, вакуумный морозильник, горячие слитки. */
final class MvGameTests {
    private MvGameTests() {}

    private static ElectricMachineBlockEntity powered(GameTestHelper h, BlockPos p, LvMachineType t) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, p, t, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        return m;
    }

    /** MV: дроблёный халькопирит → центрифугированный + крошечный кобальт + каменная пыль (500 тиков, 48 EU/t). */
    static void thermalCentrifugeMv(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.THERMAL_CENTRIFUGE_MV);
        m.setItem(0, new ItemStack(SelfTest.item("crushed_chalcopyrite")));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(1).is(SelfTest.item("centrifuged_chalcopyrite")), "out 0 " + m.getItem(1));
            h.assertTrue(m.getItem(2).is(SelfTest.item("tiny_dust_cobalt")), "out 1 " + m.getItem(2));
            h.assertTrue(m.getItem(3).is(SelfTest.item("dust_stone")), "out 2 " + m.getItem(3));
        });
    }

    /** LV: рецепты 48 EU/t выше 32 В — машина стоит (как в GT5). */
    static void thermalCentrifugeLvIdle(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.THERMAL_CENTRIFUGE);
        m.setItem(0, new ItemStack(SelfTest.item("crushed_chalcopyrite")));
        h.runAtTickTime(60, () -> {
            h.assertTrue(m.logic().maxProgress == 0, "LV thermal centrifuge started a 48 EU/t recipe");
            h.succeed();
        });
    }

    /** Куб 3×3×3: середина (4,2,3), контроллер (4,2,2) лицом на север; 4 люка LV, шины, обслуживание, 18 корпусов. */
    static VacuumFreezerBlockEntity freezer(GameTestHelper h) {
        return freezer(h, HatchType.ENERGY, 4);
    }

    /** То же с {@code count} энергетическими люками типа {@code energyType} (снизу, сверху, слева, справа от середины). */
    static VacuumFreezerBlockEntity freezer(GameTestHelper h, HatchType energyType, int count) {
        BlockPos center = new BlockPos(4, 2, 3);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) h.setBlock(center.offset(dx, dy, dz), MultiContent.FROST_PROOF_CASING.get());
        h.setBlock(center, Blocks.AIR);
        BlockPos[] energy = java.util.Arrays.copyOf(new BlockPos[]{center.below(), center.above(), center.west(), center.east()}, count);
        for (BlockPos p : energy) h.setBlock(p, MultiGameTests.hatch(energyType, Direction.UP));
        h.setBlock(center.south(), MultiGameTests.hatch(HatchType.INPUT_BUS, Direction.SOUTH));
        h.setBlock(center.south().east(), MultiGameTests.hatch(HatchType.OUTPUT_BUS, Direction.SOUTH));
        h.setBlock(center.south().west(), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.SOUTH));
        BlockPos ctrl = center.north();
        h.setBlock(ctrl, MultiContent.VACUUM_FREEZER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        VacuumFreezerBlockEntity f = h.getBlockEntity(ctrl, VacuumFreezerBlockEntity.class);
        f.maintenance().setMask(63);
        h.onEachTick(() -> {
            for (BlockPos p : energy) {
                if (h.getLevel().getBlockEntity(h.absolutePos(p)) instanceof EnergyHatchBlockEntity eh) eh.energy.stored = eh.energy.capacity;
            }
        });
        return f;
    }

    /** Горячий иридиевый слиток → иридиевый слиток (масса 192 × 3 = 576 тиков, 120 EU/t). */
    static void freezerHotIngot(GameTestHelper h) {
        freezer(h);
        h.getBlockEntity(new BlockPos(4, 2, 4), ItemBusBlockEntity.class).setItem(0, new ItemStack(SelfTest.item("hot_ingot_iridium")));
        ItemBusBlockEntity out = h.getBlockEntity(new BlockPos(5, 2, 4), ItemBusBlockEntity.class);
        h.succeedWhen(() -> h.assertTrue(out.stacks().stream().anyMatch(s -> s.is(SelfTest.item("ingot_iridium"))), "output " + out.stacks()));
    }

    /** Один энергетический люк MV (128 В, D82) тянет рецепты 120 EU/t (палладий: масса 106 × 3 = 318 тиков); один люк LV (32 В) — нет. */
    static void freezerOneMvHatch(GameTestHelper h) {
        freezer(h, HatchType.ENERGY_MV, 1);
        h.getBlockEntity(new BlockPos(4, 2, 4), ItemBusBlockEntity.class).setItem(0, new ItemStack(SelfTest.item("hot_ingot_palladium")));
        ItemBusBlockEntity out = h.getBlockEntity(new BlockPos(5, 2, 4), ItemBusBlockEntity.class);
        h.succeedWhen(() -> h.assertTrue(out.stacks().stream().anyMatch(s -> s.is(SelfTest.item("ingot_palladium"))), "output " + out.stacks()));
    }

    static void freezerOneLvHatchIdle(GameTestHelper h) {
        VacuumFreezerBlockEntity f = freezer(h, HatchType.ENERGY, 1);
        h.getBlockEntity(new BlockPos(4, 2, 4), ItemBusBlockEntity.class).setItem(0, new ItemStack(SelfTest.item("hot_ingot_palladium")));
        h.runAtTickTime(60, () -> {
            h.assertTrue(f.formed(), "freezer not formed");
            h.assertTrue(f.maxProgress() == 0, "120 EU/t recipe started on one LV hatch");
            h.succeed();
        });
    }

    /** Пустая середина обязательна: блок внутри — форма не собрана. */
    static void freezerNeedsHollow(GameTestHelper h) {
        VacuumFreezerBlockEntity f = freezer(h);
        h.runAtTickTime(25, () -> {
            h.assertTrue(f.formed(), "freezer not formed");
            h.setBlock(new BlockPos(4, 2, 3), MultiContent.FROST_PROOF_CASING.get());
        });
        h.runAtTickTime(50, () -> {
            h.assertFalse(f.formed(), "freezer formed with a filled middle");
            h.succeed();
        });
    }

    /** MV-дробитель: руда 2 EU/t × 400 тиков → 8 EU/t × 200 тиков (GT calculateOverclockedNess, D82). */
    static void maceratorMvOverclock(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.MACERATOR_MV);
        m.setItem(0, new ItemStack(SelfTest.item("ore_chalcopyrite")));
        h.succeedWhen(() -> {
            h.assertTrue(m.logic().maxProgress == 200 && m.logic().eut == 8, "maxProgress " + m.logic().maxProgress + ", eut " + m.logic().eut);
        });
    }

    /** Повышающий трансформатор LV → медный кабель → MV-дробитель: 128 В машина MV принимает и работает. */
    static void lvStepUpPowersMvMachine(GameTestHelper h) {
        TransformerBlockEntity t = LvGameTests.transformer(h, new BlockPos(2, 1, 4), Direction.EAST, true);
        for (int x = 3; x <= 4; x++) LvGameTests.cable(h, new BlockPos(x, 1, 4), "cable_copper_1x");
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(5, 1, 4), LvMachineType.MACERATOR_MV, Direction.NORTH);
        LvGameTests.cable(h, new BlockPos(4, 1, 4), "cable_copper_1x");
        h.onEachTick(() -> t.energy.stored = t.energy.capacity);
        m.setItem(0, new ItemStack(SelfTest.item("ore_chalcopyrite")));
        h.succeedWhen(() -> h.assertTrue(m.getItem(1).is(SelfTest.item("crushed_chalcopyrite")), "output " + m.getItem(1)));
    }

    /** Трансформатор MV (D82): понижает 512 → 128 В, 4 А с пяти сторон; запас 512 + 2 × 512. */
    static void transformerMvStepDown(GameTestHelper h) {
        BlockPos tp = new BlockPos(2, 1, 4);
        h.setBlock(tp, LvContent.TRANSFORMER_MV.get().defaultBlockState().setValue(TransformerBlock.FACING, Direction.WEST));
        TransformerBlockEntity t = h.getBlockEntity(tp, TransformerBlockEntity.class);
        h.assertTrue(t.outputVoltage() == 128 && t.outputAmperes() == 4, "step-down out " + t.outputVoltage() + " V " + t.outputAmperes() + " A");
        h.assertTrue(t.energy.capacity == 512 + 2 * 512, "capacity " + t.energy.capacity);
        LvGameTests.cable(h, new BlockPos(3, 1, 4), "cable_copper_4x");
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), LvMachineType.MACERATOR_MV, Direction.NORTH);
        LvGameTests.cable(h, new BlockPos(3, 1, 4), "cable_copper_4x");
        h.onEachTick(() -> t.energy.stored = t.energy.capacity);
        h.succeedWhen(() -> h.assertTrue(m.energy.stored >= m.energy.capacity / 2 && !(h.getBlockState(tp).isAir()), "MV machine stored " + m.energy.stored));
    }

    /** MV-экструдер (D83): стальной слиток + насадка «пластина» → пластина за 55 тиков при 128 EU/t; насадка остаётся. */
    static void extruderMvPlate(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.EXTRUDER_MV);
        m.setItem(0, new ItemStack(SelfTest.item("ingot_steel")));
        m.setItem(1, new ItemStack(SelfTest.item("shape_extruder_plate")));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(2).is(SelfTest.item("plate_steel")), "output " + m.getItem(2));
            h.assertTrue(m.getItem(1).is(SelfTest.item("shape_extruder_plate")), "shape consumed: " + m.getItem(1));
        });
    }

    /** LV-экструдер: стальная пластина (128 EU/t) недоступна, а капсула из олова (32 EU/t) делается, как в GT. */
    static void extruderLvCellOnly(GameTestHelper h) {
        ElectricMachineBlockEntity plate = powered(h, new BlockPos(2, 1, 4), LvMachineType.EXTRUDER);
        plate.setItem(0, new ItemStack(SelfTest.item("ingot_steel")));
        plate.setItem(1, new ItemStack(SelfTest.item("shape_extruder_plate")));
        ElectricMachineBlockEntity cell = powered(h, new BlockPos(6, 1, 4), LvMachineType.EXTRUDER);
        cell.setItem(0, new ItemStack(SelfTest.item("ingot_tin"), 2));
        cell.setItem(1, new ItemStack(SelfTest.item("shape_extruder_cell")));
        h.succeedWhen(() -> {
            h.assertTrue(cell.getItem(2).is(SelfTest.item("capsule")), "cell output " + cell.getItem(2));
            h.assertTrue(plate.logic().maxProgress == 0 && plate.getItem(2).isEmpty(), "LV extruder ran a 128 EU/t recipe");
        });
    }

    /** Формовочный пресс LV (D83): латунная пластина + форма чеканки → 4 монеты Doge (100 тиков, 16 EU/t); форма остаётся. */
    static void pressDogeCoin(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.FORMING_PRESS);
        m.setItem(0, new ItemStack(SelfTest.item("plate_brass")));
        m.setItem(1, new ItemStack(SelfTest.item("mold_credit")));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(2).is(SelfTest.item("coin_doge")) && m.getItem(2).getCount() == 4, "output " + m.getItem(2));
            h.assertTrue(m.getItem(1).is(SelfTest.item("mold_credit")), "mold consumed: " + m.getItem(1));
        });
    }

    /** Нагреватель жидкостей LV: схема 1 + 6 л воды → 960 л пара (30 тиков, 32 EU/t); схема остаётся. */
    static void fluidHeaterSteam(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.FLUID_HEATER);
        m.setItem(0, ru.iis.lv.item.ProgrammedCircuitItem.withConfig(new ItemStack(LvContent.PROGRAMMED_CIRCUIT.get()), 1));
        m.tank().setAmount(net.minecraft.world.level.material.Fluids.WATER, 1000);
        h.succeedWhen(() -> {
            h.assertTrue(m.outTank().amount() >= 960 && m.outTank().resource().getFluid() == ru.iis.fluid.IISFluids.STEAM.get(),
                    "steam " + m.outTank().amount());
            h.assertTrue(ru.iis.lv.item.ProgrammedCircuitItem.config(m.getItem(0)) == 1, "the circuit must stay");
        });
    }

    /** Электродуховка MV: ванильная плавка с разгоном MV (4 EU/t × 128 → 16 EU/t × 64). */
    static void ovenMvSmelts(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.ELECTRIC_OVEN_MV);
        m.setItem(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        h.succeedWhen(() -> h.assertTrue(m.getItem(1).is(net.minecraft.world.item.Items.IRON_INGOT), "output " + m.getItem(1)));
    }

    /** MV-дуговая печь (D85): железная пластина + 56 л кислорода → слиток кованого железа (56 тиков, 96 EU/t). */
    static void arcFurnaceMvIron(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.ARC_FURNACE_MV);
        m.setItem(0, new ItemStack(SelfTest.item("plate_iron")));
        m.tank().setAmount(ru.iis.fluid.GtFluids.get("oxygen").source().get(), 1000);
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(1).is(SelfTest.item("ingot_wrought_iron")), "output " + m.getItem(1));
            h.assertTrue(m.tank().amount() == 1000 - 56, "oxygen left " + m.tank().amount());
        });
    }

    /** LV-дуговая печь: все рецепты 96 EU/t — на 32 В печь стоит, как в GT. */
    static void arcFurnaceLvIdle(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.ARC_FURNACE);
        m.setItem(0, new ItemStack(SelfTest.item("plate_iron")));
        m.tank().setAmount(ru.iis.fluid.GtFluids.get("oxygen").source().get(), 1000);
        h.runAtTickTime(60, () -> {
            h.assertTrue(m.logic().maxProgress == 0, "LV arc furnace started a 96 EU/t recipe");
            h.succeed();
        });
    }

    /** Стопка горячих слитков в инвентаре выживающего игрока обжигает на 3 единицы. */
    static void hotIngotBurns(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(SelfTest.item("hot_ingot_palladium")));
        float before = player.getHealth();
        // урон жаром зависит от сложности (как в GT): на мирной сервера тестов он нулевой
        var server = h.getLevel().getServer();
        var difficulty = server.getWorldData().getDifficulty();
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        h.assertTrue(player.getInventory().contains(HotIngotHeat.HOT_INGOTS), "hot ingot is not in iis:hot_ingots");
        try {
            HotIngotHeat.burn(player, h.getLevel());
        } finally {
            server.setDifficulty(difficulty, true);
        }
        h.assertTrue(player.getHealth() < before, "health " + before + " -> " + player.getHealth());
        h.succeed();
    }
}

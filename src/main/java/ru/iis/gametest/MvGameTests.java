package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import ru.iis.command.SelfTest;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.HotIngotHeat;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;

/** Эпоха MV (D81): термоцентрифуга LV/MV, вакуумный морозильник, горячие слитки. */
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
        BlockPos center = new BlockPos(4, 2, 3);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) h.setBlock(center.offset(dx, dy, dz), MultiContent.FROST_PROOF_CASING.get());
        h.setBlock(center, Blocks.AIR);
        BlockPos[] energy = {center.below(), center.above(), center.west(), center.east()};
        for (BlockPos p : energy) h.setBlock(p, MultiGameTests.hatch(HatchType.ENERGY, Direction.UP));
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

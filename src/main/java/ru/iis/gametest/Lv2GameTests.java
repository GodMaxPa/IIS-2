package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import ru.iis.command.SelfTest;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;

/** LV II: центрифуга, просеиватель, электромагнитный сепаратор, автоклав, пильный станок. */
final class Lv2GameTests {
    private Lv2GameTests() {}

    private static ElectricMachineBlockEntity powered(GameTestHelper h, LvMachineType t) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), t, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        return m;
    }

    /** Грязная пыль халькопирита → пыль + крошечная пыль пирита (побочный продукт). */
    static void centrifuge(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.CENTRIFUGE);
        m.setItem(0, new ItemStack(SelfTest.item("impure_dust_chalcopyrite")));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(2).is(SelfTest.item("dust_chalcopyrite")), "out0 " + m.getItem(2));
            h.assertTrue(m.getItem(3).is(SelfTest.item("tiny_dust_pyrite")), "out1 " + m.getItem(3));
        });
    }

    /** Очищенная руда рубина уходит в просеиватель (выходы случайны, проверяем расход входа). */
    static void sifter(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.SIFTER);
        m.setItem(0, new ItemStack(SelfTest.item("purified_ruby"), 4));
        h.succeedWhen(() -> h.assertTrue(m.getItem(0).getCount() <= 2, "sifter input left " + m.getItem(0)));
    }

    /** Очищенная пыль полосчатого железняка → пыль (+ шанс железа). */
    static void separator(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.ELECTROMAGNETIC_SEPARATOR);
        m.setItem(0, new ItemStack(SelfTest.item("pure_dust_banded_iron")));
        h.succeedWhen(() -> h.assertTrue(m.getItem(1).is(SelfTest.item("dust_banded_iron")), "out " + m.getItem(1)));
    }

    /** ИИС GT_IC2.zs: глиняная пыль + 250 л воды → комок глины. */
    static void autoclave(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.AUTOCLAVE);
        m.tank().setAmount(Fluids.WATER, 1000);
        m.setItem(0, new ItemStack(SelfTest.item("dust_clay")));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(1).is(Items.CLAY_BALL), "out " + m.getItem(1));
            h.assertTrue(m.tank().amount() == 750, "water " + m.tank().amount());
        });
    }

    /** Стальной стержень + вода → 4 стальных болта. */
    static void cutter(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.CUTTER);
        m.tank().setAmount(Fluids.WATER, 1000);
        m.setItem(0, new ItemStack(SelfTest.item("rod_steel")));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(1).is(SelfTest.item("bolt_steel")) && m.getItem(1).getCount() == 4, "out " + m.getItem(1));
            h.assertTrue(m.tank().amount() < 1000, "no water used");
        });
    }
}

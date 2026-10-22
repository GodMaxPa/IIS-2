package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ru.iis.command.SelfTest;
import ru.iis.fluid.CapsuleItem;
import ru.iis.fluid.FluidContainers;
import ru.iis.fluid.FluidContent;
import ru.iis.fluid.GtFluids;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;

/** Игровые тесты среза LV III «химия и расплавы» (D76): электролизёр, химреактор, экстрактор и отвердитель, нержавейка. */
final class ChemGameTests {
    private ChemGameTests() {}

    private static ElectricMachineBlockEntity powered(GameTestHelper h, LvMachineType t) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), t, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        return m;
    }

    private static Fluid gt(String id) {
        return GtFluids.get(id).source().get();
    }

    private static ItemStack out(ElectricMachineBlockEntity m, int i) {
        return m.getItem(m.type().inputs + i);
    }

    /** Пиролюзит ×3 → марганцевая пыль + 2 000 мБ кислорода (ProcessingDust, электролиз, 30 EU/t — LV). */
    static void electrolyzerPyrolusite(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.ELECTROLYZER);
        m.setItem(0, new ItemStack(SelfTest.item("dust_pyrolusite"), 3));
        h.succeedWhen(() -> {
            h.assertTrue(out(m, 0).is(SelfTest.item("dust_manganese")), "manganese: " + out(m, 0));
            h.assertTrue(m.outTank().amount() == 2000 && m.outTank().resource().getFluid() == gt("oxygen"), "oxygen " + m.outTank().amount());
        });
    }

    /** Вода 3 000 мБ + пустая капсула → 2 000 мБ водорода + капсула кислорода (GT_MachineRecipeLoader:1144). */
    static void electrolyzerWater(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.ELECTROLYZER);
        m.setItem(0, new ItemStack(FluidContainers.CAPSULE.get()));
        m.tank().setAmount(Fluids.WATER, 3000);
        h.succeedWhen(() -> {
            FluidContent c = CapsuleItem.content(out(m, 0));
            h.assertTrue(out(m, 0).is(FluidContainers.CAPSULE.get()) && c.fluidValue() == gt("oxygen") && c.amount() == 1000, "oxygen capsule " + out(m, 0));
            h.assertTrue(m.outTank().amount() == 2000 && m.outTank().resource().getFluid() == gt("hydrogen"), "hydrogen " + m.outTank().amount());
        });
    }

    /** Химреактор: 9 сырого каучука + сера → 1 296 мБ расплава резины (GT_MachineRecipeLoader:1181). */
    static void chemicalRubber(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.CHEMICAL_REACTOR);
        m.setItem(0, new ItemStack(SelfTest.item("dust_raw_rubber"), 9));
        m.setItem(1, new ItemStack(SelfTest.item("dust_sulfur")));
        h.succeedWhen(() -> h.assertTrue(m.outTank().amount() == 1296 && m.outTank().resource().getFluid() == gt("molten_rubber"),
                "molten rubber " + m.outTank().amount()));
    }

    /** Экстрактор: 2 оловянных слитка → 288 мБ расплава олова (31 EU/t; железо — 60 EU/t, это уже MV, как в GT). */
    static void fluidExtractorTin(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.FLUID_EXTRACTOR);
        m.setItem(0, new ItemStack(SelfTest.item("ingot_tin"), 2));
        h.succeedWhen(() -> h.assertTrue(m.outTank().amount() == 288 && m.outTank().resource().getFluid() == gt("molten_tin"),
                "molten tin " + m.outTank().amount()));
    }

    /** Отвердитель: форма слитка + 288 мБ расплава олова → 2 слитка; форма остаётся. */
    static void solidifierIngot(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.FLUID_SOLIDIFIER);
        m.setItem(0, new ItemStack(LvContent.MOLD_INGOT.get()));
        m.tank().setAmount(gt("molten_tin"), 288);
        h.succeedWhen(() -> {
            h.assertTrue(out(m, 0).is(SelfTest.item("ingot_tin")) && out(m, 0).getCount() == 2, "ingots " + out(m, 0));
            h.assertTrue(m.getItem(0).is(LvContent.MOLD_INGOT.get()), "the mold must stay");
        });
    }

    /** Миксер: 4 железа + 3 инвара + марганец + хром → 9 нержавеющей пыли (GT_MachineRecipeLoader:1770). */
    static void mixerStainless(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, LvMachineType.MIXER);
        m.setItem(0, new ItemStack(SelfTest.item("dust_iron"), 4));
        m.setItem(1, new ItemStack(SelfTest.item("dust_invar"), 3));
        m.setItem(2, new ItemStack(SelfTest.item("dust_manganese")));
        m.setItem(3, new ItemStack(SelfTest.item("dust_chrome")));
        h.succeedWhen(() -> h.assertTrue(out(m, 0).is(SelfTest.item("dust_stainless_steel")) && out(m, 0).getCount() == 9, "stainless " + out(m, 0)));
    }
}

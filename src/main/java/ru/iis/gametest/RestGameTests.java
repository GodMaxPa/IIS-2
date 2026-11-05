package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.iis.gear.GearContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.HatchType;
import ru.iis.multi.block.MaintenanceHatchBlockEntity;

/** Остатки GT (D119). */
final class RestGameTests {
    private RestGameTests() {}

    private static final BlockPos POS = new BlockPos(4, 2, 4);

    private static ElectricMachineBlockEntity machine(GameTestHelper h, LvMachineType t) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, POS, t, Direction.NORTH);
        m.energy.stored = m.energy.capacity;
        return m;
    }

    private static void fill(GameTestHelper h, ElectricMachineBlockEntity m, String fluid, int amount) {
        var f = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse(fluid));
        m.tank().setAmount(f, amount);
    }

    /** Плазма в генераторе даёт энергию с КПД 50 %: гелий — 2048 EU на литр. */
    static void plasmaGenerator(GameTestHelper h) {
        h.setBlock(POS, ru.iis.lv.LvContent.GENERATORS.get(ru.iis.lv.GeneratorType.PLASMA_EV).get());
        ru.iis.lv.block.GeneratorBlockEntity g = h.getBlockEntity(POS, ru.iis.lv.block.GeneratorBlockEntity.class);
        var f = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse("iis:plasma_helium"));
        g.tank().setAmount(f, 100);
        h.assertTrue(ru.iis.lv.GeneratorType.PLASMA_EV.efficiency() == 50, "efficiency");
        h.succeedWhen(() -> h.assertTrue(g.energy.stored > 1000, "stored " + g.energy.stored));
    }

    /** Болт обогащённой наквады сгорает в энергию и выходит болтом наквады. */
    static void naquadahReactorBolt(GameTestHelper h) {
        h.setBlock(POS, ru.iis.lv.LvContent.GENERATORS.get(ru.iis.lv.GeneratorType.NAQUADAH_EV).get());
        ru.iis.lv.block.GeneratorBlockEntity g = h.getBlockEntity(POS, ru.iis.lv.block.GeneratorBlockEntity.class);
        var fuel = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("iis:bolt_naquadah_enriched"));
        g.setItem(0, new ItemStack(fuel));
        h.succeedWhen(() -> {
            h.assertTrue(g.energy.stored == 20000 || g.energy.stored > 0 && g.getItem(0).isEmpty(), "stored " + g.energy.stored);
            h.assertTrue(!g.getItem(1).isEmpty(), "no spent bolt");
        });
    }

    /** Ферментёр: биомасса в сброженную биомассу, без предметов. */
    static void fermenterBiomass(GameTestHelper h) {
        ElectricMachineBlockEntity m = machine(h, LvMachineType.FERMENTER);
        fill(h, m, "iis:biomass", 500);
        h.succeedWhen(() -> h.assertTrue(m.outTank().amount() >= 100, "out " + m.outTank().amount()));
    }

    /** Принтер красит шерсть химическим красителем. */
    static void printerPaintsWool(GameTestHelper h) {
        ElectricMachineBlockEntity m = machine(h, LvMachineType.PRINTER);
        fill(h, m, "iis:dye_chemical_dyered", 1000);
        m.setItem(0, new ItemStack(Items.WHITE_WOOL));
        h.succeedWhen(() -> h.assertTrue(m.getItem(1).is(Items.RED_WOOL), "out " + m.getItem(1)));
    }

    /** Утилизатор ест любой предмет и выпускает только утиль. */
    static void recyclerTakesAnything(GameTestHelper h) {
        ElectricMachineBlockEntity m = machine(h, LvMachineType.RECYCLER);
        m.setItem(0, new ItemStack(Items.DIRT, 64));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(0).getCount() < 64, "not eaten");
            h.assertTrue(m.getItem(1).isEmpty() || m.getItem(1).is(GearContent.SCRAP.get()), "out " + m.getItem(1));
        });
    }

    /** Амплифабрикатор: 9 утиля в 1 мБ UU-усилителя. */
    static void amplifabFromScrap(GameTestHelper h) {
        ElectricMachineBlockEntity m = machine(h, LvMachineType.AMPLIFAB);
        m.setItem(0, new ItemStack(GearContent.SCRAP.get(), 9));
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        h.succeedWhen(() -> h.assertTrue(m.outTank().amount() >= 1, "out " + m.outTank().amount()));
    }

    /** Автоматический люк чинит неисправность без материалов. */
    static void autoHatchRepairs(GameTestHelper h) {
        h.setBlock(POS, MultiContent.HATCHES.get(HatchType.MAINTENANCE_AUTO).get());
        MaintenanceHatchBlockEntity m = h.getBlockEntity(POS, MaintenanceHatchBlockEntity.class);
        m.machineMask = 0;
        h.assertTrue(m.takeFixes() == 63, "not fixed");
        m.machineMask = 63;
        h.assertTrue(m.takeFixes() == 0, "fixed a working machine");
        h.succeed();
    }

    /** Люк на наборах тратит набор и без него не чинит. */
    static void semiHatchUsesKits(GameTestHelper h) {
        h.setBlock(POS, MultiContent.HATCHES.get(HatchType.MAINTENANCE_SEMIAUTO).get());
        MaintenanceHatchBlockEntity m = h.getBlockEntity(POS, MaintenanceHatchBlockEntity.class);
        m.machineMask = 0;
        h.assertTrue(m.takeFixes() == 0, "fixed without kits");
        m.addKits(1);
        h.assertTrue(m.takeFixes() == 63, "not fixed with a kit");
        h.assertTrue(m.kits() == 0, "kit left");
        h.succeed();
    }
}

package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.LvContent;
import ru.iis.lv.item.ProgrammedCircuitItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;
import ru.iis.multi.logic.LargeBoilerLogic;
import ru.iis.multi.logic.LargeTurbineLogic;

/** Большие котлы (D88) и большая паровая турбина (D89). */
final class LargeGameTests {
    private LargeGameTests() {}

    static final BlockPos CTRL = new BlockPos(4, 1, 2), BUS = new BlockPos(3, 1, 2), WATER = new BlockPos(5, 1, 2),
            FUEL_HATCH = new BlockPos(4, 1, 4), STEAM = new BlockPos(4, 2, 2), PIPE = new BlockPos(4, 3, 3);

    /**
     * Котёл 3×5×3: контроллер (4,1,2) лицом на север, середина (4,·,3). Низ: шина топлива, люк воды (каждый тик доливается),
     * люк жидкого топлива (4,1,4), обслуживание, глушитель лицом на восток, 3 топки. Над контроллером — выходной люк пара.
     */
    static LargeBoilerBlockEntity build(GameTestHelper h, LargeBoilerLogic.Tier tier) {
        LargeBoilerBlockEntity.Casings c = MultiContent.boilerCasings(tier);
        BlockPos center = CTRL.south();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos col = center.offset(dx, 0, dz);
                h.setBlock(col, c.firebox());
                for (int k = 1; k <= 4; k++) h.setBlock(col.above(k), dx == 0 && dz == 0 && k < 4 ? c.pipe() : c.casing());
            }
        }
        h.setBlock(BUS, MultiGameTests.hatch(HatchType.INPUT_BUS, Direction.NORTH));
        h.setBlock(WATER, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.NORTH));
        h.setBlock(FUEL_HATCH, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.SOUTH));
        h.setBlock(new BlockPos(3, 1, 4), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.SOUTH));
        h.setBlock(new BlockPos(5, 1, 4), MultiGameTests.hatch(HatchType.MUFFLER, Direction.EAST));
        h.setBlock(STEAM, MultiGameTests.hatch(HatchType.OUTPUT_HATCH, Direction.NORTH));
        h.setBlock(CTRL, MultiContent.LARGE_BOILERS.get(tier).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        LargeBoilerBlockEntity b = h.getBlockEntity(CTRL, LargeBoilerBlockEntity.class);
        b.maintenance().setMask(63);
        FluidHatchBlockEntity water = h.getBlockEntity(WATER, FluidHatchBlockEntity.class);
        h.onEachTick(() -> water.tank().setAmount(Fluids.WATER, HatchType.TANK));
        return b;
    }

    private static int steam(GameTestHelper h) {
        FluidHatchBlockEntity out = h.getBlockEntity(STEAM, FluidHatchBlockEntity.class);
        return out.tank().amount() > 0 && out.tank().resource().getFluid().isSame(IISFluids.STEAM.get()) ? out.tank().amount() : 0;
    }

    /** Уголь в шине → котёл разгорается (КПД растёт порциями), в выходной люк идёт пар, уголь убывает. */
    static void bronzeBoilerCoal(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.BRONZE);
        ItemBusBlockEntity bus = h.getBlockEntity(BUS, ItemBusBlockEntity.class);
        bus.setItem(0, new ItemStack(Items.COAL, 64));
        h.succeedWhen(() -> {
            h.assertTrue(b.formed(), "boiler not formed");
            h.assertTrue(h.getBlockState(CTRL).getValue(MachineBlock.ACTIVE), "boiler not burning");
            h.assertTrue(bus.getItem(0).getCount() < 64, "coal not used");
            h.assertTrue(b.efficiency() > 0, "efficiency did not grow");
            h.assertTrue(steam(h) > 0, "no steam in the output hatch");
        });
    }

    /** Полужидкое топливо: 8000 л креозота из люка → 128 тиков горения (8 × 16, D88 dense). */
    static void bronzeBoilerCreosote(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.BRONZE);
        FluidHatchBlockEntity fuel = h.getBlockEntity(FUEL_HATCH, FluidHatchBlockEntity.class);
        fuel.tank().setAmount(IISFluids.CREOSOTE.get(), 9000);
        h.succeedWhen(() -> {
            h.assertTrue(b.formed(), "boiler not formed");
            h.assertTrue(fuel.tank().amount() == 1000, "creosote left " + fuel.tank().amount());
            h.assertTrue(b.logic().progressTimeStash > 0 || b.maxProgress() > 0, "creosote did not burn");
        });
    }

    /** Схема 8 в контроллере горячего стального котла: 600 − 8 × 25 = 400 EU/t (800 л/т пара вместо 1200). */
    static void boilerCircuitThrottle(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.STEEL);
        ItemBusBlockEntity bus = h.getBlockEntity(BUS, ItemBusBlockEntity.class);
        bus.setItem(0, new ItemStack(Items.COAL, 64));
        b.setItem(0, ProgrammedCircuitItem.withConfig(new ItemStack(LvContent.PROGRAMMED_CIRCUIT.get()), 8));
        h.onEachTick(() -> {
            if (b.efficiency() < 10000 && b.maxProgress() > 0) setEfficiency(b, 10000);
        });
        h.succeedWhen(() -> {
            h.assertTrue(b.logic().circuitConfig == 8, "circuit not read: " + b.logic().circuitConfig);
            h.assertTrue(b.logic().eut == 400, "steel boiler at circuit 8 must give 400 EU/t, got " + b.logic().eut);
        });
    }

    private static void setEfficiency(MultiblockControllerBlockEntity b, int e) {
        try {
            var f = MultiblockControllerBlockEntity.class.getDeclaredField("efficiency");
            f.setAccessible(true);
            f.setInt(b, e);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Без трубчатого корпуса в середине, с двумя топками или с чужим блоком в стенке форма не собирается. */
    static void boilerStructureRules(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.BRONZE);
        BlockPos firebox = new BlockPos(3, 1, 3);
        h.runAtTickTime(25, () -> {
            h.assertTrue(b.formed(), "boiler not formed");
            h.setBlock(PIPE, MultiContent.BRONZE_PLATED_BRICKS.get());
        });
        h.runAtTickTime(50, () -> {
            h.assertFalse(b.formed(), "formed without a pipe casing");
            h.setBlock(PIPE, MultiContent.BRONZE_PIPE_CASING.get());
            h.setBlock(firebox, MultiGameTests.hatch(HatchType.INPUT_BUS, Direction.NORTH)); // топок осталось 2
        });
        h.runAtTickTime(75, () -> {
            h.assertFalse(b.formed(), "formed with two fireboxes");
            h.setBlock(firebox, MultiContent.BRONZE_FIREBOX_CASING.get());
        });
        h.runAtTickTime(100, () -> {
            h.assertTrue(b.formed(), "three fireboxes must be enough");
            h.setBlock(STEAM, Blocks.STONE);
        });
        h.runAtTickTime(125, () -> {
            h.assertFalse(b.formed(), "formed with stone in the casing");
            h.succeed();
        });
    }

    // --- турбина ----------------------------------------------------------------------------------------------------
    static final BlockPos T_CTRL = new BlockPos(4, 2, 1), T_STEAM = new BlockPos(4, 3, 2), T_WATER = new BlockPos(3, 2, 2),
            T_DYNAMO = new BlockPos(4, 2, 4);

    /**
     * Турбина 3×3×4: контроллер (4,2,1) лицом на север (перед ним 3×3 воздуха), тело — z = 1…4. Вход пара сверху, выход воды
     * слева, обслуживание снизу (второй слой), динамо-люк dynamo в середине задней стороны. Пар доливается каждый тик.
     */
    static LargeTurbineBlockEntity turbine(GameTestHelper h, HatchType dynamo) {
        for (int x = 3; x <= 5; x++)
            for (int y = 1; y <= 3; y++)
                for (int z = 1; z <= 4; z++) h.setBlock(new BlockPos(x, y, z), MultiContent.TURBINE_CASING.get());
        h.setBlock(new BlockPos(4, 2, 2), Blocks.AIR);
        h.setBlock(new BlockPos(4, 2, 3), Blocks.AIR);
        h.setBlock(T_STEAM, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.UP));
        h.setBlock(T_WATER, MultiGameTests.hatch(HatchType.OUTPUT_HATCH, Direction.WEST));
        h.setBlock(new BlockPos(4, 1, 3), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.DOWN));
        h.setBlock(T_DYNAMO, MultiGameTests.hatch(dynamo, Direction.SOUTH));
        h.setBlock(T_CTRL, MultiContent.LARGE_STEAM_TURBINE.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        LargeTurbineBlockEntity t = h.getBlockEntity(T_CTRL, LargeTurbineBlockEntity.class);
        t.maintenance().setMask(63);
        t.setItem(0, MultiContent.ROTORS.get(LargeTurbineLogic.Size.SMALL).get().create("steel"));
        FluidHatchBlockEntity steam = h.getBlockEntity(T_STEAM, FluidHatchBlockEntity.class);
        h.onEachTick(() -> steam.tank().setAmount(IISFluids.STEAM.get(), HatchType.TANK));
        return t;
    }

    /** Стальной малый ротор, люк MV: выход растёт к 105 EU/t, энергия идёт в динамо-люк, из пара — дистиллированная вода. */
    static void turbineMakesPower(GameTestHelper h) {
        LargeTurbineBlockEntity t = turbine(h, HatchType.DYNAMO_MV);
        DynamoHatchBlockEntity dyn = h.getBlockEntity(T_DYNAMO, DynamoHatchBlockEntity.class);
        FluidHatchBlockEntity water = h.getBlockEntity(T_WATER, FluidHatchBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(t.formed(), "turbine not formed");
            h.assertTrue(t.eut() == 105, "turbine EU/t " + t.eut());
            h.assertTrue(dyn.energy.stored > DynamoHatchBlockEntity.MIN_STORED, "dynamo got " + dyn.energy.stored);
            h.assertTrue(water.tank().amount() > 0, "no distilled water");
        });
    }

    /** Без воздуха перед лицом, без динамо-люка или без ротора турбина не работает. */
    static void turbineStructureRules(GameTestHelper h) {
        LargeTurbineLogic.Size small = LargeTurbineLogic.Size.SMALL;
        LargeTurbineBlockEntity t = turbine(h, HatchType.DYNAMO_MV);
        BlockPos front = new BlockPos(3, 3, 0);
        h.runAtTickTime(25, () -> {
            h.assertTrue(t.formed(), "turbine not formed");
            h.setBlock(front, Blocks.STONE);
        });
        h.runAtTickTime(50, () -> {
            h.assertFalse(t.formed(), "formed with a block in front of the rotor");
            h.setBlock(front, Blocks.AIR);
            h.setBlock(T_DYNAMO, MultiContent.TURBINE_CASING.get());
        });
        h.runAtTickTime(75, () -> {
            h.assertFalse(t.formed(), "formed without a dynamo hatch");
            h.setBlock(T_DYNAMO, MultiGameTests.hatch(HatchType.DYNAMO, Direction.SOUTH));
            t.setItem(0, ItemStack.EMPTY);
        });
        h.runAtTickTime(200, () -> {
            h.assertTrue(t.formed(), "turbine with an LV dynamo not formed");
            h.assertTrue(t.maxProgress() == 0 && t.eut() == 0, "turbine runs without a rotor");
            h.assertTrue(MultiContent.ROTORS.get(small).get().create("steel").getMaxDamage() == 51200, "steel small rotor durability");
            h.succeed();
        });
    }

    // --- детали турбины (D90) ---------------------------------------------------------------------------------------
    /** Сборщик MV: 4 стальные лопасти + длинный стержень магналия → стальной малый ротор (100 EU/t, прочность 51200). */
    static void assemblerMakesSmallRotor(GameTestHelper h) {
        ru.iis.lv.block.ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), ru.iis.lv.LvMachineType.ASSEMBLER_MV, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("turbine_blade_steel"), 4));
        m.setItem(1, new ItemStack(ru.iis.command.SelfTest.item("long_rod_magnalium")));
        h.succeedWhen(() -> {
            ItemStack o = m.getItem(m.type().inputs);
            h.assertTrue(o.getItem() instanceof ru.iis.multi.TurbineRotorItem r && r.size == LargeTurbineLogic.Size.SMALL, "rotor: " + o);
            h.assertTrue("steel".equals(o.get(MultiContent.ROTOR_MATERIAL.get())), "rotor material " + o.get(MultiContent.ROTOR_MATERIAL.get()));
            h.assertTrue(o.getMaxDamage() == 51200, "rotor durability " + o.getMaxDamage());
        });
    }

    /** Гравёр MV: кремниевая пластина + рубиновая линза (красная) → пластина логических микросхем; линза остаётся (D91). */
    static void engraverMakesIlcWafer(GameTestHelper h) {
        ru.iis.lv.block.ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), ru.iis.lv.LvMachineType.LASER_ENGRAVER_MV, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("silicon_wafer")));
        m.setItem(1, new ItemStack(ru.iis.command.SelfTest.item("lens_ruby")));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(2).is(ru.iis.command.SelfTest.item("wafer_ilc")), "output " + m.getItem(2));
            h.assertTrue(m.getItem(1).is(ru.iis.command.SelfTest.item("lens_ruby")), "lens consumed: " + m.getItem(1));
        });
    }

    private static net.minecraft.world.level.material.Fluid gt(String id) {
        return ru.iis.fluid.GtFluids.get(id).source().get();
    }

    private static ru.iis.lv.block.ElectricMachineBlockEntity poweredMachine(GameTestHelper h, ru.iis.lv.LvMachineType t) {
        ru.iis.lv.block.ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), t, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        return m;
    }

    /** Пивоварня LV (D92): биоотходы + 750 л воды → 750 л биомассы (GT_MachineRecipeLoader:767, 128 тиков). */
    static void breweryMakesBiomass(GameTestHelper h) {
        var m = poweredMachine(h, ru.iis.lv.LvMachineType.BREWERY);
        m.setItem(0, new ItemStack(LvContent.BIO_CHAFF.get()));
        m.tank().setAmount(Fluids.WATER, 750);
        h.succeedWhen(() -> {
            h.assertTrue(m.outTank().amount() == 750 && m.outTank().resource().getFluid() == gt("biomass"), "biomass " + m.outTank().amount());
            h.assertTrue(m.getItem(0).isEmpty(), "bio chaff left: " + m.getItem(0));
        });
    }

    /** Химреактор LV: капсула кислорода + схема 1 + 144 л этилена → 216 л расплава пластика и пустая капсула (GT_RecipeAdder:274). */
    static void reactorMakesPlastic(GameTestHelper h) {
        var m = poweredMachine(h, ru.iis.lv.LvMachineType.CHEMICAL_REACTOR);
        m.setItem(0, ru.iis.fluid.CapsuleItem.withContent(new ItemStack(ru.iis.fluid.FluidContainers.CAPSULE.get()),
                ru.iis.fluid.FluidContent.of(gt("oxygen"), 1000)));
        m.setItem(1, ProgrammedCircuitItem.withConfig(new ItemStack(LvContent.PROGRAMMED_CIRCUIT.get()), 1));
        m.tank().setAmount(gt("ethylene"), 144);
        h.succeedWhen(() -> {
            h.assertTrue(m.outTank().amount() == 216 && m.outTank().resource().getFluid() == gt("molten_plastic"), "plastic " + m.outTank().amount());
            ItemStack o = m.getItem(m.type().inputs);
            h.assertTrue(o.is(ru.iis.fluid.FluidContainers.CAPSULE.get()) && ru.iis.fluid.CapsuleItem.content(o).isEmpty(), "capsule " + o);
        });
    }

    /** Сборщик схем LV: 2 хорошие интегральные схемы, 3 логические микросхемы, ОЗУ, 4 транзистора, 16 тонких проводов электрума
     *  + 72 л паяльного сплава → схема HV (GT_MachineRecipeLoader:563, 800 тиков, 28 EU/t). */
    static void circuitAssemblerMakesAdvanced(GameTestHelper h) {
        var m = poweredMachine(h, ru.iis.lv.LvMachineType.CIRCUIT_ASSEMBLER);
        m.setItem(0, new ItemStack(LvContent.CIRCUIT_INTEGRATED_GOOD.get(), 2));
        m.setItem(1, new ItemStack(LvContent.CHIP_ILC.get(), 3));
        m.setItem(2, new ItemStack(LvContent.CHIP_RAM.get()));
        m.setItem(3, new ItemStack(LvContent.TRANSISTOR.get(), 4));
        m.setItem(4, new ItemStack(ru.iis.command.SelfTest.item("wire_fine_electrum"), 16));
        m.tank().setAmount(gt("molten_solderingalloy"), 72);
        h.succeedWhen(() -> h.assertTrue(m.getItem(m.type().inputs).is(LvContent.CIRCUIT_ADVANCED.get()), "output " + m.getItem(m.type().inputs)));
    }

    /** Полная капсула в верстаке оставляет пустую (клей в текстолитовой плате, GT_MachineRecipeLoader:442); пустая — ничего. */
    static void filledCapsuleLeavesEmpty(GameTestHelper h) {
        ItemStack glue = ru.iis.fluid.CapsuleItem.withContent(new ItemStack(ru.iis.fluid.FluidContainers.CAPSULE.get()),
                ru.iis.fluid.FluidContent.of(gt("glue"), 1000));
        var rem = glue.getItem().getCraftingRemainder(glue);
        h.assertTrue(rem != null, "no remainder");
        ItemStack r = rem.create();
        h.assertTrue(r.is(ru.iis.fluid.FluidContainers.CAPSULE.get()) && ru.iis.fluid.CapsuleItem.content(r).isEmpty(), "remainder " + r);
        ItemStack empty = new ItemStack(ru.iis.fluid.FluidContainers.CAPSULE.get());
        h.assertTrue(empty.getItem().getCraftingRemainder(empty) == null, "empty capsule must leave nothing");
        h.succeed();
    }

    /** ЭДП с 16 катушками из кантала: нагрев 2700 K (GT mHeatingCapacity, D93); одна чужая катушка — форма не собрана. */
    static void ebfKanthalCoils(GameTestHelper h) {
        EbfBlockEntity e = MultiGameTests.build(h, 1);
        BlockPos center = new BlockPos(4, 1, 2);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                h.setBlock(center.offset(dx, 1, dz), MultiContent.COIL_KANTHAL.get());
                h.setBlock(center.offset(dx, 2, dz), MultiContent.COIL_KANTHAL.get());
            }
        h.assertTrue(EbfBlockEntity.coilHeat(MultiContent.COIL_CUPRONICKEL.get()) == 1800, "cupronickel heat");
        h.succeedWhen(() -> h.assertTrue(e.heat() == EbfBlockEntity.KANTHAL_HEAT, "heat " + e.heat()));
    }

    /** Измельчитель HV: второй выход GT (побочный продукт) — 5 блоков дыни дают ломтики и хотя бы раз семена (80 %). */
    static void hvMaceratorByproduct(GameTestHelper h) {
        var m = poweredMachine(h, ru.iis.lv.LvMachineType.MACERATOR_HV);
        m.setItem(0, new ItemStack(Items.MELON, 5));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(0).isEmpty(), "melons left " + m.getItem(0));
            h.assertTrue(m.getItem(1).is(Items.MELON_SLICE) && m.getItem(1).getCount() == 40, "slices " + m.getItem(1));
            h.assertTrue(m.getItem(2).is(Items.MELON_SEEDS), "seeds " + m.getItem(2));
        });
    }

    /** Паровая турбина HV (D94) → золотой кабель → измельчитель HV: 512 В, 1 А; руда халькопирита → 2 дроблёной. */
    static void hvSteamTurbinePowersMacerator(GameTestHelper h) {
        var gen = LvGameTests.generator(h, new BlockPos(1, 1, 4), ru.iis.lv.GeneratorType.STEAM_TURBINE_HV, Direction.EAST);
        h.assertTrue(gen.tank().capacity() == 72000 && gen.outputVoltage() == 512, "tank " + gen.tank().capacity() + " V " + gen.outputVoltage());
        gen.tank().setAmount(IISFluids.STEAM.get(), 72000);
        var m = LvGameTests.machine(h, new BlockPos(4, 1, 4), ru.iis.lv.LvMachineType.MACERATOR_HV, Direction.NORTH);
        for (int x = 2; x <= 3; x++) LvGameTests.cable(h, new BlockPos(x, 1, 4), "cable_gold_1x");
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("ore_chalcopyrite")));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(1).is(ru.iis.command.SelfTest.item("crushed_chalcopyrite")), "output " + m.getItem(1));
            h.assertTrue(gen.tank().amount() < 72000, "turbine used no steam");
        });
    }

    /** Трансформатор HV (D94): понижает 2048 → 512 В, 4 А с пяти сторон; запас 512 + 2 × 2048. */
    static void hvTransformerStepDown(GameTestHelper h) {
        BlockPos tp = new BlockPos(2, 1, 4);
        h.setBlock(tp, LvContent.TRANSFORMER_HV.get().defaultBlockState().setValue(ru.iis.lv.block.TransformerBlock.FACING, Direction.WEST));
        var t = h.getBlockEntity(tp, ru.iis.lv.block.TransformerBlockEntity.class);
        h.assertTrue(t.outputVoltage() == 512 && t.outputAmperes() == 4, "step-down out " + t.outputVoltage() + " V " + t.outputAmperes() + " A");
        h.assertTrue(t.energy.capacity == 512 + 2 * 2048, "capacity " + t.energy.capacity);
        var m = LvGameTests.machine(h, new BlockPos(4, 1, 4), ru.iis.lv.LvMachineType.MACERATOR_HV, Direction.NORTH);
        LvGameTests.cable(h, new BlockPos(3, 1, 4), "cable_gold_4x");
        h.onEachTick(() -> t.energy.stored = t.energy.capacity);
        h.succeedWhen(() -> h.assertTrue(m.energy.stored >= m.energy.capacity / 2 && !h.getBlockState(tp).isAir(), "HV machine stored " + m.energy.stored));
    }

    /** Буфер HV 4×4 (D94): 16 слотов, запас 512 × 64 × 16; заряжает батарейку LV (уровень не выше буфера), питает машину HV
     *  512 В; в интерфейс запас идёт двумя половинами по 16 бит. */
    static void hvBatteryBuffer4x4(GameTestHelper h) {
        BlockPos bp = new BlockPos(2, 1, 4);
        h.setBlock(bp, LvContent.BATTERY_BUFFERS_HV.get(3).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        var b = h.getBlockEntity(bp, ru.iis.lv.block.BatteryBufferBlockEntity.class);
        h.assertTrue(b.slots() == 16 && b.energy.capacity == 512L * 64 * 16 && b.outputVoltage() == 512, "buffer " + b.slots() + " " + b.energy.capacity);
        var m = LvGameTests.machine(h, new BlockPos(4, 1, 4), ru.iis.lv.LvMachineType.MACERATOR_HV, Direction.NORTH);
        LvGameTests.cable(h, new BlockPos(3, 1, 4), "cable_gold_1x");
        b.setItem(0, new ItemStack(LvContent.BATTERY_LV.get()));
        h.onEachTick(() -> b.energy.stored = b.energy.capacity);
        h.succeedWhen(() -> {
            h.assertTrue(ru.iis.lv.item.BatteryItem.charge(b.getItem(0)) > 0, "battery not charged");
            h.assertTrue(m.energy.stored > 0, "machine got no energy");
            long shown = (b.data.get(0) & 0xFFFF) | ((long) (b.data.get(1) & 0xFFFF) << 16);
            h.assertTrue(shown == b.energy.stored, "gui energy " + shown + " vs " + b.energy.stored);
        });
    }

    /** Паровая турбина MV (D95) → медный кабель → измельчитель MV: 128 В, бак 48 000 л. */
    static void mvSteamTurbinePowersMacerator(GameTestHelper h) {
        var gen = LvGameTests.generator(h, new BlockPos(1, 1, 4), ru.iis.lv.GeneratorType.STEAM_TURBINE_MV, Direction.EAST);
        h.assertTrue(gen.tank().capacity() == 48000 && gen.outputVoltage() == 128, "tank " + gen.tank().capacity() + " V " + gen.outputVoltage());
        gen.tank().setAmount(IISFluids.STEAM.get(), 48000);
        var m = LvGameTests.machine(h, new BlockPos(4, 1, 4), ru.iis.lv.LvMachineType.MACERATOR_MV, Direction.NORTH);
        for (int x = 2; x <= 3; x++) LvGameTests.cable(h, new BlockPos(x, 1, 4), "cable_copper_1x");
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("ore_chalcopyrite")));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(1).is(ru.iis.command.SelfTest.item("crushed_chalcopyrite")), "output " + m.getItem(1));
            h.assertTrue(gen.tank().amount() < 48000, "turbine used no steam");
        });
    }

    /** Буфер LV 4×4 (D95): 16 слотов, запас 32 × 64 × 16 = 32 768 (больше short — проверка передачи в интерфейс), 32 В. */
    static void lvBatteryBuffer4x4(GameTestHelper h) {
        BlockPos bp = new BlockPos(2, 1, 4);
        h.setBlock(bp, LvContent.BATTERY_BUFFER_4X4.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        var b = h.getBlockEntity(bp, ru.iis.lv.block.BatteryBufferBlockEntity.class);
        h.assertTrue(b.slots() == 16 && b.energy.capacity == 32L * 64 * 16 && b.outputVoltage() == 32, "buffer " + b.slots() + " " + b.energy.capacity);
        var m = LvGameTests.machine(h, new BlockPos(4, 1, 4), ru.iis.lv.LvMachineType.MACERATOR, Direction.NORTH);
        LvGameTests.cable(h, new BlockPos(3, 1, 4), "cable_tin_1x");
        b.setItem(15, new ItemStack(LvContent.BATTERY_LV.get()));
        h.onEachTick(() -> b.energy.stored = b.energy.capacity);
        h.succeedWhen(() -> {
            h.assertTrue(ru.iis.lv.item.BatteryItem.charge(b.getItem(15)) > 0, "battery not charged");
            h.assertTrue(m.energy.stored > 0, "machine got no energy");
            long cap = (b.data.get(2) & 0xFFFF) | ((long) (b.data.get(3) & 0xFFFF) << 16);
            h.assertTrue(cap == 32768, "gui capacity " + cap);
        });
    }

    /** Батарейки MV/HV (D96): буфер HV заряжает литиевую HV по 512 EU/t (ёмкость 1 600 000); буфер MV не берёт HV-батарейку
     *  (уровень выше), но заряжает натриевую MV по 128 EU/t. */
    static void batteryChargingTiers(GameTestHelper h) {
        BlockPos hp = new BlockPos(2, 1, 2), mp = new BlockPos(5, 1, 2);
        h.setBlock(hp, LvContent.BATTERY_BUFFERS_HV.get(0).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        h.setBlock(mp, LvContent.BATTERY_BUFFERS_MV.get(1).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        var hb = h.getBlockEntity(hp, ru.iis.lv.block.BatteryBufferBlockEntity.class);
        var mb = h.getBlockEntity(mp, ru.iis.lv.block.BatteryBufferBlockEntity.class);
        var lithiumHv = LvContent.BATTERY_LITHIUM_HV.get();
        h.assertTrue(lithiumHv.capacity == 1_600_000 && lithiumHv.transferLimit == 512 && lithiumHv.tier == 3, "lithium HV stats");
        hb.setItem(0, new ItemStack(lithiumHv));
        mb.setItem(0, new ItemStack(LvContent.BATTERY_SODIUM_HV.get()));
        mb.setItem(1, new ItemStack(LvContent.BATTERY_SODIUM_MV.get()));
        h.onEachTick(() -> {
            hb.energy.stored = hb.energy.capacity;
            mb.energy.stored = mb.energy.capacity;
        });
        h.runAfterDelay(40, () -> {
            long hv = ru.iis.lv.item.BatteryItem.charge(hb.getItem(0));
            long mvHv = ru.iis.lv.item.BatteryItem.charge(mb.getItem(0));
            long mv = ru.iis.lv.item.BatteryItem.charge(mb.getItem(1));
            h.assertTrue(hv > 0 && hv % 512 == 0, "HV battery charge " + hv);
            h.assertTrue(mvHv == 0, "MV buffer charged an HV battery: " + mvHv);
            h.assertTrue(mv > 0 && mv % 128 == 0 && mv <= 128L * 41, "MV battery charge " + mv);
            h.succeed();
        });
    }

    /** Консервирующий станок LV: 8 литиевой пыли + средний корпус → средняя литиевая батарея (400 тиков, 2 EU/t). */
    static void mvBatteryCanning(GameTestHelper h) {
        var m = poweredMachine(h, ru.iis.lv.LvMachineType.CANNER);
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("dust_lithium"), 8));
        m.setItem(1, new ItemStack(LvContent.BATTERY_HULL_MV.get()));
        h.succeedWhen(() -> h.assertTrue(m.getItem(2).is(LvContent.BATTERY_LITHIUM_MV.get()), "output " + m.getItem(2)));
    }

    /** Большой титановый котёл (D97): формируется из титановых корпусов, жжёт уголь и даёт пар. */
    static void titaniumBoilerCoal(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.TITANIUM);
        ItemBusBlockEntity bus = h.getBlockEntity(BUS, ItemBusBlockEntity.class);
        bus.setItem(0, new ItemStack(Items.COAL, 64));
        h.succeedWhen(() -> {
            h.assertTrue(b.formed(), "boiler not formed");
            h.assertTrue(bus.getItem(0).getCount() < 64, "coal not used");
            h.assertTrue(steam(h) > 0, "no steam in the output hatch");
        });
    }

    /** Сборщик HV (D97): 8 стальных лопастей + длинный титановый стержень → обычный ротор, прочность 100 × 2 × прочность стали. */
    static void assemblerMakesNormalRotor(GameTestHelper h) {
        var m = poweredMachine(h, ru.iis.lv.LvMachineType.ASSEMBLER_HV);
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("turbine_blade_steel"), 8));
        m.setItem(1, new ItemStack(ru.iis.command.SelfTest.item("long_rod_titanium")));
        h.succeedWhen(() -> {
            ItemStack o = m.getItem(m.type().inputs);
            h.assertTrue(o.getItem() instanceof ru.iis.multi.TurbineRotorItem r && r.size == LargeTurbineLogic.Size.NORMAL, "rotor: " + o);
            h.assertTrue("steel".equals(o.get(MultiContent.ROTOR_MATERIAL.get())), "rotor material");
            h.assertTrue(o.getMaxDamage() == 102400, "rotor durability " + o.getMaxDamage());
        });
    }

    /** ЭДП с нихромовыми катушками (D98): нагрев 3600 K — хватает для вольфрамостали (3000 K). */
    static void ebfNichromeCoils(GameTestHelper h) {
        EbfBlockEntity e = MultiGameTests.build(h, 1);
        BlockPos center = new BlockPos(4, 1, 2);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                h.setBlock(center.offset(dx, 1, dz), MultiContent.COIL_NICHROME.get());
                h.setBlock(center.offset(dx, 2, dz), MultiContent.COIL_NICHROME.get());
            }
        h.succeedWhen(() -> h.assertTrue(e.heat() == EbfBlockEntity.NICHROME_HEAT, "heat " + e.heat()));
    }

    /** Большой вольфрамостальной котёл (D98): формируется из вольфрамостальных корпусов, жжёт уголь и даёт пар. */
    static void tungstensteelBoilerCoal(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.TUNGSTENSTEEL);
        ItemBusBlockEntity bus = h.getBlockEntity(BUS, ItemBusBlockEntity.class);
        bus.setItem(0, new ItemStack(Items.COAL, 64));
        h.succeedWhen(() -> {
            h.assertTrue(b.formed(), "boiler not formed");
            h.assertTrue(bus.getItem(0).getCount() < 64, "coal not used");
            h.assertTrue(steam(h) > 0, "no steam in the output hatch");
        });
    }

    /** Сборщик EV (D98): 12 стальных лопастей + длинный вольфрамостальной стержень → большой ротор (1600 EU/t), прочность ×3. */
    static void assemblerMakesLargeRotor(GameTestHelper h) {
        var m = poweredMachine(h, ru.iis.lv.LvMachineType.ASSEMBLER_EV);
        h.assertTrue(m.type().voltage() == 2048, "EV assembler voltage " + m.type().voltage());
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("turbine_blade_steel"), 12));
        m.setItem(1, new ItemStack(ru.iis.command.SelfTest.item("long_rod_tungsten_steel")));
        h.succeedWhen(() -> {
            ItemStack o = m.getItem(m.type().inputs);
            h.assertTrue(o.getItem() instanceof ru.iis.multi.TurbineRotorItem r && r.size == LargeTurbineLogic.Size.LARGE, "rotor: " + o);
            h.assertTrue(o.getMaxDamage() == 153600, "rotor durability " + o.getMaxDamage());
        });
    }

    /** Большая турбина с большим стальным ротором на динамо-люке EV (D99): выход растёт до 540 EU/t, машина цела. */
    static void largeRotorEvDynamo(GameTestHelper h) {
        LargeTurbineBlockEntity t = turbine(h, HatchType.DYNAMO_EV);
        t.setItem(0, MultiContent.ROTORS.get(LargeTurbineLogic.Size.LARGE).get().create("steel"));
        DynamoHatchBlockEntity dyn = h.getBlockEntity(T_DYNAMO, DynamoHatchBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(h.getBlockState(T_CTRL).is(MultiContent.LARGE_STEAM_TURBINE.get()), "turbine exploded");
            h.assertTrue(t.formed() && t.eut() == 540, "turbine EU/t " + t.eut());
            h.assertTrue(dyn.voltage() == 2048 && dyn.energy.stored > DynamoHatchBlockEntity.MIN_STORED, "dynamo got " + dyn.energy.stored);
        });
    }

    /** Трансформатор EV (D99): понижает 8192 → 2048 В, 4 А; запас 512 + 2 × 8192. Буфер EV 4×4: запас 2048 × 64 × 16. */
    static void evTransformerAndBuffer(GameTestHelper h) {
        BlockPos tp = new BlockPos(2, 1, 2), bp = new BlockPos(5, 1, 2);
        h.setBlock(tp, LvContent.TRANSFORMER_EV.get().defaultBlockState().setValue(ru.iis.lv.block.TransformerBlock.FACING, Direction.WEST));
        var t = h.getBlockEntity(tp, ru.iis.lv.block.TransformerBlockEntity.class);
        h.assertTrue(t.outputVoltage() == 2048 && t.outputAmperes() == 4, "step-down out " + t.outputVoltage() + " V " + t.outputAmperes() + " A");
        h.assertTrue(t.energy.capacity == 512 + 2 * 8192, "capacity " + t.energy.capacity);
        h.setBlock(bp, LvContent.BATTERY_BUFFERS_EV.get(3).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        var b = h.getBlockEntity(bp, ru.iis.lv.block.BatteryBufferBlockEntity.class);
        h.assertTrue(b.slots() == 16 && b.energy.capacity == 2048L * 64 * 16 && b.outputVoltage() == 2048, "buffer " + b.energy.capacity);
        b.energy.stored = b.energy.capacity;
        long shown = (b.data.get(0) & 0xFFFF) | ((long) (b.data.get(1) & 0xFFFF) << 16);
        h.assertTrue(shown == b.energy.capacity, "gui energy " + shown);
        h.succeed();
    }

    // --- D101: плавка в ЭДП (люк EV, чтобы разгон сократил время) ---------------------------------------------------------
    private static int count(ItemBusBlockEntity bus, String id) {
        int n = 0;
        for (ItemStack s : bus.stacks()) if (s.is(ru.iis.command.SelfTest.item(id))) n += s.getCount();
        return n;
    }

    private static EbfBlockEntity smeltingEbf(GameTestHelper h, net.minecraft.world.level.block.Block coil) {
        EbfBlockEntity e = MultiGameTests.build(h, 1, HatchType.ENERGY_EV, coil, true);
        e.maintenance().setMask(63);
        return e;
    }

    /** Титан на кантале (GT_MachineRecipeLoader:1043): 2 магния + 1000 л TiCl4 → горячий титан + 2 MgCl2; 2140 K, 480 EU/t → на EV
     *  1920 EU/t × 400 тиков. На константане (1800 K) — не идёт. */
    static void ebfSmeltsTitanium(GameTestHelper h) {
        smeltingEbf(h, MultiContent.COIL_KANTHAL.get());
        ItemBusBlockEntity in = h.getBlockEntity(new BlockPos(5, 1, 2), ItemBusBlockEntity.class);
        ItemBusBlockEntity out = h.getBlockEntity(new BlockPos(4, 1, 3), ItemBusBlockEntity.class);
        FluidHatchBlockEntity fluid = h.getBlockEntity(MultiGameTests.FLUID_HATCH, FluidHatchBlockEntity.class);
        in.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("dust_magnesium"), 2));
        fluid.tank().setAmount(gt("titaniumtetrachloride"), 1000);
        h.succeedWhen(() -> {
            h.assertTrue(count(out, "hot_ingot_titanium") == 1 && count(out, "dust_magnesiumchloride") == 2, "output bus " + out.stacks());
            h.assertTrue(fluid.tank().amount() == 0, "TiCl4 left " + fluid.tank().amount());
        });
    }

    /** На константане (1800 K) титан (2140 K) не плавится — магний остаётся в шине. */
    static void ebfTitaniumNeedsKanthal(GameTestHelper h) {
        smeltingEbf(h, MultiContent.COIL_CUPRONICKEL.get());
        ItemBusBlockEntity in = h.getBlockEntity(new BlockPos(5, 1, 2), ItemBusBlockEntity.class);
        FluidHatchBlockEntity fluid = h.getBlockEntity(MultiGameTests.FLUID_HATCH, FluidHatchBlockEntity.class);
        in.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("dust_magnesium"), 2));
        fluid.tank().setAmount(gt("titaniumtetrachloride"), 1000);
        h.runAtTickTime(200, () -> {
            h.assertTrue(in.getItem(0).getCount() == 2 && fluid.tank().amount() == 1000, "smelted on cupronickel: " + in.getItem(0));
            h.succeed();
        });
    }

    /** Вольфрам (GT_MachineRecipeLoader2:50): 4 оксида + 6000 л водорода → горячий вольфрам; 3000 K — нихром; 120 EU/t × 12000 →
     *  на EV 1920 × 3000. */
    static void ebfSmeltsTungsten(GameTestHelper h) {
        smeltingEbf(h, MultiContent.COIL_NICHROME.get());
        ItemBusBlockEntity in = h.getBlockEntity(new BlockPos(5, 1, 2), ItemBusBlockEntity.class);
        ItemBusBlockEntity out = h.getBlockEntity(new BlockPos(4, 1, 3), ItemBusBlockEntity.class);
        FluidHatchBlockEntity fluid = h.getBlockEntity(MultiGameTests.FLUID_HATCH, FluidHatchBlockEntity.class);
        in.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("dust_tungsten_trioxide"), 4));
        fluid.tank().setAmount(gt("hydrogen"), 6000);
        h.succeedWhen(() -> h.assertTrue(count(out, "hot_ingot_tungsten") == 1, "output bus " + out.stacks()));
    }

    /** Вольфрамосталь (GT_MachineRecipeLoader:1029): вольфрам + сталь → 2 горячих слитка; 3000 K — нихром, 480 EU/t. */
    static void ebfSmeltsTungstensteel(GameTestHelper h) {
        smeltingEbf(h, MultiContent.COIL_NICHROME.get());
        ItemBusBlockEntity in = h.getBlockEntity(new BlockPos(5, 1, 2), ItemBusBlockEntity.class);
        ItemBusBlockEntity out = h.getBlockEntity(new BlockPos(4, 1, 3), ItemBusBlockEntity.class);
        in.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("ingot_tungsten")));
        in.setItem(1, new ItemStack(ru.iis.command.SelfTest.item("ingot_steel")));
        h.succeedWhen(() -> h.assertTrue(count(out, "hot_ingot_tungsten_steel") == 2, "output bus " + out.stacks()));
    }

    /** Ванадий-галлий (GT_MachineRecipeLoader:1031, D103): 3 ванадия + 1 галлий → 4 горячих слитка; 4500 K — только на
     *  вольфрамостальных катушках, 480 EU/t → на EV 1920 EU/t × 2250 тиков. */
    static void ebfSmeltsVanadiumGallium(GameTestHelper h) {
        EbfBlockEntity e = smeltingEbf(h, MultiContent.COIL_TUNGSTENSTEEL.get());
        ItemBusBlockEntity in = h.getBlockEntity(new BlockPos(5, 1, 2), ItemBusBlockEntity.class);
        ItemBusBlockEntity out = h.getBlockEntity(new BlockPos(4, 1, 3), ItemBusBlockEntity.class);
        in.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("ingot_vanadium"), 3));
        in.setItem(1, new ItemStack(ru.iis.command.SelfTest.item("ingot_gallium")));
        h.succeedWhen(() -> {
            h.assertTrue(e.heat() == EbfBlockEntity.TUNGSTENSTEEL_HEAT, "heat " + e.heat());
            h.assertTrue(count(out, "hot_ingot_vanadium_gallium") == 4, "output bus " + out.stacks());
        });
    }

    /** На нихроме (3600 K) ванадий-галлий не плавится. */
    static void ebfVanadiumGalliumNeedsTungstensteel(GameTestHelper h) {
        smeltingEbf(h, MultiContent.COIL_NICHROME.get());
        ItemBusBlockEntity in = h.getBlockEntity(new BlockPos(5, 1, 2), ItemBusBlockEntity.class);
        ItemBusBlockEntity out = h.getBlockEntity(new BlockPos(4, 1, 3), ItemBusBlockEntity.class);
        in.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("ingot_vanadium"), 3));
        in.setItem(1, new ItemStack(ru.iis.command.SelfTest.item("ingot_gallium")));
        h.runAtTickTime(300, () -> {
            h.assertTrue(count(out, "hot_ingot_vanadium_gallium") == 0, "smelted on nichrome: " + out.stacks());
            h.succeed();
        });
    }

    /** Измельчитель IV (D103): 8192 В, четыре выхода; руда халькопирита → 2 дроблёной. */
    static void ivMaceratorOre(GameTestHelper h) {
        var m = poweredMachine(h, ru.iis.lv.LvMachineType.MACERATOR_IV);
        h.assertTrue(m.type().voltage() == 8192 && m.type().outputs == 4, "IV macerator " + m.type().voltage() + " / " + m.type().outputs);
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("ore_chalcopyrite")));
        h.succeedWhen(() -> h.assertTrue(m.getItem(1).is(ru.iis.command.SelfTest.item("crushed_chalcopyrite")) && m.getItem(1).getCount() == 2,
                "output " + m.getItem(1)));
    }

    /** Силикон (GT_MachineRecipeLoader:3452, D103): 9 полидиметилсилоксана + 1 сера → 1296 л расплава силикона, химреактор IV. */
    static void ivReactorSilicone(GameTestHelper h) {
        var m = poweredMachine(h, ru.iis.lv.LvMachineType.CHEMICAL_REACTOR_IV);
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("dust_polydimethylsiloxane"), 9));
        m.setItem(1, new ItemStack(ru.iis.command.SelfTest.item("dust_sulfur")));
        h.succeedWhen(() -> h.assertTrue(m.outTank().amount() == 1296 && m.outTank().resource().getFluid() == gt("molten_silicone"),
                "silicone " + m.outTank().amount()));
    }

    /** Измельчитель EV (D100): 2048 В; руда халькопирита → 2 дроблёной. */
    static void evMaceratorOre(GameTestHelper h) {
        var m = poweredMachine(h, ru.iis.lv.LvMachineType.MACERATOR_EV);
        h.assertTrue(m.type().voltage() == 2048 && m.type().outputs == 3, "EV macerator " + m.type().voltage() + " / " + m.type().outputs);
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("ore_chalcopyrite")));
        h.succeedWhen(() -> h.assertTrue(m.getItem(1).is(ru.iis.command.SelfTest.item("crushed_chalcopyrite")) && m.getItem(1).getCount() == 2,
                "output " + m.getItem(1)));
    }

    /** Химреактор EV (D98): 7 тунгстата + 4000 л соляной кислоты → 4 хлорида лития + 7 вольфрамовой кислоты (1920 EU/t). */
    static void evReactorTungsticAcid(GameTestHelper h) {
        var m = poweredMachine(h, ru.iis.lv.LvMachineType.CHEMICAL_REACTOR_EV);
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("dust_tungstate"), 7));
        m.tank().setAmount(gt("hydrochloricacid_gt5u"), 4000);
        h.succeedWhen(() -> {
            int i = m.type().inputs;
            h.assertTrue(m.getItem(i).is(ru.iis.command.SelfTest.item("dust_lithium_chloride")) && m.getItem(i).getCount() == 4, "out0 " + m.getItem(i));
            h.assertTrue(m.getItem(i + 1).is(ru.iis.command.SelfTest.item("dust_tungstig_acid")) && m.getItem(i + 1).getCount() == 7, "out1 " + m.getItem(i + 1));
        });
    }
}

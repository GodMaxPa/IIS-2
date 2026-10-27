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
}

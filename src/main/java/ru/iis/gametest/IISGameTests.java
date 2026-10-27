package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.command.SelfTest;
import ru.iis.fluid.IISFluids;
import ru.iis.machine.BoilerType;
import ru.iis.machine.IISMachines;
import ru.iis.machine.SteamMachineType;
import ru.iis.machine.block.*;
import ru.iis.machine.logic.BbfStructure;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Игровые тесты среза «пар» (запуск: ./gradlew runGameTestServer, или /test runall в игре).
 * Каждый тест строит машины на каменном полу 9×9 и ждёт результата в реальных тиках.
 */
public final class IISGameTests {
    private IISGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, IIS.MODID);
    private static final Identifier FLOOR = Identifier.fromNamespaceAndPath(IIS.MODID, "floor9");
    /** Пол 16×16 для больших построек (личный челнок Био). */
    private static final Identifier FLOOR16 = Identifier.fromNamespaceAndPath(IIS.MODID, "floor16");

    private record Test(String name, int maxTicks, Consumer<GameTestHelper> fn, boolean sky, Identifier structure) {
        Test(String name, int maxTicks, Consumer<GameTestHelper> fn) {
            this(name, maxTicks, fn, false, FLOOR);
        }

        Test(String name, int maxTicks, Consumer<GameTestHelper> fn, boolean sky) {
            this(name, maxTicks, fn, sky, FLOOR);
        }
    }

    private static final List<Test> TESTS = List.of(
            new Test("boiler_feeds_macerator", 1600, IISGameTests::boilerFeedsMacerator),
            new Test("steam_through_pipes", 400, IISGameTests::steamThroughPipes),
            new Test("forge_hammer_vent_blocked", 300, IISGameTests::ventBlocked),
            new Test("alloy_smelter_bronze", 600, IISGameTests::alloySmelter),
            new Test("steam_furnace", 400, IISGameTests::steamFurnace),
            new Test("bricked_blast_furnace_starts", 100, IISGameTests::bbfStarts),
            new Test("dry_boiler_explodes", 200, IISGameTests::dryBoilerExplodes),
            new Test("lv_turbine_powers_macerator", 1400, LvGameTests::turbinePowersMacerator),
            new Test("lv_overvoltage_explodes", 100, LvGameTests::overvoltageExplodes),
            new Test("lv_cable_burns", 200, LvGameTests::cableBurns),
            new Test("lv_water_explodes", 1100, LvGameTests::waterExplodes),
            new Test("lv_bare_wire_shocks", 200, LvGameTests::bareWireShocks),
            new Test("lv_buffer_charges_battery", 200, LvGameTests::bufferChargesBattery),
            new Test("lv_coke_oven", 2000, LvGameTests::cokeOven),
            new Test("lv_ore_washer", 700, LvGameTests::oreWasher),
            new Test("lv_multimeter", 200, LvGameTests::multimeter),
            new Test("ebf_ruby_to_aluminium", 500, MultiGameTests::ebfRuby),
            new Test("ebf_one_lv_hatch_idle", 100, MultiGameTests::ebfOneHatchIdle),
            new Test("ebf_maintenance", 200, MultiGameTests::ebfMaintenance),
            new Test("ebf_gui_opens", 20, MultiGameTests::ebfGuiOpens),
            new Test("boiler_pollutes", 200, MultiGameTests::boilerPollutes),
            // MV (D81)
            new Test("mv_thermal_centrifuge", 700, MvGameTests::thermalCentrifugeMv),
            new Test("lv_thermal_centrifuge_idle", 100, MvGameTests::thermalCentrifugeLvIdle),
            new Test("vacuum_freezer_hot_ingot", 800, MvGameTests::freezerHotIngot),
            new Test("vacuum_freezer_needs_hollow", 100, MvGameTests::freezerNeedsHollow),
            new Test("hot_ingot_burns", 20, MvGameTests::hotIngotBurns),
            new Test("mv_macerator_overclock", 100, MvGameTests::maceratorMvOverclock),
            new Test("lv_step_up_powers_mv_machine", 400, MvGameTests::lvStepUpPowersMvMachine),
            new Test("transformer_mv_step_down", 100, MvGameTests::transformerMvStepDown),
            new Test("vacuum_freezer_one_mv_hatch", 500, MvGameTests::freezerOneMvHatch),
            new Test("vacuum_freezer_one_lv_hatch_idle", 100, MvGameTests::freezerOneLvHatchIdle),
            new Test("large_boiler_bronze_coal", 600, LargeGameTests::bronzeBoilerCoal),
            new Test("large_boiler_creosote", 300, LargeGameTests::bronzeBoilerCreosote),
            new Test("large_boiler_circuit_throttle", 1200, LargeGameTests::boilerCircuitThrottle),
            new Test("large_boiler_structure", 200, LargeGameTests::boilerStructureRules),
            new Test("large_turbine_power", 600, LargeGameTests::turbineMakesPower),
            new Test("large_turbine_structure", 300, LargeGameTests::turbineStructureRules),
            new Test("extruder_mv_plate", 200, MvGameTests::extruderMvPlate),
            new Test("extruder_lv_cell_only", 300, MvGameTests::extruderLvCellOnly),
            new Test("forming_press_doge_coin", 200, MvGameTests::pressDogeCoin),
            new Test("fluid_heater_steam", 100, MvGameTests::fluidHeaterSteam),
            new Test("electric_oven_mv_smelts", 150, MvGameTests::ovenMvSmelts),
            new Test("arc_furnace_mv_iron", 200, MvGameTests::arcFurnaceMvIron),
            new Test("arc_furnace_lv_idle", 100, MvGameTests::arcFurnaceLvIdle),
            new Test("laser_engraver_stone", 150, MvGameTests::laserEngraverStone),
            new Test("cover_conveyor", 600, CoverGameTests::conveyor),
            new Test("cover_pump", 40, CoverGameTests::pump),
            new Test("cover_controller", 40, CoverGameTests::controller),
            new Test("cover_activity", 200, CoverGameTests::activity),
            new Test("cover_attach", 20, CoverGameTests::attachConfigureRemove),
            new Test("lv2_canner", 300, Lv2GameTests::canner),
            new Test("lv2_centrifuge", 800, Lv2GameTests::centrifuge),
            new Test("lv2_sifter", 2000, Lv2GameTests::sifter),
            new Test("lv2_separator", 600, Lv2GameTests::separator),
            new Test("lv2_autoclave", 200, Lv2GameTests::autoclave),
            new Test("lv2_cutter", 600, Lv2GameTests::cutter),
            new Test("fix_wrench_rotates", 20, FixGameTests::wrenchRotates),
            new Test("fix_wrench_only_harvest", 20, FixGameTests::wrenchOnlyHarvest),
            new Test("fix_creosote_movable", 100, FixGameTests::creosoteMovable),
            new Test("fix_auto_output", 100, FixGameTests::autoOutput),
            new Test("fix_tree_feller", 20, FixGameTests::treeFeller),
            new Test("fast_leaf_decay", 100, FixGameTests::fastLeafDecay),
            new Test("lv_rubber_tree", 20, LvGameTests::rubberTree, true), // дереву нужен открытый верх (без потолка теста)
            new Test("bio_shuttle_feed_grow", 1600, BioGameTests::shuttleFeedAndGrow, false, FLOOR16),
            new Test("bio_starvation_retreat", 900, BioGameTests::starvationRetreat),
            new Test("bio_core_removed_kills_tissue", 20, BioGameTests::coreRemovedKillsTissue),
            new Test("bio_maw_needs_colony", 20, BioGameTests::mawNeedsColony),
            new Test("bio_damage_alarms", 300, BioGameTests::damageAlarms),
            new Test("bio_torn_tissue_dies", 300, BioGameTests::tornTissueDies),
            new Test("bio_tissue_melts_snow", 900, BioGameTests::tissueMeltsSnow),
            new Test("bio_b1_pocket_l01", 1600, BioGameTests::pocketL01),
            new Test("bio_b1_knife_carcass", 40, BioGameTests::knifeCarcass),
            new Test("bio_b1_butcher_carcass", 900, BioGameTests::butcherCarcass),
            new Test("bio_b1_pot_gel", 3000, BioGameTests::potGel),
            new Test("bio_b1_mixer_g0", 1000, BioGameTests::mixerG0),
            new Test("bio_b1_substrate_tissue", 2800, BioGameTests::substrateTissue),
            new Test("bio_b1_substrate_needs_colony", 100, BioGameTests::substrateNeedsColony),
            new Test("bio_b1_bed_grows_maw", 4000, BioGameTests::bedGrowsMaw),
            new Test("bio_b1_scrape_culture", 20, BioGameTests::scrapeCulture),
            new Test("fluid_gt_registered", 20, FluidGameTests::gtFluidsRegistered),
            new Test("fluid_capsules_tank", 20, FluidGameTests::capsulesAndTank),
            new Test("fluid_tank_drains_down", 200, FluidGameTests::tankDrainsDown),
            new Test("fluid_tank_pipe_tank", 400, FluidGameTests::tankPipeTank),
            new Test("fluid_hot_fluid_burns_pipe", 600, FluidGameTests::hotFluidBurnsPipe),
            new Test("bio_net_core_pumps", 600, FluidGameTests::corePumpsNetwork),
            new Test("bio_net_flat_no_pump", 100, FluidGameTests::flatNetworkWithoutPump),
            new Test("bio_net_gravity_drain", 300, FluidGameTests::gravityDrain),
            new Test("bio_net_tall_ulv_bursts", 100, FluidGameTests::tallUlvColumnBursts),
            new Test("bio_net_check_valve_forward", 400, FluidGameTests::checkValveForward),
            new Test("bio_net_check_valve_backward", 140, FluidGameTests::checkValveBackward),
            new Test("bio_net_clamp", 400, FluidGameTests::clampClosesAndOpens),
            new Test("bio_net_living_tank", 800, FluidGameTests::livingTankReceiveAndSupply),
            new Test("bio_net_muscle_pump", 400, FluidGameTests::musclePumpMovesFluid),
            new Test("bio_net_lv_pump_bursts_ulv", 100, FluidGameTests::lvPumpBurstsUlvVessel),
            new Test("bio_lv_gland_e1", 900, FluidGameTests::glandMakesE1),
            new Test("bio_lv_nest_needs_buffers", 60, FluidGameTests::nestNeedsBuffers),
            new Test("stand_world_works", 1800, FluidGameTests::standWorks),
            new Test("lv_circuit_selects_recipe", 20, CircuitGameTests::circuitSelectsRecipe),
            new Test("lv_bender_steel_capsules", 1500, CircuitGameTests::benderSteelCapsules),
            new Test("lv3_electrolyzer_pyrolusite", 600, ChemGameTests::electrolyzerPyrolusite),
            new Test("lv3_electrolyzer_water", 2600, ChemGameTests::electrolyzerWater),
            new Test("lv3_chemical_rubber", 1000, ChemGameTests::chemicalRubber),
            new Test("lv3_fluid_extractor_tin", 400, ChemGameTests::fluidExtractorTin),
            new Test("lv3_solidifier_ingot", 400, ChemGameTests::solidifierIngot),
            new Test("lv3_mixer_stainless", 1200, ChemGameTests::mixerStainless),
            new Test("lv4_distillery_water", 200, ChemGameTests::distilleryWater),
            new Test("lv4_bath_mercury_gold", 1000, ChemGameTests::bathMercuryGold),
            new Test("lv4_bath_bleach_glass", 600, ChemGameTests::bathBleachGlass),
            new Test("lv_tool_drill_mines_stone", 20, ElectricToolGameTests::drillMinesStone),
            new Test("lv_tool_wrench_uses_charge", 20, ElectricToolGameTests::wrenchUsesCharge),
            new Test("lv_tool_buffer_charges_tool", 200, ElectricToolGameTests::bufferChargesTool),
            new Test("lv_tool_crafted_charge", 20, ElectricToolGameTests::craftedToolTakesCharge),
            new Test("wire_assembler_joins_8x", 600, WireGameTests::assemblerJoinsWires),
            new Test("wire_assembler_insulates_copper", 400, WireGameTests::assemblerInsulatesCopper),
            new Test("wire_new_materials_carry_power", 400, WireGameTests::newWiresCarryPower),
            new Test("cover_regulator_pulls_into_hatch", 200, CoverHostGameTests::regulatorPullsIntoHatch),
            new Test("cover_filter_on_pipe", 20, CoverHostGameTests::filterOnPipe),
            new Test("cover_detector_on_steam_machine", 100, CoverHostGameTests::detectorOnSteamMachine),
            new Test("bio_hearth_fuel_slot", 20, FluidGameTests::hearthFuelSlot),
            new Test("bio_press_extract", 2000, FluidGameTests::pressExtract),
            new Test("bio_filter_w0", 1100, FluidGameTests::filterW0),
            new Test("selftest", 20, h -> {
                h.assertTrue(SelfTest.runSilently(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4))), "selftest failed, see log");
                h.succeed();
            }));

    static {
        for (Test t : TESTS) FUNCTIONS.register(t.name, () -> t.fn);
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(IISGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(Identifier.fromNamespaceAndPath(IIS.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test t : TESTS) {
            Identifier id = Identifier.fromNamespaceAndPath(IIS.MODID, t.name);
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(env, t.structure, t.maxTicks, 0, true, net.minecraft.world.level.block.Rotation.NONE, false, 1, 1, t.sky, 0)));
        }
    }

    // --- helpers -----------------------------------------------------------------------------------------------
    private static BlockState machine(SteamMachineType type, SteamMachineType.Tier tier, Direction facing) {
        return IISMachines.STEAM_MACHINES.get(type.blockId(tier)).get().defaultBlockState().setValue(MachineBlock.FACING, facing);
    }

    private static SteamMachineBlockEntity steamMachine(GameTestHelper h, BlockPos p, SteamMachineType type, Direction facing, boolean fillSteam) {
        h.setBlock(p, machine(type, SteamMachineType.Tier.BRONZE, facing));
        SteamMachineBlockEntity be = h.getBlockEntity(p, SteamMachineBlockEntity.class);
        if (fillSteam) ((FluidTank) be.fluidHandler(Direction.UP)).setAmount(IISFluids.STEAM.get(), 32000);
        return be;
    }

    private static BoilerBlockEntity hotBoiler(GameTestHelper h, BlockPos p, Direction facing) {
        h.setBlock(p, IISMachines.BOILERS.get(BoilerType.COAL_BRONZE).get().defaultBlockState().setValue(MachineBlock.FACING, facing));
        BoilerBlockEntity b = h.getBlockEntity(p, BoilerBlockEntity.class);
        b.fillWaterFromBucket();
        for (int i = 0; i < 15; i++) b.fillWaterFromBucket();
        b.logic().temperature = 500; // нагрев с 20 до 500 занимает ~6,5 минуты, здесь проверяется выработка и подача пара
        b.setItem(BoilerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 64));
        return b;
    }

    private static ItemStack out(SteamMachineBlockEntity be) {
        return be.getItem(be.getContainerSize() - 1);
    }

    // --- tests --------------------------------------------------------------------------------------------------
    /** Котёл снизу отдаёт пар вверх в дробитель; руда халькопирита → 2 дроблёной (GT: 400×2 тиков). */
    private static void boilerFeedsMacerator(GameTestHelper h) {
        BlockPos boiler = new BlockPos(4, 1, 4), mac = boiler.above();
        hotBoiler(h, boiler, Direction.NORTH);
        SteamMachineBlockEntity m = steamMachine(h, mac, SteamMachineType.MACERATOR, Direction.NORTH, false);
        m.setItem(0, new ItemStack(SelfTest.item("ore_chalcopyrite")));
        h.succeedWhen(() -> {
            ItemStack o = out(m);
            h.assertTrue(o.is(SelfTest.item("crushed_chalcopyrite")) && o.getCount() == 2, "expected 2 crushed chalcopyrite, got " + o);
            IIS.LOGGER.info("[IIS gametest] boiler_feeds_macerator done at tick {}", h.getTick());
        });
    }

    /** Котёл → 3 малые бронзовые трубы → машина: пар доходит. */
    private static void steamThroughPipes(GameTestHelper h) {
        BlockPos boiler = new BlockPos(1, 1, 4);
        hotBoiler(h, boiler, Direction.WEST);
        BlockState pipe = IISMachines.PIPES.get("pipe_small_bronze").get().defaultBlockState()
                .setValue(PipeBlock.PROPS.get(Direction.EAST), true).setValue(PipeBlock.PROPS.get(Direction.WEST), true);
        for (int x = 2; x <= 4; x++) h.setBlock(new BlockPos(x, 1, 4), pipe);
        SteamMachineBlockEntity m = steamMachine(h, new BlockPos(5, 1, 4), SteamMachineType.COMPRESSOR, Direction.EAST, false);
        h.succeedWhen(() -> {
            int steam = ((FluidTank) m.fluidHandler(Direction.WEST)).amount();
            h.assertTrue(steam >= 1000, "steam in machine: " + steam);
        });
    }

    /** Молот: камень → булыжник. Задняя сторона перекрыта — после первой операции машина ждёт; открыли — продолжила. */
    private static void ventBlocked(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 4);
        SteamMachineBlockEntity m = steamMachine(h, p, SteamMachineType.FORGE_HAMMER, Direction.NORTH, true);
        BlockPos vent = p.relative(Direction.SOUTH);
        h.setBlock(vent, Blocks.STONE);
        m.setItem(0, new ItemStack(Items.STONE, 2));
        h.runAfterDelay(120, () -> {
            h.assertTrue(out(m).getCount() == 1 && m.getItem(0).getCount() == 1, "blocked vent must stop after one operation: out=" + out(m) + " in=" + m.getItem(0));
            h.setBlock(vent, Blocks.AIR);
            h.runAfterDelay(80, () -> {
                h.assertTrue(out(m).getCount() == 2, "after clearing the vent the second stone is hammered: " + out(m));
                h.succeed();
            });
        });
    }

    /** Печь сплавов: 3 медных + 1 оловянный слиток → 4 бронзовых (GT: 200×2 тиков). */
    private static void alloySmelter(GameTestHelper h) {
        SteamMachineBlockEntity m = steamMachine(h, new BlockPos(4, 1, 4), SteamMachineType.ALLOY_SMELTER, Direction.NORTH, true);
        m.setItem(0, new ItemStack(Items.COPPER_INGOT, 3));
        m.setItem(1, new ItemStack(SelfTest.item("ingot_tin")));
        h.succeedWhen(() -> h.assertTrue(out(m).is(SelfTest.item("ingot_bronze")) && out(m).getCount() == 4, "expected 4 bronze, got " + out(m)));
    }

    /** Паровая печь плавит по ванильным рецептам: песок → стекло (бронза: 256 тиков). */
    private static void steamFurnace(GameTestHelper h) {
        SteamMachineBlockEntity m = steamMachine(h, new BlockPos(4, 1, 4), SteamMachineType.FURNACE, Direction.NORTH, true);
        m.setItem(0, new ItemStack(Items.SAND));
        h.succeedWhen(() -> h.assertTrue(out(m).is(Items.GLASS), "expected glass, got " + out(m)));
    }

    /** Кирпичная доменная печь: собранная форма + железо и уголь → печь запускается и заливает лаву. */
    private static void bbfStarts(GameTestHelper h) {
        BlockPos ctrl = new BlockPos(4, 2, 2);
        Direction facing = Direction.NORTH; // лицом на север, печь уходит на юг (+z)
        h.setBlock(ctrl, IISMachines.BRICKED_BLAST_FURNACE.get().defaultBlockState().setValue(MachineBlock.FACING, facing));
        List<BbfStructure.Pos> pos = new ArrayList<>();
        List<BbfStructure.Cell> cells = BbfStructure.cells(pos);
        Direction back = facing.getOpposite(), right = back.getClockWise();
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i) != BbfStructure.Cell.CASING) continue;
            BbfStructure.Pos p = pos.get(i);
            h.setBlock(ctrl.relative(right, p.right()).relative(Direction.UP, p.up()).relative(back, p.back()), IISMachines.FIREBRICK_CASING.get());
        }
        BbfBlockEntity be = h.getBlockEntity(ctrl, BbfBlockEntity.class);
        be.setItem(0, new ItemStack(Items.IRON_INGOT));
        be.setItem(1, new ItemStack(Items.COAL, 4));
        BlockPos lava = ctrl.relative(back);
        h.succeedWhen(() -> {
            h.assertTrue(be.formed(), "structure not formed");
            h.assertTrue(h.getBlockState(ctrl).getValue(MachineBlock.ACTIVE), "furnace not active");
            h.assertTrue(h.getBlockState(lava).is(Blocks.LAVA), "no lava in the shaft");
            h.assertTrue(be.getItem(0).isEmpty() && be.getItem(1).isEmpty(), "inputs not consumed");
        });
    }

    /** Котёл нагрет без воды; когда в него заливают воду, он взрывается (GT5). */
    private static void dryBoilerExplodes(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 4);
        h.setBlock(p, IISMachines.BOILERS.get(BoilerType.COAL_BRONZE).get().defaultBlockState());
        BoilerBlockEntity b = h.getBlockEntity(p, BoilerBlockEntity.class);
        b.logic().temperature = 300;
        h.runAfterDelay(60, () -> {
            h.assertTrue(b.logic().hadNoWater, "boiler must notice it is dry");
            var handler = b.fluidHandler(Direction.UP);
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                handler.insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(Fluids.WATER), 1000, tx);
                tx.commit();
            }
            h.succeedWhen(() -> h.assertTrue(!(h.getBlockState(p).getBlock() instanceof BoilerBlock), "boiler still there"));
        });
    }
}

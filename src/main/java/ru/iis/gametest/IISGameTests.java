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

    private record Test(String name, int maxTicks, Consumer<GameTestHelper> fn) {}

    private static final List<Test> TESTS = List.of(
            new Test("boiler_feeds_macerator", 1600, IISGameTests::boilerFeedsMacerator),
            new Test("steam_through_pipes", 400, IISGameTests::steamThroughPipes),
            new Test("forge_hammer_vent_blocked", 300, IISGameTests::ventBlocked),
            new Test("alloy_smelter_bronze", 600, IISGameTests::alloySmelter),
            new Test("steam_furnace", 400, IISGameTests::steamFurnace),
            new Test("bricked_blast_furnace_starts", 100, IISGameTests::bbfStarts),
            new Test("dry_boiler_explodes", 200, IISGameTests::dryBoilerExplodes),
            new Test("selftest", 20, h -> {
                h.assertTrue(SelfTest.runSilently(h.getLevel()), "selftest failed, see log");
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
                    new TestData<>(env, FLOOR, t.maxTicks, 0, true)));
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

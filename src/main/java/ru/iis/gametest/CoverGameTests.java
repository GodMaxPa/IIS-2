package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.iis.command.SelfTest;
import ru.iis.cover.CoverKind;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;

/** Покрытия GT: конвейер, помпа, контроллер, детектор активности, установка/отвёртка/монтировка. */
final class CoverGameTests {
    private CoverGameTests() {}

    private static ElectricMachineBlockEntity powered(GameTestHelper h, BlockPos p, LvMachineType t) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, p, t, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        return m;
    }

    /** Конвейер (выход) на восточной стороне: выход дробителя уходит в сундук за 400 тиков. */
    static void conveyor(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.MACERATOR);
        h.setBlock(new BlockPos(5, 1, 4), Blocks.CHEST);
        m.setItem(m.type().inputs, new ItemStack(Items.COBBLESTONE, 20));
        m.covers().attach(Direction.EAST, CoverKind.CONVEYOR);
        h.succeedWhen(() -> {
            ChestBlockEntity c = h.getBlockEntity(new BlockPos(5, 1, 4), ChestBlockEntity.class);
            h.assertTrue(c.countItem(Items.COBBLESTONE) == 20, "chest has " + c.countItem(Items.COBBLESTONE));
        });
    }

    /** Помпа (выход) перекачивает воду из одной рудопромывки в соседнюю по 32 л/тик. */
    static void pump(GameTestHelper h) {
        ElectricMachineBlockEntity a = powered(h, new BlockPos(3, 1, 4), LvMachineType.ORE_WASHER);
        ElectricMachineBlockEntity b = powered(h, new BlockPos(4, 1, 4), LvMachineType.ORE_WASHER);
        a.tank().setAmount(Fluids.WATER, 1000);
        a.covers().attach(Direction.EAST, CoverKind.PUMP);
        h.runAtTickTime(10, () -> {
            h.assertTrue(b.tank().amount() > 0 && b.tank().amount() <= 32 * 11, "pumped " + b.tank().amount());
            h.succeed();
        });
    }

    /** Контроллер: «включить красным сигналом» — без сигнала машина выключена, с блоком красного камня включена. */
    static void controller(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.MACERATOR);
        m.covers().attach(Direction.EAST, CoverKind.CONTROLLER);
        h.runAtTickTime(3, () -> {
            h.assertFalse(m.allowedToWork, "works without redstone");
            h.setBlock(new BlockPos(5, 1, 4), Blocks.REDSTONE_BLOCK);
        });
        h.runAtTickTime(6, () -> {
            h.assertTrue(m.allowedToWork, "does not work with redstone");
            h.succeed();
        });
    }

    /** Детектор активности выдаёт сигнал, пока дробитель работает. */
    static void activity(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.MACERATOR);
        m.setItem(0, new ItemStack(SelfTest.item("ore_chalcopyrite"), 4));
        m.covers().attach(Direction.EAST, CoverKind.ACTIVITY_DETECTOR);
        h.succeedWhen(() -> {
            int s = h.getLevel().getSignal(h.absolutePos(new BlockPos(4, 1, 4)), Direction.WEST); // так спрашивает восточный сосед
            h.assertTrue(s > 0, "signal " + s);
        });
    }

    /** ПКМ конвейером ставит покрытие, отвёртка меняет режим, монтировка снимает. */
    static void attachConfigureRemove(GameTestHelper h) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), LvMachineType.MACERATOR, Direction.NORTH);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(new BlockPos(4, 1, 4));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0.5, 0, 0), Direction.EAST, abs, false);
        java.util.function.Consumer<ItemStack> click = st -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, st);
            h.getLevel().getBlockState(abs).useItemOn(st, h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        };
        ItemStack conv = new ItemStack(LvContent.CONVEYOR_MODULE.get());
        click.accept(conv);
        h.assertTrue(m.covers().get(Direction.EAST) == CoverKind.CONVEYOR, "not attached");
        h.assertTrue(conv.isEmpty(), "item not consumed");
        click.accept(new ItemStack(SelfTest.item("screwdriver_steel")));
        h.assertTrue(m.covers().data(Direction.EAST) == 1, "mode " + m.covers().data(Direction.EAST));
        h.assertFalse(m.covers().letsItemsOut(m, Direction.EAST), "import mode lets items out");
        click.accept(new ItemStack(SelfTest.item("crowbar_steel")));
        h.assertTrue(m.covers().get(Direction.EAST) == null, "not removed");
        h.succeed();
    }
}

package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import ru.iis.command.SelfTest;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.machine.block.MachineBlock;
import ru.iis.world.TreeFeller;

/** Исправления по отчёту владельца: ключ, креозот, автовыгрузка, валка деревьев. */
final class FixGameTests {
    private FixGameTests() {}

    private static net.minecraft.world.level.block.Block block(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("iis", id));
    }

    private static InteractionHand use(GameTestHelper h, BlockPos rel, Direction face, ItemStack stack, boolean sneak) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(sneak);
        BlockPos abs = h.absolutePos(rel);
        BlockState st = h.getLevel().getBlockState(abs);
        st.useItemOn(stack, h.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false));
        return InteractionHand.MAIN_HAND;
    }

    /** Ключ по восточной грани буфера разворачивает его лицом на восток; ключ изнашивается на 20. */
    static void wrenchRotates(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 4);
        h.setBlock(p, LvContent.BATTERY_BUFFER_1X1.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        ItemStack wrench = new ItemStack(SelfTest.item("wrench_steel"));
        use(h, p, Direction.EAST, wrench, false);
        h.assertTrue(h.getBlockState(p).getValue(MachineBlock.FACING) == Direction.EAST, "facing " + h.getBlockState(p).getValue(MachineBlock.FACING));
        h.assertTrue(wrench.getDamageValue() == 20, "wrench damage " + wrench.getDamageValue());
        use(h, p, Direction.UP, wrench, false); // сверху — по часовой
        h.assertTrue(h.getBlockState(p).getValue(MachineBlock.FACING) == Direction.SOUTH, "after top click " + h.getBlockState(p).getValue(MachineBlock.FACING));
        h.succeed();
    }

    /** Корпус/машина дают дроп только с ключом; провод — рукой тоже. */
    static void wrenchOnlyHarvest(GameTestHelper h) {
        BlockState hull = block("hull_lv").defaultBlockState();
        BlockState cable = block("cable_tin_1x").defaultBlockState();
        h.assertTrue(hull.requiresCorrectToolForDrops(), "hull does not require a tool");
        h.assertTrue(new ItemStack(SelfTest.item("wrench_steel")).isCorrectToolForDrops(hull), "wrench cannot harvest the hull");
        h.assertFalse(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(hull), "pickaxe harvests the hull");
        h.assertFalse(cable.requiresCorrectToolForDrops(), "cable needs a tool");
        h.assertTrue(new ItemStack(SelfTest.item("wire_cutter_steel")).getDestroySpeed(cable) > 1.0f, "wire cutter is not fast on cables");
        h.succeed();
    }

    /** Зомби в креозоте двигается (тонет), а не висит на месте. */
    static void creosoteMovable(GameTestHelper h) {
        for (int x = 2; x <= 6; x++) for (int z = 2; z <= 6; z++) for (int y = 1; y <= 3; y++)
            h.setBlock(new BlockPos(x, y, z), IISFluids.CREOSOTE_BLOCK.get());
        var z = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3.5, 4.5));
        double y0 = z.getY();
        h.succeedWhen(() -> h.assertTrue(z.getY() < y0 - 0.5, "zombie stuck at y " + (z.getY() - y0)));
    }

    /** Кнопка автовыгрузки: выход дробителя уходит в сундук сзади. */
    static void autoOutput(GameTestHelper h) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), LvMachineType.MACERATOR, Direction.NORTH);
        h.setBlock(new BlockPos(4, 1, 5), Blocks.CHEST);
        m.setItem(m.type().inputs, new ItemStack(Items.COBBLESTONE, 10));
        m.toggleTransfer(1);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        h.succeedWhen(() -> {
            ChestBlockEntity c = h.getBlockEntity(new BlockPos(4, 1, 5), ChestBlockEntity.class);
            h.assertTrue(c.countItem(Items.COBBLESTONE) == 10, "chest has " + c.countItem(Items.COBBLESTONE));
            h.assertTrue(m.getItem(m.type().inputs).isEmpty(), "output slot not empty");
        });
    }

    /** Топор валит всё дерево; без листвы (постройка) — нет; с Shift — нет. */
    static void treeFeller(GameTestHelper h) {
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(3, y, 3), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(4, 5, 3), leaf);
        h.setBlock(new BlockPos(3, 6, 3), leaf);
        for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(6, y, 6), Blocks.OAK_LOG); // «постройка» без листвы
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        player.setShiftKeyDown(true);
        TreeFeller.onBreak(new BreakBlockEvent(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), Blocks.OAK_LOG.defaultBlockState(), player));
        h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(3, 2, 3));
        player.setShiftKeyDown(false);
        TreeFeller.onBreak(new BreakBlockEvent(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), Blocks.OAK_LOG.defaultBlockState(), player));
        TreeFeller.onBreak(new BreakBlockEvent(h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)), Blocks.OAK_LOG.defaultBlockState(), player));
        for (int y = 2; y <= 5; y++) h.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(3, y, 3));
        h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(6, 2, 6));
        h.assertTrue(player.getMainHandItem().getDamageValue() == 4, "axe damage " + player.getMainHandItem().getDamageValue());
        h.succeed();
    }
}

package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.IISMachines;
import ru.iis.machine.block.ItemPipeBlock;
import ru.iis.machine.block.ItemPipeBlockEntity;

/** Предметные трубы (D123): GT_MetaPipeEntity_Item, IMetaTileEntityItemPipe.Util#scanPipes. */
final class ItemPipeGameTests {
    private ItemPipeGameTests() {}

    private static void pipe(GameTestHelper h, BlockPos p, String id, Direction... sides) {
        BlockState s = IISMachines.ITEM_PIPES.get(id).get().defaultBlockState();
        for (Direction d : sides) s = s.setValue(ItemPipeBlock.PROPS.get(d), true);
        h.setBlock(p, s);
    }

    private static ItemPipeBlockEntity at(GameTestHelper h, BlockPos p) {
        return h.getBlockEntity(p, ItemPipeBlockEntity.class);
    }

    private static ChestBlockEntity chest(GameTestHelper h, BlockPos p) {
        h.setBlock(p, Blocks.CHEST);
        return h.getBlockEntity(p, ChestBlockEntity.class);
    }

    private static int count(ChestBlockEntity c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
        return n;
    }

    /** Вход через обычный инвентарь, как отдают машины: транзакция кладёт порцию, отвергает закрытую сторону, труба доносит до сундука. */
    static void takesFromCapability(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 2, 2), b = a.east();
        pipe(h, a, "item_pipe_medium_brass", Direction.EAST, Direction.WEST);
        pipe(h, b, "item_pipe_medium_brass", Direction.WEST, Direction.EAST);
        ChestBlockEntity box = chest(h, b.east());
        var level = h.getLevel();
        h.assertTrue(level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, h.absolutePos(a), Direction.UP) == null, "closed side has a handler");
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, h.absolutePos(a), Direction.WEST);
        h.assertTrue(handler != null, "no handler");
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int n = net.neoforged.neoforge.transfer.ResourceHandlerUtil.insertStacking(handler, net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(Items.IRON_INGOT)), 16, tx);
            h.assertTrue(n == 16, "inserted " + n);
            tx.commit();
        }
        h.succeedWhen(() -> h.assertTrue(count(box) == 16, "chest " + count(box)));
    }

    /** Полный путь: измельчитель с автовыгрузкой отдаёт пыль в трубу, труба в сундук. */
    static void machineOutputReachesChest(GameTestHelper h) {
        BlockPos m = new BlockPos(1, 2, 2), p = m.east();
        h.setBlock(m, ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.MACERATOR).get().defaultBlockState().setValue(ru.iis.machine.block.MachineBlock.FACING, Direction.WEST));
        var machine = h.getBlockEntity(m, ru.iis.lv.block.ElectricMachineBlockEntity.class);
        pipe(h, p, "item_pipe_medium_brass", Direction.WEST, Direction.EAST);
        ChestBlockEntity box = chest(h, p.east());
        machine.energy.stored = machine.energy.capacity;
        machine.itemTransfer = true;
        machine.setItem(1, new ItemStack(Items.IRON_INGOT, 4));
        h.succeedWhen(() -> h.assertTrue(count(box) == 4, "chest " + count(box) + " machine " + machine.getItem(1)));
    }

    /** Порция из первой трубы приходит в сундук за третьей: между трубами предметы не лежат. */
    static void deliversAlongLine(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 2, 2), b = a.east(), c = b.east(), end = c.east();
        pipe(h, a, "item_pipe_medium_brass", Direction.EAST);
        pipe(h, b, "item_pipe_medium_brass", Direction.WEST, Direction.EAST);
        pipe(h, c, "item_pipe_medium_brass", Direction.WEST, Direction.EAST);
        ChestBlockEntity box = chest(h, end);
        at(h, a).setItem(0, new ItemStack(Items.IRON_INGOT, 32));
        h.succeedWhen(() -> {
            h.assertTrue(count(box) == 32, "chest " + count(box));
            h.assertTrue(at(h, a).isEmpty() && at(h, b).isEmpty() && at(h, c).isEmpty(), "left in pipes");
        });
    }

    /** Развилка: сундук за ограничительной трубой ближе, но предметы идут в обычную ветку (GT mStepSize × 100). */
    static void prefersNormalOverRestrictive(GameTestHelper h) {
        BlockPos fork = new BlockPos(2, 2, 2);
        pipe(h, fork, "item_pipe_medium_brass", Direction.NORTH, Direction.SOUTH);
        BlockPos r = fork.north(), n1 = fork.south(), n2 = n1.south();
        pipe(h, r, "item_pipe_restrictive_medium_brass", Direction.SOUTH, Direction.NORTH);
        pipe(h, n1, "item_pipe_medium_brass", Direction.NORTH, Direction.SOUTH);
        pipe(h, n2, "item_pipe_medium_brass", Direction.NORTH, Direction.SOUTH);
        ChestBlockEntity near = chest(h, r.north()), far = chest(h, n2.south());
        at(h, fork).setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        h.succeedWhen(() -> {
            h.assertTrue(count(far) == 5, "far " + count(far));
            h.assertTrue(count(near) == 0, "near " + count(near));
        });
    }

    /** Когда обычной ветке некуда класть, предметы идут через ограничительную. */
    static void goesRestrictiveWhenFull(GameTestHelper h) {
        BlockPos fork = new BlockPos(2, 2, 2);
        pipe(h, fork, "item_pipe_medium_brass", Direction.NORTH, Direction.SOUTH);
        BlockPos r = fork.north(), n1 = fork.south();
        pipe(h, r, "item_pipe_restrictive_medium_brass", Direction.SOUTH, Direction.NORTH);
        pipe(h, n1, "item_pipe_medium_brass", Direction.NORTH, Direction.SOUTH);
        ChestBlockEntity near = chest(h, r.north()), far = chest(h, n1.south());
        for (int i = 0; i < far.getContainerSize(); i++) far.setItem(i, new ItemStack(Items.STONE, 64));
        at(h, fork).setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        h.succeedWhen(() -> h.assertTrue(count(near) == 5, "near " + count(near)));
    }

    /** Труба не возвращает предмет туда, откуда взяла. */
    static void notBackToSource(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 2, 2);
        pipe(h, p, "item_pipe_medium_brass", Direction.NORTH);
        ChestBlockEntity box = chest(h, p.north());
        ItemPipeBlockEntity e = at(h, p);
        h.assertTrue(e.canPlaceItemThroughFace(0, new ItemStack(Items.IRON_INGOT), Direction.NORTH), "refused");
        e.setItem(0, new ItemStack(Items.IRON_INGOT));
        h.runAtTickTime(60, () -> {
            h.assertTrue(count(box) == 0 && !e.isEmpty(), "went back");
            h.succeed();
        });
    }

    /** Большая труба из латуни: 2 слота — 2 порции за проход; огромная платиновая: 16 слотов, шаг меньше. */
    static void capacityPerSecond(GameTestHelper h) {
        ItemPipeBlock.Spec big = IISMachines.ITEM_PIPES.get("item_pipe_large_brass").get().spec, huge = IISMachines.ITEM_PIPES.get("item_pipe_huge_platinum").get().spec;
        ItemPipeBlock.Spec rest = IISMachines.ITEM_PIPES.get("item_pipe_restrictive_medium_brass").get().spec;
        h.assertTrue(big.slots() == 2 && big.step() == 16384 && big.thickness() == 12, "big " + big);
        h.assertTrue(huge.slots() == 16 && huge.step() == 2048 && huge.thickness() == 14, "huge " + huge);
        h.assertTrue(rest.step() == 3276800 && rest.restrictive(), "restrictive " + rest);
        BlockPos p = new BlockPos(2, 2, 2);
        pipe(h, p, "item_pipe_large_brass", Direction.EAST);
        ChestBlockEntity box = chest(h, p.east());
        ItemPipeBlockEntity e = at(h, p);
        e.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        e.setItem(1, new ItemStack(Items.GOLD_INGOT, 64));
        h.succeedWhen(() -> {
            h.assertTrue(count(box) == 128, "chest " + count(box));
            h.assertTrue(e.isEmpty(), "pipe not empty");
        });
    }

    /** GT allowPutStack: пока труба не пуста, принимает только с той стороны, откуда пришло первое; без соединения — нет. */
    static void acceptsOneSideAtATime(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 2, 2);
        pipe(h, p, "item_pipe_large_brass", Direction.EAST, Direction.WEST);
        ItemPipeBlockEntity e = at(h, p);
        ItemStack s = new ItemStack(Items.IRON_INGOT);
        h.assertTrue(e.canPlaceItemThroughFace(0, s, Direction.EAST), "first side refused");
        e.setItem(0, s.copy());
        h.assertFalse(e.canPlaceItemThroughFace(1, s, Direction.WEST), "second side accepted");
        h.assertFalse(e.canPlaceItemThroughFace(0, s, Direction.EAST), "occupied slot accepted");
        h.assertTrue(e.canPlaceItemThroughFace(1, s, Direction.EAST), "same side refused");
        h.assertFalse(e.canPlaceItemThroughFace(1, s, Direction.UP), "unconnected side accepted");
        h.succeed();
    }

    /** Разрыв на одной из сторон: дальше не идёт. */
    static void disconnectedStopsFlow(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 2, 2), b = a.east();
        pipe(h, a, "item_pipe_medium_brass", Direction.EAST);
        pipe(h, b, "item_pipe_medium_brass", Direction.EAST);
        ChestBlockEntity box = chest(h, b.east());
        at(h, a).setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        h.runAtTickTime(60, () -> {
            h.assertTrue(count(box) == 0 && !at(h, a).isEmpty(), "crossed a cut");
            h.succeed();
        });
    }
}

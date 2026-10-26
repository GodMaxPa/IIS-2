package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import ru.iis.command.SelfTest;
import ru.iis.lv.LvContent;
import ru.iis.lv.block.BatteryBufferBlockEntity;
import ru.iis.lv.item.BatteryItem;
import ru.iis.lv.item.ElectricToolItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.block.Wrench;
import ru.iis.world.TreeFeller;

/** Электроинструменты LV (D78): бур, цепная пила, электроключ — заряд вместо прочности, как в GT5. */
final class ElectricToolGameTests {
    private ElectricToolGameTests() {}

    private static ItemStack tool(String id, long charge) {
        ItemStack s = new ItemStack(SelfTest.item(id));
        s.set(LvContent.MAX_CHARGE.get(), 100000L);
        BatteryItem.setCharge(s, charge);
        return s;
    }

    /** Камень (твёрдость 1,5) стоит буру 75 EU (GT onBlockDestroyed: 1,5 × 50); разряженный бур копает как рука. */
    static void drillMinesStone(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, Blocks.STONE);
        BlockState stone = h.getBlockState(pos);
        ItemStack drill = tool("drill_lv_steel", 100000);
        h.assertTrue(drill.isCorrectToolForDrops(stone) && drill.getDestroySpeed(stone) > 1.0f, "charged drill must mine stone");
        h.assertTrue(drill.isCorrectToolForDrops(Blocks.DIRT.defaultBlockState()), "the drill also digs dirt (shovel blocks)");
        drill.getItem().mineBlock(drill, h.getLevel(), stone, h.absolutePos(pos), player);
        h.assertTrue(BatteryItem.charge(drill) == 100000 - 75, "charge after stone: " + BatteryItem.charge(drill));
        ItemStack empty = tool("drill_lv_steel", 0);
        h.assertTrue(!empty.isCorrectToolForDrops(stone) && empty.getDestroySpeed(stone) == 1.0f, "an empty drill works like a bare hand");
        h.succeed();
    }

    /** Поворот ключом (20 единиц = 1000 GT) тратит у электроключа 1000 EU; разряженный ключ ключом не считается. */
    static void wrenchUsesCharge(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack wrench = tool("wrench_lv_steel", 5000);
        h.assertTrue(Wrench.is(wrench), "a charged LV wrench is a wrench");
        wrench.hurtAndBreak(Wrench.DAMAGE, h.getLevel(), player, i -> {});
        h.assertTrue(BatteryItem.charge(wrench) == 4000, "charge after one turn: " + BatteryItem.charge(wrench));
        h.assertTrue(!Wrench.is(tool("wrench_lv_steel", 0)), "an empty LV wrench must not work");
        h.succeed();
    }

    /** Буфер заряжает электроинструмент, но не берёт из него энергию (инструмент — не батарейка). */
    static void bufferChargesTool(GameTestHelper h) {
        BlockPos bp = new BlockPos(2, 1, 4);
        h.setBlock(bp, LvContent.BATTERY_BUFFER_1X1.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        BatteryBufferBlockEntity b = h.getBlockEntity(bp, BatteryBufferBlockEntity.class);
        b.setItem(0, tool("chainsaw_lv_steel", 0));
        h.onEachTick(() -> b.energy.stored = b.energy.capacity);
        h.succeedWhen(() -> h.assertTrue(BatteryItem.charge(b.getItem(0)) > 0, "chainsaw not charged"));
    }

    /** Сборка: заряд батареи переходит в инструмент (GT_Shaped_Recipe); пила — это пила для крафта и валит деревья. */
    static void craftedToolTakesCharge(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack battery = new ItemStack(LvContent.BATTERY_LITHIUM_LV.get());
        BatteryItem.setCharge(battery, 60000);
        SimpleContainer grid = new SimpleContainer(9);
        grid.setItem(7, battery);
        grid.setItem(2, new ItemStack(SelfTest.item("screwdriver_steel")));
        ItemStack result = tool("chainsaw_lv_steel", 0);
        ElectricToolItem.onCrafted(new PlayerEvent.ItemCraftedEvent(player, result, grid));
        h.assertTrue(BatteryItem.charge(result) == 60000, "crafted charge: " + BatteryItem.charge(result));
        TagKey<Item> saw = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("iis", "tools/saw"));
        h.assertTrue(result.is(saw) && result.is(TreeFeller.FELLERS), "the LV chainsaw is a saw and fells trees");
        h.succeed();
    }
}

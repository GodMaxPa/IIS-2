package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.IIS;
import ru.iis.command.SelfTest;
import ru.iis.machine.BoilerType;
import ru.iis.machine.IISMachines;
import ru.iis.machine.block.BoilerBlockEntity;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;
import ru.iis.multi.logic.Maintenance;
import ru.iis.pollution.Pollution;

/** Электрическая доменная печь, люки, техобслуживание, загрязнение. */
final class MultiGameTests {
    private MultiGameTests() {}

    static BlockState hatch(HatchType t, Direction facing) {
        return MultiContent.HATCHES.get(t).get().defaultBlockState().setValue(HatchBlock.FACING, facing);
    }

    /** Печь 3×3×4: контроллер (4,1,1) лицом на север, середина (4,·,2). energyHatches — 1 или 2. */
    static EbfBlockEntity build(GameTestHelper h, int energyHatches) {
        BlockPos center = new BlockPos(4, 1, 2);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos c = center.offset(dx, 0, dz);
                h.setBlock(c, MultiContent.HEAT_PROOF_CASING.get());
                if (dx != 0 || dz != 0) {
                    h.setBlock(c.above(1), MultiContent.COIL_CUPRONICKEL.get());
                    h.setBlock(c.above(2), MultiContent.COIL_CUPRONICKEL.get());
                }
                h.setBlock(c.above(3), MultiContent.HEAT_PROOF_CASING.get());
            }
        h.setBlock(center.above(3), hatch(HatchType.MUFFLER, Direction.UP));
        h.setBlock(center, hatch(HatchType.ENERGY, Direction.DOWN));
        if (energyHatches > 1) h.setBlock(center.west(), hatch(HatchType.ENERGY, Direction.WEST));
        h.setBlock(center.east(), hatch(HatchType.INPUT_BUS, Direction.EAST));
        h.setBlock(center.south(), hatch(HatchType.OUTPUT_BUS, Direction.SOUTH));
        h.setBlock(center.south().east(), hatch(HatchType.MAINTENANCE, Direction.EAST));
        BlockPos ctrl = center.north();
        h.setBlock(ctrl, MultiContent.EBF.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        EbfBlockEntity e = h.getBlockEntity(ctrl, EbfBlockEntity.class);
        h.onEachTick(() -> {
            for (BlockPos p : new BlockPos[]{center, center.west()}) {
                if (h.getLevel().getBlockEntity(h.absolutePos(p)) instanceof EnergyHatchBlockEntity eh) eh.energy.stored = eh.energy.capacity;
            }
        });
        return e;
    }

    /** Рубин → 3 самородка алюминия + крошечная тёмная зола (1200 K, 100 EU/t, 320 тиков) при двух люках LV. */
    static void ebfRuby(GameTestHelper h) {
        EbfBlockEntity e = build(h, 2);
        e.maintenance().setMask(63);
        ItemBusBlockEntity in = h.getBlockEntity(new BlockPos(5, 1, 2), ItemBusBlockEntity.class);
        ItemBusBlockEntity out = h.getBlockEntity(new BlockPos(4, 1, 3), ItemBusBlockEntity.class);
        in.setItem(0, new ItemStack(SelfTest.item("gem_ruby")));
        h.succeedWhen(() -> {
            int nuggets = 0, ash = 0;
            for (ItemStack s : out.stacks()) {
                if (s.is(SelfTest.item("nugget_aluminium"))) nuggets += s.getCount();
                if (s.is(SelfTest.item("tiny_dust_dark_ash"))) ash += s.getCount();
            }
            h.assertTrue(nuggets == 3 && ash == 1, "output bus " + out.stacks());
            IIS.LOGGER.info("[IIS gametest] ebf ruby -> aluminium at tick {}", h.getTick());
        });
    }

    /** Один люк LV: уровень LV (32 В) — рецепты 100–120 EU/t недоступны, печь стоит. */
    static void ebfOneHatchIdle(GameTestHelper h) {
        EbfBlockEntity e = build(h, 1);
        e.maintenance().setMask(63);
        h.getBlockEntity(new BlockPos(5, 1, 2), ItemBusBlockEntity.class).setItem(0, new ItemStack(SelfTest.item("gem_ruby")));
        h.runAtTickTime(80, () -> {
            h.assertTrue(e.formed(), "structure not formed");
            h.assertTrue(e.maxProgress() == 0, "furnace started with one LV hatch");
            h.succeed();
        });
    }

    /** Новая печь со всеми неисправностями не работает; гаечный ключ по люку обслуживания чинит «трубу». */
    static void ebfMaintenance(GameTestHelper h) {
        EbfBlockEntity e = build(h, 2);
        h.getBlockEntity(new BlockPos(5, 1, 2), ItemBusBlockEntity.class).setItem(0, new ItemStack(SelfTest.item("gem_ruby")));
        MaintenanceHatchBlockEntity m = h.getBlockEntity(new BlockPos(5, 1, 3), MaintenanceHatchBlockEntity.class);
        h.runAtTickTime(40, () -> {
            h.assertTrue(e.maxProgress() == 0, "broken furnace started");
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            ItemStack wrench = new ItemStack(SelfTest.item("wrench_steel"));
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            m.useTool(wrench, player, InteractionHand.MAIN_HAND);
        });
        h.succeedWhen(() -> {
            h.assertTrue(e.maintenance().ok(Maintenance.Problem.WRENCH), "wrench fix not applied");
            h.assertTrue(e.maxProgress() > 0, "furnace with 1 of 6 fixed should run");
        });
    }

    /** Горящий угольный котёл загрязняет свой чанк (20 в секунду). */
    static void boilerPollutes(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 4);
        h.setBlock(p, IISMachines.BOILERS.get(BoilerType.COAL_BRONZE).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        BoilerBlockEntity b = h.getBlockEntity(p, BoilerBlockEntity.class);
        b.setItem(BoilerBlockEntity.SLOT_FUEL, new ItemStack(net.minecraft.world.item.Items.COAL, 8));
        int before = Pollution.get(h.getLevel(), h.absolutePos(p));
        h.succeedWhen(() -> h.assertTrue(Pollution.get(h.getLevel(), h.absolutePos(p)) >= before + 40, "pollution " + Pollution.get(h.getLevel(), h.absolutePos(p))));
    }

    /** Экран контроллера открывается (сервер создаёт меню). */
    static void ebfGuiOpens(GameTestHelper h) {
        build(h, 2);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos ctrl = h.absolutePos(new BlockPos(4, 1, 1));
        var be = h.getLevel().getBlockEntity(ctrl);
        h.assertTrue(be instanceof net.minecraft.world.MenuProvider, "controller is not a menu provider");
        var menu = ((net.minecraft.world.MenuProvider) be).createMenu(1, player.getInventory(), player);
        h.assertTrue(menu instanceof ru.iis.multi.menu.EbfMenu, "menu " + menu);
        h.succeed();
    }
}

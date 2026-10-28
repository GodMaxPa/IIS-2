package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.iis.IIS;
import ru.iis.cover.CoverKind;
import ru.iis.cover.CoverMenu;
import ru.iis.fluid.CapsuleItem;
import ru.iis.fluid.FluidContainers;
import ru.iis.fluid.FluidContent;
import ru.iis.fluid.GuiFluidClick;
import ru.iis.fluid.UniversalTankBlockEntity;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.lv.item.BatteryItem;

/** D101: одноразовые батарейки, капсула по ГУИ, стороны выхода, покрытия по сетке и уровням, ГУИ покрытий, вкладки. */
final class UxGameTests {
    private UxGameTests() {}

    private static ElectricMachineBlockEntity powered(GameTestHelper h, BlockPos p, LvMachineType t) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, p, t, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        return m;
    }

    private static Fluid fluid(String id) {
        return BuiltInRegistries.FLUID.getValue(Identifier.fromNamespaceAndPath(IIS.MODID, id));
    }

    /** Наливщик LV: корпус батарейки + 1000 л ртути → ртутная батарейка LV, заряжена на 32 000 EU (GT Item_01:411). */
    static void fluidCannerMercuryBattery(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.FLUID_CANNER);
        m.setItem(0, new ItemStack(LvContent.BATTERY_HULL_LV.get()));
        m.tank().setAmount(fluid("mercury"), 1000);
        h.succeedWhen(() -> {
            ItemStack out = m.getItem(m.type().inputs);
            h.assertTrue(out.is(LvContent.BATTERY_MERCURY_LV.get()), "output " + out);
            h.assertTrue(BatteryItem.charge(out) == 32000, "charge " + BatteryItem.charge(out));
        });
    }

    /** Одноразовую батарейку не зарядить, перезаряжаемую — можно; значок заряда GT: пустая 1, полная 8. */
    static void singleUseBatteries(GameTestHelper h) {
        BatteryItem acid = LvContent.BATTERY_ACID_LV.get();
        ItemStack s = new ItemStack(acid);
        h.assertTrue(acid.charge(s, 1000) == 0, "single-use battery charged");
        ItemStack re = new ItemStack(LvContent.BATTERY_LV.get());
        h.assertTrue(LvContent.BATTERY_LV.get().charge(re, 1000) > 0, "rechargeable battery not charged");
        h.assertTrue(BatteryItem.chargeIcon(0, 100) == 1 && BatteryItem.chargeIcon(100, 100) == 8, "icon ends");
        h.assertTrue(BatteryItem.chargeIcon(50, 100) == 4, "icon half " + BatteryItem.chargeIcon(50, 100));
        h.succeed();
    }

    /** Капсула воды на курсоре по иконке жидкости рудопромывки: вода в баке, капсула пустая; пустая капсула набирает обратно. */
    static void capsuleGuiClick(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.ORE_WASHER);
        ItemStack full = CapsuleItem.withContent(new ItemStack(FluidContainers.CAPSULE.get()), FluidContent.of(Fluids.WATER, 1000));
        ItemStack empty = GuiFluidClick.result(full, m.tank(), m.outTank());
        h.assertTrue(empty != null && CapsuleItem.content(empty).isEmpty(), "capsule not emptied: " + empty);
        h.assertTrue(m.tank().amount() == 1000, "tank " + m.tank().amount());
        ItemStack back = GuiFluidClick.result(empty, m.tank(), m.outTank());
        h.assertTrue(back != null && CapsuleItem.content(back).amount() == 1000, "capsule not filled: " + back);
        h.assertTrue(m.tank().amount() == 0, "tank after " + m.tank().amount());
        ItemStack bucket = GuiFluidClick.result(new ItemStack(Items.WATER_BUCKET), m.tank(), m.outTank());
        h.assertTrue(bucket != null && bucket.is(Items.BUCKET) && m.tank().amount() == 1000, "bucket " + bucket);
        h.succeed();
    }

    /** Ключ: предметы — на восток, Ctrl + ключ: жидкости — на запад; на заднюю сторону ничего не уходит. */
    static void separateOutputSides(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.ORE_WASHER);
        h.setBlock(new BlockPos(5, 1, 4), Blocks.CHEST);
        h.setBlock(new BlockPos(4, 1, 5), Blocks.CHEST);
        UniversalTankBlockEntity tank = FluidGameTests.tank(h, new BlockPos(3, 1, 4), "universal_tank_bronze");
        h.assertTrue(m.setOutputSide(Direction.EAST, false), "item side not set");
        h.assertTrue(m.setOutputSide(Direction.WEST, true), "fluid side not set");
        h.assertFalse(m.setOutputSide(Direction.NORTH, false), "output on the front");
        m.itemTransfer = m.fluidTransfer = true;
        m.setItem(m.type().inputs, new ItemStack(Items.COBBLESTONE, 10));
        m.outTank().setAmount(Fluids.WATER, 500);
        h.succeedWhen(() -> {
            ChestBlockEntity east = h.getBlockEntity(new BlockPos(5, 1, 4), ChestBlockEntity.class);
            ChestBlockEntity back = h.getBlockEntity(new BlockPos(4, 1, 5), ChestBlockEntity.class);
            h.assertTrue(east.countItem(Items.COBBLESTONE) == 10, "east chest " + east.countItem(Items.COBBLESTONE));
            h.assertTrue(back.countItem(Items.COBBLESTONE) == 0, "back chest got items");
            h.assertTrue(m.outTank().amount() == 0, "out tank " + m.outTank().amount());
            h.assertTrue(tank.tank.amount() == 500, "west tank " + tank.tank.amount());
        });
    }

    /** GT: покрытие ставится на грань по сетке ключа — клик у верхнего края восточной грани ставит его наверх. */
    static void coverGridPlacement(GameTestHelper h) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), LvMachineType.MACERATOR, Direction.NORTH);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(new BlockPos(4, 1, 4));
        ItemStack conv = new ItemStack(LvContent.CONVEYOR_MODULE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, conv);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0.5, 0.4, 0), Direction.EAST, abs, false);
        h.getLevel().getBlockState(abs).useItemOn(conv, h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(m.covers().get(Direction.UP) == CoverKind.CONVEYOR, "not on top: up=" + m.covers().get(Direction.UP) + " east=" + m.covers().get(Direction.EAST));
        h.succeed();
    }

    /** Конвейер MV (GT_Cover_Conveyor(100)) — уровень 1, переносит раньше, чем LV успел бы (400 тиков). */
    static void conveyorMv(GameTestHelper h) {
        ElectricMachineBlockEntity m = powered(h, new BlockPos(4, 1, 4), LvMachineType.MACERATOR);
        h.setBlock(new BlockPos(5, 1, 4), Blocks.CHEST);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(new BlockPos(4, 1, 4));
        ItemStack conv = new ItemStack(LvContent.CONVEYOR_MODULE_MV.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, conv);
        h.getLevel().getBlockState(abs).useItemOn(conv, h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0.5, 0, 0), Direction.EAST, abs, false));
        h.assertTrue(m.covers().get(Direction.EAST) == CoverKind.CONVEYOR && m.covers().tier(Direction.EAST) == 1, "MV conveyor not attached");
        m.setItem(m.type().inputs, new ItemStack(Items.COBBLESTONE, 20));
        h.succeedWhen(() -> {
            ChestBlockEntity c = h.getBlockEntity(new BlockPos(5, 1, 4), ChestBlockEntity.class);
            h.assertTrue(c.countItem(Items.COBBLESTONE) == 20, "chest has " + c.countItem(Items.COBBLESTONE));
        });
    }

    /** Кнопки ГУИ конвейера и помпы (GT_Cover_Conveyor.GUI): ввод, условие, пропуск внутрь; нажатая кнопка недоступна. */
    static void coverGuiButtons(GameTestHelper h) {
        h.assertTrue(CoverMenu.newVar(0, 1) == 1 && CoverMenu.newVar(1, 0) == 0, "import/export");
        h.assertTrue(CoverMenu.newVar(0, 3) == 2 && CoverMenu.newVar(2, 4) == 4 && CoverMenu.newVar(4, 2) == 0, "conditions");
        h.assertTrue(CoverMenu.newVar(1, 5) == 7 && CoverMenu.newVar(9, 6) == 3 && CoverMenu.newVar(7, 3) == 9, "allow input");
        h.assertFalse(CoverMenu.clickable(0, 0), "export pressed but clickable");
        h.assertTrue(CoverMenu.clickable(0, 1) && CoverMenu.clickable(0, 5) && !CoverMenu.clickable(0, 6), "clickable");
        h.assertTrue(CoverMenu.hasGui(CoverKind.PUMP) && CoverMenu.hasGui(CoverKind.SHUTTER) && !CoverMenu.hasGui(CoverKind.ARM), "has gui");
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), LvMachineType.MACERATOR, Direction.NORTH);
        m.covers().attach(Direction.EAST, CoverKind.CONVEYOR);
        m.covers().setData(Direction.EAST, CoverMenu.newVar(m.covers().data(Direction.EAST), 1));
        h.assertFalse(m.covers().letsItemsOut(m, Direction.EAST), "import mode lets items out");
        h.succeed();
    }

    /** Вкладки по напряжению: суффикс уровня, кабели по напряжению, схемы по таблице. */
    static void creativeTabTiers(GameTestHelper h) {
        h.assertTrue(LvContent.tabTier("macerator_lv") == 0 && LvContent.tabTier("macerator_mv") == 1, "machines");
        h.assertTrue(LvContent.tabTier("battery_buffer_2x2_hv") == 2 && LvContent.tabTier("transformer_ev") == 3, "buffers");
        h.assertTrue(LvContent.tabTier("circuit_good") == 1 && LvContent.tabTier("circuit_data") == 3, "circuits");
        h.assertTrue(LvContent.tabTier("cable_tin_1x") == 0, "tin cable " + LvContent.tabTier("cable_tin_1x"));
        h.assertTrue(LvContent.tabTier("treetap") == 0, "treetap");
        h.succeed();
    }
}

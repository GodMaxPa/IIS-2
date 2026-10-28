package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import ru.iis.rail.RailContent;
import ru.iis.rail.RailSpeed;
import ru.iis.rail.block.LogicTrackBlock;
import ru.iis.rail.block.SwitchActuatorBlock;
import ru.iis.rail.block.SwitchTrackBlock;
import ru.iis.rail.block.ElectricFeederBlockEntity;
import ru.iis.rail.cart.ElectricLocomotive;
import ru.iis.rail.cart.Locomotive;
import ru.iis.rail.cart.SteamLocomotive;
import ru.iis.rail.cart.TrainLinks;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Железная дорога Railcraft, заход 1 (D102): скорости путей, режим высокой скорости, стрелки, сцепка, логические пути. */
final class RailGameTests {
    private RailGameTests() {}

    private static Block track(String id) {
        return RailContent.TRACKS.get(id).get();
    }

    /** Ряд путей вдоль X на высоте 1, x от x0 до x1 включительно. */
    private static void line(GameTestHelper h, Block b, int x0, int x1, int z) {
        for (int x = x0; x <= x1; x++) h.setBlock(new BlockPos(x, 1, z), b.defaultBlockState());
    }

    /** Положение вагонетки относительно начала площадки теста. */
    private static Vec3 rel(GameTestHelper h, AbstractMinecart c) {
        return c.position().subtract(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)));
    }

    private static AbstractMinecart cart(GameTestHelper h, double x, double z, double vx, double vz) {
        AbstractMinecart c = h.spawn(EntityType.MINECART, new Vec3(x, 1.0625, z));
        c.setDeltaMovement(vx, 0, vz);
        return c;
    }

    /** Деревянный путь: вагонетка не быстрее 0.12 блока/тик (SpeedControllerSlow). */
    static void woodenTrackIsSlow(GameTestHelper h) {
        line(h, track("track_wooden"), 0, 8, 4);
        AbstractMinecart c = cart(h, 0.5, 4.5, 0.4, 0);
        Vec3 start = c.position();
        h.runAtTickTime(21, () -> {
            double moved = c.position().x - start.x;
            h.assertTrue(moved > 0.5 && moved <= 20 * 0.12 + 0.2, "moved " + moved);
            Double max = RailSpeed.maxSpeed(h.getLevel(), c);
            h.assertTrue(max != null && Math.abs(max - 0.12) < 1e-9, "max " + max);
            h.succeed();
        });
    }

    /** Скоростной путь без разгона ускорителем — предел 0.39, после ускорителя с сигналом — режим высокой скорости и 0.8. */
    static void highSpeedBooster(GameTestHelper h) {
        line(h, track("track_high_speed"), 0, 8, 4);
        h.setBlock(new BlockPos(3, 1, 4), track("track_high_speed_booster").defaultBlockState());
        h.setBlock(new BlockPos(3, 1, 5), Blocks.REDSTONE_BLOCK);
        AbstractMinecart c = cart(h, 1.5, 4.5, 0.39, 0);
        Double before = RailSpeed.maxSpeed(h.getLevel(), c);
        h.assertTrue(before != null && Math.abs(before - 0.39) < 1e-9, "max before " + before);
        h.succeedWhen(() -> {
            h.assertTrue(RailSpeed.isHighSpeed(c), "not in high speed mode");
            Double max = RailSpeed.maxSpeed(h.getLevel(), c);
            h.assertTrue(max != null && max > 0.45, "max " + max);
        });
    }

    /** Разогнанная вагонетка на обычном пути взрывается (MinecartHooks: HighSpeed вне скоростного пути). */
    static void highSpeedOffTrackExplodes(GameTestHelper h) {
        line(h, Blocks.RAIL, 0, 8, 4);
        AbstractMinecart c = cart(h, 2.5, 4.5, 0.6, 0);
        RailSpeed.setHighSpeed(c, true);
        h.succeedWhen(() -> h.assertTrue(c.isRemoved() || !RailSpeed.isHighSpeed(c), "cart still in high speed mode"));
    }

    /** Крестовина: вагонетка, едущая на восток поперёк, проезжает прямо. */
    static void junctionGoesStraight(GameTestHelper h) {
        line(h, Blocks.RAIL, 0, 8, 4);
        h.setBlock(new BlockPos(4, 1, 4), track("track_junction").defaultBlockState());
        for (int z = 2; z <= 6; z++) if (z != 4) h.setBlock(new BlockPos(4, 1, z), Blocks.RAIL);
        AbstractMinecart c = cart(h, 1.5, 4.5, 0.3, 0);
        h.succeedWhen(() -> {
            Vec3 r = rel(h, c);
            h.assertTrue(r.x > 6, "x " + r.x);
            h.assertTrue(Math.abs(r.z - 4.5) < 0.3, "left the line at z " + r.z);
        });
    }

    /** Стрелка (FACING = восток, ответвление на юг): переведённая уводит вагонетку с остряков на юг, с ответвления — на остряки. */
    static void switchRoutes(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 4);
        BlockState s = track("track_switch").defaultBlockState().setValue(SwitchTrackBlock.FACING, Direction.EAST)
                .setValue(SwitchTrackBlock.SHAPE, RailShape.EAST_WEST);
        h.setBlock(p, s);
        BlockState placed = h.getBlockState(p);
        SwitchTrackBlock b = (SwitchTrackBlock) placed.getBlock();
        BlockPos abs = h.absolutePos(p);
        AbstractMinecart fromPoints = cart(h, 4.2, 4.5, 0.2, 0);
        h.assertTrue(b.getRailDirection(placed, h.getLevel(), abs, fromPoints) == RailShape.EAST_WEST, "not switched: should go straight");
        h.setBlock(new BlockPos(4, 1, 3), RailContent.SWITCH_LEVER.get());
        h.useBlock(new BlockPos(4, 1, 3));
        BlockState thrown = h.getBlockState(p);
        h.assertTrue(thrown.getValue(SwitchTrackBlock.SWITCHED), "lever did not throw the switch");
        h.assertTrue(h.getBlockState(new BlockPos(4, 1, 3)).getValue(SwitchActuatorBlock.THROWN), "lever state");
        h.assertTrue(b.getRailDirection(thrown, h.getLevel(), abs, fromPoints) == RailShape.SOUTH_WEST, "switched: " + b.getRailDirection(thrown, h.getLevel(), abs, fromPoints));
        AbstractMinecart fromStraightEnd = cart(h, 4.8, 4.5, -0.2, 0);
        h.assertTrue(b.getRailDirection(thrown, h.getLevel(), abs, fromStraightEnd) == RailShape.EAST_WEST, "from the straight end: straight");
        AbstractMinecart fromBranch = cart(h, 4.5, 4.85, 0, -0.2);
        h.assertTrue(b.getRailDirection(placed, h.getLevel(), abs, fromBranch) == RailShape.SOUTH_WEST, "from the branch: to the points");
        h.succeed();
    }

    /** Сцепка: две сцепленные вагонетки едут вместе, расстояние держится около 1.56. */
    static void linkedCartsMoveTogether(GameTestHelper h) {
        line(h, Blocks.RAIL, 0, 8, 4);
        AbstractMinecart a = cart(h, 3.5, 4.5, 0, 0);
        AbstractMinecart b = cart(h, 1.9, 4.5, 0, 0);
        h.assertTrue(TrainLinks.createLink(a, b), "link failed");
        h.assertTrue(TrainLinks.areLinked(a, b), "not linked");
        a.setDeltaMovement(0.3, 0, 0);
        double bx = b.position().x;
        h.runAtTickTime(10, () -> {
            h.assertTrue(b.position().x > bx + 0.5, "trailing cart did not follow: " + (b.position().x - bx));
            h.assertTrue(a.distanceTo(b) < 3, "distance " + a.distanceTo(b));
            h.succeed();
        });
    }

    /** Блокирующий путь держит вагонетку, красный сигнал отпускает её дальше. */
    static void lockingTrackHoldsAndReleases(GameTestHelper h) {
        line(h, Blocks.RAIL, 0, 8, 4);
        BlockPos lock = new BlockPos(4, 1, 4);
        h.setBlock(lock, track("track_locking").defaultBlockState().setValue(LogicTrackBlock.FACING, Direction.EAST).setValue(LogicTrackBlock.SHAPE, RailShape.EAST_WEST));
        AbstractMinecart c = cart(h, 1.5, 4.5, 0.3, 0);
        h.runAtTickTime(40, () -> {
            h.assertTrue(Math.abs(rel(h, c).x - 4.5) < 0.3, "not held: at " + rel(h, c));
            h.setBlock(new BlockPos(4, 1, 5), Blocks.REDSTONE_BLOCK);
        });
        h.runAtTickTime(80, () -> {
            h.assertTrue(rel(h, c).x >= 6, "not released: at " + rel(h, c));
            h.succeed();
        });
    }

    /** Упор: вагонетку, едущую в брус, останавливает. */
    static void bufferStopStops(GameTestHelper h) {
        line(h, Blocks.RAIL, 0, 4, 4);
        h.setBlock(new BlockPos(5, 1, 4), track("track_buffer_stop").defaultBlockState().setValue(LogicTrackBlock.FACING, Direction.EAST)
                .setValue(LogicTrackBlock.SHAPE, RailShape.EAST_WEST));
        AbstractMinecart c = cart(h, 1.5, 4.5, 0.3, 0);
        h.runAtTickTime(60, () -> {
            double x = rel(h, c).x;
            h.assertTrue(x > 3 && x < 6, "passed the buffer stop or never came: x " + x);
            h.assertTrue(Math.abs(c.getDeltaMovement().x) < 0.05, "still moving " + c.getDeltaMovement().x);
            h.succeed();
        });
    }

    /** Сцепной путь с сигналом сцепляет проходящую вагонетку со стоящей рядом. */
    static void couplerTrackLinks(GameTestHelper h) {
        line(h, Blocks.RAIL, 0, 8, 4);
        h.setBlock(new BlockPos(3, 1, 4), track("track_coupler").defaultBlockState().setValue(LogicTrackBlock.FACING, Direction.EAST)
                .setValue(LogicTrackBlock.SHAPE, RailShape.EAST_WEST));
        h.setBlock(new BlockPos(3, 1, 5), Blocks.REDSTONE_BLOCK);
        AbstractMinecart parked = cart(h, 6.5, 4.5, 0, 0);
        AbstractMinecart moving = cart(h, 1.5, 4.5, 0.25, 0);
        h.succeedWhen(() -> h.assertTrue(TrainLinks.areLinked(parked, moving), "not coupled"));
    }

    // --- заход 2: локомотивы ------------------------------------------------------------------------------------

    private static <L extends Locomotive> L loco(GameTestHelper h, EntityType<L> type, double x, double z) {
        L l = h.spawn(type, new Vec3(x, 1.0625, z));
        l.setHeading(new Vec3(1, 0, 0));
        return l;
    }

    /** Котёл паровоза: с водой и углём на холостом ходу топка горит, нагрев растёт, уголь сгорает. */
    static void steamLocoBoilerHeats(GameTestHelper h) {
        line(h, Blocks.RAIL, 0, 8, 4);
        SteamLocomotive l = loco(h, RailContent.STEAM_LOCO.get(), 4.5, 4.5);
        l.addWater(SteamLocomotive.WATER_CAPACITY);
        l.inventory.setItem(SteamLocomotive.SLOT_BURN, new ItemStack(Items.COAL, 2));
        l.setMode(Locomotive.Mode.IDLE);
        h.runAtTickTime(60, () -> {
            h.assertTrue(l.burning(), "firebox not burning");
            h.assertTrue(l.heat() > 25, "heat " + l.heat());
            h.assertTrue(l.inventory.getItem(SteamLocomotive.SLOT_BURN).getCount() < 2, "coal not used");
            h.succeed();
        });
    }

    /** Паровоз с паром от половины бака едет вперёд и тянет сцепленную вагонетку. */
    static void steamLocoPullsCart(GameTestHelper h) {
        line(h, Blocks.RAIL, 0, 8, 4);
        SteamLocomotive l = loco(h, RailContent.STEAM_LOCO.get(), 2.5, 4.5);
        AbstractMinecart tail = cart(h, 0.9, 4.5, 0, 0);
        h.assertTrue(TrainLinks.createLink(l, tail), "link failed");
        l.addWater(SteamLocomotive.WATER_CAPACITY);
        l.addSteam(SteamLocomotive.STEAM_CAPACITY);
        l.setMode(Locomotive.Mode.RUNNING);
        double tx = tail.position().x;
        h.runAtTickTime(12, () -> {
            h.assertTrue(rel(h, l).x > 3.5, "loco at " + rel(h, l));
            h.assertTrue(tail.position().x > tx + 0.8, "tail did not follow: " + (tail.position().x - tx));
            h.assertTrue(l.steam() < SteamLocomotive.STEAM_CAPACITY, "no steam used");
            h.succeed();
        });
    }

    /** Паровоз берёт топливо из сцепленной сундучной вагонетки (тендера). */
    static void steamLocoTender(GameTestHelper h) {
        line(h, Blocks.RAIL, 0, 8, 4);
        SteamLocomotive l = loco(h, RailContent.STEAM_LOCO.get(), 4.5, 4.5);
        MinecartChest tender = h.spawn(EntityType.CHEST_MINECART, new Vec3(2.9, 1.0625, 4.5));
        tender.setItem(5, new ItemStack(Items.COAL, 10));
        h.assertTrue(TrainLinks.createLink(l, tender), "link failed");
        h.succeedWhen(() -> {
            int n = 0;
            for (int i = SteamLocomotive.SLOT_BURN; i < SteamLocomotive.SLOTS; i++) n += l.inventory.getItem(i).getCount();
            h.assertTrue(n > 0, "no fuel pulled from the tender");
        });
    }

    /** Электровоз на электрическом пути заряжается от фидера и едет. */
    static void electricLocoChargesAndRuns(GameTestHelper h) {
        line(h, track("track_electric"), 0, 8, 4);
        BlockPos feeder = new BlockPos(1, 0, 4);
        h.setBlock(feeder, RailContent.ELECTRIC_FEEDER.get());
        ElectricFeederBlockEntity f = (ElectricFeederBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(feeder));
        f.energy.stored = ElectricFeederBlockEntity.CAPACITY;
        ElectricLocomotive l = loco(h, RailContent.ELECTRIC_LOCO.get(), 1.5, 4.5);
        l.setMode(Locomotive.Mode.IDLE);
        h.assertTrue(l.mode() == Locomotive.Mode.SHUTDOWN, "electric loco has no idle mode");
        h.runAtTickTime(10, () -> {
            h.assertTrue(l.charge() > 0, "not charged");
            h.assertTrue(f.energy.stored < ElectricFeederBlockEntity.CAPACITY, "feeder not drained");
            l.setMode(Locomotive.Mode.RUNNING);
        });
        h.runAtTickTime(22, () -> {
            h.assertTrue(rel(h, l).x > 3, "loco at " + rel(h, l));
            h.succeed();
        });
    }
}

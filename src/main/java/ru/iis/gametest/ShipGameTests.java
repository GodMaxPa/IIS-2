package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import ru.iis.ship.LiftBlock;
import ru.iis.ship.MotherShipPiece;
import ru.iis.ship.ShipContent;
import ru.iis.ship.ShipLayout;
import ru.iis.ship.entity.Brute;
import ru.iis.ship.entity.Crawler;
import ru.iis.ship.entity.Overmind;

/** Материнский корабль (D156): существа, лифт и установка корпуса в мир. */
final class ShipGameTests {
    private ShipGameTests() {}

    /** Существа корабля создаются с заявленным здоровьем; ядро не исчезает. */
    static void creaturesSpawn(GameTestHelper h) {
        Crawler crawler = h.spawn(ShipContent.CRAWLER.get(), new BlockPos(2, 2, 2));
        Brute brute = h.spawn(ShipContent.BRUTE.get(), new BlockPos(4, 2, 4));
        Overmind core = h.spawn(ShipContent.OVERMIND.get(), new BlockPos(6, 2, 6));
        h.assertTrue(crawler.getMaxHealth() == 16.0F, "crawler health " + crawler.getMaxHealth());
        h.assertTrue(brute.getMaxHealth() == 60.0F, "brute health " + brute.getMaxHealth());
        h.assertTrue(core.getMaxHealth() == 400.0F && core.isPersistenceRequired(), "core health " + core.getMaxHealth());
        // структура и её набор размещения загружены из данных: корабль появится в новых чанках и находится командой /locate
        var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("iis", "mother_ship");
        h.assertTrue(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).containsKey(id), "structure iis:mother_ship is not loaded");
        h.assertTrue(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET).containsKey(id), "structure set iis:mother_ship is not loaded");
        crawler.discard();
        brute.discard();
        core.discard();
        h.succeed();
    }

    /** Две площадки лифта в одном столбце находят друг друга; через сплошной блок над площадкой лифт не везёт. */
    static void liftFindsPad(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos low = h.absolutePos(new BlockPos(4, 1, 4)), high = low.above(7);
        l.setBlock(low, ShipContent.LIFT.get().defaultBlockState(), 3);
        l.setBlock(high, ShipContent.LIFT.get().defaultBlockState(), 3);
        h.assertTrue(low.equals(liftFind(l, high, -1)), "down from the upper pad");
        h.assertTrue(high.equals(liftFind(l, low, 1)), "up from the lower pad");
        l.setBlock(low.above(), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(liftFind(l, high, -1) == null, "a blocked pad must not be a target");
        l.setBlock(low.above(), Blocks.AIR.defaultBlockState(), 3);
        l.setBlock(high, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    private static BlockPos liftFind(ServerLevel l, BlockPos from, int dir) {
        return LiftBlock.findPad(l, from, dir);
    }

    /**
     * Часть структуры ставит корпус в мир: в зале второго яруса — главный сундук с таблицей добычи, площадки лифта на местах,
     * обшивка снаружи. Ставим высоко над тестом, по окончании убираем.
     */
    static void pieceBuildsHull(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 60, 0));
        int x0 = o.getX(), y0 = o.getY(), z0 = o.getZ();
        MotherShipPiece piece = new MotherShipPiece(x0, y0, z0, 42L);
        BoundingBox all = piece.getBoundingBox();
        piece.postProcess(l, l.structureManager(), l.getChunkSource().getGenerator(), l.getRandom(), all, new net.minecraft.world.level.ChunkPos(x0 >> 4, z0 >> 4), o);
        try {
            h.assertTrue(l.getBlockState(new BlockPos(x0 + ShipLayout.LIFT_A_X, y0 + ShipLayout.FLOOR_1, z0 + ShipLayout.MID_Z)).is(ShipContent.LIFT.get()), "lift A, tier 1");
            h.assertTrue(l.getBlockState(new BlockPos(x0 + ShipLayout.LIFT_B_X, y0 + ShipLayout.FLOOR_3, z0 + ShipLayout.MID_Z)).is(ShipContent.LIFT.get()), "lift B, tier 3");
            h.assertTrue(l.getBlockState(new BlockPos(x0 + ShipLayout.BOSS_CHEST_X, y0 + ShipLayout.FLOOR_2 + 1, z0 + ShipLayout.MID_Z)).is(Blocks.CHEST), "main chest");
            h.assertTrue(l.getBlockState(new BlockPos(x0 + 24, y0, z0 + 24)).is(ShipContent.PLATING.get()) || l.getBlockState(new BlockPos(x0 + 24, y0, z0 + 24)).is(ShipContent.GROWTH.get()),
                    "hull bottom");
            int cores = l.getEntitiesOfClass(Overmind.class, new net.minecraft.world.phys.AABB(x0, y0, z0, x0 + 48, y0 + 23, z0 + 48)).size();
            h.assertTrue(cores == 1, "one core in the hall: " + cores);
        } finally {
            for (Overmind c : l.getEntitiesOfClass(Overmind.class, new net.minecraft.world.phys.AABB(x0, y0, z0, x0 + 48, y0 + 23, z0 + 48))) c.discard();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = 0; x < ShipLayout.SIZE; x++) for (int y = 0; y < ShipLayout.HEIGHT; y++) for (int z = 0; z < ShipLayout.SIZE; z++) {
                if (ShipLayout.at(42L, x, y, z) != ShipLayout.Kind.NONE) l.setBlock(p.set(x0 + x, y0 + y, z0 + z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        h.succeed();
    }
}

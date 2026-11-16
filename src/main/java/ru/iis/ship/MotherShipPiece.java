package ru.iis.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import ru.iis.IIS;
import ru.iis.ship.entity.Overmind;

/**
 * Единственная часть материнского корабля: ставит клетки {@link ShipLayout} в мир по чанкам, пробивает шахту выхода в пещеры
 * под днищем, разбрасывает наросты заражения по земле вокруг и сажает ядро сверхорганизма в зал второго яруса.
 */
public class MotherShipPiece extends StructurePiece {
    /** Глубина шахты под днищем: выход в пещеры обычного мира. */
    public static final int SHAFT = 14;
    /** Полоса земли вокруг корпуса с наростами заражения. */
    public static final int FRINGE = 8;

    private final int x0, y0, z0;
    private final long seed;

    public MotherShipPiece(int x0, int y0, int z0, long seed) {
        super(ShipContent.PIECE.get(), 0, new BoundingBox(x0 - FRINGE, y0 - SHAFT, z0 - FRINGE,
                x0 + ShipLayout.SIZE - 1 + FRINGE, y0 + ShipLayout.HEIGHT + 8, z0 + ShipLayout.SIZE - 1 + FRINGE));
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.seed = seed;
    }

    public MotherShipPiece(CompoundTag tag) {
        super(ShipContent.PIECE.get(), tag);
        this.x0 = tag.getIntOr("x0", 0);
        this.y0 = tag.getIntOr("y0", 0);
        this.z0 = tag.getIntOr("z0", 0);
        this.seed = tag.getLongOr("seed", 0L);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("x0", x0);
        tag.putInt("y0", y0);
        tag.putInt("z0", z0);
        tag.putLong("seed", seed);
    }

    public static ResourceKey<LootTable> loot(int tier) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(IIS.MODID, "chests/ship_tier" + tier));
    }

    public static final ResourceKey<LootTable> BOSS_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(IIS.MODID, "chests/ship_core"));

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB,
                            ChunkPos chunkPos, BlockPos referencePos) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int minX = Math.max(chunkBB.minX(), x0 - FRINGE), maxX = Math.min(chunkBB.maxX(), x0 + ShipLayout.SIZE - 1 + FRINGE);
        int minZ = Math.max(chunkBB.minZ(), z0 - FRINGE), maxZ = Math.min(chunkBB.maxZ(), z0 + ShipLayout.SIZE - 1 + FRINGE);
        BlockState plating = ShipContent.PLATING.get().defaultBlockState(), deck = ShipContent.DECK.get().defaultBlockState(),
                bulkhead = ShipContent.BULKHEAD.get().defaultBlockState(), light = ShipContent.LIGHT.get().defaultBlockState(),
                growth = ShipContent.GROWTH.get().defaultBlockState(), lift = ShipContent.LIFT.get().defaultBlockState();
        for (int wx = minX; wx <= maxX; wx++) {
            for (int wz = minZ; wz <= maxZ; wz++) {
                int x = wx - x0, z = wz - z0;
                boolean column = false;
                for (int y = 0; y < ShipLayout.HEIGHT; y++) {
                    ShipLayout.Kind k = ShipLayout.at(seed, x, y, z);
                    if (k == ShipLayout.Kind.NONE) continue;
                    column = true;
                    p.set(wx, y0 + y, wz);
                    switch (k) {
                        case AIR -> level.setBlock(p, CAVE_AIR, 2);
                        case PLATING -> level.setBlock(p, plating, 2);
                        case DECK -> level.setBlock(p, deck, 2);
                        case BULKHEAD -> level.setBlock(p, bulkhead, 2);
                        case LIGHT -> level.setBlock(p, light, 2);
                        case GROWTH -> level.setBlock(p, growth, 2);
                        case LIFT -> level.setBlock(p, lift, 2);
                        case CHEST -> createChest(level, chunkBB, random, p.immutable(), loot(ShipLayout.tier(y)), null);
                        case BOSS_CHEST -> createChest(level, chunkBB, random, p.immutable(), BOSS_LOOT, null);
                        case SPAWNER -> {
                            level.setBlock(p, Blocks.SPAWNER.defaultBlockState(), 2);
                            if (level.getBlockEntity(p) instanceof SpawnerBlockEntity s) {
                                s.setEntityId(ShipLayout.tier(y) == 2 ? ShipContent.BRUTE.get() : ShipContent.CRAWLER.get(), random);
                            }
                        }
                        default -> { }
                    }
                }
                // над проломом в носу расчищаем грунт: вход не должен оказаться под землёй
                if (x >= -2 && x <= 9 && z >= 21 && z <= 26) {
                    for (int y = ShipLayout.FLOOR_1 + 1; y < ShipLayout.HEIGHT + 6; y++) {
                        if (ShipLayout.at(seed, x, y, z) == ShipLayout.Kind.NONE) level.setBlock(p.set(wx, y0 + y, wz), CAVE_AIR, 2);
                    }
                }
                // шахта выхода под дырой в днище
                if (x >= ShipLayout.EXIT_X0 && x <= ShipLayout.EXIT_X1 && z >= ShipLayout.EXIT_Z0 && z <= ShipLayout.EXIT_Z1) {
                    for (int y = -SHAFT; y < 0; y++) {
                        p.set(wx, y0 + y, wz);
                        if (!level.isOutsideBuildHeight(p)) level.setBlock(p, CAVE_AIR, 2);
                    }
                }
                // заражённая земля вокруг корпуса (в готовом мире — стенд, тест — карта высот без _WG)
                if (!column && ShipLayout.hash(seed, x, 99, z) % 4 == 0) {
                    int top = level.getHeight(level instanceof net.minecraft.server.level.ServerLevel ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.OCEAN_FLOOR_WG, wx, wz) - 1;
                    p.set(wx, top, wz);
                    if (top > y0 && level.getBlockState(p).isSolidRender() && !level.getBlockState(p).is(plating.getBlock())) level.setBlock(p, growth, 2);
                }
            }
        }
        BlockPos boss = new BlockPos(x0 + ShipLayout.BOSS_X, y0 + ShipLayout.FLOOR_2 + 1, z0 + ShipLayout.BOSS_Z);
        if (chunkBB.isInside(boss)) {
            Overmind core = ShipContent.OVERMIND.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
            if (core != null) {
                core.snapTo(boss.getX() + 0.5, boss.getY(), boss.getZ() + 0.5, 0.0F, 0.0F);
                core.setPersistenceRequired();
                core.finalizeSpawn(level, level.getCurrentDifficultyAt(boss), EntitySpawnReason.STRUCTURE, null);
                level.addFreshEntityWithPassengers(core);
            }
        }
    }
}

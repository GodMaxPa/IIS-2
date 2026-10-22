package ru.iis.bio.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;
import ru.iis.IIS;
import ru.iis.IISConfig;
import ru.iis.bio.BioContent;
import ru.iis.bio.block.FeedingMawBlock;
import ru.iis.bio.world.ShuttleTemplate.Cell;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Личный аварийный челнок игрока (CONCEPT §4, D64, D68): игрок бежал на нём с заражённого материнского корабля. Челнок 10×7×5,
 * один на UUID, в чанке игрока, нос зарыт в землю; вокруг — борозда от падения, обожжённая земля, воронки и обломки (только по
 * естественной местности и не за пределами одного чанка). Внутри, в заражённом отсеке под пробоиной в крыше, — спящее ядро
 * с пастью. Чужие постройки, воду и сундуки не трогаем: площадка с ними отбрасывается.
 */
public final class ShuttleSpawner {
    private ShuttleSpawner() {}

    private static final int MAX_SLOPE = 4, MAX_FILL = 5, RING = 7, SEED_SQUARES = 12;
    private static final List<Cell> CELLS = ShuttleTemplate.cells();

    private enum Terrain { FREE, NATURAL, FORBIDDEN }

    /** Игроки, которым челнок ещё предстоит разбить: осталось попыток (раз в секунду). */
    private static final Map<UUID, Integer> PENDING = new HashMap<>();
    private static final int ATTEMPTS = 120;

    /**
     * При входе челнок ставится не сразу: в новом мире игрок появляется раньше, чем вокруг прогружена местность, и мгновенная
     * попытка не находила площадку (D69). Игрок заносится в очередь, а {@link #onPlayerTick} пробует раз в секунду, пока чанк не готов.
     */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!IISConfig.on(IISConfig.BIO_SHUTTLE)) return;
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (ru.iis.stand.StandData.isStand(level)) return; // стенд-эталон: свой челнок уже стоит
        if (level.getDataStorage().computeIfAbsent(ShuttleData.TYPE).has(player.getUUID())) return;
        PENDING.put(player.getUUID(), ATTEMPTS);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (PENDING.isEmpty() || !(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        Integer left = PENDING.get(player.getUUID());
        if (left == null) return;
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        ShuttleData data = level.getDataStorage().computeIfAbsent(ShuttleData.TYPE);
        if (data.has(player.getUUID())) {
            PENDING.remove(player.getUUID());
            return;
        }
        BlockPos at = place(level, player.blockPosition(), player.getX(), player.getZ(),
                player.getUUID().getLeastSignificantBits() ^ player.getUUID().getMostSignificantBits());
        if (at != null) {
            PENDING.remove(player.getUUID());
            data.put(player.getUUID(), at);
            IIS.LOGGER.info("[IIS] emergency shuttle of {} crashed at {}", player.getName().getString(), at);
            player.sendSystemMessage(Component.translatable("message.iis.bio.shuttle", at.getX(), at.getY(), at.getZ()));
        } else if (left <= 1) {
            PENDING.remove(player.getUUID());
            IIS.LOGGER.warn("[IIS] no site for the emergency shuttle of {} near {}; an operator can use /iis shuttle new elsewhere", player.getName().getString(), player.chunkPosition());
        } else {
            PENDING.put(player.getUUID(), left - 1);
        }
    }

    /** {@code /iis shuttle}: где мой челнок. */
    public static int whereIs(net.minecraft.commands.CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel overworld = src.getServer().overworld();
        BlockPos at = overworld.getDataStorage().computeIfAbsent(ShuttleData.TYPE).position(player.getUUID());
        if (at == null) {
            src.sendFailure(Component.literal("No shuttle has been issued to you yet."));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("message.iis.bio.shuttle", at.getX(), at.getY(), at.getZ()), false);
        return 1;
    }

    /** {@code /iis shuttle new}: забыть прежний челнок игрока и разбить новый рядом с ним (для проверки и на случай, когда места не нашлось). */
    public static int renew(net.minecraft.commands.CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            src.sendFailure(Component.literal("The shuttle can only crash in the Overworld."));
            return 0;
        }
        ShuttleData data = level.getDataStorage().computeIfAbsent(ShuttleData.TYPE);
        long seed = player.getUUID().getLeastSignificantBits() ^ level.getGameTime();
        BlockPos at = place(level, player.blockPosition(), player.getX(), player.getZ(), seed);
        if (at == null) {
            src.sendFailure(Component.literal("No suitable site for a shuttle in this chunk (water, buildings or a steep slope). Move and try again."));
            return 0;
        }
        data.put(player.getUUID(), at);
        src.sendSuccess(() -> Component.translatable("message.iis.bio.shuttle", at.getX(), at.getY(), at.getZ()), true);
        return 1;
    }

    // --- выбор площадки -----------------------------------------------------------------------------------------
    private record Site(int x0, int z0, int rot, boolean mirror, int ground, double cost) {}

    /**
     * Ищет лучшую площадку в чанке вокруг {@code near} (4 поворота × зеркало, перепад не больше {@value #MAX_SLOPE}) и строит челнок.
     *
     * @return позиция ядра или null, если места нет
     */
    public static @Nullable BlockPos place(ServerLevel level, BlockPos near, double px, double pz, long seed) {
        int pcx = near.getX() >> 4, pcz = near.getZ() >> 4;
        // сначала чанк игрока, потом соседние (по удалению); каждый челнок целиком внутри одного загруженного чанка
        for (int ring = 0; ring <= 1; ring++) {
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    if (level.getChunkSource().getChunkNow(pcx + dx, pcz + dz) == null) continue;
                    BlockPos core = placeInChunk(level, new ChunkPos(pcx + dx, pcz + dz), px, pz, seed);
                    if (core != null) return core;
                }
        }
        return null;
    }

    private static @Nullable BlockPos placeInChunk(ServerLevel level, ChunkPos cp, double px, double pz, long seed) {
        Site best = null;
        for (int rot = 0; rot < 4; rot++) {
            int[] ext = ShuttleTemplate.extent(rot);
            for (boolean mirror : new boolean[]{false, true}) {
                for (int x0 = cp.getMinBlockX() + 1; x0 + ext[0] <= cp.getMaxBlockX(); x0++) {
                    for (int z0 = cp.getMinBlockZ() + 1; z0 + ext[1] <= cp.getMaxBlockZ(); z0++) {
                        if (px > x0 - 1.5 && px < x0 + ext[0] + 1.5 && pz > z0 - 1.5 && pz < z0 + ext[1] + 1.5) continue; // не на голове у игрока
                        Site s = evaluate(level, x0, z0, rot, mirror, px, pz);
                        if (s != null && (best == null || s.cost < best.cost)) best = s;
                    }
                }
            }
        }
        if (best == null) return null;
        return build(level, best.x0, best.z0, best.ground, best.rot, best.mirror, px, pz, seed);
    }

    private static BlockPos at(int x0, int z0, int tailY, int rot, boolean mirror, Cell c) {
        int[] t = ShuttleTemplate.transform(c.x(), c.z(), rot, mirror);
        return new BlockPos(x0 + t[0], tailY + c.y() - ShuttleTemplate.sink(c.x()), z0 + t[1]);
    }

    private static @Nullable Site evaluate(ServerLevel level, int x0, int z0, int rot, boolean mirror, double px, double pz) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        Map<Long, Boolean> seen = new HashMap<>();
        for (Cell c : CELLS) {
            int[] t = ShuttleTemplate.transform(c.x(), c.z(), rot, mirror);
            if (seen.put(BlockPos.asLong(x0 + t[0], 0, z0 + t[1]), true) != null) continue;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0 + t[0], z0 + t[1]);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        if (max - min > MAX_SLOPE || min - 3 <= level.getMinY() + 2 || min + ShuttleTemplate.LY + 2 >= level.getMaxY()) return null;
        int ground = min, tailY = ground - 1;
        double cost = (max - min) * 3.0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Cell c : CELLS) {
            BlockPos pos = at(x0, z0, tailY, rot, mirror, c);
            switch (classify(level.getBlockState(pos))) {
                case FORBIDDEN -> {
                    return null;
                }
                case NATURAL -> cost += 1.0;
                default -> { }
            }
            if (c.y() == 0) {
                // опора под днищем: целый блок, при необходимости подсыпем до MAX_FILL
                int d = 1;
                boolean ok = false;
                for (; d <= MAX_FILL + 1; d++) {
                    p.set(pos.getX(), pos.getY() - d, pos.getZ());
                    BlockState below = level.getBlockState(p);
                    if (!below.getFluidState().isEmpty()) return null;
                    if (below.isFaceSturdy(level, p, Direction.UP)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) return null;
                cost += (d - 1) * 0.5;
            }
        }
        cost += Math.hypot(px - (x0 + ShuttleTemplate.extent(rot)[0] / 2.0), pz - (z0 + ShuttleTemplate.extent(rot)[1] / 2.0)) * 0.3;
        return new Site(x0, z0, rot, mirror, ground, cost);
    }

    private static Terrain classify(BlockState s) {
        if (s.isAir()) return Terrain.FREE;
        if (!s.getFluidState().isEmpty()) return Terrain.FORBIDDEN;
        if (s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.SNOW)) return Terrain.FREE;
        if (s.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK)) return Terrain.NATURAL;
        return Terrain.FORBIDDEN;
    }

    // --- постройка ----------------------------------------------------------------------------------------------
    /**
     * Строит челнок с углом (x0, z0) охватывающего прямоугольника, поворотом {@code rot} и отражением; {@code ground} — первая
     * свободная высота над землёй (корма садится в землю на уровне поверхности, нос глубже). Игрок стоит в (px, pz).
     *
     * @return позиция ядра
     */
    public static BlockPos build(ServerLevel level, int x0, int z0, int ground, int rot, boolean mirror, double px, double pz, long seed) {
        RandomSource r = RandomSource.create(seed ^ BlockPos.asLong(x0, ground, z0));
        int tailY = ground - 1;
        ChunkPos cp = new ChunkPos(x0 >> 4, z0 >> 4);
        int[] ext = ShuttleTemplate.extent(rot);
        double cx = x0 + ext[0] / 2.0, cz = z0 + ext[1] / 2.0;

        // 1. над зарытым носом расчищаем землю до поверхности: корпус лежит в яме
        Map<Long, Integer> top = new HashMap<>();
        for (Cell c : CELLS) {
            BlockPos p = at(x0, z0, tailY, rot, mirror, c);
            top.merge(BlockPos.asLong(p.getX(), 0, p.getZ()), p.getY(), Math::max);
        }
        for (Map.Entry<Long, Integer> e : top.entrySet()) {
            BlockPos col = BlockPos.of(e.getKey());
            for (int y = e.getValue() + 1; y <= ground + 2; y++) clearNatural(level, new BlockPos(col.getX(), y, col.getZ()));
        }

        // 2. сам челнок
        BlockPos core = null, maw = null;
        for (Cell c : CELLS) {
            BlockPos p = at(x0, z0, tailY, rot, mirror, c);
            switch (c.kind()) {
                case HULL -> level.setBlock(p, BioContent.SHUTTLE_HULL.get().defaultBlockState(), 3);
                case WINDOW -> level.setBlock(p, BioContent.SHUTTLE_WINDOW.get().defaultBlockState(), 3);
                case ENGINE -> level.setBlock(p, BioContent.SHUTTLE_ENGINE.get().defaultBlockState(), 3);
                case AIR -> {
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
                case CORE -> {
                    level.setBlock(p, BioContent.CORE.get().defaultBlockState(), 3);
                    core = p;
                }
                case MAW -> maw = p;
            }
            // 3. подсыпка под днище, если под ним пустота
            if (c.y() == 0) {
                for (int d = 1; d <= MAX_FILL; d++) {
                    BlockPos below = p.below(d);
                    BlockState bs = level.getBlockState(below);
                    if (bs.isFaceSturdy(level, below, Direction.UP) && !bs.canBeReplaced()) break;
                    if (bs.isAir() || bs.canBeReplaced()) level.setBlock(below, Blocks.DIRT.defaultBlockState(), 3);
                }
            }
        }
        Direction toPlayer = Direction.getApproximateNearest(px - (maw.getX() + 0.5), 0, pz - (maw.getZ() + 0.5));
        if (toPlayer.getAxis().isVertical()) toPlayer = Direction.NORTH;
        level.setBlock(maw, BioContent.MAW.get().defaultBlockState().setValue(FeedingMawBlock.FACING, toPlayer), 3);
        // заражение уже расползлось по обшивке вокруг ядра: спящая колония с первыми клетками ткани
        if (level.getBlockEntity(core) instanceof ru.iis.bio.block.ColonyCoreBlockEntity be) be.seedTissue(SEED_SQUARES);

        // 4. борозда впереди носа (локальный x = -1..-4), расходящаяся воронкой
        int[] depth = {0, 2, 2, 1, 1};
        int[] half = {0, 2, 2, 1, 1};
        for (int k = 1; k <= 4; k++) {
            for (int dz = -half[k]; dz <= half[k]; dz++) {
                int[] t = ShuttleTemplate.transform(-k, 3 + dz, rot, mirror);
                carve(level, cp, x0 + t[0], z0 + t[1], depth[k], r);
            }
        }
        // 5. обожжённая земля кольцом вокруг, вырванная трава
        for (int dx = -RING - ext[0] / 2; dx <= RING + ext[0] / 2; dx++) {
            for (int dz = -RING - ext[1] / 2; dz <= RING + ext[1] / 2; dz++) {
                int x = (int) Math.floor(cx) + dx, z = (int) Math.floor(cz) + dz;
                if (!inChunk(cp, x, z)) continue;
                // расстояние до прямоугольника корпуса
                double ddx = Math.max(Math.max(x0 - x, 0), x - (x0 + ext[0] - 1));
                double ddz = Math.max(Math.max(z0 - z, 0), z - (z0 + ext[1] - 1));
                double dist = Math.hypot(ddx, ddz);
                if (dist > RING) continue;
                double p = 0.9 - dist * 0.11;
                if (r.nextDouble() < p) scorch(level, x, z, r);
                else if (dist < RING - 2) stripPlants(level, x, z);
            }
        }
        // 6. воронки поменьше
        for (int i = 0; i < 3; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 4 + r.nextDouble() * 4;
            int bx = (int) Math.floor(cx + Math.cos(ang) * (rad + ext[0] / 2.0)), bz = (int) Math.floor(cz + Math.sin(ang) * (rad + ext[1] / 2.0));
            int size = 1 + r.nextInt(2);
            for (int dx = -size; dx <= size; dx++)
                for (int dz = -size; dz <= size; dz++) {
                    int x = bx + dx, z = bz + dz;
                    if (!inChunk(cp, x, z) || (x >= x0 - 1 && x < x0 + ext[0] + 1 && z >= z0 - 1 && z < z0 + ext[1] + 1)) continue;
                    if (Math.hypot(dx, dz) <= size + 0.3) carve(level, cp, x, z, Math.hypot(dx, dz) < 1.1 ? 2 : 1, r);
                }
        }
        // 7. обломки обшивки на поверхности
        for (int i = 0; i < 6; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 3 + r.nextDouble() * 6;
            int x = (int) Math.floor(cx + Math.cos(ang) * (rad + ext[0] / 2.0)), z = (int) Math.floor(cz + Math.sin(ang) * (rad + ext[1] / 2.0));
            if (!inChunk(cp, x, z) || (x >= x0 - 1 && x < x0 + ext[0] + 1 && z >= z0 - 1 && z < z0 + ext[1] + 1)) continue;
            BlockPos surface = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.getBlockState(surface).isAir() || classify(level.getBlockState(surface.below())) == Terrain.FORBIDDEN) continue;
            Block b = r.nextInt(6) == 0 ? BioContent.SHUTTLE_ENGINE.get() : BioContent.SHUTTLE_HULL.get();
            level.setBlock(surface, b.defaultBlockState(), 3);
        }
        // 8. выжженная полоса от двигателей позади кормы (локальный x = 10..13)
        for (int k = 0; k < 4; k++)
            for (int dz = -1; dz <= 1; dz++) {
                int[] t = ShuttleTemplate.transform(ShuttleTemplate.LX + k, 3 + dz, rot, mirror);
                int x = x0 + t[0], z = z0 + t[1];
                if (inChunk(cp, x, z)) {
                    BlockPos top1 = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                    if (classify(level.getBlockState(top1)) == Terrain.NATURAL) level.setBlock(top1, scorched(r), 3);
                }
            }
        return core;
    }

    private static boolean inChunk(ChunkPos cp, int x, int z) {
        return x >= cp.getMinBlockX() && x <= cp.getMaxBlockX() && z >= cp.getMinBlockZ() && z <= cp.getMaxBlockZ();
    }

    private static void clearNatural(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return;
        Terrain t = classify(s);
        if (t != Terrain.FORBIDDEN) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    }

    /** Вспаханная ударом земля: только грубая земля и гравий, ничего чужого для биома (D69). */
    private static BlockState scorched(RandomSource r) {
        return r.nextInt(100) < 60 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
    }

    /** Обжигает верхний естественный блок колонки и вырывает растения над ним. */
    private static void scorch(ServerLevel level, int x, int z, RandomSource r) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos top = new BlockPos(x, y, z);
        BlockState s = level.getBlockState(top);
        if (classify(s) == Terrain.NATURAL && s.getFluidState().isEmpty()) {
            level.setBlock(top, scorched(r), 3);
            stripPlants(level, x, z);
        } else if (classify(s) == Terrain.FREE && !s.isAir()) {
            level.setBlock(top, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void stripPlants(ServerLevel level, int x, int z) {
        BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        BlockState s = level.getBlockState(p);
        if (!s.isAir() && (s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    }

    /** Углубление в земле: срезает depth верхних естественных блоков, дно обожжено. */
    private static void carve(ServerLevel level, ChunkPos cp, int x, int z, int depth, RandomSource r) {
        if (!inChunk(cp, x, z)) return;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        for (int d = 0; d < depth; d++) {
            BlockPos p = new BlockPos(x, surface - d, z);
            BlockState s = level.getBlockState(p);
            if (classify(s) == Terrain.FORBIDDEN || !s.getFluidState().isEmpty()) return;
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        for (int y = surface + 1; y <= surface + 2; y++) clearNatural(level, new BlockPos(x, y, z));
        BlockPos floor = new BlockPos(x, surface - depth, z);
        if (classify(level.getBlockState(floor)) == Terrain.NATURAL) level.setBlock(floor, scorched(r), 3);
    }
}

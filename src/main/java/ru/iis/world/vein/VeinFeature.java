package ru.iis.world.vein;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import ru.iis.core.material.MaterialData;
import ru.iis.world.ore.OreBlocks;
import ru.iis.world.ore.OreHost;

import java.util.*;

/**
 * Генерация рудных жил GT5 в мире. Вызывается для каждого чанка (шаг underground_ores).
 *
 * Жила GT5 шире одного чанка (до 16 + 2*size блоков), а генератор мира разрешает писать только в текущий чанк.
 * Поэтому для каждого чанка мы заново (детерминированно) строим все жилы, которые могут его задеть,
 * и ставим только попадающие в него блоки. Построенные жилы кешируются.
 */
public final class VeinFeature extends Feature<NoneFeatureConfiguration> {
    /** Самая большая жила выходит на 32 блока (2 чанка) за центральный чанк; берём с запасом 3 чанка. */
    private static final int REACH_CHUNKS = 3;
    private static final int CACHE_SIZE = 512;

    private final Map<String, List<VeinDefinition>> veinsByDimension = new HashMap<>();
    private final Map<String, List<MaterialData.SmallOre>> smallByDimension = new HashMap<>();
    private final Map<CacheKey, int[]> cache = Collections.synchronizedMap(new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, int[]> e) { return size() > CACHE_SIZE; }
    });
    /** Индексы материалов заполняются один раз в конструкторе и дальше только читаются (генерация многопоточная). */
    private final List<String> materialIndex = new ArrayList<>();
    private final Map<String, Integer> materialIds = new HashMap<>();

    private record CacheKey(long seed, String dimension, int centerX, int centerZ) {}

    public VeinFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
        MaterialData.materials().forEach(m -> { materialIds.put(m.id(), materialIndex.size()); materialIndex.add(m.id()); });
        for (MaterialData.SmallOre so : MaterialData.smallOres()) {
            for (String d : so.dimensions()) smallByDimension.computeIfAbsent(d, k -> new ArrayList<>()).add(so);
        }
        for (VeinDefinition v : MaterialData.veins()) {
            for (String d : v.dimensions()) veinsByDimension.computeIfAbsent(d, k -> new ArrayList<>()).add(v);
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        String dim = dimensionName(level.getLevel().dimension());
        List<VeinDefinition> candidates = veinsByDimension.getOrDefault(dim, List.of());
        ChunkPos chunk = ChunkPos.containing(ctx.origin());
        long seed = level.getSeed();
        boolean placedAny = placeSmallOres(level, dim, chunk, seed);
        if (candidates.isEmpty()) return placedAny;

        int minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ();
        Set<Long> centers = new HashSet<>();
        for (int dx = -REACH_CHUNKS; dx <= REACH_CHUNKS; dx++) {
            for (int dz = -REACH_CHUNKS; dz <= REACH_CHUNKS; dz++) {
                centers.add(ChunkPos.pack(VeinGrid.centerChunk(chunk.x() + dx), VeinGrid.centerChunk(chunk.z() + dz)));
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long packed : centers) {
            ChunkPos center = ChunkPos.unpack(packed);
            int[] blocks = cache.computeIfAbsent(new CacheKey(seed, dim, center.x(), center.z()),
                    k -> buildVein(seed, candidates, center));
            for (int i = 0; i < blocks.length; i += 4) {
                int x = blocks[i], y = blocks[i + 1], z = blocks[i + 2];
                if (x < minX || x > minX + 15 || z < minZ || z > minZ + 15) continue;
                if (level.isOutsideBuildHeight(y)) continue;
                pos.set(x, y, z);
                OreHost host = OreHost.forReplacing(level.getBlockState(pos));
                if (host == null) continue;
                BlockState ore = OreBlocks.get(materialIndex.get(blocks[i + 3]), host);
                if (ore == null) continue;
                level.setBlock(pos, ore, 2);
                placedAny = true;
            }
        }
        return placedAny;
    }

    /**
     * Малые руды (GT_Worldgen_GT_Ore_SmallPieces#executeWorldgen): в каждом чанке для каждой малой руды измерения
     * max(1, amount/2 + rand(amount)/2) вкраплений в случайных точках чанка на высотах [minY, maxY).
     */
    private boolean placeSmallOres(WorldGenLevel level, String dim, ChunkPos chunk, long seed) {
        List<MaterialData.SmallOre> list = smallByDimension.get(dim);
        if (list == null) return false;
        Random r = new Random(seed ^ (chunk.x() * 341873128712L + chunk.z() * 132897987541L) ^ 0x5A11_0E5L);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (MaterialData.SmallOre so : list) {
            String mat = so.material().substring(so.material().indexOf(':') + 1);
            int count = Math.max(1, so.amount() / 2 + r.nextInt(so.amount()) / 2);
            for (int i = 0; i < count; i++) {
                int x = chunk.getMinBlockX() + r.nextInt(16);
                int z = chunk.getMinBlockZ() + r.nextInt(16);
                int y = so.minY() + r.nextInt(Math.max(1, so.maxY() - so.minY()));
                if (level.isOutsideBuildHeight(y)) continue;
                pos.set(x, y, z);
                OreHost host = OreHost.forReplacing(level.getBlockState(pos));
                if (host == null) continue;
                BlockState ore = OreBlocks.getSmall(mat, host);
                if (ore == null) continue;
                level.setBlock(pos, ore, 2);
                placed = true;
            }
        }
        return placed;
    }

    /** Строит жилу квадрата как плоский массив [x, y, z, индексМатериала] * N. */
    private int[] buildVein(long seed, List<VeinDefinition> candidates, ChunkPos center) {
        Random random = VeinGrid.randomFor(seed, center.x(), center.z());
        VeinDefinition vein = VeinGrid.pick(candidates, random);
        if (vein == null) return new int[0];
        int[][] buf = {new int[1024]};
        int[] n = {0};
        VeinShape.generate(vein, center.x() << 4, center.z() << 4, random, (x, y, z, layer, material) -> {
            if (n[0] + 4 > buf[0].length) buf[0] = Arrays.copyOf(buf[0], buf[0].length * 2);
            int[] b = buf[0];
            b[n[0]++] = x; b[n[0]++] = y; b[n[0]++] = z; b[n[0]++] = indexOf(material);
        });
        return Arrays.copyOf(buf[0], n[0]);
    }

    private int indexOf(String material) {
        String id = material.substring(material.indexOf(':') + 1);
        Integer idx = materialIds.get(id);
        if (idx == null) throw new IllegalStateException("Vein uses unknown material " + material);
        return idx;
    }

    static String dimensionName(ResourceKey<Level> key) {
        if (key == Level.OVERWORLD) return "overworld";
        if (key == Level.NETHER) return "nether";
        if (key == Level.END) return "end";
        return key.identifier().toString();
    }
}

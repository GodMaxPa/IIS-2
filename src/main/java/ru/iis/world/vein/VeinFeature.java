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
        for (VeinDefinition v : MaterialData.veins()) {
            for (String d : v.dimensions()) veinsByDimension.computeIfAbsent(d, k -> new ArrayList<>()).add(v);
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        String dim = dimensionName(level.getLevel().dimension());
        List<VeinDefinition> candidates = veinsByDimension.get(dim);
        if (candidates == null || candidates.isEmpty()) return false;

        ChunkPos chunk = ChunkPos.containing(ctx.origin());
        int minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ();
        long seed = level.getSeed();
        Set<Long> centers = new HashSet<>();
        for (int dx = -REACH_CHUNKS; dx <= REACH_CHUNKS; dx++) {
            for (int dz = -REACH_CHUNKS; dz <= REACH_CHUNKS; dz++) {
                centers.add(ChunkPos.pack(VeinGrid.centerChunk(chunk.x() + dx), VeinGrid.centerChunk(chunk.z() + dz)));
            }
        }

        boolean placedAny = false;
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

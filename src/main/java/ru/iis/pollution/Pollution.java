package ru.iis.pollution;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import ru.iis.IISConfig;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Загрязнение GT5 в мире (GT_Pollution): хранится по чанкам в данных измерения, раз в 1200 тиков каждый
 * чанк убывает и растекается; выше порогов (ИИС GregTech.cfg: смог 500 000, отравление 750 000,
 * растения 1 000 000, кислотный дождь 2 000 000) действует на существ и блоки.
 */
public final class Pollution {
    private Pollution() {}

    public static PollutionData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PollutionData.TYPE);
    }

    public static boolean enabled() {
        return IISConfig.on(IISConfig.POLLUTION);
    }

    public static long key(int cx, int cz) {
        return ChunkPos.pack(cx, cz);
    }

    /** GT_Pollution#addPollution для чанка блока. */
    public static void add(ServerLevel level, BlockPos pos, int amount) {
        if (!enabled() || amount == 0) return;
        data(level).add(key(pos.getX() >> 4, pos.getZ() >> 4), amount);
    }

    public static int get(ServerLevel level, BlockPos pos) {
        if (!enabled()) return 0;
        return data(level).get(key(pos.getX() >> 4, pos.getZ() >> 4));
    }

    /** Очередь обхода текущего цикла по измерениям. */
    private static final Map<ServerLevel, long[]> QUEUE = new WeakHashMap<>();
    private static final Map<ServerLevel, int[]> CURSOR = new WeakHashMap<>();

    /** GT_Pollution#tickPollutionInWorld: в нулевой тик цикла список чанков, дальше ~список/1200 + 1 за тик. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || !enabled()) return;
        PollutionData d = data(sl);
        int t = (int) (sl.getGameTime() % PollutionLogic.CYCLE);
        if (t == 0) {
            QUEUE.put(sl, d.chunks());
            CURSOR.put(sl, new int[]{0});
        }
        long[] q = QUEUE.get(sl);
        if (q == null) return;
        int[] c = CURSOR.get(sl);
        int perTick = q.length / PollutionLogic.CYCLE;
        for (int i = 0; i <= perTick && c[0] < q.length; i++) process(sl, d, q[c[0]++]);
    }

    /** Обработать чанк: распад, растекание, эффекты. */
    public static void process(ServerLevel sl, PollutionData d, long k) {
        int p = PollutionLogic.decay(d.get(k));
        if (p > PollutionLogic.SPREAD_THRESHOLD) {
            int cx = ChunkPos.getX(k), cz = ChunkPos.getZ(k);
            long[] nk = {key(cx + 1, cz), key(cx - 1, cz), key(cx, cz + 1), key(cx, cz - 1)};
            int[] nv = new int[4];
            for (int i = 0; i < 4; i++) nv[i] = d.get(nk[i]);
            p = PollutionLogic.spread(p, nv);
            for (int i = 0; i < 4; i++) d.set(nk[i], nv[i]);
            effects(sl, k, p);
        }
        d.set(k, p);
    }

    private static void effect(LivingEntity e, Holder<MobEffect> eff, int duration, int amplifier) {
        if (duration > 0) e.addEffect(new MobEffectInstance(eff, duration, amplifier));
    }

    /** Эффекты чанка с загрязнением p (как в GT5, со «сквозными» ветками switch). */
    static void effects(ServerLevel sl, long k, int p) {
        if (p <= IISConfig.SMOG_LIMIT.getAsInt()) return;
        int cx = ChunkPos.getX(k), cz = ChunkPos.getZ(k);
        if (!sl.hasChunk(cx, cz)) return;
        RandomSource r = sl.getRandom();
        AABB box = new AABB(cx << 4, sl.getMinY(), cz << 4, (cx << 4) + 16, sl.getMaxY(), (cz << 4) + 16);
        List<LivingEntity> ents = sl.getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity e : ents) {
            int dur = Math.min(p / 1000, 1000), amp = p / 400000;
            switch (r.nextInt(3)) {
                default:
                    effect(e, MobEffects.MINING_FATIGUE, dur, amp);
                case 1:
                    effect(e, MobEffects.WEAKNESS, dur, amp);
                case 2:
                    effect(e, MobEffects.SLOWNESS, dur, amp);
            }
        }
        if (p <= IISConfig.POISON_LIMIT.getAsInt()) return;
        for (LivingEntity e : ents) {
            switch (r.nextInt(4)) {
                default:
                    effect(e, MobEffects.HUNGER, p / 500000, 0);
                case 1:
                    effect(e, MobEffects.NAUSEA, Math.min(p / 2000, 1000), 1);
                case 2:
                    effect(e, MobEffects.POISON, Math.min(p / 4000, 1000), p / 500000);
                case 3:
                    effect(e, MobEffects.BLINDNESS, Math.min(p / 2000, 1000), 1);
            }
        }
        if (p <= IISConfig.VEGETATION_LIMIT.getAsInt()) return;
        boolean sour = p > IISConfig.SOUR_RAIN_LIMIT.getAsInt();
        for (int f = 20; f < p / 25000; f++) {
            int x = (cx << 4) + r.nextInt(16), z = (cz << 4) + r.nextInt(16);
            int surface = sl.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            int y = surface + (-f + r.nextInt(f * 2 + 1)) / 4;
            damageBlock(sl, new BlockPos(x, y, z), sour);
        }
    }

    /** GT_Pollution#damageBlock: листва и растения гибнут, трава и земля деградируют, кислотный дождь размывает камень. */
    static void damageBlock(ServerLevel sl, BlockPos pos, boolean sour) {
        BlockState s = sl.getBlockState(pos);
        Block b = s.getBlock();
        if (s.isAir() || b == Blocks.SAND || b == Blocks.DEAD_BUSH) return;
        if (sour && sl.isRaining() && sl.getBlockState(pos.above()).isAir() && sl.canSeeSky(pos.above())) {
            if (b == Blocks.STONE) { sl.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState()); return; }
            if (b == Blocks.COBBLESTONE) { sl.setBlockAndUpdate(pos, Blocks.GRAVEL.defaultBlockState()); return; }
            if (b == Blocks.GRAVEL) { sl.setBlockAndUpdate(pos, Blocks.SAND.defaultBlockState()); return; }
        }
        if (b == Blocks.STONE) return;
        if (s.is(BlockTags.LEAVES)) {
            sl.removeBlock(pos, false);
        } else if (b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS || b == Blocks.FERN || s.is(BlockTags.SAPLINGS)) {
            sl.setBlockAndUpdate(pos, Blocks.DEAD_BUSH.defaultBlockState());
        } else if (b == Blocks.SUGAR_CANE || b == Blocks.VINE || b == Blocks.LILY_PAD || b == Blocks.WHEAT || b == Blocks.CACTUS
                || b == Blocks.MELON || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN || b == Blocks.PUMPKIN_STEM || b == Blocks.CARROTS
                || b == Blocks.POTATOES || b == Blocks.COCOA || s.is(BlockTags.FLOWERS)) {
            sl.destroyBlock(pos, true);
        } else if (b == Blocks.MOSSY_COBBLESTONE) {
            sl.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState());
        } else if (b == Blocks.GRASS_BLOCK) {
            sl.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        } else if (b == Blocks.FARMLAND || b == Blocks.DIRT) {
            sl.setBlockAndUpdate(pos, Blocks.SAND.defaultBlockState());
        }
    }

    /** Для отладки и мультиметра. */
    public static String describe(int p) {
        if (p > IISConfig.SOUR_RAIN_LIMIT.getAsInt()) return "sour_rain";
        if (p > IISConfig.VEGETATION_LIMIT.getAsInt()) return "vegetation";
        if (p > IISConfig.POISON_LIMIT.getAsInt()) return "poison";
        if (p > IISConfig.SMOG_LIMIT.getAsInt()) return "smog";
        return "clean";
    }

    public static ChunkPos chunk(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }
}

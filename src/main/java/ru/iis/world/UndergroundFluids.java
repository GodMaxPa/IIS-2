package ru.iis.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import ru.iis.IIS;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Подземные жидкости GT5-II (GT_UndergroundOil, GT_UO_Dimension, GT_UO_Fluid; D106): в каждом чанке — запас одной жидкости,
 * её вид одинаков в поле 6×6 чанков (зерно мира + измерение + номер поля), запас — случайный между min и max настройки
 * с разбросом ±25 %. Чтение показывает запас / 5000; добыча с коэффициентом k даёт запас × k / 5000 литров и уменьшает
 * запас на «decrease» × k. Настройки — из конфига ИИС 1 (iis/underground_fluids.json); запас чанков хранится с миром.
 */
public final class UndergroundFluids {
    private UndergroundFluids() {}

    public static final int DIVIDER = 5000;
    private static final Random RANDOM = new Random();

    /** Жидкость и количество (0 — чанк истощён); вид жидкости нужен и при нуле. */
    public record Sample(Fluid fluid, int amount) {}

    record UoFluid(Identifier fluid, int chance, int decrease, int max, int min) {
        @Nullable Fluid get() {
            Fluid f = BuiltInRegistries.FLUID.getValue(fluid);
            return f == Fluids.EMPTY ? null : f;
        }

        /** GT_UO_Fluid#getRandomAmount. */
        int randomAmount(Random r) {
            double lo = Math.pow(min * 100.0 * DIVIDER, 0.2), hi = Math.pow(max * 100.0 * DIVIDER, 0.2);
            double amount = Math.pow(lo + (hi == 0 ? 0 : (double) r.nextInt((int) (10000 * (hi - lo) + 1)) / 10000), 5);
            return (int) (amount / 100);
        }
    }

    private static @Nullable Map<String, List<UoFluid>> dims;
    private static final Set<String> BLACKLIST = new HashSet<>();

    private static synchronized Map<String, List<UoFluid>> config() {
        if (dims != null) return dims;
        Map<String, List<UoFluid>> m = new HashMap<>();
        try (InputStream in = UndergroundFluids.class.getResourceAsStream("/iis/underground_fluids.json")) {
            JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("blacklist")) BLACKLIST.add(e.getAsString());
            for (Map.Entry<String, JsonElement> d : o.getAsJsonObject("dimensions").entrySet()) {
                List<UoFluid> l = new ArrayList<>();
                for (JsonElement e : d.getValue().getAsJsonArray()) {
                    JsonObject f = e.getAsJsonObject();
                    l.add(new UoFluid(Identifier.parse(f.get("fluid").getAsString()), f.get("chance").getAsInt(), f.get("decrease").getAsInt(),
                            f.get("max").getAsInt(), f.get("min").getAsInt()));
                }
                m.put(d.getKey(), l);
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot read iis/underground_fluids.json", e);
        }
        dims = m;
        return m;
    }

    /** GT_UO_Dimension#getRandomFluid: взвешенный выбор по Chance. */
    static @Nullable UoFluid randomFluid(List<UoFluid> list, Random r) {
        int maxChance = 0;
        for (UoFluid f : list) maxChance += f.chance();
        if (maxChance <= 0) return null;
        int random = r.nextInt(1000);
        UoFluid last = null;
        for (UoFluid f : list) {
            int chance = f.chance() * 1000 / maxChance;
            if (random <= chance) return f;
            random -= chance;
            last = f;
        }
        return last;
    }

    private static int dimensionId(Level level) {
        if (level.dimension() == Level.OVERWORLD) return 0;
        if (level.dimension() == Level.NETHER) return -1;
        if (level.dimension() == Level.END) return 1;
        return level.dimension().identifier().hashCode();
    }

    /** Только посмотреть (GT undergroundOilReadInformation): запас / 5000, у почти пустого чанка — 0. */
    public static @Nullable Sample read(ServerLevel sl, ChunkPos cp) {
        return access(sl, cp, -1);
    }

    /** Добыть с коэффициентом скорости (GT undergroundOil): null — ничего не добыто. */
    public static @Nullable Sample drain(ServerLevel sl, ChunkPos cp, float coefficient) {
        return access(sl, cp, coefficient);
    }

    /** Для тестов: задать запас чанка (жидкость — та, что выпала полю). Возвращает её или null, если в измерении жидкостей нет. */
    public static @Nullable Fluid prime(ServerLevel sl, ChunkPos cp, int amount) {
        String dim = sl.dimension().identifier().toString();
        if (BLACKLIST.contains(dim) && dims != null) return null;
        Random r = new Random(sl.getSeed() + dimensionId(sl) * 2L + Math.floorDiv(cp.x(), 6) + 7L * Math.floorDiv(cp.z(), 6));
        UoFluid uo = randomFluid(config().getOrDefault(dim, List.of()), r);
        Fluid fluid = uo == null ? null : uo.get();
        if (fluid == null) return null;
        sl.getDataStorage().computeIfAbsent(Data.TYPE).put(cp.pack(), new Cell(uo.fluid().toString(), amount));
        return fluid;
    }

    private static @Nullable Sample access(ServerLevel sl, ChunkPos cp, float coef) {
        String dim = sl.dimension().identifier().toString();
        Map<String, List<UoFluid>> cfg = config();
        if (BLACKLIST.contains(dim)) return null;
        Data data = sl.getDataStorage().computeIfAbsent(Data.TYPE);
        long key = cp.pack();
        Cell cell = data.cells.get(key);
        if (cell != null && cell.amount == 0) {
            Fluid f = cell.fluid();
            return coef >= 0 || f == null ? null : new Sample(f, 0);
        }
        Random r = new Random(sl.getSeed() + dimensionId(sl) * 2L + Math.floorDiv(cp.x(), 6) + 7L * Math.floorDiv(cp.z(), 6));
        UoFluid uo = randomFluid(cfg.getOrDefault(dim, List.of()), r);
        Fluid fluid = uo == null ? null : uo.get();
        if (fluid == null) {
            data.put(key, new Cell("", 0));
            return null;
        }
        String id = uo.fluid().toString();
        int amount;
        if (cell != null && cell.fluidId.equals(id)) {
            amount = cell.amount;
        } else {
            amount = uo.randomAmount(r);
            amount = (int) (amount * (0.75f + RANDOM.nextFloat() / 2f));
        }
        if (coef >= 0) {
            int extracted = (int) Math.floor(amount * (double) coef / DIVIDER);
            double average = uo.decrease() * (double) coef;
            int decrease = (int) Math.ceil(average);
            if (extracted <= 0 || amount <= decrease) {
                data.put(key, new Cell(id, 0));
                return null;
            }
            if (RANDOM.nextFloat() < decrease - average) decrease--;
            data.put(key, new Cell(id, amount - decrease));
            return new Sample(fluid, extracted);
        }
        if (amount <= DIVIDER) {
            data.put(key, new Cell(id, 0));
            return new Sample(fluid, 0);
        }
        data.put(key, new Cell(id, amount));
        return new Sample(fluid, amount / DIVIDER);
    }

    /** Запас чанка; amount 0 — истощён (или жидкости нет). */
    record Cell(String fluidId, int amount) {
        @Nullable Fluid fluid() {
            if (fluidId.isEmpty()) return null;
            Fluid f = BuiltInRegistries.FLUID.getValue(Identifier.parse(fluidId));
            return f == Fluids.EMPTY ? null : f;
        }
    }

    /** Запасы чанков одного измерения. */
    public static class Data extends SavedData {
        private record Entry(long chunk, String fluid, int amount) {
            static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.LONG.fieldOf("c").forGetter(Entry::chunk),
                    Codec.STRING.fieldOf("f").forGetter(Entry::fluid),
                    Codec.INT.fieldOf("a").forGetter(Entry::amount)).apply(i, Entry::new));
        }

        public static final Codec<Data> CODEC = Entry.CODEC.listOf().xmap(Data::new, Data::entries);
        public static final SavedDataType<Data> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(IIS.MODID, "underground_fluids"), Data::new, CODEC);

        final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();

        public Data() {}

        private Data(List<Entry> entries) {
            for (Entry e : entries) cells.put(e.chunk, new Cell(e.fluid, e.amount));
        }

        private List<Entry> entries() {
            List<Entry> r = new ArrayList<>(cells.size());
            cells.long2ObjectEntrySet().forEach(e -> r.add(new Entry(e.getLongKey(), e.getValue().fluidId(), e.getValue().amount())));
            return r;
        }

        void put(long key, Cell c) {
            cells.put(key, c);
            setDirty();
        }
    }
}

package ru.iis.pollution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import ru.iis.IIS;

import java.util.ArrayList;
import java.util.List;

/** Загрязнение по чанкам одного измерения (сохраняется вместе с миром). */
public class PollutionData extends SavedData {
    private record Entry(long chunk, int value) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("c").forGetter(Entry::chunk),
                Codec.INT.fieldOf("p").forGetter(Entry::value)).apply(i, Entry::new));
    }

    public static final Codec<PollutionData> CODEC = Entry.CODEC.listOf().xmap(PollutionData::new, PollutionData::entries);

    public static final SavedDataType<PollutionData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(IIS.MODID, "pollution"), PollutionData::new, CODEC);

    final Long2IntOpenHashMap map = new Long2IntOpenHashMap();

    public PollutionData() {}

    private PollutionData(List<Entry> entries) {
        for (Entry e : entries) if (e.value > 0) map.put(e.chunk, e.value);
    }

    private List<Entry> entries() {
        List<Entry> r = new ArrayList<>(map.size());
        map.long2IntEntrySet().forEach(e -> r.add(new Entry(e.getLongKey(), e.getIntValue())));
        return r;
    }

    public int get(long chunk) {
        return map.get(chunk);
    }

    public void set(long chunk, int value) {
        if (value <= 0) map.remove(chunk);
        else map.put(chunk, value);
        setDirty();
    }

    public void add(long chunk, int amount) {
        set(chunk, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) map.get(chunk) + amount)));
    }

    long[] chunks() {
        return map.keySet().toLongArray();
    }
}

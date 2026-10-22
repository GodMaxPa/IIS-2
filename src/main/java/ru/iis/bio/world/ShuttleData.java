package ru.iis.bio.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import ru.iis.IIS;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Личные аварийные челноки: один на игрока (UUID) в мире. Факт выдачи и координаты хранятся вместе с миром (CONCEPT §4.1). */
public class ShuttleData extends SavedData {
    public static final Codec<ShuttleData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(ShuttleData::new, ShuttleData::asMap);

    public static final SavedDataType<ShuttleData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(IIS.MODID, "bio_shuttles"), ShuttleData::new, CODEC);

    private final Map<UUID, Long> shuttles = new HashMap<>();

    public ShuttleData() {}

    private ShuttleData(Map<String, Long> raw) {
        raw.forEach((k, v) -> {
            try {
                shuttles.put(UUID.fromString(k), v);
            } catch (IllegalArgumentException ignored) {
                // повреждённая запись — пропускаем
            }
        });
    }

    private Map<String, Long> asMap() {
        Map<String, Long> m = new HashMap<>();
        shuttles.forEach((k, v) -> m.put(k.toString(), v));
        return m;
    }

    public boolean has(UUID player) {
        return shuttles.containsKey(player);
    }

    public @Nullable BlockPos position(UUID player) {
        Long l = shuttles.get(player);
        return l == null ? null : BlockPos.of(l);
    }

    public void remove(UUID player) {
        if (shuttles.remove(player) != null) setDirty();
    }

    public void put(UUID player, BlockPos pos) {
        shuttles.put(player, pos.asLong());
        setDirty();
    }
}

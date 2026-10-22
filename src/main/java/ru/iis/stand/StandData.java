package ru.iis.stand;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import ru.iis.IIS;

/** Метка «это стендовый мир» (D74): в нём не разбивается челнок Био при первом входе — эталон не портится. */
public class StandData extends SavedData {
    public static final Codec<StandData> CODEC = Codec.BOOL.xmap(StandData::new, d -> d.stand);
    public static final SavedDataType<StandData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(IIS.MODID, "stand"), StandData::new, CODEC);

    private boolean stand;

    public StandData() {}

    private StandData(boolean stand) {
        this.stand = stand;
    }

    public static boolean isStand(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE).stand;
    }

    public static void mark(ServerLevel level) {
        StandData d = level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
        d.stand = true;
        d.setDirty();
    }
}

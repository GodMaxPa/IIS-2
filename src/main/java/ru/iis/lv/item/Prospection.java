package ru.iis.lv.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import ru.iis.world.UndergroundFluids;
import ru.iis.world.ore.OreBlocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Данные разведки на флешке (D122): GT_MetaTileEntity_SeismicProspector, _AdvSeismicProspector, GT_Utility.ItemNBT.setProspectionData, setAdvancedProspectionData.
 * Сырые данные читать нельзя; анализ в сканере или считывателем (GT convertProspectionData) открывает отчёт.
 * Руды хранятся ключами перевода; малые руды не считаются, как в GT (mMetaData &lt; 16000).
 */
public record Prospection(int x, int y, int z, String dim, int tier, List<String> oils, List<String> near, List<String> middle, List<String> far, boolean analyzed) {
    /** GT: new GT_MetaTileEntity_AdvSeismicProspector(..., aRadius 95, aStep 4); near = radius / 3, чётное; middle = near × 2. */
    public static final int RADIUS = 95, STEP = 4, NEAR = 32, MIDDLE = 64;
    /** GT checkRecipe: 4 ТНТ и 200 тиков; улучшенный — 32 ТНТ и 800. */
    public static final int BASIC_TNT = 4, BASIC_TICKS = 200, ADVANCED_TNT = 32, ADVANCED_TICKS = 800;
    /** GT prospectOils: поле — 6×6 чанков, смотрим 3×3 поля. */
    public static final int FIELD = 6;

    public static final Codec<Prospection> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(Prospection::x), Codec.INT.fieldOf("y").forGetter(Prospection::y), Codec.INT.fieldOf("z").forGetter(Prospection::z),
            Codec.STRING.fieldOf("dim").forGetter(Prospection::dim), Codec.INT.fieldOf("tier").forGetter(Prospection::tier),
            Codec.STRING.listOf().fieldOf("oils").forGetter(Prospection::oils), Codec.STRING.listOf().fieldOf("near").forGetter(Prospection::near),
            Codec.STRING.listOf().fieldOf("middle").forGetter(Prospection::middle), Codec.STRING.listOf().fieldOf("far").forGetter(Prospection::far),
            Codec.BOOL.fieldOf("analyzed").forGetter(Prospection::analyzed)).apply(i, Prospection::new));
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> LIST = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
    public static final StreamCodec<RegistryFriendlyByteBuf, Prospection> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeInt(p.x);
        buf.writeInt(p.y);
        buf.writeInt(p.z);
        buf.writeUtf(p.dim);
        buf.writeVarInt(p.tier);
        LIST.encode(buf, p.oils);
        LIST.encode(buf, p.near);
        LIST.encode(buf, p.middle);
        LIST.encode(buf, p.far);
        buf.writeBoolean(p.analyzed);
    }, buf -> new Prospection(buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf(), buf.readVarInt(), LIST.decode(buf), LIST.decode(buf), LIST.decode(buf), LIST.decode(buf), buf.readBoolean()));

    public Prospection withAnalyzed() {
        return new Prospection(x, y, z, dim, tier, oils, near, middle, far, true);
    }

    /** Сколько взрывчатки нужно (GT checkRecipe), 0 — эта не подходит. Глицерил (жидкость в ячейке) не принимается. */
    public static int explosives(net.minecraft.world.item.ItemStack in, boolean advanced) {
        if (in.is(net.minecraft.world.item.Items.TNT)) return advanced ? ADVANCED_TNT : BASIC_TNT;
        if (in.is(ru.iis.lv.LvContent.INDUSTRIAL_TNT.get())) return advanced ? 16 : 2;
        if (!advanced && in.is(ru.iis.lv.LvContent.DYNAMITE.get())) return 8;
        return 0;
    }

    /** Зона скважины относительно машины: 0 ближняя, 1 средняя, 2 дальняя (GT prospectOres). */
    public static int zone(int dx, int dz) {
        int a = Math.abs(dx), b = Math.abs(dz);
        return a <= NEAR && b <= NEAR ? 0 : a <= MIDDLE && b <= MIDDLE ? 1 : 2;
    }

    /** Ключ перевода руды в этом блоке или null. Обычная руда материала в любой породе — одно имя. */
    public static String oreKey(BlockState s) {
        Block b = s.getBlock();
        if (OreBlocks.isOre(b)) {
            if (OreBlocks.isSmallOre(b)) return null;
            return canonical(b);
        }
        return s.is(Tags.Blocks.ORES) ? b.getDescriptionId() : null;
    }

    private static Map<Block, String> names;

    private static synchronized String canonical(Block b) {
        if (names == null) {
            Map<Block, String> m = new HashMap<>();
            OreBlocks.all().values().forEach(hosts -> {
                if (hosts.isEmpty()) return;
                String key = hosts.values().iterator().next().get().getDescriptionId();
                hosts.values().forEach(d -> m.put(d.get(), key));
            });
            names = m;
        }
        return names.getOrDefault(b, b.getDescriptionId());
    }

    /** GT_MetaTileEntity_SeismicProspector#endProcess: колонна 5×5 от машины вниз и жидкость этого чанка. */
    public static Prospection basic(ServerLevel sl, BlockPos pos) {
        Set<String> ores = new LinkedHashSet<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() - 1; y >= sl.getMinY(); y--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    String key = oreKey(sl.getBlockState(p.set(pos.getX() + dx, y, pos.getZ() + dz)));
                    if (key != null) ores.add(key);
                }
            }
        }
        List<String> oils = new ArrayList<>();
        UndergroundFluids.Sample f = UndergroundFluids.read(sl, ChunkPos.containing(pos));
        if (f != null) oils.add(f.amount() + "|" + f.amount() + "|" + f.fluid().getFluidType().getDescriptionId());
        return new Prospection(pos.getX(), pos.getY(), pos.getZ(), sl.dimension().identifier().toString(), 1, oils, List.copyOf(ores), List.of(), List.of(), false);
    }

    /** GT_MetaTileEntity_AdvSeismicProspector#endProcess: скважины через 4 блока в квадрате 191×191, руды по зонам от частых к редким; 3×3 нефтяных поля. */
    public static Prospection advanced(ServerLevel sl, BlockPos pos) {
        List<Map<String, Integer>> zones = List.of(new HashMap<>(), new HashMap<>(), new HashMap<>());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx += STEP) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += STEP) {
                Map<String, Integer> zone = zones.get(zone(dx, dz));
                for (int y = pos.getY(); y >= sl.getMinY(); y--) {
                    String key = oreKey(sl.getBlockState(p.set(pos.getX() + dx, y, pos.getZ() + dz)));
                    if (key != null) zone.merge(key, 1, Integer::sum);
                }
            }
        }
        return new Prospection(pos.getX(), pos.getY(), pos.getZ(), sl.dimension().identifier().toString(), 4, oilFields(sl, pos), sorted(zones.get(0)), sorted(zones.get(1)), sorted(zones.get(2)), false);
    }

    private static List<String> sorted(Map<String, Integer> m) {
        return m.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey())).map(Map.Entry::getKey).toList();
    }

    private static List<String> oilFields(ServerLevel sl, BlockPos pos) {
        ChunkPos c = ChunkPos.containing(pos);
        int fx = Math.floorDiv(c.x(), FIELD) * FIELD, fz = Math.floorDiv(c.z(), FIELD) * FIELD;
        List<String> out = new ArrayList<>();
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                int min = Integer.MAX_VALUE, max = 0;
                String fluid = null;
                for (int i = 0; i < FIELD; i++) {
                    for (int j = 0; j < FIELD; j++) {
                        UndergroundFluids.Sample f = UndergroundFluids.read(sl, new ChunkPos(fx + i + x * FIELD, fz + j + z * FIELD));
                        if (f == null) continue;
                        min = Math.min(min, f.amount());
                        max = Math.max(max, f.amount());
                        if (fluid == null) fluid = f.fluid().getFluidType().getDescriptionId();
                    }
                }
                out.add(fluid == null ? "0|0|" : min + "|" + max + "|" + fluid);
            }
        }
        return out;
    }

    /** Отчёт (GT convertProspectionData), для чата и подсказки. */
    public List<Component> report() {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable("item.iis.prospection.from", x, y, z, dim));
        if (tier == 1) {
            l.add(Component.translatable("item.iis.prospection.ores"));
            addOres(l, near);
        } else {
            l.add(Component.translatable("item.iis.prospection.near", NEAR));
            addOres(l, near);
            l.add(Component.translatable("item.iis.prospection.middle", MIDDLE));
            addOres(l, middle);
            l.add(Component.translatable("item.iis.prospection.far", RADIUS));
            addOres(l, far);
        }
        int n = 0;
        for (String oil : oils) {
            String[] a = oil.split("\\|", -1);
            n++;
            if (a[2].isEmpty()) l.add(Component.translatable("item.iis.prospection.oil_none", n));
            else l.add(Component.translatable("item.iis.prospection.oil", n, Component.translatable(a[2]), a[0], a[1]));
        }
        return l;
    }

    private static void addOres(List<Component> l, List<String> ores) {
        if (ores.isEmpty()) l.add(Component.literal("  ").append(Component.translatable("item.iis.prospection.none")));
        for (String o : ores) l.add(Component.literal("  ").append(Component.translatable(o)));
    }
}

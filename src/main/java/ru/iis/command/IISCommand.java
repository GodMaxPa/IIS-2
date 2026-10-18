package ru.iis.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import ru.iis.core.material.MaterialData;
import ru.iis.world.ore.OreBlocks;
import ru.iis.world.ore.OreHost;
import ru.iis.world.vein.VeinDefinition;
import ru.iis.world.vein.VeinShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import ru.iis.IIS;
import ru.iis.energy.VoltageTier;

/**
 * /iis — служебная команда: версия мода и таблица напряжений.
 * Нужна, чтобы владелец и автотест могли убедиться, что мод загружен.
 */
public final class IISCommand {
    private IISCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("iis")
                .executes(ctx -> info(ctx.getSource()))
                .then(Commands.literal("voltages").executes(ctx -> voltages(ctx.getSource())))
                .then(Commands.literal("vein")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(MaterialData.veins().stream().map(VeinDefinition::name), b))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> placeVein(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))))
                .then(Commands.literal("selftest")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> SelfTest.run(ctx.getSource())))
                .then(Commands.literal("scan")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> scan(ctx.getSource(), 4))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                                .executes(ctx -> scan(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static int info(CommandSourceStack src) {
        String version = ModList.get().getModContainerById(IIS.MODID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        src.sendSuccess(() -> Component.translatable("iis.command.info", version), false);
        return 1;
    }

    /**
     * /iis scan [радиус в чанках] — подсчёт руд вокруг позиции: руды IIS по материалам и оставшиеся ванильные руды.
     * Нужна для проверки генерации (в том числе автотестом на сервере).
     */
    private static int scan(CommandSourceStack src, int radius) {
        ServerLevel level = src.getLevel();
        ChunkPos center = ChunkPos.containing(BlockPos.containing(src.getPosition()));
        Map<String, Integer> iis = new TreeMap<>();
        Map<String, int[]> heights = new TreeMap<>();
        Map<String, long[]> centroid = new TreeMap<>();
        int vanilla = 0, total = 0, small = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = center.x() - radius; cx <= center.x() + radius; cx++) {
            for (int cz = center.z() - radius; cz <= center.z() + radius; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    for (int y = level.getMinY(); y <= level.getMaxY(); y++) {
                        pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                        BlockState st = chunk.getBlockState(pos);
                        if (st.isAir()) continue;
                        Identifier id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
                        if (id.getNamespace().equals(IIS.MODID) && id.getPath().contains("ore_")) {
                            String mat = id.getPath().substring(id.getPath().indexOf("ore_") + 4);
                            if (id.getPath().contains("small_ore_")) { small++; continue; }
                            iis.merge(mat, 1, Integer::sum);
                            int[] h = heights.computeIfAbsent(mat, k -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
                            h[0] = Math.min(h[0], y); h[1] = Math.max(h[1], y);
                            long[] c = centroid.computeIfAbsent(mat, k -> new long[4]);
                            c[0] += pos.getX(); c[1] += y; c[2] += pos.getZ(); c[3]++;
                            total++;
                        } else if (id.getNamespace().equals("minecraft")
                                && (id.getPath().endsWith("_ore") || id.getPath().startsWith("raw_iron_block") || id.getPath().startsWith("raw_copper_block"))) {
                            vanilla++;
                        }
                    }
                }
            }
        }
        int chunks = (2 * radius + 1) * (2 * radius + 1);
        final int fTotal = total, fVanilla = vanilla, fSmall = small;
        src.sendSuccess(() -> Component.literal("IIS scan: " + chunks + " chunks, IIS ore blocks=" + fTotal
                + ", materials=" + iis.size() + ", small ore blocks=" + fSmall + ", vanilla ore blocks=" + fVanilla), false);
        iis.forEach((mat, n) -> src.sendSuccess(() -> Component.literal("  " + mat + ": " + n
                + " (y " + heights.get(mat)[0] + ".." + heights.get(mat)[1] + ", center "
                + centroid.get(mat)[0] / n + " " + centroid.get(mat)[1] / n + " " + centroid.get(mat)[2] / n + ")"), false));
        return total;
    }

    /**
     * /iis vein <имя> <позиция> — строит жилу по алгоритму GT5 (VeinShape) с низом слоя на высоте позиции.
     * Блоки ставятся только в воздух, поэтому удобно смотреть форму жилы в небе. Отладочная команда.
     */
    private static int placeVein(CommandSourceStack src, String name, BlockPos at) {
        VeinDefinition v = MaterialData.veins().stream().filter(x -> x.name().equals(name)).findFirst().orElse(null);
        if (v == null) {
            src.sendFailure(Component.literal("Unknown vein " + name));
            return 0;
        }
        // Фиксируем высоту: диапазон [y, y+6) даёт низ слоя ровно в y
        VeinDefinition fixed = new VeinDefinition(v.name(), at.getY(), at.getY() + 6, v.weight(), v.density(), v.size(),
                v.primary(), v.secondary(), v.between(), v.sporadic(), v.dimensions());
        ServerLevel level = src.getLevel();
        int[] placed = {0};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        VeinShape.generate(fixed, at.getX() - 8, at.getZ() - 8, new java.util.Random(at.asLong()), (x, y, z, layer, mat) -> {
            pos.set(x, y, z);
            if (!level.getBlockState(pos).isAir()) return;
            BlockState ore = OreBlocks.get(mat.substring(mat.indexOf(':') + 1), OreHost.STONE);
            if (ore != null && level.setBlock(pos, ore, 2)) placed[0]++;
        });
        src.sendSuccess(() -> Component.literal("Placed vein " + name + ": " + placed[0] + " blocks"), true);
        return placed[0];
    }

    private static int voltages(CommandSourceStack src) {
        for (VoltageTier t : VoltageTier.values()) {
            src.sendSuccess(() -> Component.literal(t.shortName() + " — " + t.voltage() + " EU/t"), false);
        }
        return VoltageTier.values().length;
    }
}

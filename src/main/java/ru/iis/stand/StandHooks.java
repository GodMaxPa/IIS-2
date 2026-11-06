package ru.iis.stand;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import ru.iis.IIS;
import ru.iis.lv.block.ElectricBlockEntity;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.multi.block.MultiblockControllerBlockEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Запуск построения стенда: {@code /iis stand} или сервер разработки с {@code -Diis.stand=build}: построить, дать сети поработать, записать, что она делает
 * (строки STAND_CHECK в журнале), отпустить дальние чанки, сохранить и выключиться. Точки съёмки для проверки вида — в файле stand_views.txt.
 */
public final class StandHooks {
    private StandHooks() {}

    /** Сколько тиков сеть работает перед проверкой и сохранением. */
    private static final int RUN_TICKS = 2600;

    public static void onServerStarted(ServerStartedEvent event) {
        if (!"build".equals(System.getProperty("iis.stand"))) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        IIS.LOGGER.info("[IIS] building the stand world at y={}", y0);
        StandBuilder.Result r = StandBuilder.build(level, y0);
        writeViews(r);
        final int[] wait = {RUN_TICKS};
        server.addTickable(() -> {
            int left = wait[0]--;
            if (left == 100) check(level, r);
            if (left == 50) release(level, r);
            if (left == 0) {
                IIS.LOGGER.info("[IIS] stand ready, saving and stopping");
                server.halt(false);
            }
        });
    }

    /** Дальние залы не держат чанки: работают только сеть и Био. */
    private static void release(ServerLevel level, StandBuilder.Result r) {
        for (long key : r.allForced()) {
            if (!r.keep().contains(key)) level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);
        }
    }

    private static void writeViews(StandBuilder.Result r) {
        List<String> out = new ArrayList<>();
        for (StandBuilder.View v : r.views()) out.add(String.format(java.util.Locale.ROOT, "%s;%.1f;%.1f;%.1f;%.1f;%.1f", v.name(), v.x(), v.y(), v.z(), v.yaw(), v.pitch()));
        try {
            Files.write(Path.of("stand_views.txt"), out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            IIS.LOGGER.warn("[IIS] stand: cannot write views", e);
        }
    }

    /** Что на самом деле делает сеть: энергия, ход работы, структуры. */
    private static void check(ServerLevel level, StandBuilder.Result r) {
        int formed = 0;
        List<String> broken = new ArrayList<>();
        for (BlockPos p : r.controllers()) {
            if (level.getBlockEntity(p) instanceof MultiblockControllerBlockEntity m) {
                if (m.formed()) formed++;
                else broken.add(level.getBlockState(p).getBlock().getDescriptionId());
            } else {
                broken.add("other " + level.getBlockState(p).getBlock().getDescriptionId());
            }
        }
        IIS.LOGGER.info("STAND_CHECK structures formed {} of {}, not formed: {}", formed, r.controllers().size(), broken);
        for (BlockPos p : r.network().machines()) {
            BlockEntity be = level.getBlockEntity(p);
            String name = level.getBlockState(p).getBlock().getDescriptionId();
            if (be instanceof ru.iis.machine.block.BoilerBlockEntity b) {
                IIS.LOGGER.info("STAND_CHECK {} temperature {} fuel {}", name, b.logic().temperature, b.getItem(ru.iis.machine.block.BoilerBlockEntity.SLOT_FUEL));
            } else if (be instanceof ElectricMachineBlockEntity m) {
                IIS.LOGGER.info("STAND_CHECK {} energy {}/{} progress {}/{} in {} out {}", name, m.energy.stored, m.energy.capacity, m.logic().progress, m.logic().maxProgress, m.getItem(0), m.getItem(m.type().inputs));
            } else if (be instanceof ElectricBlockEntity e) {
                IIS.LOGGER.info("STAND_CHECK {} energy {}/{} out {} EU/t", name, e.energy.stored, e.energy.capacity, e.avgEuOut());
            }
        }
        if (level.getBlockEntity(r.network().ebf()) instanceof MultiblockControllerBlockEntity e) {
            IIS.LOGGER.info("STAND_CHECK ebf formed {} progress {}/{}", e.formed(), e.progress(), e.maxProgress());
            if (level.getBlockEntity(r.network().ebfInput()) instanceof net.minecraft.world.Container in) IIS.LOGGER.info("STAND_CHECK ebf input bus {}", in.getItem(0));
            if (level.getBlockEntity(r.network().ebfOutput()) instanceof net.minecraft.world.Container out) IIS.LOGGER.info("STAND_CHECK ebf output bus {}", out.getItem(0));
        }
        if (level.getBlockEntity(r.network().overflow()) instanceof ChestBlockEntity c) {
            int n = 0;
            for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
            IIS.LOGGER.info("STAND_CHECK overflow chest holds {}", n);
        }
        IIS.LOGGER.info("STAND_CHECK blocks shown {}, leftovers {}", r.shown(), r.leftovers());
    }

    public static int command(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        int y0 = BlockPos.containing(src.getPosition()).getY();
        StandBuilder.Result r = StandBuilder.build(level, y0);
        release(level, r);
        src.sendSuccess(() -> Component.literal("IIS: стенд построен у (0, " + y0 + ", 0); точка появления — (17, " + y0 + ", 0), залы идут на запад"), true);
        return 1;
    }
}

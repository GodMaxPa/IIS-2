package ru.iis.stand;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import ru.iis.IIS;

/** Запуск построения стенда: {@code /iis stand} или сервер разработки с {@code -Diis.stand=build} (построить, сохранить, выключиться). */
public final class StandHooks {
    private StandHooks() {}

    public static void onServerStarted(ServerStartedEvent event) {
        if (!"build".equals(System.getProperty("iis.stand"))) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        IIS.LOGGER.info("[IIS] building the stand world at y={}", y0);
        StandBuilder.build(level, y0);
        // дать машинам и сосудам несколько секунд поработать, затем сохранить и выключить
        final int[] wait = {200};
        server.addTickable(() -> {
            if (wait[0]-- == 0) {
                IIS.LOGGER.info("[IIS] stand ready, saving and stopping");
                server.halt(false);
            }
        });
    }

    public static int command(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        int y0 = net.minecraft.core.BlockPos.containing(src.getPosition()).getY();
        StandBuilder.build(level, y0);
        src.sendSuccess(() -> Component.literal("IIS: стенд построен у (0, " + y0 + ", 0); точка появления — (30, " + y0 + ", -8)"), true);
        return 1;
    }
}

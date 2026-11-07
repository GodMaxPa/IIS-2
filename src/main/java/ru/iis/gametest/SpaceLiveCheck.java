package ru.iis.gametest;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import ru.iis.space.RocketEntity;
import ru.iis.space.RocketItem;
import ru.iis.space.SpaceContent;

/**
 * Проверка на настоящем сервере: GameTest-сервер не загружает измерения из датапаков. Запускается переменной IIS_SPACE_CHECK=1:
 * ракета в воздухе над Землёй долетает до Луны и садится; на Луне реголит и руда GT. В конце сервер останавливается.
 */
public final class SpaceLiveCheck {
    private SpaceLiveCheck() {}

    /** IIS_SPACE_CHECK=mars: полёт ракеты 2-го уровня на Марс вместо Луны (D131). */
    private static final boolean MARS = "mars".equals(System.getenv("IIS_SPACE_CHECK"));

    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks;

    public static void init() {
        if ("1".equals(System.getenv("IIS_SPACE_CHECK")) || MARS) NeoForge.EVENT_BUS.addListener(SpaceLiveCheck::tick);
    }

    private static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel moon = server.getLevel(MARS ? SpaceContent.MARS : SpaceContent.MOON), earth = server.getLevel(Level.OVERWORLD);
        ticks++;
        if (ticks == 40) {
            if (moon == null) {
                finish(server, "FAILED no moon level");
                return;
            }
            earth.setChunkForced(0, 0, true);
            moon.setChunkForced(0, 0, true);
            RocketEntity r = SpaceContent.ROCKET.get().create(earth, EntitySpawnReason.TRIGGERED);
            r.setPos(0.5, RocketEntity.ARRIVAL_ALTITUDE - 20, 0.5);
            if (MARS) r.setTier(2);
            r.setup(new RocketItem.Stored(18, MARS ? RocketEntity.CAPACITY_T2 : RocketEntity.CAPACITY_T1));
            r.setState(RocketEntity.FLYING);
            earth.addFreshEntity(r);
            LOG.info("SPACE_CHECK rocket launched");
        } else if (ticks > 40 && moon != null && ticks % 20 == 0) {
            var found = moon.getEntitiesOfClass(RocketEntity.class, new AABB(-4, -64, -4, 4, 320, 4));
            if (!found.isEmpty() && found.getFirst().state() == RocketEntity.IDLE) {
                RocketEntity r = found.getFirst();
                int y = moon.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0) - 1;
                String top = BuiltInRegistries.BLOCK.getKey(moon.getBlockState(new BlockPos(0, y, 0)).getBlock()).toString();
                int ores = 0;
                for (int cx = -8; cx <= 8; cx++) {
                    for (int cz = -8; cz <= 8; cz++) {
                        LevelChunk c = moon.getChunk(cx, cz);
                        for (int x = 0; x < 16; x += 1) {
                            for (int z = 0; z < 16; z += 1) {
                                for (int yy = -60; yy < 60; yy += 1) {
                                    if (BuiltInRegistries.BLOCK.getKey(c.getBlockState(new BlockPos(x, yy, z)).getBlock()).getPath().contains("ore_")) ores++;
                                }
                            }
                        }
                    }
                }
                boolean ok = top.equals(MARS ? "iis:mars_turf" : "iis:moon_turf") && r.fuel() > 0 && r.getY() <= y + 2 && ores > 0;
                finish(server, (ok ? "PASSED" : "FAILED") + " surface y=" + y + " top=" + top + " rocket y=" + r.getY() + " fuel=" + r.fuel() + " cargo=" + r.cargoSlots() + " ores=" + ores);
            } else if (ticks > 2400) {
                finish(server, "FAILED no landed rocket after " + ticks + " ticks; on the moon: " + found.size());
            }
        }
    }

    private static void finish(MinecraftServer server, String result) {
        LOG.info("SPACE_CHECK {}", result);
        ticks = Integer.MIN_VALUE;
        server.halt(false);
    }
}

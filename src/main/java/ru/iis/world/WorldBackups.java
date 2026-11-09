package ru.iis.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Бэкап мира каждый игровой день (D144, владелец): раз в 24000 тиков сервер сохраняет мир и пишет его в zip
 * {@code <папка игры>/iis-world-backups/<мир>/}, хранит 20 последних. Работает и без игроков: на выделенном сервере завод идёт сам.
 * Пока пишется архив, автосохранение уровней выключено (как в /save-off), чтобы файлы регионов не менялись на середине.
 */
public final class WorldBackups {
    private WorldBackups() {}

    private static final Logger LOG = LoggerFactory.getLogger("IIS backups");
    public static final long INTERVAL = 24000L;
    public static final int KEEP = 20;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WorldBackups::tick);
    }

    private static void tick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        long time = server.overworld().getGameTime();
        if (time > 0 && time % INTERVAL == 0) start(server, time / INTERVAL);
    }

    public static Path folder(MinecraftServer server) {
        String world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        return FMLPaths.GAMEDIR.get().resolve("iis-world-backups").resolve(world);
    }

    /** Начать бэкап на потоке сервера; архив пишется в отдельном потоке. false — предыдущий ещё идёт. */
    public static boolean start(MinecraftServer server, long day) {
        if (!RUNNING.compareAndSet(false, true)) return false;
        try {
            server.saveEverything(true, true, true);
        } catch (RuntimeException ex) {
            LOG.error("World save before backup failed", ex);
            RUNNING.set(false);
            return false;
        }
        List<ServerLevel> paused = new java.util.ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) {
            if (!l.noSave) {
                l.noSave = true;
                paused.add(l);
            }
        }
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path dir = folder(server);
        Path zip = dir.resolve(STAMP.format(LocalDateTime.now()) + "_day" + day + ".zip");
        Thread t = new Thread(() -> {
            try {
                Files.createDirectories(dir);
                write(root, zip);
                prune(dir);
                LOG.info("World backup written: {}", zip);
            } catch (IOException | RuntimeException ex) {
                LOG.error("World backup failed: {}", zip, ex);
                try { Files.deleteIfExists(zip); } catch (IOException ignored) {}
            } finally {
                server.execute(() -> {
                    paused.forEach(l -> l.noSave = false);
                    RUNNING.set(false);
                });
            }
        }, "IIS world backup");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public static void write(Path root, Path zip) throws IOException {
        Path tmp = zip.resolveSibling(zip.getFileName() + ".part");
        try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream out = new ZipOutputStream(os)) {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().equals("session.lock")) return FileVisitResult.CONTINUE;
                    out.putNextEntry(new ZipEntry(root.relativize(file).toString().replace('\\', '/')));
                    Files.copy(file, out);
                    out.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    LOG.warn("Backup skipped {}: {}", file, exc.toString());
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        Files.move(tmp, zip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Оставить KEEP последних архивов. */
    public static void prune(Path dir) throws IOException {
        List<Path> zips;
        try (Stream<Path> s = Files.list(dir)) {
            zips = s.filter(p -> p.getFileName().toString().endsWith(".zip")).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        for (int i = 0; i < zips.size() - KEEP; i++) Files.deleteIfExists(zips.get(i));
    }
}

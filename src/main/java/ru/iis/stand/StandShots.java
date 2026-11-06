package ru.iis.stand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import ru.iis.IIS;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Снимки стенда для проверки вида (D124): клиент с {@code -Diis.stand.shots=файл} заходит в мир, ставит игрока в каждую точку из файла (имя;x;y;z;yaw;pitch), снимает экран и закрывается.
 * Снимки — в run/screenshots/stand_*.png. Обычной игры не касается: без свойства ничего не подключается.
 */
public final class StandShots {
    private StandShots() {}

    private record Shot(String name, double x, double y, double z, float yaw, float pitch) {}

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int index, wait = 400;

    public static void init() {
        String file = System.getProperty("iis.stand.shots");
        if (file == null) return;
        try {
            for (String line : Files.readAllLines(Path.of(file), StandardCharsets.UTF_8)) {
                String[] a = line.split(";");
                if (a.length >= 6) SHOTS.add(new Shot(a[0], Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]), Float.parseFloat(a[4]), Float.parseFloat(a[5])));
            }
        } catch (IOException e) {
            IIS.LOGGER.warn("[IIS] stand shots: no file", e);
            return;
        }
        NeoForge.EVENT_BUS.addListener(StandShots::tick);
    }

    /** Вопрос при открытии мира (резервная копия, экспериментальные настройки): снимкам ответ безразличен. */
    private static void proceed(net.minecraft.client.gui.screens.Screen screen) {
        String name = screen.getClass().getSimpleName();
        if (!name.equals(lastScreen)) {
            lastScreen = name;
            IIS.LOGGER.info("[IIS] stand shots: screen {}", name);
        }
        if (screen == pressed) return;
        for (var child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && List.of("selectWorld.backupJoinSkipButton", "gui.proceed", "gui.yes").contains(t.getKey())) {
                pressed = screen;
                b.onPress((net.minecraft.client.input.InputWithModifiers) null);
                return;
            }
        }
    }

    private static String lastScreen = "";
    private static net.minecraft.client.gui.screens.Screen pressed;

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (mc.screen != null) proceed(mc.screen);
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null) return;
        mc.options.hideGui = true;
        if (--wait > 0) return;
        if (index > 0 && index <= SHOTS.size() * 2 && index % 2 == 0) {
            String name = "stand_" + (index / 2 - 1) + ".png";
            Screenshot.grab(new File(mc.gameDirectory.getPath()), name, mc.getMainRenderTarget(), 1, c -> {});
        }
        if (index >= SHOTS.size() * 2) {
            mc.stop();
            return;
        }
        if (index % 2 == 0) {
            Shot s = SHOTS.get(index / 2);
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null) {
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    p.teleportTo(p.level(), s.x(), s.y(), s.z(), Set.of(), s.yaw(), s.pitch(), true);
                }
            });
            wait = 100;
        } else {
            wait = 2;
        }
        index++;
    }
}

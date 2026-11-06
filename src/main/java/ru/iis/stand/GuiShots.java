package ru.iis.stand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import ru.iis.IIS;
import ru.iis.machine.client.MachineScreen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Проверка окон машин: клиент с {@code -Diis.gui.shots=файл} ставит каждый блок ИИС с окном, щёлкает по нему как игрок, записывает экран, текстуру и слоты
 * в файл и снимает по одному кадру на каждый вид окна (screenshots/gui_*.png). Обычной игры не касается: без свойства ничего не подключается.
 */
public final class GuiShots {
    private GuiShots() {}

    private static final BlockPos POS = new BlockPos(0, 120, 0);
    private static final List<Block> BLOCKS = new ArrayList<>();
    private static final Set<String> SEEN = new HashSet<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static Path file;
    private static int index, step, wait = 500;
    private static boolean started;
    private static String shotName;

    public static void init() {
        String f = System.getProperty("iis.gui.shots");
        if (f == null) return;
        file = Path.of(f);
        NeoForge.EVENT_BUS.addListener(GuiShots::tick);
    }

    private static void collect() {
        for (Block b : BuiltInRegistries.BLOCK) {
            if (!BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals(IIS.MODID) || !(b instanceof EntityBlock eb)) continue;
            try {
                BlockEntity be = eb.newBlockEntity(POS, b.defaultBlockState());
                if (be instanceof MenuProvider) BLOCKS.add(b);
            } catch (RuntimeException e) {
                REPORT.add("ERR " + BuiltInRegistries.BLOCK.getKey(b) + " " + e);
            }
        }
        IIS.LOGGER.info("[IIS] gui shots: {} blocks with a menu", BLOCKS.size());
    }

    private static String describe(Block block, AbstractContainerScreen<?> s) {
        StringBuilder b = new StringBuilder();
        b.append(BuiltInRegistries.BLOCK.getKey(block)).append('|').append(s.getClass().getSimpleName()).append('|');
        b.append(s instanceof MachineScreen<?> m ? m.textureId().toString() : "-").append('|').append(s.getTitle().getString()).append('|');
        List<String> slots = new ArrayList<>();
        for (Slot slot : s.getMenu().slots) {
            if (slot.container == Minecraft.getInstance().player.getInventory()) continue;
            slots.add(slot.x + "," + slot.y);
        }
        return b.append(String.join(";", slots)).toString();
    }

    private static void finish(Minecraft mc) {
        try {
            Files.write(file, REPORT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            IIS.LOGGER.warn("[IIS] gui shots: cannot write the report", e);
        }
        mc.stop();
    }

    private static final String[][] GEAR = {
            {"nano", "nano_helmet", "nano_chestplate", "nano_leggings", "nano_boots"}, {"quantum", "quantum_helmet", "quantum_chestplate", "quantum_leggings", "quantum_boots"},
            {"hazmat", "hazmat_helmet", "hazmat_chestplate", "hazmat_leggings", "rubber_boots"}, {"jetpack", "nightvision_goggles", "jetpack", "", ""}, {"batpack", "", "batpack", "", ""},
            {"lappack", "", "lappack", "", ""}};
    private static int gearIndex, gearStep;

    /** Снаряжение на самом игроке: спереди и сзади (снимки gear_*.png). */
    private static void gear(Minecraft mc) {
        if (gearIndex >= GEAR.length) {
            finish(mc);
            return;
        }
        String[] set = GEAR[gearIndex];
        File dir = new File(mc.gameDirectory.getPath());
        switch (gearStep) {
            case 0 -> {
                var server = mc.getSingleplayerServer();
                var uuid = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                    if (p == null) return;
                    net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
                    for (int i = 0; i < 4; i++) {
                        p.setItemSlot(slots[i], set[i + 1].isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY
                                : new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(IIS.MODID, set[i + 1]))));
                    }
                    p.teleportTo(p.level(), POS.getX() + 0.5, POS.getY() + 2, POS.getZ() + 0.5, Set.of(), 200, 10, true);
                });
                mc.options.hideGui = true;
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                gearStep = 1;
                wait = 10;
            }
            case 1 -> {
                Screenshot.grab(dir, "gear_" + set[0] + "_front.png", mc.getMainRenderTarget(), 1, c -> {});
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                gearStep = 2;
                wait = 4;
            }
            default -> {
                Screenshot.grab(dir, "gear_" + set[0] + "_back.png", mc.getMainRenderTarget(), 1, c -> {});
                gearStep = 0;
                gearIndex++;
                wait = 2;
            }
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
            REPORT.add("DISCONNECTED at " + (index < BLOCKS.size() ? BuiltInRegistries.BLOCK.getKey(BLOCKS.get(index)) : "end"));
            finish(mc);
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            if (mc.screen != null) StandShots.proceed(mc.screen);
            return;
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (--wait > 0) return;
        if (!started) {
            started = true;
            collect();
        }
        if (index >= BLOCKS.size()) {
            gear(mc);
            return;
        }
        Block block = BLOCKS.get(index);
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        switch (step) {
            case 0 -> {
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                    if (p == null) return;
                    ServerLevel level = p.level();
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    p.teleportTo(level, POS.getX() + 0.5, POS.getY() + 2, POS.getZ() + 0.5, Set.of(), 0, 90, true);
                    level.setBlock(POS, block.defaultBlockState(), 2 | 16);
                });
                step = 1;
                wait = 4;
            }
            case 1 -> {
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                    if (p == null) return;
                    try {
                        p.level().getBlockState(POS).useWithoutItem(p.level(), p, new BlockHitResult(Vec3.atCenterOf(POS), Direction.UP, POS, false));
                    } catch (RuntimeException e) {
                        REPORT.add("CRASH " + BuiltInRegistries.BLOCK.getKey(block) + " " + e);
                    }
                });
                step = 2;
                wait = 6;
            }
            case 2 -> {
                if (mc.screen instanceof AbstractContainerScreen<?> s) {
                    String line = describe(block, s);
                    REPORT.add(line);
                    String key = line.substring(line.indexOf('|') + 1).replaceAll("\\|[^|]*\\|", "|");
                    if (s instanceof MachineScreen<?> m) key = s.getClass().getSimpleName() + m.textureId() + s.getMenu().slots.size();
                    else key = s.getClass().getSimpleName() + s.getMenu().slots.size() + (s.getClass().getSimpleName().startsWith("Bio") ? block.toString() : "");
                    if (SEEN.add(key)) {
                        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
                        Screenshot.grab(new File(mc.gameDirectory.getPath()), "gui_" + name + ".png", mc.getMainRenderTarget(), 1, c -> {});
                        if (s.getMenu() instanceof ru.iis.multi.menu.ControllerMenu || s.getMenu() instanceof ru.iis.multi.menu.EbfMenu) {
                            // как выглядит собранная: данные только на экране этого снимка
                            int[] sample = {1, ru.iis.multi.logic.Maintenance.IDEAL == 6 ? 0b101111 : 0, 10000, 240, 400, 7680, 3600, 512, 1, 0, 1500, 1800, 1, 1, 1};
                            for (int i = 0; i < sample.length; i++) {
                                try {
                                    s.getMenu().setData(i, sample[i]);
                                } catch (RuntimeException ignored) {
                                    break;
                                }
                            }
                            shotName = "guif_" + name + ".png";
                            step = 20;
                            wait = 3;
                            return;
                        }
                    }
                } else {
                    REPORT.add("NOSCREEN " + BuiltInRegistries.BLOCK.getKey(block) + " " + (mc.screen == null ? "null" : mc.screen.getClass().getSimpleName()));
                }
                step = 3;
                wait = 1;
            }
            case 20 -> {
                Screenshot.grab(new File(mc.gameDirectory.getPath()), shotName, mc.getMainRenderTarget(), 1, c -> {});
                step = 3;
                wait = 1;
            }
            default -> {
                if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
                mc.setScreen(null);
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                    if (p != null) p.level().setBlock(POS, Blocks.AIR.defaultBlockState(), 2 | 16);
                });
                step = 0;
                wait = 1;
                index++;
            }
        }
    }
}

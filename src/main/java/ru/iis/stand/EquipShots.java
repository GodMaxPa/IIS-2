package ru.iis.stand;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import ru.iis.IIS;
import ru.iis.equipment.EquipmentContent;
import ru.iis.equipment.Weapon;
import ru.iis.equipment.vehicle.Driveable;
import ru.iis.equipment.vehicle.VehicleKind;
import ru.iis.rail.RailContent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Съёмка снаряжения: клиент с {@code -Diis.equip.shots=1} сам снимает машины, поезда, оружие в руке и броню (screenshots/eq_*.png), садится в машину и стреляет.
 * Обычной игры не касается: без свойства ничего не подключается.
 */
public final class EquipShots {
    private EquipShots() {}

    private static final BlockPos POS = new BlockPos(0, 120, 0);
    private record Step(int delay, Consumer<Minecraft> action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index, wait = 500;
    private static boolean built;

    public static void init() {
        if (System.getProperty("iis.quest.shots") != null) {
            NeoForge.EVENT_BUS.addListener(EquipShots::questTick);
            return;
        }
        if (System.getProperty("iis.equip.shots") == null) return;
        NeoForge.EVENT_BUS.addListener(EquipShots::tick);
    }

    private static int questStep;

    /** Глава квестов D129 в настоящем окне книги: FTB Quests подключён только в разработке и в игре, поэтому через отражение. */
    private static void questTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            if (mc.screen != null) StandShots.proceed(mc.screen);
            return;
        }
        if (--wait > 0) return;
        wait = 100;
        try {
            Class<?> file = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile");
            switch (questStep++) {
                case 0 -> file.getMethod("openBookToQuestObject", long.class).invoke(null, 0x1D00000000001000L);
                case 1 -> shot(mc, "q_chapter");
                case 2 -> {
                    file.getMethod("openBookToQuestObject", long.class).invoke(null, 0x1A00000000100000L);
                    wait = 60;
                }
                case 3 -> shot(mc, "q_quest");
                default -> mc.stop();
            }
        } catch (ReflectiveOperationException e) {
            IIS.LOGGER.error("[IIS] quest shots", e);
            mc.stop();
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(new File(mc.gameDirectory.getPath()), "eq_" + name + ".png", mc.getMainRenderTarget(), 1, c -> {});
    }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) action.accept(p);
        });
    }

    private static void add(int wait, Consumer<Minecraft> a) {
        STEPS.add(new Step(wait, a));
    }

    private static void clearEntities(ServerPlayer p) {
        ServerLevel l = p.level();
        for (Entity e : l.getAllEntities()) {
            if (e != p && (e instanceof Driveable || e instanceof AbstractMinecart)) e.discard();
        }
    }

    private static void view(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.level(), x, y, z, Set.of(), yaw, pitch, true);
    }

    private static void build() {
        add(2, mc -> {
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, p -> {
                ServerLevel l = p.level();
                l.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withSuppressedOutput(), "time set noon");
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) l.setBlock(POS.offset(x, 0, z), (x + z & 1) == 0 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2 | 16);
                for (int x = -40; x <= 40; x++) l.setBlock(POS.offset(x, 1, -12), Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST), 2 | 16);
            });
        });
        // машины: с двух сторон
        for (VehicleKind k : VehicleKind.values()) {
            double d = Math.max(4.5, Math.max(k.width * 2.4, k.flies() ? 9.5 : 6));
            add(4, mc -> server(mc, p -> {
                clearEntities(p);
                ServerLevel l = p.level();
                if (k == VehicleKind.BOAT) {
                    for (int x = -4; x <= 4; x++) for (int z = -5; z <= 5; z++) l.setBlock(POS.offset(x, 0, z), Blocks.WATER.defaultBlockState(), 2 | 16);
                }
                Driveable v = EquipmentContent.VEHICLE_TYPES.get(k).get().create(l, EntitySpawnReason.COMMAND);
                v.snapTo(POS.getX() + 0.5, POS.getY() + 1, POS.getZ() + 0.5, 0, 0);
                v.setFuel(k.tank);
                l.addFreshEntity(v);
                view(p, POS.getX() + 0.5 + d * 0.7, POS.getY() + 1 + d * 0.28, POS.getZ() + 0.5 + d * 0.7, 135, 15);
            }));
            add(20, mc -> shot(mc, "v_" + k.id + "_a"));
            add(1, mc -> server(mc, p -> view(p, POS.getX() + 0.5 - d * 0.7, POS.getY() + 1 + d * 0.28, POS.getZ() + 0.5 - d * 0.7, -45, 15)));
            add(8, mc -> shot(mc, "v_" + k.id + "_b"));
            add(1, mc -> {
                if (k == VehicleKind.BOAT) server(mc, p -> {
                    for (int x = -4; x <= 4; x++) for (int z = -5; z <= 5; z++) p.level().setBlock(POS.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2 | 16);
                });
            });
        }
        // поезда
        String[][] carts = {{"loco_steam"}, {"loco_electric"}, {"track_layer"}, {"tunnel_bore"}, {"minecart"}, {"chest_minecart"}, {"hopper_minecart"}, {"furnace_minecart"}};
        for (String[] c : carts) {
            add(2, mc -> server(mc, p -> {
                clearEntities(p);
                ServerLevel l = p.level();
                Identifier id = c[0].contains("minecart") ? Identifier.withDefaultNamespace(c[0]) : Identifier.fromNamespaceAndPath(IIS.MODID, c[0]);
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
                Entity e = type.create(l, EntitySpawnReason.COMMAND);
                e.snapTo(POS.getX() + 0.5, POS.getY() + 1.06, POS.getZ() - 11.5, 0, 0);
                l.addFreshEntity(e);
                if (e instanceof ru.iis.rail.cart.Locomotive loco) loco.setHeading(new Vec3(1, 0, 0));
                view(p, POS.getX() + 3.6, POS.getY() + 2.6, POS.getZ() - 8.6, 135, 18);
            }));
            add(20, mc -> shot(mc, "t_" + c[0] + "_a"));
            add(1, mc -> server(mc, p -> view(p, POS.getX() - 2.6, POS.getY() + 2.6, POS.getZ() - 8.6, -135, 18)));
            add(6, mc -> shot(mc, "t_" + c[0] + "_b"));
        }
        // состав: локомотив и вагоны
        add(2, mc -> server(mc, p -> {
            clearEntities(p);
            ServerLevel l = p.level();
            String[] train = {"iis:loco_steam", "minecraft:chest_minecart", "minecraft:hopper_minecart", "minecraft:minecart"};
            double x = 3.5;
            AbstractMinecart last = null;
            for (String t : train) {
                Entity e = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(t)).create(l, EntitySpawnReason.COMMAND);
                e.snapTo(POS.getX() + x, POS.getY() + 1.06, POS.getZ() - 11.5, 0, 0);
                l.addFreshEntity(e);
                if (e instanceof ru.iis.rail.cart.Locomotive loco) loco.setHeading(new Vec3(1, 0, 0));
                if (last != null && e instanceof AbstractMinecart cart) ru.iis.rail.cart.TrainLinks.createLink(last, cart);
                if (e instanceof AbstractMinecart cart) last = cart;
                x -= 2.2;
            }
            view(p, POS.getX() - 0.5, POS.getY() + 4, POS.getZ() - 4, 180, 20);
        }));
        add(30, mc -> shot(mc, "t_train"));
        // оружие: от первого лица и от третьего
        add(2, mc -> server(mc, p -> {
            clearEntities(p);
            view(p, POS.getX() + 0.5, POS.getY() + 1, POS.getZ() + 0.5, 0, 0);
        }));
        for (Weapon w : Weapon.values()) {
            add(2, mc -> {
                mc.options.hideGui = false;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setYRot(0);
                mc.player.setXRot(0);
                server(mc, p -> {
                    ItemStack gun = new ItemStack(EquipmentContent.GUNS.get(w).get());
                    if (w.electric()) ru.iis.lv.item.BatteryItem.setCharge(gun, w.capacity);
                    else if (w.plasma()) gun.set(EquipmentContent.GUN_PLASMA.get(), ru.iis.fluid.FluidContent.of(ru.iis.fluid.GtFluids.all().get("plasma_helium").source().get(), w.plasmaCapacity() / 2));
                    else gun.set(EquipmentContent.GUN_AMMO.get(), w.magazine);
                    p.setItemSlot(EquipmentSlot.MAINHAND, gun);
                    if (w.magazineType() != null) {
                        p.getInventory().clearContent();
                        p.setItemSlot(EquipmentSlot.MAINHAND, gun);
                        ItemStack mag = new ItemStack(EquipmentContent.MAGAZINES.get(w.magazineType()).get());
                        mag.set(EquipmentContent.GUN_AMMO.get(), w.magazineType().capacity);
                        p.getInventory().add(mag);
                    }
                });
            });
            add(12, mc -> shot(mc, "g_" + w.id + "_1p"));
            add(1, mc -> server(mc, p -> ru.iis.equipment.GunActions.fire(p.level(), p, w)));
            add(w.projectile() ? 5 : 3, mc -> shot(mc, "f_" + w.id));
            add(6, mc -> {
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            });
            add(4, mc -> shot(mc, "g_" + w.id + "_3p"));
        }
        // значки в окне инвентаря
        add(2, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, p -> {
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                int i = 9;
                for (var g : EquipmentContent.GUNS.values()) p.getInventory().setItem(i++, new ItemStack(g.get()));
                for (var g : EquipmentContent.VEHICLES.values()) p.getInventory().setItem(i++, new ItemStack(g.get()));
                i = 0;
                for (var g : EquipmentContent.AMMO.values()) p.getInventory().setItem(i++, new ItemStack(g.get()));
            });
        });
        add(6, mc -> {
            mc.options.hideGui = false;
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        });
        add(8, mc -> shot(mc, "icons"));
        add(1, mc -> {
            mc.setScreen(null);
            mc.options.hideGui = true;
            server(mc, p -> {
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
            });
        });
        // броня
        String[] sets = {"musketeer", "ballistic", "assault", "power_armor", "plasma_armor"};
        for (String s : sets) {
            add(2, mc -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> {
                    String[] parts = {"helmet", "chestplate", "leggings", "boots"};
                    EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                    for (int i = 0; i < 4; i++) p.setItemSlot(slots[i], new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(IIS.MODID, s + "_" + parts[i]))));
                    p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(EquipmentContent.GUNS.get(Weapon.ASSAULT_RIFLE).get()));
                    view(p, POS.getX() + 0.5, POS.getY() + 2, POS.getZ() + 0.5, 200, 10);
                });
            });
            add(10, mc -> shot(mc, "a_" + s + "_front"));
            add(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            add(4, mc -> shot(mc, "a_" + s + "_back"));
        }
        add(1, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, p -> {
                for (EquipmentSlot sl : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) p.setItemSlot(sl, ItemStack.EMPTY);
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
            });
        });
        // за рулём: сесть, камера изнутри и снаружи
        for (VehicleKind k : new VehicleKind[]{VehicleKind.CAR, VehicleKind.TRUCK, VehicleKind.QUAD, VehicleKind.PLANE, VehicleKind.HELICOPTER, VehicleKind.TANK}) {
            add(3, mc -> server(mc, p -> {
                clearEntities(p);
                ServerLevel l = p.level();
                Driveable v = EquipmentContent.VEHICLE_TYPES.get(k).get().create(l, EntitySpawnReason.COMMAND);
                v.snapTo(POS.getX() + 0.5, POS.getY() + 1, POS.getZ() + 0.5, 0, 0);
                v.setFuel(k.tank);
                l.addFreshEntity(v);
                p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                p.startRiding(v, true, false);
                p.setYRot(0);
                p.setXRot(5);
            }));
            add(1, mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setYRot(0);
                mc.player.setXRot(8);
            });
            add(15, mc -> shot(mc, "r_" + k.id + "_1p"));
            add(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            add(6, mc -> shot(mc, "r_" + k.id + "_3p"));
            add(1, mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, ServerPlayer::stopRiding);
            });
        }
        // вождение: полный газ на настоящем клиенте
        add(3, mc -> server(mc, p -> {
            clearEntities(p);
            ServerLevel l = p.level();
            Driveable v = EquipmentContent.VEHICLE_TYPES.get(VehicleKind.CAR).get().create(l, EntitySpawnReason.COMMAND);
            v.snapTo(POS.getX() + 0.5, POS.getY() + 1, POS.getZ() - 30.5, 0, 0);
            v.setFuel(VehicleKind.CAR.tank);
            l.addFreshEntity(v);
            p.startRiding(v, true, false);
            p.setYRot(0);
        }));
        add(4, mc -> {
            mc.player.setYRot(0);
            mc.player.setXRot(8);
            mc.options.hideGui = false;
            mc.options.keyUp.setDown(true);
        });
        add(60, mc -> {
            mc.options.keyUp.setDown(false);
            IIS.LOGGER.info("[IIS] equip shots: car at z={} health {} after full throttle", mc.player.getVehicle() == null ? "none" : mc.player.getVehicle().getZ(), mc.player.getVehicle() instanceof Driveable d ? d.vehicleHealth() : -1);
            shot(mc, "r_driving");
        });
        // взлёт на настоящем клиенте: вертолёт
        add(2, mc -> {
            server(mc, p -> {
                p.stopRiding();
                clearEntities(p);
                ServerLevel l = p.level();
                Driveable v = EquipmentContent.VEHICLE_TYPES.get(VehicleKind.HELICOPTER).get().create(l, EntitySpawnReason.COMMAND);
                v.snapTo(POS.getX() + 0.5, POS.getY() + 1, POS.getZ() + 0.5, 0, 0);
                v.setFuel(VehicleKind.HELICOPTER.tank);
                l.addFreshEntity(v);
                p.startRiding(v, true, false);
            });
        });
        add(4, mc -> {
            mc.player.setYRot(0);
            mc.options.keyJump.setDown(true);
        });
        add(200, mc -> {
            mc.options.keyJump.setDown(false);
            IIS.LOGGER.info("[IIS] equip shots: helicopter height above the pad {} after holding up", mc.player.getVehicle() == null ? "none" : mc.player.getVehicle().getY() - POS.getY());
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        add(4, mc -> shot(mc, "r_heli_flying"));
        // самолёт: полный газ и нос вверх
        add(2, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, p -> {
                p.stopRiding();
                clearEntities(p);
                ServerLevel l = p.level();
                Driveable v = EquipmentContent.VEHICLE_TYPES.get(VehicleKind.PLANE).get().create(l, EntitySpawnReason.COMMAND);
                v.snapTo(POS.getX() + 0.5, POS.getY() + 1, POS.getZ() - 30.5, 0, 0);
                v.setFuel(VehicleKind.PLANE.tank);
                l.addFreshEntity(v);
                p.startRiding(v, true, false);
            });
        });
        add(4, mc -> {
            mc.player.setYRot(0);
            mc.player.setXRot(-20);
            mc.options.keyUp.setDown(true);
        });
        add(160, mc -> {
            mc.options.keyUp.setDown(false);
            IIS.LOGGER.info("[IIS] equip shots: plane height above the runway {} after full throttle", mc.player.getVehicle() == null ? "none" : mc.player.getVehicle().getY() - POS.getY());
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        add(4, mc -> shot(mc, "r_plane_flying"));
        // проходимость на настоящем клиенте (D130): ступень или лестница впереди и полный газ
        record Climb(VehicleKind kind, int wall, int stairs, int ticks) {}
        for (Climb c : List.of(new Climb(VehicleKind.CAR, 1, 0, 70), new Climb(VehicleKind.CAR, 2, 0, 70), new Climb(VehicleKind.MOTORBIKE, 1, 0, 60), new Climb(VehicleKind.QUAD, 2, 0, 90), new Climb(VehicleKind.QUAD, 3, 0, 90),
                new Climb(VehicleKind.ATV, 3, 0, 70), new Climb(VehicleKind.ATV, 4, 0, 70), new Climb(VehicleKind.TRUCK, 1, 0, 110), new Climb(VehicleKind.TANK, 2, 0, 160), new Climb(VehicleKind.CAR, 0, 10, 70), new Climb(VehicleKind.TRUCK, 0, 10, 200))) {
            String tag = c.kind().id + (c.wall() > 0 ? "_wall" + c.wall() : "_stairs" + c.stairs());
            add(3, mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> {
                    p.stopRiding();
                    clearEntities(p);
                    ServerLevel l = p.level();
                    for (int x = -6; x <= 6; x++) for (int z = -40; z <= 40; z++) for (int y = 1; y <= 14; y++) {
                        int top = c.wall() > 0 ? (z >= -25 && z <= -22 ? c.wall() : 0) : Math.max(0, Math.min(c.stairs(), (z + 32) / 2));
                        l.setBlock(POS.offset(x, y, z), y <= top && Math.abs(x) <= 4 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                    Driveable v = EquipmentContent.VEHICLE_TYPES.get(c.kind()).get().create(l, EntitySpawnReason.COMMAND);
                    v.snapTo(POS.getX() + 0.5, POS.getY() + 1, POS.getZ() - 36.5, 0, 0);
                    v.setFuel(c.kind().tank);
                    l.addFreshEntity(v);
                    p.startRiding(v, true, false);
                    p.setYRot(0);
                });
            });
            add(6, mc -> {
                mc.player.setYRot(0);
                mc.player.setXRot(5);
                mc.options.keyUp.setDown(true);
            });
            add(c.ticks(), mc -> {
                mc.options.keyUp.setDown(false);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                Entity v = mc.player.getVehicle();
                IIS.LOGGER.info("[IIS] climb {}: z={} height={} speed={} health={}", tag, v == null ? "none" : String.format("%.1f", v.getZ() - POS.getZ()), v == null ? "none" : String.format("%.1f", v.getY() - POS.getY() - 1),
                        v == null ? "none" : String.format("%.2f", v.getDeltaMovement().horizontalDistance()), v instanceof Driveable d ? d.vehicleHealth() : -1);
            });
            add(4, mc -> shot(mc, "c_" + tag));
        }
        add(2, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, p -> {
                p.stopRiding();
                clearEntities(p);
                ServerLevel l = p.level();
                for (int x = -6; x <= 6; x++) for (int z = -40; z <= 40; z++) for (int y = 1; y <= 14; y++) l.setBlock(POS.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2 | 16);
            });
        });
        // окна: грузовик, катер без груза, локомотивы
        add(2, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, p -> {
                p.stopRiding();
                clearEntities(p);
                view(p, POS.getX() + 0.5, POS.getY() + 2, POS.getZ() + 6, 180, 10);
                Driveable v = EquipmentContent.VEHICLE_TYPES.get(VehicleKind.TRUCK).get().create(p.level(), EntitySpawnReason.COMMAND);
                v.snapTo(POS.getX() + 0.5, POS.getY() + 1, POS.getZ() + 0.5, 0, 0);
                v.setFuel(VehicleKind.TRUCK.tank / 3);
                p.level().addFreshEntity(v);
                v.openMenu(p);
            });
        });
        add(10, mc -> shot(mc, "w_truck"));
        add(1, mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 1));
        add(6, mc -> shot(mc, "w_truck_page2"));
        add(2, mc -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
            server(mc, p -> {
                clearEntities(p);
                Driveable v = EquipmentContent.VEHICLE_TYPES.get(VehicleKind.QUAD).get().create(p.level(), EntitySpawnReason.COMMAND);
                v.snapTo(POS.getX() + 0.5, POS.getY() + 1, POS.getZ() + 0.5, 0, 0);
                v.setFuel(VehicleKind.QUAD.tank);
                p.level().addFreshEntity(v);
                v.openMenu(p);
            });
        });
        add(10, mc -> shot(mc, "w_quad"));
        add(2, mc -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
            server(mc, p -> {
                clearEntities(p);
                var e = RailContent.STEAM_LOCO.get().create(p.level(), EntitySpawnReason.COMMAND);
                e.snapTo(POS.getX() + 0.5, POS.getY() + 1.06, POS.getZ() - 11.5, 0, 0);
                p.level().addFreshEntity(e);
                e.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND, Vec3.ZERO);
            });
        });
        add(10, mc -> shot(mc, "w_loco_steam"));
        add(2, mc -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
            mc.setScreen(null);
        });
        // магазин наград: что уже было в руках и пригоршня жетонов
        add(2, mc -> server(mc, p -> {
            clearEntities(p);
            ServerLevel l = p.level();
            p.getInventory().clearContent();
            p.setData(ru.iis.shop.ShopContent.SEEN, new ArrayList<>(ru.iis.shop.ShopContent.prices().keySet().stream().filter(k -> k.contains("ingot") || k.contains("oak_log") || k.contains("cobblestone")).limit(70).toList()));
            for (var credit : ru.iis.shop.ShopContent.creditItems().subList(0, 3)) p.getInventory().add(new ItemStack(credit, 24));
            p.inventoryMenu.broadcastChanges();
            BlockPos at = POS.offset(0, 1, 3);
            l.setBlock(at, ru.iis.shop.ShopContent.SHOP.get().defaultBlockState(), 3);
            view(p, at.getX() + 0.5, at.getY(), at.getZ() - 2.5, 0, 0);
            l.getBlockState(at).useWithoutItem(l, p, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(at), net.minecraft.core.Direction.NORTH, at, false));
        }));
        add(12, mc -> shot(mc, "w_shop"));
        add(2, mc -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
            mc.setScreen(null);
        });
        add(2, Minecraft::stop);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            if (mc.screen != null) StandShots.proceed(mc.screen);
            return;
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (--wait > 0) return;
        if (!built) {
            built = true;
            build();
        }
        if (index >= STEPS.size()) return;
        Step s = STEPS.get(index++);
        s.action.accept(mc);
        wait = index < STEPS.size() ? STEPS.get(index).delay : 1;
    }
}

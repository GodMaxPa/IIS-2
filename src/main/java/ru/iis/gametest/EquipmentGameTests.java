package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import ru.iis.equipment.EquipmentContent;
import ru.iis.equipment.GunActions;
import ru.iis.equipment.ShotEntity;
import ru.iis.equipment.Weapon;
import ru.iis.equipment.vehicle.Driveable;
import ru.iis.equipment.vehicle.VehicleKind;
import ru.iis.equipment.vehicle.VehiclePhysics;
import ru.iis.fluid.IISFluids;
import ru.iis.gear.GearEvents;
import ru.iis.lv.item.BatteryItem;

/** Оружие, броня и транспорт D128: то, что считает сервер. Вид и управление с клавиатуры проверяет съёмочный обход. */
final class EquipmentGameTests {
    private EquipmentGameTests() {}

    private static void floor(GameTestHelper h) {
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static Player shooter(GameTestHelper h, double x, double z, float yaw) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(x, 2, z));
        p.snapTo(at.x, at.y, at.z, yaw, 0);
        return p;
    }

    private static Zombie target(GameTestHelper h, double x, double z) {
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(x, 2, z));
        zombie.setPersistenceRequired();
        return zombie;
    }

    /** Смотрит на цель: мировой поворот теста тут ни при чём, считаем по абсолютным координатам. */
    private static void aim(Player p, Zombie z) {
        Vec3 d = z.position().add(0, z.getBbHeight() * 0.6, 0).subtract(p.getEyePosition());
        p.setYRot((float) (Math.atan2(-d.x, d.z) * 180 / Math.PI));
        p.setXRot((float) (-Math.atan2(d.y, d.horizontalDistance()) * 180 / Math.PI));
    }

    /** Пуля бьёт цель в обход неуязвимости: два выстрела — два попадания. */
    static void bulletHits(GameTestHelper h) {
        floor(h);
        Player p = shooter(h, 1.5, 4.5, 0);
        Zombie z = target(h, 6.5, 4.5);
        aim(p, z);
        float hp = z.getHealth();
        GunActions.fire(h.getLevel(), p, Weapon.MUSKET);
        float one = z.getHealth();
        h.assertTrue(one < hp - 10, "мушкет: было " + hp + ", стало " + one);
        GunActions.fire(h.getLevel(), p, Weapon.PISTOL);
        h.assertTrue(z.getHealth() < one || z.isDeadOrDying(), "второй выстрел не прошёл: " + z.getHealth());
        h.succeed();
    }

    /** Стена закрывает цель. */
    static void wallStopsBullet(GameTestHelper h) {
        floor(h);
        Player p = shooter(h, 1.5, 4.5, 0);
        Zombie z = target(h, 6.5, 4.5);
        aim(p, z);
        for (int y = 2; y <= 4; y++) for (int zz = 2; zz <= 6; zz++) h.setBlock(new BlockPos(4, y, zz), Blocks.STONE);
        float hp = z.getHealth();
        GunActions.fire(h.getLevel(), p, Weapon.SNIPER_RIFLE);
        h.assertTrue(z.getHealth() == hp, "прошла сквозь камень");
        h.succeed();
    }

    /** Магазин: выстрел тратит, пустой начинает перезарядку, она берёт патроны из инвентаря. */
    static void magazineAndReload(GameTestHelper h) {
        floor(h);
        Player p = shooter(h, 1.5, 4.5, 0);
        ItemStack gun = new ItemStack(EquipmentContent.GUNS.get(Weapon.PISTOL).get());
        gun.set(EquipmentContent.GUN_AMMO.get(), 1);
        p.setItemInHand(InteractionHand.MAIN_HAND, gun);
        p.getInventory().add(new ItemStack(EquipmentContent.AMMO.get(Weapon.Ammo.PISTOL).get(), 20));
        var level = h.getLevel();
        h.assertTrue(GunActions.tryFire(level, p, gun, Weapon.PISTOL, true), "не выстрелил");
        h.assertTrue(GunActions.loaded(gun) == 0, "патрон не потрачен");
        h.assertFalse(GunActions.tryFire(level, p, gun, Weapon.PISTOL, true), "выстрел раньше темпа");
        h.runAtTickTime(Weapon.PISTOL.interval + 1, () -> {
            h.assertFalse(GunActions.tryFire(level, p, gun, Weapon.PISTOL, true), "выстрел из пустого");
            h.assertTrue(GunActions.reloading(gun, level.getGameTime()), "перезарядка не началась");
        });
        h.runAtTickTime(Weapon.PISTOL.interval + Weapon.PISTOL.reload + 3, () -> {
            GunActions.tickReload(level, p, gun, Weapon.PISTOL);
            h.assertTrue(GunActions.loaded(gun) == Weapon.PISTOL.magazine, "в магазине " + GunActions.loaded(gun));
            h.assertTrue(GunActions.countAmmo(p, Weapon.Ammo.PISTOL) == 20 - Weapon.PISTOL.magazine, "запас " + GunActions.countAmmo(p, Weapon.Ammo.PISTOL));
            h.succeed();
        });
    }

    /** Лазер: заряд тратится, без заряда молчит, луч прожигает цель. */
    static void laserUsesCharge(GameTestHelper h) {
        floor(h);
        Player p = shooter(h, 1.5, 4.5, 0);
        Zombie z = target(h, 6.5, 4.5);
        aim(p, z);
        ItemStack gun = new ItemStack(EquipmentContent.GUNS.get(Weapon.LASER_PISTOL).get());
        p.setItemInHand(InteractionHand.MAIN_HAND, gun);
        var level = h.getLevel();
        float hp = z.getHealth();
        h.assertFalse(GunActions.tryFire(level, p, gun, Weapon.LASER_PISTOL, true), "выстрел без заряда");
        BatteryItem.setCharge(gun, Weapon.LASER_PISTOL.euPerShot + 5);
        h.assertTrue(GunActions.tryFire(level, p, gun, Weapon.LASER_PISTOL, true), "заряд есть, выстрела нет");
        h.assertTrue(BatteryItem.charge(gun) == 5, "заряд " + BatteryItem.charge(gun));
        h.assertTrue(z.getHealth() < hp, "луч не попал");
        h.assertTrue(z.isOnFire(), "цель не горит");
        h.succeed();
    }

    /** Снаряд летит и взрывается: цель получает урон взрывом, блоки целы. */
    static void shellExplodes(GameTestHelper h) {
        floor(h);
        Player p = shooter(h, 1.5, 4.5, 0);
        Zombie z = target(h, 7.5, 4.5);
        aim(p, z);
        float hp = z.getHealth();
        GunActions.fire(h.getLevel(), p, Weapon.ROCKET_LAUNCHER);
        h.assertTrue(!h.getLevel().getEntitiesOfClass(ShotEntity.class, z.getBoundingBox().inflate(16)).isEmpty(), "снаряда нет");
        h.succeedWhen(() -> {
            h.assertTrue(z.getHealth() < hp - 10 || z.isDeadOrDying(), "взрыва ещё не было");
            h.assertBlockPresent(Blocks.STONE, new BlockPos(7, 1, 4));
        });
    }

    /** Дальше радиуса взрыв не достаёт, ближе бьёт, владелец получает половину. */
    static void blastFallsOff(GameTestHelper h) {
        floor(h);
        Player p = shooter(h, 0.5, 0.5, 0);
        Zombie near = target(h, 4.5, 4.5), far = target(h, 8.5, 8.5);
        Vec3 at = h.absoluteVec(new Vec3(4.5, 2.5, 4.5));
        float nearHp = near.getHealth(), farHp = far.getHealth();
        ShotEntity e = new ShotEntity(EquipmentContent.SHOT.get(), h.getLevel());
        GunActions.blast(h.getLevel(), at, e, p, Weapon.GRENADE_LAUNCHER);
        h.assertTrue(near.getHealth() < nearHp, "рядом нет урона");
        h.assertTrue(far.getHealth() == farHp, "достало за радиусом");
        h.succeed();
    }

    private static Driveable vehicle(GameTestHelper h, VehicleKind k, double x, double y, double z, float yaw) {
        Driveable v = EquipmentContent.VEHICLE_TYPES.get(k).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 at = h.absoluteVec(new Vec3(x, y, z));
        v.snapTo(at.x, at.y, at.z, yaw, 0);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    private static final VehiclePhysics.Input GAS = new VehiclePhysics.Input(true, false, false, false, false, false, 0, 0, true, true);

    /** Машина едет на топливе и тратит его; без топлива стоит. */
    static void carDrivesOnFuel(GameTestHelper h) {
        floor(h);
        Driveable car = vehicle(h, VehicleKind.CAR, 4.5, 2, 1.5, 0);
        Driveable dry = vehicle(h, VehicleKind.CAR, 8.5, 2, 1.5, 0);
        car.setFuel(1_000_000);
        dry.setFuel(0);
        Vec3 start = car.position(), dryStart = dry.position();
        car.setTestInput(new VehiclePhysics.Input(true, false, false, false, false, false, 0, 0, true, true));
        dry.setTestInput(new VehiclePhysics.Input(true, false, false, false, false, false, 0, 0, true, true));
        h.runAtTickTime(40, () -> {
            h.assertTrue(car.position().distanceTo(start) > 2, "проехала " + car.position().distanceTo(start));
            h.assertTrue(car.fuel() < 1_000_000, "топливо не тратится");
            h.assertTrue(car.engineOn(), "двигатель не работает");
            h.assertTrue(dry.position().subtract(dryStart).horizontalDistance() < 0.1, "поехала без топлива");
            h.assertFalse(dry.engineOn(), "двигатель без топлива");
            h.succeed();
        });
    }

    /** Жидкое топливо: ведро и капсула считаются по таблице генераторов; вода не топливо. Тягачу — уголь. */
    static void fuelValues(GameTestHelper h) {
        floor(h);
        Driveable car = vehicle(h, VehicleKind.CAR, 2.5, 2, 2.5, 0);
        Driveable steam = vehicle(h, VehicleKind.STEAM_CAR, 6.5, 2, 6.5, 0);
        h.assertFalse(car.canFuel(new ItemStack(Items.WATER_BUCKET)), "вода горит");
        h.assertFalse(car.canFuel(new ItemStack(Items.COAL)), "уголь в баке машины");
        h.assertTrue(steam.canFuel(new ItemStack(Items.COAL)), "уголь не топливо тягача");
        h.assertFalse(steam.canFuel(new ItemStack(Items.WATER_BUCKET)), "вода в тягаче");
        var fuel = ru.iis.fluid.GtFluids.all().get("gasoline");
        h.assertTrue(fuel != null, "нет бензина");
        h.assertTrue(Driveable.liquidValue(fuel.source().get()) == 384, "бензин " + Driveable.liquidValue(fuel.source().get()));
        ItemStack capsule = ru.iis.fluid.CapsuleItem.withContent(new ItemStack(ru.iis.fluid.FluidContainers.CAPSULE.get()), ru.iis.fluid.FluidContent.of(fuel.source().get(), 1000));
        h.assertTrue(car.fuelValue(capsule) == 384_000, "капсула " + car.fuelValue(capsule));
        h.assertFalse(car.canFuel(new ItemStack(ru.iis.fluid.FluidContainers.CAPSULE.get())), "пустая капсула");
        h.assertTrue(IISFluids.STEAM.get() != null, "пар");
        h.succeed();
    }

    /** Самолёт без скорости не держится в воздухе, вертолёт с раскрученным винтом висит. */
    static void aircraftInTheAir(GameTestHelper h) {
        Driveable plane = vehicle(h, VehicleKind.PLANE, 2.5, 30, 2.5, 0);
        Driveable heli = vehicle(h, VehicleKind.HELICOPTER, 8.5, 30, 8.5, 0);
        plane.setFuel(0);
        heli.setFuel(1_000_000);
        heli.physics().rotor = 1;
        heli.setTestInput(new VehiclePhysics.Input(false, false, false, false, false, false, 0, 0, true, true));
        double py = plane.getY(), hy = heli.getY();
        h.runAtTickTime(40, () -> {
            h.assertTrue(plane.getY() < py - 3, "самолёт висит: " + (plane.getY() - py));
            h.assertTrue(Math.abs(heli.getY() - hy) < 1.5, "вертолёт ушёл на " + (heli.getY() - hy));
            plane.discard();
            heli.discard();
            h.succeed();
        });
    }

    /** Катер держится на воде, на суше не едет. */
    static void boatFloats(GameTestHelper h) {
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++) {
            h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            h.setBlock(new BlockPos(x, 2, z), Blocks.WATER);
            h.setBlock(new BlockPos(x, 3, z), Blocks.WATER);
        }
        Driveable boat = vehicle(h, VehicleKind.BOAT, 4.5, 4, 4.5, 0);
        double water = h.absoluteVec(new Vec3(0, 4, 0)).y;
        h.runAtTickTime(60, () -> {
            h.assertTrue(Math.abs(boat.getY() - (water - 0.4)) < 0.4, "осадка: y=" + (boat.getY() - water));
            h.succeed();
        });
    }

    /** Разбитая машина высыпает груз и остаётся предметом, бак пуст. */
    static void wreckDropsItem(GameTestHelper h) {
        floor(h);
        Driveable truck = vehicle(h, VehicleKind.TRUCK, 4.5, 2, 4.5, 0);
        truck.setFuel(1000);
        truck.cargo.setItem(3, new ItemStack(Items.DIAMOND, 5));
        var level = h.getLevel();
        truck.hurtServer(level, level.damageSources().generic(), 10000);
        h.assertTrue(truck.isRemoved(), "не разбита");
        var box = new net.minecraft.world.phys.AABB(h.absoluteVec(new Vec3(0, 0, 0)), h.absoluteVec(new Vec3(9, 6, 9)));
        var items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box);
        h.assertTrue(items.stream().anyMatch(i -> i.getItem().is(Items.DIAMOND)), "груз пропал");
        var drop = items.stream().filter(i -> i.getItem().is(EquipmentContent.VEHICLES.get(VehicleKind.TRUCK).get())).findFirst();
        h.assertTrue(drop.isPresent(), "машины нет");
        h.assertTrue(drop.get().getItem().getOrDefault(EquipmentContent.VEHICLE_FUEL.get(), -1L) == 0L, "топливо не пропало");
        h.succeed();
    }

    /** Удар о стену на скорости портит машину, спокойная езда нет. */
    static void crashHurts(GameTestHelper h) {
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (int x = 0; x <= 8; x++) for (int y = 2; y <= 5; y++) h.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
        Driveable car = vehicle(h, VehicleKind.CAR, 4.5, 2, 2.5, 0);
        car.setFuel(1_000_000);
        var dir = h.absoluteVec(new Vec3(4.5, 2, 8)).subtract(h.absoluteVec(new Vec3(4.5, 2, 2.5))).normalize();
        float yaw = (float) (Math.atan2(-dir.x, dir.z) * 180 / Math.PI);
        car.setYRot(yaw);
        car.physics().speed = 0.6;
        car.setTestInput(new VehiclePhysics.Input(true, false, false, false, false, false, yaw, 0, true, true));
        float hp = car.vehicleHealth();
        h.succeedWhen(() -> h.assertTrue(car.vehicleHealth() < hp, "удара нет: скорость " + car.physics().speed + ", z " + car.getZ()));
    }

    /** Пули танк не берут, снаряд берёт. */
    static void tankShrugsBullets(GameTestHelper h) {
        floor(h);
        Driveable tank = vehicle(h, VehicleKind.TANK, 4.5, 2, 4.5, 0);
        var level = h.getLevel();
        float hp = tank.vehicleHealth();
        ShotEntity shot = new ShotEntity(EquipmentContent.SHOT.get(), level);
        tank.hurtServer(level, GunActions.source(level, Weapon.Kind.BULLET, shot, shot), 50);
        h.assertTrue(tank.vehicleHealth() == hp, "пуля пробила: " + tank.vehicleHealth());
        tank.hurtServer(level, GunActions.source(level, Weapon.Kind.ROCKET, shot, shot), 50);
        h.assertTrue(tank.vehicleHealth() < hp, "снаряд не пробил");
        tank.discard();
        h.succeed();
    }

    /** Силовая броня поглощает урон за заряд, без заряда нет; обычная держит очками брони. */
    static void poweredArmorAbsorbs(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        String[] parts = {"helmet", "chestplate", "leggings", "boots"};
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        ItemStack[] set = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            set[i] = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(ru.iis.IIS.MODID, "power_armor_" + parts[i])));
            p.setItemSlot(slots[i], set[i]);
        }
        h.assertTrue(Math.abs(GearEvents.absorb(p, 10F) - 10F) < 1e-3, "без заряда защитила");
        for (ItemStack s : set) BatteryItem.setCharge(s, 1_000_000);
        h.assertTrue(GearEvents.absorb(p, 10F) < 1e-3, "с зарядом не защитила");
        h.assertTrue(BatteryItem.charge(set[1]) < 1_000_000, "заряд не потрачен");
        ItemStack musk = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(ru.iis.IIS.MODID, "musketeer_chestplate")));
        var mods = musk.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        h.assertTrue(mods != null && mods.modifiers().stream().anyMatch(e -> e.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) && e.modifier().amount() == 6), "очки брони мушкетёра");
        h.succeed();
    }

    /** Рецепты: у каждого оружия, машины и брони есть рецепт, иначе в выживании их не получить. */
    static void everythingHasARecipe(GameTestHelper h) {
        var recipes = h.getLevel().recipeAccess();
        java.util.List<String> bad = new java.util.ArrayList<>();
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (Weapon w : Weapon.values()) ids.add("equipment/" + w.id);
        for (VehicleKind k : VehicleKind.values()) ids.add("equipment/vehicle_" + k.id);
        for (String a : new String[]{"musketeer", "ballistic", "assault"}) for (String p : new String[]{"helmet", "chestplate", "leggings", "boots"}) ids.add("equipment/" + a + "_" + p);
        for (String a : new String[]{"power_armor", "plasma_armor"}) for (String p : new String[]{"helmet", "chestplate", "leggings", "boots"}) ids.add("processing/assembler/" + a + "_" + p);
        for (String p : new String[]{"wheel_wood", "wheel_rubber", "engine_steam", "engine_lv", "engine_mv", "engine_hv", "engine_ev", "jet_turbine"}) ids.add("equipment/" + p);
        for (Weapon.Ammo a : Weapon.Ammo.values()) ids.add(a == Weapon.Ammo.POWDER ? "equipment/powder_cartridge" : "processing/assembler/" + a.id);
        for (String id : ids) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, net.minecraft.resources.Identifier.fromNamespaceAndPath(ru.iis.IIS.MODID, id));
            if (recipes.byKey(key).isEmpty()) bad.add(id);
        }
        h.assertTrue(bad.isEmpty(), "нет рецептов: " + bad);
        h.succeed();
    }
}

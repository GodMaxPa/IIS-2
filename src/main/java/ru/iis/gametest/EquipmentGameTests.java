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

    /** Россыпью: ружьё заряжается патронами из инвентаря; без патронов молчит. */
    static void looseReload(GameTestHelper h) {
        floor(h);
        Player p = shooter(h, 1.5, 4.5, 0);
        ItemStack gun = new ItemStack(EquipmentContent.GUNS.get(Weapon.MUSKET).get());
        p.setItemInHand(InteractionHand.MAIN_HAND, gun);
        var level = h.getLevel();
        h.assertFalse(GunActions.startReload(level, p, gun, Weapon.MUSKET), "заряжается из ничего");
        p.getInventory().add(new ItemStack(EquipmentContent.AMMO.get(Weapon.Ammo.POWDER).get(), 3));
        h.assertTrue(GunActions.startReload(level, p, gun, Weapon.MUSKET), "не заряжается");
        h.runAtTickTime(Weapon.MUSKET.reload + 2, () -> {
            GunActions.tickReload(level, p, gun, Weapon.MUSKET);
            h.assertTrue(GunActions.loaded(gun) == 1 && GunActions.countAmmo(p, Weapon.Ammo.POWDER) == 2, "заряд " + GunActions.loaded(gun));
            h.succeed();
        });
    }

    /** Магазин: набивается патронами, R меняет пустой на полный, снятый возвращается в инвентарь. */
    static void magazineSwap(GameTestHelper h) {
        floor(h);
        Player p = shooter(h, 1.5, 4.5, 0);
        ItemStack gun = new ItemStack(EquipmentContent.GUNS.get(Weapon.PISTOL).get());
        p.setItemInHand(InteractionHand.MAIN_HAND, gun);
        var level = h.getLevel();
        h.assertFalse(GunActions.startReload(level, p, gun, Weapon.PISTOL), "пистолет заряжается без магазина");
        p.getInventory().add(new ItemStack(EquipmentContent.AMMO.get(Weapon.Ammo.PISTOL).get(), 20));
        h.assertFalse(GunActions.startReload(level, p, gun, Weapon.PISTOL), "патроны россыпью вместо магазина");
        ItemStack mag = new ItemStack(EquipmentContent.MAGAZINES.get(Weapon.Magazine.PISTOL).get());
        mag.set(EquipmentContent.GUN_AMMO.get(), 12);
        p.getInventory().add(mag);
        h.assertTrue(GunActions.startReload(level, p, gun, Weapon.PISTOL), "магазин не взят");
        h.runAtTickTime(Weapon.PISTOL.reload + 2, () -> {
            GunActions.tickReload(level, p, gun, Weapon.PISTOL);
            h.assertTrue(GunActions.loaded(gun) == 12, "в оружии " + GunActions.loaded(gun));
            h.assertTrue(GunActions.bestMagazine(p, Weapon.Magazine.PISTOL) < 0, "магазин остался в инвентаре");
            gun.set(EquipmentContent.GUN_AMMO.get(), 3);
            ItemStack second = new ItemStack(EquipmentContent.MAGAZINES.get(Weapon.Magazine.PISTOL).get());
            second.set(EquipmentContent.GUN_AMMO.get(), 12);
            p.getInventory().add(second);
            h.assertTrue(GunActions.startReload(level, p, gun, Weapon.PISTOL), "замена не началась");
        });
        h.runAtTickTime(2 * Weapon.PISTOL.reload + 6, () -> {
            GunActions.tickReload(level, p, gun, Weapon.PISTOL);
            h.assertTrue(GunActions.loaded(gun) == 12, "после замены " + GunActions.loaded(gun));
            h.assertTrue(GunActions.roundsInMagazines(p, Weapon.Magazine.PISTOL) == 3, "снятый магазин " + GunActions.roundsInMagazines(p, Weapon.Magazine.PISTOL));
            h.succeed();
        });
    }

    /** Плазма: без неё молчит, капсула заливается, выстрел тратит литры, другой вид не смешивается. */
    static void plasmaGunUsesPlasma(GameTestHelper h) {
        floor(h);
        Player p = shooter(h, 1.5, 4.5, 0);
        ItemStack gun = new ItemStack(EquipmentContent.GUNS.get(Weapon.PLASMA_RIFLE).get());
        p.setItemInHand(InteractionHand.MAIN_HAND, gun);
        var level = h.getLevel();
        h.assertFalse(GunActions.tryFire(level, p, gun, Weapon.PLASMA_RIFLE, true), "выстрел без плазмы");
        var helium = ru.iis.fluid.GtFluids.all().get("plasma_helium").source().get();
        var nickel = ru.iis.fluid.GtFluids.all().get("plasma_nickel").source().get();
        ItemStack empty = new ItemStack(ru.iis.fluid.FluidContainers.CAPSULE.get());
        p.getInventory().add(ru.iis.fluid.CapsuleItem.withContent(empty, ru.iis.fluid.FluidContent.of(helium, 1000)));
        h.assertTrue(GunActions.fillPlasma(p, gun, Weapon.PLASMA_RIFLE), "капсула не залита");
        h.assertTrue(ru.iis.equipment.PlasmaGunItem.tank(gun).amount() == 1000, "в баке " + ru.iis.equipment.PlasmaGunItem.tank(gun).amount());
        h.assertTrue(GunActions.tryFire(level, p, gun, Weapon.PLASMA_RIFLE, true), "не выстрелил");
        h.assertTrue(ru.iis.equipment.PlasmaGunItem.tank(gun).amount() == 1000 - Weapon.PLASMA_RIFLE.plasmaLiters(), "литры не потрачены");
        h.assertTrue(Math.abs(GunActions.plasmaScale(ru.iis.equipment.PlasmaGunItem.tank(gun)) - 0.7F) < 1e-3, "сила гелия");
        p.getInventory().add(ru.iis.fluid.CapsuleItem.withContent(empty, ru.iis.fluid.FluidContent.of(nickel, 1000)));
        h.assertFalse(GunActions.fillPlasma(p, gun, Weapon.PLASMA_RIFLE), "смешал виды плазмы");
        h.succeed();
    }

    /** Заправщик: подходящая машина заправляется, неподходящая нет. */
    static void fuelPumpFills(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(4, 2, 4), ru.iis.equipment.EquipmentContent.FUEL_PUMP.get());
        var be = (ru.iis.equipment.vehicle.FuelPumpBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4, 2, 4)));
        var diesel = ru.iis.fluid.GtFluids.all().get("fuel").source().get();
        be.tank.setAmount(diesel, 10000);
        Driveable car = vehicle(h, VehicleKind.CAR, 1.5, 2, 1.5, 0);
        Driveable jet = vehicle(h, VehicleKind.JET, 7.5, 2, 7.5, 0);
        h.runAtTickTime(40, () -> {
            h.assertTrue(car.fuel() > 0, "автомобиль не заправлен");
            h.assertTrue(jet.fuel() == 0, "реактивный заправлен дизелем");
            h.assertTrue(be.tank.amount() < 10000, "бак не тратится");
            car.discard();
            jet.discard();
            h.succeed();
        });
    }

    /** Грузовой комплект: багажник вдвое и в предмете сохраняется. */
    static void cargoKit(GameTestHelper h) {
        floor(h);
        Driveable truck = vehicle(h, VehicleKind.TRUCK, 4.5, 2, 4.5, 0);
        h.assertTrue(truck.cargoSlots() == 162, "грузовик " + truck.cargoSlots());
        truck.setKit(true);
        h.assertTrue(truck.cargoSlots() == 324, "с комплектом " + truck.cargoSlots());
        h.assertTrue(truck.cargo.getContainerSize() == 325, "контейнер " + truck.cargo.getContainerSize());
        truck.discard();
        h.succeed();
    }

    /** Листва на пути вездехода ломается, у автомобиля нет. */
    static void crushesLeaves(GameTestHelper h) {
        floor(h);
        for (int x = 1; x <= 7; x++) for (int y = 2; y <= 3; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.OAK_LEAVES);
        var dir = h.absoluteVec(new Vec3(4.5, 2, 8)).subtract(h.absoluteVec(new Vec3(4.5, 2, 2.5))).normalize();
        float yaw = (float) (Math.atan2(-dir.x, dir.z) * 180 / Math.PI);
        Driveable atv = vehicle(h, VehicleKind.ATV, 4.5, 2, 2.5, yaw);
        atv.setFuel(1_000_000);
        atv.setTestInput(new VehiclePhysics.Input(true, false, false, false, false, false, yaw, 0, true, true));
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 2, 6));
        });
    }

    /** Магазин наград: продаёт только виденное, жетоны со сдачей. */
    static void shopSells(GameTestHelper h) {
        h.succeedIf(() -> {
            var prices = ru.iis.shop.ShopContent.prices();
            h.assertTrue(prices.size() > 100, "мало товаров: " + prices.size());
            h.assertTrue(prices.containsKey("iis:ingot_steel"), "нет стали");
            h.assertTrue(prices.get("iis:ingot_titanium").tier() == 2, "титан за серебряные: " + prices.get("iis:ingot_titanium").tier());
            h.assertTrue(prices.get("minecraft:oak_log").tier() == 0, "дерево за медные");
            h.assertFalse(prices.containsKey("iis:macerator_lv"), "машина в магазине");
        });
    }

    /** Магазин: выстрел тратит, пустой начинает перезарядку, она берёт патроны из инвентаря. */
    static void magazineAndReload(GameTestHelper h) {
        floor(h);
        Player p = shooter(h, 1.5, 4.5, 0);
        ItemStack gun = new ItemStack(EquipmentContent.GUNS.get(Weapon.PISTOL).get());
        gun.set(EquipmentContent.GUN_AMMO.get(), 1);
        p.setItemInHand(InteractionHand.MAIN_HAND, gun);
        ItemStack loaded = new ItemStack(EquipmentContent.MAGAZINES.get(Weapon.Magazine.PISTOL).get());
        loaded.set(EquipmentContent.GUN_AMMO.get(), 12);
        p.getInventory().add(loaded);
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
        var octane = ru.iis.fluid.GtFluids.all().get("highoctanegasoline").source().get();
        var diesel = ru.iis.fluid.GtFluids.all().get("fuel").source().get();
        h.assertTrue(Driveable.liquidValue(VehicleKind.JET, fuel.source().get()) == 0, "реактивный на простом бензине");
        h.assertTrue(Driveable.liquidValue(VehicleKind.JET, octane) == 768, "реактивный на высокооктановом");
        h.assertTrue(Driveable.liquidValue(VehicleKind.PLANE, fuel.source().get()) == 384, "самолёт на бензине");
        h.assertTrue(Driveable.liquidValue(VehicleKind.PLANE, diesel) == 0, "самолёт на дизеле");
        h.assertTrue(Driveable.liquidValue(VehicleKind.CAR, diesel) > 0, "автомобиль на дизеле");
        ItemStack capsule = ru.iis.fluid.CapsuleItem.withContent(new ItemStack(ru.iis.fluid.FluidContainers.CAPSULE.get()), ru.iis.fluid.FluidContent.of(fuel.source().get(), 1000));
        h.assertTrue(car.fuelValue(capsule) == 384_000, "капсула " + car.fuelValue(capsule));
        h.assertFalse(car.canFuel(new ItemStack(ru.iis.fluid.FluidContainers.CAPSULE.get())), "пустая капсула");
        h.assertTrue(IISFluids.STEAM.get() != null, "пар");
        // большой дизельный генератор и топливо, которое раньше не горело нигде (D130)
        var gt = ru.iis.fluid.GtFluids.all();
        h.assertTrue(ru.iis.lv.Fuels.power("diesel", octane) == 768, "генератор на высокооктановом");
        h.assertTrue(ru.iis.lv.Fuels.power("diesel", gt.get("biodiesel").source().get()) == 192, "генератор на биодизеле");
        h.assertTrue(ru.iis.lv.Fuels.power("diesel", gt.get("liquid_light_fuel").source().get()) == 256, "генератор на лёгком топливе");
        h.assertTrue(ru.iis.lv.Fuels.power("gas", gt.get("gas_gas").source().get()) == 128, "турбина на газе");
        h.assertTrue(ru.iis.lv.Fuels.power("gas", gt.get("liquid_lpg").source().get()) == 256, "турбина на LPG");
        h.succeed();
    }

    /** Самолёт без скорости не держится в воздухе, вертолёт с раскрученным винтом висит. */
    static void aircraftInTheAir(GameTestHelper h) {
        Driveable plane = vehicle(h, VehicleKind.PLANE, 2.5, 30, 2.5, 0);
        Driveable heli = vehicle(h, VehicleKind.HELICOPTER, 8.5, 30, 8.5, 0);
        plane.setFuel(0);
        heli.setFuel(1_000_000);
        heli.physics().rotor = 1;
        heli.setRotorOn(true);
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

    /** Включённый винт жжёт топливо и без пилота и крутится; выключенный не тратит и останавливается (просьба владельца). */
    static void helicopterRotorSwitch(GameTestHelper h) {
        floor(h);
        Driveable on = vehicle(h, VehicleKind.HELICOPTER, 2.5, 2, 2.5, 0);
        Driveable off = vehicle(h, VehicleKind.HELICOPTER, 8.5, 2, 8.5, 0);
        Driveable dry = vehicle(h, VehicleKind.HELICOPTER, 2.5, 2, 8.5, 0);
        on.setFuel(1_000_000);
        off.setFuel(1_000_000);
        off.physics().rotor = 0.2;
        on.setRotorOn(true);
        dry.setFuel(1);
        dry.setRotorOn(true);
        h.runAtTickTime(70, () -> {
            h.assertTrue(on.engineOn(), "двигатель не работает при включённом винте");
            h.assertTrue(on.fuel() < 1_000_000, "включённый винт не жжёт топливо");
            h.assertTrue(on.physics().rotor > 0.4, "винт не раскручивается: " + on.physics().rotor);
            h.assertTrue(!off.engineOn() && off.fuel() == 1_000_000, "выключенный винт тратит топливо");
            h.assertTrue(off.physics().rotor == 0, "выключенный винт не остановился: " + off.physics().rotor);
            h.assertTrue(!dry.rotorOn(), "без топлива выключатель не упал");
            on.discard();
            off.discard();
            dry.discard();
            h.succeed();
        });
    }

    /** При прицеливании дуло посередине: на оси взгляда по горизонтали; от бедра на стороне руки (просьба владельца). */
    static void muzzleCentredWhenAiming(GameTestHelper h) {
        var p = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        p.setYRot(0);
        p.setXRot(-30);
        p.setShiftKeyDown(false);
        Vec3 hip = ru.iis.equipment.GunActions.muzzle(p);
        h.assertTrue(hip.x < p.getX() - 0.2, "от бедра дуло не справа: " + (hip.x - p.getX()));
        p.setShiftKeyDown(true);
        Vec3 aim = ru.iis.equipment.GunActions.muzzle(p);
        h.assertTrue(Math.abs(aim.x - p.getX()) < 1e-6, "при прицеливании дуло сбоку: " + (aim.x - p.getX()));
        Vec3 fromEye = aim.subtract(p.getEyePosition());
        h.assertTrue(fromEye.normalize().dot(p.getLookAngle()) > 0.98, "дуло далеко от взгляда");
        h.succeed();
    }

    /** Предметы D132: рецепт разобрался (ошибка разбора сама по себе тесты не роняет). Ветка аккумуляторов как в конфиге ИИС 1. */
    static void questItemsHaveRecipes(GameTestHelper h) {
        var recipes = h.getLevel().getServer().getRecipeManager();
        for (String id : new String[]{"processing/assembler/match", "processing/assembler/waterproof_kit", "processing/assembler/fluid_regulator_iv", "processing/blast/nickel_zinc_ferrite", "processing/blast/tungsten_carbide",
                "processing/chemical_reactor/polyphenylene_sulfide", "processing/blast/cobaltite_roast", "processing/fluid_solidifier/volumetric_flask", "processing/laser_engraver/wafer_soc2",
                "processing/assembly_line/energy_module", "processing/assembly_line/energy_cluster", "processing/assembly_line/ultimate_battery", "processing/chemical_bath/dark_concrete", "multi/powder_barrel", "multi/pyrolyse_casing", "processing/fluid_solidifier/light_concrete", "gt/scoop"}) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("iis", id));
            h.assertTrue(recipes.byKey(key).isPresent(), "нет рецепта " + id);
        }
        h.succeed();
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
        for (VehicleKind k : VehicleKind.values()) ids.add(k == VehicleKind.STEAM_CAR ? "equipment/vehicle_steam_car" : "processing/assembler/vehicle_" + k.id);
        for (String a : new String[]{"musketeer", "ballistic", "assault"}) for (String p : new String[]{"helmet", "chestplate", "leggings", "boots"}) ids.add("equipment/" + a + "_" + p);
        for (String a : new String[]{"power_armor", "plasma_armor"}) for (String p : new String[]{"helmet", "chestplate", "leggings", "boots"}) ids.add("processing/assembler/" + a + "_" + p);
        for (String p : new String[]{"wheel_wood", "wheel_rubber", "engine_steam", "engine_lv", "engine_mv", "engine_hv", "engine_ev", "jet_turbine", "fuel_pump", "reward_shop", "gun_spring"}) ids.add("equipment/" + p);
        for (String p : new String[]{"chassis_light", "chassis_heavy", "chassis_armored", "drivetrain", "body_panels", "dashboard", "wing", "propeller", "rotor_blades", "landing_gear", "track_unit", "boat_hull", "tank_turret", "cargo_kit", "trigger_group", "scope"}) ids.add("processing/assembler/" + p);
        for (Weapon.Ammo a : Weapon.Ammo.values()) ids.add(a == Weapon.Ammo.POWDER ? "equipment/powder_cartridge" : "processing/cartridge_press/" + a.id);
        for (Weapon.Magazine m : Weapon.Magazine.values()) ids.add("equipment/" + m.id);
        for (String id : ids) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, net.minecraft.resources.Identifier.fromNamespaceAndPath(ru.iis.IIS.MODID, id));
            if (recipes.byKey(key).isEmpty()) bad.add(id);
        }
        h.assertTrue(bad.isEmpty(), "нет рецептов: " + bad);
        h.succeed();
    }
}

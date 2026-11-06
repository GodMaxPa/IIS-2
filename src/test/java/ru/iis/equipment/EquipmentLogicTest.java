package ru.iis.equipment;

import org.junit.jupiter.api.Test;
import ru.iis.equipment.vehicle.VehicleKind;
import ru.iis.equipment.vehicle.VehiclePhysics;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Числа и физика снаряжения D128. */
class EquipmentLogicTest {
    private static VehiclePhysics.Input drive(boolean forward, boolean fuel) {
        return new VehiclePhysics.Input(forward, false, false, false, false, false, 0, 0, fuel, true);
    }

    private static VehiclePhysics.State grounded() {
        VehiclePhysics.State s = new VehiclePhysics.State();
        s.onGround = true;
        return s;
    }

    @Test
    void laterErasHitHarder() {
        // лучшая пушка эпохи сильнее лучшей пушки прошлой: иначе эпоха ничего не даёт
        List<String> eras = List.of("steam", "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv");
        double last = 0;
        for (String era : eras) {
            double best = 0;
            for (Weapon w : Weapon.values()) if (w.era.equals(era)) best = Math.max(best, w.dps());
            if (best == 0) continue;
            assertTrue(best > last, era + ": " + best + " не больше " + last);
            last = best;
        }
    }

    @Test
    void everyWeaponHasOneWayToShoot() {
        for (Weapon w : Weapon.values()) {
            assertTrue(w.electric() == (w.ammo == null), w.id);
            if (w.electric()) assertTrue(w.capacity >= w.euPerShot * 100, w.id + ": меньше ста выстрелов на заряд");
            else assertTrue(w.magazine > 0 && w.reload > 0, w.id);
            assertEquals(w.projectile(), w.projectileSpeed() > 0, w.id);
            assertEquals(w.kind == Weapon.Kind.GRENADE || w.kind == Weapon.Kind.ROCKET || w.kind == Weapon.Kind.PLASMA, w.blast > 0, w.id);
        }
    }

    @Test
    void damageFallsOffOnlyFarAway() {
        Weapon w = Weapon.MUSKET;
        assertEquals(w.damage, w.damageAt(0));
        assertEquals(w.damage, w.damageAt(w.range * 0.6));
        assertEquals(w.damage * 0.4, w.damageAt(w.range), 1e-9);
        assertEquals(0, Weapon.ROCKET_LAUNCHER.blastAt(Weapon.ROCKET_LAUNCHER.blast));
        assertEquals(Weapon.ROCKET_LAUNCHER.damage, Weapon.ROCKET_LAUNCHER.blastAt(0));
    }

    @Test
    void reloadTakesWhatIsMissing() {
        assertEquals(12, Weapon.PISTOL.reloadAmount(0, 64));
        assertEquals(5, Weapon.PISTOL.reloadAmount(7, 64));
        assertEquals(3, Weapon.PISTOL.reloadAmount(0, 3));
        assertEquals(0, Weapon.PISTOL.reloadAmount(12, 64));
    }

    @Test
    void carReachesItsTopSpeedAndNoFurther() {
        VehiclePhysics.State s = grounded();
        for (int i = 0; i < 600; i++) VehiclePhysics.step(VehicleKind.CAR, s, drive(true, true));
        assertTrue(s.speed > VehicleKind.CAR.maxSpeed * 0.9, "скорость " + s.speed);
        assertTrue(s.speed <= VehicleKind.CAR.maxSpeed + 1e-9);
    }

    @Test
    void withoutFuelTheCarOnlyCoasts() {
        VehiclePhysics.State s = grounded();
        for (int i = 0; i < 100; i++) VehiclePhysics.step(VehicleKind.CAR, s, drive(true, false));
        assertEquals(0, s.speed);
        s.speed = 0.5;
        for (int i = 0; i < 300; i++) VehiclePhysics.step(VehicleKind.CAR, s, drive(false, false));
        assertTrue(s.speed < 0.02, "катится " + s.speed);
    }

    @Test
    void wheelsDoNotTurnOnTheSpotButTracksDo() {
        VehiclePhysics.Input right = new VehiclePhysics.Input(false, false, false, true, false, false, 0, 0, true, true);
        VehiclePhysics.State car = grounded(), tank = grounded();
        VehiclePhysics.step(VehicleKind.CAR, car, right);
        VehiclePhysics.step(VehicleKind.TANK, tank, right);
        assertEquals(0F, car.yaw);
        assertTrue(tank.yaw > 0);
    }

    @Test
    void boatNeedsWater() {
        VehiclePhysics.State land = grounded();
        for (int i = 0; i < 40; i++) VehiclePhysics.step(VehicleKind.BOAT, land, drive(true, true));
        assertEquals(0, land.speed, 1e-9);
        VehiclePhysics.State afloat = new VehiclePhysics.State();
        afloat.inWater = true;
        afloat.depth = 0.4;
        for (int i = 0; i < 40; i++) VehiclePhysics.step(VehicleKind.BOAT, afloat, drive(true, true));
        assertTrue(afloat.speed > 0.2);
    }

    @Test
    void planeLiftsOnlyAboveStallSpeed() {
        VehiclePhysics.State s = grounded();
        VehiclePhysics.Input up = new VehiclePhysics.Input(true, false, false, false, false, false, 0, -30, true, true);
        double[] m = {0, 0, 0};
        for (int i = 0; i < 10; i++) m = VehiclePhysics.step(VehicleKind.PLANE, s, up);
        assertTrue(s.speed < VehicleKind.PLANE.stall);
        assertTrue(m[1] <= 0, "взлетел на скорости " + s.speed);
        for (int i = 0; i < 300 && m[1] <= 0; i++) m = VehiclePhysics.step(VehicleKind.PLANE, s, up);
        assertTrue(m[1] > 0, "не взлетел");
        s.onGround = false;
        for (int i = 0; i < 60; i++) m = VehiclePhysics.step(VehicleKind.PLANE, s, up);
        assertTrue(m[1] > 0.1, "не набирает высоту: " + m[1]);
    }

    @Test
    void planeWithoutEngineSinks() {
        VehiclePhysics.State s = new VehiclePhysics.State();
        s.speed = VehicleKind.PLANE.maxSpeed;
        s.throttle = 1;
        VehiclePhysics.Input none = new VehiclePhysics.Input(false, false, false, false, false, false, 0, 0, false, true);
        double[] m = {0, 0, 0};
        for (int i = 0; i < 400; i++) m = VehiclePhysics.step(VehicleKind.PLANE, s, none);
        assertTrue(m[1] < -0.2, "не снижается: " + m[1]);
    }

    @Test
    void helicopterRisesOnlyAfterSpinUp() {
        VehiclePhysics.State s = grounded();
        VehiclePhysics.Input up = new VehiclePhysics.Input(false, false, false, false, true, false, 0, 0, true, true);
        double[] m = VehiclePhysics.step(VehicleKind.HELICOPTER, s, up);
        assertTrue(m[1] <= 0, "взлетел без раскрутки");
        for (int i = 0; i < 200; i++) m = VehiclePhysics.step(VehicleKind.HELICOPTER, s, up);
        assertTrue(m[1] > 0.2, "не поднимается: " + m[1]);
    }

    @Test
    void helicopterWithoutFuelComesDownSlowly() {
        VehiclePhysics.State s = new VehiclePhysics.State();
        s.rotor = 1;
        VehiclePhysics.Input none = new VehiclePhysics.Input(false, false, false, false, false, false, 0, 0, false, true);
        double[] m = {0, 0, 0};
        for (int i = 0; i < 40; i++) m = VehiclePhysics.step(VehicleKind.HELICOPTER, s, none);
        assertTrue(m[1] < 0 && m[1] > -0.4, "скорость снижения " + m[1]);
    }

    @Test
    void onlyASuddenStopIsACrash() {
        assertTrue(VehiclePhysics.crashed(0.8, 0.05));
        assertFalse(VehiclePhysics.crashed(0.8, 0.5), "притормозил");
        assertFalse(VehiclePhysics.crashed(0.3, 0), "шагом в стену");
        assertFalse(VehiclePhysics.crashed(50, 0), "телепорт");
    }

    @Test
    void eachVehicleCanCarrySomeone() {
        for (VehicleKind k : VehicleKind.values()) {
            assertTrue(k.seats.length > 0, k.id);
            assertTrue(k.tank / k.burn > 20 * 60 * 5, k.id + ": меньше пяти минут на баке");
        }
        assertFalse(VehicleKind.CAR.flies());
        assertTrue(VehicleKind.JET.flies());
    }
}

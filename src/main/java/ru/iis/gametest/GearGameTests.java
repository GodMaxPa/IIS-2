package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import ru.iis.gear.*;
import ru.iis.lv.item.BatteryItem;

/** Снаряжение IC2 (D118). */
final class GearGameTests {
    private GearGameTests() {}

    private static final BlockPos POS = new BlockPos(4, 2, 4);

    private static ItemStack charged(net.minecraft.world.level.ItemLike item, long eu) {
        ItemStack s = new ItemStack(item);
        BatteryItem.setCharge(s, eu);
        return s;
    }

    private static Zombie zombie(GameTestHelper h) {
        Zombie z = h.spawn(EntityType.ZOMBIE, POS.above());
        z.setNoAi(true);
        return z;
    }

    /** Полная заряженная квантовая броня: урона нет, заряд списан. */
    static void quantumAbsorbs(GameTestHelper h) {
        Zombie z = zombie(h);
        z.setItemSlot(EquipmentSlot.HEAD, charged(GearContent.QUANTUM_HELMET, 10_000_000));
        z.setItemSlot(EquipmentSlot.CHEST, charged(GearContent.QUANTUM_CHESTPLATE, 10_000_000));
        z.setItemSlot(EquipmentSlot.LEGS, charged(GearContent.QUANTUM_LEGGINGS, 10_000_000));
        z.setItemSlot(EquipmentSlot.FEET, charged(GearContent.QUANTUM_BOOTS, 10_000_000));
        float hp = z.getHealth();
        z.hurtServer(h.getLevel(), h.getLevel().damageSources().cactus(), 5);
        h.assertTrue(z.getHealth() == hp, "took damage: " + z.getHealth());
        h.assertTrue(BatteryItem.charge(z.getItemBySlot(EquipmentSlot.CHEST)) == 10_000_000 - 40_000, "chest " + BatteryItem.charge(z.getItemBySlot(EquipmentSlot.CHEST)));
        h.succeed();
    }

    /** Без заряда нано-броня не защищает. */
    static void emptyNanoDoesNotProtect(GameTestHelper h) {
        Zombie z = zombie(h);
        z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(GearContent.NANO_CHESTPLATE.get()));
        float hp = z.getHealth();
        z.hurtServer(h.getLevel(), h.getLevel().damageSources().cactus(), 5);
        h.assertTrue(z.getHealth() < hp - 4, "health " + z.getHealth());   // у зомби своя броня чуть гасит урон
        h.succeed();
    }

    /** Ранец заряжает инструмент на панели быстрого доступа; заряд переходит, а не появляется. */
    static void packChargesHotbar(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pack = charged(GearContent.ADV_BATPACK, 600_000);
        ItemStack laser = new ItemStack(ru.iis.lv.LvContent.BATTERY_LV.get());
        p.getInventory().setItem(0, laser);
        GearEvents.chargeFrom(p, pack, 2, 1000);
        long got = BatteryItem.charge(laser);
        h.assertTrue(got > 0, "not charged");
        h.assertTrue(BatteryItem.charge(pack) == 600_000 - got, "pack " + BatteryItem.charge(pack));
        h.succeed();
    }

    /** Предмет выше уровня источника не заряжается. */
    static void packRespectsTier(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pack = charged(GearContent.BATPACK, 60_000);
        ItemStack crystal = new ItemStack(GearContent.ENERGY_CRYSTAL.get());
        p.getInventory().setItem(0, crystal);
        GearEvents.chargeFrom(p, pack, 1, 100);
        h.assertTrue(BatteryItem.charge(crystal) == 0, "crystal charged");
        h.succeed();
    }

    /** Хранилище: верхний ряд заряжает из запаса, нижний разряжает в запас. */
    static void storageSlots(GameTestHelper h) {
        h.setBlock(POS, GearContent.CESU.get());
        EnergyStorageBlockEntity s = h.getBlockEntity(POS, EnergyStorageBlockEntity.class);
        s.energy.stored = 100_000;
        ItemStack in = new ItemStack(GearContent.BATTERY_ADV.get());
        ItemStack out = charged(GearContent.BATTERY_ADV, 50_000);
        s.setItem(0, in);
        s.setItem(2, out);
        h.succeedWhen(() -> {
            h.assertTrue(BatteryItem.charge(s.getItem(0)) > 0, "top not charged");
            h.assertTrue(BatteryItem.charge(s.getItem(2)) < 50_000, "bottom not discharged");
        });
    }

    /** Хранилище отдаёт напряжение своего уровня в лицевую сторону. */
    static void storageOutputsFront(GameTestHelper h) {
        h.setBlock(POS, GearContent.MFSU.get());
        EnergyStorageBlockEntity s = h.getBlockEntity(POS, EnergyStorageBlockEntity.class);
        h.assertTrue(s.outputVoltage() == 2048, "voltage " + s.outputVoltage());
        h.assertTrue(s.energy.capacity == 40_000_000, "capacity " + s.energy.capacity);
        Direction front = s.getBlockState().getValue(ru.iis.machine.block.MachineBlock.FACING);
        h.assertTrue(s.outputsEnergyTo(front) && !s.inputsEnergyFrom(front), "front");
        h.assertTrue(s.inputsEnergyFrom(front.getOpposite()) && !s.outputsEnergyTo(front.getOpposite()), "back");
        h.succeed();
    }

    /** Лазер ломает камень по лучу и тратит заряд; обсидиан не пробивает. */
    static void laserBreaksStone(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        h.setBlock(POS.east(1), Blocks.STONE);
        h.setBlock(POS.east(2), Blocks.STONE);
        h.setBlock(POS.east(3), Blocks.OBSIDIAN);
        ItemStack laser = charged(GearContent.MINING_LASER, 300_000);
        Vec3 from = Vec3.atCenterOf(h.absolutePos(POS));
        h.assertTrue(MiningLaserItem.fire(h.getLevel(), p, laser, from, new Vec3(1, 0, 0)), "not fired");
        h.assertBlockNotPresent(Blocks.STONE, POS.east(1));
        h.assertBlockNotPresent(Blocks.STONE, POS.east(2));
        h.assertBlockPresent(Blocks.OBSIDIAN, POS.east(3));
        h.assertTrue(BatteryItem.charge(laser) == 300_000 - 1250, "charge " + BatteryItem.charge(laser));
        h.succeed();
    }

    /** Без заряда лазер не стреляет. */
    static void laserNeedsCharge(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        h.setBlock(POS.east(1), Blocks.STONE);
        ItemStack laser = charged(GearContent.MINING_LASER, 100);
        h.assertTrue(!MiningLaserItem.fire(h.getLevel(), p, laser, Vec3.atCenterOf(h.absolutePos(POS)), new Vec3(1, 0, 0)), "fired");
        h.assertBlockPresent(Blocks.STONE, POS.east(1));
        h.succeed();
    }

    /** Перегрев: булыжник падает камнем. */
    static void laserSuperHeatSmelts(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        h.setBlock(POS.east(1), Blocks.COBBLESTONE);
        ItemStack laser = charged(GearContent.MINING_LASER, 300_000);
        laser.set(GearContent.MODE.get(), GearLogic.LaserMode.SUPER_HEAT.ordinal());
        MiningLaserItem.fire(h.getLevel(), p, laser, Vec3.atCenterOf(h.absolutePos(POS)), new Vec3(1, 0, 0));
        h.succeedWhen(() -> h.assertItemEntityPresent(Blocks.STONE.asItem()));
    }
}

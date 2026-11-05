package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import ru.iis.IIS;
import ru.iis.fluid.GtFluids;
import ru.iis.space.*;

/** Космос, первый этап (D115): Луна, кислород, ракета 1-го уровня. */
final class SpaceGameTests {
    private SpaceGameTests() {}

    private static RocketEntity rocket(GameTestHelper h, BlockPos p, int fuel) {
        RocketEntity r = SpaceContent.ROCKET.get().create(h.getLevel(), EntitySpawnReason.TRIGGERED);
        BlockPos a = h.absolutePos(p);
        r.setPos(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        r.setup(new RocketItem.Stored(18, fuel));
        h.getLevel().addFreshEntity(r);
        return r;
    }

    /** Сами измерения из датапаков GameTest-сервер не загружает (см. SpaceLiveCheck): проверяем тип, биом и рельеф Луны через её генератор. */
    static void moonTerrain(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        Identifier id = Identifier.fromNamespaceAndPath(IIS.MODID, "moon");
        h.assertTrue(reg.lookupOrThrow(Registries.DIMENSION_TYPE).containsKey(id), "no dimension type");
        var settings = reg.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(ResourceKey.create(Registries.NOISE_SETTINGS, id));
        var biome = reg.lookupOrThrow(Registries.BIOME).getOrThrow(ResourceKey.create(Registries.BIOME, id));
        var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), settings);
        RandomState random = RandomState.create(settings.value(), reg.lookupOrThrow(Registries.NOISE), 42L);
        int y = generator.getBaseHeight(8, 8, Heightmap.Types.WORLD_SURFACE_WG, h.getLevel(), random) - 1;
        h.assertTrue(y > 40 && y < 110, "surface at " + y);
        BlockState rock = generator.getBaseColumn(8, 8, h.getLevel(), random).getBlock(y - 5);
        h.assertTrue(rock.is(SpaceContent.MOON_ROCK.get()), "below the surface: " + rock);
        h.assertTrue(rock.is(BlockTags.STONE_ORE_REPLACEABLES), "GT veins must replace moon rock");
        h.succeed();
    }

    /** Если измерения назначения нет, ракета садится, а не исчезает; на подъём тратит топливо. */
    static void rocketWithoutTargetLands(GameTestHelper h) {
        RocketEntity r = rocket(h, new BlockPos(4, 2, 4), RocketEntity.CAPACITY_T1);
        double x = r.getX(), z = r.getZ();
        r.setState(RocketEntity.FLYING);
        r.setPos(x, RocketEntity.ARRIVAL_ALTITUDE - 1, z);
        h.succeedWhen(() -> {
            h.assertTrue(!r.isRemoved(), "rocket removed");
            h.assertTrue(r.state() == RocketEntity.LANDING, "state " + r.state());
            h.assertTrue(r.fuel() < RocketEntity.CAPACITY_T1 && r.cargoSlots() == 18, "fuel " + r.fuel());
            r.discard();
        });
    }

    /** На Луну хватает полного бака и на обратный путь, с неполным не взлететь. */
    static void flightCostFitsTheTank(GameTestHelper h) {
        int up = RocketEntity.flightCost(net.minecraft.world.level.Level.OVERWORLD, 64), back = RocketEntity.flightCost(SpaceContent.MOON, 70);
        h.assertTrue(up + back <= RocketEntity.CAPACITY_T1, "cost " + up + " + " + back);
        h.assertTrue(back < up, "the moon must cost less");
        h.assertTrue(RocketEntity.CAPACITY_T1 == 200000 && RocketEntity.fuelMultiplier(SpaceContent.MOON) == 0.7, "GC numbers");
        h.succeed();
    }

    /** Загрузчик заправляет ракету рядом и берёт только ракетное топливо. */
    static void fuelLoaderFillsRocket(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 2, 4);
        h.setBlock(p, SpaceContent.FUEL_LOADER.get());
        FuelLoaderBlockEntity be = h.getBlockEntity(p, FuelLoaderBlockEntity.class);
        be.tank.setAmount(GtFluids.get("rocket_fuel").source().get(), 1000);
        RocketEntity r = rocket(h, new BlockPos(6, 2, 4), 0);
        h.succeedWhen(() -> {
            h.assertTrue(r.fuel() == 1000, "fuel " + r.fuel());
            h.assertTrue(be.tank.amount() == 0, "tank " + be.tank.amount());
        });
    }

    /** Предмет ставит ракету только на целую площадку 3×3. */
    static void rocketNeedsFullPad(GameTestHelper h) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) h.setBlock(new BlockPos(4 + dx, 2, 4 + dz), SpaceContent.LAUNCH_PAD.get());
        BlockPos center = h.absolutePos(new BlockPos(4, 2, 4));
        h.assertTrue(RocketItem.fullPad(h.getLevel(), center), "full pad");
        h.setBlock(new BlockPos(3, 2, 3), net.minecraft.world.level.block.Blocks.AIR);
        h.assertFalse(RocketItem.fullPad(h.getLevel(), center), "broken pad");
        h.succeed();
    }

    /** Верстак: без двигателя ничего, сундуки дают груз. */
    static void workbenchAssemblesRocket(GameTestHelper h) {
        SimpleContainer parts = new SimpleContainer(NasaWorkbenchMenu.PARTS);
        for (int i = 0; i < NasaWorkbenchMenu.CHESTS; i++) parts.setItem(i, new ItemStack(NasaWorkbenchMenu.required(i)));
        parts.setItem(NasaWorkbenchMenu.ENGINE, ItemStack.EMPTY);
        h.assertTrue(NasaWorkbenchMenu.assemble(parts).isEmpty(), "no engine");
        parts.setItem(NasaWorkbenchMenu.ENGINE, new ItemStack(SpaceContent.ROCKET_ENGINE_T1.get()));
        parts.setItem(NasaWorkbenchMenu.CHESTS, new ItemStack(Items.CHEST));
        parts.setItem(NasaWorkbenchMenu.CHESTS + 2, new ItemStack(Items.CHEST));
        ItemStack out = NasaWorkbenchMenu.assemble(parts);
        h.assertTrue(out.is(SpaceContent.ROCKET_T1.get()) && out.get(SpaceContent.ROCKET_DATA.get()).cargo() == 36, "result " + out);
        h.succeed();
    }

    /** Кислород: нужны маска, оборудование и баллон; единица за вдох. */
    static void oxygenNeedsAllThree(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(OxygenSystem.breathe(p) == -1, "breathing with nothing");
        p.getInventory().add(new ItemStack(SpaceContent.OXYGEN_MASK.get()));
        p.getInventory().add(new ItemStack(SpaceContent.OXYGEN_GEAR.get()));
        h.assertTrue(OxygenSystem.breathe(p) == -1, "breathing without a tank");
        ItemStack tank = new ItemStack(SpaceContent.OXYGEN_TANK_LIGHT.get());
        p.getInventory().add(tank);
        h.assertTrue(OxygenSystem.breathe(p) == OxygenSystem.TANK_LIGHT - 1, "first breath");
        ItemStack held = p.getInventory().getItem(p.getInventory().findSlotMatchingItem(new ItemStack(SpaceContent.OXYGEN_MASK.get())) + 2);
        h.assertTrue(held.getDamageValue() == 1, "tank " + held);
        h.assertTrue(OxygenSystem.airless(SpaceContent.MOON) && !OxygenSystem.airless(net.minecraft.world.level.Level.OVERWORLD), "airless");
        h.succeed();
    }

    /** Тяжесть GC: 0.08 − 0.062 = 0.018; снимается при возвращении. */
    static void moonGravity(GameTestHelper h) {
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        LowGravity.apply(z, true);
        h.assertTrue(Math.abs(z.getAttributeValue(Attributes.GRAVITY) - 0.018) < 1e-6, "gravity " + z.getAttributeValue(Attributes.GRAVITY));
        h.assertTrue(Math.abs(z.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER) - 0.18) < 1e-6, "fall " + z.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
        LowGravity.apply(z, false);
        h.assertTrue(Math.abs(z.getAttributeValue(Attributes.GRAVITY) - 0.08) < 1e-6, "gravity back " + z.getAttributeValue(Attributes.GRAVITY));
        h.succeed();
    }
}

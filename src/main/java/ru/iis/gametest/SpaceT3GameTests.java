package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import ru.iis.space.LowGravity;
import ru.iis.space.NasaWorkbenchMenu;
import ru.iis.space.OxygenSystem;
import ru.iis.space.RocketEntity;
import ru.iis.space.RocketItem;
import ru.iis.space.SpaceContent;
import ru.iis.space.SpaceMachineBlockEntity;

/** Космос, третий этап (D134): ракета 3-го уровня, астероиды, кислородные машины, загрузчик груза. */
final class SpaceT3GameTests {
    private SpaceT3GameTests() {}

    /** Тяжёлые детали и два ускорителя дают ракету 3-го уровня; её бак больше, с Земли она летит и на астероиды. */
    static void rocketTier3(GameTestHelper h) {
        SimpleContainer parts = new SimpleContainer(NasaWorkbenchMenu.PARTS);
        for (int i = 0; i < NasaWorkbenchMenu.CHESTS; i++) parts.setItem(i, new ItemStack(NasaWorkbenchMenu.heavy(i)));
        h.assertTrue(NasaWorkbenchMenu.assemble(parts).isEmpty(), "без ускорителей собралось");
        parts.setItem(NasaWorkbenchMenu.BOOSTERS, new ItemStack(SpaceContent.ROCKET_BOOSTER.get()));
        parts.setItem(NasaWorkbenchMenu.BOOSTERS + 1, new ItemStack(SpaceContent.ROCKET_BOOSTER.get()));
        h.assertTrue(NasaWorkbenchMenu.assemble(parts).is(SpaceContent.ROCKET_T3.get()), "тяжёлые детали не дают 3-й уровень");
        parts.setItem(NasaWorkbenchMenu.PLATES, new ItemStack(SpaceContent.HEAVY_PLATING_T2.get()));
        h.assertTrue(NasaWorkbenchMenu.assemble(parts).isEmpty(), "покрытия вперемешку");
        RocketEntity r = SpaceContent.ROCKET.get().create(h.getLevel(), EntitySpawnReason.TRIGGERED);
        r.setPos(h.absoluteVec(new Vec3(4.5, 2, 4.5)));
        r.setTier(3);
        r.setup(new RocketItem.Stored(0, RocketEntity.CAPACITY_T3));
        h.assertTrue(r.fuel() == 400000, "бак 3-го уровня " + r.fuel());
        r.setTarget(RocketEntity.TARGET_ASTEROIDS);
        h.assertTrue(r.destination() == SpaceContent.ASTEROIDS, "3-й уровень на астероиды: " + r.destination());
        r.setTier(2);
        h.assertTrue(r.destination() == SpaceContent.MARS, "2-й уровень на астероиды не летает: " + r.destination());
        h.assertTrue(OxygenSystem.airless(SpaceContent.ASTEROIDS), "на астероидах есть воздух");
        var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel(), EntitySpawnReason.TRIGGERED);
        LowGravity.apply(z, SpaceContent.ASTEROIDS);
        h.assertTrue(Math.abs(z.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY) - (0.08 - LowGravity.ASTEROID_GRAVITY)) < 1e-6, "тяжесть астероидов");
        for (String id : new String[]{"space/heavy_ingot_t3", "processing/implosion/heavy_plating_t3", "space/heavy_rocket_engine", "space/heavy_nose_cone", "space/heavy_rocket_fins",
                "space/rocket_booster", "space/oxygen_collector", "space/oxygen_distributor", "space/cargo_loader", "space/cargo_unloader", "processing/implosion/compressed_desh"}) {
            var key = net.minecraft.resources.ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("iis", id));
            h.assertTrue(h.getLevel().recipeAccess().byKey(key).isPresent(), "нет рецепта " + id);
        }
        h.succeed();
    }

    /** На Земле сборщик набирает кислород из воздуха; распределитель с кислородом держит пузырь, без него пузырь гаснет. */
    static void oxygenMachines(GameTestHelper h) {
        BlockPos c = new BlockPos(2, 2, 2), d = new BlockPos(6, 2, 6);
        h.setBlock(c, SpaceContent.OXYGEN_COLLECTOR.get());
        h.setBlock(d, SpaceContent.OXYGEN_DISTRIBUTOR.get());
        var dist = (SpaceMachineBlockEntity) h.getBlockEntity(d, SpaceMachineBlockEntity.class);
        dist.tank.setAmount(ru.iis.fluid.GtFluids.get("oxygen").source().get(), 1000);
        h.runAtTickTime(100, () -> {
            var col = (SpaceMachineBlockEntity) h.getBlockEntity(c, SpaceMachineBlockEntity.class);
            h.assertTrue(col.tank.amount() >= 60, "сборщик набрал " + col.tank.amount());
            h.assertTrue(dist.bubble() >= 3, "пузырь " + dist.bubble());
            h.assertTrue(SpaceMachineBlockEntity.inBubble(h.getLevel(), h.absoluteVec(new Vec3(6.5, 3, 7.5))), "точка рядом не в пузыре");
            h.assertTrue(!SpaceMachineBlockEntity.inBubble(h.getLevel(), h.absoluteVec(new Vec3(6.5, 3, 40))), "далёкая точка в пузыре");
            h.setBlock(d, Blocks.AIR);
            h.assertTrue(!SpaceMachineBlockEntity.inBubble(h.getLevel(), h.absoluteVec(new Vec3(6.5, 3, 7.5))), "пузырь остался без распределителя");
            h.succeed();
        });
    }

    /** Загрузчик перекладывает предметы из соседнего сундука в ракету рядом. */
    static void cargoLoader(GameTestHelper h) {
        BlockPos loader = new BlockPos(2, 2, 2), chest = new BlockPos(1, 2, 2);
        h.setBlock(loader, SpaceContent.CARGO_LOADER.get());
        h.setBlock(chest, Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(chest, ChestBlockEntity.class)).setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        RocketEntity r = SpaceContent.ROCKET.get().create(h.getLevel(), EntitySpawnReason.TRIGGERED);
        r.setPos(h.absoluteVec(new Vec3(4.5, 2, 2.5)));
        r.setup(new RocketItem.Stored(18, 0));
        h.getLevel().addFreshEntity(r);
        h.runAtTickTime(80, () -> {
            int n = 0;
            for (int i = 0; i < r.cargo().getContainerSize(); i++) if (r.cargo().getItem(i).is(Items.IRON_INGOT)) n += r.cargo().getItem(i).getCount();
            h.assertTrue(n == 5, "в ракете слитков " + n);
            r.discard();
            h.succeed();
        });
    }
}

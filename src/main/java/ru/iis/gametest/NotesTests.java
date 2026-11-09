package ru.iis.gametest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import ru.iis.gear.GearContent;
import ru.iis.machine.logic.BoilerLogic;
import ru.iis.shop.ShopContent;
import ru.iis.tool.GtTools;

import java.nio.file.Files;
import java.nio.file.Path;

/** Правки владельца 09.11 (D144). */
final class NotesTests {
    private NotesTests() {}

    private static boolean recipe(GameTestHelper h, String id) {
        return h.getLevel().recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).isPresent();
    }

    /** Магазин продаёт еду без «видел»; рецепты как в ИИС 1; котёл, ферма, инструменты на месте. */
    static void notes(GameTestHelper h) {
        var bread = ShopContent.prices().get("minecraft:bread");
        h.assertTrue(bread != null && bread.always(), "хлеб не продаётся сразу");
        h.assertTrue(ShopContent.allowed(bread, java.util.List.of(), "minecraft:bread"), "еда не покупается без «видел»");
        var cobble = ShopContent.prices().get("minecraft:cobblestone");
        h.assertTrue(cobble != null && !ShopContent.allowed(cobble, java.util.List.of(), "minecraft:cobblestone")
                && ShopContent.allowed(cobble, java.util.List.of("minecraft:cobblestone"), "minecraft:cobblestone"), "сырьё продаётся без «видел»");

        for (String id : new String[]{"minecraft:torch", "minecraft:iron_block", "minecraft:iron_ingot_from_iron_block", "minecraft:bucket", "minecraft:charcoal",
                "minecraft:repeater", "minecraft:iron_helmet", "minecraft:paper", "minecraft:tnt"}) {
            h.assertTrue(!recipe(h, id), "ванильный рецепт не выключен: " + id);
        }
        for (String id : new String[]{"iis:iis1/torch_resin", "iis:iis1/bucket", "iis:iis1/iron_helmet", "iis:iis1/paper", "iis:processing/assembler/repeater",
                "iis:processing/assembler/dispenser", "iis:machine/boiler_solar_steel", "iis:multi/tree_farm", "iis:processing/tree_farm/oak_sapling",
                "iis:processing/tree_farm/rubber_sapling_fertilizer", "iis:gear/vajra", "iis:gear/gravitool"}) {
            h.assertTrue(recipe(h, id), "нет рецепта " + id);
        }

        h.assertTrue(BoilerLogic.Kind.SOLAR_STEEL.solarMax() == 3 * BoilerLogic.Kind.SOLAR_BRONZE.solarMax(), "стальной солнечный котёл не втрое сильнее");
        for (String id : new String[]{"iis:drill_mv_steel", "iis:drill_hv_steel", "iis:jackhammer_steel", "iis:buzzsaw_steel", "iis:screwdriver_lv_steel",
                "iis:sense_steel", "iis:plow_steel", "iis:branch_cutter_steel", "iis:butchery_knife_steel", "iis:plunger_steel", "iis:universal_spade_steel",
                "iis:boiler_solar_steel", "iis:tree_farm"}) {
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.parse(id)), "нет предмета " + id);
        }
        h.assertTrue(GtTools.electricTier("drill_hv") == 3 && GtTools.electricTier("wrench_mv") == 2 && GtTools.electricTier("buzzsaw") == 1, "уровни электроинструментов");
        ItemStack vajra = new ItemStack(GearContent.VAJRA.get());
        ru.iis.lv.item.BatteryItem.setCharge(vajra, 1_000_000);
        h.assertTrue(vajra.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()) && vajra.getDestroySpeed(Blocks.OBSIDIAN.defaultBlockState()) > 100, "ваджра не берёт обсидиан");
        h.assertTrue(ru.iis.tool.ToolBehaviours.saplingFor(Blocks.OAK_LEAVES) == Items.OAK_SAPLING, "сучкорез: дуб -> саженец");
        h.succeed();
    }

    /** Бэкап: архив пишется, старые сверх 20 удаляются. */
    static void backups(GameTestHelper h) {
        try {
            Path root = Files.createTempDirectory("iis-world");
            Files.writeString(root.resolve("level.dat"), "x");
            Files.createDirectories(root.resolve("region"));
            Files.writeString(root.resolve("region").resolve("r.0.0.mca"), "y");
            Files.writeString(root.resolve("session.lock"), "lock");
            Path dir = Files.createTempDirectory("iis-backups");
            for (int i = 0; i < ru.iis.world.WorldBackups.KEEP + 3; i++) Files.writeString(dir.resolve(String.format("2026-01-01_00-00-%02d_day%d.zip", i, i)), "old");
            Path zip = dir.resolve("2027-01-01_00-00-00_day99.zip");
            ru.iis.world.WorldBackups.write(root, zip);
            ru.iis.world.WorldBackups.prune(dir);
            long left;
            try (var s = Files.list(dir)) { left = s.filter(f -> f.toString().endsWith(".zip")).count(); }
            h.assertTrue(Files.exists(zip) && Files.size(zip) > 0, "архив не записан");
            h.assertTrue(left == ru.iis.world.WorldBackups.KEEP, "осталось архивов: " + left);
            try (var z = new java.util.zip.ZipFile(zip.toFile())) {
                h.assertTrue(z.getEntry("region/r.0.0.mca") != null && z.getEntry("session.lock") == null, "в архиве не то");
            }
        } catch (java.io.IOException e) {
            h.fail(net.minecraft.network.chat.Component.literal("ошибка бэкапа: " + e));
        }
        h.succeed();
    }
}

package ru.iis.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import ru.iis.IIS;

import java.util.ArrayList;
import java.util.List;

/**
 * /iis selftest — проверка механик среза прямо на сервере (рецепты, инструменты, молот, котёл).
 * Нужна автотесту scripts/smoke-server.sh: в конце печатает "SELFTEST PASSED" или "SELFTEST FAILED".
 */
final class SelfTest {
    private SelfTest() {}

    private record Ctx(CommandSourceStack src, ServerLevel level, List<String> failures) {
        void check(String name, boolean ok, String details) {
            src.sendSuccess(() -> Component.literal((ok ? "  PASS " : "  FAIL ") + name + (ok ? "" : " — " + details)), false);
            if (!ok) failures.add(name);
        }
    }

    static int run(CommandSourceStack src) {
        Ctx c = new Ctx(src, src.getLevel(), new ArrayList<>());
        src.sendSuccess(() -> Component.literal("IIS selftest:"), false);
        try {
            recipes(c);
            hammerMining(c);
            cauldron(c);
        } catch (Exception e) {
            IIS.LOGGER.error("selftest crashed", e);
            c.check("no exceptions", false, e.toString());
        }
        boolean ok = c.failures.isEmpty();
        src.sendSuccess(() -> Component.literal(ok ? "SELFTEST PASSED" : "SELFTEST FAILED: " + c.failures), false);
        return ok ? 1 : 0;
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id.contains(":") ? id : IIS.MODID + ":" + id));
    }

    static ItemStack stack(String id) { return new ItemStack(item(id)); }

    /** Крафт в сетке width x height; "" — пустая клетка. Возвращает [результат, остаток в клетке 0..]. */
    static List<ItemStack> craft(Ctx c, int w, int h, ItemStack... grid) {
        CraftingInput input = CraftingInput.of(w, h, List.of(grid));
        var recipe = c.level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, c.level);
        List<ItemStack> out = new ArrayList<>();
        if (recipe.isEmpty()) { out.add(ItemStack.EMPTY); return out; }
        lastRecipe = recipe.get().id().identifier().toString();
        out.add(recipe.get().value().assemble(input));
        NonNullList<ItemStack> rem = recipe.get().value().getRemainingItems(input);
        out.addAll(rem);
        return out;
    }

    static String lastRecipe = "";

    static ItemStack smelt(Ctx c, ItemStack in) {
        var r = c.level.getServer().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(in), c.level);
        return r.map(h -> h.value().assemble(new SingleRecipeInput(in))).orElse(ItemStack.EMPTY);
    }

    static boolean is(ItemStack s, String id, int count) {
        return s.getItem() == item(id) && s.getCount() == count;
    }

    static String show(ItemStack s) {
        return s.isEmpty() ? "nothing" : s.getCount() + "x " + BuiltInRegistries.ITEM.getKey(s.getItem());
    }

    static void recipes(Ctx c) {
        ItemStack e = ItemStack.EMPTY;
        // Печь: халькопирит -> медный слиток; касситерит -> 2 оловянных слитка (множитель плавки 2); незерачная руда x2
        ItemStack r = smelt(c, stack("ore_chalcopyrite"));
        c.check("furnace: chalcopyrite ore -> copper ingot", is(r, "minecraft:copper_ingot", 1), show(r));
        r = smelt(c, stack("ore_cassiterite"));
        c.check("furnace: cassiterite ore -> 2 tin ingots", is(r, "ingot_tin", 2), show(r));
        r = smelt(c, stack("crushed_chalcopyrite"));
        c.check("furnace: crushed chalcopyrite -> 6 copper nuggets", is(r, "minecraft:copper_nugget", 6), show(r));
        r = smelt(c, stack("dust_bronze"));
        c.check("furnace: bronze dust -> bronze ingot", is(r, "ingot_bronze", 1), show(r));

        // Ступка: слиток -> пыль, ступка теряет 8 прочности и остаётся в сетке
        List<ItemStack> res = craft(c, 1, 2, stack("minecraft:copper_ingot"), stack("mortar_flint"));
        c.check("mortar: copper ingot -> copper dust", is(res.get(0), "dust_copper", 1), show(res.get(0)));
        ItemStack mortarLeft = res.size() > 2 ? res.get(2) : e;
        c.check("mortar is damaged, not consumed", mortarLeft.getItem() == item("mortar_flint")
                && mortarLeft.getOrDefault(DataComponents.DAMAGE, 0) == 8, show(mortarLeft) + " dmg=" + mortarLeft.getOrDefault(DataComponents.DAMAGE, 0));

        // Бронза: 3 медных + 1 оловянная пыль -> 3 бронзовые (ИИС: NerfDustCrafting)
        res = craft(c, 2, 2, stack("dust_copper"), stack("dust_copper"), stack("dust_copper"), stack("dust_tin"));
        c.check("bronze dust 3+1 -> 3", is(res.get(0), "dust_bronze", 3), show(res.get(0)));

        // Молот: молот + 2 слитка (вертикально) -> пластина
        res = craft(c, 1, 3, stack("hammer_bronze"), stack("ingot_bronze"), stack("ingot_bronze"));
        c.check("hammer: 2 bronze ingots -> bronze plate", is(res.get(0), "plate_bronze", 1), show(res.get(0)));

        // Молот из слитков, напильник из пластин
        res = craft(c, 3, 3, stack("ingot_bronze"), stack("ingot_bronze"), e, stack("ingot_bronze"), stack("ingot_bronze"),
                stack("minecraft:stick"), stack("ingot_bronze"), stack("ingot_bronze"), e);
        c.check("bronze hammer recipe", is(res.get(0), "hammer_bronze", 1), show(res.get(0)));
        res = craft(c, 1, 3, stack("plate_bronze"), stack("plate_bronze"), stack("minecraft:stick"));
        c.check("bronze file recipe", is(res.get(0), "file_bronze", 1), show(res.get(0)));

        // Наконечник кирки: P I I / f _ h, потом наконечник + палка
        res = craft(c, 3, 2, stack("plate_bronze"), stack("ingot_bronze"), stack("ingot_bronze"),
                stack("file_bronze"), e, stack("hammer_bronze"));
        c.check("bronze pickaxe head", is(res.get(0), "pickaxe_head_bronze", 1), show(res.get(0)));
        res = craft(c, 2, 1, stack("pickaxe_head_bronze"), stack("minecraft:stick"));
        c.check("bronze pickaxe", is(res.get(0), "pickaxe_bronze", 1), show(res.get(0)));

        // Ванильные металлические инструменты не крафтятся (ИИС ToolFix.zs)
        res = craft(c, 3, 3, stack("minecraft:iron_ingot"), stack("minecraft:iron_ingot"), stack("minecraft:iron_ingot"),
                e, stack("minecraft:stick"), e, e, stack("minecraft:stick"), e);
        c.check("vanilla iron pickaxe is disabled", res.get(0).isEmpty(), show(res.get(0)) + " via " + lastRecipe);

        // Бронзовая кирка добывает железный уровень (руды с уровнем GT 2)
        ItemStack pick = stack("pickaxe_bronze");
        var ironLevelOre = BuiltInRegistries.BLOCK.getValue(Identifier.parse("iis:ore_magnetite")).defaultBlockState();
        c.check("bronze pickaxe harvests iron-level ore", pick.isCorrectToolForDrops(ironLevelOre), "not correct tool");
        c.check("stone pickaxe can't harvest iron-level ore", !stack("minecraft:stone_pickaxe").isCorrectToolForDrops(ironLevelOre), "is correct tool");

        long ours = c.level.getServer().getRecipeManager().getRecipes().stream()
                .filter(h -> h.id().identifier().getNamespace().equals(IIS.MODID)).count();
        c.check("IIS recipes loaded (>= 800)", ours >= 800, "count=" + ours);
    }

    static void hammerMining(Ctx c) {
        ServerLevel level = c.level;
        BlockPos pos = BlockPos.containing(c.src.getPosition()).atY(level.getMaxY() - 10);
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack("hammer_bronze"));
        level.setBlock(pos, BuiltInRegistries.BLOCK.getValue(Identifier.parse("iis:ore_cassiterite")).defaultBlockState(), 3);
        boolean destroyed = player.gameMode.destroyBlock(pos);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        int crushed = drops.stream().filter(d -> d.getItem().getItem() == item("crushed_cassiterite")).mapToInt(d -> d.getItem().getCount()).sum();
        c.check("hammer mining cassiterite ore -> 2 crushed (ore multiplier 2)", destroyed && crushed == 2,
                "destroyed=" + destroyed + " drops=" + drops.stream().map(d -> show(d.getItem())).toList());
        int dmg = player.getMainHandItem().getOrDefault(DataComponents.DAMAGE, 0);
        c.check("hammer lost durability (1 block + 4 conversion)", dmg == 5, "damage=" + dmg);
        drops.forEach(ItemEntity::discard);

        // Малая руда меди: выпадает дроблёная руда или грязная пыль (GT5 GT_TileEntity_Ores#getDrops, мета >= 16000)
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack("pickaxe_bronze"));
        level.setBlock(pos, BuiltInRegistries.BLOCK.getValue(Identifier.parse("iis:small_ore_copper")).defaultBlockState(), 3);
        player.gameMode.destroyBlock(pos);
        drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        boolean ok = !drops.isEmpty() && drops.stream().allMatch(d -> {
            Item i = d.getItem().getItem();
            return i == item("crushed_copper") || i == item("impure_dust_copper") || i == item("dust_stone");
        }) && drops.stream().anyMatch(d -> d.getItem().getItem() != item("dust_stone"));
        c.check("small copper ore drops crushed/impure dust (+ stone dust)", ok, drops.stream().map(d -> show(d.getItem())).toList().toString());
        drops.forEach(ItemEntity::discard);
    }

    static void cauldron(Ctx c) {
        ServerLevel level = c.level;
        BlockPos pos = BlockPos.containing(c.src.getPosition()).atY(level.getMaxY() - 14);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), 3);
        ItemEntity ie = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, new ItemStack(item("crushed_chalcopyrite"), 5));
        level.addFreshEntity(ie);
        ie.getItem().getItem().onEntityItemUpdate(ie.getItem(), ie);
        c.check("cauldron: crushed -> purified", is(ie.getItem(), "purified_chalcopyrite", 5), show(ie.getItem()));
        int lvl = level.getBlockState(pos).getValue(LayeredCauldronBlock.LEVEL);
        c.check("cauldron water level lowered", lvl == 2, "level=" + lvl);
        ie.discard();
    }
}

package ru.iis.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import ru.iis.IIS;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.lv.menu.ElectricMachineMenu;

import java.util.ArrayList;
import java.util.List;

/** Окна машин (D126): каждое открывается, и клиент строит то же меню, что сервер. Ошибка тут выбрасывала игрока из мира. */
final class GuiGameTests {
    private GuiGameTests() {}

    private static final BlockPos POS = new BlockPos(4, 2, 4);

    /** Для каждого блока с окном: меню сервера создаётся и рассылает содержимое, меню клиента того же размера. */
    static void everyMenuOpens(GameTestHelper h) {
        Player player = h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        BlockPos abs = h.absolutePos(POS);
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(IIS.MODID) || !(block instanceof EntityBlock)) continue;
            h.setBlock(POS, block);
            BlockEntity be = h.getLevel().getBlockEntity(abs);
            if (!(be instanceof MenuProvider provider)) continue;
            String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
            try {
                AbstractContainerMenu server = provider.createMenu(1, player.getInventory(), player);
                if (server == null) continue;
                server.broadcastFullState();
                for (var slot : server.slots) slot.getItem();
                if (server instanceof ru.iis.bio.production.BioStationMenu) {
                    checked++;
                    continue; // у мест Био свой заголовок меню, размер проверяет BioGameTests
                }
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
                buf.writeBlockPos(abs);
                AbstractContainerMenu client = server.getType().create(1, player.getInventory(), buf);
                if (client.slots.size() != server.slots.size()) bad.add(id + ": server " + server.slots.size() + ", client " + client.slots.size());
                for (var slot : client.slots) slot.getItem();
                checked++;
            } catch (RuntimeException e) {
                bad.add(id + ": " + e);
            }
        }
        h.assertTrue(bad.isEmpty(), "broken windows: " + bad);
        h.assertTrue(checked > 500, "only " + checked + " windows");
        h.succeed();
    }

    /** D127: рецепты ИИС 1 для готовых модов загрузились, рецепты модов, которые они заменяют, выключены. Без модов проверять нечего. */
    static void moreModsRecipes(GameTestHelper h) {
        if (!net.neoforged.fml.ModList.get().isLoaded("ironchest")) {
            h.succeed();
            return;
        }
        var recipes = h.getLevel().recipeAccess();
        List<String> bad = new ArrayList<>();
        for (String id : new String[]{"ironchest/iron_chest", "ironchest/gold_chest", "ironchest/diamond_chest", "ironchest/crystal_chest", "ironchest/obsidian_chest", "ironchest/arc_diamond",
                "chunkloaders/basic_chunk_loader", "constructionwand/iron_wand", "constructionwand/diamond_wand", "constructionwand/infinity_wand"}) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, net.minecraft.resources.Identifier.fromNamespaceAndPath(IIS.MODID, "moremods/" + id));
            if (recipes.byKey(key).isEmpty()) bad.add(id);
        }
        for (String id : new String[]{"ironchest:chests/vanilla_iron_chest", "chunkloaders:basic_chunk_loader", "constructionwand:iron_wand"}) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, net.minecraft.resources.Identifier.parse(id));
            if (recipes.byKey(key).isPresent()) bad.add("still on: " + id);
        }
        h.assertTrue(bad.isEmpty(), "recipes: " + bad);
        h.succeed();
    }

    /** Флешка кладётся в особый слот окна (125,63), как в GT, батарейка — в свой (80,63). */
    static void specialSlotTakesStick(GameTestHelper h) {
        h.setBlock(POS, ru.iis.lv.LvContent.MACHINES.get(LvMachineType.SCANNER).get());
        ElectricMachineBlockEntity be = h.getBlockEntity(POS, ElectricMachineBlockEntity.class);
        Player player = h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ElectricMachineMenu menu = (ElectricMachineMenu) be.createMenu(1, player.getInventory(), player);
        h.assertTrue(menu != null, "no menu");
        var stick = new net.minecraft.world.item.ItemStack(ru.iis.lv.LvContent.DATA_STICK.get());
        boolean battery = false, special = false;
        for (var slot : menu.slots) {
            if (slot.x == 80 && slot.y == 63) battery = slot.mayPlace(stick);
            if (slot.x == 125 && slot.y == 63) special = slot.mayPlace(stick);
        }
        h.assertTrue(!battery, "a stick goes to the battery slot");
        h.assertTrue(special, "no special slot");
        h.succeed();
    }
}

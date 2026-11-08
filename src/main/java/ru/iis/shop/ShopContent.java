package ru.iis.shop;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import ru.iis.IIS;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Магазин наград (D130): блок продаёт за жетоны квестов сырьё, которое уже бывало в инвентаре этого игрока. Цены — iis/shop_prices.json из tools/gen/shop.py.
 * Глава «Магазин» книги ИИС 1 остаётся рядом как есть.
 */
public final class ShopContent {
    private ShopContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IIS.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IIS.MODID);

    /** always — продаётся сразу, не дожидаясь, пока предмет побывает в инвентаре (еда, D142). */
    public record Price(int tier, int give, boolean always) {}

    private static Map<String, Price> prices;

    public static synchronized Map<String, Price> prices() {
        if (prices == null) {
            prices = new LinkedHashMap<>();
            try (var in = IIS.class.getResourceAsStream("/iis/shop_prices.json")) {
                if (in != null) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    root.getAsJsonArray("items").forEach(e -> {
                        JsonObject o = e.getAsJsonObject();
                        prices.put(o.get("id").getAsString(), new Price(o.get("tier").getAsInt(), o.get("give").getAsInt(), o.has("always") && o.get("always").getAsBoolean()));
                    });
                }
            } catch (Exception e) {
                IIS.LOGGER.error("[IIS] shop_prices.json", e);
            }
        }
        return prices;
    }

    /** Что бывало у игрока в руках: хранится с игроком и переживает смерть. */
    public static final Supplier<AttachmentType<List<String>>> SEEN = ATTACHMENTS.register("shop_seen",
            () -> AttachmentType.<List<String>>builder(() -> new ArrayList<>()).serialize(Codec.STRING.listOf().fieldOf("seen"), l -> !l.isEmpty()).copyOnDeath().build());

    public static class ShopBlock extends Block {
        public ShopBlock(Properties p) {
            super(p);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (player instanceof ServerPlayer sp) {
                List<String> seen = new ArrayList<>(sp.getData(SEEN));
                prices().forEach((id, pr) -> {
                    if (pr.always() && !seen.contains(id)) seen.add(id);
                });
                sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ShopMenu(id, inv, seen, pos), Component.translatable("gui.iis.shop.title")), buf -> {
                    buf.writeVarInt(seen.size());
                    seen.forEach(buf::writeUtf);
                });
            }
            return InteractionResult.SUCCESS;
        }
    }

    public static final DeferredBlock<ShopBlock> SHOP = BLOCKS.registerBlock("reward_shop", ShopBlock::new, p -> p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2.0F, 3.0F));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SHOP_ITEM = ITEMS.registerSimpleBlockItem("reward_shop", SHOP);

    public static final DeferredHolder<MenuType<?>, MenuType<ShopMenu>> SHOP_MENU = MENUS.register("reward_shop", () -> IMenuTypeExtension.create(ShopMenu::client));

    /** Без слотов: список приходит при открытии, покупка отдельным пакетом. */
    public static class ShopMenu extends AbstractContainerMenu {
        public final List<String> seen;
        private final BlockPos pos;
        private final Player player;

        public ShopMenu(int id, Inventory inv, List<String> seen, BlockPos pos) {
            super(SHOP_MENU.get(), id);
            this.seen = seen;
            this.pos = pos;
            this.player = inv.player;
        }

        /** Своих ячеек у окна нет, поэтому клиент без этого не узнаёт о списанных жетонах и купленном. */
        @Override
        public void broadcastChanges() {
            super.broadcastChanges();
            if (!player.level().isClientSide()) player.inventoryMenu.broadcastChanges();
        }

        public static ShopMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<String> seen = new ArrayList<>(n);
            for (int i = 0; i < n; i++) seen.add(buf.readUtf());
            return new ShopMenu(id, inv, seen, BlockPos.ZERO);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return pos == BlockPos.ZERO || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64;
        }
    }

    public static List<Item> creditItems() {
        return ru.iis.gear.GearContent.CREDITS.stream().map(d -> (Item) d.get()).toList();
    }

    public static int[] countCredits(Player p) {
        List<Item> credits = creditItems();
        int[] out = new int[credits.size()];
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            int k = credits.indexOf(s.getItem());
            if (k >= 0) out[k] += s.getCount();
        }
        return out;
    }

    /** Заменить жетоны игрока новым набором: убрать разницу или выдать её. */
    private static void setCredits(Player p, int[] was, int[] now) {
        List<Item> credits = creditItems();
        var inv = p.getInventory();
        for (int k = 0; k < now.length; k++) {
            int remove = was[k] - now[k];
            for (int i = 0; i < inv.getContainerSize() && remove > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(credits.get(k))) continue;
                int d = Math.min(remove, s.getCount());
                s.shrink(d);
                remove -= d;
            }
            for (int add = now[k] - was[k]; add > 0; ) {
                int n = Math.min(add, 64);
                give(p, new ItemStack(credits.get(k), n));
                add -= n;
            }
        }
    }

    private static void give(Player p, ItemStack s) {
        if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
    }

    /** Покупка на сервере: только то, что игрок уже видел; true — прошла. */
    public static boolean buy(ServerPlayer p, String id, int times) {
        Price price = prices().get(id);
        if (price == null || times < 1 || times > 64 || !p.getData(SEEN).contains(id)) return false;
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        int[] coins = countCredits(p);
        boolean free = p.getAbilities().instabuild;
        int[] after = free ? coins : ShopLogic.pay(coins, ShopLogic.value(price.tier()) * times);
        if (after == null) {
            p.sendOverlayMessage(Component.translatable("message.iis.shop.poor"));
            return false;
        }
        if (!free) setCredits(p, coins, after);
        int total = price.give() * times;
        while (total > 0) {
            int n = Math.min(total, item.getDefaultMaxStackSize());
            give(p, new ItemStack(item, n));
            total -= n;
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.5F, 1.2F);
        return true;
    }

    /** Раз в секунду: что из продаваемого сейчас в инвентаре, то игрок видел. */
    private static void remember(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        if (p.level().isClientSide() || p.tickCount % 20 != 0 || p.isSpectator()) return;
        Map<String, Price> table = prices();
        List<String> seen = null;
        Set<String> known = null;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            if (!table.containsKey(id)) continue;
            if (seen == null) {
                seen = new ArrayList<>(p.getData(SEEN));
                known = new HashSet<>(seen);
            }
            if (known.add(id)) seen.add(id);
        }
        if (seen != null && seen.size() != p.getData(SEEN).size()) p.setData(SEEN, seen);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(ShopContent::remember);
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || e.getTab() == ru.iis.equipment.EquipmentContent.TAB.get()) e.accept(SHOP_ITEM.get());
        });
    }
}

package ru.iis.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import ru.iis.IIS;

import java.util.Set;
import java.util.function.Supplier;

/**
 * В мире ИИС 2 нет магии (D137, решение владельца): ни зачарования, ни зелий, ни маяка, проводника, тотема, зачарованного яблока и пузырька опыта.
 * Рецепты выключены и сделки торговцев переписаны генератором (tools/gen/nomagic.py); здесь — добыча, снаряжение мобов и блоки, уже стоящие в мире.
 * Ведьмы и вызыватели остаются со своими атаками; машины Ender IO на душах и опыте остаются (ответы владельца).
 */
public final class NoMagic {
    private NoMagic() {}

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, IIS.MODID);
    public static final Supplier<MapCodec<Modifier>> CODEC = MODIFIERS.register("no_magic", () -> Modifier.MAP_CODEC);

    public static void register(IEventBus modBus) {
        MODIFIERS.register(modBus);
        NeoForge.EVENT_BUS.addListener(NoMagic::onJoin);
        NeoForge.EVENT_BUS.addListener(NoMagic::onUseBlock);
    }

    /** Вещи, которых нет совсем. */
    public static boolean magic(ItemStack s) {
        if (s.is(Items.ENCHANTED_BOOK) || s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION) || s.is(Items.TIPPED_ARROW) || s.is(Items.EXPERIENCE_BOTTLE)
                || s.is(Items.TOTEM_OF_UNDYING) || s.is(Items.ENCHANTED_GOLDEN_APPLE) || s.is(Items.BEACON) || s.is(Items.CONDUIT)
                || s.is(Items.ENCHANTING_TABLE) || s.is(Items.BREWING_STAND)) return true;
        // бутылка воды — просто вода
        return s.is(Items.POTION) && !s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
    }

    /** Снять чары; вернёт тот же предмет. */
    public static ItemStack disenchant(ItemStack s) {
        if (s.has(DataComponents.ENCHANTMENTS)) s.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (s.has(DataComponents.STORED_ENCHANTMENTS)) s.set(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        return s;
    }

    /** Добыча сундуков, рыбалки, мобов и всего прочего: магические вещи пропадают, с остального снимаются чары. */
    public static final class Modifier extends LootModifier {
        static final MapCodec<Modifier> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).apply(i, Modifier::new));

        public Modifier(LootItemCondition[] conditions, int priority) {
            super(conditions, priority);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            loot.removeIf(NoMagic::magic);
            loot.forEach(NoMagic::disenchant);
            return loot;
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return CODEC.get();
        }
    }

    /** Мобы появляются в незачарованном снаряжении. */
    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob mob)) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = mob.getItemBySlot(slot);
            if (s.isEmpty()) continue;
            if (magic(s)) mob.setItemSlot(slot, ItemStack.EMPTY);
            else disenchant(s);
        }
    }

    private static final Set<net.minecraft.world.level.block.Block> DEAD = Set.of(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE,
            net.minecraft.world.level.block.Blocks.BREWING_STAND, net.minecraft.world.level.block.Blocks.BEACON);

    /** Стол зачарования, варочная стойка и маяк в постройках мира не открываются. */
    private static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        BlockState s = e.getLevel().getBlockState(e.getPos());
        if (!DEAD.contains(s.getBlock())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
        if (!e.getLevel().isClientSide()) e.getEntity().sendOverlayMessage(Component.translatable("message.iis.no_magic"));
    }
}

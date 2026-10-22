package ru.iis.lv.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import ru.iis.IIS;
import ru.iis.lv.LvContent;

import java.util.function.Consumer;

/**
 * Программная схема GT (GT_IntegratedCircuit_Item, D75): конфигурация 0…24 лежит во входном слоте машины и выбирает рецепт,
 * не расходуясь. ПКМ — +1, Shift+ПКМ — −1; нужна отвёртка в инвентаре (износ 1), в творческом режиме — без неё.
 * Конфигурацию можно задать и на верстаке: схема и отвёртка в одном из 24 положений (как в GT).
 */
public class ProgrammedCircuitItem extends Item {
    public static final int MAX = 24;
    private static final TagKey<Item> SCREWDRIVER = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IIS.MODID, "tools/screwdriver"));

    public ProgrammedCircuitItem(Properties properties) {
        super(properties);
    }

    public static int config(ItemStack stack) {
        Integer c = stack.get(LvContent.CIRCUIT_CONFIG.get());
        return c == null ? 0 : c;
    }

    public static ItemStack withConfig(ItemStack stack, int config) {
        ItemStack s = stack.copy();
        s.set(LvContent.CIRCUIT_CONFIG.get(), Math.floorMod(config, MAX + 1));
        return s;
    }

    private static boolean useScrewdriver(Player player) {
        if (player.getAbilities().instabuild) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(SCREWDRIVER)) {
                s.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (!useScrewdriver(player)) {
            player.sendOverlayMessage(Component.translatable("message.iis.circuit.need_screwdriver"));
            return InteractionResult.FAIL;
        }
        int c = Math.floorMod(config(stack) + (player.isShiftKeyDown() ? -1 : 1), MAX + 1);
        stack.set(LvContent.CIRCUIT_CONFIG.get(), c);
        player.sendOverlayMessage(Component.translatable("message.iis.circuit.config", c));
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId() + ".numbered", config(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.iis.programmed_circuit.config", config(stack)).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.iis.programmed_circuit.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}

package ru.iis.lv.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;
import ru.iis.core.material.MaterialData;
import ru.iis.lv.ElectricToolLogic;
import ru.iis.lv.LvContent;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Электроинструмент LV (GT_MetaGenerated_Tool_01 DRILL_LV, CHAINSAW_LV, WRENCH_LV; D78). Головка из материала, корпус
 * из нержавейки, заряд — от батареи, с которой собран (натриевая 50 000, кадмиевая 75 000, литиевая 100 000 EU).
 * Каждое действие тратит EU вместо прочности (см. {@link ElectricToolLogic}); разряженный инструмент работает как пустая рука.
 * Заряжается в аккумуляторном буфере, как паяльник.
 */
public class ElectricToolItem extends BatteryItem {
    /** Обход перехвата урона: износ головки уже рассчитан (добыча блока). */
    private static final ThreadLocal<Boolean> RAW = ThreadLocal.withInitial(() -> false);
    private final MaterialData.ToolDef def;

    public ElectricToolItem(Properties properties, MaterialData.ToolDef def) {
        super(100000, 32, 1, properties);
        this.def = def;
    }

    public MaterialData.ToolDef def() {
        return def;
    }

    @Override
    public boolean isBattery() {
        return false;
    }

    @Override
    public long capacity(ItemStack stack) {
        return stack.getOrDefault(LvContent.MAX_CHARGE.get(), capacity);
    }

    /** Можно ли сейчас работать этим предметом (обычный инструмент — всегда, электрический — пока есть заряд). */
    public static boolean usable(ItemStack stack) {
        return !(stack.getItem() instanceof ElectricToolItem) || charge(stack) > 0;
    }

    private static int roll() {
        return ThreadLocalRandom.current().nextInt(ElectricToolLogic.WEAR_CHANCE);
    }

    /** Любой износ через hurtAndBreak (поворот ключом, валка, крафт): N единиц = N·50 EU, головка — с шансом 1/25. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        if (RAW.get()) return amount;
        ElectricToolLogic.Result r = ElectricToolLogic.use(charge(stack), (long) amount * ElectricToolLogic.GT_PER_POINT, roll());
        setCharge(stack, r.charge());
        return r.wear();
    }

    /** GT onBlockDestroyed: max(1, твёрдость × урон за блок) EU. */
    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (level.isClientSide() || (miner instanceof Player p && p.getAbilities().instabuild)) return true;
        long cost = ElectricToolLogic.blockCost(state.getDestroySpeed(level, pos), def.blockDamage() * ElectricToolLogic.GT_PER_POINT);
        ElectricToolLogic.Result r = ElectricToolLogic.use(charge(stack), cost, roll());
        setCharge(stack, r.charge());
        if (r.wear() > 0) {
            RAW.set(true);
            try {
                stack.hurtAndBreak(r.wear(), miner, EquipmentSlot.MAINHAND);
            } finally {
                RAW.set(false);
            }
        }
        return true;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return charge(stack) > 0 ? super.getDestroySpeed(stack, state) : 1.0f;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return charge(stack) > 0 && super.isCorrectToolForDrops(stack, state);
    }

    /** В рецептах крафта (ключ, пила) инструмент остаётся и тратит EU: GT getToolDamagePerContainerCraft. */
    @Override
    public ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        if (!(instance instanceof ItemStack stack) || stack.isEmpty()) return null;
        ItemStack copy = stack.copyWithCount(1);
        ElectricToolLogic.Result r = ElectricToolLogic.use(charge(copy), (long) def.craftDamage() * ElectricToolLogic.GT_PER_POINT, roll());
        setCharge(copy, r.charge());
        int damage = copy.getOrDefault(DataComponents.DAMAGE, 0) + r.wear();
        if (damage >= copy.getMaxDamage()) return null; // головка сломалась
        copy.set(DataComponents.DAMAGE, damage);
        return ItemStackTemplate.fromNonEmptyStack(copy);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("item.iis.electric_tool.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /** GT_Shaped_Recipe#getCraftingResult: собранный инструмент забирает заряд батарей из сетки (не других инструментов). */
    public static void onCrafted(PlayerEvent.ItemCraftedEvent e) {
        ItemStack result = e.getCrafting();
        if (!(result.getItem() instanceof ElectricToolItem tool)) return;
        Container grid = e.getInventory();
        long sum = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.getItem() instanceof BatteryItem b && b.isBattery()) sum += charge(s);
        }
        setCharge(result, ElectricToolLogic.craftedCharge(sum, tool.capacity(result)));
    }
}

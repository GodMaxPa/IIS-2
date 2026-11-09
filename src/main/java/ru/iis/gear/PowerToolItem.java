package ru.iis.gear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import ru.iis.lv.item.BatteryItem;

import java.util.function.Consumer;

/**
 * Электроинструменты IC2 и GraviSuite из ИИС 1 (D144), по уровням ИИС 2 (предложение ИИ, владелец: «по тирам»):
 * <ul>
 *   <li>электрокраник IC2 (LV, 10 000 EU, 50 EU за смолу) — как краник, без износа;</li>
 *   <li>электроключ IC2 (LV, 12 000 EU, 100 EU за блок или крафт) — снимает машины, ключ в рецептах;</li>
 *   <li>гравитул GraviSuite (HV, 300 000 EU, 50 EU) — ключ и отвёртка в рецептах, краник и мотыга по ПКМ;</li>
 *   <li>продвинутый бур GraviSuite (EV, 2 000 000 EU, 160 EU за блок) — Shift+ПКМ переключает копку 3×3;</li>
 *   <li>ваджра GraviSuite (IV, 10 000 000 EU, 3333 EU за блок) — ломает любой блок сразу, Shift+ПКМ — режим «шёлка» (блок целиком, без чар).</li>
 * </ul>
 */
public class PowerToolItem extends BatteryItem {
    public enum Kind { ELECTRIC_TREETAP, ELECTRIC_WRENCH, GRAVITOOL, ADVANCED_DRILL, VAJRA }

    public final Kind kind;
    public final int euPerUse;
    private static final ThreadLocal<Boolean> AREA = ThreadLocal.withInitial(() -> false);

    public PowerToolItem(Kind kind, long capacity, int tier, int euPerUse, Properties p) {
        super(capacity, 32L << (2 * (tier - 1)), tier, p);
        this.kind = kind;
        this.euPerUse = euPerUse;
    }

    @Override
    public boolean isBattery() {
        return false;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PowerToolItem::onBreak);
        NeoForge.EVENT_BUS.addListener(PowerToolItem::onDrops);
    }

    // --- режим (продвинутый бур: 3×3, ваджра: «шёлк») ------------------------------------------------------------------------
    public static boolean mode(ItemStack s) {
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr("iis_mode", false);
    }

    static void setMode(ItemStack s, boolean on) {
        CompoundTag t = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        t.putBoolean("iis_mode", on);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    boolean spend(ItemStack s, long eu) {
        if (charge(s) < eu) return false;
        setCharge(s, charge(s) - eu);
        return true;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if ((kind == Kind.ADVANCED_DRILL || kind == Kind.VAJRA) && player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                setMode(s, !mode(s));
                player.sendOverlayMessage(Component.translatable("message.iis.power_tool." + (kind == Kind.VAJRA ? "silk" : "area") + "." + (mode(s) ? "on" : "off")));
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        ItemStack s = ctx.getItemInHand();
        if (kind == Kind.ELECTRIC_TREETAP || kind == Kind.GRAVITOOL) {
            BlockState st = ctx.getLevel().getBlockState(ctx.getClickedPos());
            if (st.getBlock() instanceof ru.iis.lv.block.RubberLogBlock) {
                if (charge(s) < euPerUse) return InteractionResult.FAIL;
                InteractionResult r = ru.iis.lv.item.TreetapItem.extract(ctx, false);
                if (r == InteractionResult.SUCCESS && !ctx.getLevel().isClientSide()) spend(s, euPerUse);
                return r;
            }
            if (kind == Kind.GRAVITOOL && (st.is(Blocks.DIRT) || st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT_PATH))
                    && ctx.getLevel().getBlockState(ctx.getClickedPos().above()).isAir() && charge(s) >= euPerUse) {
                if (ctx.getLevel() instanceof ServerLevel sl) {
                    sl.setBlock(ctx.getClickedPos(), Blocks.FARMLAND.defaultBlockState(), 11);
                    sl.playSound(null, ctx.getClickedPos(), SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
                    spend(s, euPerUse);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.useOn(ctx);
    }

    // --- копка ---------------------------------------------------------------------------------------------------------------
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (charge(stack) < euPerUse) return 1.0f;
        if (kind == Kind.VAJRA) return 1000.0f;
        return super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        if (charge(stack) < euPerUse) return false;
        return kind == Kind.VAJRA || super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide() && !(miner instanceof Player p && p.getAbilities().instabuild) && state.getDestroySpeed(level, pos) > 0) spend(stack, euPerUse);
        return true;
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
        return 0;   // износа нет: всё оплачивает заряд
    }

    /** В рецептах (ключ, отвёртка) инструмент остаётся и тратит заряд. */
    @Override
    public ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        if (!(instance instanceof ItemStack stack) || stack.isEmpty()) return null;
        ItemStack copy = stack.copyWithCount(1);
        setCharge(copy, Math.max(0, charge(copy) - euPerUse));
        return ItemStackTemplate.fromNonEmptyStack(copy);
    }

    /** Продвинутый бур в режиме 3×3: ломает и соседей в плоскости грани. */
    private static void onBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent e) {
        Player p = e.getPlayer();
        ItemStack s = p.getMainHandItem();
        if (AREA.get() || !(s.getItem() instanceof PowerToolItem t) || t.kind != Kind.ADVANCED_DRILL || !mode(s)
                || !(e.getLevel() instanceof ServerLevel sl) || p.isShiftKeyDown()) return;
        HitResult hit = p.pick(p.blockInteractionRange(), 0.0f, false);
        Direction face = hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b.getDirection() : Direction.UP;
        AREA.set(true);
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    BlockPos q = switch (face.getAxis()) {
                        case X -> e.getPos().offset(0, a, b);
                        case Y -> e.getPos().offset(a, 0, b);
                        case Z -> e.getPos().offset(a, b, 0);
                    };
                    BlockState st = sl.getBlockState(q);
                    if (st.isAir() || st.getDestroySpeed(sl, q) < 0 || !t.isCorrectToolForDrops(s, st) || charge(s) < t.euPerUse) continue;
                    if (sl.destroyBlock(q, true, p)) t.spend(s, t.euPerUse);
                }
            }
        } finally {
            AREA.set(false);
        }
    }

    /** Ваджра в режиме «шёлка»: блок падает сам собой (без зачарования, D137). */
    private static void onDrops(BlockDropsEvent e) {
        ItemStack s = e.getTool();
        if (!(s.getItem() instanceof PowerToolItem t) || t.kind != Kind.VAJRA || !mode(s)) return;
        Item self = e.getState().getBlock().asItem();
        if (self == net.minecraft.world.item.Items.AIR) return;
        e.getDrops().clear();
        BlockPos p = e.getPos();
        e.getDrops().add(new ItemEntity(e.getLevel(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, new ItemStack(self)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("item.iis.power_tool.cost", euPerUse).withStyle(net.minecraft.ChatFormatting.GRAY));
        if (kind == Kind.ADVANCED_DRILL || kind == Kind.VAJRA) {
            builder.accept(Component.translatable("item.iis.power_tool." + (kind == Kind.VAJRA ? "silk" : "area") + "." + (mode(stack) ? "on" : "off"))
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        }
    }
}

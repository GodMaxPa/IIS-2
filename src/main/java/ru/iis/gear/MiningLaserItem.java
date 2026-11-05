package ru.iis.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import ru.iis.lv.item.BatteryItem;

import java.util.function.Consumer;

/** Лазерный бур: ПКМ стреляет, Shift+ПКМ меняет режим. Луч свой: без сущности, сразу до конца пути (D5, D118). */
public class MiningLaserItem extends BatteryItem {
    public MiningLaserItem(Properties p) {
        super(300000, 2048, 3, p);
    }

    @Override
    public boolean isBattery() {
        return false;
    }

    public static GearLogic.LaserMode mode(ItemStack stack) {
        return GearLogic.LaserMode.of(stack.getOrDefault(GearContent.MODE.get(), 0));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        GearLogic.LaserMode m = mode(stack);
        if (player.isShiftKeyDown()) {
            m = m.next();
            stack.set(GearContent.MODE.get(), m.ordinal());
            player.sendOverlayMessage(Component.translatable("item.iis.mining_laser.mode", Component.translatable("item.iis.mining_laser." + m.id)));
            return InteractionResult.SUCCESS;
        }
        fire(sl, player, stack, player.getEyePosition(), player.getLookAngle());
        return InteractionResult.SUCCESS;
    }

    /** Один выстрел текущего режима. false — не хватило заряда. */
    public static boolean fire(ServerLevel sl, Player player, ItemStack stack, Vec3 from, Vec3 look) {
        GearLogic.LaserMode m = mode(stack);
        long c = charge(stack);
        if (c < m.cost) return false;
        setCharge(stack, c - m.cost);
        Vec3 dir = m.horizontal ? new Vec3(look.x, 0, look.z).normalize() : look.normalize();
        if (m == GearLogic.LaserMode.SCATTER) {
            var rnd = sl.getRandom();
            for (int i = 0; i < m.rays; i++) {
                ray(sl, player, from, dir.add((rnd.nextFloat() - 0.5) * 0.5, (rnd.nextFloat() - 0.5) * 0.5, (rnd.nextFloat() - 0.5) * 0.5).normalize(), m);
            }
        } else if (m == GearLogic.LaserMode.THREE_BY_THREE) {
            Vec3 right = dir.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
            Vec3 up = right.cross(dir).normalize();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) ray(sl, player, from.add(right.scale(x)).add(up.scale(y)), dir, m);
            }
        } else {
            ray(sl, player, from, dir, m);
        }
        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.6F);
        return true;
    }

    private static void ray(ServerLevel sl, Player player, Vec3 from, Vec3 dir, GearLogic.LaserMode m) {
        float power = m.power;
        BlockPos last = null;
        Vec3 end = from;
        for (double d = 0.5; d <= m.range && power > 0; d += 0.25) {
            end = from.add(dir.scale(d));
            BlockPos pos = BlockPos.containing(end);
            if (pos.equals(last)) continue;
            last = pos;
            if (!sl.hasChunkAt(pos) || sl.isOutsideBuildHeight(pos)) break;
            BlockState state = sl.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            float hardness = state.getDestroySpeed(sl, pos);
            if (hardness < 0) break;
            float cost = GearLogic.blockCost(hardness);
            if (cost > power || !mayBreak(sl, player, pos, state)) break;
            power -= cost;
            if (m.smelts) {
                for (ItemStack drop : Block.getDrops(state, sl, pos, sl.getBlockEntity(pos), player, ItemStack.EMPTY)) Block.popResource(sl, pos, smelted(sl, drop));
                sl.destroyBlock(pos, false, player);
            } else {
                sl.destroyBlock(pos, true, player);
            }
        }
        AABB box = new AABB(from, end).inflate(0.5);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.getBoundingBox().inflate(0.3).clip(from, from.add(dir.scale(m.range))).isPresent())) {
            e.hurtServer(sl, player.damageSources().playerAttack(player), m.power * 2);
            if (m.smelts) e.igniteForSeconds(5);
        }
        double len = end.distanceTo(from);
        for (double d = 1; d < len; d += 1) {
            Vec3 p = from.add(dir.scale(d));
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static boolean mayBreak(ServerLevel sl, Player player, BlockPos pos, BlockState state) {
        return sl.mayInteract(player, pos) && !NeoForge.EVENT_BUS.post(new BreakBlockEvent(sl, pos, state, player)).isCanceled();
    }

    /** Перегрев: что плавится, падает переплавленным. */
    private static ItemStack smelted(ServerLevel sl, ItemStack drop) {
        return sl.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(drop), sl).map(r -> {
            ItemStack out = r.value().assemble(new SingleRecipeInput(drop));
            out.setCount(out.getCount() * drop.getCount());
            return out;
        }).orElse(drop);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("item.iis.mining_laser.mode", Component.translatable("item.iis.mining_laser." + mode(stack).id)).withStyle(ChatFormatting.GRAY));
    }
}

package ru.iis.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Простые инструменты GT из ИИС 1 (D144):
 * <ul>
 *   <li>коса (GT_Tool_Sense) — срезает растения и листву вокруг сломанного блока (5×3×5), единица прочности за блок;</li>
 *   <li>плуг (GT_Tool_Plow) — ПКМ вспахивает землю 3×3; ломает снег и землю как лопата;</li>
 *   <li>сучкорез (GT_Tool_BranchCutter) — с листвы всегда падает саженец;</li>
 *   <li>разделочный нож (GT_Tool_ButcheryKnife) — с убитого существа падает вдвое больше;</li>
 *   <li>вантуз (GT_Tool_Plunger) — ПКМ по трубе или люку сливает до 1000 л жидкости.</li>
 * </ul>
 * Плуг в GT убирал снег; пахота 3×3 — предложение ИИ, ближе к названию.
 */
public final class ToolBehaviours {
    private ToolBehaviours() {}

    private static final ThreadLocal<Boolean> SENSING = ThreadLocal.withInitial(() -> false);

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ToolBehaviours::onBreak);
        NeoForge.EVENT_BUS.addListener(ToolBehaviours::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(ToolBehaviours::onLivingDrops);
    }

    static boolean is(ItemStack s, String type) {
        return s.getItem() instanceof GtToolItem t && t.toolType().equals(type);
    }

    static boolean plant(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.CROPS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.SAPLINGS);
    }

    private static void onBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent e) {
        Player p = e.getPlayer();
        if (SENSING.get() || !(e.getLevel() instanceof ServerLevel sl) || !is(p.getMainHandItem(), "sense") || !plant(e.getState())) return;
        SENSING.set(true);
        try {
            for (BlockPos q : BlockPos.betweenClosed(e.getPos().offset(-2, -1, -2), e.getPos().offset(2, 1, 2))) {
                if (q.equals(e.getPos()) || p.getMainHandItem().isEmpty()) continue;
                BlockState s = sl.getBlockState(q);
                if (!plant(s)) continue;
                sl.destroyBlock(q.immutable(), true, p);
                p.getMainHandItem().hurtAndBreak(1, p, EquipmentSlot.MAINHAND);
            }
        } finally {
            SENSING.set(false);
        }
    }

    /** Саженец к листве: oak_leaves -> oak_sapling (и каучуковая листва ИИС), иначе ничего. */
    public static Item saplingFor(Block leaves) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(leaves);
        String path = id.getPath().replace("_leaves", "_sapling");
        if (path.equals("mangrove_sapling")) path = "mangrove_propagule";
        if (path.equals("azalea_sapling") || path.equals("flowering_azalea_sapling")) return Items.AZALEA;
        Item i = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(id.getNamespace(), path));
        return i == Items.AIR ? null : i;
    }

    private static void onBlockDrops(BlockDropsEvent e) {
        if (!is(e.getTool(), "branch_cutter") || !e.getState().is(BlockTags.LEAVES)) return;
        Item sap = saplingFor(e.getState().getBlock());
        if (sap == null) return;
        for (ItemEntity d : e.getDrops()) if (d.getItem().is(sap)) return;
        BlockPos p = e.getPos();
        e.getDrops().add(new ItemEntity(e.getLevel(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, new ItemStack(sap)));
    }

    private static void onLivingDrops(LivingDropsEvent e) {
        if (!(e.getSource().getEntity() instanceof Player p) || !is(p.getMainHandItem(), "butchery_knife")) return;
        List<ItemEntity> more = new ArrayList<>();
        for (ItemEntity d : e.getDrops()) {
            more.add(new ItemEntity(d.level(), d.getX(), d.getY(), d.getZ(), d.getItem().copy()));
        }
        e.getDrops().addAll(more);
    }

    /** ПКМ плугом и вантузом; null — не наш случай. */
    static InteractionResult useOn(GtToolItem tool, UseOnContext ctx) {
        String type = tool.toolType();
        if (type.equals("plow")) {
            if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(ctx.getClickedPos().offset(-1, 0, -1), ctx.getClickedPos().offset(1, 0, 1))) {
                BlockState s = sl.getBlockState(q);
                if (!sl.getBlockState(q.above()).isAir()) continue;
                if (s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT_PATH)) {
                    sl.setBlock(q, Blocks.FARMLAND.defaultBlockState(), 11);
                    n++;
                } else if (s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT)) {
                    sl.setBlock(q, Blocks.DIRT.defaultBlockState(), 11);
                    n++;
                }
            }
            if (n == 0) return null;
            sl.playSound(null, ctx.getClickedPos(), SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (ctx.getPlayer() != null) ctx.getItemInHand().hurtAndBreak(n, ctx.getPlayer(), ctx.getHand());
            return InteractionResult.CONSUME;
        }
        if (type.equals("plunger")) {
            var be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
            ru.iis.machine.block.FluidTank tank = be instanceof ru.iis.machine.block.PipeBlockEntity pipe ? pipe.tank()
                    : be instanceof ru.iis.multi.block.FluidHatchBlockEntity h ? h.tank() : null;
            if (tank == null || tank.amount() <= 0) return null;
            if (ctx.getLevel().isClientSide()) return InteractionResult.SUCCESS;
            tank.drainInternal(1000);
            ctx.getLevel().playSound(null, ctx.getClickedPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6f, 1.4f);
            if (ctx.getPlayer() != null) ctx.getItemInHand().hurtAndBreak(1, ctx.getPlayer(), ctx.getHand());
            return InteractionResult.CONSUME;
        }
        return null;
    }
}

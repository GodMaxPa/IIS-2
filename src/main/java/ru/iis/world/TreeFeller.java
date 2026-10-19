package ru.iis.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Валка дерева топором (GT5 GT_Tool_Axe, «Treecapitator»): срубленное бревно роняет всё дерево —
 * связанные брёвна выше и вокруг. С Shift — рубит одно бревно. Срабатывает, только если рядом есть
 * природная листва (постройки из брёвен не валятся). Каждое бревно изнашивает топор.
 */
public final class TreeFeller {
    private TreeFeller() {}

    public static final int MAX_LOGS = 256;
    private static boolean busy;

    public static void onBreak(BreakBlockEvent e) {
        if (busy || e.isCanceled() || !(e.getLevel() instanceof ServerLevel level)) return;
        Player player = e.getPlayer();
        BlockState state = e.getState();
        if (player.isShiftKeyDown() || player.getAbilities().instabuild || !state.is(BlockTags.LOGS)) return;
        ItemStack tool = player.getMainHandItem();
        if (!tool.is(ItemTags.AXES)) return;
        List<BlockPos> logs = findTree(level, e.getPos());
        if (logs.isEmpty()) return;
        busy = true;
        try {
            for (BlockPos p : logs) {
                if (tool.isEmpty()) break;
                BlockState s = level.getBlockState(p);
                if (!s.is(BlockTags.LOGS)) continue;
                Block.dropResources(s, level, p, level.getBlockEntity(p), player, tool);
                level.removeBlock(p, false);
                level.levelEvent(2001, p, Block.getId(s));
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
        } finally {
            busy = false;
        }
    }

    /** Брёвна дерева над срубленным (без него самого); пусто, если рядом нет природной листвы. */
    public static List<BlockPos> findTree(ServerLevel level, BlockPos origin) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> out = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        boolean leaves = false;
        while (!queue.isEmpty() && out.size() < MAX_LOGS) {
            BlockPos c = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos n = c.offset(dx, dy, dz);
                if (!seen.add(n)) continue;
                BlockState s = level.getBlockState(n);
                if (s.is(BlockTags.LOGS)) {
                    queue.add(n);
                    out.add(n);
                } else if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) {
                    leaves = true;
                }
            }
        }
        return leaves ? out : List.of();
    }
}

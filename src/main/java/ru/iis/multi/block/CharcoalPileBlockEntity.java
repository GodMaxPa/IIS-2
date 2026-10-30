package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.multi.MultiContent;
import ru.iis.multi.menu.EbfMenu;
import ru.iis.pollution.Pollution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Поджигатель угольной ямы (GT_MetaTileEntity_Charcoal_Pit, D105). Ставится сверху на кучу брёвен (до 11×6×11:
 * брёвна в пределах 5 блоков по сторонам и 5 вниз, верхний слой — вровень с поджигателем). Снизу у брёвен — кирпичи,
 * по сторонам и сверху — земля или дёрн (над первым бревном — сам поджигатель), пустот между брёвнами нет. Горит
 * √(брёвен × 240000) тиков, в начале даёт 5 загрязнения за каждый тик горения, потом все брёвна становятся ломким углём.
 * Обслуживания не требует, энергии не берёт.
 */
public class CharcoalPileBlockEntity extends MultiblockControllerBlockEntity {
    private boolean running;

    public CharcoalPileBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.CHARCOAL_PILE_BE.get(), pos, state);
    }

    /** GT checkMachine: формы нет, все неисправности считаются устранёнными. */
    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        maintenance.setMask(63);
        return true;
    }

    @Override
    protected int idleInterval() {
        return 100;
    }

    /** GT checkRecipe: первый вызов — проверить кучу и разжечь, вызов после горения — превратить брёвна в уголь. */
    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        if (!checkRecursiveBlocks(sl)) {
            efficiency = efficiencyIncrease = maxProgress = 0;
            running = false;
            return false;
        }
        if (efficiency == 0) {
            efficiency = 10000;
            efficiencyIncrease = 10000;
            maxProgress = Math.max(1, maxProgress);
            progress = 0;
            pendingItems = List.of();
            pendingFluids = List.of();
            Pollution.add(sl, worldPosition, maxProgress * 5);
            setChanged();
            return true;
        }
        efficiency = efficiencyIncrease = maxProgress = 0;
        return false;
    }

    private static boolean log(BlockState s) {
        return s.is(BlockTags.LOGS_THAT_BURN);
    }

    private static boolean dirt(BlockState s) {
        return s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL);
    }

    /** GT checkRecursiveBlocks + checkAllBlockSides (смещения — от поджигателя). */
    private boolean checkRecursiveBlocks(ServerLevel sl) {
        BlockPos start = new BlockPos(0, -1, 0);
        if (!log(sl.getBlockState(worldPosition.offset(start)))) return false;
        List<BlockPos> done = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int x = p.getX(), y = p.getY(), z = p.getZ();
            if (!side(sl, x + 1, y, z, x + 1 < 6, seen, queue) || !side(sl, x - 1, y, z, x - 1 > -6, seen, queue)
                    || !side(sl, x, y, z + 1, z + 1 < 6, seen, queue) || !side(sl, x, y, z - 1, z - 1 > -6, seen, queue)) return false;
            BlockState up = sl.getBlockState(worldPosition.offset(x, y + 1, z));
            if (y + 1 < 1 && log(up)) {
                enqueue(new BlockPos(x, y + 1, z), seen, queue);
            } else if (!dirt(up) && !(x == 0 && y == -1 && z == 0 && up.is(MultiContent.CHARCOAL_PILE.get()))) {
                return false;
            }
            BlockState down = sl.getBlockState(worldPosition.offset(x, y - 1, z));
            if (y - 1 > -6 && log(down)) enqueue(new BlockPos(x, y - 1, z), seen, queue);
            else if (!down.is(Blocks.BRICKS)) return false;
            done.add(p);
        }
        if (running) {
            for (BlockPos p : done) {
                BlockPos w = worldPosition.offset(p);
                if (log(sl.getBlockState(w))) sl.setBlock(w, MultiContent.BRITTLE_CHARCOAL.get().defaultBlockState(), 3);
            }
            running = false;
            return false;
        }
        maxProgress = (int) Math.sqrt(done.size() * 240000.0);
        running = true;
        return true;
    }

    /** Сосед сбоку: бревно в пределах — в очередь; иначе там должна быть земля или дёрн. */
    private boolean side(ServerLevel sl, int x, int y, int z, boolean inside, Set<BlockPos> seen, ArrayDeque<BlockPos> queue) {
        BlockState s = sl.getBlockState(worldPosition.offset(x, y, z));
        if (inside && log(s)) {
            enqueue(new BlockPos(x, y, z), seen, queue);
            return true;
        }
        return dirt(s);
    }

    private static void enqueue(BlockPos p, Set<BlockPos> seen, ArrayDeque<BlockPos> queue) {
        if (seen.add(p)) queue.add(p);
    }

    /** Энергии не берёт. */
    @Override
    protected boolean runningTick(ServerLevel sl) {
        return true;
    }

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return EbfMenu.freezer(id, inventory, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putBoolean("running", running);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        running = in.getBooleanOr("running", false);
    }
}

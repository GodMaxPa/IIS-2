package ru.iis.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.item.Prospection;
import ru.iis.world.ore.OreBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Разведка молотом (GT Behaviour_Prospecting, D122): ПКМ по камню — что за стеной и нет ли руды рядом; стук стоит единицу прочности.
 * Качество — уровень добычи молота: глубина 6 + q, пробы 9 + 2q в кубе ±(4 + q). Случайность зависит только от места и грани, как в GT: один и тот же стук даёт один ответ.
 */
public final class HammerProspecting {
    private HammerProspecting() {}

    /** Блок, по которому можно стучать (GT isReplaceableOreGen и руды GT). */
    public static boolean knockable(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(Blocks.END_STONE) || OreBlocks.isOre(s.getBlock());
    }

    /** null — стучать нельзя. */
    public static @Nullable List<Component> knock(ServerLevel sl, BlockPos pos, Direction face, int quality) {
        BlockState target = sl.getBlockState(pos);
        String here = Prospection.oreKey(target);
        if (here != null) return List.of(Component.translatable("item.iis.prospect.here", Component.translatable(here)));
        if (!knockable(target)) return null;
        List<Component> out = new ArrayList<>();
        Block block = target.getBlock();
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0, n = 6 + quality; i < n; i++) {
            p.move(face.getOpposite());
            BlockState s = sl.getBlockState(p);
            if (s.getFluidState().is(FluidTags.LAVA)) {
                out.add(Component.translatable("item.iis.prospect.lava"));
                break;
            }
            if (!s.getFluidState().isEmpty()) {
                out.add(Component.translatable("item.iis.prospect.liquid"));
                break;
            }
            if (s.getCollisionShape(sl, p).isEmpty()) {
                out.add(Component.translatable("item.iis.prospect.air"));
                break;
            }
            if (s.getBlock() != block) {
                if (i < 4) out.add(Component.translatable("item.iis.prospect.changing"));
                break;
            }
        }
        Random r = new Random(pos.getX() ^ pos.getY() ^ pos.getZ() ^ face.ordinal());
        int spread = 9 + 2 * quality;
        for (int i = 0; i < spread; i++) {
            p.set(pos.getX() - 4 - quality + r.nextInt(spread), pos.getY() - 4 - quality + r.nextInt(spread), pos.getZ() - 4 - quality + r.nextInt(spread));
            String key = Prospection.oreKey(sl.getBlockState(p));
            if (key != null) {
                out.add(Component.translatable("item.iis.prospect.found", Component.translatable(key)));
                return out;
            }
        }
        out.add(Component.translatable("item.iis.prospect.nothing"));
        return out;
    }
}

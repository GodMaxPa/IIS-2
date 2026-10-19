package ru.iis.machine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Поворот блоков гаечным ключом как в GT5: грань под прицелом делится сеткой 3×3 (сетка рисуется
 * на блоке, когда ключ в руке, см. WrenchGrid). Износ ключа — 1000 GT = 20 наших единиц.
 */
public final class Wrench {
    private Wrench() {}

    public static final int DAMAGE = 20;

    public static boolean is(ItemStack stack) {
        return stack.is(PipeBlock.WRENCH);
    }

    /**
     * GT_Utility.determineWrenchingSide: грань делится сеткой 0.25/0.75 на 9 клеток.
     * Центр — сама грань, край — соседняя грань в ту сторону (левый край — влево, верхний — вверх),
     * угол — противоположная грань. hx/hy/hz — точка клика внутри блока (0..1).
     */
    public static Direction wrenchingSide(Direction face, double hx, double hy, double hz) {
        Direction back = face.getOpposite();
        double a, b;          // две координаты на плоскости грани
        Direction aLo, aHi, bLo, bHi;
        switch (face.getAxis()) {
            case Y -> { a = hx; b = hz; aLo = Direction.WEST; aHi = Direction.EAST; bLo = Direction.NORTH; bHi = Direction.SOUTH; }
            case Z -> { a = hx; b = hy; aLo = Direction.WEST; aHi = Direction.EAST; bLo = Direction.DOWN; bHi = Direction.UP; }
            default -> { a = hz; b = hy; aLo = Direction.NORTH; aHi = Direction.SOUTH; bLo = Direction.DOWN; bHi = Direction.UP; }
        }
        boolean aEdge = a < 0.25 || a > 0.75, bEdge = b < 0.25 || b > 0.75;
        if (aEdge && bEdge) return back;
        if (aEdge) return a < 0.25 ? aLo : aHi;
        if (bEdge) return b < 0.25 ? bLo : bHi;
        return face;
    }

    public static Direction wrenchingSide(BlockPos pos, net.minecraft.world.phys.BlockHitResult hit) {
        var l = hit.getLocation();
        return wrenchingSide(hit.getDirection(), l.x - pos.getX(), l.y - pos.getY(), l.z - pos.getZ());
    }

    /** Поворачивает блок на сервере; true, если состояние изменилось. */
    /** GT onWrenchRightClick: лицевая сторона ставится на side, если она допустима для блока и отличается от текущей. */
    public static boolean rotate(Level level, BlockPos pos, BlockState state, EnumProperty<Direction> prop,
                                 Direction next, Player player, InteractionHand hand, ItemStack stack) {
        Direction now = state.getValue(prop);
        if (next == now || !prop.getPossibleValues().contains(next)) return false;
        level.setBlock(pos, state.setValue(prop, next), 3);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
        if (!player.getAbilities().instabuild) stack.hurtAndBreak(DAMAGE, player, hand);
        return true;
    }
}

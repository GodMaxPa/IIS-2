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
 * Поворот блоков гаечным ключом (GT5 onWrenchRightClick): лицевая сторона ставится на ту грань,
 * по которой кликнули. Клик по самой лицевой грани (или по верху/низу у блоков с горизонтальной
 * лицевой стороной) поворачивает по часовой стрелке. Износ ключа — 1000 GT = 20 наших единиц.
 */
public final class Wrench {
    private Wrench() {}

    public static final int DAMAGE = 20;

    public static boolean is(ItemStack stack) {
        return stack.is(PipeBlock.WRENCH);
    }

    /** Новое направление лицевой стороны. allowVertical — может ли лицо смотреть вверх/вниз. */
    public static Direction target(Direction current, Direction clicked, boolean allowVertical) {
        if (!allowVertical && clicked.getAxis() == Direction.Axis.Y) return current.getClockWise();
        if (clicked != current) return clicked;
        return clicked.getAxis() == Direction.Axis.Y ? Direction.NORTH : current.getClockWise();
    }

    /** Поворачивает блок на сервере; true, если состояние изменилось. */
    public static boolean rotate(Level level, BlockPos pos, BlockState state, EnumProperty<Direction> prop,
                                 Direction clicked, Player player, InteractionHand hand, ItemStack stack) {
        boolean vertical = prop.getPossibleValues().contains(Direction.UP);
        Direction now = state.getValue(prop);
        Direction next = target(now, clicked, vertical);
        if (next == now) return false;
        level.setBlock(pos, state.setValue(prop, next), 3);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
        if (!player.getAbilities().instabuild) stack.hurtAndBreak(DAMAGE, player, hand);
        return true;
    }
}

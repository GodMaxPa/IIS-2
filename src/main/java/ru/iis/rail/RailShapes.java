package ru.iis.rail;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.RailShape;

/** Формы пути по сторонам блока, которые путь соединяет. */
public final class RailShapes {
    private RailShapes() {}

    /** Путь между двумя горизонтальными сторонами: прямой или поворот. */
    public static RailShape connecting(Direction a, Direction b) {
        if (a.getAxis() == b.getAxis()) return a.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        boolean north = a == Direction.NORTH || b == Direction.NORTH;
        boolean east = a == Direction.EAST || b == Direction.EAST;
        return north ? (east ? RailShape.NORTH_EAST : RailShape.NORTH_WEST) : (east ? RailShape.SOUTH_EAST : RailShape.SOUTH_WEST);
    }

    public static RailShape straight(Direction.Axis axis) {
        return axis == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
    }

    public static boolean isCurve(RailShape s) {
        return s == RailShape.NORTH_EAST || s == RailShape.NORTH_WEST || s == RailShape.SOUTH_EAST || s == RailShape.SOUTH_WEST;
    }
}

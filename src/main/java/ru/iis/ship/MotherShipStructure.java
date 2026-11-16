package ru.iis.ship;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Материнский корабль — редкая крупная структура Верхнего мира (CONCEPT §5.1, D156). Упавший корпус: верхний ярус
 * выступает над землёй, два нижних ушли в грунт. Ставится только на суше. Частота и биомы — в данных
 * ({@code worldgen/structure_set/mother_ship.json}).
 */
public class MotherShipStructure extends Structure {
    public static final MapCodec<MotherShipStructure> CODEC = simpleCodec(MotherShipStructure::new);
    /** На сколько блоков корпус зарыт: пол первого яруса оказывается на уровне земли. */
    public static final int BURIED = ShipLayout.FLOOR_1;

    public MotherShipStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
        int lowest = getLowestY(context, x0, z0, ShipLayout.SIZE, ShipLayout.SIZE);
        if (lowest < context.chunkGenerator().getSeaLevel()) return Optional.empty();
        int y0 = Math.max(context.heightAccessor().getMinY() + 16, lowest - BURIED);
        long seed = context.seed() ^ chunk.pack();
        return Optional.of(new GenerationStub(new BlockPos(x0 + ShipLayout.SIZE / 2, y0 + BURIED, z0 + ShipLayout.SIZE / 2),
                builder -> builder.addPiece(new MotherShipPiece(x0, y0, z0, seed))));
    }

    @Override
    public StructureType<?> type() {
        return ShipContent.STRUCTURE.get();
    }
}
